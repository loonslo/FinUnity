package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergedHoldingTest {

    @Test
    fun `名称中的证券编码与纯编码归并为一行并重算成本收益`() {
        val merged = mergeHoldingInputs(
            listOf(
                HoldingMergeInput(
                    codeOrName = "510300",
                    displayName = "沪深300ETF",
                    quantity = 100.0,
                    costInBaseCurrency = 1_000.0,
                    currentValueInBaseCurrency = 1_200.0,
                    accountId = "broker-a",
                    accountName = "华泰证券",
                    currency = "CNY",
                    riskBucket = RiskBucket.CONSERVATIVE
                ),
                HoldingMergeInput(
                    codeOrName = "沪深300ETF · 510300",
                    displayName = "沪深300ETF · 510300",
                    quantity = 50.0,
                    costInBaseCurrency = 600.0,
                    currentValueInBaseCurrency = 750.0,
                    accountId = "fund-b",
                    accountName = "天天基金",
                    currency = "CNY",
                    riskBucket = RiskBucket.AGGRESSIVE
                )
            )
        )

        assertEquals(1, merged.size)
        val holding = merged.single()
        assertEquals("510300", holding.code)
        assertEquals(150.0, holding.totalQuantity, 0.001)
        assertEquals(1_600.0, holding.totalCost, 0.001)
        assertEquals(1_950.0, holding.currentValue, 0.001)
        assertEquals(350.0, holding.profitLoss, 0.001)
        assertEquals(350.0 / 1_600.0, holding.profitLossRatio, 0.001)
        assertEquals(1_600.0 / 150.0, holding.averageCost, 0.001)
        assertEquals(13.0, holding.currentPrice, 0.001)
        assertEquals(2, holding.accountCount)
        assertEquals(2, holding.sourceCount)
        assertEquals(RiskBucket.CONSERVATIVE, holding.riskBucket)
    }

    @Test
    fun `同一账户多条来源仍只计一个账户但保留来源笔数`() {
        val merged = mergeHoldingInputs(
            listOf(
                input("AAPL", "a1", 10.0, 1_000.0, 1_100.0),
                input("aapl", "a1", 5.0, 500.0, 550.0)
            )
        ).single()

        assertEquals("AAPL", merged.code)
        assertEquals(1, merged.accountCount)
        assertEquals(2, merged.sourceCount)
        assertTrue(merged.accountIds.contains("a1"))
    }

    @Test
    fun `无法识别编码时使用清洗后的名称作为合并键`() {
        assertEquals("现金管理", normalizeSecurityCode("  现金管理  "))
        assertEquals("AAPL", normalizeSecurityCode("aapl"))
        assertEquals("510300", normalizeSecurityCode("沪深300ETF · 510300"))
        assertEquals("700", normalizeSecurityCode("00700.HK"))
        assertEquals("700", normalizeSecurityCode("0700.HK"))
    }

    private fun input(
        code: String,
        accountId: String,
        quantity: Double,
        cost: Double,
        value: Double
    ) = HoldingMergeInput(
        codeOrName = code,
        displayName = code,
        quantity = quantity,
        costInBaseCurrency = cost,
        currentValueInBaseCurrency = value,
        accountId = accountId,
        accountName = "账户-$accountId",
        currency = "USD",
        riskBucket = RiskBucket.AGGRESSIVE
    )
}
