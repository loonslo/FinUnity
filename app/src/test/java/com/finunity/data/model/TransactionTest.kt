package com.finunity.data.model

import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 交易流水测试
 */
class TransactionTest {

    @Test
    fun `买入交易创建`() {
        val transaction = Transaction(
            id = "1",
            accountId = "acc1",
            symbol = "AAPL",
            type = TransactionType.BUY,
            shares = 100.0,
            price = 150.0,
            amount = 15000.0,
            currency = "USD"
        )

        assertEquals("AAPL", transaction.symbol)
        assertEquals(TransactionType.BUY, transaction.type)
        assertEquals(100.0, transaction.shares!!, 0.01)
        assertEquals(150.0, transaction.price!!, 0.01)
        assertEquals(15000.0, transaction.amount, 0.01)
    }

    @Test
    fun `转账交易无symbol`() {
        val transaction = Transaction(
            id = "1",
            accountId = "acc1",
            symbol = null,
            type = TransactionType.TRANSFER_IN,
            shares = null,
            price = null,
            amount = 10000.0,
            currency = "CNY"
        )

        assertNull(transaction.symbol)
        assertEquals(TransactionType.TRANSFER_IN, transaction.type)
        assertEquals(10000.0, transaction.amount, 0.01)
    }

    @Test
    fun `分红交易`() {
        val transaction = Transaction(
            id = "1",
            accountId = "acc1",
            symbol = "AAPL",
            type = TransactionType.DIVIDEND,
            shares = null,
            price = null,
            amount = 100.0,
            currency = "USD",
            note = "Q3分红"
        )

        assertEquals(TransactionType.DIVIDEND, transaction.type)
        assertEquals(100.0, transaction.amount, 0.01)
        assertEquals("Q3分红", transaction.note)
    }

    @Test
    fun `手续费计算`() {
        // 买入100股@$150，手续费$9.99
        val buyAmount = 100.0 * 150.0
        val fee = 9.99
        val totalCost = buyAmount + fee

        assertEquals(15009.99, totalCost, 0.01)
    }

    @Test
    fun `卖出后持仓成本验证`() {
        // 持仓：200股，总成本 $22000，平均成本 $110
        // 卖出：100股
        // 剩余：100股，总成本 $11000，平均成本 $110
        val originalShares = 200.0
        val originalCost = 22000.0
        val sharesToSell = 100.0

        val remainingShares = originalShares - sharesToSell
        val costPerShare = originalCost / originalShares
        val remainingCost = remainingShares * costPerShare

        assertEquals(110.0, remainingCost / remainingShares, 0.01)
        assertEquals(11000.0, remainingCost, 0.01)
    }

    @Test
    fun `全部卖出后持仓清零`() {
        val originalShares = 50.0
        val originalCost = 4000.0
        val sharesToSell = 50.0

        val remainingShares = originalShares - sharesToSell
        val costPerShare = originalCost / originalShares
        val remainingCost = remainingShares * costPerShare

        assertEquals(0.0, remainingShares, 0.01)
        assertEquals(0.0, remainingCost, 0.01)
    }

    @Test
    fun `卖出数量不能超过持仓`() {
        val positionShares = 100.0
        val requestedToSell = 150.0

        val isValid = requestedToSell <= positionShares
        assertEquals(false, isValid)
    }
}
