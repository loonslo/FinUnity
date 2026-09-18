package com.finunity.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.PriceStatus
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.displayName
import com.finunity.data.local.entity.defaultRiskBucket
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.normalizeSecurityCode
import com.finunity.data.repository.ScreenshotImportRepository
import com.finunity.data.repository.MonthlyChange
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinBucketTag
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinInlineField
import com.finunity.ui.components.FinSettingRow
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一级导航只保留用户完成核心资产识别所需的三个入口。
 * 资产变动、配置和账户管理均通过二级路径进入，避免底部导航承载低频功能。
 */
enum class PrototypeTab { Overview, Assets, Mine }

private enum class AssetEventFilter { ALL, TRADES, INFLOW, OUTFLOW }

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
    val bucket: PrototypeBucket,
    val accountIds: List<String>
) {
    val profitLoss: Double get() = value - cost
    val profitRatio: Double get() = if (cost > 0) profitLoss / cost else 0.0
}

data class OcrHolding(
    val name: String,
    val securityCode: String,
    val quantity: String,
    val currentPrice: String,
    val cost: String = "",
    val currency: String = "CNY",
    val needsReview: Boolean = false,
    val rawText: String = "",
    val rawSecurityCode: String = securityCode,
    val confidence: String = "HIGH",
    val reviewReason: String = "",
    val instrumentId: String = ""
) {
    val quantityValue: Double? get() = ocrNumber(quantity)
    val currentPriceValue: Double? get() = ocrNumber(currentPrice)
    val costValue: Double? get() = ocrNumber(cost)
    val isReadyForImport: Boolean
        get() = name.isNotBlank() && securityCode.isNotBlank() &&
            (quantityValue ?: 0.0) > 0.0 && (currentPriceValue ?: 0.0) > 0.0 && (costValue ?: 0.0) > 0.0
}

private fun ocrNumber(value: String): Double? = value.replace(",", "").trim().toDoubleOrNull()

private fun ocrNumberText(value: Double): String =
    String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')

private fun prototypeBucket(bucket: RiskBucket): PrototypeBucket = when (bucket) {
    RiskBucket.DEFENSIVE -> PrototypeBucket.DEFENSIVE
    RiskBucket.AGGRESSIVE -> PrototypeBucket.AGGRESSIVE
    RiskBucket.BALANCED -> PrototypeBucket.BALANCED
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
            bucket = prototypeBucket(item.riskBucket),
            accountIds = item.accountIds
        )
    }
}

private fun prototypeAllocations(summary: PortfolioSummary): Map<PrototypeBucket, Double> {
    val values = summary.riskBuckets.associate { it.riskBucket to it.totalValue }
    val total = summary.totalAssets
    if (total <= 0.0) return PrototypeBucket.entries.associateWith { 0.0 }
    return mapOf(
        PrototypeBucket.DEFENSIVE to (values[RiskBucket.DEFENSIVE] ?: 0.0) / total,
        PrototypeBucket.BALANCED to (values[RiskBucket.BALANCED] ?: 0.0) / total,
        PrototypeBucket.AGGRESSIVE to (values[RiskBucket.AGGRESSIVE] ?: 0.0) / total
    )
}

private fun prototypeTargets(targetAllocation: String): Map<PrototypeBucket, Float> {
    val target = parseTargetAllocation(targetAllocation)
    return mapOf(
        PrototypeBucket.DEFENSIVE to (target["DEFENSIVE"] ?: 0.0).toFloat(),
        PrototypeBucket.BALANCED to (target["BALANCED"] ?: 0.0).toFloat(),
        PrototypeBucket.AGGRESSIVE to (target["AGGRESSIVE"] ?: 0.0).toFloat()
    )
}

private fun money(value: Double, currency: String): String = formatCurrency(value, currency)

private fun signedMoney(value: Double, currency: String): String = when {
    value > 0 -> "+${money(value, currency)}"
    value < 0 -> "-${money(-value, currency)}"
    else -> money(0.0, currency)
}

private fun signedPercent(value: Double): String = when {
    value > 0 -> "+${String.format(Locale.US, "%.1f%%", value * 100)}"
    value < 0 -> String.format(Locale.US, "%.1f%%", value * 100)
    else -> "0.0%"
}

private fun changeColor(value: Double): Color = when {
    value > 0 -> FinColors.Profit
    value < 0 -> FinColors.Loss
    else -> FinColors.TextSecondary
}

private fun driftColor(value: Double, threshold: Double): Color =
    if (abs(value) > threshold) FinColors.Warning else FinColors.TextSecondary

@Composable
private fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FinColors.TextPrimary,
    size: Int = 12,
    weight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
        fontWeight = weight,
            letterSpacing = 0.sp,
            fontFeatureSettings = "\"tnum\""
        )
    )
}

