package com.finunity.data.model

import com.finunity.data.local.entity.RiskBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 风险体检告警逻辑测试（纯函数 evaluateRiskAlerts）。
 */
class RiskAlertTest {

    private fun point(
        sub: String,
        current: Double,
        target: Double,
        cap: Double = 0.0,
        stop: String = ""
    ) = LandingPoint(
        subCategory = sub,
        riskBucket = RiskBucket.AGGRESSIVE,
        currentValue = current,
        targetAmount = target,
        capAmount = cap,
        stopNote = stop,
        hasTarget = target > 0
    )

    @Test
    fun `进取占比超上限触发风险仓位告警`() {
        val alerts = evaluateRiskAlerts(0.75, 0.70, emptyList())
        assertEquals(1, alerts.size)
        assertEquals(RiskAlertLevel.WARNING, alerts[0].level)
        assertTrue(alerts[0].title.contains("风险仓位"))
    }

    @Test
    fun `进取占比未超上限不告警`() {
        val alerts = evaluateRiskAlerts(0.68, 0.70, emptyList())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `上限设为100以上视为不启用`() {
        // maxAggressiveRatio 超出 0..1 区间 → 不做风险仓位判定
        val alerts = evaluateRiskAlerts(0.95, 1.5, emptyList())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `落点超上限触发告警`() {
        val points = listOf(point("训练仓", current = 70000.0, target = 60000.0, cap = 60000.0))
        val alerts = evaluateRiskAlerts(0.5, 0.70, points)
        assertTrue(alerts.any { it.level == RiskAlertLevel.WARNING && it.title.contains("训练仓") })
    }

    @Test
    fun `落点达标但未超限给出停止提示`() {
        val points = listOf(point("纳指100", current = 215000.0, target = 215000.0, stop = "投满即停"))
        val alerts = evaluateRiskAlerts(0.5, 0.70, points)
        val info = alerts.single()
        assertEquals(RiskAlertLevel.INFO, info.level)
        assertTrue(info.detail.contains("投满即停"))
    }

    @Test
    fun `多重红线同时触发`() {
        val points = listOf(
            point("训练仓", current = 70000.0, target = 60000.0, cap = 60000.0),
            point("红利", current = 90000.0, target = 90000.0)
        )
        val alerts = evaluateRiskAlerts(0.80, 0.70, points)
        // 风险仓位(1) + 训练仓超限(1) + 红利达标(1)
        assertEquals(3, alerts.size)
        assertEquals(2, alerts.count { it.level == RiskAlertLevel.WARNING })
    }

    @Test
    fun `标的行业训练仓黄金与亏损红线可同时触发`() {
        val rows = listOf(
            HoldingRedlineInput("A股", 120000.0, 150000.0, com.finunity.data.local.entity.AssetType.STOCK, "训练仓", "半导体"),
            HoldingRedlineInput("芯片ETF", 100000.0, 90000.0, com.finunity.data.local.entity.AssetType.ETF, "训练仓", "半导体"),
            HoldingRedlineInput("黄金ETF", 60000.0, 60000.0, com.finunity.data.local.entity.AssetType.ETF, "黄金", "")
        )
        val alerts = evaluateHoldingRedlines(rows, strategyAssets = 1_000_000.0)

        assertTrue(alerts.any { it.title.contains("单只持仓") })
        assertTrue(alerts.any { it.title.contains("半导体") })
        assertTrue(alerts.any { it.title.contains("训练仓") })
        assertTrue(alerts.any { it.title.contains("黄金") })
        assertTrue(alerts.any { it.title.contains("亏损达 15%") })
    }

    @Test
    fun `策略盘为零时不评估标的红线`() {
        val rows = listOf(HoldingRedlineInput("A", 1.0, 1.0, com.finunity.data.local.entity.AssetType.STOCK))
        assertTrue(evaluateHoldingRedlines(rows, 0.0).isEmpty())
    }
}
