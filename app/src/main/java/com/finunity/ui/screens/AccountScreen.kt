package com.finunity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.finunity.data.model.AccountAssetRules
import com.finunity.ui.components.FinTextField
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    account: Account?,
    allowDelete: Boolean = false,
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
    val accountTypes = listOf(
        AccountType.BANK,
        AccountType.BROKER,
        AccountType.CASH_MANAGEMENT,
        AccountType.FUND,
        AccountType.BOND,
        AccountType.INSURANCE,
        AccountType.LIABILITY,
        AccountType.OTHER
    )
    val isEditing = account != null

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
        containerColor = FinColors.PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "编辑账户" else "添加账户",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinColors.TextPrimary
                    )
                },
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
                    containerColor = FinColors.PageBg
                )
            )
        },
        bottomBar = {
            AccountEditBottomBar(
                enabled = name.isNotBlank(),
                isEditing = isEditing,
                allowDelete = allowDelete,
                onSave = {
                    val newAccount = Account(
                        id = account?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        type = selectedType,
                        currency = selectedCurrency,
                        balance = if (selectedType == AccountType.LIABILITY) balance.toDoubleOrNull() ?: 0.0 else 0.0,
                        createdAt = account?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(newAccount)
                },
                onDelete = { showDeleteConfirmDialog = true }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FinColors.PageBg)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountHeroCard(
                isEditing = isEditing,
                selectedType = selectedType,
                selectedCurrency = selectedCurrency
            )

            AccountFormCard(
                name = name,
                onNameChange = { name = it },
                selectedCurrency = selectedCurrency,
                currencies = currencies,
                onCurrencyChange = { selectedCurrency = it }
            )

            AccountTypeSection(
                accountTypes = accountTypes,
                selectedType = selectedType,
                onTypeChange = { selectedType = it }
            )

            AccountAssetMatchCard(selectedType = selectedType)

            if (selectedType == AccountType.LIABILITY) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "负债金额",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = FinColors.TextPrimary
                        )
                        Text(
                            text = "负债账户只记录待还金额，不参与普通持仓录入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                        FinTextField(
                            value = balance,
                            onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "当前待还金额",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeroCard(
    isEditing: Boolean,
    selectedType: AccountType,
    selectedCurrency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = FinColors.TextPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isEditing) "账户资料" else "新的资产入口",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
            Text(
                text = if (isEditing) "保持账户信息清晰可追溯" else "先建立账户，再把钱按用途放进去",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${selectedType.displayName()} · $selectedCurrency",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun AccountFormCard(
    name: String,
    onNameChange: (String) -> Unit,
    selectedCurrency: String,
    currencies: List<String>,
    onCurrencyChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionTitle(
                title = "账户名称",
                subtitle = "用你自己能一眼识别的名字，比如“招行工资卡”或“富途港股”。"
            )
            FinTextField(
                value = name,
                onValueChange = onNameChange,
                label = "名称",
                placeholder = "如：招商银行、富途证券"
            )
            SectionTitle(
                title = "默认币种",
                subtitle = "后续资产仍可使用自己的原始币种，这里用于账户归类。"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinColors.PageBg, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                currencies.forEach { currency ->
                    CurrencySegment(
                        currency = currency,
                        selected = selectedCurrency == currency,
                        onClick = { onCurrencyChange(currency) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountTypeSection(
    accountTypes: List<AccountType>,
    selectedType: AccountType,
    onTypeChange: (AccountType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle(
                title = "账户类型",
                subtitle = "选择后，添加资产页只会显示对应的资产类型。"
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                accountTypes.forEach { type ->
                    AccountTypeCard(
                        type = type,
                        selected = selectedType == type,
                        onClick = { onTypeChange(type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountTypeCard(
    type: AccountType,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
        shape = FinShapes.lg,
        color = if (selected) FinColors.Accent.copy(alpha = 0.10f) else FinColors.PageBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) FinColors.Accent.copy(alpha = 0.45f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) FinColors.Accent else Color.White,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = accountTypeInitial(type),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else FinColors.Accent
                )
            }
            Text(
                text = type.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FinColors.TextPrimary
            )
            Text(
                text = "可添加：${AccountAssetRules.allowedAssetText(type)}",
                style = MaterialTheme.typography.labelSmall,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun AccountAssetMatchCard(
    selectedType: AccountType
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = FinColors.Accent.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${selectedType.displayName()} 可以记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = FinColors.TextPrimary
            )
            Text(
                text = AccountAssetRules.allowedAssetText(selectedType),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = FinColors.Accent
            )
            Text(
                text = AccountAssetRules.ruleNote(selectedType),
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CurrencySegment(
    currency: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color.White else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currency,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) FinColors.TextPrimary else FinColors.TextSecondary
            )
            Text(
                text = currencyLabel(currency),
                style = MaterialTheme.typography.labelSmall,
                color = FinColors.TextSecondary
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = FinColors.TextPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = FinColors.TextSecondary
        )
    }
}

@Composable
private fun AccountEditBottomBar(
    enabled: Boolean,
    isEditing: Boolean,
    allowDelete: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = FinColors.PageBg,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = FinShapes.md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                ),
                enabled = enabled
            ) {
                Text(
                    text = if (isEditing) "保存修改" else "创建账户",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.Number
                )
            }
            if (isEditing && allowDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = FinShapes.md
                ) {
                    Text("删除账户")
                }
            }
        }
    }
}

private fun accountTypeInitial(type: AccountType): String = when (type) {
    AccountType.BROKER -> "券"
    AccountType.BANK -> "银"
    AccountType.FUND -> "基"
    AccountType.CASH_MANAGEMENT -> "现"
    AccountType.BOND -> "债"
    AccountType.INSURANCE -> "保"
    AccountType.LIABILITY -> "负"
    AccountType.OTHER -> "其"
}

private fun accountTypeHint(type: AccountType): String = when (type) {
    AccountType.BROKER -> "股票、ETF、基金"
    AccountType.BANK -> "银行卡、存款"
    AccountType.FUND -> "基金和理财"
    AccountType.CASH_MANAGEMENT -> "余额宝、零钱"
    AccountType.BOND -> "债券和固收"
    AccountType.INSURANCE -> "保单现金价值"
    AccountType.LIABILITY -> "房贷、信用卡"
    AccountType.OTHER -> "其他资产入口"
}

private fun currencyLabel(currency: String): String = when (currency) {
    "CNY" -> "人民币"
    "USD" -> "美元"
    "HKD" -> "港币"
    else -> currency
}
