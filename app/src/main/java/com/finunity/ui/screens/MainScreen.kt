package com.finunity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.Account
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.HoldingSummary
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.PositionSummary
import com.finunity.data.model.RiskBucketSummary
import com.finunity.data.model.displayName
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.displayName
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.ui.components.FinCard
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinSectionLabel
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    portfolioSummary: PortfolioSummary?,
    isLoading: Boolean,
    error: String? = null,
    lastPriceUpdated: Long? = null,
    onboarded: Boolean = false,
    onStartAddFlow: () -> Unit,
    onEditAccount: (Account) -> Unit = {},
    onViewRiskBucketDetail: (Int) -> Unit = {},  // bucket index
    onViewAccounts: () -> Unit = {},
    onRefreshPrices: () -> Unit = {},
    onOpenPlanning: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinColors.PageBg,
        modifier = modifier
    ) { padding ->
        // 错误提示
        if (error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        val summary = portfolioSummary
        if (isLoading && summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (!onboarded && (summary == null || summary.accounts.isEmpty())) {
            OnboardingScreen(
                onStart = onStartAddFlow,
                modifier = Modifier.padding(padding)
            )
        } else if (summary == null) {
            // onboarded 但数据尚未就绪，等待加载
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (summary.totalAssets == 0.0) {
            AccountReadyEmptyAssetState(
                portfolioSummary = summary,
                onStartAddFlow = onStartAddFlow,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FinColors.PageBg)
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    AssetOverviewCard(
                        totalAssets = summary.totalAssets,
                        riskBuckets = summary.riskBuckets,
                        baseCurrency = summary.baseCurrency,
                        totalCost = summary.assetRecords.sumOf { it.costInBaseCurrency } +
                            summary.positions.sumOf { it.totalCost },
                        onRiskBucketClick = onViewRiskBucketDetail
                    )
                }

                item {
                    AssuranceCheckCard(
                        portfolioSummary = summary,
                        lastPriceUpdated = lastPriceUpdated,
                        isLoading = isLoading,
                        onRefreshPrices = onRefreshPrices
                    )
                }

                // 规划入口（始终提供，决策动作收敛到规划页）
                item {
                    PlanningEntryCard(
                        needsRebalance = summary.needsRebalance,
                        onClick = onOpenPlanning
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun AccountReadyEmptyAssetState(
    portfolioSummary: PortfolioSummary,
    onStartAddFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FinColors.PageBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "账户已创建",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FinColors.TextPrimary
                    )
                    Text(
                        text = "下一步，把现金、基金、股票或定期存款记录到对应账户里。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinColors.TextSecondary
                    )
                    Button(
                        onClick = onStartAddFlow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = FinShapes.md,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FinColors.SoftGreen,
                            contentColor = FinColors.Number
                        )
                    ) {
                        Text(
                            text = "添加第一笔资产",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FinColors.Number
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun AssuranceCheckCard(
    portfolioSummary: PortfolioSummary,
    lastPriceUpdated: Long?,
    isLoading: Boolean = false,
    onRefreshPrices: () -> Unit = {}
) {
    val dateFormatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val syncText = lastPriceUpdated?.let { "价格记录更新于 ${dateFormatter.format(Date(it))}" }
        ?: "暂无价格历史，当前以手动录入价格为准"
    val rebalanceText = if (portfolioSummary.needsRebalance) {
        "配置已偏离目标，建议查看再平衡方案"
    } else {
        "配置未明显偏离目标，暂时无需调整"
    }
    val sourceText = "本位币 ${portfolioSummary.baseCurrency}；价格来自 Yahoo Finance，现金与手动资产以本地记录为准"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "安心检查",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.TextPrimary
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = FinColors.Accent
                    )
                } else {
                    Text(
                        text = "立即刷新",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = FinColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onRefreshPrices)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            AssuranceCheckRow(
                label = "配置状态",
                text = rebalanceText,
                color = if (portfolioSummary.needsRebalance) FinColors.Cash else FinColors.Profit
            )
            AssuranceCheckRow(
                label = "数据时间",
                text = syncText,
                color = FinColors.Conservative
            )
            AssuranceCheckRow(
                label = "数据来源",
                text = sourceText,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun AssuranceCheckRow(
    label: String,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = FinColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextPrimary
            )
        }
    }
}

@Composable
fun AssetOverviewCard(
    totalAssets: Double,
    baseCurrency: String,
    totalCost: Double,
    riskBuckets: List<RiskBucketSummary>,
    onRiskBucketClick: (Int) -> Unit = {}
) {
    val cumulativeProfit = totalAssets - totalCost
    val cumulativeRatio = if (totalCost > 0) cumulativeProfit / totalCost else 0.0
    val profitColor = if (cumulativeProfit >= 0) FinColors.Profit else FinColors.Loss
    val cumulativeText = "累计收益 ${if (cumulativeProfit >= 0) "+" else ""}${formatCurrency(cumulativeProfit, baseCurrency)} ${formatSignedPercent(cumulativeRatio)}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "资产结构",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                AllocationDonutSummary(
                    riskBuckets = riskBuckets,
                    totalAssets = totalAssets,
                    baseCurrency = baseCurrency,
                    onRiskBucketClick = onRiskBucketClick,
                    cumulativeProfitText = cumulativeText,
                    profitColor = profitColor
                )
            }
        }
    }
}

@Composable
private fun OverviewPill(
    text: String,
    color: Color
) {
    Surface(
        color = Color.White.copy(alpha = 0.86f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AllocationDonutSummary(
    riskBuckets: List<RiskBucketSummary>,
    totalAssets: Double,
    baseCurrency: String,
    onRiskBucketClick: (Int) -> Unit,
    cumulativeProfitText: String? = null,
    profitColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val buckets = riskBuckets.filter { it.percentage > 0.0 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(152.dp)
        ) {
            AllocationDonut(
                riskBuckets = riskBuckets,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(112.dp)
            ) {
                Text(
                    text = "结构",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrency(totalAssets, baseCurrency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (buckets.isEmpty()) {
                AllocationLegendRow(
                    label = "暂无分布",
                    percentage = 0.0,
                    color = FinColors.Outline,
                    onClick = {}
                )
            } else {
                riskBuckets.forEachIndexed { index, bucket ->
                    AllocationLegendRow(
                        label = bucket.riskBucket.displayName(),
                        percentage = bucket.percentage,
                        color = allocationColor(bucket.riskBucket),
                        onClick = { onRiskBucketClick(index) }
                    )
                }
            }
            if (cumulativeProfitText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = cumulativeProfitText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = profitColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AllocationDonut(
    riskBuckets: List<RiskBucketSummary>,
    modifier: Modifier = Modifier
) {
    val nonZeroBuckets = riskBuckets.filter { it.percentage > 0.0 }
    Canvas(modifier = modifier) {
        val strokeWidth = 16.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = FinColors.Outline.copy(alpha = 0.55f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (nonZeroBuckets.isEmpty()) return@Canvas

        var startAngle = -90f
        nonZeroBuckets.forEach { bucket ->
            val sweep = (bucket.percentage * 360f).toFloat()
            drawArc(
                color = allocationColor(bucket.riskBucket),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun AllocationLegendRow(
    label: String,
    percentage: Double,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = riskBucketHint(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${String.format("%.0f", percentage * 100)}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun riskBucketHint(label: String): String = when (label) {
    "防守" -> "要花的钱 · 随时要用"
    "保命" -> "保命的钱 · 应急与保险"
    "稳健" -> "保本的钱 · 1-3 年要用"
    "进取" -> "生钱的钱 · 5 年以上长期"
    "暂无分布" -> "添加资产后显示用途分布"
    else -> "按用途管理这笔钱"
}

@Composable
private fun HomeAccountSummaryCard(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onViewAll: () -> Unit,
    onViewAccount: (Account) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账户",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.TextPrimary
                )
                TextButton(onClick = onViewAll) {
                    Text("查看全部")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (accounts.isEmpty()) {
                Text(
                    text = "暂时还没有账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinColors.TextSecondary
                )
            } else {
                accounts.take(3).forEachIndexed { index, summary ->
                    HomeAccountRow(
                        summary = summary,
                        baseCurrency = baseCurrency,
                        onClick = { onViewAccount(summary.account) }
                    )
                    if (index != accounts.take(3).lastIndex) {
                        Divider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = FinColors.Outline.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAccountRow(
    summary: AccountSummary,
    baseCurrency: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = FinColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${summary.account.type.displayName()} · ${summary.account.currency}",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (summary.balanceInBaseCurrency < 0) FinColors.Loss else FinColors.Number
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = FinColors.TextSecondary
        )
    }
}

private fun allocationColor(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> FinColors.Aggressive
    RiskBucket.CONSERVATIVE -> FinColors.Conservative
    RiskBucket.INSURANCE -> FinColors.Insurance
    RiskBucket.CASH -> FinColors.Cash
}

@Composable
private fun PlanningEntryCard(
    needsRebalance: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "资产规划",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (needsRebalance) "配置已偏离目标，去看看怎么调整"
                    else "对照标普四象限目标，做一次决策检查",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsRebalance) FinColors.Cash else FinColors.TextSecondary
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = FinColors.TextSecondary
            )
        }
    }
}

@Composable
fun AssetRecordItem(
    summary: AssetRecordSummary,
    baseCurrency: String,
    onClick: () -> Unit = {}
) {
    val profitColor = if (summary.profitLoss >= 0) FinColors.Profit else FinColors.Loss
    val isCash = summary.record.assetType == com.finunity.data.local.entity.AssetType.CASH

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        summary.accountName,
                        summary.record.assetType.displayName(),
                        summary.record.riskBucket.displayName().takeIf { summary.record.assetType != com.finunity.data.local.entity.AssetType.CASH }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(summary.currentValue, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isCash) {
                    Text(
                        text = formatSignedPercent(summary.profitLossRatio),
                        style = MaterialTheme.typography.bodySmall,
                        color = profitColor
                    )
                }
            }
        }
    }
}

@Composable
fun AssetItem(label: String, amount: String, color: Color, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.7f)
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
fun PositionItem(
    position: PositionSummary,
    baseCurrency: String,
    onClick: () -> Unit = {}
) {
    val profitColor = if (position.profitLoss >= 0)
        FinColors.Profit
    else
        FinColors.Loss

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 股票代码
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.symbol.take(2),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 股票名称/代码
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${position.totalShares.toInt()} 股 · ${formatCurrency(position.currentPrice, baseCurrency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // 市值和盈亏
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(position.currentValue, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatSignedPercent(position.profitLossRatio),
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}

@Composable
fun HoldingItem(
    holding: HoldingSummary,
    baseCurrency: String,
    onClick: () -> Unit = {}
) {
    val profitColor = if (holding.profitLoss >= 0) FinColors.Profit else FinColors.Loss

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = holding.position.symbol.take(3),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.position.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${holding.accountName} · ${holding.position.shares.toInt()} 股 · ${formatCurrency(holding.currentPrice, baseCurrency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(holding.currentValue, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatSignedPercent(holding.profitLossRatio),
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}

@Composable
fun AccountItem(
    summary: AccountSummary,
    baseCurrency: String,
    onEdit: () -> Unit = {}
) {
    FinCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${summary.account.type.displayName()} · ${summary.account.currency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountListCard(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onEdit: (Account) -> Unit
) {
    FinCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "账户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            accounts.forEach { summary ->
                AccountItemRow(
                    summary = summary,
                    baseCurrency = baseCurrency,
                    onClick = { onEdit(summary.account) }
                )
                if (summary != accounts.last()) {
                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountItemRow(
    summary: AccountSummary,
    baseCurrency: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${summary.account.type.displayName()} · ${summary.account.currency}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Text(
            text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (summary.balanceInBaseCurrency < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatCurrency(amount: Double, currency: String): String {
    if (amount.isNaN() || amount.isInfinite()) return "--"
    val safeAmount = amount
    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(safeAmount)
    return when (currency) {
        "CNY" -> "¥$formatted"
        "USD" -> "\$$formatted"
        "HKD" -> "HK\$$formatted"
        else -> "$currency$formatted"
    }
}

fun formatSignedPercent(value: Double): String {
    val safeValue = if (value.isNaN() || value.isInfinite()) 0.0 else value
    return "${if (safeValue >= 0) "+" else ""}${String.format("%.1f", safeValue * 100)}%"
}
