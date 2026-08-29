package com.finunity.data.model

import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.PriceStatus
import com.finunity.data.local.entity.evaluatePriceHealth
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceHealthTest {
    private val now = 2_000_000_000_000L

    @Test
    fun `stale cache is delayed and very old cache is expired`() {
        val delayed = Price("AAPL", 100.0, currency = "USD", updatedAt = now - 13 * 60 * 60 * 1000)
        val expired = Price("600519.SS", 100.0, currency = "CNY", updatedAt = now - 4 * 24 * 60 * 60 * 1000)

        assertEquals(PriceStatus.DELAYED, evaluatePriceHealth(listOf(delayed), now = now).status)
        assertEquals(PriceStatus.EXPIRED, evaluatePriceHealth(listOf(expired), now = now).status)
    }

    @Test
    fun `partial failure and fallback have explicit status`() {
        val price = Price("AAPL", 100.0, currency = "USD", updatedAt = now, isFallback = true)
        assertEquals(PriceStatus.CACHE_FALLBACK, evaluatePriceHealth(listOf(price), now = now).status)
        assertEquals(PriceStatus.PARTIAL_FAILURE, evaluatePriceHealth(listOf(price), partialFailure = true, now = now).status)
    }

    @Test
    fun `missing requested symbol is expired rather than a fake zero price`() {
        val health = evaluatePriceHealth(emptyList(), requestedSymbols = setOf("HKDCNY=X"), now = now)
        assertEquals(PriceStatus.EXPIRED, health.status)
        assertEquals(setOf("HKDCNY=X"), health.unavailableSymbols)
    }
}
