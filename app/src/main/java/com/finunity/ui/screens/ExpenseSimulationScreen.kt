package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.displayName
import com.finunity.ui.components.FinPill
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

private enum class FundingSource(val label: String, val order: List<RiskBucket>) {
    CASH_FIRST("活钱优先", listOf(RiskBucket.CASH, RiskBucket.CONSERVATIVE, RiskBucket.AGGRESSIVE)),
    STEADY_FIRST("稳健优先", listOf(RiskBucket.CONSERVATIVE, RiskBucket.CASH, RiskBucket.AGGRESSIVE)),
    PROPORTIONAL("按比例", listOf(RiskBucket.CASH, RiskBucket.CONSERVATIVE, RiskBucket.AGGRESSIVE))
}

private val PURPOSES = listOf("买房", "买车", "装修", "教育", "其他")

// 展示顺序固定
private val BUCKET_ORDER = listOf(
    RiskBucket.AGGRESSIVE, RiskBucket.CONSERVATIVE, RiskBucket.INSURANCE, RiskBucket.CASH
)

/**
 * 大额支出模拟：输入金额 + 用途 + 资金来源，预览支出前后的总资产和四象限结构变化。
 * 「保命的钱」不参与扣减。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSimulationScreen(
    portfolioSummary: PortfolioSummary?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val baseCurrency = summary?.baseCurrency ?: "CNY"
    val totalAssets = summary?.totalAssets ?: 0.0

    // 当前各象限金额
    val beforeMap: Map<RiskBucket, Double> = remember(summary) {
        val m = RiskBucket.entries.associateWith { 0.0 }.toMutableMap()
        summary?.riskBuckets?.forEach { m[it.riskBucket] = it.totalValue }
        m
    }

    var amountText by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf(PURPOSES.first()) }
    var source by remember { mutableStateOf(FundingSource.CASH_FIRST) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val deduction = remember(amount, source, beforeMap) { deduct(beforeMap, amount, source) }
    val afterMap = deduction.first
    val shortfall = deduction.second
    val totalAfter = (totalAssets - amount + shortfall).coerceAtLeast(0.0)

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = {
            TopAppBar(
                title = { Text("大额支出模拟", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FinColors.PageBg)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 输入
            item {
                WhiteCard {
                    Text("这笔支出", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("支出金额") },
                        prefix = { Text(currencySymbol(baseCurrency)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = FinShapes.sm,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("用途", style = MaterialTheme.typography.labelLarge, color = FinColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LabelPills(PURPOSES, purpose) { purpose = it }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("资金来源", style = MaterialTheme.typography.labelLarge, color = FinColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FundingSource.entries.forEach { s ->
                            FinPill(selected = source == s, onClick = { source = s }, text = s.label)
                        }
                    }
                }
            }

            if (amount > 0) {
                // 总资产前后
                item {
                    WhiteCard {
                        Text("总资产变化", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BeforeAfterValue("支出前", totalAssets, baseCurrency, FinColors.TextSecondary, Modifier.weight(1f))
                            Text("→", style = MaterialTheme.typography.titleLarge, color = FinColors.TextSecondary)
                            BeforeAfterValue("支出后", totalAfter, baseCurrency, FinColors.Number, Modifier.weight(1f))
                        }
                    }
                }

                // 结构前后
                item {
                    WhiteCard {
                        Text("资产结构变化", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("支出前", style = MaterialTheme.typography.labelSmall, color = FinColors.TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        StructureBar(beforeMap)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("支出后", style = MaterialTheme.typography.labelSmall, color = FinColors.TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        StructureBar(afterMap)
                        Spacer(modifier = Modifier.height(14.dp))
                        BUCKET_ORDER.forEach { b ->
                            LegendDeltaRow(b, beforeMap[b] ?: 0.0, afterMap[b] ?: 0.0, totalAssets, totalAfter)
                        }
                    }
                }

                // 风险提示
                item {
                    val notes = riskNotes(amount, totalAssets, shortfall, afterMap, totalAfter, purpose)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinShapes.xl,
                        colors = CardDefaults.cardColors(
                            containerColor = if (shortfall > 0.01) Color(0xFFFFF6E9) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("温馨提示", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                            notes.forEach {
                                Text("· $it", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

/** 按资金来源瀑布式扣减，返回(扣减后金额map, 缺口) */
private fun deduct(
    before: Map<RiskBucket, Double>,
    amount: Double,
    source: FundingSource
): Pair<Map<RiskBucket, Double>, Double> {
    val after = before.toMutableMap()
    if (amount <= 0) return after to 0.0

    if (source == FundingSource.PROPORTIONAL) {
        val pool = source.order.sumOf { before[it] ?: 0.0 }
        if (pool <= 0) return after to amount
        val take = minOf(amount, pool)
        source.order.forEach { b ->
            val v = before[b] ?: 0.0
            after[b] = v - take * (v / pool)
        }
        return after to (amount - take)
    }

    var remaining = amount
    for (b in source.order) {
        if (remaining <= 0) break
        val v = after[b] ?: 0.0
        val t = minOf(remaining, v)
        after[b] = v - t
        remaining -= t
    }
    return after to remaining
}

