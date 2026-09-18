package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Price
import com.finunity.data.remote.ApiEnvelope
import com.finunity.data.remote.ApiError
import com.finunity.data.remote.MarketSyncData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun closeDb() = db.close()

    @Test
    fun serviceLevelErrorIsPropagatedAndDoesNotCreatePrice() = runBlocking {
        val repository = PriceRepository(db.priceDao()) {
            ApiEnvelope(
                requestId = "req-service-error",
                error = ApiError("PROVIDER_ERROR", "上游不可用", retryable = true)
            )
        }

        val error = runCatching {
            repository.refreshMarketData(
                listOf(MarketAssetRequest("record-1", "AAPL", "STOCK")),
                emptySet(),
                "CNY"
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("PROVIDER_ERROR"))
        assertTrue(db.priceDao().getAllPrices().isEmpty())
    }

    @Test
    fun emptySuccessfulPayloadIsReportedAsMissingAndDoesNotCreatePrice() = runBlocking {
        val repository = PriceRepository(db.priceDao()) {
            ApiEnvelope(requestId = "req-empty", data = MarketSyncData())
        }

        val result = repository.refreshMarketData(
            listOf(MarketAssetRequest("record-1", "AAPL", "STOCK")),
            emptySet(),
            "CNY"
        )

        assertEquals(listOf("AAPL"), result.symbolsFailed)
        assertEquals("MISSING_ITEM", result.failureCodes["AAPL"])
        assertNull(db.priceDao().getPrice("AAPL"))
    }

    @Test
    fun staleCacheIsExplicitlyMarkedAsFallback() = runBlocking {
        db.priceDao().insert(
            Price(
                symbol = "AAPL",
                price = 100.0,
                previousClose = 99.0,
                currency = "USD",
                updatedAt = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
            )
        )

        val result = PriceRepository(db.priceDao()).getPrice("AAPL")

        assertEquals(100.0, result?.price ?: 0.0, 0.001)
        assertTrue(result?.isFallback == true)
    }
}
