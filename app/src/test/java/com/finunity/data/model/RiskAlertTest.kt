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
}
