package com.finunity.data.model

import com.finunity.data.local.entity.FinancialGoal
import com.finunity.data.local.entity.PlanningProfile
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate

data class PlannerScenario(
    val extraIncome: String = "0",
    val extraExpenses: String = "0",
    val annualReturn: String = "0",
    val annualInflation: String = "0",
    val extensionMonths: Int = 0,
    val targetReductionPercent: String = "0"
)

data class FinancialPlanSnapshot(
    val profile: PlanningProfile,
    val goals: List<FinancialGoal>,
    val availableCash: String,
    val availableCashKnown: Boolean = true,
    val baseCurrency: String,
    val asOfDate: String,
    val scenario: PlannerScenario = PlannerScenario()
)

data class GoalProjection(
    val goalId: String,
    val goalName: String,
    val monthsRemaining: Int,
    val targetAmount: String,
    val allocatedAmount: String,
    val monthlyRequested: String,
    val monthlyAffordable: String,
    val requiredMonthly: String?,
    val projectedAmount: String,
    val projectedShortfall: String
)

data class FinancialPlanResult(
    val baseCurrency: String,
    val emergencyReserve: String?,
    val cashAvailableForGoals: String?,
    val monthlySurplus: String?,
    val monthlyGoalBudget: String?,
    val monthlyUnfunded: String?,
    val missing: List<String>,
    val errors: List<String>,
    val goals: List<GoalProjection>
) {
    val canConfirm: Boolean get() = missing.isEmpty() && errors.isEmpty() && goals.isNotEmpty()
}

/** Deterministic local planner. It has no database, network, clock, or ledger side effects. */
object FinancialPlannerEngine {
    private val mc = MathContext(34, RoundingMode.HALF_EVEN)
    private val zero = BigDecimal.ZERO
    private val cents = BigDecimal("0.01")

