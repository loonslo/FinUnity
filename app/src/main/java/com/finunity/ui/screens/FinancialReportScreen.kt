package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finunity.data.repository.FinancialReport
import com.finunity.data.repository.ReportMonth
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.ui.components.FinTopBar
import com.finunity.ui.theme.FinColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FinancialReportScreen(
    report: FinancialReport?,
    snapshots: List<AssetSnapshot> = emptyList(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = { FinTopBar("财务报表", onBack) }, modifier = modifier) { padding ->
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val hasActivity = report.monthlyRows.any { it.income != 0.0 || it.expense != 0.0 } || report.categoryRows.isNotEmpty()
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                if (!hasActivity) {
                    item {
                        Text("暂时没有资产事件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("记录工资、大额支出、入金、出金或分红后，这里会用来解释资产变化；无需逐笔记录日常消费。", style = MaterialTheme.typography.bodyMedium, color = FinColors.TextSecondary)
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReportMetric("本月收入", formatCurrency(report.monthIncome, report.baseCurrency), FinColors.Profit, Modifier.weight(1f))
                            ReportMetric("本月支出", formatCurrency(report.monthExpense, report.baseCurrency), FinColors.Loss, Modifier.weight(1f))
                            ReportMetric("本月结余", formatCurrency(report.monthIncome - report.monthExpense, report.baseCurrency), FinColors.TextPrimary, Modifier.weight(1f))
                        }
                    }
                    item {
                        Text("年度汇总", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("收入 ${formatCurrency(report.yearIncome, report.baseCurrency)} · 支出 ${formatCurrency(report.yearExpense, report.baseCurrency)} · 结余 ${formatCurrency(report.yearIncome - report.yearExpense, report.baseCurrency)}", style = MaterialTheme.typography.bodyMedium)
                        if (report.missingCurrencies.isNotEmpty()) Text("汇率暂缺：${report.missingCurrencies.joinToString()}，相关金额未计入合计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        val rateTimes = report.rateUpdatedAt.filterValues { it != null }
                        if (rateTimes.isNotEmpty()) {
                            val latest = rateTimes.values.filterNotNull().maxOrNull()
                            Text("汇率缓存更新：${latest?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)) } ?: "暂无"}", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                        }
                    }
                    item { Text("近 12 个月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(report.monthlyRows) { row -> MonthlyReportRow(row, report.baseCurrency) }
                    if (snapshots.size >= 2) {
                        item { Text("净资产趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        item { AssetChart(snapshots = snapshots.reversed(), modifier = Modifier.fillMaxWidth().height(180.dp)) }
                    }
                    if (report.categoryRows.isNotEmpty()) {
                        item { Text("分类汇总", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        items(report.categoryRows) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.category.displayName)
                                Text(formatCurrency(row.amount, report.baseCurrency), color = if (row.income) FinColors.Profit else FinColors.Loss)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MonthlyReportRow(row: ReportMonth, currency: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.label, fontWeight = FontWeight.Medium)
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("收入 ${formatCurrency(row.income, currency)} · 支出 ${formatCurrency(row.expense, currency)}", style = MaterialTheme.typography.bodySmall)
                Text("结余 ${formatCurrency(row.net, currency)}", color = if (row.net >= 0) FinColors.Profit else FinColors.Loss, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
