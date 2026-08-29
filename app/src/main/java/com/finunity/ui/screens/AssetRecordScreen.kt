package com.finunity.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.defaultRiskBucket
import com.finunity.data.local.entity.displayName
import com.finunity.data.model.AccountAssetRules
import com.finunity.data.model.displayName
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.theme.FinColors
import java.util.Locale
import com.finunity.ui.components.FinPill
import com.finunity.ui.components.FinBucketTag
import com.finunity.ui.components.FinSoftButton
import com.finunity.ui.components.FinTextField
import com.finunity.ui.components.FinInlineField
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetRecordScreen(
    record: AssetRecord?,
    account: Account?,
    onSave: (AssetRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowedAssetTypes = remember(account?.type, record?.assetType) {
        allowedAssetTypesFor(account?.type, record?.assetType)
    }
    var name by remember { mutableStateOf(record?.name ?: "") }
    var securityCode by remember { mutableStateOf(record?.securityCode ?: "") }
    var selectedAssetType by remember { mutableStateOf(record?.assetType ?: allowedAssetTypes.firstOrNull() ?: AssetType.CASH) }
    var selectedRiskBucket by remember { mutableStateOf(record?.riskBucket ?: defaultRiskBucketFor(selectedAssetType)) }
    var quantity by remember { mutableStateOf(record?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(record?.cost?.toString() ?: "") }
    var currentPrice by remember { mutableStateOf(record?.currentPrice?.toString() ?: "") }
    var selectedCurrency by remember { mutableStateOf(record?.currency ?: account?.currency ?: "CNY") }
    var subCategory by remember { mutableStateOf(record?.subCategory ?: "") }
    var industryTag by remember { mutableStateOf(record?.industryTag ?: "") }
    var purchaseRestricted by remember { mutableStateOf(record?.purchaseRestricted ?: false) }
    var peRatio by remember { mutableStateOf(record?.peRatio?.toString() ?: "") }
    var dividendYield by remember { mutableStateOf(record?.dividendYield?.times(100)?.toString() ?: "") }
    var premiumRate by remember { mutableStateOf(record?.premiumRate?.times(100)?.toString() ?: "") }
    var locked by remember { mutableStateOf(record?.locked ?: false) }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showRiskBucketSheet by remember { mutableStateOf(false) }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    val isNewRecord = record == null
    val hasRiskBucket = selectedAssetType != AssetType.CASH
    val hasConfiguredAdvancedOptions = selectedCurrency != (account?.currency ?: "CNY") ||
        selectedRiskBucket != defaultRiskBucketFor(selectedAssetType) ||
        subCategory.isNotBlank() || industryTag.isNotBlank() || purchaseRestricted ||
        peRatio.isNotBlank() || dividendYield.isNotBlank() || premiumRate.isNotBlank() || locked

    LaunchedEffect(allowedAssetTypes) {
        if (selectedAssetType !in allowedAssetTypes && allowedAssetTypes.isNotEmpty()) {
            selectedAssetType = allowedAssetTypes.first()
            selectedRiskBucket = defaultRiskBucketFor(allowedAssetTypes.first())
        }
    }

    // 检测是否有未保存的修改
    val hasUnsavedChanges = remember(name, securityCode, quantity, cost, currentPrice, selectedCurrency, selectedAssetType, selectedRiskBucket, subCategory, industryTag, purchaseRestricted, peRatio, dividendYield, premiumRate, locked) {
        if (record == null) {
            // 新建时只要有输入就有改动
            name.isNotBlank() || securityCode.isNotBlank() || quantity.isNotBlank() || cost.isNotBlank() || currentPrice.isNotBlank() ||
            subCategory.isNotBlank() || industryTag.isNotBlank() || purchaseRestricted || peRatio.isNotBlank() || dividendYield.isNotBlank() || premiumRate.isNotBlank() || locked
        } else {
            // 编辑时对比原始值
            name != record.name ||
            securityCode != record.securityCode ||
            quantity != record.quantity.toString() ||
            cost != record.cost.toString() ||
            currentPrice != record.currentPrice.toString() ||
            selectedCurrency != record.currency ||
            selectedAssetType != record.assetType ||
            selectedRiskBucket != record.riskBucket ||
            subCategory != record.subCategory ||
            industryTag != record.industryTag ||
            purchaseRestricted != record.purchaseRestricted ||
            peRatio != (record.peRatio?.toString() ?: "") ||
            dividendYield != (record.dividendYield?.times(100)?.toString() ?: "") ||
            premiumRate != (record.premiumRate?.times(100)?.toString() ?: "") ||
            locked != record.locked
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

    if (showRiskBucketSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRiskBucketSheet = false },
            containerColor = FinColors.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = FinColors.TextTertiary) }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择策略桶", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("归类只影响配置占比和偏移提醒，可随时修改。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                RiskBucket.entries.forEach { bucket ->
                    val bucketColor = when (bucket) {
                        RiskBucket.DEFENSIVE -> FinColors.Cash
                        RiskBucket.BALANCED -> FinColors.Conservative
                        RiskBucket.AGGRESSIVE -> FinColors.Aggressive
                    }
                    Surface(onClick = { selectedRiskBucket = bucket; showRiskBucketSheet = false }, color = Color.Transparent, shape = FinShapes.sm, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            FinBucketTag(bucket.displayName(), bucketColor)
                            if (selectedRiskBucket == bucket) Text("✓", color = FinColors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    Scaffold(
        topBar = { FinTopBar(if (record == null) "添加资产" else "编辑资产", onBack = {
            if (hasUnsavedChanges) showUnsavedDialog = true else onBack()
        }) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (account != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("添加到", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                    Text(
                        "${account.name} · ${account.type.displayName()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
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
                                    securityCode = ""
                                    quantity = ""
                                    cost = ""
                                    currentPrice = ""
                                    selectedRiskBucket = defaultRiskBucketFor(type)
                                }
                                selectedAssetType = type
                            },
                            text = type.displayName()
                        )
                    }
                }
            }

            if (selectedAssetType != AssetType.CASH) {
                FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    FinInlineField(
                        value = name,
                        onValueChange = { name = it },
                        label = if (!isNewRecord && selectedAssetType in listOf(AssetType.STOCK, AssetType.ETF)) {
                            "资产名称"
                        } else {
                            getNameLabel(selectedAssetType)
                        },
                        placeholder = getNamePlaceholder(selectedAssetType)
                    )
                    if (!isNewRecord && selectedAssetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                        FinInlineField(
                            value = securityCode,
                            onValueChange = { securityCode = it },
                            label = "证券编码",
                            placeholder = "如 510300.SS、AAPL、0700.HK"
                        )
                    }
                }
            }

            DynamicAssetFields(
                selectedAssetType = selectedAssetType,
                quantity = quantity,
                cost = cost,
                currentPrice = currentPrice,
                selectedCurrency = selectedCurrency,
                showCurrentPrice = !isNewRecord || showAdvancedOptions,
                autoFillCurrentPrice = isNewRecord,
                onQuantityChange = { quantity = it },
                onCostChange = { cost = it },
                onCurrentPriceChange = { currentPrice = it }
            )

            TextButton(
                onClick = { showAdvancedOptions = !showAdvancedOptions },
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Text(
                    when {
                        showAdvancedOptions -> "收起更多选项"
                        hasConfiguredAdvancedOptions -> "更多选项 · 已设置"
                        else -> "更多选项"
                    }
                )
            }

            if (showAdvancedOptions) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasRiskBucket) {
                Column {
                    Text("资产用途", style = MaterialTheme.typography.labelLarge, color = FinColors.TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.clickable { showRiskBucketSheet = true },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bucketColor = when (selectedRiskBucket) {
                                RiskBucket.DEFENSIVE -> FinColors.Cash
                                RiskBucket.BALANCED -> FinColors.Conservative
                                RiskBucket.AGGRESSIVE -> FinColors.Aggressive
                            }
                            FinBucketTag(selectedRiskBucket.displayName(), bucketColor)
                            Text("已按资产类型自动归类 · 点击修改", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                        }
                    }
                }
            }

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

            // 落点 / 子类 + 专款锁定（对应方案第三章落点表与专款隔离）
            Column {
                Text(
                    text = "落点 / 子类（选填）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FinTextField(
                    value = subCategory,
                    onValueChange = { subCategory = it },
                    label = "落点",
                    placeholder = "如：标普500、纳指100、红利、训练仓、弹药"
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "归入同一落点的资产会在「落点跟踪」里合并，对照目标金额看缺口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                if (selectedAssetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FinTextField(
                        value = industryTag,
                        onValueChange = { industryTag = it },
                        label = "行业 / 产业链（选填）",
                        placeholder = "如：互联网、半导体、医药"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "同标签持仓会合并计算行业集中度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("手动估值与溢价（选填）", style = MaterialTheme.typography.labelLarge)
                    Text("数据不会自动抓取，请按基金公告或交易页定期更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    FinTextField(
                        value = peRatio,
                        onValueChange = { peRatio = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "PE（倍）",
                        placeholder = "如：12",
                        keyboardType = KeyboardType.Decimal
                    )
                    FinTextField(
                        value = dividendYield,
                        onValueChange = { dividendYield = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "股息率（%）",
                        placeholder = "如：4.5",
                        keyboardType = KeyboardType.Decimal
                    )
                    FinTextField(
                        value = premiumRate,
                        onValueChange = { premiumRate = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "场内溢价率（%）",
                        placeholder = "如：1.2",
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("当前限购", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text("QDII 暂停申购或限额时打开，规划页会提醒暂停加仓。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        }
                        Switch(checked = purchaseRestricted, onCheckedChange = { purchaseRestricted = it })
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "锁定为专款",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "生存层、嫁妆等专款隔离，不计入可投策略盘、不参与再平衡。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = locked, onCheckedChange = { locked = it })
                }
            }
            }

            if (!isNewRecord && quantity.isNotEmpty() && cost.isNotEmpty() && currentPrice.isNotEmpty()) {
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
                                Text("${String.format(Locale.US, "%.2f", currentValue)} $selectedCurrency")
                            }
                        } else if (selectedAssetType == AssetType.TIME_DEPOSIT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("到期本息:")
                                Text("${String.format(Locale.US, "%.2f", currentValue)} $selectedCurrency")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("利息收益:")
                                Text(
                                    "${if (profitLoss >= 0) "+" else ""}${String.format(Locale.US, "%.2f", profitLoss)} $selectedCurrency (${String.format(Locale.US, "%.1f", profitLossRatio * 100)}%)",
                                    color = if (profitLoss >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("当前市值:")
                                Text("${String.format(Locale.US, "%.2f", currentValue)} $selectedCurrency")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("盈亏:")
                                Text(
                                    "${if (profitLoss >= 0) "+" else ""}${String.format(Locale.US, "%.2f", profitLoss)} $selectedCurrency (${String.format(Locale.US, "%.1f", profitLossRatio * 100)}%)",
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
            FinSoftButton(
                text = "保存",
                onClick = {
                    val newRecord = AssetRecord(
                        id = record?.id ?: java.util.UUID.randomUUID().toString(),
                        accountId = account?.id ?: record?.accountId ?: "",
                        assetType = selectedAssetType,
                        riskBucket = if (selectedAssetType == AssetType.CASH) RiskBucket.DEFENSIVE else selectedRiskBucket,
                        name = if (selectedAssetType == AssetType.CASH) "现金" else name.trim(),
                        securityCode = when {
                            selectedAssetType == AssetType.CASH -> ""
                            isNewRecord && isTradableType -> name.trim()
                            else -> securityCode.trim()
                        },
                        quantity = qty,
                        cost = c,
                        currentPrice = price,
                        currency = selectedCurrency,
                        subCategory = subCategory.trim(),
                        industryTag = industryTag.trim(),
                        purchaseRestricted = purchaseRestricted,
                        peRatio = peRatio.toDoubleOrNull(),
                        dividendYield = dividendYield.toDoubleOrNull()?.div(100.0),
                        premiumRate = premiumRate.toDoubleOrNull()?.div(100.0),
                        locked = locked,
                        createdAt = record?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newRecord)
                },
                modifier = Modifier
                    .fillMaxWidth(),
                enabled = isFormValid
            )

            // 买入/卖出（调仓）已收敛到资产详情页主操作；编辑表单只负责修改资料

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
    showCurrentPrice: Boolean,
    autoFillCurrentPrice: Boolean,
    onQuantityChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onCurrentPriceChange: (String) -> Unit
) {
    when (selectedAssetType) {
        AssetType.CASH -> {
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinInlineField(
                    value = quantity,
                    onValueChange = { onQuantityChange(it.filter { c -> c.isDigit() || c == '.' }) },
                    label = "金额",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal
                )
            }
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

            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinInlineField(
                    value = principalInput,
                    onValueChange = { principalInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "本金",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal
                )
                FinInlineField(
                    value = rateInput,
                    onValueChange = { rateInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "年利率 (%)",
                    placeholder = "如：3.5",
                    keyboardType = KeyboardType.Decimal
                )
            }

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
                                "${String.format(Locale.US, "%.2f", maturityValue)} $selectedCurrency",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "利息: ${String.format(Locale.US, "%.2f", maturityValue - principal)} $selectedCurrency",
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
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinInlineField(
                    value = valuationInput,
                    onValueChange = { valuationInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "当前估值",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal
                )
            }
            LaunchedEffect(valuationInput) {
                val valuation = valuationInput.toDoubleOrNull() ?: 0.0
                onQuantityChange("1")
                onCostChange(valuation.toString())
                onCurrentPriceChange(valuation.toString())
            }
        }
        else -> {
            // 买入单价：编辑态用 总成本/数量 反推
            var currentPriceManuallyEdited by remember(selectedAssetType) { mutableStateOf(false) }
            var buyPrice by remember(selectedAssetType) {
                mutableStateOf(run {
                    val q = quantity.toDoubleOrNull() ?: 0.0
                    val c = cost.toDoubleOrNull() ?: 0.0
                    if (q > 0 && c > 0) trimNumber(c / q) else ""
                })
            }
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinInlineField(
                    value = quantity,
                    onValueChange = { onQuantityChange(it.filter { c -> c.isDigit() || c == '.' }) },
                    label = "数量/份额",
                    placeholder = "0",
                    keyboardType = KeyboardType.Decimal
                )
                FinInlineField(
                    value = buyPrice,
                    onValueChange = { buyPrice = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "买入单价/净值",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal
                )
                if (showCurrentPrice) {
                    FinInlineField(
                        value = currentPrice,
                        onValueChange = {
                            currentPriceManuallyEdited = true
                            onCurrentPriceChange(it.filter { c -> c.isDigit() || c == '.' })
                        },
                        label = "当前价格/净值（选填）",
                        placeholder = "默认与买入价相同",
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }
            // 总成本 = 数量 × 买入单价，自动回填
            val previewQty = quantity.toDoubleOrNull() ?: 0.0
            val previewBuy = buyPrice.toDoubleOrNull() ?: 0.0
            if (previewQty > 0 && previewBuy > 0) {
                Text(
                    text = "买入总成本：${String.format(Locale.US, "%.2f", previewQty * previewBuy)} $selectedCurrency",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            LaunchedEffect(quantity, buyPrice) {
                val q = quantity.toDoubleOrNull() ?: 0.0
                val bp = buyPrice.toDoubleOrNull() ?: 0.0
                onCostChange(if (q > 0 && bp > 0) trimNumber(q * bp) else "")
                if (autoFillCurrentPrice && !currentPriceManuallyEdited) {
                    onCurrentPriceChange(if (bp > 0) trimNumber(bp) else "")
                }
            }
        }
    }
}

/** 去掉多余小数尾零，便于把计算结果回填到表单字段 */
private fun trimNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return ""
    return java.math.BigDecimal(value).setScale(4, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
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

private fun defaultRiskBucketFor(assetType: AssetType): RiskBucket =
    assetType.defaultRiskBucket()

private fun allowedAssetTypesFor(accountType: AccountType?, currentType: AssetType?): List<AssetType> {
    val baseAllowed = AccountAssetRules.allowedAssetTypes(accountType)
    val allowed = if (currentType == null) {
        // 新建时不再过滤现金，只是把现金排到末尾（默认仍选中第一个非现金类型）
        baseAllowed.sortedBy { it == AssetType.CASH }
    } else {
        baseAllowed
    }
    return if (currentType != null && currentType !in allowed) {
        listOf(currentType) + allowed
    } else {
        allowed
    }
}
