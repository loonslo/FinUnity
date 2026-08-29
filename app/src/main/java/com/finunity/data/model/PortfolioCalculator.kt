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
    private val rateCache = mutableMapOf<Pair<String, String>, Double?>()
    private val missingExchangeRates = linkedSetOf<String>()

    val missingExchangeRateCurrencies: Set<String>
        get() = missingExchangeRates.toSet()

    /**
     * 获取汇率（统一入口，避免 ?: 1.0 静默降级）
     * 汇率获取失败时返回 null，由调用方决定如何处理
     */
    private suspend fun getRate(fromCurrency: String, toCurrency: String): Double? = withContext(Dispatchers.IO) {
        if (fromCurrency == toCurrency) return@withContext 1.0
        val key = fromCurrency.uppercase() to toCurrency.uppercase()
        if (rateCache.containsKey(key)) return@withContext rateCache[key]
        val rate = priceRepository.getExchangeRate(fromCurrency, toCurrency)
            ?.takeIf { it.isFinite() && it > 0.0 }
        rateCache[key] = rate
        if (rate == null) missingExchangeRates += fromCurrency.uppercase()
        rate
    }

    /** Missing FX must not be treated as a 1:1 conversion. The caller also receives the consistency warning. */
    private suspend fun getRateOrZero(fromCurrency: String, toCurrency: String): Double =
        getRate(fromCurrency, toCurrency) ?: 0.0

    /**
     * 计算账户级现金。非负债账户只作为资产容器，现金应作为 AssetRecord 录入。
     */
    suspend fun computeAccountCash(): Double = withContext(Dispatchers.IO) {
        var total = 0.0
        for (account in accounts) {
            if (account.type == AccountType.LIABILITY) {
                val rate = getRateOrZero(account.currency, baseCurrency)
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
                val rate = getRateOrZero(account.currency, baseCurrency)
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
        val rate = getRateOrZero(record.currency, baseCurrency)
        record.currentValue * rate
    }

    /**
     * 计算资产记录成本（已按基准货币换算）
     */
    suspend fun computeAssetRecordCost(record: AssetRecord): Double = withContext(Dispatchers.IO) {
        val rate = getRateOrZero(record.currency, baseCurrency)
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
        val rate = getRateOrZero(position.currency, baseCurrency)
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
        val rate = getRateOrZero(position.currency, baseCurrency)
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
     * 统一合并新旧两套持仓模型。
     *
     * 这里先把每条来源记录换算到本位币，再交给纯函数
     * [mergeHoldingInputs] 按证券编码聚合，确保 UI、总览和后续调仓都能复用同一口径。
     */
    suspend fun computeMergedHoldingSummaries(): List<MergedHoldingSummary> = withContext(Dispatchers.IO) {
        val inputs = mutableListOf<HoldingMergeInput>()

        for (record in assetRecords) {
            val rate = getRateOrZero(record.currency, baseCurrency)
            inputs += HoldingMergeInput(
                codeOrName = record.securityCode.ifBlank { record.name },
                displayName = record.name,
                quantity = record.quantity,
                costInBaseCurrency = record.cost * rate,
                currentValueInBaseCurrency = record.currentValue * rate,
                accountId = record.accountId,
                accountName = accountNamesById[record.accountId] ?: "未命名账户",
                currency = record.currency,
                riskBucket = record.riskBucket
            )
        }

        for (position in positions) {
            val rate = getRateOrZero(position.currency, baseCurrency)
            val currentPrice = priceRepository.getPrice(position.symbol)?.price ?: position.averageCost
            inputs += HoldingMergeInput(
                codeOrName = position.symbol,
                displayName = position.symbol,
                quantity = position.shares,
                costInBaseCurrency = position.totalCost * rate,
                currentValueInBaseCurrency = position.shares * currentPrice * rate,
                accountId = position.accountId,
                accountName = accountNamesById[position.accountId] ?: "未命名账户",
                currency = position.currency,
                riskBucket = RiskBucket.AGGRESSIVE
            )
        }

        mergeHoldingInputs(inputs)
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
            val rate = getRateOrZero(currency, baseCurrency)
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
    suspend fun computeRiskBucketSummaries(
        totalAssets: Double,
        excludeLocked: Boolean = false
    ): List<RiskBucketSummary> = withContext(Dispatchers.IO) {
        val riskBucketTotals = mutableMapOf<RiskBucket, Double>()
        val riskBucketCounts = mutableMapOf<RiskBucket, Int>()

        // AssetRecord 计入风险维度
        for (record in assetRecords) {
            if (excludeLocked && record.locked) continue
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
     * 计算锁定专款合计（基准货币）。
     * locked=true 的资产记录（生存层、嫁妆等）属于专款隔离，不计入可投策略盘、不参与再平衡。
     */
    suspend fun computeLockedValue(): Double = withContext(Dispatchers.IO) {
        var total = 0.0
        for (record in assetRecords) {
            if (record.locked) total += computeAssetRecordValue(record)
        }
        total
    }

    /**
     * 计算落点表（子桶级目标 vs 现有）。
     *
     * 按 [AssetRecord.subCategory] 把持仓归入落点并汇总现值；与传入的目标列表对齐：
     * - 有目标的落点：填入目标/上限/停止条件；
     * - 有持仓但无目标的落点：作为"未跟踪"行展示，提示用户补设目标；
     * - 有目标但暂无持仓的落点：现值 0，缺口=目标，提示从 0 建仓。
     * 旧 Position 无 subCategory，统一并入"未归类"落点。
     */
    suspend fun computeLandingPoints(
        targets: List<com.finunity.data.local.entity.AllocationTarget>
    ): List<LandingPoint> = withContext(Dispatchers.IO) {
        // 汇总每个落点的现值
        val currentBySubCategory = mutableMapOf<String, Double>()
        for (record in assetRecords) {
            // orEmpty 兜底旧备份经 Gson 反序列化后可能为 null 的情况
            val key = record.subCategory.orEmpty().trim()
            if (key.isEmpty()) continue
            currentBySubCategory[key] = (currentBySubCategory[key] ?: 0.0) + computeAssetRecordValue(record)
        }

        val targetBySubCategory = targets.associateBy { it.subCategory.trim() }
        val allKeys = (currentBySubCategory.keys + targetBySubCategory.keys).toMutableSet()

        val bucketOrder = listOf(
            RiskBucket.AGGRESSIVE, RiskBucket.BALANCED, RiskBucket.BALANCED, RiskBucket.DEFENSIVE
        )

        allKeys.map { key ->
            val target = targetBySubCategory[key]
            val current = currentBySubCategory[key] ?: 0.0
            LandingPoint(
                subCategory = key,
                riskBucket = target?.riskBucket ?: RiskBucket.AGGRESSIVE,
                currentValue = current,
                targetAmount = target?.targetAmount ?: 0.0,
                capAmount = target?.capAmount ?: 0.0,
                stopNote = target?.stopNote ?: "",
                hasTarget = target != null
            )
        }.sortedWith(
             // 已设目标的优先，其次按三桶固定顺序，再按缺口从大到小
            compareByDescending<LandingPoint> { it.hasTarget }
                .thenBy { bucketOrder.indexOf(it.riskBucket).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenByDescending { it.gap }
        )
    }

    /**
     * 计算总资产（基准货币）
     */
    suspend fun computeTotalAssets(): Double = computePortfolioTotals().grossAssets

    /** 正资产、负债和三桶金额的唯一计算入口，首页、快照和报表应复用它。 */
    suspend fun computePortfolioTotals(): PortfolioTotals = withContext(Dispatchers.IO) {
        val bucketTotals = RiskBucket.entries.associateWith { 0.0 }.toMutableMap()
        for (record in assetRecords) {
            val value = computeAssetRecordValue(record).coerceAtLeast(0.0)
            bucketTotals[record.riskBucket] = (bucketTotals[record.riskBucket] ?: 0.0) + value
        }
        for (position in positions) {
            val value = computeHoldingCurrentValue(position).coerceAtLeast(0.0)
            bucketTotals[RiskBucket.AGGRESSIVE] =
                (bucketTotals[RiskBucket.AGGRESSIVE] ?: 0.0) + value
        }
        val grossAssets = bucketTotals.values.sum()
        var liabilities = 0.0
        for (account in accounts.filter { it.type == AccountType.LIABILITY }) {
            liabilities += (account.balance * getRateOrZero(account.currency, baseCurrency))
                .coerceAtLeast(0.0)
        }
        PortfolioTotals(
            grossAssets = grossAssets,
            liabilities = liabilities,
            netWorth = grossAssets - liabilities,
            bucketValues = bucketTotals.toMap()
        )
    }

    /** 成本统一按本位币换算，汇率缺失时该项不伪造为 1:1。 */
    suspend fun computeTotalCost(): Double = withContext(Dispatchers.IO) {
        positions.sumOf { it.totalCost * getRateOrZero(it.currency, baseCurrency) } +
            assetRecords.sumOf { it.cost * getRateOrZero(it.currency, baseCurrency) }
    }

    /**
     * 计算股票资产总值（基准货币）
     */
    suspend fun computeStockValue(): Double = withContext(Dispatchers.IO) {
        computePortfolioTotals().bucketValues[RiskBucket.AGGRESSIVE] ?: 0.0
    }

    /**
     * 计算现金资产总值（基准货币）
     */
    suspend fun computeCashValue(): Double = withContext(Dispatchers.IO) {
        computePortfolioTotals().bucketValues[RiskBucket.DEFENSIVE] ?: 0.0
    }

    /**
     * 计算今日盈亏（基准货币）。
     * 今日盈亏 = Σ (现价 − 昨收) × 数量 × 汇率，仅统计有昨收价的可交易标的。
     * 昨收未知（previousClose<=0，如休市/停牌/接口未返回）的标的记 0，不参与、不报错。
     */
    suspend fun computeTodayChange(): Double = computeTodayChangeMetrics().amount

    suspend fun computeTodayChangeMetrics(): TodayChangeMetrics = withContext(Dispatchers.IO) {
        var total = 0.0
        var trackedYesterdayValue = 0.0
        val tradableTypes = listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)

        // 新资产记录：现价取 record.currentPrice（与市值口径一致），昨收取价格缓存
        for (record in assetRecords) {
            if (record.assetType !in tradableTypes) continue
            val prevClose = priceRepository.getPrice(record.securityCode.ifBlank { record.name })?.previousClose ?: 0.0
            if (prevClose <= 0.0) continue
            val rate = getRateOrZero(record.currency, baseCurrency)
            total += (record.currentPrice - prevClose) * record.quantity * rate
            trackedYesterdayValue += record.quantity * prevClose * rate
        }

        // 旧持仓：现价取价格缓存（与市值口径一致）
        for (position in positions) {
            val cached = priceRepository.getPrice(position.symbol) ?: continue
            if (cached.previousClose <= 0.0) continue
            val rate = getRateOrZero(position.currency, baseCurrency)
            total += (cached.price - cached.previousClose) * position.shares * rate
            trackedYesterdayValue += position.shares * cached.previousClose * rate
        }

        TodayChangeMetrics(total, trackedYesterdayValue)
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

data class PortfolioTotals(
    val grossAssets: Double,
    val liabilities: Double,
    val netWorth: Double,
    val bucketValues: Map<RiskBucket, Double>
)

data class TodayChangeMetrics(
    val amount: Double,
    val trackedYesterdayValue: Double
)
