package com.finunity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.displayName
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinPill
import com.finunity.ui.components.FinSoftButton
import com.finunity.ui.components.FinTextField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    account: Account?,
    onSave: (Account) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var selectedType by remember { mutableStateOf(account?.type ?: AccountType.BANK) }
    var selectedCurrency by remember { mutableStateOf(account?.currency ?: "CNY") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currencies = listOf("CNY", "USD", "HKD")
    val commonAccountTypes = listOf(AccountType.BANK, AccountType.BROKER, AccountType.CASH_MANAGEMENT)
    val otherAccountTypes = listOf(AccountType.INSURANCE, AccountType.LIABILITY, AccountType.OTHER)

    // 删除确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("删除账户") },
            text = {
                Column {
                    Text("确定要删除账户 \"${account?.name}\" 吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "注意：删除账户将同时删除该账户下的所有持仓、价格历史和交易流水",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        account?.let { onDelete(it.id) }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FinTextField(
                value = name,
                onValueChange = { name = it },
                label = "账户名称",
                placeholder = "如：招商银行、富途证券"
            )

            // 账户类型
            Column {
                Text(
                    text = "账户类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "常用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commonAccountTypes.forEach { type ->
                        FinPill(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            text = type.displayName()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "其他",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    otherAccountTypes.forEach { type ->
                        FinPill(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            text = type.displayName()
                        )
                    }
                }
            }

            // 货币
            Column {
                Text(
                    text = "币种",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { currency ->
                        FinPill(
                            selected = selectedCurrency == currency,
                            onClick = { selectedCurrency = currency },
                            text = currency
                        )
                    }
                }
            }

            if (selectedType == AccountType.LIABILITY) {
                FinTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "负债金额",
                    keyboardType = KeyboardType.Decimal
                )
            } else {
                FinCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)) {
                    Text(
                        text = "账户只用于归类。现金、基金、定期等金额请在账户下添加持仓，避免重复统计。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FinSoftButton(
                text = "保存",
                onClick = {
                    val newAccount = Account(
                        id = account?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        type = selectedType,
                        currency = selectedCurrency,
                        balance = if (selectedType == AccountType.LIABILITY) balance.toDoubleOrNull() ?: 0.0 else 0.0,
                        createdAt = account?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(newAccount)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                enabled = name.isNotBlank()
            )

            // 删除按钮（仅编辑时显示）
            if (account != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("删除账户")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
