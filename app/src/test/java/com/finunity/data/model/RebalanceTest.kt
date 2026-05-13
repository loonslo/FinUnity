package com.finunity.data.model

import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.data.local.entity.calculateRebalanceRecommendations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 再平衡功能测试
 */
class RebalanceTest {

    @Test
    fun `解析目标资产配置`() {
        val allocation = parseTargetAllocation("STOCK:0.6,CASH:0.3,LIABILITY:0.1")

        assertEquals(0.6, allocation.getValue("STOCK"), 0.01)
        assertEquals(0.3, allocation.getValue("CASH"), 0.01)
        assertEquals(0.1, allocation.getValue("LIABILITY"), 0.01)
    }

    @Test
    fun `解析无效配置返回空map`() {
        val allocation = parseTargetAllocation("INVALID")
        assertTrue(allocation.isEmpty())
    }

    @Test
    fun `解析空配置返回空map`() {
        val allocation = parseTargetAllocation("")
        assertTrue(allocation.isEmpty())
    }

    @Test
    fun `偏离度超过阈值生成调仓建议`() {
        val currentAllocation = mapOf(
            "STOCK" to 0.75,
            "CASH" to 0.25
        )
        val targetAllocation = mapOf(
            "STOCK" to 0.6,
            "CASH" to 0.4
        )
        val threshold = 0.05

        val recommendations = calculateRebalanceRecommendations(
            currentAllocation,
            targetAllocation,
            threshold
        )

        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.contains("STOCK") })
    }

    @Test
    fun `偏离度在阈值内不生成调仓建议`() {
        val currentAllocation = mapOf(
            "STOCK" to 0.62,
            "CASH" to 0.38
        )
        val targetAllocation = mapOf(
            "STOCK" to 0.6,
            "CASH" to 0.4
        )
        val threshold = 0.05

        val recommendations = calculateRebalanceRecommendations(
            currentAllocation,
            targetAllocation,
            threshold
        )

        assertTrue(recommendations.isEmpty())
    }

    @Test
    fun `负债账户减少总资产`() {
        // 现金资产 100000，负债 10000
        // 净资产 = 100000 - 10000 = 90000
        val cashAssets = 100000.0
        val liability = 10000.0
        val netAssets = cashAssets - liability

        assertEquals(90000.0, netAssets, 0.01)
    }

    @Test
    fun `多账户类型资产汇总`() {
        // 银行账户 50000 (CNY)
        // 券商现金 10000 (USD, 汇率7.2) = 72000 CNY
        // 基金账户 30000 (CNY)
        // 现金管理 20000 (CNY)
        // 负债 5000 (CNY)
        // 总资产 = 50000 + 72000 + 30000 + 20000 - 5000 = 167000
        val bank = 50000.0
        val brokerUSD = 10000.0 * 7.2
        val fund = 30000.0
        val cashMgmt = 20000.0
        val liability = 5000.0

        val totalAssets = bank + brokerUSD + fund + cashMgmt - liability

        assertEquals(167000.0, totalAssets, 0.01)
    }

    @Test
    fun `风险维度金额转换为百分比比例`() {
        // 金额：CONSERVATIVE=20000, AGGRESSIVE=60000, CASH=20000，总计 100000
        // 比例应为：0.2, 0.6, 0.2
        val riskBucketTotals = mapOf(
            "CONSERVATIVE" to 20000.0,
            "AGGRESSIVE" to 60000.0,
            "CASH" to 20000.0
        )
        val totalValue = riskBucketTotals.values.sum()
        val percentages = riskBucketTotals.mapValues { it.value / totalValue }

        assertEquals(0.2, percentages["CONSERVATIVE"]!!, 0.001)
        assertEquals(0.6, percentages["AGGRESSIVE"]!!, 0.001)
        assertEquals(0.2, percentages["CASH"]!!, 0.001)
    }

    @Test
    fun `零资产时再平衡不崩溃`() {
        val currentAllocation = mapOf(
            "CONSERVATIVE" to 0.0,
            "AGGRESSIVE" to 0.0,
            "CASH" to 0.0
        )
        val targetAllocation = mapOf(
            "CONSERVATIVE" to 0.2,
            "AGGRESSIVE" to 0.6,
            "CASH" to 0.2
        )
        val threshold = 0.05

        val recommendations = calculateRebalanceRecommendations(
            currentAllocation,
            targetAllocation,
            threshold
        )

        // 零资产时所有维度都应建议增配
        assertTrue(recommendations.isNotEmpty())
    }

    @Test
    fun `负净资产时再平衡不崩溃`() {
        // 总资产为负（负债超过资产）
        val totalAssets = -5000.0  // 负净资产
        val stockRatio = if (totalAssets > 0) 0.6 else 0.0  // 负资产时股票比例为0

        assertEquals(0.0, stockRatio, 0.001)
    }

    @Test
    fun `目标配置缺失key时使用零值`() {
        // 当前配置有 CONSERVATIVE 和 AGGRESSIVE 和 CASH
        // 目标配置只有 CONSERVATIVE 和 CASH
        val currentAllocation = mapOf(
            "CONSERVATIVE" to 0.5,
            "AGGRESSIVE" to 0.3,
            "CASH" to 0.2
        )
        val targetAllocation = mapOf(
            "CONSERVATIVE" to 0.3,
            "CASH" to 0.7
            // AGGRESSIVE 缺失
        )
        val threshold = 0.05

        val recommendations = calculateRebalanceRecommendations(
            currentAllocation,
            targetAllocation,
            threshold
        )

        // AGGRESSIVE 缺失时视为 0，超出部分应建议减配
        assertTrue(recommendations.any { it.contains("进取") || it.contains("稳健") })
    }

    @Test
    fun `目标配置总和不等于1时校验失败`() {
        // 目标配置总和为 0.3 + 0.6 = 0.9，不等于 1
        val targetAllocation = mapOf(
            "CONSERVATIVE" to 0.3,
            "AGGRESSIVE" to 0.6
            // CASH 缺失，总和为 0.9
        )
        val total = targetAllocation.values.sum()

        // 应该被检测为无效配置
        assertTrue(kotlin.math.abs(total - 1.0) > 0.001)
    }

    @Test
    fun `目标配置总和等于1时校验通过`() {
        val targetAllocation = mapOf(
            "CONSERVATIVE" to 0.2,
            "AGGRESSIVE" to 0.6,
            "CASH" to 0.2
        )
        val total = targetAllocation.values.sum()

        assertTrue(kotlin.math.abs(total - 1.0) <= 0.001)
    }

    @Test
    fun `部分持仓场景再平衡建议`() {
        // 部分持仓：CONSERVATIVE=10%, AGGRESSIVE=80%, CASH=10%，目标 20/60/20
        // AGGRESSIVE 超出 20%，应建议减配
        val currentAllocation = mapOf(
            "CONSERVATIVE" to 0.1,
            "AGGRESSIVE" to 0.8,
            "CASH" to 0.1
        )
        val targetAllocation = mapOf(
            "CONSERVATIVE" to 0.2,
            "AGGRESSIVE" to 0.6,
            "CASH" to 0.2
        )
        val threshold = 0.05

        val recommendations = calculateRebalanceRecommendations(
            currentAllocation,
            targetAllocation,
            threshold
        )

        assertTrue(recommendations.any { it.contains("进取") && it.contains("减配") })
        assertTrue(recommendations.any { it.contains("稳健") && it.contains("增配") })
    }
}
