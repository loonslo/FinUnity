package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class PortfolioTotalsTest {
    private val prices: PriceRepository = mock {
        onBlocking { getPrice("AAPL") } doReturn Price(
            symbol = "AAPL", price = 110.0, previousClose = 100.0, currency = "CNY"
        )
        onBlocking { getExchangeRate("USD", "CNY") } doReturn null
    }

    private fun record(
        id: String,
        type: AssetType,
        bucket: RiskBucket,
        value: Double,
        locked: Boolean = false,
        currency: String = "CNY",
        code: String = ""
    ) = AssetRecord(
        id = id,
        accountId = "a",
        assetType = type,
        riskBucket = bucket,
        name = id,
        securityCode = code,
        quantity = value,
        cost = value,
        currentPrice = 1.0,
        currency = currency,
        locked = locked
    )

    @Test
    fun `gross assets and liabilities are separate from three buckets`() = runBlocking {
        val calculator = PortfolioCalculator(
            accounts = listOf(
                Account(id = "a", name = "虚构资产账户", type = AccountType.BANK, currency = "CNY", balance = 999_999.0),
                Account(id = "debt", name = "虚构负债", type = AccountType.LIABILITY, currency = "CNY", balance = 200_000.0)
            ),
            positions = emptyList(),
            assetRecords = listOf(
                record("房产", AssetType.REAL_ESTATE, RiskBucket.BALANCED, 500_000.0),
                record("现金", AssetType.CASH, RiskBucket.DEFENSIVE, 100_000.0),
                record("股票", AssetType.STOCK, RiskBucket.AGGRESSIVE, 400_000.0)
            ),
            priceRepository = prices,
            baseCurrency = "CNY"
        )

        val totals = calculator.computePortfolioTotals()
        assertEquals(1_000_000.0, totals.grossAssets, 0.01)
        assertEquals(200_000.0, totals.liabilities, 0.01)
        assertEquals(800_000.0, totals.netWorth, 0.01)
        assertEquals(1_000_000.0, totals.bucketValues.values.sum(), 0.01)
        assertEquals(500_000.0, totals.bucketValues.getValue(RiskBucket.BALANCED), 0.01)
    }

    @Test
    fun `locked assets remain in gross assets but not strategy assets`() = runBlocking {
        val calculator = PortfolioCalculator(
            accounts = emptyList(), positions = emptyList(), priceRepository = prices, baseCurrency = "CNY",
            assetRecords = listOf(
                record("锁定现金", AssetType.CASH, RiskBucket.DEFENSIVE, 100_000.0, locked = true),
                record("稳健", AssetType.TIME_DEPOSIT, RiskBucket.BALANCED, 600_000.0),
                record("股票", AssetType.STOCK, RiskBucket.AGGRESSIVE, 300_000.0)
            )
        )

        val totals = calculator.computePortfolioTotals()
        val locked = calculator.computeLockedValue()
        val strategy = totals.grossAssets - locked
        assertEquals(1_000_000.0, totals.grossAssets, 0.01)
        assertEquals(100_000.0, locked, 0.01)
        assertEquals(900_000.0, strategy, 0.01)
        val strategyBuckets = calculator.computeRiskBucketSummaries(strategy, excludeLocked = true)
        assertEquals(strategy, strategyBuckets.sumOf { it.totalValue }, 0.01)
    }

    @Test
    fun `missing exchange rate is excluded and reported`() = runBlocking {
        val calculator = PortfolioCalculator(
            accounts = emptyList(), positions = emptyList(), priceRepository = prices, baseCurrency = "CNY",
            assetRecords = listOf(record("美元资产", AssetType.STOCK, RiskBucket.AGGRESSIVE, 100.0, currency = "USD", code = "AAPL"))
        )

        assertEquals(0.0, calculator.computePortfolioTotals().grossAssets, 0.01)
        assertTrue(calculator.missingExchangeRateCurrencies.contains("USD"))
    }

    @Test
    fun `today change ratio uses tracked yesterday security value only`() = runBlocking {
        val calculator = PortfolioCalculator(
            accounts = emptyList(), positions = emptyList(), priceRepository = prices, baseCurrency = "CNY",
            assetRecords = listOf(
                record("AAPL", AssetType.STOCK, RiskBucket.AGGRESSIVE, 1_100.0, code = "AAPL").copy(
                    quantity = 10.0, currentPrice = 110.0, cost = 1_000.0
                ),
                record("房产", AssetType.REAL_ESTATE, RiskBucket.BALANCED, 1_000_000.0)
            )
        )

        val today = calculator.computeTodayChangeMetrics()
        assertEquals(100.0, today.amount, 0.01)
        assertEquals(1_000.0, today.trackedYesterdayValue, 0.01)
    }
}
