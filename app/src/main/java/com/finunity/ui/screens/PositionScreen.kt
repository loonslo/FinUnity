package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Position
import com.finunity.data.model.AccountSummary
import com.finunity.ui.screens.formatCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionScreen(
    position: Position?,
    accountId: String,
    accounts: List<AccountSummary>,
    onSave: (Position) -> Unit,
    onDelete: (String) -> Unit,
    onSell: (String, Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var symbol by remember { mutableStateOf(position?.symbol ?: "") }
    var shares by remember { mutableStateOf(position?.shares?.toString() ?: "") }
    var totalCost by remember { mutableStateOf(position?.totalCost?.toString() ?: "") }
    var currency by remember { mutableStateOf(position?.currency ?: "USD") }
    var selectedAccountId by remember { mutableStateOf(position?.accountId ?: accountId) }
    var showSellDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sellShares by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(position == null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.account.id == selectedAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            position == null -> "添加持仓"
                            isEditing -> "编辑持仓"
                            else -> "持仓详情"
                        },
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (position != null) {
                        if (isEditing) {
                            // 保存编辑
                            TextButton(
                                onClick = {
                                    val updatedPosition = position.copy(
                                        accountId = selectedAccountId,
                                        symbol = symbol,
                                        shares = shares.toDoubleOrNull() ?: 0.0,
                                        totalCost = totalCost.toDoubleOrNull() ?: 0.0,
                                        currency = currency
                                    )
                                    onSave(updatedPosition)
                                },
                                enabled = selectedAccountId.isNotBlank() &&
                                         symbol.isNotBlank() &&
                                         (shares.toDoubleOrNull() ?: 0.0) > 0 &&
                                         (totalCost.toDoubleOrNull() ?: 0.0) > 0
                            ) {
                                Text("保存", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            // 编辑按钮
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑")
                            }
                            // 卖出按钮
                            IconButton(onClick = { showSellDialog = true }) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "卖出",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            // 删除按钮
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedAccountId.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "请先添加账户",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = it && (position == null || isEditing) }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.account?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("所属账户") },
                    placeholder = { Text("选择持仓所在账户") },
                    supportingText = {
                        Text(selectedAccount?.let { "${it.account.type.name} · ${it.account.currency}" } ?: "先创建账户，再录入股票/ETF")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    enabled = position == null || isEditing,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false }
                ) {
                    accounts.forEach { summary ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(summary.account.name)
                                    Text(
                                        text = "${summary.account.type.name} · ${summary.account.currency}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            onClick = {
                                selectedAccountId = summary.account.id
                                if (position == null) {
                                    currency = summary.account.currency
                                }
                                accountMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // 股票代码
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it.uppercase() },
                label = { Text("股票 / ETF 代码") },
                placeholder = { Text("如：AAPL, QQQ, 159919.SZ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = position == null || isEditing,
                supportingText = {
                    Text("支持手动录入具体股票或 ETF 代码，后续按代码刷新价格")
                },
                shape = RoundedCornerShape(12.dp)
            )

            // 股数
            OutlinedTextField(
                value = shares,
                onValueChange = { shares = it },
                label = { Text("股数") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = position == null || isEditing,
                shape = RoundedCornerShape(12.dp)
            )

            // 总成本
            OutlinedTextField(
                value = totalCost,
                onValueChange = { totalCost = it },
                label = { Text("总成本（买入总金额）") },
                placeholder = { Text("如：10000.0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = position == null || isEditing,
                supportingText = {
                    Text("买入时填总金额，如买入100股@$10则填1000.0")
                },
                shape = RoundedCornerShape(12.dp)
            )

            // 币种
            val validCurrencies = listOf("CNY", "USD", "HKD", "EUR", "GBP", "JPY")
            OutlinedTextField(
                value = currency,
                onValueChange = {
                    val filtered = it.uppercase().filter { c -> c.isLetter() }
                    currency = if (filtered.length == 3) filtered else currency
                },
                label = { Text("币种") },
                placeholder = { Text("如：USD, HKD, CNY") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = position == null || isEditing,
                supportingText = {
                    Text("股票计价货币，如美股为USD，港股为HKD（仅支持：${validCurrencies.joinToString(", ")}）")
                },
                shape = RoundedCornerShape(12.dp)
            )

            // 平均成本和市值提示
            if (position != null && !isEditing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("平均成本", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = formatCurrency(position.averageCost, position.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 添加模式下显示保存按钮
            if (position == null) {
                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val newPosition = Position(
                            id = java.util.UUID.randomUUID().toString(),
                            accountId = selectedAccountId,
                            symbol = symbol,
                            shares = shares.toDoubleOrNull() ?: 0.0,
                            totalCost = totalCost.toDoubleOrNull() ?: 0.0,
                            currency = currency,
                            createdAt = System.currentTimeMillis()
                        )
                        onSave(newPosition)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = symbol.isNotBlank() &&
                              (shares.toDoubleOrNull() ?: 0.0) > 0 &&
                              (totalCost.toDoubleOrNull() ?: 0.0) > 0 &&
                              currency.isNotBlank() &&
                              selectedAccountId.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 编辑模式下显示取消按钮
            if (position != null && isEditing) {
                Spacer(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = {
                        // 恢复原值
                        symbol = position.symbol
                        shares = position.shares.toString()
                        totalCost = position.totalCost.toString()
                        currency = position.currency
                        selectedAccountId = position.accountId
                        isEditing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // 卖出对话框
        if (showSellDialog && position != null) {
            val parsedSellShares = sellShares.toDoubleOrNull()
            val sellError = when {
                sellShares.isNotBlank() && parsedSellShares == null -> "请输入有效股数"
                parsedSellShares == null || parsedSellShares <= 0 -> "卖出股数必须大于 0"
                parsedSellShares > position.shares -> "卖出股数不能超过当前持有"
                else -> null
            }
            AlertDialog(
                onDismissRequest = { showSellDialog = false },
                title = { Text("卖出持仓") },
                text = {
                    Column {
                        Text("当前持有: ${String.format("%.4f", position.shares).trimEnd('0').trimEnd('.')} 股")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = sellShares,
                            onValueChange = { sellShares = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("卖出股数") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            isError = sellError != null,
                            supportingText = sellError?.let { { Text(it) } }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toSell = sellShares.toDoubleOrNull() ?: 0.0
                            onSell(position.id, toSell)
                            showSellDialog = false
                            onBack()
                        },
                        enabled = sellError == null
                    ) {
                        Text("确认卖出")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSellDialog = false }) {
                        Text("取消")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 删除确认对话框
        if (showDeleteDialog && position != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除持仓") },
                text = {
                    Text("确定要删除 ${position.symbol} 的全部持仓吗？此操作不可撤销。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(position.id)
                            showDeleteDialog = false
                            onBack()
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
