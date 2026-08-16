package com.finunity.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingTradeCalculatorTest {

    @Test
    fun `买入按数量加权累加成本`() {
        val result = calculateHoldingTrade(HoldingCostState(100.0, 1_000.0), true, 50.0, 12.0)
        val state = (result as HoldingTradeCalculation.Success).state!!

        assertEquals(150.0, state.quantity, 0.001)
        assertEquals(1_600.0, state.totalCost, 0.001)
    }

    @Test
    fun `卖出按平均成本比例结转`() {
        val result = calculateHoldingTrade(HoldingCostState(150.0, 1_600.0), false, 50.0, 15.0)
        val state = (result as HoldingTradeCalculation.Success).state!!

        assertEquals(100.0, state.quantity, 0.001)
        assertEquals(1_600.0 * 100.0 / 150.0, state.totalCost, 0.001)
    }

    @Test
    fun `卖完持仓返回空状态且超额卖出被拒绝`() {
        val soldOut = calculateHoldingTrade(HoldingCostState(10.0, 100.0), false, 10.0, 11.0)
        assertNull((soldOut as HoldingTradeCalculation.Success).state)

        val overSell = calculateHoldingTrade(HoldingCostState(10.0, 100.0), false, 11.0, 11.0)
        assertTrue(overSell is HoldingTradeCalculation.Error)
    }
}
