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
import com.finunity.ui.components.FinTextField
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

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
    onConfirmBuy: (qty: Double, price: Double) -> Unit,
    onConfirmSell: (qty: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val record = summary.record
    val currency = record.currency
    val holdingLabel = trimQty(record.quantity)

    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf(if (record.currentPrice > 0) String.format("%.2f", record.currentPrice) else "") }

    val qty = qtyText.toDoubleOrNull() ?: 0.0
    val buyPrice = priceText.toDoubleOrNull() ?: 0.0

    val sellQty = qtyText.toDoubleOrNull() ?: record.quantity   // 卖出留空=全部
    val sellError = when {
        !isBuy && qtyText.isNotBlank() && qtyText.toDoubleOrNull() == null -> "请输入有效数量"
        !isBuy && sellQty <= 0 -> "卖出数量必须大于 0"
        !isBuy && sellQty > record.quantity -> "不能超过持有数量 $holdingLabel"
        else -> null
    }
    val valid = if (isBuy) qty > 0 && buyPrice > 0 else sellError == null
    val previewAmount = if (isBuy) qty * buyPrice else sellQty * record.currentPrice

    Scaffold(
        containerColor = FinColors.PageBg,
        topBar = { FinTopBar(title = "${if (isBuy) "买入" else "卖出"} ${record.name}", onBack = onBack) },
        bottomBar = {
            Surface(color = FinColors.PageBg) {
                Button(
                    onClick = { if (isBuy) onConfirmBuy(qty, buyPrice) else onConfirmSell(sellQty) },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = FinShapes.md,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuy) FinColors.Accent else FinColors.Loss,
                        contentColor = Color.White,
                        disabledContainerColor = (if (isBuy) FinColors.Accent else FinColors.Loss).copy(alpha = 0.45f),
                        disabledContentColor = Color.White
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
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 持仓概要
            WhiteCard {
                InfoRow("当前持有", "$holdingLabel 份")
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("最新价", "${String.format("%.2f", record.currentPrice)} $currency")
            }

            // 输入区
            WhiteCard {
                if (isBuy) {
                    FinTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "买入数量",
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FinTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "买入单价/净值",
                        placeholder = "0.00",
                        keyboardType = KeyboardType.Decimal
                    )
                } else {
                    FinTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "卖出数量（留空为全部）",
                        placeholder = holdingLabel,
                        keyboardType = KeyboardType.Decimal,
                        isError = sellError != null,
                        supportingText = sellError
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isBuy) "本次买入金额" else "预计金额", style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                    Text(
                        text = if (valid) "${String.format("%.2f", previewAmount)} $currency" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinColors.Number
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
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

private fun trimQty(q: Double): String = String.format("%.4f", q).trimEnd('0').trimEnd('.')
