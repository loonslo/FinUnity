package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.displayName
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinTopBar
import java.util.Locale
import com.finunity.ui.components.FinBucketTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    account: Account,
    assetRecords: List<AssetRecordSummary>,
    baseCurrency: String,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onRecordCashFlow: () -> Unit,
    onAddRecord: () -> Unit,
    onEditRecord: (AssetRecord) -> Unit,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recordsForAccount = assetRecords.filter { it.record.accountId == account.id }

    Scaffold(
        topBar = {
            FinTopBar(account.name, onBack, actions = {
                IconButton(onClick = onEditAccount) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑账户")
                }
            })
        },
        containerColor = FinColors.PageBg,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinColors.PageBg)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 账户信息卡片
            item {
                val assetValue = recordsForAccount.sumOf { it.currentValue }
                AccountInfoCard(
                    account = account,
                    assetValue = assetValue,
                    baseCurrency = baseCurrency
                )
            }

            item {
                AccountActionRow(
                    onAddRecord = onAddRecord,
                    onRecordCashFlow = onRecordCashFlow,
                    onViewTransactions = onViewTransactions
                )
            }

            // 统计概览
            if (recordsForAccount.isNotEmpty()) {
                item {
                    AccountStatsRow(
                        records = recordsForAccount,
                        baseCurrency = baseCurrency
                    )
                }
            }

            // 资产列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "资产 (${recordsForAccount.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            if (recordsForAccount.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinShapes.md,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "暂无资产",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onAddRecord) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("添加资产")
                                }
                            }
                        }
                    }
                }
            } else {
                items(recordsForAccount) { record ->
                    AccountRecordItem(
                        summary = record,
                        baseCurrency = baseCurrency,
                        onClick = { onEditRecord(record.record) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AccountActionRow(
    onAddRecord: () -> Unit,
    onRecordCashFlow: () -> Unit,
    onViewTransactions: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAddRecord,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = FinColors.SoftGreen,
                contentColor = FinColors.Number
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = FinColors.Number)
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加记录", color = FinColors.TextPrimary)
        }
        Button(
            onClick = onRecordCashFlow,
            modifier = Modifier.weight(1f).height(54.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = FinColors.SurfaceElevated, contentColor = FinColors.TextPrimary)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("资金变动")
        }
    }
    TextButton(onClick = onViewTransactions, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("查看资产变动")
    }
}

@Composable
fun AccountInfoCard(
    account: Account,
    assetValue: Double,
    baseCurrency: String
) {
    val isLiability = account.type == com.finunity.data.local.entity.AccountType.LIABILITY

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(
            containerColor = FinColors.Surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 账户名称和类型
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${account.type.displayName()} · ${account.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLiability) "负债金额" else "账户资产",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = formatCurrency(assetValue, baseCurrency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isLiability) MaterialTheme.colorScheme.error else FinColors.TextPrimary
                )
            }

            if (isLiability) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        if (account.initialPrincipal > 0) append("初始本金 ${formatCurrency(account.initialPrincipal, account.currency)}")
                        if (account.annualInterestRate > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("年利率 ${String.format(Locale.US, "%.2f", account.annualInterestRate * 100)}%")
                        }
                        if (account.dueDayOfMonth > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("每月${account.dueDayOfMonth}日还款")
                        }
                        if (account.minimumPayment > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("最低 ${formatCurrency(account.minimumPayment, account.currency)}")
                        }
                    }.ifBlank { "可在编辑账户中补充利率、还款日和最低还款额" },
                    style = MaterialTheme.typography.bodySmall,
                    color = FinColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun AccountStatsRow(
    records: List<AssetRecordSummary>,
    baseCurrency: String
) {
    val totalValue = records.sumOf { it.currentValue }
    val incomeRecords = records.filter { it.record.assetType != AssetType.CASH }
    val totalCost = incomeRecords.sumOf { it.costInBaseCurrency }
    val totalProfitLoss = incomeRecords.sumOf { it.profitLoss }
    val profitLossRatio = if (totalCost > 0) totalProfitLoss / totalCost else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCell("市值", formatCurrency(totalValue, baseCurrency), FinColors.TextPrimary)
            if (incomeRecords.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.08f)))
                StatCell("盈亏", formatSignedMoney(totalProfitLoss, baseCurrency), when {
                    totalProfitLoss > 0 -> FinColors.Profit
                    totalProfitLoss < 0 -> FinColors.Loss
                    else -> FinColors.TextSecondary
                })
                Box(Modifier.width(1.dp).height(34.dp).background(Color.White.copy(alpha = 0.08f)))
                StatCell("收益率", formatSignedPercent(profitLossRatio), when {
                    profitLossRatio > 0 -> FinColors.Profit
                    profitLossRatio < 0 -> FinColors.Loss
                    else -> FinColors.TextSecondary
                })
            }
        }
    }
}

@Composable
private fun RowScope.StatCell(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = FinColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(
            containerColor = FinColors.Surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
fun AccountRecordItem(
    summary: AssetRecordSummary,
    baseCurrency: String,
    onClick: () -> Unit
) {
    val profitColor = when {
        summary.profitLoss > 0 -> FinColors.Profit
        summary.profitLoss < 0 -> FinColors.Loss
        else -> FinColors.TextSecondary
    }
    val isCash = summary.record.assetType == AssetType.CASH

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = summary.record.name.take(2),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.record.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(summary.record.assetType.displayName(), style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    if (!isCash) {
                        val bucketColor = when (summary.record.riskBucket) {
                            com.finunity.data.local.entity.RiskBucket.DEFENSIVE -> FinColors.Cash
                            com.finunity.data.local.entity.RiskBucket.BALANCED -> FinColors.Conservative
                            com.finunity.data.local.entity.RiskBucket.AGGRESSIVE -> FinColors.Aggressive
                        }
                        FinBucketTag(summary.record.riskBucket.displayName(), bucketColor)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(summary.currentValue, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isCash) {
                    Text(
                        text = formatSignedPercent(summary.profitLossRatio),
                        style = MaterialTheme.typography.bodySmall,
                        color = profitColor
                    )
                }
            }
        }
    }
}
