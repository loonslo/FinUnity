package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
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
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.repository.MonthlyChange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    snapshots: List<AssetSnapshot>,
    monthlyChange: MonthlyChange?,
    baseCurrency: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史分析", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        if (snapshots.isEmpty()) {
            EmptyHistoryState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // 月度变化卡片
                if (monthlyChange != null) {
                    item {
                        MonthlyChangeCard(
                            change = monthlyChange,
                            baseCurrency = baseCurrency
                        )
                    }
                }

                // 累计收益
                item {
                    CumulativeReturnCard(
                        snapshots = snapshots,
                        baseCurrency = baseCurrency
                    )
                }

                // 资产曲线
                if (snapshots.size >= 2) {
                    item {
                        Text(
                            text = "总资产曲线",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    item {
                        AssetChart(
                            snapshots = snapshots.reversed(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }

                // 历史记录列表
                item {
                    Text(
                        text = "历史快照",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(snapshots.take(30)) { snapshot ->
                    SnapshotItem(
                        snapshot = snapshot,
                        dateFormat = fullDateFormat,
                        baseCurrency = baseCurrency
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无历史数据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "资产快照每天自动记录，需要一定时间积累",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun MonthlyChangeCard(
    change: MonthlyChange,
    baseCurrency: String
) {
    val isPositive = change.change >= 0
    val changeColor = if (isPositive) Color(0xFF00A86B) else Color(0xFFE53935)
    val changeSymbol = if (isPositive) "+" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "本月变化",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatCurrency(change.endAssets, baseCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$changeSymbol${formatCurrency(kotlin.math.abs(change.change), baseCurrency)} (${changeSymbol}${String.format("%.1f", change.percentageChange)}%)",
                    style = MaterialTheme.typography.titleMedium,
                    color = changeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "月初: ${formatCurrency(change.startAssets, baseCurrency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
                Text(
                    text = "月末: ${formatCurrency(change.endAssets, baseCurrency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun CumulativeReturnCard(
    snapshots: List<AssetSnapshot>,
    baseCurrency: String
) {
    // snapshots are ordered DESC by timestamp (newest first)
    val oldestSnapshot = snapshots.lastOrNull()
    val newestSnapshot = snapshots.firstOrNull()

    if (oldestSnapshot == null || newestSnapshot == null) return

    val totalReturn = if (oldestSnapshot.totalCost > 0) {
        ((newestSnapshot.totalAssets - oldestSnapshot.totalCost) / oldestSnapshot.totalCost) * 100
    } else 0.0

    val isPositive = totalReturn >= 0
    val returnColor = if (isPositive) Color(0xFF00A86B) else Color(0xFFE53935)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "累计收益率",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))

            val symbol = if (isPositive) "+" else ""
            Text(
                text = "$symbol${String.format("%.2f", totalReturn)}%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = returnColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "总投入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatCurrency(newestSnapshot.totalCost, baseCurrency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "当前总资产",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatCurrency(newestSnapshot.totalAssets, baseCurrency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun AssetChart(
    snapshots: List<AssetSnapshot>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        if (snapshots.size < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "数据点不足",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            val minValue = snapshots.minOfOrNull { it.totalAssets } ?: 0.0
            val maxValue = snapshots.maxOfOrNull { it.totalAssets } ?: 1.0
            val range = (maxValue - minValue).coerceAtLeast(1.0)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (snapshots.size - 1).coerceAtLeast(1)

                val path = Path()
                var isFirst = true

                snapshots.forEachIndexed { index, snapshot ->
                    val x = index * stepX
                    val normalizedY = (snapshot.totalAssets - minValue) / range
                    val y = height - (normalizedY * height * 0.8f + height * 0.1f)

                    if (isFirst) {
                        path.moveTo(x.toFloat(), y.toFloat())
                        isFirst = false
                    } else {
                        path.lineTo(x.toFloat(), y.toFloat())
                    }
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SnapshotItem(
    snapshot: AssetSnapshot,
    dateFormat: SimpleDateFormat,
    baseCurrency: String
) {
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
                    text = dateFormat.format(Date(snapshot.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "股票 ${(snapshot.stockRatio * 100).toInt()}% · 现金 ${((1 - snapshot.stockRatio) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(snapshot.totalAssets, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (snapshot.totalCost > 0) {
                    val return_pct = ((snapshot.totalAssets - snapshot.totalCost) / snapshot.totalCost) * 100
                    val color = if (return_pct >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
                    val symbol = if (return_pct >= 0) "+" else ""
                    Text(
                        text = "$symbol${String.format("%.1f", return_pct)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
        }
    }
}