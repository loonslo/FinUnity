package com.finunity.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.theme.FinSizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHubScreen(
    accounts: List<AccountSummary>,
    assetRecords: List<AssetRecordSummary>,
    holdings: List<HoldingSummary>,
    baseCurrency: String,
    onViewAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenPriceChanges: () -> Unit = {},
    amountsVisible: Boolean = true,
    onToggleAmounts: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val totalBalance = accounts.sumOf { it.balanceInBaseCurrency }
    val totalProfit = assetRecords.sumOf { it.profitLoss } + holdings.sumOf { it.profitLoss }

    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinColors.PageBg,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinColors.PageBg)
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                TotalAssetOverviewCard(
                    totalBalance = totalBalance,
                    totalProfit = totalProfit,
                    accountCount = accounts.size,
                    baseCurrency = baseCurrency,
                    amountsVisible = amountsVisible,
                    onToggleAmounts = onToggleAmounts,
                    onOpenTransactions = onOpenTransactions,
                    onOpenPriceChanges = onOpenPriceChanges
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
private fun EyeToggleIcon(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val eyeTop = size.height * 0.26f
        val eyeHeight = size.height * 0.48f
        drawOval(
            color = FinColors.TextSecondary,
            topLeft = Offset(size.width * 0.08f, eyeTop),
            size = Size(size.width * 0.84f, eyeHeight),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = FinColors.TextSecondary,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width * 0.5f, size.height * 0.5f)
        )
        if (!visible) {
            drawLine(
                color = FinColors.TextSecondary,
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
    accountCount: Int,
    baseCurrency: String,
    amountsVisible: Boolean,
    onToggleAmounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenPriceChanges: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    color = FinColors.TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleAmounts,
                        modifier = Modifier.size(36.dp)
                    ) {
                        EyeToggleIcon(
                            visible = amountsVisible,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    TextButton(
                        onClick = onOpenTransactions,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "交易记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                    }
                    TextButton(
                        onClick = onOpenPriceChanges,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "价格变化",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = hiddenAware(formatCurrency(totalBalance, baseCurrency), amountsVisible),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = FinColors.Number
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "元",
                    style = MaterialTheme.typography.titleMedium,
                    color = FinColors.TextSecondary,
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
                    label = "账户数",
                    value = "$accountCount 个",
                    valueColor = FinColors.TextPrimary
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
            color = FinColors.TextSecondary
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
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val assetValue = assetRecords.sumOf { it.currentValue } + holdings.sumOf { it.currentValue }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${summary.account.type.displayName()} · ${summary.account.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinColors.TextSecondary
                    )
                }
                Text(
                    text = hiddenAware(formatCurrency(assetValue, baseCurrency), amountsVisible),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (assetValue < 0) MaterialTheme.colorScheme.error else FinColors.Number
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户详情",
                    tint = FinColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            AccountMetricRow(
                label = "资产数量",
                value = "${holdingCount} 项"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AccountMetricRow(
                label = "累计收益",
                value = hiddenAware(formatSignedCurrency(profit, baseCurrency), amountsVisible),
                valueColor = profitColorFor(profit)
            )
            if (profitRate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AccountMetricRow(
                    label = "收益率",
                    value = formatAssetSignedPercent(profitRate),
                    valueColor = profitColorFor(profitRate)
                )
            }
        }
    }
}

@Composable
private fun AccountMetricRow(
    label: String,
    value: String,
    valueColor: Color = FinColors.TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FinColors.TextSecondary
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
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = FinShapes.md,
                color = FinColors.Accent.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = FinColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂时还没有账户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FinColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "先添加一个账户，开始记录你的资产",
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddAccount,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加账户", color = FinColors.Number)
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
    value > 0 -> FinColors.Profit
    value < 0 -> FinColors.Loss
    else -> FinColors.TextPrimary
}

@Composable
fun AccountAssetsByAccountScreen(
    accounts: List<AccountSummary>,
    assetRecords: List<AssetRecordSummary>,
    holdings: List<HoldingSummary>,
    baseCurrency: String,
    onAddAccount: () -> Unit,
    onEditAccount: (com.finunity.data.local.entity.Account) -> Unit,
    onOpenImportCsv: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit = {},
    amountsVisible: Boolean = true,
    onToggleAmounts: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinColors.PageBg,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinColors.PageBg)
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                AccountProfileHeader()
            }
            if (accounts.isEmpty()) {
                item { AccountEmptyManagementCard(onAddAccount = onAddAccount) }
            } else {
                item {
                    FinSectionLabel("账户管理")
                }
                items(accounts, key = { it.account.id }) { summary ->
                    val assetValue = assetRecords.filter { it.record.accountId == summary.account.id }.sumOf { it.currentValue } +
                        holdings.filter { it.position.accountId == summary.account.id }.sumOf { it.currentValue }
                    ManagementAccountCard(
                        summary = summary,
                        assetValue = assetValue,
                        baseCurrency = baseCurrency,
                        amountsVisible = amountsVisible,
                        onView = { onEditAccount(summary.account) }
                    )
                }
            }
            item {
                AccountToolsCard(
                    accountCount = accounts.size,
                    onAddAccount = onAddAccount,
                    onOpenImportCsv = onOpenImportCsv,
                    onOpenSettings = onOpenSettings,
                    onOpenBackup = onOpenBackup
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}


@Composable
private fun AccountProfileHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = FinShapes.xl,
                color = FinColors.Accent.copy(alpha = 0.09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "账",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = FinColors.Accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "账户与数据",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = FinColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本地账本、统一查看、按用途管理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinColors.TextSecondary
                )
            }
//            Text(
//                text = "${accountCount} 个账户",
//                style = MaterialTheme.typography.bodySmall,
//                color = FinColors.TextSecondary
//            )
        }
    }
}

