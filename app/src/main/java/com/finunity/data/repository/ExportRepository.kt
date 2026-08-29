package com.finunity.data.repository

import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.Transaction
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Human-readable exports. CSV is intentionally spreadsheet-compatible and works offline. */
class ExportRepository(private val database: AppDatabase) {
    suspend fun exportAssetsCsv(): String {
        val accounts = database.accountDao().getAllAccounts().first().associateBy { it.id }
        val records = database.assetRecordDao().getAllRecords().first()
        return buildString {
            appendLine("账户,资产类型,风险桶,名称,证券编码,数量,成本,当前价,市值,币种,落点")
            records.forEach { record ->
                appendLine(listOf(
                    accounts[record.accountId]?.name.orEmpty(), record.assetType.name, record.riskBucket.name,
                    record.name, record.securityCode, record.quantity, record.cost, record.currentPrice,
                    record.currentValue, record.currency, record.subCategory
                ).joinToString(",") { csv(it.toString()) })
            }
        }
    }

    suspend fun exportTransactionsCsv(): String {
        val accounts = database.accountDao().getAllAccounts().first().associateBy { it.id }
        val transactions = database.transactionDao().getAllTransactions().first()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            appendLine("时间,账户,类型,分类,资产编码,数量,价格,金额,币种,备注,来源")
            transactions.sortedBy { it.timestamp }.forEach { tx ->
                appendLine(listOf(
                    date.format(Date(tx.timestamp)), accounts[tx.accountId]?.name.orEmpty(), tx.type.name,
                    tx.category.displayName, tx.symbol.orEmpty(), tx.shares ?: "", tx.price ?: "",
                    tx.amount, tx.currency, tx.note.orEmpty(), tx.origin.name
                ).joinToString(",") { csv(it.toString()) })
            }
        }
    }

    suspend fun exportHtmlReport(baseCurrency: String): String {
        val transactions = database.transactionDao().getAllTransactions().first()
        val report = FinancialReportRepository(database).build(transactions, baseCurrency)
        return buildString {
            append("<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>衡仓财务报表</title>")
            append("<style>body{font-family:sans-serif;max-width:900px;margin:32px auto;color:#222}table{border-collapse:collapse;width:100%;margin:16px 0}td,th{border:1px solid #ddd;padding:8px;text-align:left}h1{color:#166b45}</style>")
            append("<h1>衡仓财务报表</h1><p>本位币：${html(baseCurrency)} · 生成时间：${html(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))}</p>")
            append("<h2>汇总</h2><p>本月收入 ${report.monthIncome.formatMoney()} · 本月支出 ${report.monthExpense.formatMoney()} · 本月结余 ${(report.monthIncome - report.monthExpense).formatMoney()}</p>")
            append("<h2>近 12 个月</h2><table><tr><th>月份</th><th>收入</th><th>支出</th><th>结余</th></tr>")
            report.monthlyRows.forEach { row -> append("<tr><td>${html(row.label)}</td><td>${row.income.formatMoney()}</td><td>${row.expense.formatMoney()}</td><td>${row.net.formatMoney()}</td></tr>") }
            append("</table><p>可在浏览器中选择“打印 → 另存为 PDF”。</p></html>")
        }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun html(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun Double.formatMoney(): String = String.format(Locale.US, "%.2f", this)
}
