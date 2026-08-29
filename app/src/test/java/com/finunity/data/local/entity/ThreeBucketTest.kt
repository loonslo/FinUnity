package com.finunity.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeBucketTest {
    @Test
    fun `legacy allocation merges conservative and insurance into balanced`() {
        val result = validateTargetAllocation(
            "CONSERVATIVE:0.4,AGGRESSIVE:0.3,INSURANCE:0.2,CASH:0.1"
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertEquals(0.1, result.values.getValue("DEFENSIVE"), 1e-6)
        assertEquals(0.6, result.values.getValue("BALANCED"), 1e-6)
        assertEquals(0.3, result.values.getValue("AGGRESSIVE"), 1e-6)
    }

    @Test
    fun `validation reports missing duplicate invalid and non unit targets`() {
        assertFalse(validateTargetAllocation("DEFENSIVE:0.1,AGGRESSIVE:0.3").isValid)
        assertFalse(validateTargetAllocation("DEFENSIVE:0.1,DEFENSIVE:0.2,BALANCED:0.4,AGGRESSIVE:0.3").isValid)
        assertFalse(validateTargetAllocation("DEFENSIVE:-0.1,BALANCED:0.8,AGGRESSIVE:0.3").isValid)
        assertFalse(validateTargetAllocation("DEFENSIVE:0.1,BALANCED:not-a-number,AGGRESSIVE:0.3").isValid)
        assertFalse(validateTargetAllocation("DEFENSIVE:0.1,BALANCED:0.5,AGGRESSIVE:0.3").isValid)
    }

    @Test
    fun `asset type defaults follow three bucket policy`() {
        assertEquals(RiskBucket.DEFENSIVE, AssetType.CASH.defaultRiskBucket())
        assertEquals(RiskBucket.BALANCED, AssetType.INSURANCE_POLICY.defaultRiskBucket())
        assertEquals(RiskBucket.BALANCED, AssetType.REAL_ESTATE.defaultRiskBucket())
        assertEquals(RiskBucket.BALANCED, AssetType.VEHICLE.defaultRiskBucket())
        assertEquals(RiskBucket.BALANCED, AssetType.TIME_DEPOSIT.defaultRiskBucket())
        assertEquals(RiskBucket.BALANCED, AssetType.FUND.defaultRiskBucket())
        assertEquals(RiskBucket.AGGRESSIVE, AssetType.ETF.defaultRiskBucket())
        assertEquals(RiskBucket.AGGRESSIVE, AssetType.STOCK.defaultRiskBucket())
    }

    @Test
    fun `unknown persisted bucket fails with diagnostic`() {
        val error = runCatching { requireThreeBucket("UNKNOWN") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("UNKNOWN"))
    }
}
