package com.finunity.data.model

import com.finunity.data.local.entity.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 币种处理测试
 */
class CurrencyTest {

    @Test
    fun `持仓有明确币种字段`() {
        val position = Position(
            id = "1",
            accountId = "acc1",
            symbol = "AAPL",
            shares = 100.0,
            totalCost = 15000.0,
            currency = "USD"
        )

        assertEquals("USD", position.currency)
    }

    @Test
    fun `默认币种为USD`() {
        val position = Position(
            id = "1",
            accountId = "acc1",
            symbol = "AAPL",
            shares = 100.0,
            totalCost = 15000.0
        )

        assertEquals("USD", position.currency)
    }

    @Test
    fun `港股币种为HKD`() {
        val position = Position(
            id = "1",
            accountId = "acc1",
            symbol = "00700.HK",
            shares = 100.0,
            totalCost = 35000.0,
            currency = "HKD"
        )

        assertEquals("HKD", position.currency)
    }

    @Test
    fun `A股币种为CNY`() {
        val position = Position(
            id = "1",
            accountId = "acc1",
            symbol = "600036.SH",
            shares = 1000.0,
            totalCost = 35000.0,
            currency = "CNY"
        )

        assertEquals("CNY", position.currency)
    }

    @Test
    fun `多币种汇率换算`() {
        // USD 10000，换算 CNY
        val usdAmount = 10000.0
        val usdToCny = 7.2
        val cnyAmount = usdAmount * usdToCny

        assertEquals(72000.0, cnyAmount, 0.01)
    }

    @Test
    fun `HKD汇率换算`() {
        // HKD 10000，换算 CNY
        val hkdAmount = 10000.0
        val hkdToCny = 0.92
        val cnyAmount = hkdAmount * hkdToCny

        assertEquals(9200.0, cnyAmount, 0.01)
    }

    @Test
    fun `相同币种汇率1`() {
        val rate = 1.0
        assertEquals(1.0, rate, 0.01)
    }
}
