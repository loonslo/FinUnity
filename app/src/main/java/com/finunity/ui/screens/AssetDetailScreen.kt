package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Transaction
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.displayName
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.components.FinBucketTag
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSettingRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    summary: AssetRecordSummary,
    priceHistory: List<PriceHistory>,
    transactions: List<Transaction>,
    baseCurrency: String,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    onBuy: () -> Unit = {},
    onSell: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabs = listOf("概览", "流水", "价格")
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val profitColor = when {
        summary.profitLoss > 0 -> FinColors.Profit
        summary.profitLoss < 0 -> FinColors.Loss
        else -> FinColors.TextSecondary
    }
    val isTradable = summary.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
    val hideProfit = summary.record.assetType in listOf(
        AssetType.CASH,
        AssetType.REAL_ESTATE,
        AssetType.VEHICLE,
        AssetType.INSURANCE_POLICY
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除资产") },
            text = { Text("将删除「${summary.record.name}」，并移除流水 ${transactions.size} 条、价格历史 ${priceHistory.size} 条。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FinTopBar(summary.record.name, onBack, actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑资料") },
                                onClick = { menuExpanded = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text("删除资产", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; showDeleteDialog = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isTradable) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onBuy,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = FinColors.Profit, contentColor = Color.White)
                        ) { Text("买入", color = Color.White, fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = onSell,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = FinColors.Loss, contentColor = Color.White)
                        ) { Text("卖出", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                FinCard {
                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(summary.record.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${summary.accountName} · ${summary.record.assetType.displayName()} · ${summary.record.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FinColors.TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatCurrency(summary.currentValue, baseCurrency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                if (!hideProfit) {
                                    Text(formatSignedPercent(summary.profitLossRatio), color = profitColor, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
            when (selectedTab) {
                0 -> item {
                    DetailOverview(summary = summary, baseCurrency = baseCurrency, onEdit = onEdit)
                }
                1 -> {
                    if (transactions.isEmpty()) {
                        item { EmptyDetailText("暂无流水") }
                    } else {
                        items(transactions, key = { it.id }) { tx ->
                            DetailTransactionItem(tx, dateFormat)
                        }
                    }
                }
                2 -> {
                    val tradable = summary.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
                    if (tradable && priceHistory.size >= 2) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = FinShapes.md,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                PriceTrendChart(priceHistory, summary.record.currency)
                            }
                        }
                    } else if (tradable && priceHistory.size in 1..1) {
                        item { EmptyDetailText("积累几天价格后展示趋势") }
                    }
                    if (priceHistory.isEmpty()) {
                        item { EmptyDetailText("暂无价格记录") }
                    } else {
                        items(priceHistory, key = { it.id }) { history ->
                            DetailPriceItem(history, dateFormat, summary.record.currency)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailOverview(summary: AssetRecordSummary, baseCurrency: String, onEdit: () -> Unit) {
    val hideProfit = summary.record.assetType in listOf(
        AssetType.CASH,
        AssetType.REAL_ESTATE,
        AssetType.VEHICLE,
        AssetType.INSURANCE_POLICY
    )
    FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
        Column {
            FinSettingRow("当前总值", formatCurrency(summary.currentValue, baseCurrency))
            FinSettingRow("总成本", formatCurrency(summary.costInBaseCurrency, baseCurrency))
            if (!hideProfit) {
                FinSettingRow("累计盈亏", formatSignedMoney(summary.profitLoss, baseCurrency))
                FinSettingRow("收益率", formatSignedPercent(summary.profitLossRatio))
            }
            val bucketColor = when (summary.record.riskBucket) {
                com.finunity.data.local.entity.RiskBucket.AGGRESSIVE -> FinColors.Aggressive
                com.finunity.data.local.entity.RiskBucket.BALANCED -> FinColors.Conservative
                com.finunity.data.local.entity.RiskBucket.DEFENSIVE -> FinColors.Cash
            }
            FinSettingRow("策略桶", "", onClick = onEdit, valueContent = {
                FinBucketTag(summary.record.riskBucket.displayName(), bucketColor)
            })
            FinSettingRow("持有数量", String.format(Locale.US, "%.4f", summary.record.quantity).trimEnd('0').trimEnd('.'))
            FinSettingRow("单位成本", formatCurrency(summary.record.averageCost, summary.record.currency))
            FinSettingRow("当前单价", formatCurrency(summary.record.currentPrice, summary.record.currency), showDivider = false)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailBucketRow(label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("策略桶", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FinBucketTag(label, color)
    }
}

@Composable
private fun DetailTransactionItem(tx: Transaction, dateFormat: SimpleDateFormat) {
    val type = tx.type
    val label: String
    val labelColor: Color
    when (type) {
        com.finunity.data.local.entity.TransactionType.BUY -> { label = "买入"; labelColor = FinColors.Profit }
        com.finunity.data.local.entity.TransactionType.SELL -> { label = "卖出"; labelColor = FinColors.Loss }
        com.finunity.data.local.entity.TransactionType.DIVIDEND -> { label = "分红"; labelColor = FinColors.Profit }
        com.finunity.data.local.entity.TransactionType.FEE -> { label = "手续费"; labelColor = FinColors.TextSecondary }
        com.finunity.data.local.entity.TransactionType.TRANSFER_IN -> { label = "转入"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.TRANSFER_OUT -> { label = "转出"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.DEPOSIT -> { label = "入金"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.WITHDRAW -> { label = "出金"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.LIABILITY_PAYMENT -> { label = "还款"; labelColor = FinColors.Loss }
    }
    val qtyPriceLine = if (tx.shares != null && tx.shares > 0 && tx.price != null && tx.price > 0) {
        "${String.format(Locale.US, "%.4f", tx.shares).trimEnd('0').trimEnd('.')} 份 · 单价 ${formatCurrency(tx.price, tx.currency)}"
    } else null

    Card(
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = labelColor)
                if (qtyPriceLine != null) {
                    Text(qtyPriceLine, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                }
                Text(dateFormat.format(Date(tx.timestamp)), style = MaterialTheme.typography.labelSmall, color = FinColors.TextTertiary)
            }
            val signedAmount = when (tx.type) {
                com.finunity.data.local.entity.TransactionType.BUY -> "-${formatCurrency(tx.amount, tx.currency)}"
                com.finunity.data.local.entity.TransactionType.SELL -> "+${formatCurrency(tx.amount, tx.currency)}"
                com.finunity.data.local.entity.TransactionType.LIABILITY_PAYMENT -> "-${formatCurrency(tx.amount, tx.currency)}"
                else -> formatCurrency(tx.amount, tx.currency)
            }
            Text(
                signedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    tx.amount == 0.0 -> FinColors.TextSecondary
                    type == com.finunity.data.local.entity.TransactionType.BUY -> FinColors.Profit
                    type == com.finunity.data.local.entity.TransactionType.SELL -> FinColors.Loss
                    else -> FinColors.Number
                }
            )
        }
    }
}

@Composable
private fun DetailPriceItem(history: PriceHistory, dateFormat: SimpleDateFormat, currency: String) {
    Card(
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateFormat.format(Date(history.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCurrency(history.price, currency), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PriceTrendChart(history: List<PriceHistory>, currency: String) {
    val sorted = remember(history) { history.sortedBy { it.timestamp } }
    val prices = remember(sorted) { sorted.map { it.price.toFloat() } }
    val priceMin = remember(prices) { prices.minOrNull() ?: 0f }
    val priceMax = remember(prices) { prices.maxOrNull() ?: 0f }
    val priceRange = priceMax - priceMin

    Column(modifier = Modifier.padding(16.dp)) {
        // 顶部价格区间
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最高 ${formatCurrency(priceMax.toDouble(), currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
            Text(
                text = "最低 ${formatCurrency(priceMin.toDouble(), currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 折线图
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val width = size.width
            val height = size.height
            val padding = 4f
            val chartHeight = height - padding * 2

            if (prices.size >= 2) {
                val points = if (priceRange > 0f) {
                    prices.mapIndexed { index, price ->
                        val x = if (prices.size > 1) index.toFloat() / (prices.size - 1) * (width - padding * 2) + padding else width / 2f
                        val y = chartHeight - ((price - priceMin) / priceRange) * chartHeight + padding
                        Offset(x, y)
                    }
                } else {
                    // 所有价格相等，画居中水平线
                    val midY = height / 2f
                    prices.mapIndexed { index, _ ->
                        val x = if (prices.size > 1) index.toFloat() / (prices.size - 1) * (width - padding * 2) + padding else width / 2f
                        Offset(x, midY)
                    }
                }

                // 画折线
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = FinColors.Accent,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 画数据点
                for (point in points) {
                    drawCircle(
                        color = FinColors.Accent,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDetailText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
