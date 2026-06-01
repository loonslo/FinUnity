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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Settings
import com.finunity.ui.theme.FinColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onBack: () -> Unit,
    onOpenTargetAllocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var baseCurrency by remember { mutableStateOf(settings.baseCurrency) }
    var rebalanceThreshold by remember { mutableStateOf((settings.rebalanceThreshold * 100).toString()) }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

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

            // 目标资产配置 → 跳转独立配置页
            Column {
                Text(
                    text = "目标资产配置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = onOpenTargetAllocation
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "编辑目标配置",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "调整四象限目标比例，使用独立配置表单",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮（仅本位币和阈值）
            Button(
                onClick = {
                    val threshold = (rebalanceThreshold.toDoubleOrNull() ?: 5.0) / 100
                    val newSettings = settings.copy(
                        baseCurrency = baseCurrency,
                        rebalanceThreshold = threshold.coerceIn(0.01, 0.5)
                    )
                    onSave(newSettings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                )
            ) {
                Text("保存设置", style = MaterialTheme.typography.titleMedium, color = FinColors.Number)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
