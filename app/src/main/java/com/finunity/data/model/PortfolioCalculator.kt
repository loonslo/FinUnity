package com.finunity.data.model

import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一资产计算器
 * 解决三套独立循环导致的统计口径不一致问题
 */
class PortfolioCalculator(
    private val accounts: List<Account>,
    private val positions: List<Position>,
    private val assetRecords: List<AssetRecord>,
    private val priceRepository: PriceRepository,
    private val baseCurrency: String
) {
    private val accountNamesById: Map<String, String> = accounts.associate { it.id to it.name }

    /**
     * 获取汇率（统一入口，避免 ?: 1.0 静默降级）
     * 汇率获取失败时返回 null，由调用方决定如何处理
     */
    private suspend fun getRate(fromCurrency: String, toCurrency: String): Double? = withContext(Dispatchers.IO) {
        if (fromCurrency == toCurrency) return@withContext 1.0
        priceRepository.getExchangeRate(fromCurrency, toCurrency)
    }

    /**
     * 计算账户级现金。非负债账户只作为资产容器，现金应作为 AssetRecord 录入。
     */
    suspend fun computeAccountCash(): Double = withContext(Dispatchers.IO) {
        var total = 0.0
        for (account in accounts) {
            if (account.type == AccountType.LIABILITY) {
                val rate = getRate(account.currency, baseCurrency) ?: 1.0
                total -= account.balance * rate
            }
        }
        total
    }

    /**
     * 计算所有账户汇总信息（已按基准货币换算余额）
     */
    suspend fun computeAccountSummaries(): List<AccountSummary> = withContext(Dispatchers.IO) {
        accounts.map { account ->
            var assetRecordsValue = 0.0
            for (record in assetRecords) {
                if (record.accountId == account.id) {
                    assetRecordsValue += computeAssetRecordValue(record)
                }
            }
            var positionsValue = 0.0
            for (position in positions) {
                if (position.accountId == account.id) {
                    positionsValue += computeHoldingCurrentValue(position)
                }
            }
            val liabilityValue = if (account.type == AccountType.LIABILITY) {
                val rate = getRate(account.currency, baseCurrency) ?: 1.0
                account.balance * rate
            } else {
                0.0
            }
            AccountSummary(
                account = account,
                balanceInBaseCurrency = assetRecordsValue + positionsValue - liabilityValue
            )
        }
    }

    /**
     * 计算资产记录市值（已按基准货币换算）
     */
    suspend fun computeAssetRecordValue(record: AssetRecord): Double = withContext(Dispatchers.IO) {
        val rate = getRate(record.currency, baseCurrency) ?: 1.0
        record.currentValue * rate
    }

    /**
     * 计算资产记录成本（已按基准货币换算）
     */
    suspend fun computeAssetRecordCost(record: AssetRecord): Double = withContext(Dispatchers.IO) {
        val rate = getRate(record.currency, baseCurrency) ?: 1.0
        record.cost * rate
    }

    /**
     * 计算所有资产记录汇总
     */
    suspend fun computeAssetRecordSummaries(): List<AssetRecordSummary> = withContext(Dispatchers.IO) {
        assetRecords.map { record ->
            val currentValue = computeAssetRecordValue(record)
            val costInBase = computeAssetRecordCost(record)
            val profitLoss = currentValue - costInBase
            val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0
            AssetRecordSummary(
                record = record,
                accountName = accountNamesById[record.accountId] ?: "未命名账户",
                currentValue = currentValue,
                costInBaseCurrency = costInBase,
                profitLoss = profitLoss,
                profitLossRatio = profitLossRatio
            )
        }
    }

    /**
     * 计算单条持仓的当前价格（已按基准货币换算）
     */
    private suspend fun computeHoldingCurrentPrice(position: Position): Double = withContext(Dispatchers.IO) {
        val currentPrice = priceRepository.getPrice(position.symbol)?.price ?: position.averageCost
        val rate = getRate(position.currency, baseCurrency) ?: 1.0
        currentPrice * rate
    }

    /**
     * 计算单条持仓的当前市值（已按基准货币换算）
     */
    private suspend fun computeHoldingCurrentValue(position: Position): Double = withContext(Dispatchers.IO) {
        val currentPrice = computeHoldingCurrentPrice(position)
        position.shares * currentPrice
    }

    /**
     * 计算单条持仓的成本（已按基准货币换算）
     */
    private suspend fun computeHoldingCost(position: Position): Double = withContext(Dispatchers.IO) {
        val rate = getRate(position.currency, baseCurrency) ?: 1.0
        position.totalCost * rate
    }

    /**
     * 计算所有持仓明细汇总
     */
    suspend fun computeHoldingSummaries(): List<HoldingSummary> = withContext(Dispatchers.IO) {
        positions.map { position ->
            val currentPriceInBase = computeHoldingCurrentPrice(position)
            val currentValue = computeHoldingCurrentValue(position)
            val costInBase = computeHoldingCost(position)
            val profitLoss = currentValue - costInBase
            val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0
            HoldingSummary(
                position = position,
                accountName = accountNamesById[position.accountId] ?: "未命名账户",
                currentPrice = currentPriceInBase,
                currentValue = currentValue,
                profitLoss = profitLoss,
                profitLossRatio = profitLossRatio
            )
        }
    }

    /**
     * 计算持仓按股票代码汇总
     */
    suspend fun computePositionSummaries(): List<PositionSummary> = withContext(Dispatchers.IO) {
        val bySymbol = positions.groupBy { it.symbol }
        bySymbol.map { (symbol, symbolPositions) ->
            val totalShares = symbolPositions.sumOf { it.shares }
            val totalCost = symbolPositions.sumOf { it.totalCost }
            val averageCost = if (totalShares > 0) totalCost / totalShares else 0.0
            val currency = symbolPositions.firstOrNull()?.currency ?: "USD"
            val currentPrice = priceRepository.getPrice(symbol)?.price ?: averageCost
            val rate = getRate(currency, baseCurrency) ?: 1.0
            val currentPriceInBase = currentPrice * rate
            val currentValue = totalShares * currentPriceInBase
            val costInBase = totalCost * rate
            val profitLoss = currentValue - costInBase
            val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0
            PositionSummary(
                symbol = symbol,
                totalShares = totalShares,
                averageCost = averageCost,
                totalCost = costInBase,
                currentPrice = currentPriceInBase,
                currentValue = currentValue,
                profitLoss = profitLoss,
                profitLossRatio = profitLossRatio,
                currency = currency
            )
        }
    }

    /**
     * 计算风险维度汇总
     */
    suspend fun computeRiskBucketSummaries(totalAssets: Double): List<RiskBucketSummary> = withContext(Dispatchers.IO) {
        val riskBucketTotals = mutableMapOf<RiskBucket, Double>()
        val riskBucketCounts = mutableMapOf<RiskBucket, Int>()

        // AssetRecord 计入风险维度
        for (record in assetRecords) {
            val currentValue = computeAssetRecordValue(record)
            riskBucketTotals[record.riskBucket] = (riskBucketTotals[record.riskBucket] ?: 0.0) + currentValue
            riskBucketCounts[record.riskBucket] = (riskBucketCounts[record.riskBucket] ?: 0) + 1
        }

        // 旧 Position 全部计入 AGGRESSIVE
        for (position in positions) {
            val currentValue = computeHoldingCurrentValue(position)
            riskBucketTotals[RiskBucket.AGGRESSIVE] = (riskBucketTotals[RiskBucket.AGGRESSIVE] ?: 0.0) + currentValue
            riskBucketCounts[RiskBucket.AGGRESSIVE] = (riskBucketCounts[RiskBucket.AGGRESSIVE] ?: 0) + 1
        }

        // 非负债账户只作为容器，现金必须通过 AssetRecord.CASH 进入风险维度，避免账户余额和现金持仓重复统计。

        RiskBucket.entries.map { bucket ->
            val value = riskBucketTotals[bucket] ?: 0.0
            RiskBucketSummary(
                riskBucket = bucket,
                totalValue = value,
                recordCount = riskBucketCounts[bucket] ?: 0,
                percentage = if (totalAssets > 0) value / totalAssets else 0.0
            )
        }
    }

    /**
     * 计算总资产（基准货币）
     */
    suspend fun computeTotalAssets(): Double = withContext(Dispatchers.IO) {
        var totalStockValue = 0.0
        var totalCash = 0.0

        // 账户级金额只统计负债；非负债账户只作为资产容器，现金由 AssetRecord.CASH 表达。
        for (account in accounts) {
            if (account.type == AccountType.LIABILITY) {
                val rate = getRate(account.currency, baseCurrency) ?: 1.0
                totalCash -= account.balance * rate
            }
        }

        // 资产记录市值
        for (record in assetRecords) {
            val currentValue = computeAssetRecordValue(record)
            when (record.assetType) {
                AssetType.STOCK, AssetType.ETF, AssetType.FUND -> totalStockValue += currentValue
                AssetType.CASH, AssetType.TIME_DEPOSIT -> totalCash += currentValue
            }
        }

        // 旧持仓市值
        for (position in positions) {
            totalStockValue += computeHoldingCurrentValue(position)
        }

        totalCash + totalStockValue
    }

    /**
     * 计算股票资产总值（基准货币）
     */
    suspend fun computeStockValue(): Double = withContext(Dispatchers.IO) {
        var total = 0.0
        for (record in assetRecords) {
            if (record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                total += computeAssetRecordValue(record)
            }
        }
        for (position in positions) {
            total += computeHoldingCurrentValue(position)
        }
        total
    }

    /**
     * 计算现金资产总值（基准货币）
     */
    suspend fun computeCashValue(): Double = withContext(Dispatchers.IO) {
        var total = 0.0
        for (record in assetRecords) {
            if (record.assetType in listOf(AssetType.CASH, AssetType.TIME_DEPOSIT)) {
                total += computeAssetRecordValue(record)
            }
        }
        total
    }

    /**
     * 验证计算一致性
     * 检查汇率获取是否有 NaN（汇率失败导致）
     */
    suspend fun verifyConsistency(): ConsistencyResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()

        // 检查账户汇率
        for (account in accounts) {
            val rate = getRate(account.currency, baseCurrency)
            if (rate == null) {
                issues.add("账户 ${account.name} 汇率获取失败 (${account.currency}->$baseCurrency)")
            }
        }

        // 检查资产记录汇率
        for (record in assetRecords) {
            val rate = getRate(record.currency, baseCurrency)
            if (rate == null) {
                issues.add("资产记录 ${record.name} 汇率获取失败 (${record.currency}->$baseCurrency)")
            }
        }

        // 检查持仓汇率和价格
        for (position in positions) {
            val rate = getRate(position.currency, baseCurrency)
            if (rate == null) {
                issues.add("持仓 ${position.symbol} 汇率获取失败 (${position.currency}->$baseCurrency)")
            }
            if (priceRepository.getPrice(position.symbol) == null) {
                issues.add("持仓 ${position.symbol} 价格获取失败（无缓存且网络失败）")
            }
        }

        ConsistencyResult(
            isConsistent = issues.isEmpty(),
            issues = issues
        )
    }
}

/**
 * 一致性检查结果
 */
data class ConsistencyResult(
    val isConsistent: Boolean,
    val issues: List<String>
)
