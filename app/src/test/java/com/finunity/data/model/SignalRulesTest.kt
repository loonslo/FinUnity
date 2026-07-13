package com.finunity.data.model

import com.finunity.data.local.entity.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalRulesTest {
    private fun record(name: String, value: Double, currency: String = "USD", restricted: Boolean = false) = AssetRecord(
        accountId = "a", assetType = AssetType.ETF, riskBucket = RiskBucket.AGGRESSIVE,
        name = name, quantity = 1.0, cost = value, currentPrice = value, currency = currency,
        purchaseRestricted = restricted
    )

    @Test
    fun `美元证券敞口按原币市值触发六万档`() {
        val result = evaluateSignalRules(listOf(record("A", 30_000.0), record("B", 31_000.0), record("CNY", 1_000_000.0, "CNY")))
        assertEquals(61_000.0, result.usdSecuritiesExposure, 0.01)
        assertTrue(result.alerts.single().title.contains("60K"))
    }

    @Test
    fun `手动限购标记产生暂停加仓提示`() {
        val result = evaluateSignalRules(listOf(record("QDII", 1000.0, restricted = true)))
        assertTrue(result.alerts.any { it.title.contains("限购") && it.detail.contains("暂停加仓") })
    }

    @Test
    fun `三个点溢价与红利估值未达标均告警`() {
        val premium = record("跨境ETF", 1000.0, "CNY").copy(premiumRate = 0.031)
        val dividend = record("红利ETF", 1000.0, "CNY").copy(
            subCategory = "红利", peRatio = 18.0, dividendYield = 0.025
        )
        val alerts = evaluateSignalRules(listOf(premium, dividend)).alerts
        assertTrue(alerts.any { it.title.contains("溢价过高") })
        assertTrue(alerts.any { it.title.contains("估值未达门槛") && it.detail.contains("股息率") })
    }
}
