package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StressTestTest {
    @Test
    fun `压力浮亏按象限权重与跌幅加权`() {
        val scenario = StressScenario("x", "测试", mapOf(
            RiskBucket.AGGRESSIVE to 0.50,
            RiskBucket.BALANCED to 0.10,
            RiskBucket.DEFENSIVE to 0.0
        ))
        val weights = mapOf(
            RiskBucket.AGGRESSIVE to 0.40,
            RiskBucket.BALANCED to 0.30,
            RiskBucket.DEFENSIVE to 0.20
        )

        val result = computeStressLoss(1_000_000.0, weights, scenario, maxTolerableLoss = 250_000.0)
        assertEquals(0.23, result.lossRatio, 0.0001)
        assertEquals(230_000.0, result.lossAmount, 0.01)
        assertTrue(result.withinTolerance)
    }

    @Test
    fun `超过最大可承受亏损时判定不通过`() {
        val scenario = StressScenario("x", "测试", mapOf(RiskBucket.AGGRESSIVE to 0.5))
        val result = computeStressLoss(1_000_000.0, mapOf(RiskBucket.AGGRESSIVE to 0.5), scenario, 200_000.0)
        assertFalse(result.withinTolerance)
        assertEquals(250_000.0, result.lossAmount, 0.01)
    }
}
