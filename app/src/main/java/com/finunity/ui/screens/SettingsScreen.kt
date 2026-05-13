package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.parseTargetAllocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialTargetAllocationMap = remember(settings.targetAllocation) {
        parseTargetAllocation(settings.targetAllocation)
    }
    var baseCurrency by remember { mutableStateOf(settings.baseCurrency) }
    var rebalanceThreshold by remember { mutableStateOf((settings.rebalanceThreshold * 100).toString()) }
    var conservativePercent by remember {
        mutableStateOf(((initialTargetAllocationMap["CONSERVATIVE"] ?: 0.2) * 100).toInt().toString())
    }
    var aggressivePercent by remember {
        mutableStateOf(((initialTargetAllocationMap["AGGRESSIVE"] ?: 0.6) * 100).toInt().toString())
    }
    var defensivePercent by remember {
        mutableStateOf(((initialTargetAllocationMap["CASH"] ?: 0.2) * 100).toInt().toString())
    }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    val conservativeValue = conservativePercent.toDoubleOrNull() ?: 0.0
    val aggressiveValue = aggressivePercent.toDoubleOrNull() ?: 0.0
    val defensiveValue = defensivePercent.toDoubleOrNull() ?: 0.0
    val allocationTotalPercent = conservativeValue + aggressiveValue + defensiveValue

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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 本位币
            Column {
                Text(
                    text = "本位币",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { currency ->
                        FilterChip(
                            selected = baseCurrency == currency,
                            onClick = { baseCurrency = currency },
                            label = { Text("$currency (${currencyLabels[currency]})") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Text(
                    text = "本位币仅影响总览折算显示，不会修改账户和持仓的原始币种。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // 再平衡阈值
            Column {
                Text(
                    text = "再平衡阈值",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rebalanceThreshold,
                    onValueChange = { rebalanceThreshold = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("偏离阈值（百分比）") },
                    placeholder = { Text("如：5") },
                    suffix = { Text("%") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "当资产配置偏离目标超过此阈值时，将提示调整建议",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // 目标资产配置
            Column {
                Text(
                    text = "目标资产配置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AllocationPercentField(
                    label = "稳健",
                    value = conservativePercent,
                    onValueChange = { conservativePercent = it.filter { c -> c.isDigit() || c == '.' } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                AllocationPercentField(
                    label = "进取",
                    value = aggressivePercent,
                    onValueChange = { aggressivePercent = it.filter { c -> c.isDigit() || c == '.' } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                AllocationPercentField(
                    label = "防守",
                    value = defensivePercent,
                    onValueChange = { defensivePercent = it.filter { c -> c.isDigit() || c == '.' } }
                )
                Text(
                    text = "合计 ${String.format("%.0f", allocationTotalPercent)}%，需等于 100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (kotlin.math.abs(allocationTotalPercent - 100.0) <= 0.001) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                TargetAllocationPreview(
                    conservative = conservativeValue,
                    aggressive = aggressiveValue,
                    defensive = defensiveValue
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            val isValidAllocation = kotlin.math.abs(allocationTotalPercent - 100.0) <= 0.001
            val saveError = when {
                !isValidAllocation -> "目标配置无效：各项占比之和应为 100%"
                else -> null
            }

            Button(
                onClick = {
                    // 用户输入的是百分比（如 5 表示 5%），需要除以 100 转为小数
                    val threshold = (rebalanceThreshold.toDoubleOrNull() ?: 5.0) / 100
                    val newSettings = settings.copy(
                        baseCurrency = baseCurrency,
                        rebalanceThreshold = threshold.coerceIn(0.01, 0.5),
                        targetAllocation = "CONSERVATIVE:${conservativeValue / 100},AGGRESSIVE:${aggressiveValue / 100},CASH:${defensiveValue / 100}"
                    )
                    onSave(newSettings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = isValidAllocation,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存设置", style = MaterialTheme.typography.titleMedium)
            }
            if (saveError != null) {
                Text(
                    text = saveError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TargetAllocationPreview(
    conservative: Double,
    aggressive: Double,
    defensive: Double
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("目标配置预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = targetSummary(conservative, aggressive, defensive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TargetBar(label = "进取", value = aggressive, color = androidx.compose.ui.graphics.Color(0xFF3D7A5C))
            TargetBar(label = "稳健", value = conservative, color = androidx.compose.ui.graphics.Color(0xFF8DA7C7))
            TargetBar(label = "防守", value = defensive, color = androidx.compose.ui.graphics.Color(0xFFD8B36A))
        }
    }
}

@Composable
private fun TargetBar(
    label: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${String.format("%.0f", value)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / 100).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(999.dp))
            )
        }
    }
}

private fun targetSummary(
    conservative: Double,
    aggressive: Double,
    defensive: Double
): String = when {
    aggressive >= conservative && aggressive >= defensive -> "进取为主，兼顾稳健与防守"
    conservative >= aggressive && conservative >= defensive -> "稳健为主，控制波动"
    else -> "防守为主，保留灵活空间"
}

@Composable
private fun AllocationPercentField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text("%") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp)
    )
}
