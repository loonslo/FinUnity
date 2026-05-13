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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.HoldingSummary
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
    holdings: List<HoldingSummary>,
    baseCurrency: String,
    onBack: () -> Unit,
    onViewAccountTransactions: (String) -> Unit = {},
    onEditAssetRecord: (String) -> Unit = {},  // recordId -> AssetRecordScreen
    modifier: Modifier = Modifier
) {
    // 过滤出属于该风险维度的账户和记录
    // 账户属于某个维度的情况：
    // - CASH: 账户下有现金类持仓
    // - AGGRESSIVE: 账户下有持仓（Position）或股票/ETF/基金资产记录
    // - CONSERVATIVE: 账户下有定期存款资产记录
    val accountsInBucket = accounts.filter { account ->
        when (riskBucketSummary.riskBucket) {
            RiskBucket.CASH -> assetRecords.any {
                    it.record.accountId == account.account.id &&
                    it.record.riskBucket == RiskBucket.CASH
                }
            RiskBucket.AGGRESSIVE -> {
                // 账户有持仓（Position）或股票/ETF/基金资产记录
                holdings.any { it.position.accountId == account.account.id } ||
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
    val holdingsInBucket = if (riskBucketSummary.riskBucket == RiskBucket.AGGRESSIVE) {
        holdings
    } else {
        emptyList()
    }

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
                    val recordValueForAccount = recordsInBucket
                            .filter { it.record.accountId == accountSummary.account.id }
                            .sumOf { it.currentValue }
                    val bucketValueForAccount = when (riskBucketSummary.riskBucket) {
                        RiskBucket.CASH -> recordValueForAccount
                        RiskBucket.AGGRESSIVE -> {
                            recordValueForAccount + holdingsInBucket
                                .filter { it.position.accountId == accountSummary.account.id }
                                .sumOf { it.currentValue }
                        }
                        RiskBucket.CONSERVATIVE -> recordValueForAccount
                    }

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
                        text = "持仓 (${recordsInBucket.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                items(recordsInBucket) { record ->
                    AssetRecordInBucketItem(
                        summary = record,
                        baseCurrency = baseCurrency,
                        onClick = { onEditAssetRecord(record.record.id) }
                    )
                }
            }

            if (holdingsInBucket.isNotEmpty()) {
                item {
                    Text(
                        text = "旧持仓 (${holdingsInBucket.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                items(holdingsInBucket) { holding ->
                    HoldingInBucketItem(
                        holding = holding,
                        baseCurrency = baseCurrency
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
        RiskBucket.AGGRESSIVE -> MaterialTheme.colorScheme.primary
        RiskBucket.CASH -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewTransactions),
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
                    text = "${account.type.displayName()} · ${account.currency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = formatCurrency(valueInBucket, baseCurrency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun HoldingInBucketItem(
    holding: HoldingSummary,
    baseCurrency: String
) {
    val profitColor = if (holding.profitLoss >= 0) Color(0xFF00A86B) else Color(0xFFE53935)

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
                    text = holding.position.symbol.take(2),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.position.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${holding.accountName} · 旧持仓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(holding.currentValue, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatSignedPercent(holding.profitLossRatio),
                    style = MaterialTheme.typography.bodySmall,
                    color = profitColor
                )
            }
        }
    }
}

@Composable
fun AssetRecordInBucketItem(
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
