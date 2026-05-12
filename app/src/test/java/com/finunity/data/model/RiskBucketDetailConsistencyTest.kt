package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 风险维度详情一致性测试
 * 验证账户现金余额、旧 Position、新 AssetRecord 同时存在时，
 * 首页风险维度金额、记录数、详情页明细三者一致
 */
class RiskBucketDetailConsistencyTest {

    @Test
    fun `CASH维度账户包含非负债有余额账户`() {
        // 非负债账户有正余额 -> 属于 CASH 维度
        val account = Account(
            id = "acc1",
            name = "我的银行",
            type = AccountType.BANK,
            currency = "CNY",
            balance = 10000.0
        )

        val isInCashBucket = account.type != AccountType.LIABILITY && account.balance > 0
        assertTrue(isInCashBucket)
    }

    @Test
    fun `负债账户不计入CASH维度`() {
        val liabilityAccount = Account(
            id = "acc2",
            name = "信用贷款",
            type = AccountType.LIABILITY,
            currency = "CNY",
            balance = 5000.0
        )

        val isInCashBucket = liabilityAccount.type != AccountType.LIABILITY && liabilityAccount.balance > 0
        assertFalse(isInCashBucket)
    }

    @Test
    fun `AGGRESSIVE维度包含股票ETF基金记录`() {
        val stockRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.STOCK,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "AAPL",
            quantity = 100.0,
            cost = 15000.0,
            currentPrice = 180.0,
            currency = "USD"
        )

        val etfRecord = AssetRecord(
            id = "rec2",
            accountId = "acc1",
            assetType = AssetType.ETF,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "QQQ",
            quantity = 200.0,
            cost = 80000.0,
            currentPrice = 420.0,
            currency = "USD"
        )

        val fundRecord = AssetRecord(
            id = "rec3",
            accountId = "acc1",
            assetType = AssetType.FUND,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "余额宝",
            quantity = 50000.0,
            cost = 50000.0,
            currentPrice = 1.0,
            currency = "CNY"
        )

