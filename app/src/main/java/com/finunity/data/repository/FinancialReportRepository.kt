package com.finunity.data.repository

import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportMonth(val label: String, val income: Double, val expense: Double) {
    val net: Double get() = income - expense
}

data class ReportCategory(val category: CashFlowCategory, val amount: Double, val income: Boolean)

data class FinancialReport(
    val baseCurrency: String,
    val currentMonth: String,
    val monthIncome: Double,
    val monthExpense: Double,
    val yearIncome: Double,
    val yearExpense: Double,
    val monthlyRows: List<ReportMonth>,
    val categoryRows: List<ReportCategory>,
    val missingCurrencies: List<String>,
    val rateUpdatedAt: Map<String, Long?> = emptyMap()
)

/** Builds a report from local records only. No account or broker connection is involved. */
class FinancialReportRepository(
    private val database: AppDatabase,
    private val priceRepositoryOverride: PriceRepository? = null
) {
    suspend fun build(transactions: List<Transaction>, baseCurrency: String): FinancialReport {
        val currencies = transactions.map { it.currency.uppercase() }.distinct()
        val priceRepository = priceRepositoryOverride ?: PriceRepository(database.priceDao())
        val rates = currencies.associateWith { currency ->
            if (currency == baseCurrency) 1.0 else priceRepository.getExchangeRate(currency, baseCurrency)
        }
        val missing = rates.filterValues { it == null }.keys.toList()
        val rateUpdatedAt = currencies.associateWith { currency ->
            if (currency == baseCurrency) null
            else database.priceDao().getPrice("${currency}${baseCurrency}=X")?.updatedAt
        }
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val now = Calendar.getInstance()
        val currentMonth = fmt.format(now.time)
        val year = now.get(Calendar.YEAR)
        val rowsByMonth = linkedMapOf<String, Pair<Double, Double>>()
        repeat(12) {
            val cal = now.clone() as Calendar
            cal.add(Calendar.MONTH, -it)
            rowsByMonth[fmt.format(cal.time)] = 0.0 to 0.0
        }
        val categoryTotals = linkedMapOf<Pair<Boolean, CashFlowCategory>, Double>()
        var yearIncome = 0.0
        var yearExpense = 0.0

        transactions.forEach { tx ->
            val flow = classify(tx) ?: return@forEach
            // 缺汇率时明确排除该笔金额，绝不把 USD/HKD 当作 CNY 1:1 计算。
            val rate = rates[tx.currency.uppercase()] ?: return@forEach
            val amount = tx.amount * rate
            val month = fmt.format(Date(tx.timestamp))
            val old = rowsByMonth[month] ?: return@forEach
            rowsByMonth[month] = if (flow.first) old.first + amount to old.second else old.first to old.second + amount
            categoryTotals[flow] = (categoryTotals[flow] ?: 0.0) + amount
            if (Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.YEAR) == year) {
                if (flow.first) yearIncome += amount else yearExpense += amount
            }
        }
        val currentPair = rowsByMonth[currentMonth] ?: (0.0 to 0.0)
        return FinancialReport(
            baseCurrency = baseCurrency,
            currentMonth = currentMonth,
            monthIncome = currentPair.first,
            monthExpense = currentPair.second,
            yearIncome = yearIncome,
            yearExpense = yearExpense,
            monthlyRows = rowsByMonth.entries.reversed().map { ReportMonth(it.key, it.value.first, it.value.second) },
            categoryRows = categoryTotals.entries
                .map { ReportCategory(it.key.second, it.value, it.key.first) }
                .sortedByDescending { it.amount },
            missingCurrencies = missing,
            rateUpdatedAt = rateUpdatedAt
        )
    }

    private fun classify(tx: Transaction): Pair<Boolean, CashFlowCategory>? {
        return when {
            tx.type == TransactionType.TRANSFER_IN || tx.type == TransactionType.TRANSFER_OUT -> null
            tx.type == TransactionType.BUY || tx.type == TransactionType.SELL -> null
            tx.type == TransactionType.LIABILITY_PAYMENT -> false to CashFlowCategory.LOAN_REPAYMENT
            tx.category.income == true -> true to tx.category
            tx.category.income == false -> false to tx.category
            tx.type == TransactionType.DEPOSIT || tx.type == TransactionType.DIVIDEND -> true to
                if (tx.type == TransactionType.DIVIDEND) CashFlowCategory.DIVIDEND else CashFlowCategory.OTHER_INCOME
            tx.type == TransactionType.WITHDRAW || tx.type == TransactionType.FEE -> false to
                if (tx.type == TransactionType.FEE) CashFlowCategory.FEE else CashFlowCategory.OTHER_EXPENSE
            else -> null
        }
    }
}