@Composable
private fun AccountToolsCard(
    accountCount: Int,
    onAddAccount: () -> Unit,
    onOpenImportCsv: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "数据工具",
            style = MaterialTheme.typography.labelMedium,
            color = FinColors.TextSecondary.copy(alpha = 0.72f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryToolChip(
                text = "添加账户",
                onClick = onAddAccount,
                modifier = Modifier.weight(1f)
            )
            SecondaryToolChip(
                text = if (accountCount == 0) "导入表格" else "导入数据",
                onClick = onOpenImportCsv,
                modifier = Modifier.weight(1f)
            )
            SecondaryToolChip(
                text = "备份恢复",
                onClick = onOpenBackup,
                modifier = Modifier.weight(1f)
            )
            SecondaryToolChip(
                text = "偏好设置",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SecondaryToolChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, FinColors.Outline.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun AccountEmptyManagementCard(onAddAccount: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                color = FinColors.Accent.copy(alpha = 0.09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = FinColors.Accent,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "添加你的第一个账户",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = FinColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "用账户来归类银行卡、证券和现金资产",
                style = MaterialTheme.typography.bodyMedium,
                color = FinColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onAddAccount,
                shape = FinShapes.md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加账户", color = FinColors.Number)
            }
        }
    }
}

@Composable
private fun ManagementAccountCard(
    summary: AccountSummary,
    assetValue: Double,
    baseCurrency: String,
    amountsVisible: Boolean = true,
    onView: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                shape = FinShapes.md,
                color = FinColors.Accent.copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = summary.account.name.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FinColors.Accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    summary.account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${summary.account.type.displayName()} · ${summary.account.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "账户资料与持仓管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinColors.TextSecondary.copy(alpha = 0.72f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "资产",
                    style = MaterialTheme.typography.labelSmall,
                    color = FinColors.TextSecondary.copy(alpha = 0.72f)
                )
                Text(
                    text = hiddenAware(formatCurrency(assetValue, baseCurrency), amountsVisible),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (assetValue < 0) MaterialTheme.colorScheme.error.copy(alpha = 0.72f) else FinColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户",
                    tint = FinColors.TextSecondary.copy(alpha = 0.55f)
                )
            }
        }
    }
}
