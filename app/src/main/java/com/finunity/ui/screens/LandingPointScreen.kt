package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AllocationTarget
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.model.LandingPoint
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.displayName
import com.finunity.ui.components.FinPill
import com.finunity.ui.components.FinSoftButton
import com.finunity.ui.components.FinTextField
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.components.FinTopBar

/**
 * 落点跟踪：把三桶之下的具体落点（标普/纳指/红利/训练仓/弹药…）
 * 的目标金额、现有、缺口、上限红线和停止条件汇总成表，对应方案第三章加仓落点表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPointScreen(
    portfolioSummary: PortfolioSummary?,
    onBack: () -> Unit,
    onSaveTarget: (AllocationTarget) -> Unit,
    onDeleteTarget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = portfolioSummary
    val baseCurrency = summary?.baseCurrency ?: "CNY"
    val points = summary?.landingPoints ?: emptyList()

    // null = 不显示弹窗；非 null = 编辑/新增中的落点（existing 为 null 表示新增）
    var editing by remember { mutableStateOf<LandingPoint?>(null) }
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar("落点跟踪", onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNew = true },
                containerColor = FinColors.Accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增落点目标")
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 策略盘摘要
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FinShapes.xl,
                    colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("可投策略盘", style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                        Text(
                            text = formatCurrency(summary?.strategyAssets ?: 0.0, baseCurrency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = FinColors.Number
                        )
                        if ((summary?.lockedAssets ?: 0.0) > 0.0) {
                            Text(
                                text = "已锁定专款 ${formatCurrency(summary?.lockedAssets ?: 0.0, baseCurrency)}（生存层/嫁妆等，不计入策略盘）",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinColors.TextSecondary
                            )
                        }
                        Text(
                            text = "总资产 ${formatCurrency(summary?.totalAssets ?: 0.0, baseCurrency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinColors.TextSecondary
                        )
                    }
                }
            }

            if (points.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FinShapes.xl,
                        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("还没有落点", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                            Text(
                                text = "在资产编辑里给持仓填上「落点」（如标普500、纳指100、红利、训练仓），" +
                                    "再用右下角「+」设定每个落点的目标金额，就能在这里看到目标/现有/缺口和停止条件。",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinColors.TextSecondary
                            )
                        }
                    }
                }
            }

            items(points) { point ->
                LandingPointCard(
                    point = point,
                    baseCurrency = baseCurrency,
                    onClick = { editing = point }
                )
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }

    if (showNew) {
        TargetEditDialog(
            point = null,
            baseCurrency = baseCurrency,
            onDismiss = { showNew = false },
            onSave = { onSaveTarget(it); showNew = false },
            onDelete = null
        )
    }

    editing?.let { point ->
        TargetEditDialog(
            point = point,
            baseCurrency = baseCurrency,
            onDismiss = { editing = null },
            onSave = { onSaveTarget(it); editing = null },
            onDelete = if (point.hasTarget) {
                { onDeleteTarget(point.subCategory); editing = null }
            } else null
        )
    }
}

@Composable
private fun LandingPointCard(
    point: LandingPoint,
    baseCurrency: String,
    onClick: () -> Unit
) {
    val color = bucketColorLp(point.riskBucket)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(color))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(point.subCategory, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                }
                Text(point.riskBucket.displayName(), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, color = color)
            }

            if (!point.hasTarget) {
                Text(
                    text = "现有 ${formatCurrency(point.currentValue, baseCurrency)} · 未设目标，点此设定",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinColors.Accent
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("现有 ${formatCurrency(point.currentValue, baseCurrency)}",
                    style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                Text("目标 ${formatCurrency(point.targetAmount, baseCurrency)}",
                    style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
            }

            // 进度条
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(999.dp)).background(FinColors.Outline)
            ) {
                Box(modifier = Modifier.fillMaxHeight()
                    .fillMaxWidth(point.progress.toFloat().coerceIn(0f, 1f))
                    .background(if (point.overCap) FinColors.Loss else color))
            }

            // 缺口 / 达标 / 超限提示
            val gapText = when {
                point.reachedTarget -> "已达目标，可停止加仓"
                else -> "还需增配约 ${formatCurrency(point.gap, baseCurrency)}"
            }
            Text(
                text = gapText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (point.reachedTarget) FinColors.Profit else FinColors.Accent
            )

            if (point.overCap) {
                Text(
                    text = "⚠ 已超过上限 ${formatCurrency(point.capAmount, baseCurrency)}，建议不再加仓",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = FinColors.Loss
                )
            }

            if (point.stopNote.isNotBlank()) {
                Text(
                    text = "停止条件：${point.stopNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TargetEditDialog(
    point: LandingPoint?,
    baseCurrency: String,
    onDismiss: () -> Unit,
    onSave: (AllocationTarget) -> Unit,
    onDelete: (() -> Unit)?
) {
    // point 为 null = 新增；point.hasTarget=false = 给未跟踪持仓补设目标（锁定名称）
    val nameLocked = point != null
    var name by remember { mutableStateOf(point?.subCategory ?: "") }
    var bucket by remember { mutableStateOf(point?.riskBucket ?: RiskBucket.AGGRESSIVE) }
    var targetText by remember {
        mutableStateOf(if (point?.hasTarget == true) trimAmount(point.targetAmount) else "")
    }
    var capText by remember {
        mutableStateOf(if (point?.hasTarget == true && point.capAmount > 0) trimAmount(point.capAmount) else "")
    }
    var stopNote by remember { mutableStateOf(point?.stopNote ?: "") }

    val buckets = listOf(RiskBucket.AGGRESSIVE, RiskBucket.BALANCED, RiskBucket.DEFENSIVE)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (point?.hasTarget == true) "编辑落点目标" else "新增落点目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FinTextField(
                    value = name,
                    onValueChange = { if (!nameLocked) name = it },
                    label = "落点名称",
                    placeholder = "如：标普500、纳指100、红利"
                )
                if (nameLocked) {
                    Text("落点名称与持仓关联，不可修改。",
                        style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                }
                Text("所属三桶", style = MaterialTheme.typography.labelMedium, color = FinColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buckets.forEach { b ->
                        FinPill(selected = bucket == b, onClick = { bucket = b }, text = b.displayName())
                    }
                }
                FinTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "目标金额（$baseCurrency）",
                    placeholder = "如：90000",
                    keyboardType = KeyboardType.Decimal
                )
                FinTextField(
                    value = capText,
                    onValueChange = { capText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "上限红线（选填，$baseCurrency）",
                    placeholder = "如：60000，留空=不设上限",
                    keyboardType = KeyboardType.Decimal
                )
                FinTextField(
                    value = stopNote,
                    onValueChange = { stopNote = it },
                    label = "停止条件（选填）",
                    placeholder = "如：投满即停、达标后只再平衡"
                )
            }
        },
        confirmButton = {
            val target = targetText.toDoubleOrNull() ?: 0.0
            val valid = name.isNotBlank() && target > 0
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        AllocationTarget(
                            subCategory = name.trim(),
                            riskBucket = bucket,
                            targetAmount = target,
                            capAmount = capText.toDoubleOrNull() ?: 0.0,
                            stopNote = stopNote.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除目标", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun trimAmount(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun bucketColorLp(bucket: RiskBucket): Color = when (bucket) {
    RiskBucket.AGGRESSIVE -> FinColors.Aggressive
    RiskBucket.BALANCED -> FinColors.Conservative
    RiskBucket.DEFENSIVE -> FinColors.Cash
}
