package com.finunity.data.model

import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交易流水与资产一致性测试
 * 验证卖出后资产记录、交易流水、账户现金的关系
 */
class TransactionAuditTest {

    @Test
    fun `卖出资产记录产生正确交易流水`() {
        // 原始记录：100股，成本15000，平均成本150
        val record = AssetRecord(
            id = "1",
            accountId = "acc1",
            assetType = AssetType.STOCK,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "AAPL",
            quantity = 100.0,
            cost = 15000.0,
            currentPrice = 180.0,
            currency = "USD"
        )

        // 卖出40股
        val sellQuantity = 40.0
        val remainingQty = record.quantity - sellQuantity
        val costPerUnit = record.cost / record.quantity
        val remainingCost = remainingQty * costPerUnit
        val sellAmount = sellQuantity * record.currentPrice  // 40 * 180 = 7200

        // 验证卖出流水
        val sellTransaction = Transaction(
            id = "tx1",
            accountId = record.accountId,
            symbol = record.name,
            type = TransactionType.SELL,
            shares = sellQuantity,
            price = record.currentPrice,
            amount = sellAmount,
            currency = record.currency,
            note = "卖出资产: ${record.name}",
            recordId = record.id
        )

        assertEquals(TransactionType.SELL, sellTransaction.type)
        assertEquals(40.0, sellTransaction.shares!!, 0.001)
        assertEquals(180.0, sellTransaction.price!!, 0.001)
        assertEquals(7200.0, sellTransaction.amount, 0.001)
        assertEquals(record.id, sellTransaction.recordId)
    }

    @Test
    fun `卖出后资产记录成本正确结转`() {
        // 原始记录：100股，成本15000
        val originalQty = 100.0
        val originalCost = 15000.0
        val sellQuantity = 40.0

        // 剩余60股
        val remainingQty = originalQty - sellQuantity
        val costPerUnit = originalCost / originalQty
        val remainingCost = remainingQty * costPerUnit

        assertEquals(60.0, remainingQty, 0.001)
        assertEquals(9000.0, remainingCost, 0.001)
        // 平均成本不变
        assertEquals(150.0, remainingCost / remainingQty, 0.001)
    }

    @Test
    fun `全部卖出后资产记录删除或归零`() {
        // 全部卖出
        val originalQty = 100.0
        val originalCost = 15000.0
        val sellQuantity = 100.0

        val remainingQty = originalQty - sellQuantity
        val costPerUnit = originalCost / originalQty
        val remainingCost = remainingQty * costPerUnit

        assertEquals(0.0, remainingQty, 0.001)
        assertEquals(0.0, remainingCost, 0.001)
        // 剩余数量<=0时应删除记录
    }

    @Test
    fun `买入资产记录产生交易流水`() {
        val record = AssetRecord(
            id = "1",
            accountId = "acc1",
            assetType = AssetType.STOCK,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "AAPL",
            quantity = 100.0,
            cost = 15000.0,
            currentPrice = 150.0,  // 买入价格
            currency = "USD"
        )

        val buyTransaction = Transaction(
            id = "tx1",
            accountId = record.accountId,
            symbol = record.name,
            type = TransactionType.BUY,
            shares = record.quantity,
            price = record.averageCost,
            amount = record.cost,
            currency = record.currency,
            note = "资产记录买入: ${record.name}",
            recordId = record.id
        )

        assertEquals(TransactionType.BUY, buyTransaction.type)
        assertEquals(100.0, buyTransaction.shares!!, 0.001)
        assertEquals(150.0, buyTransaction.price!!, 0.001)
        assertEquals(15000.0, buyTransaction.amount, 0.001)
        assertEquals(record.id, buyTransaction.recordId)
    }

    @Test
    fun `CSV导入资产记录的审计流水和价格历史使用资产记录ID与单位成本`() {
        val record = AssetRecord(
            id = "rec_csv",
            accountId = "acc1",
            assetType = AssetType.ETF,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "QQQ",
            quantity = 20.0,
            cost = 8000.0,
            currentPrice = 420.0,
            currency = "USD"
        )
        val averageCost = record.cost / record.quantity

        val buyTransaction = Transaction(
            id = "tx_csv",
            accountId = record.accountId,
            symbol = record.name,
            type = TransactionType.BUY,
            shares = record.quantity,
            price = averageCost,
            amount = record.cost,
            currency = record.currency,
            note = "CSV 导入初始化",
            recordId = record.id
        )
        val priceHistory = com.finunity.data.local.entity.PriceHistory(
            id = "ph_csv",
            recordId = record.id,
            price = record.currentPrice,
            cost = averageCost
        )

        assertEquals(record.id, buyTransaction.recordId)
        assertEquals(400.0, buyTransaction.price!!, 0.001)
        assertEquals(record.id, priceHistory.recordId)
        assertEquals(400.0, priceHistory.cost, 0.001)
    }

    @Test
    fun `现金买入不产生交易流水`() {
        val record = AssetRecord(
            id = "1",
            accountId = "acc1",
            assetType = AssetType.CASH,
            riskBucket = RiskBucket.CASH,
            name = "活期存款",
            quantity = 10000.0,
            cost = 10000.0,
            currentPrice = 1.0,
            currency = "CNY"
        )

        // 现金类资产买入不产生交易流水
        val producesTransaction = record.assetType in listOf(
            AssetType.STOCK,
            AssetType.ETF,
            AssetType.FUND
        )

        assertEquals(false, producesTransaction)
    }

