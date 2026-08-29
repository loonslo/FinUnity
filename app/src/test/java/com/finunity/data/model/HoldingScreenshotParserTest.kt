package com.finunity.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingScreenshotParserTest {
    @Test
    fun `parses common mainland holding rows without using market value as cost`() {
        val rows = HoldingScreenshotParser.parse(
            """
                我的持仓
                证券名称 证券代码 持仓数量 参考市值
                沪深300ETF 510300 20,000 72,000.00
                贵州茅台 600519 200 285,000.00
            """.trimIndent()
        )

        assertEquals(2, rows.size)
        assertEquals("510300", rows[0].securityCode)
        assertEquals("沪深300ETF", rows[0].name)
        assertEquals(20_000.0, rows[0].quantity, 0.0)
        assertEquals(72_000.0, rows[0].marketValue, 0.0)
        assertEquals(3.6, rows[0].currentPrice, 0.0)
        assertTrue(rows[0].needsReview)
    }

    @Test
    fun `flags extra table values and detects Hong Kong currency`() {
        val rows = HoldingScreenshotParser.parse("腾讯控股 00700 100 10 45,000.00 HKD")

        assertEquals(1, rows.size)
        assertEquals("HKD", rows.single().currency)
        assertTrue(rows.single().needsReview)
    }

    @Test
    fun `normalizes exchange suffixes and keeps raw OCR code`() {
        val rows = HoldingScreenshotParser.parse(
            """
                沪深300ETF 510300.SS 20 72000
                贵州茅台 600519.SH 200 285000
                易方达 159919.SZ 100 12000
                腾讯控股 00700.HK 100 45000 HKD
                Apple AAPL 10 1800 USD
            """.trimIndent()
        )
        assertEquals(listOf("510300.SS", "600519.SS", "159919.SZ", "0700.HK", "AAPL"), rows.map { it.securityCode })
        assertEquals("600519.SH", rows[1].rawSecurityCode)
        assertEquals("贵州茅台 600519.SH 200 285000", rows[1].rawText)
        assertTrue(rows.first().needsReview.not())
    }
}
