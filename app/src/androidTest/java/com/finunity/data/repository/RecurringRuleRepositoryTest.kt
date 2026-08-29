package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.RecurringRule
import com.finunity.data.local.entity.RecurringRuleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class RecurringRuleRepositoryTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun generatesMonthEndRuleOnceAndIsIdempotent() = runBlocking {
        val account = Account(
            id = "recurring-account",
            name = "虚构周期账户",
            type = AccountType.BANK,
            currency = "CNY",
            balance = 0.0
        )
        val lastGenerated = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.JANUARY, 31, 12, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            clear()
            set(2024, Calendar.FEBRUARY, 29, 13, 0)
        }.timeInMillis
        val rule = RecurringRule(
            id = "recurring-rule",
            accountId = account.id,
            type = RecurringRuleType.INCOME,
            amount = 1000.0,
            currency = "CNY",
            category = CashFlowCategory.SALARY,
            note = "虚构工资",
            dayOfMonth = 31,
            lastGeneratedAt = lastGenerated
        )
        db.accountDao().insert(account)
        db.recurringRuleDao().insert(rule)
        val repository = RecurringRuleRepository(db)

        assertEquals(1, repository.generateDue(now))
        assertEquals(0, repository.generateDue(now))

        val transactions = db.transactionDao().getAllTransactions().first()
        assertEquals(1, transactions.size)
        assertEquals("RECURRING:${rule.id}:2024-02", transactions.single().sourceFingerprint)
        assertEquals(29, Calendar.getInstance().apply {
            timeInMillis = transactions.single().timestamp
        }.get(Calendar.DAY_OF_MONTH))
    }
}
