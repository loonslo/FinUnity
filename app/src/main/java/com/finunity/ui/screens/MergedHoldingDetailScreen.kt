package com.finunity.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.MergedHoldingSummary
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors

/** A merged code is a view, not an editable entity; every trade starts from one source row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergedHoldingDetailScreen(
    holding: MergedHoldingSummary,
    sources: List<AssetRecordSummary>,
    baseCurrency: String,
    onOpenTrade: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { FinTopBar("${holding.displayName} · 资产详情", onBack) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = FinColors.Surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(holding.code, color = FinColors.TextSecondary)
                        Text("总市值 ${formatCurrency(holding.currentValue, baseCurrency)}", fontWeight = FontWeight.Bold)
                        Text("总成本 ${formatCurrency(holding.totalCost, baseCurrency)}")
                        Text(
                            "总盈亏 ${formatSignedMoney(holding.profitLoss, baseCurrency)} · 收益率 ${formatSignedPercent(holding.profitLossRatio)}",
                            color = if (holding.profitLoss >= 0) FinColors.Profit else FinColors.Loss
                        )
                        Text(
                            if (holding.currency == "MIXED") "币种状态：MIXED（各来源分币种计价，禁止合并单价）"
                            else "币种：${holding.currency} · 加权成本 ${formatCurrency(holding.averageCost, holding.currency)}",
                            color = if (holding.currency == "MIXED") FinColors.Warning else FinColors.TextSecondary
                        )
                    }
                }
            }
            item { Text("账户来源（记录交易前选择具体账户）", fontWeight = FontWeight.SemiBold) }
            items(sources, key = { it.record.id }) { source ->
                SourceRow(source, baseCurrency, onOpenTrade)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SourceRow(
    source: AssetRecordSummary,
    baseCurrency: String,
    onOpenTrade: (String, Boolean) -> Unit
) {
    val record: AssetRecord = source.record
    Card(colors = CardDefaults.cardColors(containerColor = FinColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(source.accountName, fontWeight = FontWeight.SemiBold)
                Text(record.currency, color = FinColors.Secondary)
            }
            Text("数量 ${formatQuantity(record.quantity)} · 总成本 ${formatCurrency(record.cost, record.currency)}")
            Text("单位成本 ${formatCurrency(record.averageCost, record.currency)} · 当前单价 ${formatCurrency(record.currentPrice, record.currency)}")
            Text("市值 ${formatCurrency(source.currentValue, baseCurrency)} · 盈亏 ${formatSignedMoney(source.profitLoss, baseCurrency)}")
            Text("价格状态：${if (record.currentPrice > 0.0) "有本地价格" else "缺价格"}", color = FinColors.TextSecondary)
            Button(
                onClick = { onOpenTrade(record.id, true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FinColors.Secondary)
            ) { Text("记录交易") }
        }
    }
}

private fun formatQuantity(value: Double): String = String.format(java.util.Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
