package com.finunity.ui.screens

import androidx.activity.compose.BackHandler
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
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountAssetRules
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
    var selectedRiskBucket by remember { mutableStateOf(record?.riskBucket ?: defaultRiskBucketFor(selectedAssetType, account?.type)) }
    var quantity by remember { mutableStateOf(record?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(record?.cost?.toString() ?: "") }
    var currentPrice by remember { mutableStateOf(record?.currentPrice?.toString() ?: "") }
    var selectedCurrency by remember { mutableStateOf(record?.currency ?: account?.currency ?: "CNY") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSellDialog by remember { mutableStateOf(false) }
    var sellQuantity by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val riskBuckets = RiskBucket.entries
    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    val isNewRecord = record == null
    val hasRiskBucket = selectedAssetType != AssetType.CASH

    LaunchedEffect(allowedAssetTypes) {
        if (selectedAssetType !in allowedAssetTypes && allowedAssetTypes.isNotEmpty()) {
            selectedAssetType = allowedAssetTypes.first()
            selectedRiskBucket = defaultRiskBucketFor(allowedAssetTypes.first(), account?.type)
        }
    }

    // 检测是否有未保存的修改
    val hasUnsavedChanges = remember(name, quantity, cost, currentPrice, selectedCurrency, selectedAssetType, selectedRiskBucket) {
        if (record == null) {
            // 新建时只要有输入就有改动
            name.isNotBlank() || quantity.isNotBlank() || cost.isNotBlank() || currentPrice.isNotBlank()
        } else {
            // 编辑时对比原始值
            name != record.name ||
            quantity != record.quantity.toString() ||
            cost != record.cost.toString() ||
            currentPrice != record.currentPrice.toString() ||
            selectedCurrency != record.currency ||
            selectedAssetType != record.assetType ||
            selectedRiskBucket != record.riskBucket
        }
    }

    // 返回时未保存提示
    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("未保存的修改") },
            text = { Text("你有未保存的修改，要放弃吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除资产") },
            text = {
                Column {
                    Text("确定要删除「${record?.name ?: ""}」吗？")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "关联的交易流水和价格历史将一并删除，此操作不可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
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
        var sellPrice by remember { mutableStateOf(String.format("%.2f", record?.currentPrice ?: 0.0)) }
        var sellFee by remember { mutableStateOf("") }
        var sellDate by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())) }
        var sellNote by remember { mutableStateOf("") }
        val parsedPrice = sellPrice.toDoubleOrNull()
        val sellAmount = sellQty * (parsedPrice ?: 0.0)
        val sellError = when {
            sellQuantity.isNotBlank() && parsedSellQty == null -> "请输入有效数量"
            sellQty <= 0 -> "卖出数量必须大于 0"
            sellQty > currentQty -> "卖出数量不能超过当前持有"
            parsedPrice == null || parsedPrice <= 0 -> "请输入有效成交价"
            else -> null
        }

        AlertDialog(
            onDismissRequest = { showSellDialog = false },
            title = { Text("确认卖出") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("卖出 ${record?.name}", fontWeight = FontWeight.Medium)
                    Text("持有 ${String.format("%.4f", currentQty)} 份，当前价 ${String.format("%.2f", record?.currentPrice ?: 0.0)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    OutlinedTextField(
                        value = sellQuantity,
                        onValueChange = { sellQuantity = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("卖出数量（留空为全部）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = sellPrice,
                        onValueChange = { sellPrice = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("成交价") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        suffix = { Text(record?.currency ?: "") }
                    )
                    OutlinedTextField(
                        value = sellFee,
                        onValueChange = { sellFee = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("费用（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = sellDate,
                        onValueChange = { sellDate = it },
                        label = { Text("日期") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = sellNote,
                        onValueChange = { sellNote = it },
                        label = { Text("备注（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (parsedPrice != null && parsedPrice > 0) {
                        Text(
                            text = "成交金额: ${String.format("%.2f", sellAmount)} ${record?.currency ?: ""}${if (sellFee.toDoubleOrNull() != null && sellFee.toDoubleOrNull()!! > 0) "（含费）" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (sellError != null) {
                        Text(sellError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSellDialog = false
                        record?.let { r ->
                            onSell(r.id, sellQty)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("账户", style = MaterialTheme.typography.bodyMedium)
                            Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = "${account.type.displayName()} 可添加：${AccountAssetRules.allowedAssetText(account.type)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        )
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
                                    selectedRiskBucket = defaultRiskBucketFor(type, account?.type)
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
                if (selectedAssetType == AssetType.STOCK || selectedAssetType == AssetType.ETF) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "为自动获取每日价格，请填写 Yahoo 代码：美股直接填代码（AAPL），" +
                            "沪市加 .SS（600519.SS），深市加 .SZ（000001.SZ），港股加 .HK（0700.HK）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                if (selectedAssetType == AssetType.FUND) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "基金按手动净值维护，暂不自动同步中国公募基金净值。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
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
        AssetType.REAL_ESTATE, AssetType.VEHICLE, AssetType.INSURANCE_POLICY -> {
            // 估值录入：只显示一个估值字段，currentValue = 1 * 估值 = 估值
            var valuationInput by remember(selectedAssetType) { mutableStateOf(
                if (cost.toDoubleOrNull() == currentPrice.toDoubleOrNull() && cost.toDoubleOrNull() != null && cost.toDoubleOrNull()!! > 0) cost else ""
            ) }
            FinTextField(
                value = valuationInput,
                onValueChange = { valuationInput = it.filter { c -> c.isDigit() || c == '.' } },
                label = "当前估值",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal
            )
            LaunchedEffect(valuationInput) {
                val valuation = valuationInput.toDoubleOrNull() ?: 0.0
                onQuantityChange("1")
                onCostChange(valuation.toString())
                onCurrentPriceChange(valuation.toString())
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
    AssetType.REAL_ESTATE -> "房产名称"
    AssetType.VEHICLE -> "车辆名称"
    AssetType.INSURANCE_POLICY -> "保单名称"
}

private fun getNamePlaceholder(assetType: AssetType): String = when (assetType) {
    AssetType.STOCK -> "如：AAPL、600519.SS、0700.HK"
    AssetType.ETF -> "如：510300.SS、SPY"
    AssetType.FUND -> "如：余额宝、上证指数基金"
    AssetType.CASH -> "现金"
    AssetType.TIME_DEPOSIT -> "如：一年定期"
    AssetType.REAL_ESTATE -> "如：自住房"
    AssetType.VEHICLE -> "如：家用车"
    AssetType.INSURANCE_POLICY -> "如：重疾险"
}

private fun defaultRiskBucketFor(assetType: AssetType, accountType: AccountType? = null): RiskBucket = when {
    assetType == AssetType.CASH -> RiskBucket.CASH
    assetType == AssetType.INSURANCE_POLICY -> RiskBucket.INSURANCE
    // 保险账户下的资产默认归入"保命的钱"象限
    accountType == AccountType.INSURANCE -> RiskBucket.INSURANCE
    assetType == AssetType.TIME_DEPOSIT -> RiskBucket.CONSERVATIVE
    assetType == AssetType.REAL_ESTATE || assetType == AssetType.VEHICLE -> RiskBucket.CONSERVATIVE
    else -> RiskBucket.AGGRESSIVE  // STOCK / ETF / FUND
}

private fun allowedAssetTypesFor(accountType: AccountType?, currentType: AssetType?): List<AssetType> {
    val baseAllowed = AccountAssetRules.allowedAssetTypes(accountType)
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