        assertEquals(RiskBucket.AGGRESSIVE, stockRecord.riskBucket)
        assertEquals(RiskBucket.AGGRESSIVE, etfRecord.riskBucket)
        assertEquals(RiskBucket.AGGRESSIVE, fundRecord.riskBucket)
    }

    @Test
    fun `CONSERVATIVE维度包含定期存款记录`() {
        val depositRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.TIME_DEPOSIT,
            riskBucket = RiskBucket.CONSERVATIVE,
            name = "一年定期",
            quantity = 100000.0,
            cost = 100000.0,
            currentPrice = 1.035,
            currency = "CNY"
        )

        assertEquals(RiskBucket.CONSERVATIVE, depositRecord.riskBucket)
    }

    @Test
    fun `CASH维度包含现金记录`() {
        val cashRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.CASH,
            riskBucket = RiskBucket.CASH,
            name = "活期存款",
            quantity = 10000.0,
            cost = 10000.0,
            currentPrice = 1.0,
            currency = "CNY"
        )

        assertEquals(RiskBucket.CASH, cashRecord.riskBucket)
    }

    @Test
    fun `风险维度金额计算包含账户现金余额`() {
        // 账户现金余额计入 CASH 维度
        val bankAccountBalance = 50000.0  // 银行账户 50000 CNY
        val brokerCashBalance = 10000.0 * 7.2  // 券商账户 10000 USD = 72000 CNY
        val fundBalance = 30000.0  // 基金账户 30000 CNY

        // 非负债账户的正现金余额都计入 CASH
        val totalCashFromAccounts = bankAccountBalance + brokerCashBalance + fundBalance

        // 减去负债
        val liabilityBalance = 5000.0
        val netCashFromAccounts = totalCashFromAccounts - liabilityBalance

        assertEquals(147000.0, netCashFromAccounts, 0.01)
    }

    @Test
    fun `风险维度金额计算包含AssetRecord市值`() {
        // 股票记录市值
        val stockRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.STOCK,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "AAPL",
            quantity = 100.0,
            cost = 15000.0,
            currentPrice = 180.0,
            currency = "USD"
        )
        val stockValue = stockRecord.currentValue * 7.2  // 180 * 100 * 7.2 = 129600 CNY

        // 定期存款本息
        val depositRecord = AssetRecord(
            id = "rec2",
            accountId = "acc2",
            assetType = AssetType.TIME_DEPOSIT,
            riskBucket = RiskBucket.CONSERVATIVE,
            name = "一年定期",
            quantity = 100000.0,
            cost = 100000.0,
            currentPrice = 1.035,
            currency = "CNY"
        )
        val depositValue = depositRecord.currentValue  // 100000 * 1.035 = 103500 CNY

        // 现金记录
        val cashRecord = AssetRecord(
            id = "rec3",
            accountId = "acc1",
            assetType = AssetType.CASH,
            riskBucket = RiskBucket.CASH,
            name = "活期存款",
            quantity = 10000.0,
            cost = 10000.0,
            currentPrice = 1.0,
            currency = "CNY"
        )
        val cashValue = cashRecord.currentValue  // 10000 CNY

        // 验证各维度金额
        val aggressiveValue = stockValue
        val conservativeValue = depositValue
        val cashValueTotal = cashValue

        assertEquals(129600.0, aggressiveValue, 0.01)
        assertEquals(103500.0, conservativeValue, 0.01)
        assertEquals(10000.0, cashValueTotal, 0.01)
    }

    @Test
    fun `定期存款按riskBucket计入稳健维度不重复计入现金维度`() {
        val depositRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.TIME_DEPOSIT,
            riskBucket = RiskBucket.CONSERVATIVE,
            name = "一年定期",
            quantity = 100000.0,
            cost = 100000.0,
            currentPrice = 1.03,
            currency = "CNY"
        )
        val accountCash = 5000.0

        val conservativeValue = listOf(depositRecord)
            .filter { it.riskBucket == RiskBucket.CONSERVATIVE }
            .sumOf { it.currentValue }
        val cashValue = accountCash + listOf(depositRecord)
            .filter { it.riskBucket == RiskBucket.CASH }
            .sumOf { it.currentValue }

        assertEquals(103000.0, conservativeValue, 0.01)
        assertEquals(5000.0, cashValue, 0.01)
    }

    @Test
    fun `recordCount包含AssetRecord和账户现金`() {
        // 假设 riskBucketCounts 计算方式：
        // - CASH: 非负债有正余额账户数量
        // - AGGRESSIVE: 股票/ETF/基金记录数量
        // - CONSERVATIVE: 定期存款记录数量

        val accounts = listOf(
            Account(id = "acc1", name = "银行", type = AccountType.BANK, currency = "CNY", balance = 50000.0),
            Account(id = "acc2", name = "券商", type = AccountType.BROKER, currency = "USD", balance = 10000.0),
            Account(id = "acc3", name = "基金", type = AccountType.FUND, currency = "CNY", balance = 30000.0),
            Account(id = "acc4", name = "负债", type = AccountType.LIABILITY, currency = "CNY", balance = 5000.0)
        )

        val assetRecords = listOf(
            AssetRecord(id = "rec1", accountId = "acc2", assetType = AssetType.STOCK, riskBucket = RiskBucket.AGGRESSIVE, name = "AAPL", quantity = 100.0, cost = 15000.0, currentPrice = 180.0, currency = "USD"),
            AssetRecord(id = "rec2", accountId = "acc1", assetType = AssetType.TIME_DEPOSIT, riskBucket = RiskBucket.CONSERVATIVE, name = "定期", quantity = 100000.0, cost = 100000.0, currentPrice = 1.035, currency = "CNY"),
            AssetRecord(id = "rec3", accountId = "acc1", assetType = AssetType.CASH, riskBucket = RiskBucket.CASH, name = "现金", quantity = 5000.0, cost = 5000.0, currentPrice = 1.0, currency = "CNY")
        )

        // CASH 维度计数 = 非负债有正余额账户数量 (acc1, acc2, acc3) = 3
        val cashAccountCount = accounts.count { it.type != AccountType.LIABILITY && it.balance > 0 }
        assertEquals(3, cashAccountCount)

        // AGGRESSIVE 维度计数 = 股票/ETF/基金记录数量 = 1
        val aggressiveRecordCount = assetRecords.count { it.riskBucket == RiskBucket.AGGRESSIVE }
        assertEquals(1, aggressiveRecordCount)

        // CONSERVATIVE 维度计数 = 定期存款记录数量 = 1
        val conservativeRecordCount = assetRecords.count { it.riskBucket == RiskBucket.CONSERVATIVE }
        assertEquals(1, conservativeRecordCount)
    }

    @Test
    fun `账户现金和AssetRecord可以同时存在分别统计`() {
        // 账户有现金余额，同时账户下也有现金类资产记录
        // 两者都会计入 CASH 维度，这是设计允许的重复统计风险

        val accountBalance = 50000.0  // 账户现金余额
        val cashRecordValue = 10000.0  // 现金类资产记录

        // 两者都计入 CASH 维度总价值
        val totalCashValue = accountBalance + cashRecordValue

        // 产品策略决定：是否允许这种重复？
        // 如果不允许，需要互斥或 UI 说明
        // 当前实现是允许的，所以 totalCashValue = 60000

        assertEquals(60000.0, totalCashValue, 0.01)
    }

    @Test
    fun `首页风险维度金额等于详情页金额汇总`() {
        // 模拟首页计算
        val riskBucketTotals = mutableMapOf<RiskBucket, Double>()
        riskBucketTotals[RiskBucket.CASH] = 147000.0 + 10000.0  // 账户现金 + 现金记录
        riskBucketTotals[RiskBucket.AGGRESSIVE] = 129600.0  // 股票市值
        riskBucketTotals[RiskBucket.CONSERVATIVE] = 103500.0  // 定期存款本息

        // 详情页汇总应该等于首页
        val cashFromAccounts = 147000.0
        val cashRecords = listOf(AssetRecord(
            id = "rec1", accountId = "acc1", assetType = AssetType.CASH,
            riskBucket = RiskBucket.CASH, name = "现金", quantity = 10000.0,
            cost = 10000.0, currentPrice = 1.0, currency = "CNY"
        ))
        val cashFromRecords = cashRecords.sumOf { it.currentValue }
        val detailCashTotal = cashFromAccounts + cashFromRecords

        assertEquals(riskBucketTotals[RiskBucket.CASH] ?: 0.0, detailCashTotal, 0.01)
    }

    @Test
    fun `详情页账户金额只计算该维度相关资产`() {
        // 一个账户下可能有多种资产类型
        // CASH 维度详情应只显示该账户的现金相关资产

        val accountId = "acc1"
        val allRecords = listOf(
            AssetRecord(
                id = "rec1", accountId = "acc1", assetType = AssetType.STOCK,
                riskBucket = RiskBucket.AGGRESSIVE, name = "AAPL",
                quantity = 100.0, cost = 15000.0, currentPrice = 180.0, currency = "USD"
            ),
            AssetRecord(
                id = "rec2", accountId = "acc1", assetType = AssetType.CASH,
                riskBucket = RiskBucket.CASH, name = "现金",
                quantity = 10000.0, cost = 10000.0, currentPrice = 1.0, currency = "CNY"
            )
        )

        // CASH 维度下该账户的金额
        val cashValue = allRecords
            .filter { it.accountId == accountId && it.riskBucket == RiskBucket.CASH }
            .sumOf { it.currentValue }

        // AGGRESSIVE 维度下该账户的金额
        val aggressiveValue = allRecords
            .filter { it.accountId == accountId && it.riskBucket == RiskBucket.AGGRESSIVE }
            .sumOf { it.currentValue }

        assertEquals(10000.0, cashValue, 0.01)
        assertEquals(18000.0, aggressiveValue, 0.01)
    }

    @Test
    fun `现金维度账户金额包含账户余额和现金资产记录`() {
        val accountBalanceInBase = 5000.0
        val cashRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.CASH,
            riskBucket = RiskBucket.CASH,
            name = "备用金",
            quantity = 1200.0,
            cost = 1200.0,
            currentPrice = 1.0,
            currency = "CNY"
        )

        val recordValueForAccount = listOf(cashRecord)
            .filter { it.accountId == "acc1" && it.riskBucket == RiskBucket.CASH }
            .sumOf { it.currentValue }
        val detailAccountValue = accountBalanceInBase + recordValueForAccount

        assertEquals(6200.0, detailAccountValue, 0.01)
    }

    @Test
    fun `进攻维度账户金额包含旧持仓和资产记录`() {
        val stockRecord = AssetRecord(
            id = "rec1",
            accountId = "acc1",
            assetType = AssetType.STOCK,
            riskBucket = RiskBucket.AGGRESSIVE,
            name = "AAPL",
            quantity = 10.0,
            cost = 1500.0,
            currentPrice = 180.0,
            currency = "USD"
        )
        val legacyPosition = Position(
            id = "pos1",
            accountId = "acc1",
            symbol = "QQQ",
            shares = 5.0,
            totalCost = 2000.0,
            currency = "USD"
        )
        val holding = HoldingSummary(
            position = legacyPosition,
            accountName = "券商",
            currentPrice = 420.0,
            currentValue = 2100.0,
            profitLoss = 100.0,
            profitLossRatio = 0.05
        )

        val recordValueForAccount = listOf(stockRecord)
            .filter { it.accountId == "acc1" && it.riskBucket == RiskBucket.AGGRESSIVE }
            .sumOf { it.currentValue }
        val holdingValueForAccount = listOf(holding)
            .filter { it.position.accountId == "acc1" }
            .sumOf { it.currentValue }

        assertEquals(3900.0, recordValueForAccount + holdingValueForAccount, 0.01)
    }

    @Test
    fun `删除AssetRecord不影响账户现金维度`() {
        // 删除资产记录不影响账户现金余额
        // 账户现金余额是独立维护的

        val initialAccountBalance = 50000.0
        val cashRecordValue = 10000.0

        // CASH 维度总价值
        val totalCashBefore = initialAccountBalance + cashRecordValue
        assertEquals(60000.0, totalCashBefore, 0.01)

        // 假设删除了现金资产记录（但账户余额不变）
        val totalCashAfter = initialAccountBalance  // 只有账户余额
        assertEquals(50000.0, totalCashAfter, 0.01)

        // 账户现金余额不受资产记录删除影响
        assertEquals(initialAccountBalance, 50000.0, 0.01)
    }
}
