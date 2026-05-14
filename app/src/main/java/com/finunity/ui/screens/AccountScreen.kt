package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.displayName
import com.finunity.ui.components.FinPill
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
        containerColor = AccountFormPage,
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
                    containerColor = AccountFormPage
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AccountFormPage)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountFormHeader(accountName = name, isEditing = account != null)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    FinTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "账户名称",
                        placeholder = "如：招商银行、富途证券"
                    )

                    Column {
                        Text(
                            text = "账户类型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccountFormText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "常用",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccountFormMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "其他",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccountFormMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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

                    Column {
                        Text(
                            text = "币种",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccountFormText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AccountFormAccent.copy(alpha = 0.07f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "账户用于归类资产。工资、转账和现金变化请在账户详情里用「记一笔」记录。",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AccountFormText.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
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
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccountFormAccent),
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = "保存",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 删除按钮（仅编辑时显示）
            if (account != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(22.dp)
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
                            shape = RoundedCornerShape(16.dp)
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

@Composable
private fun AccountFormHeader(
    accountName: String,
    isEditing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(AccountFormAccent.copy(alpha = 0.09f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = accountName.trim().take(1).ifBlank { "+" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccountFormAccent
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isEditing) "编辑账户" else "添加账户",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccountFormText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "设置账户资料，资金变化用记一笔记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccountFormMuted
                )
            }
        }
    }
}

private val AccountFormPage = Color(0xFFF7F8FA)
private val AccountFormText = Color(0xFF1F2933)
private val AccountFormMuted = Color(0xFFA0A4AA)
private val AccountFormAccent = Color(0xFF1E8E5A)
