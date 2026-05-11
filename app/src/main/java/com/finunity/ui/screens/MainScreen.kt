package com.finunity.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    portfolioSummary: PortfolioSummary?,
    isLoading: Boolean,
    error: String? = null,
    onRefresh: () -> Unit,
    onAddAccount: () -> Unit,
    onAddPosition: () -> Unit,
    onAddAssetRecord: (String) -> Unit = {},  // accountId
    onEditAssetRecord: (AssetRecordSummary) -> Unit = {},  // NEW: edit/view asset record
    onEditPosition: (HoldingSummary) -> Unit = {},
    onEditAccount: (Account) -> Unit = {},
    onViewHistory: () -> Unit = {},
    onViewRiskBucketDetail: (Int) -> Unit = {},  // bucket index
    onOpenSettings: () -> Unit = {},  // NEW: open settings screen
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资产总览", fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "历史分析",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPosition,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加", tint = Color.White)
            }
        },
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
                    TextButton(onClick = onRefresh) {
                        Text("重试")
                    }
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
        } else if (portfolioSummary == null || portfolioSummary.totalAssets < 0.0) {
            EmptyState(onAddAccount = onAddAccount, onAddPosition = onAddPosition)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 总资产卡片
                item {
                    AssetOverviewCard(
                        totalAssets = portfolioSummary.totalAssets,
                        riskBuckets = portfolioSummary.riskBuckets,
                        baseCurrency = portfolioSummary.baseCurrency,
                        onRiskBucketClick = onViewRiskBucketDetail
                    )
                }

                // 再平衡提醒
                if (portfolioSummary.needsRebalance && portfolioSummary.rebalanceRecommendations.isNotEmpty()) {
                    item {
                        RebalanceAlertCard(
                            recommendations = portfolioSummary.rebalanceRecommendations,
                            baseCurrency = portfolioSummary.baseCurrency
                        )
                    }
                }

                // 账户总览
                if (portfolioSummary.accounts.isNotEmpty()) {
                    item {
                        Text(
                            text = "账户",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    items(portfolioSummary.accounts) { summary ->
                        AccountItem(
                            summary = summary,
                            baseCurrency = portfolioSummary.baseCurrency,
                            onEdit = { onEditAccount(summary.account) },
                            onAddRecord = { onAddAssetRecord(summary.account.id) }
                        )
                    }
                }

                if (portfolioSummary.holdings.isNotEmpty()) {
                    item {
                        Text(
                            text = "具体持仓",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    items(portfolioSummary.holdings) { holding ->
                        HoldingItem(
                            holding = holding,
                            baseCurrency = portfolioSummary.baseCurrency,
                            onClick = { onEditPosition(holding) }
                        )
                    }
                }

                if (portfolioSummary.assetRecords.isNotEmpty()) {
                    item {
                        Text(
                            text = "资产记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    items(portfolioSummary.assetRecords) { record ->
                        AssetRecordItem(
                            summary = record,
                            baseCurrency = portfolioSummary.baseCurrency,
                            onClick = { onEditAssetRecord(record) }
                        )
                    }
                }

                if (portfolioSummary.positions.isNotEmpty()) {
                    item {
                        Text(
                            text = "按代码汇总",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    items(portfolioSummary.positions) { position ->
                        PositionItem(
                            position = position,
                            baseCurrency = portfolioSummary.baseCurrency
                        )
                    }
                }

                // 更新时间
                item {
                    Text(
                        text = "更新于 ${dateFormat.format(Date(portfolioSummary.lastUpdated))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun EmptyState(onAddAccount: () -> Unit, onAddPosition: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "开始管理你的资产",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "添加账户和持仓，了解你的资产配置",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onAddAccount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加账户")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onAddPosition) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加资产记录")
            }
        }
    }
}

@Composable
fun AssetOverviewCard(
    totalAssets: Double,
    baseCurrency: String,
    riskBuckets: List<RiskBucketSummary>,
    onRiskBucketClick: (Int) -> Unit = {}
) {
    val aggressiveRatio = if (totalAssets > 0) {
        riskBuckets.firstOrNull { it.riskBucket.name == "AGGRESSIVE" }?.percentage ?: 0.0
    } else 0.0
    val animatedRatio by animateFloatAsState(targetValue = aggressiveRatio.toFloat(), label = "ratio")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 总资产
            Text(
                text = "总资产",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatCurrency(totalAssets, baseCurrency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 资产比例圆环
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                RatioRing(
                    ratio = animatedRatio,
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(aggressiveRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "进攻占比",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                riskBuckets.forEachIndexed { index, bucket ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                    AssetItem(
                        label = bucket.riskBucket.displayName(),
                        amount = formatCurrency(bucket.totalValue, baseCurrency),
                        color = Color.White,
                        onClick = { onRiskBucketClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun RebalanceAlertCard(
    recommendations: List<String>,
    baseCurrency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "再平衡建议",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            recommendations.forEach { recommendation ->
                Text(
                    text = "• $recommendation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun AssetRecordItem(
    summary: AssetRecordSummary,
    baseCurrency: String,
    onClick: () -> Unit = {}
) {
    val profitColor = if (summary.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
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
                    text = "${summary.accountName} · ${summary.record.assetType.displayName()} · ${summary.record.riskBucket.displayName()}",
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
                Text(
                    text = "${if (summary.profitLoss >= 0) "+" else ""}${String.format("%.1f", summary.profitLossRatio * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}

@Composable
fun RatioRing(ratio: Float, modifier: Modifier = Modifier) {
    val stockColor = Color.White
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

        // 进度环
        drawArc(
            color = stockColor,
            startAngle = -90f,
            sweepAngle = ratio * 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
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
                    text = "${if (position.profitLoss >= 0) "+" else ""}${String.format("%.1f", position.profitLossRatio * 100)}%",
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
        shape = RoundedCornerShape(16.dp),
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
                    text = "${if (holding.profitLossRatio >= 0) "+" else ""}${String.format("%.1f", holding.profitLossRatio * 100)}%",
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
    onEdit: () -> Unit = {},
    onAddRecord: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    text = "${summary.account.type.name} · ${summary.account.currency}",
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
                IconButton(onClick = onAddRecord) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加记录",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

fun formatCurrency(amount: Double, currency: String): String {
    return when (currency) {
        "CNY" -> "¥${String.format("%.2f", amount)}"
        "USD" -> "$${String.format("%.2f", amount)}"
        "HKD" -> "HK$${String.format("%.2f", amount)}"
        else -> "${currency}${String.format("%.2f", amount)}"
    }
}