private fun riskNotes(
    amount: Double,
    totalBefore: Double,
    shortfall: Double,
    after: Map<RiskBucket, Double>,
    totalAfter: Double,
    purpose: String
): List<String> {
    val notes = mutableListOf<String>()
    if (shortfall > 0.01) {
        notes.add("可动用资产不足以覆盖这笔$purpose 支出，缺口约 ${shortfall.toLong()}（保命的钱不计入）。")
    }
    val cashAfter = after[RiskBucket.CASH] ?: 0.0
    if (totalAfter > 0 && cashAfter / totalAfter < 0.05) {
        notes.add("支出后活钱占比偏低，建议保留 3-6 个月日常开销的现金。")
    }
    if (totalBefore > 0 && amount / totalBefore > 0.5) {
        notes.add("这笔支出超过总资产的一半，属于重大决策，建议谨慎评估。")
    }
    notes.add("「保命的钱」不参与本次扣减，保险与应急保障始终保留。")
    return notes
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun LabelPills(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        items.forEach { FinPill(selected = selected == it, onClick = { onSelect(it) }, text = it) }
    }
}

@Composable
private fun BeforeAfterValue(label: String, value: Double, baseCurrency: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = FinColors.TextSecondary)
        Text(formatCurrency(value, baseCurrency), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun StructureBar(map: Map<RiskBucket, Double>) {
    val total = BUCKET_ORDER.sumOf { map[it] ?: 0.0 }.coerceAtLeast(1.0)
    Row(
        modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(999.dp)).background(FinColors.Outline)
    ) {
        BUCKET_ORDER.forEach { b ->
            val v = map[b] ?: 0.0
            if (v > 0) {
                Box(modifier = Modifier.fillMaxHeight().weight((v / total).toFloat()).background(bucketColorSim(b)))
            }
        }
    }
}

@Composable
private fun LegendDeltaRow(bucket: RiskBucket, before: Double, after: Double, totalBefore: Double, totalAfter: Double) {
    val pctBefore = if (totalBefore > 0) before / totalBefore else 0.0
    val pctAfter = if (totalAfter > 0) after / totalAfter else 0.0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(bucketColorSim(bucket)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(bucket.displayName(), style = MaterialTheme.typography.bodySmall,
            color = FinColors.TextPrimary, modifier = Modifier.weight(1f))
        Text("${(pctBefore * 100).toInt()}% → ${(pctAfter * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
    }
}

private fun bucketColorSim(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> FinColors.Aggressive
    RiskBucket.CONSERVATIVE -> FinColors.Conservative
    RiskBucket.INSURANCE -> FinColors.Insurance
    RiskBucket.CASH -> FinColors.Cash
}

private fun currencySymbol(currency: String): String = when (currency) {
    "CNY" -> "¥"; "USD" -> "$"; "HKD" -> "HK$"; else -> currency
}
