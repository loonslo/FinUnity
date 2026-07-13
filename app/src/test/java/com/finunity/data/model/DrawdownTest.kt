package com.finunity.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawdownTest {
    @Test
    fun `快照不足两条时不给阶梯建议`() {
        assertNull(evaluateDrawdownLadder(90.0, listOf(100.0), 10.0))
    }

    @Test
    fun `回撤按历史总资产高点进入对应档位`() {
        val advice = evaluateDrawdownLadder(
            currentAssets = 790_000.0,
            historicalTotals = listOf(800_000.0, 1_000_000.0, 900_000.0),
            availableAmmo = 80_000.0
        )!!

        assertEquals(0.21, advice.drawdownRatio, 0.0001)
        assertEquals(20, advice.levelPercent)
        assertEquals(2.0, advice.recurringMultiplier, 0.0001)
        assertEquals(60_000.0, advice.recommendedAmmoAmount, 0.01)
    }

    @Test
    fun `未到五个点时保持常规定投`() {
        val advice = evaluateDrawdownLadder(970.0, listOf(980.0, 1000.0), 100.0)!!
        assertTrue(!advice.triggered)
        assertEquals(1.0, advice.recurringMultiplier, 0.0001)
        assertEquals(0.0, advice.recommendedAmmoAmount, 0.01)
    }
}
