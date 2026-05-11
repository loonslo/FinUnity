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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetRecord
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
    onAddRecord: () -> Unit,
    onEditRecord: (AssetRecord) -> Unit,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val recordsForAccount = assetRecords.filter { it.record.accountId == account.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        account.name,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onViewTransactions) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "交易流水",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEditAccount) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑账户",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRecord,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录", tint = Color.White)
            }
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

            // 统计概览
            if (recordsForAccount.isNotEmpty()) {
                item {
                    AccountStatsRow(
                        records = recordsForAccount,
                        baseCurrency = baseCurrency
                    )
                }
            }

            // 资产记录列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "资产记录 (${recordsForAccount.size})",
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
                                    text = "暂无资产记录",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onAddRecord) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("添加第一条记录")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "账户类型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = when (account.type.name) {
                            "BROKER" -> "券商账户"
                            "BANK" -> "银行账户"
                            "FUND" -> "基金账户"
                            "CASH_MANAGEMENT" -> "现金管理"
                            "BOND" -> "债券"
                            "INSURANCE" -> "保险"
                            "LIABILITY" -> "负债"
                            else -> "其他"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "币种",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = account.currency,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
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
                    text = if (isLiability) "负债余额" else "现金余额",
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
    val totalCost = records.sumOf { it.costInBaseCurrency }
    val totalProfitLoss = totalValue - totalCost
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
        StatCard(
            modifier = Modifier.weight(1f),
            label = "盈亏",
            value = "${if (totalProfitLoss >= 0) "+" else ""}${formatCurrency(totalProfitLoss, baseCurrency)}",
            color = if (totalProfitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = "收益率",
            value = "${if (profitLossRatio >= 0) "+" else ""}${String.format("%.1f", profitLossRatio * 100)}%",
            color = if (profitLossRatio >= 0) Color(0xFF00A86B) else Color(0xFFE53935)
        )
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
                    text = "${summary.record.assetType.displayName()} · ${summary.record.riskBucket.displayName()}",
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
                    text = "${if (summary.profitLoss >= 0) "+" else ""}${String.format("%.1f", summary.profitLossRatio * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}
