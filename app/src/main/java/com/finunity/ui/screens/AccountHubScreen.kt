package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.model.AccountSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHubScreen(
    database: AppDatabase,
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onBack: () -> Unit,
    onViewAccount: (String) -> Unit,
    onEditAccount: (Account) -> Unit,
    onAddAccount: () -> Unit,
    onViewTransactions: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf<Account?>(null) }

    val accountBalanceTotal = accounts.sumOf { it.balanceInBaseCurrency }
    val brokerCount = accounts.count { it.account.type.name == "BROKER" }
    val bankCount = accounts.count { it.account.type.name == "BANK" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户管理", fontWeight = FontWeight.Medium) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增账户", tint = Color.White)
            }
        },
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "账户余额合计",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatCurrency(accountBalanceTotal, baseCurrency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HubChip(label = "券商:$brokerCount", icon = Icons.Default.DateRange)
                            HubChip(label = "银行:$bankCount", icon = Icons.Default.List)
                            HubChip(label = "合计:${accounts.size}", icon = Icons.Default.Settings)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "所有账户 (${accounts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            if (accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("暂无账户", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("点击 + 按钮添加第一个账户", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            items(accounts, key = { it.account.id }) { summary ->
                HubAccountCard(
                    summary = summary,
                    baseCurrency = baseCurrency,
                    onView = { onViewAccount(summary.account.id) },
                    onEdit = { onEditAccount(summary.account) },
                    onViewTransactions = { onViewTransactions(summary.account.id) },
                    onDelete = { showDeleteConfirm = summary.account }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    showDeleteConfirm?.let { account ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除账户") },
            text = {
                Text("确定要删除账户 \"${account.name}\" 吗？\n\n此操作会同时删除：\n- 该账户下的所有持仓和资产记录\n- 该账户的所有交易流水\n\n此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { database.accountDao().deleteById(account.id) }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun HubAccountCard(
    summary: AccountSummary,
    baseCurrency: String,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onViewTransactions: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${summary.account.type.name} · ${summary.account.currency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.balanceInBaseCurrency < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(summary.account.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HubActionButton(icon = Icons.Default.Info, label = "查看", onClick = onView)
                HubActionButton(icon = Icons.Default.Edit, label = "编辑", onClick = onEdit)
                HubActionButton(icon = Icons.Default.List, label = "流水", onClick = onViewTransactions)
                HubActionButton(icon = Icons.Default.Delete, label = "删除", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HubActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(onClick = onClick, modifier = Modifier.padding(4.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun HubChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
