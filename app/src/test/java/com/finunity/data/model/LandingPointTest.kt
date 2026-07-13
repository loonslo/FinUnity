package com.finunity.data.model

import com.finunity.data.local.entity.AllocationTarget
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * 落点表计算测试。
 * 所有记录均为 CNY、基准货币 CNY，getRate 走 fromCurrency==toCurrency 短路，
 * 故 PriceRepository 不会被调用，用 mock 占位即可。
 */
class LandingPointTest {

    private val priceRepository: PriceRepository = mock()

    private fun record(
        sub: String,
        value: Double,
        bucket: RiskBucket = RiskBucket.AGGRESSIVE,
        locked: Boolean = false,
        type: AssetType = AssetType.STOCK
    ) = AssetRecord(
        accountId = "acc1",
        assetType = type,
        riskBucket = bucket,
        name = sub.ifEmpty { "未命名" },
        quantity = 1.0,
        cost = value,
        currentPrice = value,   // currentValue = 1 * value
        currency = "CNY",
        subCategory = sub,
        locked = locked
    )

    private fun calculator(records: List<AssetRecord>) = PortfolioCalculator(
        accounts = emptyList(),
        positions = emptyList(),
        assetRecords = records,
        priceRepository = priceRepository,
        baseCurrency = "CNY"
    )

    @Test
    fun `同落点合并现值并对照目标算缺口`() = runBlocking {
        val records = listOf(
            record("标普500", 30000.0),
            record("标普500", 10000.0),
            record("纳指100", 17000.0)
        )
        val targets = listOf(
            AllocationTarget("标普500", RiskBucket.AGGRESSIVE, targetAmount = 396500.0),
            AllocationTarget("纳指100", RiskBucket.AGGRESSIVE, targetAmount = 215000.0)
        )
        val points = calculator(records).computeLandingPoints(targets)

        val sp = points.first { it.subCategory == "标普500" }
        assertEquals(40000.0, sp.currentValue, 0.01)
        assertEquals(396500.0, sp.targetAmount, 0.01)
        assertEquals(356500.0, sp.gap, 0.01)
        assertTrue(sp.hasTarget)
        assertFalse(sp.reachedTarget)
        assertFalse(sp.overCap)
    }

    @Test
    fun `无持仓的目标落点现值为零、缺口等于目标`() = runBlocking {
        val targets = listOf(
            AllocationTarget("红利", RiskBucket.CONSERVATIVE, targetAmount = 90000.0)
        )
        val points = calculator(emptyList()).computeLandingPoints(targets)

        val dividend = points.first { it.subCategory == "红利" }
        assertEquals(0.0, dividend.currentValue, 0.01)
        assertEquals(90000.0, dividend.gap, 0.01)
        assertTrue(dividend.hasTarget)
    }

    @Test
    fun `有持仓但无目标的落点标记为未跟踪`() = runBlocking {
        val records = listOf(record("黄金", 12000.0))
        val points = calculator(records).computeLandingPoints(emptyList())

        val gold = points.first { it.subCategory == "黄金" }
        assertFalse(gold.hasTarget)
        assertEquals(12000.0, gold.currentValue, 0.01)
        assertEquals(0.0, gold.targetAmount, 0.01)
    }

    @Test
    fun `超过上限红线时 overCap 为真`() = runBlocking {
        val records = listOf(record("训练仓", 70000.0))
        val targets = listOf(
            AllocationTarget("训练仓", RiskBucket.AGGRESSIVE, targetAmount = 60000.0, capAmount = 60000.0)
        )
        val points = calculator(records).computeLandingPoints(targets)

        val train = points.first { it.subCategory == "训练仓" }
        assertTrue(train.overCap)
        assertTrue(train.reachedTarget)
    }

    @Test
    fun `未填落点的资产不进入落点表`() = runBlocking {
        val records = listOf(record("", 50000.0))
        val points = calculator(records).computeLandingPoints(emptyList())
        assertTrue(points.isEmpty())
    }

    @Test
    fun `锁定专款计入 lockedValue`() = runBlocking {
        val records = listOf(
            record("标普500", 40000.0),
            record("生存层", 60000.0, bucket = RiskBucket.CASH, locked = true, type = AssetType.CASH)
        )
        assertEquals(60000.0, calculator(records).computeLockedValue(), 0.01)
    }

    @Test
    fun `策略盘风险配置排除锁定专款但总览仍包含`() = runBlocking {
        val records = listOf(
            record("标普500", 40000.0),
            record("稳健债基", 60000.0, bucket = RiskBucket.CONSERVATIVE, type = AssetType.FUND),
            record("生存层", 100000.0, bucket = RiskBucket.CASH, locked = true, type = AssetType.CASH)
        )
        val calc = calculator(records)

        val overview = calc.computeRiskBucketSummaries(totalAssets = 200000.0)
        val strategy = calc.computeRiskBucketSummaries(totalAssets = 100000.0, excludeLocked = true)

        assertEquals(100000.0, overview.first { it.riskBucket == RiskBucket.CASH }.totalValue, 0.01)
        assertEquals(0.0, strategy.first { it.riskBucket == RiskBucket.CASH }.totalValue, 0.01)
        assertEquals(0.4, strategy.first { it.riskBucket == RiskBucket.AGGRESSIVE }.percentage, 0.0001)
        assertEquals(0.6, strategy.first { it.riskBucket == RiskBucket.CONSERVATIVE }.percentage, 0.0001)
    }
}
