package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.finunity.data.repository.MonthlyChange
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

/**
 * 月度复盘：回顾这段时间资产怎么变、是否偏离目标、要不要调整。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReviewScreen(
    portfolioSummary: PortfolioSummary?,
    monthlyChange: MonthlyChange?,
    onBack: () -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val baseCurrency = summary?.baseCurrency ?: "CNY"
    val target = parseTargetAllocation(summary?.targetAllocation ?: "")
    val threshold = summary?.rebalanceThreshold ?: 0.05
    val totalAssets = summary?.totalAssets ?: 0.0

    val order = listOf(RiskBucket.AGGRESSIVE, RiskBucket.CONSERVATIVE, RiskBucket.INSURANCE, RiskBucket.CASH)
    val driftItems = order.mapNotNull { bucket ->
        val current = summary?.allocations?.get(bucket.name) ?: 0.0
        val tgt = target[bucket.name] ?: 0.0
        val drift = current - tgt
        if (kotlin.math.abs(drift) > threshold) Triple(bucket, drift, kotlin.math.abs(drift) * totalAssets) else null
    }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = {
            TopAppBar(
                title = { Text("月度复盘", fontWeight = FontWeight.SemiBold) },
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

            // 本月变化
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("这段时间", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                        Text(
                            text = "当前总资产 ${formatCurrency(totalAssets, baseCurrency)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = FinColors.Number
                        )
                        if (monthlyChange != null) {
                            val up = monthlyChange.change >= 0
                            Text(
                                text = "较上月 ${if (up) "+" else ""}${formatCurrency(monthlyChange.change, baseCurrency)}" +
                                    " (${if (up) "+" else ""}${String.format("%.1f", monthlyChange.percentageChange)}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (up) FinColors.Profit else FinColors.Loss
                            )
                        } else {
                            Text(
                                text = "还没有足够的历史快照（至少需要一个月、两条记录）。应用每天会自动记录一次，坚持记录后这里会显示变化。",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // 偏离与建议
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("要不要调整", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                        if (driftItems.isEmpty()) {
                            Text("各类资产与目标接近，这个月不用特别调整，保持就好。",
                                style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                        } else {
                            driftItems.forEach { (bucket, drift, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp))
                                            .background(bucketColorReview(bucket)))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${bucket.displayName()}${if (drift > 0) "偏多" else "偏少"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
                                    }
                                    Text(
                                        text = "${if (drift > 0) "减" else "增"} ${formatCurrency(amount, baseCurrency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (drift > 0) FinColors.Loss else FinColors.Accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 调整目标入口
            item {
                Button(
                    onClick = onEditTarget,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = FinShapes.md,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FinColors.SoftGreen, contentColor = FinColors.Number
                    )
                ) {
                    Text("调整目标配置", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.Number)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

private fun bucketColorReview(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> FinColors.Aggressive
    RiskBucket.CONSERVATIVE -> FinColors.Conservative
    RiskBucket.INSURANCE -> FinColors.Insurance
    RiskBucket.CASH -> FinColors.Cash
}
