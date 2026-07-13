package com.finunity.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewChecklistTest {
    private fun summary() = PortfolioSummary(
        totalAssets = 1_000_000.0,
        cashAssets = 200_000.0,
        stockAssets = 500_000.0,
        stockRatio = 0.5,
        baseCurrency = "CNY",
        rebalanceThreshold = 0.05,
        needsRebalance = false,
        targetAllocation = "CONSERVATIVE:0.3,AGGRESSIVE:0.5,INSURANCE:0.1,CASH:0.1",
        allocations = mapOf("CONSERVATIVE" to 0.3, "AGGRESSIVE" to 0.5, "INSURANCE" to 0.1, "CASH" to 0.1),
        rebalanceRecommendations = emptyList(),
        accounts = emptyList(), riskBuckets = emptyList(), assetRecords = emptyList(),
        holdings = emptyList(), positions = emptyList(),
        landingPoints = listOf(
            LandingPoint("弹药", com.finunity.data.local.entity.RiskBucket.CASH, 50_000.0, 50_000.0, 60_000.0, "", true)
        ),
        lastUpdated = 0L
    )

    @Test
    fun `复盘清单包含月季年三种周期并自动判定弹药`() {
        val rows = buildReviewChecklist(summary())
        assertEquals(ReviewCadence.entries.toSet(), rows.map { it.cadence }.toSet())
        assertTrue(rows.first { it.id == "ammo" }.automaticPassed == true)
        assertFalse(rows.first { it.id == "overseas_hold" }.automaticPassed != null)
    }
}
