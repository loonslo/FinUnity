package com.finunity.data.repository

import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.RecurringRule
import com.finunity.data.local.entity.RecurringRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringRuleDateTest {
    @Test
    fun `day 31 runs on February last day`() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.FEBRUARY, 29, 13, 0)
        }.timeInMillis
        val rule = rule(day = 31)
        val due = recurringDueMonths(rule, now)

        assertEquals(listOf("2024-02"), due.map { it.key })
        val dueCalendar = Calendar.getInstance().apply { timeInMillis = due.single().timestamp }
        assertEquals(29, dueCalendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `a delayed worker catches up the last generated month once`() {
        val last = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.JANUARY, 31, 12, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.MARCH, 1, 9, 0)
        }.timeInMillis
        val due = recurringDueMonths(rule(day = 31, lastGeneratedAt = last), now)

        assertEquals(listOf("2024-02"), due.map { it.key })
        assertTrue(due.single().timestamp > last)
    }

    private fun rule(day: Int, lastGeneratedAt: Long? = null) = RecurringRule(
        accountId = "account",
        type = RecurringRuleType.INCOME,
        amount = 1_000.0,
        currency = "CNY",
        category = CashFlowCategory.SALARY,
        note = "工资",
        dayOfMonth = day,
        lastGeneratedAt = lastGeneratedAt
    )
}
