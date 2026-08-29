package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.viewmodel.ReconciliationResult
import com.finunity.viewmodel.ReconciliationStatus
import com.finunity.ui.components.FinTopBar

@Composable
fun ReconciliationScreen(
    accounts: List<Account>,
    results: Map<String, ReconciliationResult>,
    onCheck: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = { FinTopBar("数据对账", onBack) }, modifier = modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("检查流水推导余额与当前现金/负债记录是否一致。导入前可先备份，自动修复只会更新负债账户金额。", style = MaterialTheme.typography.bodySmall)
            }
            if (accounts.isEmpty()) item { Text("暂无账户", modifier = Modifier.padding(vertical = 40.dp)) }
            items(accounts) { account ->
                val result = results[account.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (result?.status) {
                                    null -> "未检查"
                                    ReconciliationStatus.CONSISTENT -> "一致"
                                    ReconciliationStatus.INCONSISTENT -> "有差异"
                                    ReconciliationStatus.INSUFFICIENT_DATA -> "数据不足"
                                },
                                color = when (result?.status) {
                                    ReconciliationStatus.CONSISTENT -> MaterialTheme.colorScheme.primary
                                    ReconciliationStatus.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        result?.let {
                            Text("当前 ${formatCurrency(it.currentBalance, account.currency)} · 推导 ${formatCurrency(it.computedBalance, account.currency)} · 差额 ${formatCurrency(it.difference, account.currency)}", style = MaterialTheme.typography.bodySmall)
                            it.issues.take(2).forEach { issue -> Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                        OutlinedButton(onClick = { onCheck(account.id) }, modifier = Modifier.fillMaxWidth()) { Text("检查") }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
