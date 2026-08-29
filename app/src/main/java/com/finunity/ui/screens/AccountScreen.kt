package com.finunity.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountAssetRules
import com.finunity.ui.components.FinTextField
import com.finunity.ui.components.FinInlineField
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSettingRow
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    account: Account?,
    allowDelete: Boolean = false,
    deleteAssetCount: Int = 0,
    deleteTransactionCount: Int = 0,
    deletePriceHistoryCount: Int = 0,
    onSave: (Account) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var selectedType by remember { mutableStateOf(account?.type ?: AccountType.BANK) }
    var selectedCurrency by remember { mutableStateOf(account?.currency ?: "CNY") }
    var balance by remember { mutableStateOf(account?.balance?.toString() ?: "") }
    var initialPrincipal by remember { mutableStateOf(account?.initialPrincipal?.takeIf { it > 0 }?.toString() ?: "") }
    var annualInterestRate by remember { mutableStateOf((account?.annualInterestRate?.times(100))?.takeIf { it > 0 }?.toString() ?: "") }
    var dueDayOfMonth by remember { mutableStateOf(account?.dueDayOfMonth?.takeIf { it > 0 }?.toString() ?: "") }
    var minimumPayment by remember { mutableStateOf(account?.minimumPayment?.takeIf { it > 0 }?.toString() ?: "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showTypeSheet by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }

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
    val hasUnsavedChanges = name != (account?.name ?: "") ||
        selectedType != (account?.type ?: AccountType.BANK) ||
        selectedCurrency != (account?.currency ?: "CNY") ||
        balance != (account?.balance?.toString() ?: "") ||
        initialPrincipal != (account?.initialPrincipal?.takeIf { it > 0 }?.toString() ?: "") ||
        annualInterestRate != ((account?.annualInterestRate?.times(100))?.takeIf { it > 0 }?.toString() ?: "") ||
        dueDayOfMonth != (account?.dueDayOfMonth?.takeIf { it > 0 }?.toString() ?: "") ||
        minimumPayment != (account?.minimumPayment?.takeIf { it > 0 }?.toString() ?: "")

    BackHandler(enabled = hasUnsavedChanges) { showUnsavedDialog = true }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("未保存的修改") },
            text = { Text("返回将丢失当前输入，确定放弃吗？") },
            confirmButton = { TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                Text("放弃修改", color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { showUnsavedDialog = false }) { Text("继续编辑") } }
        )
    }

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
                        text = "将同时删除：资产记录 $deleteAssetCount 条 · 流水 $deleteTransactionCount 条 · 价格历史 $deletePriceHistoryCount 条",
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

    if (showTypeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTypeSheet = false },
            containerColor = FinColors.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = FinColors.TextTertiary) }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("选择账户类型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("类型会决定添加资产页可录入的内容。", color = FinColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                accountTypes.forEach { type ->
                    Surface(onClick = { selectedType = type; showTypeSheet = false }, color = Color.Transparent, modifier = Modifier.fillMaxWidth(), shape = FinShapes.sm) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(if (selectedType == type) FinColors.Accent else FinColors.SurfaceElevated, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(accountTypeInitial(type), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(type.displayName(), color = FinColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(accountTypeShortDescription(type), color = FinColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            if (selectedType == type) Text("✓", color = FinColors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showCurrencySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCurrencySheet = false },
            containerColor = FinColors.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = FinColors.TextTertiary) }
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("选择账户默认币种", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("资产可保留各自原始币种；此项用于账户默认值。", color = FinColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                listOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币").forEach { (code, label) ->
                    Surface(
                        onClick = { selectedCurrency = code; showCurrencySheet = false },
                        color = Color.Transparent,
                        shape = FinShapes.sm,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(code, color = FinColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                            Text(label, color = FinColors.TextSecondary, modifier = Modifier.weight(1f))
                            if (selectedCurrency == code) Text("✓", color = FinColors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.06f))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar(if (isEditing) "编辑账户" else "添加账户", onBack = {
            if (hasUnsavedChanges) showUnsavedDialog = true else onBack()
        }) },
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
                        createdAt = account?.createdAt ?: System.currentTimeMillis(),
                        initialPrincipal = if (selectedType == AccountType.LIABILITY) {
                            initialPrincipal.toDoubleOrNull() ?: balance.toDoubleOrNull() ?: 0.0
                        } else 0.0,
                        annualInterestRate = if (selectedType == AccountType.LIABILITY) {
                            (annualInterestRate.toDoubleOrNull() ?: 0.0) / 100.0
                        } else 0.0,
                        dueDayOfMonth = if (selectedType == AccountType.LIABILITY) {
                            dueDayOfMonth.toIntOrNull()?.coerceIn(0, 31) ?: 0
                        } else 0,
                        minimumPayment = if (selectedType == AccountType.LIABILITY) {
                            minimumPayment.toDoubleOrNull() ?: 0.0
                        } else 0.0
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountCompactHeader(name = name, selectedType = selectedType, selectedCurrency = selectedCurrency)
            Text("账户资料", color = FinColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    FinInlineField(name, { name = it }, "账户名称", placeholder = "如：招商银行、华泰证券")
                    FinSettingRow("账户类型", selectedType.displayName(), onClick = { showTypeSheet = true })
                    FinSettingRow("币种", selectedCurrency, onClick = { showCurrencySheet = true }, showDivider = false)
                }
            }
            Text("数据", color = FinColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    FinSettingRow("持仓资产", if (isEditing) "$deleteAssetCount 项" else "保存后显示")
                    FinSettingRow("交易流水", if (isEditing) "$deleteTransactionCount 笔" else "保存后显示", showDivider = false)
                }
            }

            if (selectedType == AccountType.LIABILITY) {
                FinCard {
                    Column(
                        modifier = Modifier.padding(4.dp),
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
                        FinTextField(
                            value = initialPrincipal,
                            onValueChange = { initialPrincipal = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "初始本金（可选）",
                            keyboardType = KeyboardType.Decimal
                        )
                        FinTextField(
                            value = annualInterestRate,
                            onValueChange = { annualInterestRate = it.filter { c -> c.isDigit() || c == '.' } },
                            label = "年利率（可选）",
                            keyboardType = KeyboardType.Decimal
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FinTextField(
                                value = dueDayOfMonth,
                                onValueChange = { dueDayOfMonth = it.filter(Char::isDigit).take(2) },
                                label = "还款日（1-31）",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            FinTextField(
                                value = minimumPayment,
                                onValueChange = { minimumPayment = it.filter { c -> c.isDigit() || c == '.' } },
                                label = "最低还款额",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "还款日和最低还款额只用于提醒与计划，不会自动扣款。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCompactHeader(name: String, selectedType: AccountType, selectedCurrency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(FinColors.Primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Text(accountTypeInitial(selectedType), color = FinColors.Secondary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name.ifBlank { "新的账户" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${selectedType.displayName()} · $selectedCurrency", color = FinColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountSettingRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = FinColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = FinColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Text("›", color = FinColors.TextSecondary, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp))
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
        colors = CardDefaults.cardColors(containerColor = FinColors.SurfaceElevated),
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
    onNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
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
            // 币种不在账户层选择：录入每笔资产时按其原始币种填写（同一账户可持多币种资产）。
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
        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
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
        // Keep the selected state in the same dark card system; the light outline
        // carries selection without introducing a competing pale-blue card.
        color = FinColors.PageBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color.White.copy(alpha = 0.72f) else FinColors.Outline
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
                        if (selected) FinColors.Accent else FinColors.Surface,
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
                text = accountTypeShortDescription(type),
                style = MaterialTheme.typography.labelSmall,
                color = FinColors.TextSecondary,
                maxLines = 2
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
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
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除账户", style = MaterialTheme.typography.bodySmall)
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

private fun accountTypeShortDescription(type: AccountType): String = when (type) {
    AccountType.BROKER -> "股票 · ETF · 基金"
    AccountType.BANK -> "基金 · 定期存款"
    AccountType.FUND -> "基金 · 现金"
    AccountType.CASH_MANAGEMENT -> "现金 · 日常收支"
    AccountType.BOND -> "债券 · 固收"
    AccountType.INSURANCE -> "保单 · 年金"
    AccountType.LIABILITY -> "待还金额"
    AccountType.OTHER -> "自定义资产"
}

