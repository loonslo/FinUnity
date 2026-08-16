package com.finunity.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PrototypeTab { Overview, Holdings, Flows, Allocation, Accounts }

enum class PrototypeBucket(val label: String, val color: Color, val description: String) {
    DEFENSIVE("防守", FinColors.Cash, "要花的钱 · 随时要用"),
    BALANCED("稳健", FinColors.Conservative, "保本的钱 · 1-3 年要用"),
    AGGRESSIVE("进攻", FinColors.Aggressive, "生钱的钱 · 长期持有")
}

private data class PrototypeHolding(
    val key: String,
    val name: String,
    val value: Double,
    val cost: Double,
    val accountCount: Int,
    val rawCount: Int,
    val bucket: PrototypeBucket
) {
    val profitLoss: Double get() = value - cost
    val profitRatio: Double get() = if (cost > 0) profitLoss / cost else 0.0
}

data class OcrHolding(
    val name: String,
    val securityCode: String,
    val quantity: Double,
    val marketValue: Double,
    val needsReview: Boolean = false
)

private fun prototypeBucket(bucket: RiskBucket): PrototypeBucket = when (bucket) {
    RiskBucket.CASH -> PrototypeBucket.DEFENSIVE
    RiskBucket.AGGRESSIVE -> PrototypeBucket.AGGRESSIVE
    RiskBucket.CONSERVATIVE, RiskBucket.INSURANCE -> PrototypeBucket.BALANCED
}

private fun buildPrototypeHoldings(summary: PortfolioSummary): List<PrototypeHolding> {
    return summary.mergedHoldings.map { item ->
        PrototypeHolding(
            key = item.code,
            name = item.displayName,
            value = item.currentValue,
            cost = item.totalCost,
            accountCount = item.accountCount,
            rawCount = item.sourceCount,
            bucket = prototypeBucket(item.riskBucket)
        )
    }
}

private fun prototypeAllocations(summary: PortfolioSummary): Map<PrototypeBucket, Double> {
    val values = summary.riskBuckets.associate { it.riskBucket to it.totalValue }
    val total = summary.totalAssets
    if (total <= 0.0) return PrototypeBucket.entries.associateWith { 0.0 }
    return mapOf(
        PrototypeBucket.DEFENSIVE to (values[RiskBucket.CASH] ?: 0.0) / total,
        PrototypeBucket.BALANCED to ((values[RiskBucket.CONSERVATIVE] ?: 0.0) + (values[RiskBucket.INSURANCE] ?: 0.0)) / total,
        PrototypeBucket.AGGRESSIVE to (values[RiskBucket.AGGRESSIVE] ?: 0.0) / total
    )
}

private fun prototypeTargets(targetAllocation: String): Map<PrototypeBucket, Float> {
    val target = parseTargetAllocation(targetAllocation)
    return mapOf(
        PrototypeBucket.DEFENSIVE to (target["CASH"] ?: 0.0).toFloat(),
        PrototypeBucket.BALANCED to ((target["CONSERVATIVE"] ?: 0.0) + (target["INSURANCE"] ?: 0.0)).toFloat(),
        PrototypeBucket.AGGRESSIVE to (target["AGGRESSIVE"] ?: 0.0).toFloat()
    )
}

private fun money(value: Double, currency: String): String = formatCurrency(value, currency)

private fun signedMoney(value: Double, currency: String): String =
    (if (value >= 0) "+" else "") + money(value, currency)

private fun signedPercent(value: Double): String =
    (if (value >= 0) "+" else "") + String.format(Locale.US, "%.1f%%", value * 100)

@Composable
private fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FinColors.TextPrimary,
    size: Int = 12,
    weight: FontWeight = FontWeight.Normal
) {
    Text(text, modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, fontWeight = weight)
}

@Composable
private fun PrototypeCard(
    modifier: Modifier = Modifier,
    color: Color = FinColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(modifier = Modifier.padding(14.dp), content = content) }
}

@Composable
fun PrototypeBottomBar(
    selected: PrototypeTab,
    onSelect: (PrototypeTab) -> Unit
) {
    Surface(color = FinColors.Surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PrototypeTabItem(PrototypeTab.Overview, "总览", Icons.Default.Home, selected, onSelect)
            PrototypeTabItem(PrototypeTab.Holdings, "持仓", Icons.Default.List, selected, onSelect)
            PrototypeTabItem(PrototypeTab.Flows, "流水", Icons.Default.DateRange, selected, onSelect)
            PrototypeTabItem(PrototypeTab.Allocation, "配置", Icons.Default.DateRange, selected, onSelect)
            PrototypeTabItem(PrototypeTab.Accounts, "账户", Icons.Default.Person, selected, onSelect)
        }
    }
}

