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
import com.finunity.data.model.displayName
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

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
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val target = parseTargetAllocation(summary?.targetAllocation ?: "")
    val threshold = summary?.rebalanceThreshold ?: 0.05
    val totalAssets = summary?.totalAssets ?: 0.0
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
        topBar = {
            TopAppBar(
                title = { Text("资产规划", fontWeight = FontWeight.SemiBold) },
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
                                amount = kotlin.math.abs(current - tgt) * totalAssets,
                                baseCurrency = baseCurrency
                            )
                        }
                    }
                }
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

            item { Spacer(modifier = Modifier.height(80.dp)) }
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
