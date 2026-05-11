package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
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
    var baseCurrency by remember { mutableStateOf(settings.baseCurrency) }
    var rebalanceThreshold by remember { mutableStateOf((settings.rebalanceThreshold * 100).toString()) }
    var targetAllocation by remember { mutableStateOf(settings.targetAllocation) }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    val targetAllocationMap = remember(targetAllocation) {
        parseTargetAllocation(targetAllocation)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("设置", fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                .padding(top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 基准货币
            Column {
                Text(
                    text = "基准货币",
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
                    text = "当资产配置偏离目标超过此阈值时，将提示再平衡建议",
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
                OutlinedTextField(
                    value = targetAllocation,
                    onValueChange = { targetAllocation = it },
                    label = { Text("配置格式") },
                    placeholder = { Text("如：CONSERVATIVE:0.2,AGGRESSIVE:0.6,CASH:0.2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "风险维度及其目标占比，之和应为 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // 解析预览
                if (targetAllocationMap.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                            Text("配置预览", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            targetAllocationMap.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = when (key) {
                                            "CONSERVATIVE" -> "稳健型"
                                            "AGGRESSIVE" -> "进攻型"
                                            "CASH" -> "现金类"
                                            else -> key
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${String.format("%.0f", value * 100)}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            val total = targetAllocationMap.values.sum()
                            if (kotlin.math.abs(total - 1.0) > 0.001) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "警告：配置之和为 ${String.format("%.1f", total * 100)}%，应为 100%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            val allocationTotal = targetAllocationMap.values.sum()
            val isValidAllocation = kotlin.math.abs(allocationTotal - 1.0) <= 0.001 &&
                    targetAllocationMap.keys.all { it in listOf("CONSERVATIVE", "AGGRESSIVE", "CASH") }
            val saveError = when {
                !isValidAllocation -> "目标配置无效：各项占比之和应为 100%，且 key 应为 CONSERVATIVE/AGGRESSIVE/CASH"
                else -> null
            }

            Button(
                onClick = {
                    // 用户输入的是百分比（如 5 表示 5%），需要除以 100 转为小数
                    val threshold = (rebalanceThreshold.toDoubleOrNull() ?: 5.0) / 100
                    val newSettings = settings.copy(
                        baseCurrency = baseCurrency,
                        rebalanceThreshold = threshold.coerceIn(0.01, 0.5),
                        targetAllocation = targetAllocation.trim()
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