@Composable
private fun PrototypeTabItem(
    tab: PrototypeTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: PrototypeTab,
    onSelect: (PrototypeTab) -> Unit
) {
    val active = tab == selected
    Column(
        modifier = Modifier.weight(1f).clip(FinShapes.sm).clickable { onSelect(tab) }.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = if (active) FinColors.TextPrimary else FinColors.TextSecondary)
        Text(label, fontSize = 10.sp, color = if (active) FinColors.TextPrimary else FinColors.TextSecondary)
        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (active) Color.White else Color.Transparent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeOverviewScreen(
    portfolioSummary: PortfolioSummary?,
    isLoading: Boolean,
    lastPriceUpdated: Long?,
    onStartAddFlow: () -> Unit,
    onRefreshPrices: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = FinColors.PageBg,
        bottomBar = bottomBar
    ) { padding ->
        val summary = portfolioSummary
        if (isLoading && summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FinColors.Secondary)
            }
        } else if (summary == null || summary.totalAssets <= 0.0) {
            EmptyPrototypeOverview(onStartAddFlow, Modifier.padding(padding))
        } else {
            val allocations = prototypeAllocations(summary)
            val targets = prototypeTargets(summary.targetAllocation)
            val holdings = buildPrototypeHoldings(summary)
            val threshold = summary.rebalanceThreshold
            val drift = allocations.mapValues { (bucket, actual) -> actual - (targets[bucket] ?: 0f) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OverviewHeader(lastPriceUpdated, isLoading, onRefreshPrices)
                }
                item {
                    TotalAssetCard(summary, portfolioSummary.accounts.size)
                }
                if (drift.values.any { abs(it) > threshold }) {
                    item { DriftWarningCard(drift, threshold, summary.totalAssets, summary.baseCurrency) }
                }
                item {
                    AllocationCard(allocations, targets, holdings.size)
                }
                if (holdings.isNotEmpty()) {
                    item { SectionCaption("市值 TOP 3") }
                    items(holdings.take(3), key = { it.key }) { holding ->
                        CompactHoldingRow(holding, summary.baseCurrency)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyPrototypeOverview(onStartAddFlow: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("把散落的持仓，汇成一张总账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("连接账户或手动录入第一笔资产，衡仓会按证券编码自动合并。", color = FinColors.TextSecondary)
        Spacer(Modifier.height(22.dp))
        Button(onClick = onStartAddFlow, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("添加账户 / 持仓", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverviewHeader(lastPriceUpdated: Long?, isLoading: Boolean, onRefreshPrices: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("总览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val time = lastPriceUpdated?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "暂无"
        Row(
            modifier = Modifier.clip(CircleShape).clickable(onClick = onRefreshPrices).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (lastPriceUpdated == null) FinColors.Cash else FinColors.Loss))
            Spacer(Modifier.width(5.dp))
            Text(if (isLoading) "同步中" else "已同步 $time", fontSize = 10.sp, color = FinColors.TextSecondary)
        }
    }
}

@Composable
private fun TotalAssetCard(summary: PortfolioSummary, accountCount: Int) {
    val cost = summary.assetRecords.sumOf { it.costInBaseCurrency } + summary.positions.sumOf { it.totalCost }
    val cumulative = summary.totalAssets - cost
    PrototypeCard(color = Color(0xFF20263A)) {
        Text("总资产（$accountCount 账户合并）", color = FinColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        MonoText(money(summary.totalAssets, summary.baseCurrency), size = 29, weight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MetricCell("今日收益", signedMoney(summary.todayChange, summary.baseCurrency), FinColors.Profit)
            MetricCell("今日收益率", signedPercent(summary.todayChangeRatio), FinColors.Profit)
            MetricCell("累计收益", signedMoney(cumulative, summary.baseCurrency), if (cumulative >= 0) FinColors.Profit else FinColors.Loss)
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, color = FinColors.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        MonoText(value, color = color, size = 11, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun DriftWarningCard(drift: Map<PrototypeBucket, Float>, threshold: Double, total: Double, currency: String) {
    val bad = drift.filterValues { abs(it) > threshold }.entries
    val aggressive = drift[PrototypeBucket.AGGRESSIVE] ?: 0f
    val advice = if (aggressive > threshold) {
        "减持进攻型约 ${money(total * aggressive, currency)}，转入欠配桶"
    } else "按目标比例进行再平衡"
    PrototypeCard(color = Color(0xFF3A2029)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp).clip(FinShapes.sm).background(FinColors.Profit.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = FinColors.Profit, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("配置偏移提醒 · 阈值 ±${(threshold * 100).roundToInt()}%", color = Color(0xFFFF8589), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    bad.joinToString("，") { (bucket, value) -> "${bucket.label}${if (value > 0) "超配" else "欠配"} ${signedPercent(value.toDouble())}" } + "：$advice",
                    color = FinColors.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AllocationCard(actual: Map<PrototypeBucket, Double>, target: Map<PrototypeBucket, Float>, holdingCount: Int) {
    PrototypeCard {
        Text("策略配置占比", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            "彩环 = 实际 · 白刻度 = 目标 ${target[PrototypeBucket.DEFENSIVE]?.times(100)?.roundToInt() ?: 0}/${target[PrototypeBucket.BALANCED]?.times(100)?.roundToInt() ?: 0}/${target[PrototypeBucket.AGGRESSIVE]?.times(100)?.roundToInt() ?: 0}",
            color = FinColors.TextSecondary,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                AllocationDonut(actual, target, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MonoText("$holdingCount 项", size = 14, weight = FontWeight.Bold)
                    Text("合并后持仓", color = FinColors.TextSecondary, fontSize = 9.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PrototypeBucket.entries.forEach { bucket ->
                    val value = actual[bucket] ?: 0.0
                    val goal = target[bucket] ?: 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(bucket.color))
                        Spacer(Modifier.width(6.dp))
                        Text(bucket.label, color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(28.dp))
                        MonoText(String.format(Locale.US, "%.1f%%", value * 100), size = 11, weight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        MonoText(signedPercent(value - goal), color = if (abs(value - goal) > 0.03) FinColors.Profit else FinColors.TextSecondary, size = 10)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocationDonut(actual: Map<PrototypeBucket, Double>, target: Map<PrototypeBucket, Float>, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 13.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        var start = -90f
        PrototypeBucket.entries.forEach { bucket ->
            val sweep = ((actual[bucket] ?: 0.0) * 360f).toFloat()
            if (sweep > 0f) drawArc(bucket.color, start, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
        var accumulated = 0f
        PrototypeBucket.entries.forEach { bucket ->
            accumulated += target[bucket] ?: 0f
            val angle = Math.toRadians((accumulated * 360f - 90f).toDouble())
            val center = Offset(size.width / 2, size.height / 2)
            val inner = diameter / 2 - stroke / 2 - 3.dp.toPx()
            val outer = diameter / 2 + stroke / 2 + 3.dp.toPx()
            drawLine(Color.White, center + Offset((kotlin.math.cos(angle) * inner).toFloat(), (kotlin.math.sin(angle) * inner).toFloat()), center + Offset((kotlin.math.cos(angle) * outer).toFloat(), (kotlin.math.sin(angle) * outer).toFloat()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(text, color = FinColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp, bottom = 0.dp))
}

@Composable
private fun CompactHoldingRow(holding: PrototypeHolding, currency: String) {
    PrototypeCard(modifier = Modifier.padding(bottom = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(holding.bucket.color))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(holding.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MonoText("${holding.key}${if (holding.accountCount > 1) " · ${holding.accountCount} 账户合并" else ""}", color = FinColors.TextSecondary, size = 9)
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoText(money(holding.value, currency), size = 12, weight = FontWeight.SemiBold)
                MonoText(signedPercent(holding.profitRatio), color = if (holding.profitLoss >= 0) FinColors.Profit else FinColors.Loss, size = 10)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeHoldingsScreen(
    portfolioSummary: PortfolioSummary?,
    onOpenAsset: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        val summary = portfolioSummary
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FinColors.Secondary) }
            return@Scaffold
        }
        val holdings = buildPrototypeHoldings(summary)
        var selected by remember { mutableStateOf<PrototypeBucket?>(null) }
        val filtered = holdings.filter { selected == null || it.bucket == selected }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("持仓", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("${summary.accounts.size} 账户 · ${holdings.sumOf { it.rawCount }} 笔 → 合并为 ${holdings.size} 项", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                HoldingSummaryCard(holdings, summary)
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrototypeChip("全部", selected == null) { selected = null }
                    PrototypeBucket.entries.forEach { bucket -> PrototypeChip(bucket.label, selected == bucket) { selected = if (selected == bucket) null else bucket } }
                }
            }
            items(filtered, key = { it.key }) { holding ->
                HoldingListRow(holding, summary.baseCurrency, holdings.sumOf { it.value }, onClick = { onOpenAsset(holding.key) })
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HoldingSummaryCard(holdings: List<PrototypeHolding>, summary: PortfolioSummary) {
    val totalCost = holdings.sumOf { it.cost }
    val totalProfit = holdings.sumOf { it.profitLoss }
    PrototypeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryMetric(money(holdings.sumOf { it.value }, summary.baseCurrency), "总市值")
            SummaryMetric(signedPercent(if (totalCost > 0) totalProfit / totalCost else 0.0), "累计收益率", if (totalProfit >= 0) FinColors.Profit else FinColors.Loss)
            SummaryMetric(holdings.size.toString(), "持仓项")
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, color: Color = FinColors.TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        MonoText(value, color = color, size = 14, weight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = FinColors.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun HoldingListRow(holding: PrototypeHolding, currency: String, totalValue: Double, onClick: () -> Unit) {
    PrototypeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(holding.bucket.color))
            Spacer(Modifier.width(8.dp))
            Text(holding.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(7.dp))
            MonoText(holding.key, color = FinColors.TextSecondary, size = 9)
            if (holding.accountCount > 1) {
                Spacer(Modifier.weight(1f))
                Surface(color = Color(0xFF26385F), shape = RoundedCornerShape(6.dp)) { Text("${holding.accountCount} 账户合并", color = FinColors.Secondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("市值（元）", color = FinColors.TextSecondary, fontSize = 9.sp)
                MonoText(money(holding.value, currency), size = 14, weight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoText(signedPercent(holding.profitRatio), color = if (holding.profitLoss >= 0) FinColors.Profit else FinColors.Loss, size = 12, weight = FontWeight.SemiBold)
                Text("${holding.bucket.label}型", color = FinColors.TextSecondary, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth((if (totalValue > 0) holding.value / totalValue else 0.0).toFloat().coerceIn(0.03f, 1f)).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(holding.bucket.color))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(String.format(Locale.US, "占组合 %.1f%%", if (totalValue > 0) holding.value / totalValue * 100 else 0.0), color = FinColors.TextSecondary, fontSize = 9.sp)
            Text(if (holding.rawCount > 1) "${holding.rawCount} 笔归集" else "单笔持仓", color = FinColors.TextSecondary, fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeFlowsScreen(
    transactions: List<Transaction>,
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onRecordTrade: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf<TransactionType?>(null) }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val accountNames = remember(accounts) { accounts.associate { it.account.id to it.account.name } }
    // 初始录入产生的审计 BUY 流水不算“记一笔”交易，避免把导入持仓误计为本月买入。
    val trades = transactions.filter {
        (it.type == TransactionType.BUY || it.type == TransactionType.SELL) &&
            !it.note.orEmpty().startsWith("录入 ")
    }
    val filtered = trades.filter { filter == null || it.type == filter }
    val currentMonth = monthFormat.format(Date())
    val currentMonthTrades = trades.filter { monthFormat.format(Date(it.timestamp)) == currentMonth }
    val bought = currentMonthTrades.filter { it.type == TransactionType.BUY }.sumOf { it.amount }
    val sold = currentMonthTrades.filter { it.type == TransactionType.SELL }.sumOf { it.amount }
    val grouped = filtered.groupBy { monthFormat.format(Date(it.timestamp)) }.toSortedMap(compareByDescending { it })

    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("流水", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("买入卖出会自动更新同账户持仓", color = FinColors.TextSecondary, fontSize = 10.sp)
                    }
                    Button(
                        onClick = onRecordTrade,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)
                    ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("记一笔", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            item {
                PrototypeCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryMetric("-${money(bought, baseCurrency)}", "本月买入", FinColors.Profit)
                        SummaryMetric("+${money(sold, baseCurrency)}", "本月卖出", FinColors.Loss)
                        SummaryMetric(signedMoney(sold - bought, baseCurrency), "净流入")
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrototypeChip("全部", filter == null) { filter = null }
                    PrototypeChip("买入", filter == TransactionType.BUY) { filter = TransactionType.BUY }
                    PrototypeChip("卖出", filter == TransactionType.SELL) { filter = TransactionType.SELL }
                }
            }
            if (grouped.isEmpty()) {
                item {
                    PrototypeCard(modifier = Modifier.padding(top = 18.dp)) {
                        Text("暂无买入或卖出流水", color = FinColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp))
                    }
                }
            } else {
                grouped.forEach { (month, monthTransactions) ->
                    item { Text(month, color = FinColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp)) }
                    items(monthTransactions, key = { it.id }) { transaction ->
                        PrototypeTradeRow(transaction, accountNames[transaction.accountId], dateFormat)
                    }
                }
            }
            item {
                Text("流水驱动持仓：保存后按证券编码匹配账户内的原始持仓，买入加权重算成本，卖出按比例结转成本。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PrototypeTradeRow(transaction: Transaction, accountName: String?, dateFormat: SimpleDateFormat) {
    val isBuy = transaction.type == TransactionType.BUY
    PrototypeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = (if (isBuy) FinColors.Profit else FinColors.Loss).copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                Text(if (isBuy) "买" else "卖", color = if (isBuy) FinColors.Profit else FinColors.Loss, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.note?.substringAfter(' ')?.substringBefore(" ·") ?: transaction.symbol.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${transaction.symbol.orEmpty()} · ${accountName ?: "未知账户"} · ${dateFormat.format(Date(transaction.timestamp))}", color = FinColors.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoText("${if (isBuy) "-" else "+"}${money(transaction.amount, transaction.currency)}", color = if (isBuy) FinColors.Profit else FinColors.Loss, size = 11, weight = FontWeight.SemiBold)
                MonoText("${formatQuantity(transaction.shares)} @ ${formatQuantity(transaction.price)}", color = FinColors.TextSecondary, size = 9)
            }
        }
    }
}

@Composable
private fun formatQuantity(value: Double?): String = value?.let { String.format(Locale.US, "%.4f", it).trimEnd('0').trimEnd('.') } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeTradeEntryScreen(
    accounts: List<AccountSummary>,
    holdings: List<com.finunity.data.model.MergedHoldingSummary>,
    onBack: () -> Unit,
    onSave: (isBuy: Boolean, accountId: String, name: String, securityCode: String, quantity: Double, price: Double, bucket: PrototypeBucket, timestamp: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isBuy by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var securityCode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var bucket by remember { mutableStateOf(PrototypeBucket.AGGRESSIVE) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false } }
    var dateText by remember { mutableStateOf(dateFormat.format(Date())) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsedQuantity = quantity.toDoubleOrNull()
    val parsedPrice = price.toDoubleOrNull()
    val timestamp = remember(dateText) { runCatching { dateFormat.parse(dateText)?.time }.getOrNull() }
    val amount = (parsedQuantity ?: 0.0) * (parsedPrice ?: 0.0)
    val selectedCurrency = accounts.firstOrNull { it.account.id == selectedAccount }?.account?.currency ?: "CNY"

    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("记一笔", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { isBuy = true }, modifier = Modifier.weight(1f), shape = FinShapes.sm, colors = ButtonDefaults.buttonColors(containerColor = if (isBuy) FinColors.Profit else FinColors.SurfaceElevated, contentColor = Color.White)) { Text("买入") }
                    Button(onClick = { isBuy = false }, modifier = Modifier.weight(1f), shape = FinShapes.sm, colors = ButtonDefaults.buttonColors(containerColor = if (!isBuy) FinColors.Loss else FinColors.SurfaceElevated, contentColor = Color.White)) { Text("卖出") }
                }
            }
            if (holdings.isNotEmpty()) {
                item {
                    PrototypeCard {
                        Text("已有持仓（点击带入）", color = FinColors.TextSecondary, fontSize = 10.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            holdings.take(8).forEach { holding ->
                                PrototypeChip("${holding.displayName} · ${holding.code}", false) { name = holding.displayName; securityCode = holding.code }
                            }
                        }
                    }
                }
            }
            item { PrototypeField("持仓名称", name, { name = it }, "如 沪深300ETF") }
            item { PrototypeField("证券编码", securityCode, { securityCode = it }, "如 510300、AAPL") }
            item { PrototypeField("成交数量", quantity, { quantity = it }, "份 / 股", number = true) }
            item { PrototypeField("成交价", price, { price = it }, "元", number = true) }
            item {
                PrototypeCard {
                    Text("成交金额（自动计算）", color = FinColors.TextSecondary, fontSize = 10.sp)
                    MonoText(if (parsedQuantity != null && parsedPrice != null) money(amount, selectedCurrency) else "—", size = 18, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                PrototypeCard {
                    Text("成交账户", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { account -> PrototypeChip(account.account.name, selectedAccount == account.account.id) { selectedAccount = account.account.id } }
                    }
                }
            }
            if (isBuy) {
                item {
                    PrototypeCard {
                        Text("新建持仓的策略桶", color = FinColors.TextSecondary, fontSize = 10.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PrototypeBucket.entries.forEach { item -> PrototypeChip(item.label, bucket == item) { bucket = item } }
                        }
                    }
                }
            }
            item { PrototypeField("成交日期", dateText, { dateText = it }, "yyyy-MM-dd") }
            item {
                if (error != null) Text(error!!, color = FinColors.Profit, fontSize = 10.sp)
                Button(
                    onClick = {
                        error = when {
                            name.isBlank() || securityCode.isBlank() -> "请填写名称和证券编码"
                            parsedQuantity == null || parsedQuantity <= 0.0 -> "请输入有效成交数量"
                            parsedPrice == null || parsedPrice <= 0.0 -> "请输入有效成交价"
                            selectedAccount.isBlank() -> "请选择成交账户"
                            timestamp == null -> "日期格式应为 yyyy-MM-dd"
                            else -> null
                        }
                        if (error == null) onSave(isBuy, selectedAccount, name.trim(), securityCode.trim(), parsedQuantity!!, parsedPrice!!, bucket, timestamp!!)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBuy) FinColors.Profit else FinColors.Loss, contentColor = Color.White)
                ) { Text("保存流水 · 更新持仓", fontWeight = FontWeight.Bold) }
            }
            item { Text("卖出数量超过当前持仓会被拦截；买入同码持仓会按数量加权重算成本。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAllocationScreen(
    portfolioSummary: PortfolioSummary?,
    onSave: (Map<PrototypeBucket, Float>) -> Unit,
    onSaved: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    if (summary == null) {
        Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FinColors.Secondary) }
        }
        return
    }
    val actual = prototypeAllocations(summary)
    val initial = remember(summary.targetAllocation) { prototypeTargets(summary.targetAllocation) }
    var defensive by remember(initial) { mutableStateOf(initial[PrototypeBucket.DEFENSIVE] ?: 0f) }
    var balanced by remember(initial) { mutableStateOf(initial[PrototypeBucket.BALANCED] ?: 0f) }
    var aggressive by remember(initial) { mutableStateOf(initial[PrototypeBucket.AGGRESSIVE] ?: 0f) }
    val values = mapOf(PrototypeBucket.DEFENSIVE to defensive, PrototypeBucket.BALANCED to balanced, PrototypeBucket.AGGRESSIVE to aggressive)
    val total = defensive + balanced + aggressive
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("策略配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("拖动设定目标比例 · 合计须为 100%", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
            }
            PrototypeBucket.entries.forEach { bucket ->
                item {
                    val value = values[bucket] ?: 0f
                    val onChange: (Float) -> Unit = when (bucket) {
                        PrototypeBucket.DEFENSIVE -> { next: Float -> defensive = next }
                        PrototypeBucket.BALANCED -> { next: Float -> balanced = next }
                        PrototypeBucket.AGGRESSIVE -> { next: Float -> aggressive = next }
                    }
                    SliderCard(bucket, value, actual[bucket] ?: 0.0, onChange)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("目标比例合计", color = FinColors.TextSecondary, fontSize = 11.sp)
                    MonoText(String.format(Locale.US, "%.0f%%", total * 100), color = if (abs(total - 1f) < 0.001f) FinColors.TextPrimary else FinColors.Profit, size = 13, weight = FontWeight.Bold)
                }
            }
            item { AllocationPreview(actual, values, summary.rebalanceThreshold) }
            item {
                Button(
                    onClick = { onSave(values); onSaved() },
                    enabled = abs(total - 1f) < 0.001f,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg, disabledContainerColor = FinColors.SurfaceElevated, disabledContentColor = FinColors.TextTertiary)
                ) { Text("保存配置", fontWeight = FontWeight.Bold) }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun SliderCard(bucket: PrototypeBucket, value: Float, actual: Double, onChange: (Float) -> Unit) {
    PrototypeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(bucket.color))
            Spacer(Modifier.width(8.dp))
            Text("${bucket.label}型", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            MonoText(String.format(Locale.US, "%.0f%%", value * 100), size = 16, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("${bucket.description} · 当前实际 ${String.format(Locale.US, "%.1f%%", actual * 100)}", color = FinColors.TextSecondary, fontSize = 10.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            steps = 99,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = bucket.color, inactiveTrackColor = Color.White.copy(alpha = 0.10f))
        )
    }
}

@Composable
private fun AllocationPreview(actual: Map<PrototypeBucket, Double>, target: Map<PrototypeBucket, Float>, threshold: Double) {
    PrototypeCard {
        Text("偏移预览 · 阈值 ±${(threshold * 100).roundToInt()}%", color = FinColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        PrototypeBucket.entries.forEach { bucket ->
            val actualValue = actual[bucket] ?: 0.0
            val targetValue = target[bucket] ?: 0f
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.label, color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxWidth(actualValue.toFloat().coerceIn(0f, 1f)).fillMaxSize().background(bucket.color))
                    Box(Modifier.fillMaxWidth().padding(start = (targetValue * 100).coerceIn(0f, 100f).dp).width(2.dp).fillMaxSize().background(Color.White))
                }
                MonoText(signedPercent(actualValue - targetValue), color = if (abs(actualValue - targetValue) > threshold) FinColors.Profit else FinColors.Loss, size = 11, weight = FontWeight.SemiBold, modifier = Modifier.width(58.dp).padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAccountsScreen(
    portfolioSummary: PortfolioSummary?,
    onViewAccount: (String) -> Unit,
    onAddSource: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        val summary = portfolioSummary
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FinColors.Secondary) }
            return@Scaffold
        }
        val rawCount = summary.assetRecords.size + summary.holdings.size
        val mergedCount = buildPrototypeHoldings(summary).size
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("账户", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("已接入 ${summary.accounts.size} 个数据源 · 支持券商与基金平台", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            }
            items(summary.accounts, key = { it.account.id }) { account ->
                AccountSourceRow(account, summary, onClick = { onViewAccount(account.account.id) })
            }
            item {
                Button(onClick = onAddSource, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = FinColors.TextPrimary)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("添加账户", fontWeight = FontWeight.Bold)
                }
            }
            item { MergeRuleCard(rawCount, mergedCount) }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun AccountSourceRow(summary: AccountSummary, portfolioSummary: PortfolioSummary, onClick: () -> Unit) {
    val count = portfolioSummary.assetRecords.count { it.record.accountId == summary.account.id } + portfolioSummary.holdings.count { it.position.accountId == summary.account.id }
    val updated = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(portfolioSummary.lastUpdated))
    val stale = System.currentTimeMillis() - portfolioSummary.lastUpdated > 12 * 60 * 60 * 1000L
    PrototypeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(FinColors.Primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (summary.account.type == AccountType.BROKER) Icons.Default.AccountBalance else Icons.Default.Person, contentDescription = null, tint = FinColors.Secondary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.account.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${summary.account.type.displayName()} · ${summary.account.currency}", color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoText("$count 笔", size = 12, weight = FontWeight.SemiBold)
                Text(if (stale) "数据可能过期" else "已同步 $updated", color = if (stale) FinColors.Cash else FinColors.Loss, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = FinColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MergeRuleCard(rawCount: Int, mergedCount: Int) {
    PrototypeCard(color = FinColors.SurfaceElevated) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MergeBox("$rawCount 笔原始持仓")
            Text("→", color = FinColors.TextSecondary, modifier = Modifier.padding(horizontal = 6.dp))
            MergeBox("按编码归集")
            Text("→", color = FinColors.TextSecondary, modifier = Modifier.padding(horizontal = 6.dp))
            MergeBox("$mergedCount 项持仓")
        }
        Spacer(Modifier.height(10.dp))
        Text("合并规则：同一证券编码在不同账户中的持仓自动归集为一行，数量、市值、成本按持仓量加权合并；A/C 份额等同名不同码资产默认分列。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun MergeBox(text: String) {
    Surface(color = FinColors.Surface, shape = RoundedCornerShape(8.dp)) { Text(text, color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAddSourceScreen(
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onBrokerConnect: (Account) -> Unit,
    onScreenshot: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBrokerDialog by remember { mutableStateOf(false) }
    if (showBrokerDialog) {
        AlertDialog(
            onDismissRequest = { showBrokerDialog = false },
            title = { Text("选择券商 / 基金平台") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("华泰证券", "中信证券", "东方财富", "天天基金", "支付宝").forEach { platform ->
                        TextButton(onClick = { showBrokerDialog = false; onBrokerConnect(Account(name = platform, type = AccountType.BROKER, currency = "CNY", balance = 0.0)) }, modifier = Modifier.fillMaxWidth()) { Text(platform, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBrokerDialog = false }) { Text("取消") } }
        )
    }
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("添加账户", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("选择适合你的接入方式", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)) }
            item { SourceOptionCard("券商授权导入", "华泰 · 中信 · 东财等 20+ 券商", FinColors.Conservative, Icons.Default.AccountBalance, true, { showBrokerDialog = true }) }
            item { SourceOptionCard("截图识别", "上传持仓截图，自动识别导入", FinColors.Cash, Icons.Default.DateRange, false, onScreenshot) }
            item { SourceOptionCard("手动录入", "逐项填写，零散持仓也能记", FinColors.Aggressive, Icons.Default.List, false, onManual) }
            item {
                SectionCaption("已支持平台")
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("华泰证券", "中信证券", "东方财富", "天天基金", "支付宝", "+12").forEach { PrototypeChip(it, false) {} }
                }
            }
            item {
                PrototypeCard(color = FinColors.SurfaceElevated) {
                    Text("数据安全", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("授权与截图仅用于读取持仓，不获取交易权限；数据本地保存，可随时在账户页解除绑定。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceOptionCard(title: String, description: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, recommended: Boolean, onClick: () -> Unit) {
    PrototypeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (recommended) { Spacer(Modifier.width(6.dp)); Surface(color = Color.White, shape = RoundedCornerShape(5.dp)) { Text("推荐", color = FinColors.PageBg, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } }
                }
                Text(description, color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = FinColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeOcrImportScreen(
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onImport: (String, List<OcrHolding>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var selectedRows by remember { mutableStateOf(setOf(0, 1, 2, 3)) }
    val rows = remember {
        listOf(
            OcrHolding("沪深300ETF", "510300", 20_000.0, 72_000.0),
            OcrHolding("红利低波ETF", "512890", 15_000.0, 32_400.0),
            OcrHolding("贵州茅台", "600519", 200.0, 52_900.0),
            OcrHolding("中证500ETF", "510500", 10_000.0, 25_700.0, true)
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedUri = it }
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("截图识别", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PrototypeCard(modifier = Modifier.clickable { launcher.launch("image/*") }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF26385F)), contentAlignment = Alignment.Center) { Text("⌁", color = FinColors.Secondary, fontSize = 25.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(if (selectedUri == null) "点击上传 / 拍照" else "截图已选择", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("支持券商与基金 App 的持仓页截图", color = FinColors.TextSecondary, fontSize = 9.sp)
                    }
                }
            }
            if (selectedUri != null) {
                item { Text("截图已读取 · 本地识别结果预览", color = FinColors.TextSecondary, fontSize = 9.sp) }
                item {
                    PrototypeCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("选择归属账户", color = FinColors.TextSecondary, fontSize = 10.sp)
                            Text("${selectedRows.size} 项已勾选", color = FinColors.Secondary, fontSize = 10.sp)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            accounts.forEach { account -> PrototypeChip(account.account.name, selectedAccount == account.account.id) { selectedAccount = account.account.id } }
                        }
                    }
                }
                item {
                    PrototypeCard {
                        Text("已识别 ${rows.size} 项持仓", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        rows.forEachIndexed { index, row ->
                            OcrRow(row, selectedRows.contains(index)) { selectedRows = if (selectedRows.contains(index)) selectedRows - index else selectedRows + index }
                        }
                    }
                }
                item {
                    Button(onClick = { onImport(selectedAccount, rows.filterIndexed { index, _ -> selectedRows.contains(index) }) }, enabled = selectedRows.isNotEmpty() && selectedAccount.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)) { Text("确认导入 ${selectedRows.size} 项", fontWeight = FontWeight.Bold) }
                }
            }
            item { Text("识别后的记录仍会进入按编码合并管线；低置信度项会标记为需确认。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun OcrRow(row: OcrHolding, checked: Boolean, onChecked: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onChecked).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) FinColors.Conservative else Color.Transparent).then(Modifier), contentAlignment = Alignment.Center) {
            if (checked) Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) else Box(Modifier.fillMaxSize().background(Color.Transparent))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (row.needsReview) { Spacer(Modifier.width(6.dp)); Text("需确认", color = FinColors.Cash, fontSize = 8.sp) }
            }
            MonoText("${row.securityCode} · ${row.quantity.toInt()} 份 / 股", color = FinColors.TextSecondary, size = 9)
        }
        MonoText(money(row.marketValue, "CNY"), size = 11, weight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeManualEntryScreen(
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onSave: (AssetRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var securityCode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var currentPrice by remember { mutableStateOf("") }
    var selectedBucket by remember { mutableStateOf(PrototypeBucket.AGGRESSIVE) }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("手动录入", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PrototypeField("持仓名称", name, { name = it }, "如 沪深300ETF") }
            item { PrototypeField("证券编码", securityCode, { securityCode = it }, "如 510300、AAPL") }
            item {
                PrototypeCard {
                    Text("策略桶（归入其一）", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { PrototypeBucket.entries.forEach { bucket -> PrototypeChip(bucket.label, selectedBucket == bucket) { selectedBucket = bucket } } }
                }
            }
            item { PrototypeField("持有数量", quantity, { quantity = it }, "份 / 股", number = true) }
            item { PrototypeField("成本价", costPrice, { costPrice = it }, "元", number = true) }
            item { PrototypeField("当前价（可选）", currentPrice, { currentPrice = it }, "元", number = true) }
            item {
                PrototypeCard {
                    Text("归属账户", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { accounts.forEach { account -> PrototypeChip(account.account.name, selectedAccount == account.account.id) { selectedAccount = account.account.id } } }
                }
            }
            item {
                if (error != null) Text(error!!, color = FinColors.Profit, fontSize = 10.sp)
                Button(onClick = {
                    val qty = quantity.toDoubleOrNull()
                    val cost = costPrice.toDoubleOrNull()
                    val price = currentPrice.toDoubleOrNull() ?: cost
                    if (name.isBlank() || securityCode.isBlank() || qty == null || qty <= 0 || cost == null || cost <= 0 || price == null || selectedAccount.isBlank()) {
                        error = "请完整填写名称、证券编码、数量、成本价和账户"
                    } else {
                        onSave(AssetRecord(accountId = selectedAccount, assetType = AssetType.ETF, riskBucket = when (selectedBucket) { PrototypeBucket.DEFENSIVE -> RiskBucket.CASH; PrototypeBucket.BALANCED -> RiskBucket.CONSERVATIVE; PrototypeBucket.AGGRESSIVE -> RiskBucket.AGGRESSIVE }, name = name.trim(), securityCode = securityCode.trim(), quantity = qty, cost = qty * cost, currentPrice = price, currency = "CNY") )
                    }
                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)) { Text("保存并加入汇总", fontWeight = FontWeight.Bold) }
            }
            item { Text("保存后立即参与总市值、策略占比与偏移计算；同编码持仓自动合并。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun PrototypeField(label: String, value: String, onValueChange: (String) -> Unit, hint: String, number: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        textStyle = if (number) androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace) else androidx.compose.ui.text.TextStyle.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FinColors.Secondary, unfocusedBorderColor = FinColors.Outline, focusedLabelColor = FinColors.Secondary, unfocusedLabelColor = FinColors.TextSecondary, cursorColor = FinColors.Secondary)
    )
}

@Composable
private fun PrototypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clip(CircleShape).clickable(onClick = onClick), shape = CircleShape, color = if (selected) Color.White else Color.Transparent, border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, FinColors.Outline)) {
        Text(text, color = if (selected) FinColors.PageBg else FinColors.TextSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrototypeTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = FinColors.TextPrimary) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = FinColors.PageBg, titleContentColor = FinColors.TextPrimary)
    )
}
