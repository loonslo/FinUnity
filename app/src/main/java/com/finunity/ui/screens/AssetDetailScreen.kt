package com.finunity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Transaction
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.displayName
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
    onSell: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("概览", "流水", "价格")
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val profitColor = if (summary.profitLoss >= 0) Color(0xFF0F9D58) else Color(0xFFD93025)
    val isCash = summary.record.assetType == AssetType.CASH

    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                if (!isCash) {
                                    Text(formatSignedPercent(summary.profitLossRatio), color = profitColor, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (!isCash) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Text("买入")
                                }
                                OutlinedButton(onClick = onSell, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Text("卖出")
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
                    if (priceHistory.isEmpty()) {
                        item { EmptyDetailText("暂无价格记录") }
                    } else {
                        items(priceHistory, key = { it.id }) { history ->
                            DetailPriceItem(history, dateFormat)
                        }
                    }
                }
            }

            // 调整持仓按钮
            item {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("调整持仓")
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DetailOverview(summary: AssetRecordSummary, baseCurrency: String) {
    val isCash = summary.record.assetType == AssetType.CASH
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow("当前市值", formatCurrency(summary.currentValue, baseCurrency))
            if (!isCash) {
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
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(tx.type.name, fontWeight = FontWeight.Medium)
                Text(dateFormat.format(Date(tx.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${String.format("%.2f", tx.amount)} ${tx.currency}", fontWeight = FontWeight.SemiBold)
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
private fun EmptyDetailText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
