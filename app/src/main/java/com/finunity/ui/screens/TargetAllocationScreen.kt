package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

/**
 * 标普四象限目标配置模板。
 * 四个维度：进取(生钱) / 稳健(保本) / 保命(保险) / 防守(要花)。
 */
private data class AllocationTemplate(
    val key: String,
    val name: String,
    val desc: String,
    val aggressive: Int,    // 进取
    val conservative: Int,  // 稳健
    val insurance: Int,     // 保命
    val cash: Int           // 防守
)

private val ALLOCATION_TEMPLATES = listOf(
    AllocationTemplate("conservative", "保守型", "稳健保本为主，适合风险承受力低", aggressive = 10, conservative = 60, insurance = 20, cash = 10),
    AllocationTemplate("steady", "稳健型", "兼顾保本与增长，波动可控", aggressive = 20, conservative = 50, insurance = 20, cash = 10),
    AllocationTemplate("balanced", "平衡型", "标普经典 4·3·2·1 配置", aggressive = 30, conservative = 40, insurance = 20, cash = 10),
    AllocationTemplate("aggressive", "进取型", "追求长期增长，能承受较大波动", aggressive = 50, conservative = 20, insurance = 20, cash = 10)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAllocationScreen(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialMap = remember(settings.targetAllocation) { parseTargetAllocation(settings.targetAllocation) }
    var aggressive by remember { mutableStateOf(((initialMap["AGGRESSIVE"] ?: 0.3) * 100).toInt().toString()) }
    var conservative by remember { mutableStateOf(((initialMap["CONSERVATIVE"] ?: 0.4) * 100).toInt().toString()) }
    var insurance by remember { mutableStateOf(((initialMap["INSURANCE"] ?: 0.0) * 100).toInt().toString()) }
    var cash by remember { mutableStateOf(((initialMap["CASH"] ?: 0.1) * 100).toInt().toString()) }

    val aggressiveV = aggressive.toDoubleOrNull() ?: 0.0
    val conservativeV = conservative.toDoubleOrNull() ?: 0.0
    val insuranceV = insurance.toDoubleOrNull() ?: 0.0
    val cashV = cash.toDoubleOrNull() ?: 0.0
    val total = aggressiveV + conservativeV + insuranceV + cashV
    val isValid = kotlin.math.abs(total - 100.0) <= 0.001

    // 判断当前数值匹配哪个模板
    val activeTemplate = ALLOCATION_TEMPLATES.firstOrNull {
        it.aggressive.toDouble() == aggressiveV && it.conservative.toDouble() == conservativeV &&
            it.insurance.toDouble() == insuranceV && it.cash.toDouble() == cashV
    }?.key

    fun applyTemplate(t: AllocationTemplate) {
        aggressive = t.aggressive.toString()
        conservative = t.conservative.toString()
        insurance = t.insurance.toString()
        cash = t.cash.toString()
    }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = {
            TopAppBar(
                title = { Text("目标配置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FinColors.PageBg)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 模板选择
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择一个模板", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    ALLOCATION_TEMPLATES.forEach { t ->
                        TemplateRow(
                            template = t,
                            selected = activeTemplate == t.key,
                            onClick = { applyTemplate(t) }
                        )
                    }
                }
            }

            // 微调
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("微调比例", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                        Text(
                            text = "${activeTemplate?.let { "" } ?: "自定义 · "}合计 ${String.format("%.0f", total)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValid) FinColors.TextSecondary else MaterialTheme.colorScheme.error
                        )
                    }
                    PercentRow("进取", "生钱的钱 · 5 年以上", FinColors.Aggressive, aggressive) {
                        aggressive = it.filter { c -> c.isDigit() }
                    }
                    PercentRow("稳健", "保本的钱 · 1-3 年", FinColors.Conservative, conservative) {
                        conservative = it.filter { c -> c.isDigit() }
                    }
                    PercentRow("保命", "保命的钱 · 应急与保险", FinColors.Insurance, insurance) {
                        insurance = it.filter { c -> c.isDigit() }
                    }
                    PercentRow("防守", "要花的钱 · 随时要用", FinColors.Cash, cash) {
                        cash = it.filter { c -> c.isDigit() }
                    }
                }
            }

            // 预览条
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("目标结构预览", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    StackedAllocationBar(
                        segments = listOf(
                            aggressiveV to FinColors.Aggressive,
                            conservativeV to FinColors.Conservative,
                            insuranceV to FinColors.Insurance,
                            cashV to FinColors.Cash
                        )
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            targetAllocation = "CONSERVATIVE:${conservativeV / 100},AGGRESSIVE:${aggressiveV / 100}," +
                                "INSURANCE:${insuranceV / 100},CASH:${cashV / 100}"
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = FinShapes.md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen,
                    contentColor = FinColors.Number
                )
            ) {
                Text("保存目标配置", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = FinColors.Number)
            }
            if (!isValid) {
                Text("四项占比之和需等于 100%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TemplateRow(
    template: AllocationTemplate,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FinShapes.md)
            .background(if (selected) FinColors.SoftGreen else FinColors.PageBg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(template.desc, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
        }
        Text(
            text = "进${template.aggressive} 稳${template.conservative} 保${template.insurance} 守${template.cash}",
            style = MaterialTheme.typography.labelSmall,
            color = FinColors.TextSecondary
        )
    }
}

@Composable
private fun PercentRow(
    label: String,
    hint: String,
    color: Color,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = FinColors.TextSecondary)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = FinShapes.sm,
            modifier = Modifier.width(110.dp)
        )
    }
}

@Composable
private fun StackedAllocationBar(segments: List<Pair<Double, Color>>) {
    val total = segments.sumOf { it.first }.coerceAtLeast(1.0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FinColors.Outline)
    ) {
        segments.forEach { (value, color) ->
            if (value > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((value / total).toFloat())
                        .background(color)
                )
            }
        }
    }
}
