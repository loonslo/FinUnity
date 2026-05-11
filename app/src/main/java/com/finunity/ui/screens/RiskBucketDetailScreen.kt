package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.RiskBucketSummary
import com.finunity.data.model.displayName
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskBucketDetailScreen(
    riskBucketSummary: RiskBucketSummary,
    accounts: List<AccountSummary>,
    assetRecords: List<AssetRecordSummary>,
    baseCurrency: String,
    onBack: () -> Unit,
    onViewAccountTransactions: (String) -> Unit = {},
    onViewAssetHistory: (String) -> Unit = {},  // recordId -> PriceHistoryScreen
    onViewAssetTransactions: (String) -> Unit = {},  // recordId -> AssetTransactionHistoryScreen
    onEditAssetRecord: (String) -> Unit = {},  // recordId -> AssetRecordScreen
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 过滤出属于该风险维度的账户和记录
    // 账户属于某个维度的情况：
    // - CASH: 非负债账户有正现金余额
    // - AGGRESSIVE: 账户下有持仓（Position）或股票/ETF/基金资产记录
    // - CONSERVATIVE: 账户下有定期存款资产记录
    val accountsInBucket = accounts.filter { account ->
        when (riskBucketSummary.riskBucket) {
            RiskBucket.CASH -> account.account.type != com.finunity.data.local.entity.AccountType.LIABILITY && account.account.balance > 0
            RiskBucket.AGGRESSIVE -> {
                // 账户有持仓或股票/ETF/基金记录
                assetRecords.any {
                    it.record.accountId == account.account.id &&
                    it.record.riskBucket == RiskBucket.AGGRESSIVE
                }
            }
            RiskBucket.CONSERVATIVE -> {
                // 账户有定期存款记录
                assetRecords.any {
                    it.record.accountId == account.account.id &&
                    it.record.riskBucket == RiskBucket.CONSERVATIVE
                }
            }
        }
    }

    val recordsInBucket = assetRecords.filter { it.record.riskBucket == riskBucketSummary.riskBucket }
    val accountsWithRecords = recordsInBucket.map { it.accountName }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${riskBucketSummary.riskBucket.displayName()} 详情",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 风险维度汇总卡片
            item {
                RiskBucketSummaryCard(
                    summary = riskBucketSummary,
                    baseCurrency = baseCurrency
                )
            }

            // 账户列表
            if (accountsInBucket.isNotEmpty()) {
                item {
                    Text(
                        text = "涉及账户 (${accountsInBucket.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                items(accountsInBucket) { accountSummary ->
                    // 计算该账户在该风险维度下的资产合计
                    val bucketValueForAccount = recordsInBucket
                        .filter { it.record.accountId == accountSummary.account.id }
                        .sumOf { it.currentValue }

                    AccountInBucketItem(
                        account = accountSummary.account,
                        valueInBucket = bucketValueForAccount,
                        baseCurrency = baseCurrency,
                        onViewTransactions = { onViewAccountTransactions(accountSummary.account.id) }
                    )
                }
            }

            // 记录项列表
            if (recordsInBucket.isNotEmpty()) {
                item {
                    Text(
                        text = "资产记录 (${recordsInBucket.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                items(recordsInBucket) { record ->
                    AssetRecordInBucketItem(
                        summary = record,
                        baseCurrency = baseCurrency,
                        onViewHistory = { onViewAssetHistory(record.record.id) },
                        onViewTransactions = { onViewAssetTransactions(record.record.id) },
                        onEdit = { onEditAssetRecord(record.record.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun RiskBucketSummaryCard(
    summary: RiskBucketSummary,
    baseCurrency: String
) {
    val bucketColor = when (summary.riskBucket) {
        RiskBucket.CONSERVATIVE -> Color(0xFF4CAF50)
        RiskBucket.AGGRESSIVE -> Color(0xFFE53935)
        RiskBucket.CASH -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = bucketColor.copy(alpha = 0.1f)
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
                        text = "总价值",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatCurrency(summary.totalValue, baseCurrency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "占比",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${String.format("%.1f", summary.percentage * 100)}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = bucketColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "记录数量: ${summary.recordCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = summary.riskBucket.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = bucketColor
                )
            }
        }
    }
}

@Composable
fun AccountInBucketItem(
    account: Account,
    valueInBucket: Double,
    baseCurrency: String,
    onViewTransactions: () -> Unit
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${account.type.name} · ${account.currency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCurrency(valueInBucket, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onViewTransactions) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "查看流水",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun AssetRecordInBucketItem(
    summary: AssetRecordSummary,
    baseCurrency: String,
    onViewHistory: () -> Unit,
    onViewTransactions: () -> Unit,
    onEdit: () -> Unit
) {
    val profitColor = if (summary.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "${summary.accountName} · ${summary.record.assetType.displayName()}",
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

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onViewTransactions) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "交易流水",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
