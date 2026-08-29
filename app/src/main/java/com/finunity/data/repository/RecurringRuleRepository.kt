package com.finunity.data.repository

import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.RecurringRule
import com.finunity.data.local.entity.RecurringRuleType
import com.finunity.data.local.entity.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecurringDueMonth(val key: String, val timestamp: Long)

/** Returns every due month since the last generated month; day 29/30/31 clamps to month end. */
fun recurringDueMonths(rule: RecurringRule, now: Long): List<RecurringDueMonth> {
    val current = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val start = java.util.Calendar.getInstance().apply {
        timeInMillis = rule.lastGeneratedAt ?: now
        if (rule.lastGeneratedAt != null) add(java.util.Calendar.MONTH, 1)
    }
    start.set(java.util.Calendar.DAY_OF_MONTH, 1)
    start.set(java.util.Calendar.HOUR_OF_DAY, 0)
    start.set(java.util.Calendar.MINUTE, 0)
    start.set(java.util.Calendar.SECOND, 0)
    start.set(java.util.Calendar.MILLISECOND, 0)
    val currentMonth = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val result = mutableListOf<RecurringDueMonth>()
    repeat(120) {
        if (start.after(currentMonth)) return@repeat
        val lastDay = start.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val dueDay = rule.dayOfMonth.coerceIn(1, 31).coerceAtMost(lastDay)
        val due = start.clone() as java.util.Calendar
        due.set(java.util.Calendar.DAY_OF_MONTH, dueDay)
        val key = SimpleDateFormat("yyyy-MM", Locale.US).format(start.time)
        if (!due.after(current)) {
            result += RecurringDueMonth(key, due.timeInMillis)
        }
        start.add(java.util.Calendar.MONTH, 1)
    }
    return result
}

class RecurringRuleRepository(private val database: AppDatabase) {
    suspend fun generateDue(now: Long = System.currentTimeMillis()): Int {
        var generated = 0
        val ledger = HoldingLedgerRepository(database)
        database.recurringRuleDao().getEnabled().forEach { rule ->
            val dueMonths = recurringDueMonths(rule, now)
            if (dueMonths.isEmpty()) return@forEach
            var lastGeneratedAt = rule.lastGeneratedAt
            dueMonths.forEach { due ->
                val fingerprint = "RECURRING:${rule.id}:${due.key}"
                val alreadyExists = database.transactionDao().getBySourceFingerprint(fingerprint) != null
                val result = ledger.recordCashMovement(
                    accountId = rule.accountId,
                    amount = rule.amount,
                    type = if (rule.type == RecurringRuleType.INCOME) TransactionType.DEPOSIT else TransactionType.WITHDRAW,
                    note = rule.note,
                    origin = com.finunity.data.local.entity.TransactionOrigin.RECURRING,
                    sourceFingerprint = fingerprint,
                    category = rule.category,
                    timestamp = due.timestamp
                )
                if (result is LedgerResult.Success) {
                    if (!alreadyExists) generated++
                    lastGeneratedAt = due.timestamp
                }
            }
            if (lastGeneratedAt != rule.lastGeneratedAt) {
                database.recurringRuleDao().update(rule.copy(lastGeneratedAt = lastGeneratedAt))
            }
        }
        return generated
    }
}
