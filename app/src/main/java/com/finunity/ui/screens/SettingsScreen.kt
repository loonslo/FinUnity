package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinSettingRow
import com.finunity.ui.components.FinTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenRecurringRules: () -> Unit = {},
    onOpenReconciliation: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenHoldingImport: () -> Unit = {},
    onOpenCsvImport: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenPlanning: () -> Unit = {},
    onOpenMonthlyReview: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenExpenseSimulation: () -> Unit = {},
    onOpenStressTest: () -> Unit = {},
    onOpenLandingPoints: () -> Unit = {},
    onBack: () -> Unit,
    notificationsAllowed: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var baseCurrency by remember { mutableStateOf(settings.baseCurrency) }
    var rebalanceThreshold by remember { mutableStateOf((settings.rebalanceThreshold * 100).toString()) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")
    val thresholdInput = rebalanceThreshold.toDoubleOrNull()
    val thresholdValid = thresholdInput != null && thresholdInput in 1.0..50.0
    val thresholdValue = (thresholdInput ?: settings.rebalanceThreshold * 100) / 100
    val hasChanges = baseCurrency != settings.baseCurrency ||
        kotlin.math.abs(thresholdValue - settings.rebalanceThreshold) > 0.0001

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("本页修改尚未保存，返回后将丢失。") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") } }
        )
    }

    Scaffold(
        topBar = { FinTopBar("设置", onBack = { if (hasChanges) showDiscardDialog = true else onBack() }) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
                .navigationBarsPadding()
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
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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

            Column {
                Text(
                    text = "月度复盘提醒",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (notificationsAllowed) {
                        "通知权限已开启，衡仓会按月提醒你查看资产变化。"
                    } else {
                        "默认不打扰。需要月度复盘提醒时，可在这里主动开启通知权限。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestNotificationPermission,
                    enabled = !notificationsAllowed,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (notificationsAllowed) "通知权限已开启" else "开启月度提醒")
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
                    isError = !thresholdValid,
                    supportingText = if (!thresholdValid) {
                        { Text("请输入 1–50 之间的百分比") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "当资产配置偏离目标超过此阈值时，将提示调整建议",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // 目标资产配置统一在「规划」页编辑，设置页不再重复入口（仅保留本位币与再平衡阈值）

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮（仅本位币和阈值）
            Button(
                onClick = {
                    val threshold = (thresholdInput ?: 5.0) / 100
                    val newSettings = settings.copy(
                        baseCurrency = baseCurrency,
                        rebalanceThreshold = threshold.coerceIn(0.01, 0.5)
                    )
                    onSave(newSettings)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = thresholdValid && hasChanges,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                )
            ) {
                Text("保存设置", style = MaterialTheme.typography.titleMedium, color = FinColors.Number)
            }

            Text(
                text = "数据管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    FinSettingRow("导入资产", "截图识别或手动添加", onClick = onOpenHoldingImport)
                FinSettingRow("CSV 导入", "批量导入本地记录", onClick = onOpenCsvImport)
                FinSettingRow("备份恢复", "导出或恢复完整账本", onClick = onOpenBackup, showDivider = false)
            }

            Text(
                text = "报表与维护",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinSettingRow("隐私说明", "本地数据与权限", onClick = onOpenPrivacy)
                FinSettingRow("财务报表", "收入、支出与净资产", onClick = onOpenReport)
                FinSettingRow("数据对账", "检查并修复记录", onClick = onOpenReconciliation)
                FinSettingRow("导出报表", "CSV 与 HTML", onClick = onOpenExport, showDivider = false)
            }

            Text(
                text = "更多工具",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            FinCard(contentPadding = PaddingValues(horizontal = 16.dp)) {
                FinSettingRow("目标配置", "查看三桶比例与偏离", onClick = onOpenPlanning)
                FinSettingRow("月度复盘", "解释本月资产变化", onClick = onOpenMonthlyReview)
                FinSettingRow("资产历史", "查看历史快照", onClick = onOpenHistory)
                FinSettingRow("周期收支规则", "仅设置工资、房贷、房租、保费等显著事项", onClick = onOpenRecurringRules)
                FinSettingRow("落点跟踪", "高级目标工具", onClick = onOpenLandingPoints)
                FinSettingRow("大额支出模拟", "评估重要支出影响", onClick = onOpenExpenseSimulation)
                FinSettingRow("压力测试", "查看极端情景影响", onClick = onOpenStressTest, showDivider = false)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
