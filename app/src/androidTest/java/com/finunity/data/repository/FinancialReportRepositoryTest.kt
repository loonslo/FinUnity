package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.remote.ChartData
import com.finunity.data.remote.ChartError
import com.finunity.data.remote.ChartResult
import com.finunity.data.remote.StockMeta
import com.finunity.data.remote.YahooFinanceApi
import com.finunity.data.remote.YahooFinanceResponse
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
            priceRepositoryOverride = PriceRepository(db.priceDao(), FakeYahooFinanceApi())
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
        val repository = FinancialReportRepository(
            database = db,
            priceRepositoryOverride = PriceRepository(
                db.priceDao(),
                FakeYahooFinanceApi(exchangeRates = mapOf("USDCNY=X" to 7.2))
            )
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

    private class FakeYahooFinanceApi(
        private val exchangeRates: Map<String, Double> = emptyMap()
    ) : YahooFinanceApi {
        override suspend fun getStockPrice(
            symbol: String,
            interval: String,
            range: String
        ): YahooFinanceResponse = failureResponse()

        override suspend fun getExchangeRate(
            symbol: String,
            interval: String,
            range: String
        ): YahooFinanceResponse {
            val rate = exchangeRates[symbol]
            return if (rate == null) failureResponse() else YahooFinanceResponse(
                chart = ChartResult(
                    result = listOf(
                        ChartData(
                            meta = StockMeta(
                                symbol = symbol,
                                regularMarketPrice = rate,
                                regularMarketPreviousClose = null,
                                chartPreviousClose = null,
                                currency = "CNY"
                            )
                        )
                    ),
                    error = null
                )
            )
        }

        private fun failureResponse() = YahooFinanceResponse(
            chart = ChartResult(
                result = null,
                error = ChartError("NOT_FOUND", "not found in test")
            )
        )
    }
}
