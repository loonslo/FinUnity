package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    account: Account,
    accountSummary: AccountSummary?,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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

            // 账户信息卡片
            item {
                AccountInfoCard(
                    account = account,
                    balanceInBaseCurrency = accountSummary?.balanceInBaseCurrency ?: account.balance,
                    baseCurrency = baseCurrency
                )
            }

            item {
                AccountActionRow(
                    onRecordCashFlow = onRecordCashFlow,
                    onAddRecord = onAddRecord,
                    onViewTransactions = onViewTransactions,
                    onEditAccount = onEditAccount
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

            // 持仓列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "持仓 (${recordsForAccount.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    TextButton(onClick = onAddRecord) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("添加")
                    }
                }
            }

            if (recordsForAccount.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "暂无持仓",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onAddRecord) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("添加持仓")
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
private fun AccountActionRow(
    onRecordCashFlow: () -> Unit,
    onAddRecord: () -> Unit,
    onViewTransactions: () -> Unit,
    onEditAccount: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRecordCashFlow,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("记一笔")
            }
            OutlinedButton(
                onClick = onAddRecord,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加资产")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = onViewTransactions,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("流水")
            }
            TextButton(
                onClick = onEditAccount,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("编辑账户")
            }
        }
    }
}

@Composable
fun AccountInfoCard(
    account: Account,
    balanceInBaseCurrency: Double,
    baseCurrency: String
) {
    val isLiability = account.type == com.finunity.data.local.entity.AccountType.LIABILITY

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLiability)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
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
                    text = formatCurrency(balanceInBaseCurrency, baseCurrency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = "市值",
            value = formatCurrency(totalValue, baseCurrency),
            color = MaterialTheme.colorScheme.primary
        )
        if (incomeRecords.isNotEmpty()) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "盈亏",
                value = "${if (totalProfitLoss >= 0) "+" else ""}${formatCurrency(totalProfitLoss, baseCurrency)}",
                color = if (totalProfitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "收益率",
                value = formatSignedPercent(profitLossRatio),
                color = if (profitLossRatio >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
            )
        }
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    val profitColor = if (summary.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
    val isCash = summary.record.assetType == AssetType.CASH

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                Text(
                    text = listOfNotNull(
                        summary.record.assetType.displayName(),
                        summary.record.riskBucket.displayName().takeIf { summary.record.assetType != AssetType.CASH }
                    ).joinToString(" · "),
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