    fun fingerprint(snapshot: FinancialPlanSnapshot): String {
        val canonical = buildString {
            append(snapshot.profile.id).append('|')
            append(snapshot.profile.monthlyIncome).append('|')
            append(snapshot.profile.monthlyExpenses).append('|')
            append(snapshot.profile.monthlyDebtPayment).append('|')
            append(snapshot.profile.essentialExpenses).append('|')
            append(snapshot.profile.reserveMonths).append('|')
            append(snapshot.availableCash).append('|').append(snapshot.baseCurrency).append('|')
            append(snapshot.availableCashKnown).append('|').append(snapshot.asOfDate).append('|').append(snapshot.scenario).append('|')
            snapshot.goals.sortedWith(compareBy<FinancialGoal> { it.priority }.thenBy { it.id }).forEach {
                append(it.id).append(':').append(it.name).append(':').append(it.targetAmount)
                    .append(':').append(it.allocatedAmount).append(':').append(it.monthlyContribution)
                    .append(':').append(it.targetDate).append(':').append(it.priority).append('|')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun calculate(snapshot: FinancialPlanSnapshot): FinancialPlanResult {
        val missing = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val profile = snapshot.profile
        val scenario = snapshot.scenario
        val asOf = parseDate(snapshot.asOfDate, "测算日期", errors)
        val income = parseMoney(profile.monthlyIncome, "月收入", missing, errors)
        val expenses = parseMoney(profile.monthlyExpenses, "月支出", missing, errors)
        val debtPayment = parseMoney(profile.monthlyDebtPayment, "月最低还款", missing, errors, defaultZero = true)
        val essential = parseMoney(profile.essentialExpenses, "必要支出", missing, errors)
        val cash = parseMoney(snapshot.availableCash, "可用现金", missing, errors)
        if (!snapshot.availableCashKnown) missing += "部分现金币种缺少汇率，暂不能确认该规划输入"
        val extraIncome = parseMoney(scenario.extraIncome, "情景额外收入", missing, errors, defaultZero = true)
        val extraExpenses = parseMoney(scenario.extraExpenses, "情景额外支出", missing, errors, defaultZero = true)
        val annualReturn = parseRate(scenario.annualReturn, "年化收益假设", errors, BigDecimal("-90"), BigDecimal("50"))
        val annualInflation = parseRate(scenario.annualInflation, "年通胀假设", errors, zero, BigDecimal("30"))
        val targetReduction = parseRate(scenario.targetReductionPercent, "目标下调比例", errors, zero, BigDecimal("100"), isPercent = false)

        if (snapshot.baseCurrency.isBlank()) errors += "请先设置基准币种"
        if (profile.reserveMonths !in 0..60) errors += "应急预留月数需在 0–60 之间"
        if (scenario.extensionMonths !in 0..120) errors += "延期月数需在 0–120 之间"
        if (snapshot.goals.isEmpty()) missing += "请至少添加一个财务目标"
        if (snapshot.goals.map { it.id }.distinct().size != snapshot.goals.size) errors += "目标 ID 重复"

        val reserve = if (essential == null) null else essential.multiply(BigDecimal(profile.reserveMonths))
        val cashForGoals = if (reserve == null || cash == null) null else cash.subtract(reserve).max(zero)
        val allocatedTotal = snapshot.goals.fold(zero) { acc, goal ->
            acc + (parseMoney(goal.allocatedAmount, "${goal.name}已分配金额", missing, errors) ?: zero)
        }
        if (cashForGoals != null && allocatedTotal > cashForGoals) {
            errors += "目标已分配资金超过扣除应急预留后的可用现金"
        }

        val monthlySurplus = if (income == null || expenses == null || debtPayment == null || extraIncome == null || extraExpenses == null) null else {
            income + extraIncome - expenses - extraExpenses - debtPayment
        }
        if (monthlySurplus != null && monthlySurplus < zero) errors += "月收入不足以覆盖支出和最低还款"
        val monthlyBudget = monthlySurplus?.max(zero)
        var remainingBudget = monthlyBudget ?: zero
        val goals = mutableListOf<GoalProjection>()

        if (asOf != null) {
            snapshot.goals.sortedWith(compareBy<FinancialGoal> { it.priority }.thenBy { it.createdAt }).forEach { goal ->
                val target = parseMoney(goal.targetAmount, "${goal.name}目标金额", missing, errors)
                val allocated = parseMoney(goal.allocatedAmount, "${goal.name}已分配金额", missing, errors) ?: zero
                val requested = parseMoney(goal.monthlyContribution, "${goal.name}月投入", missing, errors) ?: zero
                val targetDate = parseDate(goal.targetDate, "${goal.name}目标日期", errors)
                val affordable = requested.min(remainingBudget)
                remainingBudget -= affordable
                val months = if (targetDate == null) 0 else monthsBetween(asOf, targetDate.plusMonths(scenario.extensionMonths.toLong()))
                if (months <= 0) errors += "${goal.name}的目标日期必须晚于测算日期"
                val adjustedTarget = target?.multiply(BigDecimal.ONE - (targetReduction ?: zero).divide(BigDecimal("100"), mc))
                    ?.multiply(BigDecimal.ONE.add((annualInflation ?: zero).divide(BigDecimal("12"), mc)).pow(months.coerceAtLeast(0), mc))
                val monthlyRate = (annualReturn ?: zero).divide(BigDecimal("12"), mc)
                val growth = BigDecimal.ONE.add(monthlyRate).pow(months.coerceAtLeast(0), mc)
                val annuity = if (months <= 0) zero else if (monthlyRate.compareTo(zero) == 0) {
                    BigDecimal(months)
                } else {
                    growth.subtract(BigDecimal.ONE).divide(monthlyRate, mc)
                }
                val projected = if (adjustedTarget == null) zero else allocated.multiply(growth, mc)
                    .add(affordable.multiply(annuity, mc), mc)
                val required = if (adjustedTarget == null || annuity.compareTo(zero) <= 0) null else {
                    adjustedTarget.subtract(allocated.multiply(growth, mc)).max(zero)
                        .divide(annuity, mc).setScale(2, RoundingMode.CEILING)
                }
                val shortfall = (adjustedTarget ?: zero).subtract(projected).max(zero)
                goals += GoalProjection(
                    goalId = goal.id,
                    goalName = goal.name,
                    monthsRemaining = months.coerceAtLeast(0),
                    targetAmount = adjustedTarget?.let(::format) ?: "0.00",
                    allocatedAmount = format(allocated),
                    monthlyRequested = format(requested),
                    monthlyAffordable = format(affordable),
                    requiredMonthly = required?.let(::format),
                    projectedAmount = format(projected),
                    projectedShortfall = format(shortfall)
                )
            }
        }

        val totalRequested = goals.fold(zero) { acc, goal -> acc + BigDecimal(goal.monthlyRequested) }
        val monthlyUnfunded = (totalRequested - (monthlyBudget ?: zero)).max(zero)
        return FinancialPlanResult(
            baseCurrency = snapshot.baseCurrency,
            emergencyReserve = reserve?.let(::format),
            cashAvailableForGoals = cashForGoals?.let(::format),
            monthlySurplus = monthlySurplus?.let(::format),
            monthlyGoalBudget = monthlyBudget?.let(::format),
            monthlyUnfunded = monthlyBudget?.let { format(monthlyUnfunded) },
            missing = missing.distinct(),
            errors = errors.distinct(),
            goals = goals
        )
    }

    private fun parseMoney(
        value: String?,
        label: String,
        missing: MutableList<String>,
        errors: MutableList<String>,
        defaultZero: Boolean = false
    ): BigDecimal? {
        if (value.isNullOrBlank()) {
            if (defaultZero) return zero
            missing += "请填写$label"
            return null
        }
        val parsed = value.trim().toBigDecimalOrNull()
        if (parsed == null || parsed < zero) {
            errors += "${label}必须是非负数字"
            return null
        }
        return parsed
    }

    private fun parseRate(
        value: String,
        label: String,
        errors: MutableList<String>,
        min: BigDecimal,
        max: BigDecimal,
        isPercent: Boolean = true
    ): BigDecimal? {
        val parsed = value.trim().toBigDecimalOrNull()
        if (parsed == null || parsed < min || parsed > max) {
            errors += "${label}超出允许范围"
            return null
        }
        return if (isPercent) parsed.divide(BigDecimal("100"), mc) else parsed
    }

    private fun parseDate(value: String, label: String, errors: MutableList<String>): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrElse {
            errors += "${label}格式应为 YYYY-MM-DD"
            null
        }

    private fun monthsBetween(from: LocalDate, to: LocalDate): Int {
        val raw = (to.year - from.year) * 12 + to.monthValue - from.monthValue
        if (raw <= 0) return 0
        return if (from.plusMonths(raw.toLong()) > to) raw - 1 else raw
    }

    private fun format(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
