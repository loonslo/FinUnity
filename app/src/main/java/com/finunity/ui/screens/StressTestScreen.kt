package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.model.*
import com.finunity.ui.components.FinPill
import com.finunity.ui.components.FinTextField
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

@Composable
fun StressTestScreen(
    portfolioSummary: PortfolioSummary?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val baseCurrency = summary?.baseCurrency ?: "CNY"
    val strategyAssets = summary?.strategyAssets ?: 0.0
    val weights = RiskBucket.entries.associateWith { summary?.allocations?.get(it.name) ?: 0.0 }
    var maxLossText by remember { mutableStateOf("") }
    var scenarios by remember { mutableStateOf(DEFAULT_STRESS_SCENARIOS) }
    var selectedId by remember { mutableStateOf(DEFAULT_STRESS_SCENARIOS.first().id) }
    val selected = scenarios.first { it.id == selectedId }
    val maxLoss = maxLossText.toDoubleOrNull() ?: 0.0
    val results = scenarios.map { computeStressLoss(strategyAssets, weights, it, maxLoss) }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar(title = "压力测试", onBack = onBack) },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                StressCard {
                    Text("你能承受多少浮亏？", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    FinTextField(
                        value = maxLossText,
                        onValueChange = { maxLossText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "最大可承受亏损 M（$baseCurrency）",
                        placeholder = "如：100000",
                        keyboardType = KeyboardType.Decimal
                    )
                    Text("计算口径：可投策略盘 ${formatCurrency(strategyAssets, baseCurrency)}，不包含锁定专款。",
                        style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                }
            }
            item {
                StressCard {
                    Text("编辑情景跌幅", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        scenarios.forEach { FinPill(selected = it.id == selectedId, onClick = { selectedId = it.id }, text = it.id) }
                    }
                    Text(selected.label, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    RiskBucket.entries.forEach { bucket ->
                        val percent = ((selected.drops[bucket] ?: 0.0) * 100).toString().removeSuffix(".0")
                        FinTextField(
                            value = percent,
                            onValueChange = { raw ->
                                val value = raw.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull()?.div(100.0)?.coerceIn(0.0, 1.0) ?: 0.0
                                scenarios = scenarios.map { scenario ->
                                    if (scenario.id == selectedId) scenario.copy(drops = scenario.drops + (bucket to value)) else scenario
                                }
                            },
                            label = "${bucketStressLabel(bucket)}跌幅（%）",
                            placeholder = "0",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }
            }
            results.forEach { result ->
                item(key = result.scenario.id) {
                    StressCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(result.scenario.label, fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
                            Text(String.format("-%.1f%%", result.lossRatio * 100),
                                color = if (result.withinTolerance) FinColors.Profit else FinColors.Loss)
                        }
                        Text("预估峰谷浮亏 ${formatCurrency(result.lossAmount, baseCurrency)}",
                            style = MaterialTheme.typography.bodyMedium, color = FinColors.Number)
                        Text(
                            if (maxLoss <= 0.0) "填写 M 后判断是否可承受"
                            else if (result.withinTolerance) "在你的承受范围内" else "超过 M，需降低风险仓位或提高安全垫",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (maxLoss > 0.0 && !result.withinTolerance) FinColors.Loss else FinColors.TextSecondary
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun StressCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

private fun bucketStressLabel(bucket: RiskBucket) = when (bucket) {
    RiskBucket.AGGRESSIVE -> "进取"
    RiskBucket.CONSERVATIVE -> "稳健"
    RiskBucket.INSURANCE -> "保命"
    RiskBucket.CASH -> "防守"
}
