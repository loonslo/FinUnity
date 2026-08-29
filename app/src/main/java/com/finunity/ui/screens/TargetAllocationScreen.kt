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
import com.finunity.ui.components.FinTopBar
import java.util.Locale

/** 三桶目标配置模板。 */
private data class AllocationTemplate(
    val key: String,
    val name: String,
    val desc: String,
    val aggressive: Int,    // 进攻
    val balanced: Int,      // 稳健
    val defensive: Int      // 防守
)

private val ALLOCATION_TEMPLATES = listOf(
    AllocationTemplate("conservative", "保守型", "稳健保值为主，适合风险承受力低", aggressive = 10, balanced = 80, defensive = 10),
    AllocationTemplate("steady", "稳健", "兼顾保值与增长，波动可控", aggressive = 20, balanced = 70, defensive = 10),
    AllocationTemplate("balanced", "平衡型", "默认三桶配置", aggressive = 30, balanced = 60, defensive = 10),
    AllocationTemplate("aggressive", "进攻", "追求长期增长，能承受较大波动", aggressive = 50, balanced = 40, defensive = 10)
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
    var balanced by remember { mutableStateOf(((initialMap["BALANCED"] ?: 0.6) * 100).toInt().toString()) }
    var defensive by remember { mutableStateOf(((initialMap["DEFENSIVE"] ?: 0.1) * 100).toInt().toString()) }
    var maxAgg by remember { mutableStateOf((settings.maxAggressiveRatio * 100).toInt().toString()) }

    val aggressiveV = aggressive.toDoubleOrNull() ?: 0.0
    val balancedV = balanced.toDoubleOrNull() ?: 0.0
    val defensiveV = defensive.toDoubleOrNull() ?: 0.0
    val total = aggressiveV + balancedV + defensiveV
    val isValid = kotlin.math.abs(total - 100.0) <= 0.001

    // 判断当前数值匹配哪个模板
    val activeTemplate = ALLOCATION_TEMPLATES.firstOrNull {
        it.aggressive.toDouble() == aggressiveV && it.balanced.toDouble() == balancedV &&
            it.defensive.toDouble() == defensiveV
    }?.key

    fun applyTemplate(t: AllocationTemplate) {
        aggressive = t.aggressive.toString()
        balanced = t.balanced.toString()
        defensive = t.defensive.toString()
    }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar("目标配置", onBack) },
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
                colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择一个模板", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    Text("保存后会影响规划页偏离判定、再平衡建议和月度复盘金额。",
                        style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
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
                colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
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
                            text = "${activeTemplate?.let { "" } ?: "自定义 · "}合计 ${String.format(Locale.US, "%.0f", total)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValid) FinColors.TextSecondary else MaterialTheme.colorScheme.error
                        )
                    }
                    PercentRow("进攻", "生钱的钱 · 5 年以上", FinColors.Aggressive, aggressive) {
                        aggressive = it.filter { c -> c.isDigit() }
                    }
                    PercentRow("稳健", "保障、保值与低波动资产", FinColors.Conservative, balanced) {
                        balanced = it.filter { c -> c.isDigit() }
                    }
                    PercentRow("防守", "现金、活期与近期备用金", FinColors.Cash, defensive) {
                        defensive = it.filter { c -> c.isDigit() }
                    }
                }
            }

            // 预览条
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("目标结构预览", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    StackedAllocationBar(
                        segments = listOf(
                            aggressiveV to FinColors.Aggressive,
                            balancedV to FinColors.Conservative,
                            defensiveV to FinColors.Cash
                        )
                    )
                }
            }

            // 永不满仓 · 风险仓位上限
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FinShapes.xl,
                colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("永不满仓 · 风险仓位上限", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                    Text(
                        text = "进攻（生钱的钱）占比超过此上限时，规划页「风险体检」会提示风险仓位偏高。默认 70%。",
                        style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary
                    )
                    PercentRow("风险仓位上限", "进攻占比的红线", FinColors.Aggressive, maxAgg) {
                        maxAgg = it.filter { c -> c.isDigit() }
                    }
                }
            }

            Button(
                onClick = {
                    val maxAggV = (maxAgg.toIntOrNull() ?: 70).coerceIn(0, 100)
                    onSave(
                        settings.copy(
                            targetAllocation = "DEFENSIVE:${defensiveV / 100},BALANCED:${balancedV / 100}," +
                                "AGGRESSIVE:${aggressiveV / 100}",
                            maxAggressiveRatio = maxAggV / 100.0
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
                Text("三项占比之和需等于 100%", style = MaterialTheme.typography.bodySmall,
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
            text = "进${template.aggressive} 稳${template.balanced} 守${template.defensive}",
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