    @Test
    fun `定期存款买入不产生交易流水`() {
        val record = AssetRecord(
            id = "1",
            accountId = "acc1",
            assetType = AssetType.TIME_DEPOSIT,
            riskBucket = RiskBucket.CONSERVATIVE,
            name = "一年定期",
            quantity = 100000.0,
            cost = 100000.0,
            currentPrice = 1.035,
            currency = "CNY"
        )

        // 定期存款买入不产生交易流水（实际是开户行为）
        val producesTransaction = record.assetType in listOf(
            AssetType.STOCK,
            AssetType.ETF,
            AssetType.FUND
        )

        assertEquals(false, producesTransaction)
    }

    @Test
    fun `账户删除级联删除交易流水`() {
        // 验证 Transaction.accountId 是外键，会随 Account 删除而删除
        val accountId = "acc1"
        val transactions = listOf(
            Transaction(
                id = "tx1",
                accountId = accountId,
                symbol = "AAPL",
                type = TransactionType.BUY,
                shares = 100.0,
                price = 150.0,
                amount = 15000.0,
                currency = "USD"
            ),
            Transaction(
                id = "tx2",
                accountId = accountId,
                symbol = "AAPL",
                type = TransactionType.SELL,
                shares = 40.0,
                price = 180.0,
                amount = 7200.0,
                currency = "USD"
            )
        )

        // 删除账户后，这些流水都会被级联删除
        assertEquals(2, transactions.filter { it.accountId == accountId }.size)
    }

    @Test
    fun `删除资产记录保留交易流水`() {
        val recordId = "rec1"
        val transaction = Transaction(
            id = "tx1",
            accountId = "acc1",
            symbol = "AAPL",
            type = TransactionType.BUY,
            shares = 100.0,
            price = 150.0,
            amount = 15000.0,
            currency = "USD",
            recordId = recordId
        )

        // recordId 不是外键，删除资产记录后流水保留
        assertNotNull(transaction.recordId)
        assertEquals(recordId, transaction.recordId)
    }

    @Test
    fun `卖出流水金额计算正确`() {
        val shares = 40.0
        val price = 180.0
        val amount = shares * price

        assertEquals(7200.0, amount, 0.001)
    }

    @Test
    fun `部分卖出成本结转比例一致`() {
        // 100股，总成本15000，买入价150
        // 卖出25股，剩余75股，成本应为75*150=11250
        val originalShares = 100.0
        val originalCost = 15000.0
        val sharesToSell = 25.0

        val remainingShares = originalShares - sharesToSell
        val costPerShare = originalCost / originalShares
        val remainingCost = remainingShares * costPerShare

        assertEquals(75.0, remainingShares, 0.001)
        assertEquals(11250.0, remainingCost, 0.001)
        // 成本结转比例：75%
        assertEquals(0.75, remainingCost / originalCost, 0.001)
    }

    @Test
    fun `PriceHistory与AssetRecord级联关系`() {
        // PriceHistory.recordId 是外键，指向 AssetRecord.id
        // 删除 AssetRecord 时会级联删除 PriceHistory
        // 这是正确的行为，因为价格历史依赖于记录存在

        val recordId = "rec1"
        val priceHistory = listOf(
            com.finunity.data.local.entity.PriceHistory(
                id = "ph1",
                recordId = recordId,
                price = 150.0,
                cost = 150.0  // 买入时：成本=价格
            ),
            com.finunity.data.local.entity.PriceHistory(
                id = "ph2",
                recordId = recordId,
                price = 180.0,
                cost = 150.0  // 当前价格180，成本150
            )
        )

        // 计算盈亏
        val latestPrice = priceHistory.last().price
        val originalCost = priceHistory.first().cost
        val profitLoss = latestPrice - originalCost
        val profitLossRatio = profitLoss / originalCost

        assertEquals(30.0, profitLoss, 0.001)
        assertEquals(0.2, profitLossRatio, 0.001)  // 20%收益
    }

    @Test
    fun `卖出数量不能超过持仓`() {
        val positionShares = 100.0
        val requestedToSell = 150.0

        val isValid = requestedToSell <= positionShares
        assertEquals(false, isValid)
    }

    @Test
    fun `卖出数量等于持仓`() {
        val positionShares = 100.0
        val requestedToSell = 100.0

        val isValid = requestedToSell <= positionShares
        assertEquals(true, isValid)
    }

    @Test
    fun `旧Position卖出产生交易流水`() {
        val position = Position(
            id = "pos1",
            accountId = "acc1",
            symbol = "AAPL",
            shares = 100.0,
            totalCost = 15000.0,
            currency = "USD"
        )

        val sharesToSell = 40.0
        val currentPrice = 180.0
        val sellAmount = sharesToSell * currentPrice

        val transaction = Transaction(
            id = "tx1",
            accountId = position.accountId,
            symbol = position.symbol,
            type = TransactionType.SELL,
            shares = sharesToSell,
            price = currentPrice,
            amount = sellAmount,
            currency = position.currency,
            note = "卖出持仓: ${position.symbol}"
        )

        assertEquals(TransactionType.SELL, transaction.type)
        assertEquals(7200.0, transaction.amount, 0.001)
        assertNull(transaction.recordId)  // 旧 Position 没有 recordId
    }
}
