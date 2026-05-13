package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountSummary
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSectionLabel
import com.finunity.ui.components.FinSoftButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHubScreen(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onViewAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onOpenImportData: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (accounts.isEmpty()) {
                item {
                    FinCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无账户",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "添加银行卡、券商账户或现金账户",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FinSoftButton(
                                    text = "添加账户",
                                    onClick = onAddAccount,
                                    modifier = Modifier.weight(1f)
                                )
                                FinSoftButton(
                                    text = "导入数据",
                                    onClick = onOpenImportData,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                // 账户汇总卡
                item {
                    AccountSummaryCard(
                        accounts = accounts,
                        baseCurrency = baseCurrency,
                        onAddAccount = onAddAccount,
                        onOpenImportData = onOpenImportData
                    )
                }

                // 账户列表标题
                item {
                    FinSectionLabel("账户 (${accounts.size})")
                }

                // 账户列表
                items(accounts, key = { it.account.id }) { summary ->
                    HubAccountCard(
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
private fun AccountSummaryCard(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onAddAccount: () -> Unit,
    onOpenImportData: () -> Unit
) {
    val brokerCount = accounts.count { it.account.type == AccountType.BROKER }
    val bankCount = accounts.count { it.account.type == AccountType.BANK }
    val cashCount = accounts.count { it.account.type == AccountType.CASH_MANAGEMENT }
    val totalBalance = accounts.sumOf { it.balanceInBaseCurrency }

    FinCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 账户类型统计 - 无边框纯文字行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (brokerCount > 0) {
                    Text(
                        text = "证券 $brokerCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bankCount > 0) {
                    Text(
                        text = "银行 $bankCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cashCount > 0) {
                    Text(
                        text = "现金 $cashCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 总资产 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "总资产",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatCurrency(totalBalance, baseCurrency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenImportData) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "导入数据",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Button(
                        onClick = onAddAccount,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


@Composable
private fun HubAccountCard(
    summary: AccountSummary,
    baseCurrency: String,
    onView: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                Text(summary.account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${summary.account.type.displayName()} · ${summary.account.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.balanceInBaseCurrency < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "查看账户",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}