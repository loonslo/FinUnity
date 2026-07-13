package com.finunity.data.model

import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * 今日盈亏（computeTodayChange）测试。
 * 所有标的均为 CNY、基准 CNY，getRate 走同币种短路，只验证 (现价 − 昨收) × 数量 的汇总。
 */
class TodayChangeTest {

    private val priceRepository: PriceRepository = mock {
        // 有昨收：110 − 100
        onBlocking { getPrice("AAPL") } doReturn
            Price(symbol = "AAPL", price = 110.0, previousClose = 100.0, currency = "CNY")
        // 昨收未知（休市/接口未返回）：应记 0，不报错
        onBlocking { getPrice("NODATA") } doReturn
            Price(symbol = "NODATA", price = 50.0, previousClose = 0.0, currency = "CNY")
        // 旧持仓用价格缓存现价：210 − 200
        onBlocking { getPrice("0700.HK") } doReturn
            Price(symbol = "0700.HK", price = 210.0, previousClose = 200.0, currency = "CNY")
    }

    private fun record(name: String, currentPrice: Double, quantity: Double) = AssetRecord(
        accountId = "acc1",
        assetType = AssetType.STOCK,
        riskBucket = RiskBucket.AGGRESSIVE,
        name = name,
        quantity = quantity,
        cost = currentPrice * quantity,
        currentPrice = currentPrice,
        currency = "CNY"
    )

    private fun calculator(
        records: List<AssetRecord> = emptyList(),
        positions: List<Position> = emptyList()
    ) = PortfolioCalculator(
        accounts = emptyList(),
        positions = positions,
        assetRecords = records,
        priceRepository = priceRepository,
        baseCurrency = "CNY"
    )

    @Test
    fun `资产记录按现价减昨收乘数量汇总`() = runBlocking {
        val change = calculator(records = listOf(record("AAPL", currentPrice = 110.0, quantity = 10.0)))
            .computeTodayChange()
        assertEquals(100.0, change, 0.01) // (110 - 100) * 10
    }

    @Test
    fun `昨收未知的标的记零`() = runBlocking {
        val change = calculator(records = listOf(record("NODATA", currentPrice = 50.0, quantity = 100.0)))
            .computeTodayChange()
        assertEquals(0.0, change, 0.01)
    }

    @Test
    fun `旧持仓用价格缓存现价计算`() = runBlocking {
        val pos = Position(
            accountId = "acc1",
            symbol = "0700.HK",
            shares = 5.0,
            totalCost = 1000.0,
            currency = "CNY"
        )
        val change = calculator(positions = listOf(pos)).computeTodayChange()
        assertEquals(50.0, change, 0.01) // (210 - 200) * 5
    }

    @Test
    fun `多标的合计`() = runBlocking {
        val pos = Position(
            accountId = "acc1",
            symbol = "0700.HK",
            shares = 5.0,
            totalCost = 1000.0,
            currency = "CNY"
        )
        val change = calculator(
            records = listOf(
                record("AAPL", currentPrice = 110.0, quantity = 10.0),
                record("NODATA", currentPrice = 50.0, quantity = 100.0)
            ),
            positions = listOf(pos)
        ).computeTodayChange()
        assertEquals(150.0, change, 0.01) // 100 + 0 + 50
    }
}
