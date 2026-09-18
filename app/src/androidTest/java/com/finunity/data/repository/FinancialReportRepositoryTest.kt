package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialReportRepositoryTest {

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
    fun missingExchangeRateExcludesForeignCurrencyFromTotals() = runBlocking {
        val repository = FinancialReportRepository(
            database = db,
            priceRepositoryOverride = PriceRepository(db.priceDao())
        )
        val transactions = listOf(
            transaction("usd-income", "USD", 100.0, CashFlowCategory.SALARY),
            transaction("cny-expense", "CNY", 50.0, CashFlowCategory.FOOD)
        )

        val report = repository.build(transactions, "CNY")

        assertEquals(listOf("USD"), report.missingCurrencies)
        assertEquals(0.0, report.yearIncome, 0.001)
        assertEquals(50.0, report.yearExpense, 0.001)
        assertEquals(1, report.categoryRows.size)
        assertEquals(CashFlowCategory.FOOD, report.categoryRows.single().category)
    }

    @Test
    fun validExchangeRateIsAppliedAndTimestampIsExposed() = runBlocking {
        db.priceDao().insert(
            com.finunity.data.local.entity.Price(
                symbol = "USDCNY=X",
                price = 7.2,
                currency = "USD/CNY",
                source = "FINUNITY",
                quality = "EOD",
                valueType = "FX_RATE"
            )
        )
        val repository = FinancialReportRepository(
            database = db,
            priceRepositoryOverride = PriceRepository(db.priceDao())
        )

        val report = repository.build(
            listOf(transaction("usd-income", "USD", 100.0, CashFlowCategory.SALARY)),
            "CNY"
        )

        assertTrue(report.missingCurrencies.isEmpty())
        assertEquals(720.0, report.yearIncome, 0.001)
        assertTrue(report.rateUpdatedAt["USD"] != null)
    }

    private fun transaction(
        id: String,
        currency: String,
        amount: Double,
        category: CashFlowCategory
    ) = Transaction(
        id = id,
        accountId = "report-account",
        symbol = null,
        type = TransactionType.DEPOSIT,
        shares = null,
        price = null,
        amount = amount,
        currency = currency,
        category = category
    )

}
