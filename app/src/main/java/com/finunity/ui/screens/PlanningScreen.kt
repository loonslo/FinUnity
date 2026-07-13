package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.DrawdownAdvice
import com.finunity.data.model.RiskAlert
import com.finunity.data.model.RiskAlertLevel
import com.finunity.data.model.displayName
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinTopBar

/**
 * 规划页：回答"资产配置是否偏离目标"，给出调整建议，并提供目标配置与复盘入口。
 * 不做数据录入，只做决策建议。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    portfolioSummary: PortfolioSummary?,
    onBack: () -> Unit,
    onEditTarget: () -> Unit,
    onReview: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onSimulateExpense: () -> Unit = {},
    onOpenLandingPoints: () -> Unit = {},
    onOpenStressTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val target = parseTargetAllocation(summary?.targetAllocation ?: "")
    val threshold = summary?.rebalanceThreshold ?: 0.05
    val strategyAssets = summary?.strategyAssets ?: 0.0
    val baseCurrency = summary?.baseCurrency ?: "CNY"

    // 四象限固定展示顺序
    val order = listOf(
        RiskBucket.AGGRESSIVE,
        RiskBucket.CONSERVATIVE,
        RiskBucket.INSURANCE,
        RiskBucket.CASH
    )

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar("资产规划", onBack) },
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

            // 风险体检（永不满仓 + 落点红线）
            item {
                RiskCheckCard(
                    aggressiveRatio = summary?.aggressiveRatio ?: 0.0,
                    maxAggressiveRatio = summary?.maxAggressiveRatio ?: 0.70,
                    alerts = summary?.riskAlerts ?: emptyList()
                )
            }

            item {
                DrawdownCard(summary?.drawdownAdvice, baseCurrency)
            }

            // 状态摘要
            item {
                val needs = summary?.needsRebalance == true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (needs) "配置已偏离目标" else "配置基本符合目标",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (needs) FinColors.Cash else FinColors.Profit
                        )
                        Text(
                            text = if (needs) "下面列出了偏离较大的部分，可按建议慢慢调整，无需一次到位。"
                            else "各类资产与目标接近，暂时无需调整。可定期复盘保持平衡。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                    }
                }
            }

            // 当前 vs 目标 对比
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("当前 vs 目标", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                            Text("调整目标 ›", style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium, color = FinColors.Accent,
                                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                    .clickable(onClick = onEditTarget)
                                    .padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        order.forEach { bucket ->
                            val current = summary?.allocations?.get(bucket.name) ?: 0.0
                            val tgt = target[bucket.name] ?: 0.0
                            CompareRow(
                                label = bucket.displayName(),
                                current = current,
                                target = tgt,
                                threshold = threshold,
                                color = bucketColor(bucket),
                                amount = kotlin.math.abs(current - tgt) * strategyAssets,
                                baseCurrency = baseCurrency
                            )
                        }
                    }
                }
            }

            // 落点跟踪入口
            item {
                EntryRowCard(
                    title = "落点跟踪",
                    subtitle = "象限之下的每个落点：目标 / 现有 / 缺口 / 停止条件",
                    onClick = onOpenLandingPoints
                )
            }

            // 复盘入口
            item {
                EntryRowCard(
                    title = "月度复盘",
                    subtitle = "看看这段时间资产怎么变了，需不需要调整",
                    onClick = onReview
                )
            }

            item {
                EntryRowCard(
                    title = "资产历史走势",
                    subtitle = "看总资产这段时间怎么变",
                    onClick = onOpenHistory
                )
            }

            // 大额支出模拟入口
            item {
                EntryRowCard(
                    title = "大额支出模拟",
                    subtitle = "买房买车前，先看看会怎么影响你的资产结构",
                    onClick = onSimulateExpense
                )
            }

            item {
                EntryRowCard(
                    title = "压力测试",
                    subtitle = "用历史极端情景，看组合浮亏是否超过承受上限",
                    onClick = onOpenStressTest
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DrawdownCard(advice: DrawdownAdvice?, baseCurrency: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("回撤加仓阶梯", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
            if (advice == null) {
                Text("至少积累 2 天资产快照后，才会根据历史总资产高点给出阶梯建议。",
                    style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
            } else {
                Text("自高点回撤 ${String.format("%.1f", advice.drawdownRatio * 100)}%",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (advice.triggered) FinColors.Cash else FinColors.Number)
                Text("历史高点 ${formatCurrency(advice.peakAssets, baseCurrency)} · ${advice.sampleCount} 个快照样本",
                    style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                if (advice.triggered) {
                    Text("当前触发 -${advice.levelPercent}% 档：${advice.fundingSource}，定投 ${String.format("%.1f", advice.recurringMultiplier)} 倍。",
                        style = MaterialTheme.typography.bodyMedium, color = FinColors.TextPrimary)
                    if (advice.recommendedAmmoAmount > 0.0) {
                        Text("本档可分批使用弹药 ${formatCurrency(advice.recommendedAmmoAmount, baseCurrency)}，优先用于核心宽基。",
                            style = MaterialTheme.typography.bodySmall, color = FinColors.Accent)
                    }
                } else {
                    Text("未到 -5% 触发线，保持常规定投，弹药继续留存。",
                        style = MaterialTheme.typography.bodySmall, color = FinColors.Profit)
                }
            }
        }
    }
}

@Composable
private fun RiskCheckCard(
    aggressiveRatio: Double,
    maxAggressiveRatio: Double,
    alerts: List<RiskAlert>
) {
    val exceeded = maxAggressiveRatio in 0.0..1.0 && aggressiveRatio > maxAggressiveRatio + 1e-9
    val barColor = if (exceeded) FinColors.Loss else FinColors.Aggressive

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("风险体检", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)

            // 风险仓位（进取占比）vs 上限
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("风险仓位（进取）", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
                    Text(
                        text = "${(aggressiveRatio * 100).toInt()}% / 上限 ${(maxAggressiveRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exceeded) FinColors.Loss else FinColors.TextSecondary
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(999.dp)).background(FinColors.Outline)
                ) {
                    // 上限刻度（浅色背景到上限处）
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(maxAggressiveRatio.toFloat().coerceIn(0f, 1f))
                        .background(barColor.copy(alpha = 0.18f)))
                    // 当前仓位
                    Box(modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(aggressiveRatio.toFloat().coerceIn(0f, 1f))
                        .background(barColor))
                }
            }

            if (alerts.isEmpty()) {
                Text("未触及红线，结构健康。", style = MaterialTheme.typography.bodySmall,
                    color = FinColors.Profit)
            } else {
                alerts.forEach { alert ->
                    val color = if (alert.level == RiskAlertLevel.WARNING) FinColors.Loss else FinColors.Accent
                    Row(verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp)
                            .clip(RoundedCornerShape(999.dp)).background(color))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(alert.title, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, color = color)
                            Text(alert.detail, style = MaterialTheme.typography.bodySmall,
                                color = FinColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRowCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = FinColors.TextSecondary)
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    current: Double,
    target: Double,
    threshold: Double,
    color: Color,
    amount: Double,
    baseCurrency: String
) {
    val drift = current - target
    val off = kotlin.math.abs(drift) > threshold
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(color))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
            }
            Text(
                text = "${(current * 100).toInt()}% / 目标 ${(target * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
        }
        // 双层进度条：底=目标，前=当前
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(999.dp)).background(FinColors.Outline)
        ) {
            Box(modifier = Modifier.fillMaxHeight()
                .fillMaxWidth(target.toFloat().coerceIn(0f, 1f))
                .background(color.copy(alpha = 0.3f)))
            Box(modifier = Modifier.fillMaxHeight()
                .fillMaxWidth(current.toFloat().coerceIn(0f, 1f))
                .background(color))
        }
        if (off) {
            Text(
                text = if (drift > 0) "偏高，可考虑减少约 ${formatCurrency(amount, baseCurrency)}"
                else "偏低，可考虑增加约 ${formatCurrency(amount, baseCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (drift > 0) FinColors.Loss else FinColors.Accent
            )
        }
    }
}

private fun bucketColor(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> FinColors.Aggressive
    RiskBucket.CONSERVATIVE -> FinColors.Conservative
    RiskBucket.INSURANCE -> FinColors.Insurance
    RiskBucket.CASH -> FinColors.Cash
}
