package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    onBuy: () -> Unit = {},
    onSell: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("概览", "流水", "价格")
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val profitColor = if (summary.profitLoss >= 0) FinColors.Profit else FinColors.Loss
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
            text = { Text("将删除「${summary.record.name}」及其交易流水与价格历史，且不可恢复。确定删除吗？") },
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
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (isTradable) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onBuy,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FinColors.SoftGreen, contentColor = FinColors.Number)
                        ) { Text("买入", color = FinColors.Number, fontWeight = FontWeight.SemiBold) }
                        OutlinedButton(
                            onClick = onSell,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("卖出", color = FinColors.TextPrimary, fontWeight = FontWeight.SemiBold) }
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(summary.record.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
                    DetailOverview(summary = summary, baseCurrency = baseCurrency)
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
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            DetailPriceItem(history, dateFormat)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailOverview(summary: AssetRecordSummary, baseCurrency: String) {
    val hideProfit = summary.record.assetType in listOf(
        AssetType.CASH,
        AssetType.REAL_ESTATE,
        AssetType.VEHICLE,
        AssetType.INSURANCE_POLICY
    )
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow("当前市值", formatCurrency(summary.currentValue, baseCurrency))
            if (!hideProfit) {
                DetailRow("买入成本", formatCurrency(summary.costInBaseCurrency, baseCurrency))
                DetailRow("累计盈亏", "${if (summary.profitLoss >= 0) "+" else ""}${formatCurrency(summary.profitLoss, baseCurrency)}")
                DetailRow("收益率", formatSignedPercent(summary.profitLossRatio))
            }
            DetailRow("持有数量", String.format("%.4f", summary.record.quantity).trimEnd('0').trimEnd('.'))
            DetailRow("最新价", "${String.format("%.2f", summary.record.currentPrice)} ${summary.record.currency}")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailTransactionItem(tx: Transaction, dateFormat: SimpleDateFormat) {
    val type = tx.type
    val label: String
    val labelColor: Color
    when (type) {
        com.finunity.data.local.entity.TransactionType.BUY -> { label = "买入"; labelColor = FinColors.Accent }
        com.finunity.data.local.entity.TransactionType.SELL -> { label = "卖出"; labelColor = FinColors.Loss }
        com.finunity.data.local.entity.TransactionType.DIVIDEND -> { label = "分红"; labelColor = FinColors.Profit }
        com.finunity.data.local.entity.TransactionType.FEE -> { label = "手续费"; labelColor = FinColors.TextSecondary }
        com.finunity.data.local.entity.TransactionType.TRANSFER_IN -> { label = "转入"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.TRANSFER_OUT -> { label = "转出"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.DEPOSIT -> { label = "入金"; labelColor = FinColors.TextPrimary }
        com.finunity.data.local.entity.TransactionType.WITHDRAW -> { label = "出金"; labelColor = FinColors.TextPrimary }
    }
    val qtyPriceLine = if (tx.shares != null && tx.shares > 0 && tx.price != null && tx.price > 0) {
        "${String.format("%.4f", tx.shares).trimEnd('0').trimEnd('.')} 份 · 单价 ${String.format("%.2f", tx.price)}"
    } else null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
            Text(
                "${String.format("%.2f", tx.amount)} ${tx.currency}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FinColors.Number
            )
        }
    }
}

@Composable
private fun DetailPriceItem(history: PriceHistory, dateFormat: SimpleDateFormat) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateFormat.format(Date(history.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format("%.2f", history.price), fontWeight = FontWeight.SemiBold)
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
                text = "最高 ${String.format("%.2f", priceMax)} $currency",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
            Text(
                text = "最低 ${String.format("%.2f", priceMin)} $currency",
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
