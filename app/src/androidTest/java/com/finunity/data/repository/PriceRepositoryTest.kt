package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Price
import com.finunity.data.remote.ChartData
import com.finunity.data.remote.ChartError
import com.finunity.data.remote.ChartResult
import com.finunity.data.remote.StockMeta
import com.finunity.data.remote.YahooFinanceApi
import com.finunity.data.remote.YahooFinanceResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PriceRepositoryTest {

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
    fun refreshDeduplicatesSymbolsAndStoresSuccessfulPrices() = runBlocking {
        val api = FakeYahooFinanceApi()
        val repository = PriceRepository(db.priceDao(), api)

        val result = repository.refreshAllPrices(
            symbols = listOf("AAPL", "AAPL", "600519.SS"),
            rates = emptyMap()
        )

        assertEquals(2, result.symbolsRefreshed)
        assertTrue(result.symbolsFailed.isEmpty())
        assertFalse(result.isPartialFailure)
        assertEquals(setOf("AAPL", "600519.SS"), result.successfulSymbols)
        assertEquals(2, db.priceDao().getAllPrices().size)
        assertEquals(2, api.stockRequests.size)
    }

    @Test
    fun failedRefreshIsReportedAndDoesNotCreateFakePrice() = runBlocking {
        val api = FakeYahooFinanceApi(failSymbols = setOf("AAPL"))
        val repository = PriceRepository(db.priceDao(), api)

        val result = repository.refreshAllPrices(listOf("AAPL"), emptyMap())

        assertEquals(0, result.symbolsRefreshed)
        assertEquals(listOf("AAPL"), result.symbolsFailed)
        assertTrue(result.isPartialFailure)
        assertEquals(emptyList<Price>(), db.priceDao().getAllPrices())
    }

    @Test
    fun staleCacheIsReturnedAsFallbackWhenNetworkFails() = runBlocking {
        val oldPrice = Price(
            symbol = "AAPL",
            price = 100.0,
            previousClose = 99.0,
            currency = "USD",
            updatedAt = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L,
            isFallback = false
        )
        db.priceDao().insert(oldPrice)
        val repository = PriceRepository(
            db.priceDao(),
            FakeYahooFinanceApi(failSymbols = setOf("AAPL"))
        )

        val result = repository.getPrice("AAPL")

        assertEquals(100.0, result?.price ?: 0.0, 0.001)
        assertTrue(result?.isFallback == true)
    }

    @Test
    fun circuitBreakerStopsRequestsAfterFiveConsecutiveFailures() = runBlocking {
        val symbols = (1..6).map { "FAIL$it" }
        val api = FakeYahooFinanceApi(failSymbols = symbols.toSet())
        val repository = PriceRepository(db.priceDao(), api)

        val result = repository.refreshAllPrices(symbols, emptyMap())

        assertEquals(symbols, result.symbolsFailed)
        assertEquals(5, api.stockRequests.size)
        assertTrue(result.isPartialFailure)
    }

    private class FakeYahooFinanceApi(
        private val failSymbols: Set<String> = emptySet()
    ) : YahooFinanceApi {
        val stockRequests = mutableListOf<String>()

        override suspend fun getStockPrice(
            symbol: String,
            interval: String,
            range: String
        ): YahooFinanceResponse {
            stockRequests += symbol
            if (symbol in failSymbols) throw IllegalStateException("simulated failure")
            return YahooFinanceResponse(
                chart = ChartResult(
                    result = listOf(
                        ChartData(
                            meta = StockMeta(
                                symbol = symbol,
                                regularMarketPrice = if (symbol == "AAPL") 200.0 else 1500.0,
                                regularMarketPreviousClose = 199.0,
                                chartPreviousClose = null,
                                currency = if (symbol == "AAPL") "USD" else "CNY"
                            )
                        )
                    ),
                    error = null
                )
            )
        }

        override suspend fun getExchangeRate(
            symbol: String,
            interval: String,
            range: String
        ): YahooFinanceResponse = YahooFinanceResponse(
            chart = ChartResult(
                result = null,
                error = ChartError("NOT_USED", "not used in this test")
            )
        )
    }
}
