package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.PriceHistory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    priceHistory: List<PriceHistory>,
    assetName: String,
    assetCurrency: String = "USD",  // 资产原币种
    baseCurrency: String = assetCurrency,  // 基准货币（用于汇总展示）
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // 计算统计数据
    val latestPrice = priceHistory.firstOrNull()?.price ?: 0.0
    val latestCost = priceHistory.firstOrNull()?.cost ?: 0.0
    val priceChange = if (priceHistory.size >= 2) {
        priceHistory.first().price - priceHistory.last().price
    } else 0.0
    val priceChangeRatio = if (priceHistory.size >= 2 && priceHistory.last().price > 0) {
        priceChange / priceHistory.last().price
    } else 0.0

    val minPrice = priceHistory.minOfOrNull { it.price } ?: 0.0
    val maxPrice = priceHistory.maxOfOrNull { it.price } ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "$assetName 历史价格",
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        if (priceHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无价格历史",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "价格历史会在下次刷新时自动保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 统计卡片
                item {
                    PriceStatsCard(
                        latestPrice = latestPrice,
                        latestCost = latestCost,
                        priceChange = priceChange,
                        priceChangeRatio = priceChangeRatio,
                        minPrice = minPrice,
                        maxPrice = maxPrice,
                        baseCurrency = baseCurrency
                    )
                }

                // 价格走势图
                if (priceHistory.size >= 2) {
                    item {
                        PriceChartCard(
                            priceHistory = priceHistory,
                            baseCurrency = baseCurrency
                        )
                    }
                }

                // 历史记录列表
                item {
                    Text(
                        text = "历史记录 (${priceHistory.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                items(priceHistory) { history ->
                    PriceHistoryItem(
                        history = history,
                        baseCurrency = baseCurrency,
                        dateFormat = dateFormat,
                        timeFormat = timeFormat
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun PriceStatsCard(
    latestPrice: Double,
    latestCost: Double,
    priceChange: Double,
    priceChangeRatio: Double,
    minPrice: Double,
    maxPrice: Double,
    baseCurrency: String
) {
    val changeColor = if (priceChange >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "最新价格",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${String.format("%.2f", latestPrice)} $baseCurrency",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "买入成本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${String.format("%.2f", latestCost)} $baseCurrency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "价格变化",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (priceChange >= 0) "+" else ""}${String.format("%.2f", priceChange)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "变化率",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (priceChangeRatio >= 0) "+" else ""}${String.format("%.1f", priceChangeRatio * 100)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor
                    )
                }
                Column {
                    Text(
                        text = "历史低价",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = String.format("%.2f", minPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00A86B)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "历史高价",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = String.format("%.2f", maxPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}

@Composable
fun PriceChartCard(
    priceHistory: List<PriceHistory>,
    baseCurrency: String
) {
    val prices = priceHistory.reversed().map { it.price }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val priceRange = maxPrice - minPrice

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "价格走势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (prices.size < 2 || priceRange == 0.0) return@Canvas

                val width = size.width
                val height = size.height
                val stepX = width / (prices.size - 1)

                val path = Path()
                prices.forEachIndexed { index, price ->
                    val x = index * stepX
                    val y = height - ((price - minPrice) / priceRange * height).toFloat()

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF2196F3),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "低价 ${String.format("%.2f", minPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00A86B)
                )
                Text(
                    text = "高价 ${String.format("%.2f", maxPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
fun PriceHistoryItem(
    history: PriceHistory,
    baseCurrency: String,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat
) {
    val profitLoss = history.price - history.cost
    val profitLossRatio = if (history.cost > 0) profitLoss / history.cost else 0.0
    val profitColor = if (profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

    val date = Date(history.timestamp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
            Column {
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = timeFormat.format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%.2f", history.price)} $baseCurrency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "成本: ${String.format("%.2f", history.cost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (profitLoss >= 0) "+" else ""}${String.format("%.2f", profitLoss)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = profitColor
                )
                Text(
                    text = "${if (profitLossRatio >= 0) "+" else ""}${String.format("%.1f", profitLossRatio * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}
