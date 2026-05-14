package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.HoldingSummary
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHubScreen(
    accounts: List<AccountSummary>,
    assetRecords: List<AssetRecordSummary>,
    holdings: List<HoldingSummary>,
    baseCurrency: String,
    onBack: () -> Unit,
    onViewAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenTransactions: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalBalance = accounts.sumOf { it.balanceInBaseCurrency }
    val totalProfit = assetRecords.sumOf { it.profitLoss } + holdings.sumOf { it.profitLoss }
    var amountsVisible by remember { mutableStateOf(true) }

    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinAssetPage,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinAssetPage)
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                AccountHomeHeader(
                    amountsVisible = amountsVisible,
                    onBack = onBack,
                    onToggleAmounts = { amountsVisible = !amountsVisible }
                )
            }

            item {
                TotalAssetOverviewCard(
                    totalBalance = totalBalance,
                    totalProfit = totalProfit,
                    baseCurrency = baseCurrency,
                    amountsVisible = amountsVisible,
                    onOpenTransactions = onOpenTransactions
                )
            }

            if (accounts.isEmpty()) {
                item {
                    EmptyAccountState(onAddAccount = onAddAccount)
                }
            } else {
                item {
                    FinSectionLabel("账户")
                }
                items(accounts, key = { it.account.id }) { summary ->
                    AccountAssetCard(
                        summary = summary,
                        assetRecords = assetRecords.filter { it.record.accountId == summary.account.id },
                        holdings = holdings.filter { it.position.accountId == summary.account.id },
                        baseCurrency = baseCurrency,
                        amountsVisible = amountsVisible,
                        onClick = { onViewAccount(summary.account.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AccountHomeHeader(
    amountsVisible: Boolean,
    onBack: () -> Unit,
    onToggleAmounts: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = FinTextPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "我的资产",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = FinTextPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onToggleAmounts,
            modifier = Modifier.size(40.dp)
        ) {
            EyeToggleIcon(
                visible = amountsVisible,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EyeToggleIcon(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val eyeTop = size.height * 0.26f
        val eyeHeight = size.height * 0.48f
        drawOval(
            color = FinTextSecondary,
            topLeft = Offset(size.width * 0.08f, eyeTop),
            size = Size(size.width * 0.84f, eyeHeight),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = FinTextSecondary,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width * 0.5f, size.height * 0.5f)
        )
        if (!visible) {
            drawLine(
                color = FinTextSecondary,
                start = Offset(size.width * 0.14f, size.height * 0.86f),
                end = Offset(size.width * 0.86f, size.height * 0.14f),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
private fun TotalAssetOverviewCard(
    totalBalance: Double,
    totalProfit: Double,
    baseCurrency: String,
    amountsVisible: Boolean,
    onOpenTransactions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总资产",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinTextPrimary
                )
                TextButton(
                    onClick = onOpenTransactions,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "交易记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = hiddenAware(formatCurrency(totalBalance, baseCurrency), amountsVisible),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = FinNumber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "元",
                    style = MaterialTheme.typography.titleMedium,
                    color = FinTextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssetMetricItem(
                    label = "累计收益",
                    value = hiddenAware(formatSignedCurrency(totalProfit, baseCurrency), amountsVisible),
                    valueColor = profitColorFor(totalProfit)
                )
                AssetMetricItem(
                    label = "加权收益率",
                    value = "--",
                    valueColor = FinTextPrimary
                )
                AssetMetricItem(
                    label = "年化收益率",
                    value = "--",
                    valueColor = FinTextPrimary
                )
            }
        }
    }
}

@Composable
private fun AssetMetricItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FinTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun AccountAssetCard(
    summary: AccountSummary,
    assetRecords: List<AssetRecordSummary>,
    holdings: List<HoldingSummary>,
    baseCurrency: String,
    amountsVisible: Boolean,
    onClick: () -> Unit
) {
    val profit = assetRecords.sumOf { it.profitLoss } + holdings.sumOf { it.profitLoss }
    val cost = assetRecords.sumOf { it.costInBaseCurrency } + holdings.sumOf { it.position.totalCost }
    val profitRate = if (cost > 0.0) profit / cost else null
    val holdingCount = assetRecords.size + holdings.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${summary.account.type.displayName()} · ${summary.account.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTextSecondary
                    )
                }
                Text(
                    text = hiddenAware(formatCurrency(summary.balanceInBaseCurrency, baseCurrency), amountsVisible),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.balanceInBaseCurrency < 0) MaterialTheme.colorScheme.error else FinNumber
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户详情",
                    tint = FinTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            AccountMetricRow(
                label = "持仓数量",
                value = "${holdingCount} 项"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccountMetricRow(
                label = "累计收益",
                value = hiddenAware(formatSignedCurrency(profit, baseCurrency), amountsVisible),
                valueColor = profitColorFor(profit)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccountMetricRow(
                label = "收益率",
                value = profitRate?.let { formatAssetSignedPercent(it) } ?: "--",
                valueColor = profitRate?.let { profitColorFor(it) } ?: FinTextPrimary
            )
        }
    }
}

@Composable
private fun AccountMetricRow(
    label: String,
    value: String,
    valueColor: Color = FinTextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FinTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun EmptyAccountState(onAddAccount: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = FinAccent.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = FinAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂时还没有账户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FinTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "先添加一个账户，开始记录你的资产",
                style = MaterialTheme.typography.bodySmall,
                color = FinTextSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加账户")
            }
        }
    }
}

private fun hiddenAware(value: String, visible: Boolean): String = if (visible) value else "****"

private fun formatSignedCurrency(amount: Double, currency: String): String {
    if (amount.isNaN() || amount.isInfinite()) return "--"
    val prefix = if (amount > 0) "+" else ""
    return prefix + formatCurrency(amount, currency)
}

private fun formatAssetSignedPercent(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "--"
    val prefix = if (value > 0) "+" else ""
    return "$prefix${String.format("%.1f", value * 100)}%"
}

private fun profitColorFor(value: Double): Color = when {
    value > 0 -> FinProfit
    value < 0 -> FinLoss
    else -> FinTextPrimary
}

private val FinAssetPage = Color(0xFFF7F8FA)
private val FinTextPrimary = Color(0xFF1F2933)
private val FinTextSecondary = Color(0xFFA0A4AA)
private val FinNumber = Color(0xFF111827)
private val FinAccent = Color(0xFF1E8E5A)
private val FinProfit = Color(0xFF1E8E5A)
private val FinLoss = Color(0xFFE53935)

@Composable
fun AccountAssetsByAccountScreen(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onViewAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinAssetPage,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinAssetPage)
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                AccountProfileHeader(
                    onAddAccount = onAddAccount
                )
            }
            if (accounts.isEmpty()) {
                item {
                    AccountEmptyManagementCard(onAddAccount = onAddAccount)
                }
            } else {
                items(accounts, key = { it.account.id }) { summary ->
                    ManagementAccountCard(
                        summary = summary,
                        baseCurrency = baseCurrency,
                        onView = { onViewAccount(summary.account.id) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}


@Composable
private fun AccountProfileHeader(
    onAddAccount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(24.dp),
                color = FinAccent.copy(alpha = 0.09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "F",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FinAccent
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FinUnity",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = FinTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "个人资产账本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTextSecondary
                )
            }
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onAddAccount),
                shape = RoundedCornerShape(18.dp),
                color = FinAccent.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加账户",
                        tint = FinAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountEmptyManagementCard(onAddAccount: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(22.dp),
                color = FinAccent.copy(alpha = 0.09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = FinAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "添加你的第一个账户",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = FinTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "用账户来归类银行卡、证券和现金资产",
                style = MaterialTheme.typography.bodyMedium,
                color = FinTextSecondary
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinAccent)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加账户")
            }
        }
    }
}

@Composable
private fun ManagementAccountCard(
    summary: AccountSummary,
    baseCurrency: String,
    onView: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = FinAccent.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = summary.account.name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FinAccent
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = FinTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${summary.account.type.displayName()} · ${summary.account.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "账户资料与持仓管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTextSecondary.copy(alpha = 0.72f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "资产",
                    style = MaterialTheme.typography.labelSmall,
                    color = FinTextSecondary.copy(alpha = 0.72f)
                )
                Text(
                    text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (summary.balanceInBaseCurrency < 0) MaterialTheme.colorScheme.error.copy(alpha = 0.72f) else FinTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户",
                    tint = FinTextSecondary.copy(alpha = 0.55f)
                )
            }
        }
    }
}
