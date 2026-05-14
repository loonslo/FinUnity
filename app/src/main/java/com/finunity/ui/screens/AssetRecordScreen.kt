package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.model.displayName
import com.finunity.ui.components.FinPill
import com.finunity.ui.components.FinSoftButton
import com.finunity.ui.components.FinTextField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetRecordScreen(
    record: AssetRecord?,
    account: Account?,
    onSave: (AssetRecord) -> Unit,
    onDelete: (String) -> Unit,
    onSell: (String, Double) -> Unit,  // recordId, quantity
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowedAssetTypes = remember(account?.type, record?.assetType) {
        allowedAssetTypesFor(account?.type, record?.assetType)
    }
    var name by remember { mutableStateOf(record?.name ?: "") }
    var selectedAssetType by remember { mutableStateOf(record?.assetType ?: allowedAssetTypes.firstOrNull() ?: AssetType.CASH) }
    var selectedRiskBucket by remember { mutableStateOf(record?.riskBucket ?: RiskBucket.AGGRESSIVE) }
    var quantity by remember { mutableStateOf(record?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(record?.cost?.toString() ?: "") }
    var currentPrice by remember { mutableStateOf(record?.currentPrice?.toString() ?: "") }
    var selectedCurrency by remember { mutableStateOf(record?.currency ?: account?.currency ?: "CNY") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSellDialog by remember { mutableStateOf(false) }
    var sellQuantity by remember { mutableStateOf("") }

    val riskBuckets = RiskBucket.entries
    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    val isNewRecord = record == null
    val hasRiskBucket = selectedAssetType != AssetType.CASH

    LaunchedEffect(allowedAssetTypes) {
        if (selectedAssetType !in allowedAssetTypes && allowedAssetTypes.isNotEmpty()) {
            selectedAssetType = allowedAssetTypes.first()
            selectedRiskBucket = defaultRiskBucketFor(allowedAssetTypes.first())
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除持仓") },
            text = { Text("确定要删除这项持仓吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        record?.let { onDelete(it.id) }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 卖出确认对话框
    if (showSellDialog) {
        val currentQty = record?.quantity ?: 0.0
        val parsedSellQty = sellQuantity.toDoubleOrNull()
        val sellQty = parsedSellQty ?: currentQty
        val sellAmount = sellQty * (record?.currentPrice ?: 0.0)
        val sellError = when {
            sellQuantity.isNotBlank() && parsedSellQty == null -> "请输入有效数量"
            sellQty <= 0 -> "卖出数量必须大于 0"
            sellQty > currentQty -> "卖出数量不能超过当前持有"
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showSellDialog = false },
            title = { Text("确认卖出") },
            text = {
                Column {
                    Text("卖出 ${record?.name}")
                    Text("数量: ${String.format("%.4f", sellQty)} 份")
                    Text("预计金额: ${String.format("%.2f", sellAmount)} ${record?.currency ?: "CNY"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sellQuantity,
                        onValueChange = { sellQuantity = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("卖出数量（留空为全部）") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = sellError != null,
                        supportingText = sellError?.let { { Text(it) } }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSellDialog = false
                        record?.let {
                            val qty = sellQuantity.toDoubleOrNull() ?: it.quantity
                            onSell(it.id, qty)
                        }
                    },
                    enabled = sellError == null
                ) {
                    Text("确认卖出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSellDialog = false }) {
                    Text("取消")
                }
            }
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
                actions = {
                    if (!isNewRecord) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
            if (account != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("账户", style = MaterialTheme.typography.bodyMedium)
                        Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (allowedAssetTypes.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "该账户类型不支持新增持仓",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column {
                Text(
                    text = "资产类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allowedAssetTypes.forEach { type ->
                        FinPill(
                            selected = selectedAssetType == type,
                            onClick = {
                                if (selectedAssetType != type) {
                                    name = ""
                                    quantity = ""
                                    cost = ""
                                    currentPrice = ""
                                    sellQuantity = ""
                                    selectedRiskBucket = defaultRiskBucketFor(type)
                                }
                                selectedAssetType = type
                            },
                            text = type.displayName()
                        )
                    }
                }
            }

            if (hasRiskBucket) {
                Column {
                    Text(
                        text = "风险维度",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        riskBuckets.forEach { bucket ->
                            FinPill(
                                selected = selectedRiskBucket == bucket,
                                onClick = { selectedRiskBucket = bucket },
                                text = bucket.displayName()
                            )
                        }
                    }
                }
            }

            if (selectedAssetType != AssetType.CASH) {
                FinTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = getNameLabel(selectedAssetType),
                    placeholder = getNamePlaceholder(selectedAssetType)
                )
            }

            DynamicAssetFields(
                selectedAssetType = selectedAssetType,
                quantity = quantity,
                cost = cost,
                currentPrice = currentPrice,
                selectedCurrency = selectedCurrency,
                onQuantityChange = { quantity = it },
                onCostChange = { cost = it },
                onCurrentPriceChange = { currentPrice = it }
            )

            Column {
                Text(
                    text = if (selectedAssetType == AssetType.CASH) "单位" else "币种",
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
                            text = "$currency (${currencyLabels[currency]})"
                        )
                    }
                }
            }

            if (quantity.isNotEmpty() && cost.isNotEmpty() && currentPrice.isNotEmpty()) {
                val qty = quantity.toDoubleOrNull() ?: 0.0
                val c = cost.toDoubleOrNull() ?: 0.0
                val price = currentPrice.toDoubleOrNull() ?: 0.0

                // 定期存款特殊处理：currentValue = quantity * currentPrice
                // 但预览中单独显示利息收益
                val currentValue = if (selectedAssetType == AssetType.TIME_DEPOSIT) {
                    // 定期存款：quantity=本金, cost=本金, currentPrice=1+利率(小数)
                    qty * price
                } else {
                    qty * price
                }
                val profitLoss = if (selectedAssetType == AssetType.TIME_DEPOSIT) {
                    // 定期存款：盈亏 = 到期本息 - 本金
                    currentValue - c
                } else {
                    currentValue - c
                }
                val profitLossRatio = if (c > 0) profitLoss / c else 0.0

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
                        Text("预览", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (selectedAssetType == AssetType.CASH) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("金额:")
                                Text("${String.format("%.2f", currentValue)} $selectedCurrency")
                            }
                        } else if (selectedAssetType == AssetType.TIME_DEPOSIT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("到期本息:")
                                Text("${String.format("%.2f", currentValue)} $selectedCurrency")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("利息收益:")
                                Text(
                                    "${if (profitLoss >= 0) "+" else ""}${String.format("%.2f", profitLoss)} $selectedCurrency (${String.format("%.1f", profitLossRatio * 100)}%)",
                                    color = if (profitLoss >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("当前市值:")
                                Text("${String.format("%.2f", currentValue)} $selectedCurrency")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("盈亏:")
                                Text(
                                    "${if (profitLoss >= 0) "+" else ""}${String.format("%.2f", profitLoss)} $selectedCurrency (${String.format("%.1f", profitLossRatio * 100)}%)",
                                    color = if (profitLoss >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 校验逻辑
            val qty = quantity.toDoubleOrNull() ?: 0.0
            val c = cost.toDoubleOrNull() ?: 0.0
            val price = currentPrice.toDoubleOrNull() ?: 0.0
            val isTradableType = selectedAssetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
            val isValidForTradable = !isTradableType || (c > 0 && price > 0)
            val isFormValid = (selectedAssetType == AssetType.CASH || name.isNotBlank()) &&
                    qty > 0 &&
                    isValidForTradable &&
                    allowedAssetTypes.isNotEmpty()
            val missingReasons = buildList {
                if (allowedAssetTypes.isEmpty()) add("当前账户类型不支持新增持仓")
                if (selectedAssetType != AssetType.CASH && name.isBlank()) add("${getNameLabel(selectedAssetType)}")
                if (qty <= 0) add(if (selectedAssetType == AssetType.CASH) "金额" else "数量/份额")
                if (isTradableType && c <= 0) add("买入总成本")
                if (isTradableType && price <= 0) add("当前价格/净值")
            }

            if (!isFormValid && missingReasons.isNotEmpty()) {
                Text(
                    text = "请填写必填项：${missingReasons.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            FinSoftButton(
                text = "保存",
                onClick = {
                    val newRecord = AssetRecord(
                        id = record?.id ?: java.util.UUID.randomUUID().toString(),
                        accountId = account?.id ?: record?.accountId ?: "",
                        assetType = selectedAssetType,
                        riskBucket = if (selectedAssetType == AssetType.CASH) RiskBucket.CASH else selectedRiskBucket,
                        name = if (selectedAssetType == AssetType.CASH) "现金" else name.trim(),
                        quantity = qty,
                        cost = c,
                        currentPrice = price,
                        currency = selectedCurrency,
                        createdAt = record?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newRecord)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                enabled = isFormValid
            )

            if (!isNewRecord) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
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
                    ){}
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DynamicAssetFields(
    selectedAssetType: AssetType,
    quantity: String,
    cost: String,
    currentPrice: String,
    selectedCurrency: String,
    onQuantityChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onCurrentPriceChange: (String) -> Unit
) {
    when (selectedAssetType) {
        AssetType.CASH -> {
            FinTextField(
                value = quantity,
                onValueChange = { onQuantityChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = "金额",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            LaunchedEffect(quantity) {
                onCostChange(quantity)
                onCurrentPriceChange("1.0")
            }
        }
        AssetType.TIME_DEPOSIT -> {
            // 定期存款：quantity=本金, cost=本金, currentPrice=1+利率(小数)
            // 编辑时：从 currentPrice 反算年利率
            val currentPriceDouble = currentPrice.toDoubleOrNull() ?: 1.0
            var principalInput by remember(selectedAssetType) { mutableStateOf(quantity) }
            var rateInput by remember(selectedAssetType) { mutableStateOf(
                if (currentPriceDouble > 1.0) ((currentPriceDouble - 1) * 100).toString() else ""
            ) }

            FinTextField(
                value = principalInput,
                onValueChange = { principalInput = it.filter { c -> c.isDigit() || c == '.' } },
                label = "本金",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            FinTextField(
                value = rateInput,
                onValueChange = { rateInput = it.filter { c -> c.isDigit() || c == '.' } },
                label = "年利率 (%)",
                placeholder = "如：3.5",
                keyboardType = KeyboardType.Decimal
            )

            val principal = principalInput.toDoubleOrNull() ?: 0.0
            val rate = rateInput.toDoubleOrNull() ?: 0.0
            val maturityValue = principal * (1 + rate / 100)

            if (principalInput.isNotEmpty() && rateInput.isNotEmpty()) {
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
                            Text("到期本息:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${String.format("%.2f", maturityValue)} $selectedCurrency",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "利息: ${String.format("%.2f", maturityValue - principal)} $selectedCurrency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // 同步到表单字段：quantity=本金, cost=本金, currentPrice=1+利率(小数)
            // 这样 currentValue = quantity * currentPrice = 本金 * (1+利率) = 到期本息
            LaunchedEffect(principalInput, rateInput) {
                val parsedRate = rateInput.toDoubleOrNull() ?: 0.0
                onQuantityChange(principalInput)  // 本金
                onCostChange(principalInput)  // 成本=本金
                if (principalInput.isNotEmpty() && rateInput.isNotEmpty()) {
                    onCurrentPriceChange((1 + parsedRate / 100).toString())  // 利率系数
                }
            }
        }
        else -> {
            FinTextField(
                value = quantity,
                onValueChange = { onQuantityChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = "数量/份额",
                placeholder = "0",
                keyboardType = KeyboardType.Decimal
            )
            FinTextField(
                value = cost,
                onValueChange = { onCostChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = "买入总成本",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            FinTextField(
                value = currentPrice,
                onValueChange = { onCurrentPriceChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = "当前价格/净值",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
        }
    }
}

private fun getNameLabel(assetType: AssetType): String = when (assetType) {
    AssetType.STOCK -> "股票代码"
    AssetType.ETF -> "ETF代码"
    AssetType.FUND -> "基金名称/代码"
    AssetType.CASH -> "现金"
    AssetType.TIME_DEPOSIT -> "定期存款名称"
}

private fun getNamePlaceholder(assetType: AssetType): String = when (assetType) {
    AssetType.STOCK -> "如：AAPL"
    AssetType.ETF -> "如：510300"
    AssetType.FUND -> "如：余额宝、上证指数基金"
    AssetType.CASH -> "现金"
    AssetType.TIME_DEPOSIT -> "如：一年定期"
}

private fun defaultRiskBucketFor(assetType: AssetType): RiskBucket = when (assetType) {
    AssetType.STOCK, AssetType.ETF, AssetType.FUND -> RiskBucket.AGGRESSIVE
    AssetType.CASH -> RiskBucket.CASH
    AssetType.TIME_DEPOSIT -> RiskBucket.CONSERVATIVE
}

private fun allowedAssetTypesFor(accountType: AccountType?, currentType: AssetType?): List<AssetType> {
    val baseAllowed = when (accountType) {
        AccountType.BROKER -> listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND, AssetType.CASH)
        AccountType.BANK -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT, AssetType.CASH)
        AccountType.FUND -> listOf(AssetType.FUND, AssetType.CASH)
        AccountType.CASH_MANAGEMENT -> listOf(AssetType.FUND, AssetType.CASH)
        AccountType.BOND -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT, AssetType.CASH)
        AccountType.INSURANCE -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT)
        AccountType.LIABILITY -> emptyList()
        AccountType.OTHER, null -> AssetType.entries.toList()
    }
    val allowed = if (currentType == null) {
        baseAllowed.filterNot { it == AssetType.CASH }
    } else {
        baseAllowed
    }
    return if (currentType != null && currentType !in allowed) {
        listOf(currentType) + allowed
    } else {
        allowed
    }
}
