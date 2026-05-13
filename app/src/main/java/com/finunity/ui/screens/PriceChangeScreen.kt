package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.displayName
import com.finunity.ui.components.FinCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PriceChangeScreen(
    records: List<AssetRecordSummary>,
    priceHistory: List<PriceHistory>,
    baseCurrency: String,
    onViewAssetHistory: (String) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf<AssetType?>(null) }
    var sortOption by remember { mutableStateOf(SortOption.BY_VALUE) }
    var showSortSheet by remember { mutableStateOf(false) }
    val historiesByRecord = remember(priceHistory) {
        priceHistory.groupBy { it.recordId }
            .mapValues { (_, histories) -> histories.sortedByDescending { it.timestamp } }
    }
    val visibleRecords = records
        .filter { selectedType == null || it.record.assetType == selectedType }
        .let { filtered ->
            when (sortOption) {
                SortOption.BY_VALUE -> filtered.sortedByDescending { it.currentValue }
                SortOption.BY_CHANGE -> filtered.sortedByDescending { holdingDailyChange(it, historiesByRecord[it.record.id].orEmpty()) }
                SortOption.BY_NAME -> filtered.sortedBy { it.record.name }
            }
        }

    if (showSortSheet) {
        SortOptionSheet(
            currentOption = sortOption,
            onOptionSelected = { sortOption = it; showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }

    Scaffold(
        bottomBar = bottomBar,
        modifier = modifier
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无价格数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    PriceFilterPanel(
                        selectedType = selectedType,
                        onTypeChange = { selectedType = it }
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${visibleRecords.size} 项资产",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        TextButton(onClick = { showSortSheet = true }) {
                            Text(sortOption.displayName())
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "排序",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (visibleRecords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当前筛选下暂无资产",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(visibleRecords, key = { it.record.id }) { summary ->
                    val histories = historiesByRecord[summary.record.id].orEmpty()
                    PriceChangeItem(
                        summary = summary,
                        histories = histories,
                        baseCurrency = baseCurrency,
                        onClick = { onViewAssetHistory(summary.record.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PriceFilterPanel(
    selectedType: AssetType?,
    onTypeChange: (AssetType?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PriceTypeChip(
            text = "全部",
            selected = selectedType == null,
            onClick = { onTypeChange(null) },
            modifier = Modifier.weight(1f)
        )
        listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND, AssetType.CASH, AssetType.TIME_DEPOSIT).forEach { type ->
            PriceTypeChip(
                text = type.displayName(),
                selected = selectedType == type,
                onClick = { onTypeChange(type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PriceTypeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PriceChangeItem(
    summary: AssetRecordSummary,
    histories: List<PriceHistory>,
    baseCurrency: String,
    onClick: () -> Unit
) {
    val latest = histories.firstOrNull()
    val previous = histories.drop(1).firstOrNull()
    val priceDelta = if (latest != null && previous != null) latest.price - previous.price else 0.0
    val priceDeltaRatio = if (previous != null && previous.price > 0) priceDelta / previous.price else 0.0
    val holdingDelta = holdingDailyChange(summary, histories)
    val color = if (holdingDelta >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
    val isCash = summary.record.assetType == AssetType.CASH

    FinCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.record.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${summary.record.assetType.displayName()} · ${summary.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(summary.currentValue, baseCurrency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isCash) "现金" else "${if (holdingDelta >= 0) "+" else ""}${formatCurrency(holdingDelta, baseCurrency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCash) MaterialTheme.colorScheme.onSurfaceVariant else color
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isCash) "" else "净值 ${formatNumber(latest?.price ?: summary.record.currentPrice)} · 份额 ${formatNumber(summary.record.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!isCash) {
                    Text(
                        text = "${if (priceDelta >= 0) "+" else ""}${formatNumber(priceDelta)} (${if (priceDeltaRatio >= 0) "+" else ""}${formatNumber(priceDeltaRatio * 100)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
            if (!isCash && histories.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                PriceSparkline(
                    histories = histories,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                )
            }
        }
    }
}

@Composable
private fun PriceSparkline(
    histories: List<PriceHistory>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val prices = histories.reversed().map { it.price }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 0.0
    val range = maxPrice - minPrice

    Canvas(modifier = modifier) {
        if (prices.size < 2 || range <= 0.0) return@Canvas

        val path = Path()
        val stepX = size.width / (prices.size - 1)
        prices.forEachIndexed { index, price ->
            val x = index * stepX
            val y = size.height - (((price - minPrice) / range) * size.height).toFloat()
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.78f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0.00"
    return String.format("%.2f", value)
}

private fun holdingDailyChange(
    summary: AssetRecordSummary,
    histories: List<PriceHistory>
): Double {
    val latest = histories.firstOrNull()
    val previous = histories.drop(1).firstOrNull()
    val priceDelta = if (latest != null && previous != null) latest.price - previous.price else 0.0
    return priceDelta * summary.record.quantity
}

private enum class SortOption {
    BY_VALUE,
    BY_CHANGE,
    BY_NAME;

    fun displayName() = when (this) {
        BY_VALUE -> "按市值"
        BY_CHANGE -> "按变化"
        BY_NAME -> "按名称"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOptionSheet(
    currentOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            SortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (option == currentOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (option == currentOption) {
                        RadioButton(selected = true, onClick = null)
                    }
                }
            }
        }
    }
}
