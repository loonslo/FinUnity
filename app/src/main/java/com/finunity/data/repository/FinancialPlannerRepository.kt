package com.finunity.data.repository

import androidx.room.withTransaction
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.FinancialGoal
import com.finunity.data.local.entity.FinancialPlanReview
import com.finunity.data.local.entity.FinancialPlanVersion
import com.finunity.data.local.entity.PlanningProfile
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.model.FinancialPlanResult
import com.finunity.data.model.FinancialPlanSnapshot
import com.finunity.data.model.FinancialPlannerEngine
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

sealed interface ConfirmFinancialPlanResult {
    data object Confirmed : ConfirmFinancialPlanResult
    data object Stale : ConfirmFinancialPlanResult
    data object Invalid : ConfirmFinancialPlanResult
    data object NeedsShortfallAcceptance : ConfirmFinancialPlanResult
}

class FinancialPlannerRepository(private val db: AppDatabase) {
    private val dao = db.financialPlannerDao()
    private val gson = Gson()

    fun observeProfile(): Flow<PlanningProfile?> = dao.observeProfile()
    fun observeGoals(): Flow<List<FinancialGoal>> = dao.observeGoals()
    fun observePlans(): Flow<List<FinancialPlanVersion>> = dao.observePlans()
    fun observeReviews(): Flow<List<FinancialPlanReview>> = dao.observeReviews()

    suspend fun saveProfile(profile: PlanningProfile) = dao.saveProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    suspend fun saveGoal(goal: FinancialGoal) = dao.saveGoal(goal.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteGoal(goal: FinancialGoal) = dao.deleteGoal(goal)

    suspend fun saveDraft(snapshot: FinancialPlanSnapshot): FinancialPlanVersion {
        val result = FinancialPlannerEngine.calculate(snapshot)
        return FinancialPlanVersion(
            inputFingerprint = FinancialPlannerEngine.fingerprint(snapshot),
            snapshotJson = gson.toJson(snapshot),
            resultJson = gson.toJson(result)
        ).also { dao.savePlan(it) }
    }

    suspend fun confirm(
        planId: String,
        currentSnapshot: FinancialPlanSnapshot,
        acceptShortfalls: Boolean
    ): ConfirmFinancialPlanResult = db.withTransaction {
        val plan = dao.getPlan(planId) ?: return@withTransaction ConfirmFinancialPlanResult.Invalid
        if (plan.status != FinancialPlanVersion.STATUS_DRAFT ||
            plan.inputFingerprint != FinancialPlannerEngine.fingerprint(currentSnapshot)
        ) return@withTransaction ConfirmFinancialPlanResult.Stale
        val result = FinancialPlannerEngine.calculate(currentSnapshot)
        if (!result.canConfirm) return@withTransaction ConfirmFinancialPlanResult.Invalid
        if (!acceptShortfalls && result.goals.any { BigDecimal(it.projectedShortfall) > BigDecimal.ZERO }) {
            return@withTransaction ConfirmFinancialPlanResult.NeedsShortfallAcceptance
        }
        dao.supersedeConfirmedPlans()
        dao.savePlan(plan.copy(status = FinancialPlanVersion.STATUS_CONFIRMED, confirmedAt = System.currentTimeMillis()))
        ConfirmFinancialPlanResult.Confirmed
    }

    fun isStale(plan: FinancialPlanVersion, currentSnapshot: FinancialPlanSnapshot): Boolean =
        plan.status == FinancialPlanVersion.STATUS_DRAFT &&
            plan.inputFingerprint != FinancialPlannerEngine.fingerprint(currentSnapshot)

    suspend fun saveReview(
        plan: FinancialPlanVersion,
        month: YearMonth,
        transactions: List<Transaction>,
        note: String = ""
    ): FinancialPlanReview {
        val start = month.atDay(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()
        val monthly = transactions.filter {
            val instant = Instant.ofEpochMilli(it.timestamp)
            !instant.isBefore(start) && instant.isBefore(end) &&
                it.type !in setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
        }
        val income = monthly.filter { it.category.income == true }.sumAmounts()
        val expenses = monthly.filter { it.category.income == false }.sumAmounts()
        val contributions = transactions.asSequence()
            .filter {
                it.financialGoalId != null && it.type == TransactionType.TRANSFER_IN &&
                    Instant.ofEpochMilli(it.timestamp).let { time -> !time.isBefore(start) && time.isBefore(end) }
            }
            .groupBy { it.financialGoalId!! }
            .mapValues { (_, rows) -> rows.sumAmounts() }
            .mapValues { (_, amount) -> amount.toPlainString() }
        val review = FinancialPlanReview(
            planId = plan.id,
            month = month.toString(),
            actualIncome = income.toPlainString(),
            actualExpenses = expenses.toPlainString(),
            actualGoalContributionsJson = gson.toJson(contributions),
            note = note,
            createdAt = System.currentTimeMillis()
        )
        dao.saveReview(review)
        return review
    }

    private fun List<Transaction>.sumAmounts(): BigDecimal =
        fold(BigDecimal.ZERO) { total, transaction -> total + BigDecimal.valueOf(transaction.amount) }
}
