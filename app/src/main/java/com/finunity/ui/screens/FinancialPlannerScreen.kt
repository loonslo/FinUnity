package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.FinancialGoal
import com.finunity.data.local.entity.FinancialPlanReview
import com.finunity.data.local.entity.FinancialPlanVersion
import com.finunity.data.local.entity.PlanningProfile
import com.finunity.data.local.entity.Transaction
import com.finunity.data.model.FinancialPlanSnapshot
import com.finunity.data.model.FinancialPlanResult
import com.finunity.data.model.FinancialPlannerEngine
import com.finunity.data.model.PlannerScenario
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSectionLabel
import com.finunity.ui.components.FinSoftButton
import com.finunity.ui.components.FinTextField
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Composable
fun FinancialPlannerScreen(
    profile: PlanningProfile?,
    goals: List<FinancialGoal>,
    plans: List<FinancialPlanVersion>,
    reviews: List<FinancialPlanReview>,
    availableCash: BigDecimal,
    availableCashKnown: Boolean,
    baseCurrency: String,
    accounts: List<Account>,
    transactions: List<Transaction>,
    onBack: () -> Unit,
    onSaveProfile: (PlanningProfile) -> Unit,
    onSaveGoal: (FinancialGoal) -> Unit,
    onDeleteGoal: (FinancialGoal) -> Unit,
    onSaveDraft: (FinancialPlanSnapshot) -> Unit,
    onConfirmPlan: (String, FinancialPlanSnapshot, Boolean) -> Unit,
    onRecordTransfer: (String, String, String, Double) -> Unit,
    onSaveReview: (FinancialPlanVersion, YearMonth, List<Transaction>, String) -> Unit
) {
    var income by remember(profile?.updatedAt) { mutableStateOf(profile?.monthlyIncome.orEmpty()) }
    var expenses by remember(profile?.updatedAt) { mutableStateOf(profile?.monthlyExpenses.orEmpty()) }
    var debtPayment by remember(profile?.updatedAt) { mutableStateOf(profile?.monthlyDebtPayment.orEmpty()) }
    var essential by remember(profile?.updatedAt) { mutableStateOf(profile?.essentialExpenses.orEmpty()) }
    var reserveMonths by remember(profile?.updatedAt) { mutableStateOf(profile?.reserveMonths?.toString() ?: "6") }
    var extraIncome by rememberSaveable { mutableStateOf("0") }
    var extraExpenses by rememberSaveable { mutableStateOf("0") }
    var annualReturn by rememberSaveable { mutableStateOf("0") }
    var annualInflation by rememberSaveable { mutableStateOf("0") }
    var targetReduction by rememberSaveable { mutableStateOf("0") }
    var extensionMonths by rememberSaveable { mutableStateOf("0") }
    var showGoalEditor by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<FinancialGoal?>(null) }
    var contributionGoal by remember { mutableStateOf<FinancialGoal?>(null) }
    var acceptShortfalls by rememberSaveable { mutableStateOf(false) }
    var reviewNote by rememberSaveable { mutableStateOf("") }

    val workingProfile = PlanningProfile(
        monthlyIncome = income,
        monthlyExpenses = expenses,
        monthlyDebtPayment = debtPayment,
        essentialExpenses = essential,
        reserveMonths = reserveMonths.toIntOrNull() ?: 6
    )
    val scenario = PlannerScenario(
        extraIncome = extraIncome.ifBlank { "0" },
        extraExpenses = extraExpenses.ifBlank { "0" },
        annualReturn = annualReturn.ifBlank { "0" },
        annualInflation = annualInflation.ifBlank { "0" },
        extensionMonths = extensionMonths.toIntOrNull() ?: 0,
        targetReductionPercent = targetReduction.ifBlank { "0" }
    )
    val today = remember { LocalDate.now(ZoneId.of("Asia/Shanghai")) }
    val snapshot = FinancialPlanSnapshot(
        profile = workingProfile,
        goals = goals,
        availableCash = availableCash.toPlainString(),
        availableCashKnown = availableCashKnown,
        baseCurrency = baseCurrency,
        asOfDate = today.toString(),
        scenario = scenario
    )
    val result = remember(snapshot) { FinancialPlannerEngine.calculate(snapshot) }
    val currentFingerprint = remember(snapshot) { FinancialPlannerEngine.fingerprint(snapshot) }
    val currentMonth = remember { YearMonth.now(ZoneId.of("Asia/Shanghai")) }
    val usableAccounts = remember(accounts) { accounts.filter { it.type != AccountType.LIABILITY } }
    val confirmedPlan = plans.firstOrNull { it.status == FinancialPlanVersion.STATUS_CONFIRMED }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar("目标规划", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                    Text("把目标变成每月能执行的计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("规划和预测不会改动账本；只有你确认实际投入后才会记流水。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                }
            }
            item {
                FinCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                    Text("可安排现金", style = MaterialTheme.typography.labelMedium, color = FinColors.TextSecondary)
                    Text(
                        money(availableCash, baseCurrency),
                        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
                        color = FinColors.Primary
                    )
                    Text("来自 FinUnity 未锁定的现金资产；房产和锁定资产不会算作可用现金。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                }
            }
            item {
                FinCard {
                    FinSectionLabel("每月财务情况")
                    Spacer(Modifier.height(8.dp))
                    PlannerMoneyField("月收入", income, { income = it }, baseCurrency)
                    PlannerMoneyField("月支出（不含下方最低还款）", expenses, { expenses = it }, baseCurrency)
                    PlannerMoneyField("每月最低还款", debtPayment, { debtPayment = it }, baseCurrency)
                    PlannerMoneyField("必要支出（用于应急预留）", essential, { essential = it }, baseCurrency)
                    FinTextField(reserveMonths, { reserveMonths = it.filter(Char::isDigit).take(2) }, "应急预留月数", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(10.dp))
                    FinSoftButton("保存财务资料", onClick = { onSaveProfile(workingProfile) }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FinSectionLabel("家庭目标", Modifier.weight(1f))
                    TextButton(onClick = { editingGoal = null; showGoalEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("添加目标")
                    }
                }
            }
            if (goals.isEmpty()) {
                item {
                    FinCard {
                        Text("还没有财务目标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("例如：家庭长期储蓄、教育、买房首付或旅行。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    }
                }
            } else {
                items(goals, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        projection = result.goals.firstOrNull { it.goalId == goal.id },
                        actualThisMonth = transactions.asSequence()
                            .filter { it.financialGoalId == goal.id && it.type == com.finunity.data.local.entity.TransactionType.TRANSFER_IN }
                            .filter { YearMonth.from(java.time.Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.of("Asia/Shanghai"))) == currentMonth }
                            .fold(BigDecimal.ZERO) { total, tx -> total + BigDecimal.valueOf(tx.amount) },
                        baseCurrency = baseCurrency,
                        onEdit = { editingGoal = goal; showGoalEditor = true },
                        onDelete = { onDeleteGoal(goal) },
                        onRecord = { contributionGoal = goal }
                    )
                }
            }
            item {
                FinCard {
                    FinSectionLabel("情景试算")
                    Text("收益和通胀只是可调整的假设，不代表承诺回报。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    PlannerMoneyField("每月额外收入", extraIncome, { extraIncome = it }, baseCurrency)
                    PlannerMoneyField("每月额外支出", extraExpenses, { extraExpenses = it }, baseCurrency)
                    PlannerMoneyField("年化收益假设（%）", annualReturn, { annualReturn = it }, "%", allowNegative = true)
                    PlannerMoneyField("年通胀假设（%）", annualInflation, { annualInflation = it }, "%")
                    PlannerMoneyField("目标金额下调（%）", targetReduction, { targetReduction = it }, "%")
                    FinTextField(extensionMonths, { extensionMonths = it.filter(Char::isDigit).take(3) }, "延期月数", keyboardType = KeyboardType.Number)
                }
            }
            item { PlanResultCard(result, baseCurrency) }
            item {
                FinCard {
                    FinSectionLabel("方案版本")
                    Text("先保存测算结果，再确认采用。后续修改输入会让草案过期，已确认的历史版本保留。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    FinSoftButton("保存当前测算", onClick = { onSaveDraft(snapshot) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acceptShortfalls, onCheckedChange = { acceptShortfalls = it })
                        Text("我已看到目标缺口，仍确认采用", style = MaterialTheme.typography.bodySmall)
                    }
                    plans.take(5).forEach { plan ->
                        val stale = plan.status == FinancialPlanVersion.STATUS_DRAFT && plan.inputFingerprint != currentFingerprint
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(statusLabel(plan.status, stale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(plan.createdAt)), style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                            }
                            if (plan.status == FinancialPlanVersion.STATUS_DRAFT && !stale) {
                                TextButton(onClick = { onConfirmPlan(plan.id, snapshot, acceptShortfalls) }) { Text("确认方案") }
                            }
                        }
                    }
                }
            }
            if (confirmedPlan != null) {
                item {
                    FinCard {
                        FinSectionLabel("本月复盘")
                        Text("账本统计只反映已记录的流水；未记入 FinUnity 的收支不会被推断为零。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                        FinTextField(reviewNote, { reviewNote = it }, "复盘备注（可选）")
                        Spacer(Modifier.height(8.dp))
                        FinSoftButton("保存 ${currentMonth} 复盘快照", onClick = {
                            onSaveReview(confirmedPlan, currentMonth, transactions, reviewNote)
                        }, modifier = Modifier.fillMaxWidth())
                        reviews.filter { it.planId == confirmedPlan.id }.take(3).forEach { review ->
                            Text("${review.month} · 收入 ${moneyString(review.actualIncome, baseCurrency)} · 支出 ${moneyString(review.actualExpenses, baseCurrency)}", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                            if (review.note.isNotBlank()) Text(review.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (showGoalEditor) {
        GoalEditorDialog(
            initial = editingGoal,
            onDismiss = { showGoalEditor = false },
            onSave = { onSaveGoal(it); showGoalEditor = false }
        )
    }
    contributionGoal?.let { goal ->
        ContributionDialog(
            goal = goal,
            accounts = usableAccounts,
            baseCurrency = baseCurrency,
            onDismiss = { contributionGoal = null },
            onSave = { from, to, amount -> onRecordTransfer(goal.id, from, to, amount); contributionGoal = null }
        )
    }
}

@Composable
private fun PlannerMoneyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String,
    allowNegative: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val numericText = if (allowNegative) text.removePrefix("-") else text
            if (numericText.all { it.isDigit() || it == '.' } && numericText.count { it == '.' } <= 1) onChange(text)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = FinShapes.xs
    )
}

@Composable
private fun GoalCard(
    goal: FinancialGoal,
    projection: com.finunity.data.model.GoalProjection?,
    actualThisMonth: BigDecimal,
    baseCurrency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRecord: () -> Unit
) {
    FinCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("期限 ${goal.targetDate} · 优先级 ${goal.priority}", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
            }
            TextButton(onClick = onEdit) { Text("编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "删除目标", tint = FinColors.TextSecondary) }
        }
        Text(moneyString(goal.targetAmount, baseCurrency), style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal), color = FinColors.Primary)
        Text("已分配 ${moneyString(goal.allocatedAmount, baseCurrency)} · 每月计划 ${moneyString(goal.monthlyContribution, baseCurrency)}", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
        projection?.let {
            Spacer(Modifier.height(10.dp))
            Text("按当前预算，每月可安排 ${moneyString(it.monthlyAffordable, baseCurrency)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("预计缺口 ${moneyString(it.projectedShortfall, baseCurrency)} · 尚有 ${it.monthsRemaining} 个月", style = MaterialTheme.typography.bodySmall, color = if (it.projectedShortfall == "0.00") FinColors.Success else FinColors.Warning)
        }
        Spacer(Modifier.height(6.dp))
        Text("本月已关联投入 ${money(actualThisMonth, baseCurrency)}", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
        TextButton(onClick = onRecord, enabled = true) { Text("记录实际投入") }
    }
}

@Composable
private fun PlanResultCard(result: FinancialPlanResult, currency: String) {
    FinCard {
        FinSectionLabel("测算结果")
        PlannerMetric("应急预留", result.emergencyReserve, currency)
        PlannerMetric("扣除应急预留后的现金", result.cashAvailableForGoals, currency)
        PlannerMetric("每月可安排预算", result.monthlyGoalBudget, currency)
        PlannerMetric("目标月投入缺口", result.monthlyUnfunded, currency)
        result.errors.forEach { Text(it, color = FinColors.Danger, style = MaterialTheme.typography.bodySmall) }
        result.missing.forEach { Text(it, color = FinColors.Warning, style = MaterialTheme.typography.bodySmall) }
        result.goals.forEach { goal ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.goalName, style = MaterialTheme.typography.bodyMedium)
                Text("需 ${goal.requiredMonthly?.let { moneyString(it, currency) } ?: "—"}/月", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PlannerMetric(label: String, amount: String?, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
        Text(amount?.let { moneyString(it, currency) } ?: "待补充", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoalEditorDialog(initial: FinancialGoal?, onDismiss: () -> Unit, onSave: (FinancialGoal) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var target by remember(initial?.id) { mutableStateOf(initial?.targetAmount.orEmpty()) }
    var allocated by remember(initial?.id) { mutableStateOf(initial?.allocatedAmount ?: "0") }
    var monthly by remember(initial?.id) { mutableStateOf(initial?.monthlyContribution ?: "0") }
    var date by remember(initial?.id) { mutableStateOf(initial?.targetDate ?: LocalDate.now(ZoneId.of("Asia/Shanghai")).plusYears(1).toString()) }
    var priority by remember(initial?.id) { mutableStateOf(initial?.priority?.toString() ?: "1") }
    val valid = name.isNotBlank() && target.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
        listOf(allocated, monthly).all { it.toBigDecimalOrNull()?.let { amount -> amount >= BigDecimal.ZERO } == true } &&
        runCatching { LocalDate.parse(date) }.isSuccess && priority.toIntOrNull()?.let { it in 1..100 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加财务目标" else "编辑财务目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FinTextField(name, { name = it }, "目标名称")
                PlannerMoneyField("目标金额", target, { target = it }, "")
                PlannerMoneyField("当前已分配金额", allocated, { allocated = it }, "")
                PlannerMoneyField("每月计划投入", monthly, { monthly = it }, "")
                FinTextField(date, { date = it }, "目标日期（YYYY-MM-DD）")
                FinTextField(priority, { priority = it.filter(Char::isDigit).take(3) }, "优先级（1–100）", keyboardType = KeyboardType.Number)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(FinancialGoal(
                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim(), targetAmount = target, allocatedAmount = allocated,
                monthlyContribution = monthly, targetDate = date, priority = priority.toInt(),
                createdAt = initial?.createdAt ?: System.currentTimeMillis()
            ))
        }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ContributionDialog(
    goal: FinancialGoal,
    accounts: List<Account>,
    baseCurrency: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit
) {
    var fromId by remember(goal.id) { mutableStateOf("") }
    var toId by remember(goal.id) { mutableStateOf("") }
    var amount by remember(goal.id) { mutableStateOf("") }
    var expandedFrom by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf(false) }
    val from = accounts.firstOrNull { it.id == fromId }
    val to = accounts.firstOrNull { it.id == toId }
    val targetAccounts = accounts.filter { it.currency.equals(baseCurrency, ignoreCase = true) }
    val parsedAmount = amount.toDoubleOrNull()
    val valid = from != null && to != null && from.id != to.id && parsedAmount != null && parsedAmount > 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录「${goal.name}」实际投入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("保存后会在 FinUnity 账本中记为真实转账，并关联此目标。", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                TextButton(onClick = { expandedFrom = true }) { Text("转出：${from?.name ?: "选择账户"}") }
                DropdownMenu(expanded = expandedFrom, onDismissRequest = { expandedFrom = false }) {
                    accounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} · ${account.currency}") }, onClick = { fromId = account.id; expandedFrom = false }) }
                }
                TextButton(onClick = { expandedTo = true }, enabled = targetAccounts.isNotEmpty()) {
                    Text("转入：${to?.name ?: if (targetAccounts.isEmpty()) "无 $baseCurrency 账户" else "选择 $baseCurrency 账户"}")
                }
                DropdownMenu(expanded = expandedTo, onDismissRequest = { expandedTo = false }) {
                    targetAccounts.forEach { account -> DropdownMenuItem(text = { Text("${account.name} · ${account.currency}") }, onClick = { toId = account.id; expandedTo = false }) }
                }
                PlannerMoneyField("转出金额（到账统一为 $baseCurrency）", amount, { amount = it }, from?.currency ?: baseCurrency)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(fromId, toId, parsedAmount!!) }) { Text("确认记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun money(value: BigDecimal, currency: String): String = moneyString(value.toPlainString(), currency)

@Composable
private fun moneyString(value: String, currency: String): String {
    val formatted = value.toBigDecimalOrNull()?.let {
        NumberFormat.getNumberInstance(Locale.CHINA).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(it)
    } ?: value
    return if (currency == "%") "$formatted%" else "$currency $formatted"
}

private fun statusLabel(status: String, stale: Boolean): String = when {
    stale -> "草案已过期，请重新测算"
    status == FinancialPlanVersion.STATUS_CONFIRMED -> "当前采用方案"
    status == FinancialPlanVersion.STATUS_SUPERSEDED -> "历史方案"
    else -> "待确认草案"
}
