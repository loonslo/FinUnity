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
import com.finunity.ui.theme.FinChrome
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.components.FinBucketTag
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSettingRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
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
    onRecordTrade: () -> Unit = {},
    onViewTransactions: () -> Unit = {},
    onViewPrices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val profitColor = when {
        summary.profitLoss > 0 -> FinColors.Profit
        summary.profitLoss < 0 -> FinColors.Loss
        else -> FinColors.TextSecondary
    }
    val isTradable = summary.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
    val hideProfit = summary.record.assetType in listOf(
        AssetType.CASH,
        AssetType.TIME_DEPOSIT,
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
                    Button(
                        onClick = onRecordTrade,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = FinColors.Secondary, contentColor = Color.White)
                    ) { Text("记录交易", color = Color.White, fontWeight = FontWeight.SemiBold) }
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
                                    Text(
                                        if (abs(summary.profitLossRatio) > 5.0) "收益率异常 · 请检查成本价" else formatSignedPercent(summary.profitLossRatio),
                                        color = if (abs(summary.profitLossRatio) > 5.0) FinColors.Warning else profitColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { DetailOverview(summary = summary, baseCurrency = baseCurrency, onEdit = onEdit) }
            item { RecentTransactionsSection(transactions, dateFormat, onViewTransactions) }
            item { RecentPriceSection(summary, priceHistory, onViewPrices) }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailOverview(summary: AssetRecordSummary, baseCurrency: String, onEdit: () -> Unit) {
    val hideProfit = summary.record.assetType in listOf(
        AssetType.CASH,
        AssetType.TIME_DEPOSIT,
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
                FinSettingRow("收益率", if (abs(summary.profitLossRatio) > 5.0) "异常，请检查成本价" else formatSignedPercent(summary.profitLossRatio))
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
private fun RecentTransactionsSection(
    transactions: List<Transaction>,
    dateFormat: SimpleDateFormat,
    onViewAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("最近交易", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (transactions.isNotEmpty()) {
                TextButton(onClick = onViewAll) { Text("查看全部") }
            }
        }
        if (transactions.isEmpty()) {
            EmptyDetailText("暂无交易记录")
        } else {
            transactions.take(3).forEach { tx -> DetailTransactionItem(tx, dateFormat) }
        }
    }
}

@Composable
private fun RecentPriceSection(
    summary: AssetRecordSummary,
    priceHistory: List<PriceHistory>,
    onViewAll: () -> Unit
) {
    val tradable = summary.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
    if (!tradable) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("最近行情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (priceHistory.isNotEmpty()) TextButton(onClick = onViewAll) { Text("查看全部") }
        }
        when {
            priceHistory.size >= 2 -> {
                val latest = priceHistory.maxByOrNull { it.timestamp }!!
                val previous = priceHistory.filter { it.timestamp < latest.timestamp }.maxByOrNull { it.timestamp }
                val delta = previous?.let { latest.price - it.price }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最新 ${formatCurrency(latest.price, summary.record.currency)}", fontWeight = FontWeight.SemiBold)
                    if (delta != null) Text("较上次 ${formatSignedMoney(delta, summary.record.currency)}", color = if (delta >= 0) FinColors.Profit else FinColors.Loss)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.md,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, FinChrome.CardBorder)
                ) { PriceTrendChart(priceHistory, summary.record.currency) }
            }
            priceHistory.size == 1 -> EmptyDetailText("暂无足够历史数据")
            else -> EmptyDetailText("暂无价格记录")
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
        border = BorderStroke(1.dp, FinChrome.CardBorder),
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
        border = BorderStroke(1.dp, FinChrome.CardBorder)
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
        val lineColor = FinColors.Accent
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
                        color = lineColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 画数据点
                for (point in points) {
                    drawCircle(
                        color = lineColor,
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