@Composable
private fun PrototypeCard(
    modifier: Modifier = Modifier,
    color: Color = FinColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    FinCard(
        modifier = modifier,
        containerColor = color,
        contentPadding = PaddingValues(14.dp),
        content = content
    )
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
            PrototypeTabItem(PrototypeTab.Assets, "资产", Icons.Default.List, selected, onSelect)
            PrototypeTabItem(PrototypeTab.Mine, "我的", Icons.Default.Person, selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.PrototypeTabItem(
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
    priceStatus: PriceStatus = PriceStatus.NORMAL,
    priceHistory: List<PriceHistory> = emptyList(),
    missingCurrencies: Set<String> = emptySet(),
    monthlyChange: MonthlyChange? = null,
    onStartAddFlow: () -> Unit,
    onRefreshPrices: () -> Unit,
    onOpenAllocation: () -> Unit = {},
    onOpenBucket: (Int) -> Unit = {},
    onOpenDataQuality: (String) -> Unit = {},
    onOpenAsset: (String) -> Unit = {},
    onOpenMonthlyReview: () -> Unit = {},
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
            PrototypeOverviewSkeleton(Modifier.fillMaxSize().padding(padding))
        } else if (summary == null || summary.totalAssets <= 0.0) {
            EmptyPrototypeOverview(
                onStartAddFlow = onStartAddFlow,
                hasAccounts = summary?.accounts?.isNotEmpty() == true,
                modifier = Modifier.padding(padding)
            )
        } else {
            val allocations = prototypeAllocations(summary)
            val targets = prototypeTargets(summary.targetAllocation)
            val holdings = buildPrototypeHoldings(summary)
            val holdingTrends = holdings.associate { holding ->
                holding.key to prototypeHoldingPriceTrend(summary, holding, priceHistory)
            }
            val threshold = summary.rebalanceThreshold
            val suspiciousRecord = summary.assetRecords.firstOrNull {
                it.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND) &&
                    it.costInBaseCurrency > 0.0 && abs(it.profitLossRatio) > 5.0
            }
            val drift = allocations.mapValues { (bucket, actual) ->
                actual - (targets[bucket] ?: 0f).toDouble()
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OverviewHeader(lastPriceUpdated, priceStatus, isLoading, onRefreshPrices)
                }
                item {
                    TotalAssetCard(summary, monthlyChange)
                }
                if (suspiciousRecord != null || missingCurrencies.isNotEmpty() || priceStatus == PriceStatus.EXPIRED || priceStatus == PriceStatus.CACHE_FALLBACK || priceStatus == PriceStatus.PARTIAL_FAILURE || drift.values.any { abs(it) > threshold }) {
                    item {
                        OverviewPriorityAlert(
                            missingCurrencies = missingCurrencies,
                            priceStatus = priceStatus,
                            suspiciousAssetName = suspiciousRecord?.record?.name,
                            drift = drift,
                            threshold = threshold,
                            onOpenQuality = suspiciousRecord?.record?.id?.let { id -> { onOpenDataQuality(id) } }
                        )
                    }
                }
                item {
                    AllocationCard(allocations, targets, holdings.size, threshold, onOpenAllocation, onOpenBucket)
                }
                if (holdings.isNotEmpty()) {
                    item { SectionCaption("市值 TOP 3") }
                    items(holdings.take(3), key = { it.key }) { holding ->
                        CompactHoldingRow(holding, summary.baseCurrency, holdingTrends[holding.key].orEmpty(), onClick = { onOpenAsset(holding.key) })
                    }
                    if (holdings.size < 3) {
                        item { AddHoldingPrompt(onStartAddFlow) }
                    }
                }
                if (monthlyChange != null) {
                    item {
                        TextButton(onClick = onOpenMonthlyReview, modifier = Modifier.fillMaxWidth()) {
                            Text("查看本月资产复盘", color = FinColors.Secondary)
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyPrototypeOverview(
    onStartAddFlow: () -> Unit,
    hasAccounts: Boolean,
    modifier: Modifier
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hasAccounts) "账户已建立，先添加一项资产" else "把散落的资产，汇成一张总账",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasAccounts) "录入后会立即参与总览、配置占比和偏离提醒。"
            else "先创建账户，再录入资产；衡仓会按证券编码自动合并。",
            color = FinColors.TextSecondary
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onStartAddFlow, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (hasAccounts) "添加第一项资产" else "添加账户", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverviewHeader(
    lastPriceUpdated: Long?,
    priceStatus: PriceStatus,
    isLoading: Boolean,
    onRefreshPrices: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("总览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val time = lastPriceUpdated?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "暂无"
        Row(
            modifier = Modifier.clip(CircleShape).clickable(onClick = onRefreshPrices).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(6.dp).clip(CircleShape).background(
                    when (priceStatus) {
                        PriceStatus.NORMAL -> FinColors.Success
                        PriceStatus.DELAYED, PriceStatus.CACHE_FALLBACK -> FinColors.Warning
                        PriceStatus.EXPIRED -> FinColors.Danger
                        PriceStatus.PARTIAL_FAILURE -> FinColors.Warning
                    }
                )
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (isLoading) "同步中" else when (priceStatus) {
                    PriceStatus.NORMAL -> if (lastPriceUpdated == null) "待同步" else "已同步 $time"
                    PriceStatus.DELAYED -> "价格延迟 $time"
                    PriceStatus.EXPIRED -> "价格已过期"
                    PriceStatus.CACHE_FALLBACK -> "缓存回退 $time"
                    PriceStatus.PARTIAL_FAILURE -> "部分同步成功"
                },
                fontSize = 10.sp,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun TotalAssetCard(summary: PortfolioSummary, monthlyChange: MonthlyChange?) {
    // 活跃资产统一来自 AssetRecord；旧 Position 只由迁移/备份兼容层处理。
    val cost = summary.assetRecords.sumOf { it.costInBaseCurrency }
    val cumulative = summary.totalAssets - cost
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1E2438), Color(0xFF171B2A))))
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            Column(Modifier.padding(16.dp)) {
                Text("总资产", color = FinColors.TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                MonoText(money(summary.totalAssets, summary.baseCurrency), size = 29, weight = FontWeight.Bold)
                Text(
                    "总成本 ${money(cost, summary.baseCurrency)}",
                    color = FinColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val monthValue = monthlyChange?.change ?: 0.0
                    MetricCell("本月变化", signedMoney(monthValue, summary.baseCurrency), changeColor(monthValue))
                    MetricDivider()
                    MetricCell("本月变化率", signedPercent(monthlyChange?.percentageChange ?: 0.0), changeColor(monthlyChange?.percentageChange ?: 0.0))
                    MetricDivider()
                    MetricCell("累计收益", signedMoney(cumulative, summary.baseCurrency), changeColor(cumulative))
                }
                Text("今日变化 ${signedMoney(summary.todayChange, summary.baseCurrency)} · 仅作辅助参考", color = FinColors.TextTertiary, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** 首页只呈现一条最高优先级提醒：数据可信度优先于配置建议。 */
@Composable
private fun OverviewPriorityAlert(
    missingCurrencies: Set<String>,
    priceStatus: PriceStatus,
    suspiciousAssetName: String?,
    drift: Map<PrototypeBucket, Double>,
    threshold: Double,
    onOpenQuality: (() -> Unit)? = null
) {
    val title: String
    val detail: String
    val color: Color
    when {
        suspiciousAssetName != null -> {
            title = "收益率异常"
            detail = "${suspiciousAssetName} 的累计收益率超过 500%，请检查成本价后再判断收益。"
            color = FinColors.Danger
        }
        missingCurrencies.isNotEmpty() -> {
            title = "数据质量提醒"
            detail = "缺少 ${missingCurrencies.joinToString()} 汇率，部分资产未计入总额。请补齐汇率后再查看配置。"
            color = FinColors.Warning
        }
        priceStatus == PriceStatus.EXPIRED -> {
            title = "行情已过期"
            detail = "部分资产使用过期价格，刷新行情后再判断资产变化。"
            color = FinColors.Danger
        }
        priceStatus == PriceStatus.CACHE_FALLBACK || priceStatus == PriceStatus.PARTIAL_FAILURE -> {
            title = "行情使用缓存"
            detail = "最新价格暂未全部更新，当前金额仅供参考。"
            color = FinColors.Warning
        }
        else -> {
            val bad = drift.filterValues { abs(it) > threshold }
            title = "配置偏离提醒"
            detail = bad.entries.joinToString("，") { (bucket, value) -> "${bucket.label}${if (value > 0) "超配" else "欠配"} ${signedPercent(value)}" } +
                "。建议按目标比例检查，不代表需要立即交易。"
            color = FinColors.Warning
        }
    }
    PrototypeCard(modifier = if (onOpenQuality != null) Modifier.clickable { onOpenQuality() } else Modifier, color = color.copy(alpha = 0.13f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text(detail, color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun DataQualityCard(missingCurrencies: Set<String>) {
    PrototypeCard(color = Color(0xFF3A3020)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "数据提醒",
                tint = FinColors.Warning,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("部分金额未计入总览", color = FinColors.Warning, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text(
                    "缺少 ${missingCurrencies.joinToString()} 的汇率。补齐汇率后，资产合计和配置比例会重新计算。",
                    color = FinColors.TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun RowScope.MetricCell(label: String, value: String, color: Color) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, color = FinColors.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        MonoText(value, color = color, size = 11, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun DriftWarningCard(drift: Map<PrototypeBucket, Double>, threshold: Double, total: Double, currency: String) {
    val bad = drift.filterValues { abs(it) > threshold }.entries
    val aggressive = drift[PrototypeBucket.AGGRESSIVE] ?: 0.0
    val advice = if (aggressive > threshold) {
        "减持进攻约 ${money(total * aggressive, currency)}，转入欠配桶"
    } else "按目标比例进行再平衡"
    PrototypeCard(color = Color(0xFF3A3020)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp).clip(FinShapes.sm).background(FinColors.Warning.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Warning, contentDescription = "配置提醒", tint = FinColors.Warning, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("配置偏移提醒 · 阈值 ±${(threshold * 100).roundToInt()}%", color = FinColors.Warning, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    bad.joinToString("，") { (bucket, value) -> "${bucket.label}${if (value > 0) "超配" else "欠配"} ${signedPercent(value.toDouble())}" } + "：$advice",
                    color = FinColors.Warning,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AllocationCard(actual: Map<PrototypeBucket, Double>, target: Map<PrototypeBucket, Float>, holdingCount: Int, threshold: Double, onClick: () -> Unit = {}, onOpenBucket: (Int) -> Unit = {}) {
    PrototypeCard {
        Text("资产结构（查看目标配置）", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick))
        Spacer(Modifier.height(3.dp))
        Text(
            "彩环 = 实际 · 白刻度 = 目标 ${target[PrototypeBucket.DEFENSIVE]?.times(100)?.roundToInt() ?: 0}/${target[PrototypeBucket.BALANCED]?.times(100)?.roundToInt() ?: 0}/${target[PrototypeBucket.AGGRESSIVE]?.times(100)?.roundToInt() ?: 0}",
            color = FinColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                AllocationDonut(actual, target, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MonoText("$holdingCount 项", size = 14, weight = FontWeight.Bold)
                    Text("资产", color = FinColors.TextSecondary, fontSize = 9.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PrototypeBucket.entries.forEach { bucket ->
                    val value = actual[bucket] ?: 0.0
                    val goal = target[bucket] ?: 0f
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpenBucket(bucket.ordinal) })
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(bucket.color))
                        Spacer(Modifier.width(6.dp))
                        Text(bucket.label, color = bucket.color, fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(45.dp))
                        MonoText(String.format(Locale.US, "%.1f%%", value * 100), size = 11, weight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        MonoText(signedPercent(value - goal), color = driftColor(value - goal, threshold), size = 10)
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
private fun PrototypeOverviewSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.28f).height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = FinColors.Accent
            )
        }
        Text("正在读取本地账本…", color = FinColors.TextSecondary, fontSize = 12.sp)
        SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(92.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(230.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(74.dp))
    }
}

/** 各列表页共用的加载占位，避免在内容区域裸露一个转圈。 */
@Composable
private fun PrototypeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.30f).height(22.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = FinColors.Accent
            )
        }
        Text("正在整理账本…", color = FinColors.TextSecondary, fontSize = 12.sp)
        SkeletonBlock(Modifier.fillMaxWidth().height(88.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(modifier.clip(FinShapes.md).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun MetricDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun AddHoldingPrompt(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = FinShapes.md,
        color = Color.Transparent,
        border = BorderStroke(1.dp, FinColors.Outline)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, tint = FinColors.Secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("再添加一项资产", color = FinColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(text, color = FinColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp, bottom = 0.dp))
}

@Composable
private fun CompactHoldingRow(holding: PrototypeHolding, currency: String, priceTrend: List<Double>, onClick: () -> Unit = {}) {
    PrototypeCard(modifier = Modifier.padding(bottom = 0.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(holding.bucket.color))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(holding.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MonoText("${holding.key}${if (holding.accountCount > 1) " · 分布于 ${holding.accountCount} 个账户" else ""}", color = FinColors.TextSecondary, size = 9)
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoText(money(holding.value, currency), size = 12, weight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (abs(holding.profitRatio) > 5.0) {
                        MonoText("收益率异常", color = FinColors.Warning, size = 10)
                    } else {
                        MonoText(signedPercent(holding.profitRatio), color = changeColor(holding.profitRatio), size = 10)
                    }
                    Spacer(Modifier.width(6.dp))
                    if (priceTrend.size >= 2) HoldingSparkline(priceTrend, holding.profitRatio)
                }
            }
        }
    }
}

/** 只展示已缓存的真实价格点；无历史时不显示走势线。 */
@Composable
private fun HoldingSparkline(prices: List<Double>, profitRatio: Double) {
    val color = changeColor(profitRatio)
    val min = prices.minOrNull() ?: return
    val max = prices.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    Canvas(Modifier.width(40.dp).height(14.dp)) {
        prices.takeLast(12).zipWithNext().forEachIndexed { index, (start, end) ->
            val count = prices.takeLast(12).size
            val x1 = size.width * index / (count - 1)
            val x2 = size.width * (index + 1) / (count - 1)
            drawLine(
                color = color,
                start = Offset(x1, size.height * (1f - ((start - min) / range).toFloat())),
                end = Offset(x2, size.height * (1f - ((end - min) / range).toFloat())),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun prototypeHoldingPriceTrend(
    summary: PortfolioSummary,
    holding: PrototypeHolding,
    priceHistory: List<PriceHistory>
): List<Double> {
    val matchingRecordIds = summary.assetRecords
        .filter { normalizeSecurityCode(it.record.securityCode.ifBlank { it.record.name }) == holding.key }
        .map { it.record.id }
        .toSet()
    // 同证券跨账户时，选择价格记录最完整的来源，而不是把不同币种或时间点直接相加。
    return priceHistory
        .filter { it.recordId in matchingRecordIds }
        .groupBy { it.recordId }
        .maxByOrNull { it.value.size }
        ?.value
        ?.sortedBy { it.timestamp }
        ?.map { it.price }
        .orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeHoldingsScreen(
    portfolioSummary: PortfolioSummary?,
    onOpenAsset: (String) -> Unit,
    onRecordTrade: () -> Unit,
    onOpenFlows: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        val summary = portfolioSummary
        if (summary == null) {
            PrototypeLoadingSkeleton(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val holdings = buildPrototypeHoldings(summary)
        var selected by remember { mutableStateOf<PrototypeBucket?>(null) }
        var selectedAccountId by remember { mutableStateOf<String?>(null) }
        var filtersExpanded by rememberSaveable { mutableStateOf(false) }
        var sortDescending by rememberSaveable { mutableStateOf(true) }
        val filtered = holdings
            .filter { selected == null || it.bucket == selected }
            .filter { selectedAccountId == null || selectedAccountId in it.accountIds }
            .let { list -> if (sortDescending) list.sortedByDescending { it.value } else list.sortedBy { it.value } }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("资产", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("${summary.accounts.size} 个账户 · ${holdings.size} 项资产", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                HoldingSummaryCard(holdings, summary)
            }
            if (holdings.size > 1) {
                item {
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (filtersExpanded) "收起筛选/排序" else "筛选/排序", color = FinColors.Secondary)
                    }
                    if (filtersExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PrototypeChip("全部", selected == null) { selected = null }
                                PrototypeBucket.entries.forEach { bucket -> PrototypeChip(bucket.label, selected == bucket) { selected = if (selected == bucket) null else bucket } }
                            }
                            if (summary.accounts.size > 1) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PrototypeChip("全部账户", selectedAccountId == null) { selectedAccountId = null }
                                    summary.accounts.forEach { account ->
                                        PrototypeChip(account.account.name, selectedAccountId == account.account.id) { selectedAccountId = if (selectedAccountId == account.account.id) null else account.account.id }
                                    }
                                }
                            }
                            TextButton(onClick = { sortDescending = !sortDescending }) {
                                Text(if (sortDescending) "市值从高到低" else "市值从低到高", color = FinColors.TextSecondary)
                            }
                        }
                    }
                }
            }
            items(filtered, key = { it.key }) { holding ->
                HoldingListRow(
                    holding,
                    summary.baseCurrency,
                    holdings.maxOfOrNull { it.value } ?: 0.0,
                    holdings.sumOf { it.value },
                    onClick = { onOpenAsset(holding.key) }
                )
            }
            item {
                Button(
                    onClick = onRecordTrade,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)
                ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("添加记录", fontWeight = FontWeight.Bold) }
            }
            item {
                TextButton(onClick = onOpenFlows, modifier = Modifier.fillMaxWidth()) {
                    Text("查看资产变动", color = FinColors.Secondary)
                }
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
            SummaryMetric(signedPercent(if (totalCost > 0) totalProfit / totalCost else 0.0), "累计收益率", changeColor(if (totalCost > 0) totalProfit / totalCost else 0.0))
            SummaryMetric(holdings.size.toString(), "资产项")
        }
    }
}

@Composable
private fun RowScope.SummaryMetric(value: String, label: String, color: Color = FinColors.TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        MonoText(value, color = color, size = 14, weight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = FinColors.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun HoldingListRow(holding: PrototypeHolding, currency: String, maxValue: Double, totalValue: Double, onClick: () -> Unit) {
    PrototypeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(holding.bucket.color))
            Spacer(Modifier.width(8.dp))
            Text(holding.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(7.dp))
            MonoText(holding.key, color = FinColors.TextSecondary, size = 9)
            if (holding.accountCount > 1) {
                Spacer(Modifier.weight(1f))
                Surface(color = Color(0xFF26385F), shape = RoundedCornerShape(6.dp)) { Text("分布于 ${holding.accountCount} 个账户", color = FinColors.Secondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                MonoText(money(holding.value, currency), size = 14, weight = FontWeight.Bold)
                Text("总成本 ${money(holding.cost, currency)}", color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                if (abs(holding.profitRatio) > 5.0) {
                    MonoText("收益率异常", color = FinColors.Warning, size = 11, weight = FontWeight.SemiBold)
                } else {
                    MonoText(signedPercent(holding.profitRatio), color = changeColor(holding.profitRatio), size = 12, weight = FontWeight.SemiBold)
                }
                FinBucketTag(holding.bucket.label, holding.bucket.color, Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))) {
            Box(Modifier.fillMaxWidth((if (maxValue > 0) holding.value / maxValue else 0.0).toFloat().coerceIn(0.03f, 1f)).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(holding.bucket.color))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(String.format(Locale.US, "占组合 %.1f%%", if (totalValue > 0) holding.value / totalValue * 100 else 0.0), color = FinColors.TextSecondary, fontSize = 9.sp)
            Text(if (holding.rawCount > 1) "${holding.rawCount} 条来源" else "单项资产", color = FinColors.TextSecondary, fontSize = 9.sp)
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
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(AssetEventFilter.ALL) }
    val monthFormat = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val accountNames = remember(accounts) { accounts.associate { it.account.id to it.account.name } }
    // 初始录入产生的审计 BUY 不算资产事件，避免把导入动作误计入本月变化。
    val events = transactions.filter { !it.note.orEmpty().startsWith("录入 ") }
    val filtered = events.filter {
        when (filter) {
            AssetEventFilter.ALL -> true
            AssetEventFilter.TRADES -> it.type == TransactionType.BUY || it.type == TransactionType.SELL
            AssetEventFilter.INFLOW -> it.type == TransactionType.DEPOSIT || it.type == TransactionType.DIVIDEND || it.category.income == true
            AssetEventFilter.OUTFLOW -> it.type == TransactionType.WITHDRAW || it.type == TransactionType.LIABILITY_PAYMENT || it.category.income == false
        }
    }
    val currentMonth = monthFormat.format(Date())
    val currentMonthEvents = events.filter { monthFormat.format(Date(it.timestamp)) == currentMonth }
    val bought = currentMonthEvents.filter { it.type == TransactionType.BUY }.sumOf { it.amount }
    val sold = currentMonthEvents.filter { it.type == TransactionType.SELL }.sumOf { it.amount }
    val inflow = currentMonthEvents.filter {
        it.type == TransactionType.DEPOSIT || it.type == TransactionType.DIVIDEND || it.category.income == true
    }.sumOf { it.amount }
    val outflow = currentMonthEvents.filter {
        it.type == TransactionType.WITHDRAW || it.type == TransactionType.LIABILITY_PAYMENT || it.category.income == false
    }.sumOf { it.amount }
    val grouped = filtered.groupBy { monthFormat.format(Date(it.timestamp)) }.toSortedMap(compareByDescending { it })

    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("资产变动", onBack) }, bottomBar = bottomBar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("资产变动", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("交易与重要资金事件，用来解释资产变化", color = FinColors.TextSecondary, fontSize = 10.sp)
                    }
                    Button(
                        onClick = onRecordTrade,
                        modifier = Modifier.height(44.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)
                    ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("添加记录", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
            item {
                PrototypeCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SummaryMetric(signedMoney(inflow - outflow, baseCurrency), "本月净流入", changeColor(inflow - outflow))
                        MetricDivider()
                        SummaryMetric(signedMoney(sold - bought, baseCurrency), "投资交易净额", changeColor(sold - bought))
                        MetricDivider()
                        SummaryMetric(events.size.toString(), "事件数")
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrototypeChip("全部", filter == AssetEventFilter.ALL) { filter = AssetEventFilter.ALL }
                    PrototypeChip("交易", filter == AssetEventFilter.TRADES) { filter = AssetEventFilter.TRADES }
                    PrototypeChip("资金流入", filter == AssetEventFilter.INFLOW) { filter = AssetEventFilter.INFLOW }
                    PrototypeChip("资金流出", filter == AssetEventFilter.OUTFLOW) { filter = AssetEventFilter.OUTFLOW }
                }
            }
            if (grouped.isEmpty()) {
                item {
                    PrototypeCard(modifier = Modifier.padding(top = 18.dp)) {
                        Text("暂无资产变动记录", color = FinColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp))
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
                Text("资产变动用于解释资产变化；内部转账不计入净流入，普通小额消费可按周或按月汇总记录。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PrototypeTradeRow(transaction: Transaction, accountName: String?, dateFormat: SimpleDateFormat) {
    val isBuy = transaction.type == TransactionType.BUY
    val label = when (transaction.type) {
        TransactionType.BUY -> "买入"
        TransactionType.SELL -> "卖出"
        TransactionType.DIVIDEND -> "分红/利息"
        TransactionType.FEE -> "费用"
        TransactionType.TRANSFER_IN -> "转入"
        TransactionType.TRANSFER_OUT -> "转出"
        TransactionType.DEPOSIT -> "收入/入金"
        TransactionType.WITHDRAW -> "支出/出金"
        TransactionType.LIABILITY_PAYMENT -> "还款"
    }
    val tradeColor = when {
        transaction.amount == 0.0 -> FinColors.TextSecondary
        transaction.type == TransactionType.BUY || transaction.type == TransactionType.DEPOSIT || transaction.type == TransactionType.DIVIDEND || transaction.type == TransactionType.TRANSFER_IN -> FinColors.Profit
        transaction.type == TransactionType.SELL || transaction.type == TransactionType.WITHDRAW || transaction.type == TransactionType.FEE || transaction.type == TransactionType.LIABILITY_PAYMENT || transaction.type == TransactionType.TRANSFER_OUT -> FinColors.Loss
        else -> FinColors.TextSecondary
    }
    val note = transaction.note.orEmpty()
    val source = listOf("快照录入", "手动录入", "截图识别导入")
        .firstOrNull { note.contains(it) }
    val displayName = if (source != null) {
        note.substringAfter(source).trim().substringBefore(" ·").ifBlank { normalizeSecurityCode(transaction.symbol.orEmpty()) }
    } else {
        note.substringBefore(" ·").ifBlank { normalizeSecurityCode(transaction.symbol.orEmpty()) }
    }
    PrototypeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = (if (isBuy) FinColors.Profit else FinColors.Loss).copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                Text(label.take(1), color = tradeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (source != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = FinColors.SurfaceElevated, shape = RoundedCornerShape(4.dp)) {
                            Text(source, color = FinColors.TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("${normalizeSecurityCode(transaction.symbol.orEmpty())} · ${accountName ?: "未知账户"} · ${dateFormat.format(Date(transaction.timestamp))}", color = FinColors.TextSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val sign = when (transaction.type) {
                    TransactionType.BUY, TransactionType.WITHDRAW, TransactionType.FEE, TransactionType.LIABILITY_PAYMENT, TransactionType.TRANSFER_OUT -> "-"
                    TransactionType.SELL, TransactionType.DEPOSIT, TransactionType.DIVIDEND, TransactionType.TRANSFER_IN -> "+"
                    else -> ""
                }
                MonoText("$sign${money(transaction.amount, transaction.currency)}", color = tradeColor, size = 11, weight = FontWeight.SemiBold)
                MonoText("${formatQuantity(transaction.shares)} @ ${transaction.price?.let { String.format(Locale.US, "%.2f", it) } ?: "—"}", color = FinColors.TextSecondary, size = 9)
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
    onSave: (isBuy: Boolean, accountId: String, name: String, securityCode: String, quantity: Double, price: Double, bucket: PrototypeBucket, timestamp: Long, currency: String, fee: Double, note: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isBuy by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var securityCode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var moreOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var selectedHoldingCurrency by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false } }
    var dateText by remember { mutableStateOf(dateFormat.format(Date())) }
    var error by remember { mutableStateOf<String?>(null) }
    val parsedQuantity = quantity.toDoubleOrNull()
    val parsedPrice = price.toDoubleOrNull()
    val fee = feeText.toDoubleOrNull() ?: 0.0
    val timestamp = remember(dateText) { runCatching { dateFormat.parse(dateText)?.time }.getOrNull() }
    val amount = (parsedQuantity ?: 0.0) * (parsedPrice ?: 0.0)
    val selectedCurrency = selectedHoldingCurrency
        ?: accounts.firstOrNull { it.account.id == selectedAccount }?.account?.currency
        ?: "CNY"

    Scaffold(
        modifier = modifier,
        containerColor = FinColors.PageBg,
        topBar = { PrototypeTopBar("记录交易", onBack) },
        bottomBar = {
            Surface(color = FinColors.PageBg) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = {
                            error = when {
                                securityCode.isBlank() -> "请填写证券代码"
                                parsedQuantity == null || parsedQuantity <= 0.0 -> "请输入有效成交数量"
                                parsedPrice == null || parsedPrice <= 0.0 -> "请输入有效成交价"
                                selectedAccount.isBlank() -> "请选择成交账户"
                                fee < 0.0 -> "费用不能为负数"
                                timestamp == null -> "日期格式应为 yyyy-MM-dd"
                                else -> null
                            }
                            if (error == null) onSave(isBuy, selectedAccount, name.trim().ifBlank { securityCode.trim() }, securityCode.trim(), parsedQuantity!!, parsedPrice!!, PrototypeBucket.AGGRESSIVE, timestamp!!, selectedCurrency, fee, noteText.trim().ifBlank { null })
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isBuy) FinColors.Profit else FinColors.Loss, contentColor = Color.White)
                    ) { Text("保存交易 · 更新资产", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeModeChip("买入", isBuy, FinColors.Profit) { isBuy = true }
                    TradeModeChip("卖出", !isBuy, FinColors.Loss) { isBuy = false }
                }
            }
            if (holdings.isNotEmpty()) {
                item {
                    PrototypeCard {
                        Text("已有资产（点击带入）", color = FinColors.TextSecondary, fontSize = 10.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            holdings.take(8).forEach { holding ->
                                PrototypeChip("${holding.displayName} · ${holding.code} · ${holding.currency}", false) {
                                    name = holding.displayName
                                    securityCode = holding.code
                                    selectedAccount = holding.accountIds.firstOrNull() ?: selectedAccount
                                    selectedHoldingCurrency = holding.currency.takeUnless { it == "MIXED" }
                                }
                            }
                        }
                    }
                }
            }
            item {
                PrototypeCard {
                    PrototypeField("证券代码", securityCode, { securityCode = it }, "如 510300.SS、AAPL、0700.HK")
                }
            }
            item {
                PrototypeCard {
                    PrototypeField("成交数量", quantity, { quantity = it }, "份 / 股", number = true)
                    PrototypeField("成交价", price, { price = it }, "元", number = true)
                    FinSettingRow(
                        label = "成交金额",
                        value = if (parsedQuantity != null && parsedPrice != null) money(amount, selectedCurrency) else "—"
                    )
                    Text("默认记录为今天", color = FinColors.TextSecondary, fontSize = 10.sp)
                }
            }
            item {
                PrototypeCard {
                    Text("成交账户", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { account ->
                            PrototypeChip(account.account.name, selectedAccount == account.account.id) {
                                selectedAccount = account.account.id
                                selectedHoldingCurrency = null
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { moreOptionsExpanded = !moreOptionsExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (moreOptionsExpanded) "收起更多选项" else "更多选项（费用、日期、备注）", color = FinColors.Secondary)
                }
            }
            if (moreOptionsExpanded) {
                item {
                    PrototypeCard {
                        PrototypeField("费用", feeText, { feeText = it }, "可选", number = true)
                        PrototypeField("成交日期", dateText, { dateText = it.take(10) }, "yyyy-MM-dd")
                        PrototypeField("备注", noteText, { noteText = it }, "可选")
                    }
                }
            }
            item { if (error != null) Text(error!!, color = FinColors.Danger, fontSize = 10.sp) }
            item { Text("卖出数量超过当前资产会被拦截；买入同码资产会按数量加权重算成本。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAllocationScreen(
    portfolioSummary: PortfolioSummary?,
    onSave: (Map<PrototypeBucket, Float>) -> Unit,
    onSaved: () -> Unit,
    onOpenPlanning: () -> Unit = {},
    onBack: () -> Unit = {},
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    if (summary == null) {
        Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("资产结构目标", onBack) }, bottomBar = bottomBar) { padding ->
            PrototypeLoadingSkeleton(Modifier.fillMaxSize().padding(padding))
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
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("资产结构目标", onBack) }, bottomBar = bottomBar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("资产结构目标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("设置防守、稳健、进攻的目标比例 · 合计须为 100%", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                OutlinedButton(
                    onClick = onOpenPlanning,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = CircleShape
                ) { Text("更多规划工具") }
            }
            item {
                PrototypeCard {
                    PrototypeBucket.entries.forEachIndexed { index, bucket ->
                        if (index > 0) Divider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))
                        val value = values[bucket] ?: 0f
                        val onChange: (Float) -> Unit = when (bucket) {
                            PrototypeBucket.DEFENSIVE -> { next: Float -> defensive = next }
                            PrototypeBucket.BALANCED -> { next: Float -> balanced = next }
                            PrototypeBucket.AGGRESSIVE -> { next: Float -> aggressive = next }
                        }
                        SliderCard(bucket, value, actual[bucket] ?: 0.0, onChange)
                    }
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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(bucket.color))
            Spacer(Modifier.width(8.dp))
            Text(bucket.label, color = bucket.color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            MonoText(String.format(Locale.US, "%.0f%%", value * 100), size = 16, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("${bucket.description} · 当前实际 ${String.format(Locale.US, "%.1f%%", actual * 100)}", color = FinColors.TextSecondary, fontSize = 10.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            steps = 0,
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
                BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxWidth(actualValue.toFloat().coerceIn(0f, 1f)).fillMaxSize().background(bucket.color))
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = ((maxWidth - 3.dp) * targetValue.coerceIn(0f, 1f)))
                            .width(3.dp)
                            .height(14.dp)
                            .background(Color.White)
                    )
                }
                MonoText(
                    signedPercent(actualValue - targetValue),
                    color = driftColor(actualValue - targetValue, threshold),
                    size = 11,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.width(58.dp).padding(start = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAccountsScreen(
    portfolioSummary: PortfolioSummary?,
    onViewAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, bottomBar = bottomBar) { padding ->
        val summary = portfolioSummary
        if (summary == null) {
            PrototypeLoadingSkeleton(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val rawCount = summary.assetRecords.size + summary.holdings.size
        val mergedCount = buildPrototypeHoldings(summary).size
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("账户与关键数据操作", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Text("账户", color = FinColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
            items(summary.accounts, key = { it.account.id }) { account ->
                AccountSourceRow(account, summary, onClick = { onViewAccount(account.account.id) })
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = FinColors.Outline,
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                            )
                        }
                        .clickable(onClick = onAddAccount),
                    color = Color.Transparent,
                    shape = FinShapes.md
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = FinColors.TextPrimary)
                        Spacer(Modifier.width(5.dp))
                        Text("添加账户", color = FinColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
                    color = FinColors.SurfaceElevated,
                    shape = FinShapes.md
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("数据与安全、设置和更多工具", fontWeight = FontWeight.SemiBold)
                            Text("导入、备份、导出、隐私与低频工具", color = FinColors.TextSecondary, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "打开设置", tint = FinColors.TextSecondary)
                    }
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
            Box(Modifier.size(40.dp).clip(CircleShape).background(FinColors.Cash.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(if (summary.account.type == AccountType.BROKER) Icons.Default.AccountBalance else Icons.Default.Person, contentDescription = null, tint = FinColors.Secondary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(summary.account.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${summary.account.type.displayName()} · ${summary.account.currency}", color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                val accountValue = portfolioSummary.assetRecords
                    .filter { it.record.accountId == summary.account.id }
                    .sumOf { it.currentValue }
                    .plus(portfolioSummary.holdings.filter { it.position.accountId == summary.account.id }.sumOf { it.currentValue })
                MonoText(money(accountValue, portfolioSummary.baseCurrency), size = 12, weight = FontWeight.SemiBold)
                Text("$count 项资产", color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                Text(if (stale) "数据可能过期" else "已同步 $updated", color = if (stale) FinColors.Warning else FinColors.Success, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
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
        Text("资产详情会按证券编码归集来源；同名不同码资产仍分别展示。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun MergeBox(text: String) {
    Surface(color = FinColors.Surface, shape = RoundedCornerShape(8.dp)) { Text(text, color = FinColors.TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAddSourceScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onScreenshot: () -> Unit,
    onManual: () -> Unit,
    onRecordTrade: () -> Unit = {},
    onRecordCashFlow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("添加记录", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("选择要记录的资产事件", color = FinColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp)) }
            item { SourceOptionCard("截图导入", "从券商或基金 App 截图识别资产，本地确认后保存", FinColors.Cash, Icons.Default.CropFree, true, onScreenshot) }
            item { SourceOptionCard("手动添加资产", "逐项填写非交易型资产或初始持仓", FinColors.Aggressive, Icons.Default.Edit, false, onManual) }
            item { SourceOptionCard("记录交易", "记录买入或卖出，自动更新数量与成本", FinColors.Secondary, Icons.Default.ReceiptLong, false, onRecordTrade) }
            item { SourceOptionCard("记录资金变动", "记录收入、大额支出、转账或分红等重要事件", FinColors.Warning, Icons.Default.SwapHoriz, false, onRecordCashFlow) }
            item {
                TextButton(onClick = onAddAccount, modifier = Modifier.fillMaxWidth()) {
                    Text("还没有归属账户？先创建账户", color = FinColors.Secondary)
                }
            }
            item {
                PrototypeCard(color = FinColors.SurfaceElevated) {
                    Text("识别方式与隐私", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("衡仓不连接券商、不会索取登录信息或交易权限。只有经你确认，所选截图才会发送到 FinUnity 专属服务解析；返回结果需再次确认后才会保存。", color = FinColors.TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
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
    val context = LocalContext.current
    val screenshotImportRepository = remember(context.applicationContext) {
        ScreenshotImportRepository(context.applicationContext)
    }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var selectedRows by remember { mutableStateOf(emptySet<Int>()) }
    var rows by remember { mutableStateOf(emptyList<OcrHolding>()) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognitionError by remember { mutableStateOf<String?>(null) }
    var uploadApproved by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        uploadApproved = false
    }
    if (selectedUri != null && !uploadApproved) {
        AlertDialog(
            onDismissRequest = { selectedUri = null },
            title = { Text("发送图片进行解析") },
            text = { Text("所选持仓截图将通过加密连接发送到 FinUnity 专属服务。服务端返回结构化结果后仍需你逐项确认，解析错误会直接显示，不会填充模拟数据。") },
            confirmButton = {
                TextButton(onClick = { uploadApproved = true }) { Text("同意并解析") }
            },
            dismissButton = {
                TextButton(onClick = { selectedUri = null }) { Text("取消") }
            }
        )
    }
    LaunchedEffect(selectedUri, uploadApproved) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (!uploadApproved) return@LaunchedEffect
        isRecognizing = true
        recognitionError = null
        rows = emptyList()
        selectedRows = emptySet()
        try {
            val parsed = screenshotImportRepository.recognize(uri)
            rows = parsed.map {
                OcrHolding(
                    name = it.name,
                    securityCode = it.securityCode,
                    quantity = ocrNumberText(it.quantity),
                    currentPrice = ocrNumberText(it.currentPrice),
                    cost = it.totalCost?.let(::ocrNumberText).orEmpty(),
                    currency = it.currency,
                    needsReview = it.needsReview,
                    rawText = it.rawText,
                    rawSecurityCode = it.rawSecurityCode,
                    confidence = it.confidence.name,
                    reviewReason = it.reviewReason.orEmpty(),
                    instrumentId = it.instrumentId
                )
            }
            selectedRows = rows.indices.toSet()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recognitionError = "识别失败：${error.message ?: "请换一张清晰的持仓截图后重试"}"
        } finally {
            isRecognizing = false
        }
    }
    val selectedHoldings = rows.filterIndexed { index, _ -> index in selectedRows }
    val invalidSelectionCount = selectedHoldings.count { !it.isReadyForImport }
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("截图识别", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PrototypeCard(modifier = Modifier.clickable { launcher.launch("image/*") }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF26385F)), contentAlignment = Alignment.Center) { Icon(Icons.Default.CropFree, contentDescription = "扫描截图", tint = FinColors.Secondary, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text(if (selectedUri == null) "选择持仓截图" else "重新选择截图", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("图片将发送到 FinUnity 专属服务解析", color = FinColors.TextSecondary, fontSize = 9.sp)
                    }
                }
            }
            if (isRecognizing) {
                item { Text("正在通过 FinUnity 专属服务解析截图…", color = FinColors.TextSecondary, fontSize = 10.sp) }
            }
            recognitionError?.let { error -> item { Text(error, color = FinColors.Danger, fontSize = 10.sp) } }
            if (selectedUri != null && !isRecognizing && recognitionError == null && rows.isEmpty()) {
                item {
                    PrototypeCard(color = FinColors.SurfaceElevated) {
                        Text("没有识别到可导入的持仓", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("请截取包含“名称、代码、持仓数量、市值”的完整持仓列表，并确保文字清晰。", color = FinColors.TextSecondary, fontSize = 10.sp)
                    }
                }
            }
            if (rows.isNotEmpty()) {
                item { Text("本地识别结果 · 请逐条核对后保存", color = FinColors.TextSecondary, fontSize = 9.sp) }
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
                            OcrRow(
                                row = row,
                                checked = index in selectedRows,
                                onChecked = { selectedRows = if (index in selectedRows) selectedRows - index else selectedRows + index },
                                onChange = { updated -> rows = rows.toMutableList().also { it[index] = updated } }
                            )
                        }
                    }
                }
                item {
                    if (invalidSelectionCount > 0) {
                        Text("请为已勾选的持仓补齐总成本，并核对名称、代码、数量和当前价。", color = FinColors.Warning, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onImport(selectedAccount, selectedHoldings) },
                        enabled = selectedHoldings.isNotEmpty() && invalidSelectionCount == 0 && selectedAccount.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)
                    ) { Text("确认导入 ${selectedHoldings.size} 项", fontWeight = FontWeight.Bold) }
                }
            }
            if (selectedUri == null) {
                item {
                    PrototypeCard(color = FinColors.SurfaceElevated) {
                        Text("最近识别", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = FinColors.TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("上传第一张持仓截图后，识别记录会显示在这里", color = FinColors.TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
            item { Text("市值不会被当作成本导入。每项持仓都会按证券代码合并；带“需确认”的行尤其应核对。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun OcrRow(row: OcrHolding, checked: Boolean, onChecked: () -> Unit, onChange: (OcrHolding) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onChecked), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) FinColors.Conservative else Color.Transparent).then(Modifier), contentAlignment = Alignment.Center) {
                if (checked) Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) else Box(Modifier.fillMaxSize().background(Color.Transparent))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (row.needsReview) {
                        Spacer(Modifier.width(6.dp))
                        Text("需确认", color = FinColors.Cash, fontSize = 8.sp)
                    }
                }
                MonoText("${row.securityCode} · ${row.currency}", color = FinColors.TextSecondary, size = 9)
                if (row.needsReview && row.reviewReason.isNotBlank()) {
                    Text(row.reviewReason, color = FinColors.Cash, fontSize = 8.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OcrImportField("名称", row.name) { onChange(row.copy(name = it)) }
        OcrImportField("证券代码", row.securityCode) { onChange(row.copy(securityCode = it.uppercase())) }
        OcrImportField("持有数量", row.quantity) { onChange(row.copy(quantity = it)) }
        OcrImportField("当前价", row.currentPrice) { onChange(row.copy(currentPrice = it)) }
        OcrImportField("总成本（必填）", row.cost) { onChange(row.copy(cost = it)) }
    }
}

@Composable
private fun OcrImportField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textStyle = TextStyle(fontSize = 12.sp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeManualEntryScreen(
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onImportWithScreenshot: () -> Unit,
    onSave: (AssetRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var securityCode by rememberSaveable { mutableStateOf("") }
    var selectedAssetType by rememberSaveable { mutableStateOf(AssetType.ETF) }
    var currency by rememberSaveable { mutableStateOf("CNY") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var costPrice by rememberSaveable { mutableStateOf("") }
    var currentPrice by rememberSaveable { mutableStateOf("") }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.account?.id.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(modifier = modifier, containerColor = FinColors.PageBg, topBar = { PrototypeTopBar("手动录入", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PrototypeField("资产名称", name, { name = it }, "如 沪深300ETF、标普500基金、现金") }
            item { PrototypeField("证券代码（现金可留空）", securityCode, { securityCode = it }, "如 510300.SS、AAPL、0700.HK") }
            item {
                PrototypeCard {
                    Text("资产类型（自动归入三桶）", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssetType.entries.forEach { type ->
                            PrototypeChip(assetTypeLabel(type), selectedAssetType == type) { selectedAssetType = type }
                        }
                    }
                    Text(
                        "默认归桶：${assetTypeLabel(selectedAssetType)} → ${riskBucketLabel(selectedAssetType.defaultRiskBucket())}",
                        color = FinColors.TextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            item {
                PrototypeCard {
                    PrototypeField("币种", currency, { currency = it.uppercase() }, "CNY / USD / HKD")
                    PrototypeField("持有数量", quantity, { quantity = it }, "份 / 股", number = true)
                    PrototypeField("单位成本价", costPrice, { costPrice = it }, "元", number = true)
                    PrototypeField("当前价", currentPrice, { currentPrice = it }, "元", number = true)
                }
            }
            item {
                PrototypeCard {
                    Text("归属账户", color = FinColors.TextSecondary, fontSize = 10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { accounts.forEach { account -> PrototypeChip(account.account.name, selectedAccount == account.account.id) { selectedAccount = account.account.id } } }
                }
            }
            item {
                TextButton(onClick = onImportWithScreenshot, modifier = Modifier.fillMaxWidth()) {
                    Text("持仓较多？改用截图识别导入", color = FinColors.Secondary)
                }
            }
            item {
                if (error != null) Text(error!!, color = FinColors.Danger, fontSize = 10.sp)
                Button(onClick = {
                    val qty = quantity.toDoubleOrNull()
                    val cost = costPrice.toDoubleOrNull()
                    val latest = currentPrice.toDoubleOrNull()
                    val finalName = name.trim().ifBlank { securityCode.trim() }.ifBlank { if (selectedAssetType == AssetType.CASH) "现金" else "" }
                    if (finalName.isBlank() || (selectedAssetType != AssetType.CASH && securityCode.isBlank()) ||
                        qty == null || qty <= 0 || cost == null || cost <= 0 || latest == null || latest <= 0 ||
                        currency.isBlank() || selectedAccount.isBlank()
                    ) {
                        error = "请填写名称、类型、币种、数量、成本价、当前价和账户"
                    } else {
                        val accountCurrency = accounts.firstOrNull { it.account.id == selectedAccount }?.account?.currency ?: "CNY"
                        onSave(
                            AssetRecord(
                                accountId = selectedAccount,
                                assetType = selectedAssetType,
                                riskBucket = selectedAssetType.defaultRiskBucket(),
                                name = finalName,
                                securityCode = if (selectedAssetType == AssetType.CASH) "" else securityCode.trim(),
                                quantity = qty,
                                cost = qty * cost,
                                currentPrice = latest,
                                currency = currency.trim().uppercase().ifBlank { accountCurrency }
                            )
                        )
                    }
                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FinColors.PageBg)) { Text("保存并加入汇总", fontWeight = FontWeight.Bold) }
            }
            item { Text("保存后立即参与总市值、策略占比与偏移计算；同编码持仓自动合并。", color = FinColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        }
    }
}

@Composable
private fun PrototypeField(label: String, value: String, onValueChange: (String) -> Unit, hint: String, number: Boolean = false) {
    FinInlineField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = hint,
        keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text
    )
}

private fun assetTypeLabel(type: AssetType): String = when (type) {
    AssetType.STOCK -> "股票"
    AssetType.ETF -> "ETF"
    AssetType.FUND -> "基金"
    AssetType.CASH -> "现金"
    AssetType.TIME_DEPOSIT -> "定期"
    AssetType.REAL_ESTATE -> "房产"
    AssetType.VEHICLE -> "车辆"
    AssetType.INSURANCE_POLICY -> "保单"
}

private fun riskBucketLabel(bucket: RiskBucket): String = when (bucket) {
    RiskBucket.DEFENSIVE -> "防守"
    RiskBucket.BALANCED -> "稳健"
    RiskBucket.AGGRESSIVE -> "进攻"
}

@Composable
private fun TradeModeChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) color else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, FinColors.Outline)
    ) {
        Text(text, color = if (selected) Color.White else FinColors.TextSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
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
