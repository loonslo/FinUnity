package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.model.AssetRecordSummary
import com.finunity.ui.components.FinInlineField
import com.finunity.ui.components.FinCard
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes
import java.text.SimpleDateFormat
import java.util.*

/**
 * 买入/卖出（调仓）独立页面，替代底部弹层。
 * isBuy=true 买入加仓（数量+单价）；isBuy=false 卖出减仓（数量，按最新价估算金额）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    summary: AssetRecordSummary,
    isBuy: Boolean,
    onBack: () -> Unit,
    onConfirmBuy: (qty: Double, price: Double, fee: Double, timestamp: Long, note: String?) -> Unit,
    onConfirmSell: (qty: Double, price: Double, fee: Double, timestamp: Long, note: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val record = summary.record
    val currency = record.currency
    val holdingLabel = trimQty(record.quantity)

    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf(if (record.currentPrice > 0) String.format(Locale.US, "%.2f", record.currentPrice) else "") }
    var feeText by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false } }
    var dateText by remember { mutableStateOf(dateFormat.format(Date())) }
    var noteText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val buyPrice = priceText.toDoubleOrNull() ?: 0.0
    val tradePrice = priceText.toDoubleOrNull() ?: 0.0
    val fee = feeText.toDoubleOrNull() ?: 0.0
    val tradeTimestamp = remember(dateText) { runCatching { dateFormat.parse(dateText)?.time }.getOrNull() }

    val sellQty = qtyText.toDoubleOrNull() ?: record.quantity   // 卖出留空=全部
    val sellError = when {
        !isBuy && qtyText.isNotBlank() && qtyText.toDoubleOrNull() == null -> "请输入有效数量"
        !isBuy && sellQty <= 0 -> "卖出数量必须大于 0"
        !isBuy && sellQty > record.quantity -> "不能超过持有数量 $holdingLabel"
        !isBuy && tradePrice <= 0 -> "请填写实际成交价"
        !isBuy && fee < 0 -> "费用不能为负数"
        !isBuy && tradeTimestamp == null -> "日期格式应为 yyyy-MM-dd"
        else -> null
    }
    val valid = if (isBuy) qty > 0 && buyPrice > 0 && fee >= 0 && tradeTimestamp != null else sellError == null
    val previewAmount = if (isBuy) qty * buyPrice + fee else (sellQty * tradePrice - fee).coerceAtLeast(0.0)

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(if (isBuy) "确认买入" else "确认卖出") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${record.name} · ${summary.accountName}", fontWeight = FontWeight.SemiBold)
                    Text("数量：${if (isBuy) trimQty(qty) else trimQty(sellQty)} 份")
                    Text("成交价：${formatCurrency(tradePrice, currency)}")
                    Text("费用：${formatCurrency(fee, currency)}")
                    Text(
                        if (isBuy) "预计扣款：${formatCurrency(previewAmount, currency)}"
                        else "预计到账：${formatCurrency(previewAmount, currency)}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("日期：$dateText", color = FinColors.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    if (isBuy) onConfirmBuy(qty, buyPrice, fee, tradeTimestamp!!, noteText.trim().ifBlank { null })
                    else onConfirmSell(sellQty, tradePrice, fee, tradeTimestamp!!, noteText.trim().ifBlank { null })
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("返回修改") }
            }
        )
    }

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar(title = "${if (isBuy) "买入" else "卖出"} ${record.name}", onBack = onBack) },
        bottomBar = {
            Surface(color = FinColors.PageBg) {
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuy) FinColors.Profit else FinColors.Loss,
                        contentColor = Color.White,
                        disabledContainerColor = FinColors.SurfaceElevated,
                        disabledContentColor = FinColors.TextTertiary
                    )
                ) {
                    Text(if (isBuy) "确认买入" else "确认卖出", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 持仓概要
            WhiteCard {
                InfoRow("所属账户", summary.accountName)
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("资产币种", currency)
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("当前持有", "$holdingLabel 份")
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("最新价", formatCurrency(record.currentPrice, currency))
            }

            // 输入区
            WhiteCard {
                if (isBuy) {
                    FinInlineField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "买入数量",
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal
                    )
                    FinInlineField(value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } }, label = "买入单价/净值", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
                } else {
                    FinInlineField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "卖出数量（留空为全部）",
                        placeholder = holdingLabel,
                        keyboardType = KeyboardType.Decimal,
                        isError = sellError != null
                    )
                    FinInlineField(value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } }, label = "实际成交价", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isBuy) "本次买入金额" else "预计净到账", style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                    Text(if (valid) formatCurrency(previewAmount, currency) else "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                }
                FinInlineField(value = feeText, onValueChange = { feeText = it.filter { c -> c.isDigit() || c == '.' } }, label = "费用（选填）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
                FinInlineField(value = dateText, onValueChange = { dateText = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, label = "成交日期", placeholder = "yyyy-MM-dd")
                FinInlineField(value = noteText, onValueChange = { noteText = it }, label = "备注（选填）", placeholder = if (isBuy) "如：按计划加仓" else "如：按计划减仓")
                if (!isBuy && sellError != null) {
                    Text(sellError, color = FinColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    FinCard(content = content)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = FinColors.TextPrimary)
    }
}

private fun trimQty(q: Double): String = String.format(Locale.US, "%.4f", q).trimEnd('0').trimEnd('.')
