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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.finunity.ui.components.FinGreen
import com.finunity.ui.components.FinLine
import com.finunity.ui.components.FinSectionLabel
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    portfolioSummary: PortfolioSummary?,
    isLoading: Boolean,
    error: String? = null,
    onStartAddFlow: () -> Unit,
    onEditAccount: (Account) -> Unit = {},
    onViewHistory: () -> Unit = {},
    onViewRiskBucketDetail: (Int) -> Unit = {},  // bucket index
    onOpenSettings: () -> Unit = {},  // NEW: open settings screen
    onOpenImportCsv: () -> Unit = {},  // NEW: open CSV import screen
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
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
        if (isLoading && portfolioSummary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (portfolioSummary == null || portfolioSummary.totalAssets == 0.0) {
            EmptyState(
                onStartAddFlow = onStartAddFlow,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    AssetOverviewCard(
                        totalAssets = portfolioSummary.totalAssets,
                        riskBuckets = portfolioSummary.riskBuckets,
                        baseCurrency = portfolioSummary.baseCurrency,
                        totalCost = portfolioSummary.assetRecords.sumOf { it.costInBaseCurrency } +
                            portfolioSummary.positions.sumOf { it.totalCost },
                        onRiskBucketClick = onViewRiskBucketDetail,
                        onOpenImportCsv = onOpenImportCsv,
                        onOpenSettings = onOpenSettings,
                        onViewHistory = onViewHistory
                    )
                }

                // 再平衡提醒
                if (portfolioSummary.needsRebalance && portfolioSummary.rebalanceRecommendations.isNotEmpty()) {
                    item {
                        RebalanceAlertCard(
                            portfolioSummary = portfolioSummary
                        )
                    }
                }

                // 账户总览
                if (portfolioSummary.accounts.isNotEmpty()) {
                    item {
                        AccountListCard(
                            accounts = portfolioSummary.accounts,
                            baseCurrency = portfolioSummary.baseCurrency,
                            onEdit = { onEditAccount(it) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun EmptyState(
    onStartAddFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFEAF7EF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "¥",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinGreen.copy(alpha = 0.65f)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "还没有资产记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "先添加一个账户，再记录你的第一笔资产",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStartAddFlow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("添加账户")
            }
        }
    }
}

@Composable
fun AssetOverviewCard(
    totalAssets: Double,
    baseCurrency: String,
    totalCost: Double,
    riskBuckets: List<RiskBucketSummary>,
    onRiskBucketClick: (Int) -> Unit = {},
    onOpenImportCsv: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onViewHistory: () -> Unit = {}
) {
    val cumulativeProfit = totalAssets - totalCost
    val cumulativeRatio = if (totalCost > 0) cumulativeProfit / totalCost else 0.0
    val profitColor = if (cumulativeProfit >= 0) Color(0xFF0F9D58) else Color(0xFFD93025)
    var showMoreMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.6.dp, FinLine.copy(alpha = 0.72f)),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF7FBF8),
                            Color(0xFFEEF7F2)
                        )
                    )
                )
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(120.dp)
            ) {
                drawCircle(
                    color = Color(0xFF166534).copy(alpha = 0.07f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.72f, size.height * 0.70f)
                )
                drawCircle(
                    color = Color(0xFF166534).copy(alpha = 0.05f),
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * 0.42f, size.height * 0.45f)
                )
            }
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
            Text(
                text = "资产结构",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            AllocationDonutSummary(
                riskBuckets = riskBuckets,
                totalAssets = totalAssets,
                baseCurrency = baseCurrency,
                onRiskBucketClick = onRiskBucketClick,
                cumulativeProfitText = "累计 ${if (cumulativeProfit >= 0) "+" else ""}${formatCurrency(cumulativeProfit, baseCurrency)} ${formatSignedPercent(cumulativeRatio)}",
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
                    text = "总资产",
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
                    color = FinLine,
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
            color = Color(0xFFEEF0F3),
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
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${String.format("%.0f", percentage * 100)}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun allocationColor(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> Color(0xFF3D7A5C)
    RiskBucket.CONSERVATIVE -> Color(0xFF8DA7C7)
    RiskBucket.CASH -> Color(0xFFD8B36A)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RebalanceAlertCard(
    portfolioSummary: PortfolioSummary
) {
    var expanded by remember { mutableStateOf(false) }
    val targetAllocation = remember(portfolioSummary.targetAllocation) {
        parseTargetAllocation(portfolioSummary.targetAllocation)
    }
    val adviceItems = targetAllocation.mapNotNull { (key, target) ->
        val current = portfolioSummary.allocations[key] ?: 0.0
        val drift = current - target
        if (kotlin.math.abs(drift) <= portfolioSummary.rebalanceThreshold) {
            null
        } else {
            RebalanceAdvice(
                label = riskBucketLabel(key),
                current = current,
                target = target,
                amount = kotlin.math.abs(drift) * portfolioSummary.totalAssets,
                action = if (drift > 0) "减少" else "增加"
            )
        }
    }
    if (adviceItems.isEmpty()) return
    val firstAdvice = adviceItems.first()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${firstAdvice.label}资产有点${if (firstAdvice.action == "减少") "多" else "少"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "可以考虑${firstAdvice.action} ${formatCurrency(firstAdvice.amount, portfolioSummary.baseCurrency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (expanded) "收起" else "查看方案",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            // 展开的详情
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "资产小建议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                adviceItems.forEach { advice ->
                    RebalanceAdviceRow(
                        advice = advice,
                        baseCurrency = portfolioSummary.baseCurrency
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private data class RebalanceAdvice(
    val label: String,
    val current: Double,
    val target: Double,
    val amount: Double,
    val action: String
)

@Composable
private fun RebalanceAdviceRow(
    advice: RebalanceAdvice,
    baseCurrency: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${advice.label}资产有点${if (advice.action == "减少") "多" else "少"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "当前 ${String.format("%.0f", advice.current * 100)}%，目标 ${String.format("%.0f", advice.target * 100)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "可以考虑${advice.action} ${formatCurrency(advice.amount, baseCurrency)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (advice.action == "减少") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

private fun riskBucketLabel(key: String): String = when (key) {
    "CONSERVATIVE" -> "稳健"
    "AGGRESSIVE" -> "进取"
    "CASH" -> "防守"
    else -> key
}

@Composable
fun AssetRecordItem(
    summary: AssetRecordSummary,
    baseCurrency: String,
    onClick: () -> Unit = {}
) {
    val profitColor = if (summary.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
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
fun RatioRing(
    aggressiveRatio: Float,
    conservativeRatio: Float,
    defensiveRatio: Float,
    modifier: Modifier = Modifier
) {
    val aggressiveColor = Color(0xFFE53935)
    val conservativeColor = Color(0xFF4CAF50)
    val defensiveColor = Color(0xFF2196F3)
    val backgroundColor = Color.White.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // 背景环
        drawCircle(
            color = backgroundColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 三段式进度环
        val total = aggressiveRatio + conservativeRatio + defensiveRatio
        if (total <= 0f) return@Canvas

        var currentAngle = -90f

        // 稳健段 (绿色)
        val conservativeSweep = (conservativeRatio / total) * 360f
        if (conservativeSweep > 0) {
            drawArc(
                color = conservativeColor,
                startAngle = currentAngle,
                sweepAngle = conservativeSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            currentAngle += conservativeSweep
        }

        // 进取段 (红色)
        val aggressiveSweep = (aggressiveRatio / total) * 360f
        if (aggressiveSweep > 0) {
            drawArc(
                color = aggressiveColor,
                startAngle = currentAngle,
                sweepAngle = aggressiveSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            currentAngle += aggressiveSweep
        }

        // 防守段 (蓝色)
        val defensiveSweep = (defensiveRatio / total) * 360f
        if (defensiveSweep > 0) {
            drawArc(
                color = defensiveColor,
                startAngle = currentAngle,
                sweepAngle = defensiveSweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
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
        Color(0xFF00A86B)  // 绿色
    else
        Color(0xFFE53935)   // 红色

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
    val profitColor = if (holding.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

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
