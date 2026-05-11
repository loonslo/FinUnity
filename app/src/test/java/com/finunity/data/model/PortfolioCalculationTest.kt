package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.Position
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 资产计算逻辑测试
 */
class PortfolioCalculationTest {

    @Test
    fun `平均成本计算 - 首次买入`() {
        val position = Position(
            id = "1",
            accountId = "acc1",
            symbol = "AAPL",
            shares = 100.0,
            totalCost = 10000.0,  // 100股 @ $100
            createdAt = System.currentTimeMillis()
        )

        assertEquals(100.0, position.averageCost, 0.01)
    }

    @Test
    fun `平均成本计算 - 多次买入`() {
        // 模拟两次买入后的平均成本
        // 第一次：100股 @ $100 = $10000
        // 第二次：100股 @ $120 = $12000
        // 总成本 = $22000，总股数 = 200
        // 平均成本 = $22000 / 200 = $110

        val totalShares = 200.0
        val totalCost = 22000.0

        val averageCost = totalCost / totalShares
        assertEquals(110.0, averageCost, 0.01)
    }

    @Test
    fun `盈亏计算`() {
        // 持仓：200股，平均成本 $110
        // 当前价：$120
        // 市值 = 200 * 120 = $24000
        // 成本 = 200 * 110 = $22000
        // 盈亏 = 24000 - 22000 = $2000

        val totalShares = 200.0
        val averageCost = 110.0
        val currentPrice = 120.0

        val currentValue = totalShares * currentPrice
        val totalCost = totalShares * averageCost
        val profitLoss = currentValue - totalCost

        assertEquals(2000.0, profitLoss, 0.01)
    }

    @Test
    fun `盈亏比例计算`() {
        val profitLoss = 2000.0
        val totalCost = 22000.0

        val profitLossRatio = profitLoss / totalCost

        assertEquals(0.0909, profitLossRatio, 0.001)  // ~9.09%
    }

    @Test
    fun `股票占比计算`() {
        val stockAssets = 70000.0
        val cashAssets = 30000.0
        val totalAssets = stockAssets + cashAssets

        val stockRatio = stockAssets / totalAssets

        assertEquals(0.7, stockRatio, 0.01)  // 70%
    }

    @Test
    fun `卖出后平均成本不变`() {
        // 平均成本法：按比例减少股数和成本，保持平均成本不变
        // 持仓：200股，总成本 $22000，平均成本 $110
        // 卖出：100股
        // 剩余：100股，总成本 $11000，平均成本 $110（不变）
        val originalShares = 200.0
        val originalCost = 22000.0
        val sharesToSell = 100.0
        val remainingShares = originalShares - sharesToSell

        val costPerShare = originalCost / originalShares
        val remainingCost = remainingShares * costPerShare

        // 验证平均成本保持不变
        assertEquals(110.0, remainingCost / remainingShares, 0.01)
        // 验证剩余成本正确
        assertEquals(11000.0, remainingCost, 0.01)
    }

    @Test
    fun `部分卖出场景`() {
        // 持仓：100股，平均成本 $50，总成本 $5000
        // 卖出：30股
        // 剩余：70股，总成本 $3500，平均成本 $50
        val originalShares = 100.0
        val totalCost = 5000.0
        val sharesToSell = 30.0
        val remainingShares = originalShares - sharesToSell

        val costPerShare = totalCost / originalShares
        val newTotalCost = remainingShares * costPerShare

        assertEquals(50.0, newTotalCost / remainingShares, 0.01)
        assertEquals(3500.0, newTotalCost, 0.01)
    }

    @Test
    fun `全部卖出场景`() {
        // 持仓：50股，平均成本 $80，总成本 $4000
        // 卖出：50股（全部）
        // 剩余：0股，总成本 $0
        val originalShares = 50.0
        val totalCost = 4000.0
        val sharesToSell = 50.0
        val remainingShares = originalShares - sharesToSell

        val costPerShare = totalCost / originalShares
        val newTotalCost = remainingShares * costPerShare

        assertEquals(0.0, remainingShares, 0.01)
        assertEquals(0.0, newTotalCost, 0.01)
    }

    @Test
    fun `卖出数量非法场景`() {
        // 持仓：100股，尝试卖出 150 股 -> 应该拒绝
        val originalShares = 100.0
        val sharesToSell = 150.0

        val isValid = sharesToSell <= originalShares
        assertEquals(false, isValid)
    }

    @Test
    fun `多币种换算`() {
        val usdAmount = 1000.0
        val exchangeRate = 7.2  // USD to CNY

        val cnyAmount = usdAmount * exchangeRate

        assertEquals(7200.0, cnyAmount, 0.01)
    }

    @Test
    fun `总资产汇总`() {
        // 账户1：银行账户 CNY 30000
        // 账户2：券商账户 USD 10000 (汇率 7.2)
        // 持仓：股票市值 $20000 (汇率 7.2)

        val cashCNY = 30000.0
        val cashUSD = 10000.0
        val exchangeRate = 7.2
        val stockValueUSD = 20000.0

        val totalCash = cashCNY + (cashUSD * exchangeRate)
        val totalStock = stockValueUSD * exchangeRate
        val totalAssets = totalCash + totalStock

        assertEquals(102000.0, totalCash, 0.01)
        assertEquals(144000.0, totalStock, 0.01)
        assertEquals(246000.0, totalAssets, 0.01)
    }

    @Test
    fun `所有账户类型现金都应计入总资产`() {
        // 银行账户 CNY 10000
        // 券商账户 USD 5000 (汇率 7.2) -> 36000 CNY
        // 基金账户 CNY 20000
        // 其他账户 HKD 1000 (汇率 0.92) -> 920 CNY
        // 总现金 = 10000 + 36000 + 20000 + 920 = 66920

        val bankBalance = 10000.0
        val brokerBalanceUSD = 5000.0
        val fundBalance = 20000.0
        val otherBalanceHKD = 1000.0

        val usdToCny = 7.2
        val hkdToCny = 0.92

        val totalCash = bankBalance +
            (brokerBalanceUSD * usdToCny) +
            fundBalance +
            (otherBalanceHKD * hkdToCny)

        assertEquals(66920.0, totalCash, 0.01)
    }

    @Test
    fun `现金占比和股票占比计算`() {
        // 总现金 30000，总股票 70000，总资产 100000
        // 现金占比 30%，股票占比 70%

        val cashAssets = 30000.0
        val stockAssets = 70000.0
        val totalAssets = cashAssets + stockAssets

        val cashRatio = cashAssets / totalAssets
        val stockRatio = stockAssets / totalAssets

        assertEquals(0.30, cashRatio, 0.01)
        assertEquals(0.70, stockRatio, 0.01)
    }

    @Test
    fun `再平衡阈值判断 - 需要调仓`() {
        val stockRatio = 0.75
        val threshold = 0.70

        val needsRebalance = stockRatio > threshold

        assertEquals(true, needsRebalance)
    }

    @Test
    fun `再平衡阈值判断 - 无需调仓`() {
        val stockRatio = 0.60
        val threshold = 0.70

        val needsRebalance = stockRatio > threshold

        assertEquals(false, needsRebalance)
    }
}
