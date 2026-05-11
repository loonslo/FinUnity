package com.finunity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Settings
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.HoldingSummary
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.PositionSummary
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.RiskBucketSummary
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.data.local.entity.calculateRebalanceRecommendations
import com.finunity.data.repository.PriceRepository
import com.finunity.data.repository.RefreshResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(
    private val database: AppDatabase,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _portfolioSummary = MutableStateFlow<PortfolioSummary?>(null)
    val portfolioSummary: StateFlow<PortfolioSummary?> = _portfolioSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    init {
        loadSettings()
        observeData()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            database.settingsDao().getSettings().collect { settings ->
                if (settings != null) {
                    _settings.value = settings
                } else {
                    // 首次初始化，插入默认设置
                    val defaultSettings = Settings()
                    database.settingsDao().insert(defaultSettings)
                    _settings.value = defaultSettings
                }
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                database.accountDao().getAllAccounts(),
                database.positionDao().getAllPositions(),
                database.assetRecordDao().getAllRecords(),
                _settings
            ) { accounts, positions, assetRecords, settings ->
                PortfolioInputs(accounts, positions, assetRecords, settings)
            }.collect { inputs ->
                calculatePortfolio(
                    accounts = inputs.accounts,
                    positions = inputs.positions,
                    assetRecords = inputs.assetRecords,
                    settings = inputs.settings
                )
            }
        }
    }

    private suspend fun calculatePortfolio(
        accounts: List<Account>,
        positions: List<Position>,
        assetRecords: List<AssetRecord>,
        settings: Settings
    ) {
        _isLoading.value = true
        _error.value = null

        try {
            val baseCurrency = settings.baseCurrency
            var totalCash = 0.0
            var totalStockValue = 0.0
            var totalRecordValue = 0.0
            val accountSummaries = mutableListOf<AccountSummary>()
            val assetRecordSummaries = mutableListOf<AssetRecordSummary>()
            val holdingSummaries = mutableListOf<HoldingSummary>()
            val positionSummaries = mutableListOf<PositionSummary>()
            val riskBucketTotals = mutableMapOf<RiskBucket, Double>()
            val riskBucketCounts = mutableMapOf<RiskBucket, Int>()
            val accountNamesById = accounts.associate { it.id to it.name }

            // 处理所有账户的现金余额（按货币换算）
            // 负债账户的余额从总资产中扣除
            for (account in accounts) {
                val rate = priceRepository.getExchangeRate(account.currency, baseCurrency) ?: 1.0
                val convertedBalance = account.balance * rate
                if (account.type == AccountType.LIABILITY) {
                    totalCash -= convertedBalance  // 负债减少总资产
                } else {
                    totalCash += convertedBalance
                }
                accountSummaries.add(
                    AccountSummary(
                        account = account,
                        balanceInBaseCurrency = if (account.type == AccountType.LIABILITY) -convertedBalance else convertedBalance
                    )
                )
            }

            for (record in assetRecords) {
                val exchangeRate = priceRepository.getExchangeRate(record.currency, baseCurrency) ?: 1.0
                val currentValue = record.currentValue * exchangeRate
                val costInBase = record.cost * exchangeRate
                val profitLoss = currentValue - costInBase
                val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0

                totalRecordValue += currentValue
                when (record.assetType) {
                    AssetType.STOCK, AssetType.ETF, AssetType.FUND -> totalStockValue += currentValue
                    AssetType.CASH, AssetType.TIME_DEPOSIT -> totalCash += currentValue
                }

                riskBucketTotals[record.riskBucket] = (riskBucketTotals[record.riskBucket] ?: 0.0) + currentValue
                riskBucketCounts[record.riskBucket] = (riskBucketCounts[record.riskBucket] ?: 0) + 1

                assetRecordSummaries.add(
                    AssetRecordSummary(
                        record = record,
                        accountName = accountNamesById[record.accountId] ?: "未命名账户",
                        currentValue = currentValue,
                        costInBaseCurrency = costInBase,
                        profitLoss = profitLoss,
                        profitLossRatio = profitLossRatio
                    )
                )
            }

            for (position in positions) {
                val currency = position.currency
                val currentPrice = priceRepository.getPrice(position.symbol)?.price ?: position.averageCost
                val exchangeRate = priceRepository.getExchangeRate(currency, baseCurrency) ?: 1.0
                val currentPriceInBase = currentPrice * exchangeRate
                val currentValue = position.shares * currentPriceInBase
                val costInBase = position.totalCost * exchangeRate
                val profitLoss = currentValue - costInBase
                val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0

                holdingSummaries.add(
                    HoldingSummary(
                        position = position,
                        accountName = accountNamesById[position.accountId] ?: "未命名账户",
                        currentPrice = currentPriceInBase,
                        currentValue = currentValue,
                        profitLoss = profitLoss,
                        profitLossRatio = profitLossRatio
                    )
                )

                riskBucketTotals[RiskBucket.AGGRESSIVE] = (riskBucketTotals[RiskBucket.AGGRESSIVE] ?: 0.0) + currentValue
                riskBucketCounts[RiskBucket.AGGRESSIVE] = (riskBucketCounts[RiskBucket.AGGRESSIVE] ?: 0) + 1
            }

            // 按股票代码汇总持仓
            val positionsBySymbol = positions.groupBy { it.symbol }

            for ((symbol, symbolPositions) in positionsBySymbol) {
                val totalShares = symbolPositions.sumOf { it.shares }
                val totalCost = symbolPositions.sumOf { it.totalCost }

                // 平均成本法
                val averageCost = if (totalShares > 0) totalCost / totalShares else 0.0

                // 使用持仓明确的币种，而非从 symbol 推断
                val currency = symbolPositions.firstOrNull()?.currency ?: "USD"

                // 获取当前价格
                val price = priceRepository.getPrice(symbol)
                val currentPrice = price?.price ?: averageCost

                // 换算为基准货币
                val exchangeRate = priceRepository.getExchangeRate(currency, baseCurrency) ?: 1.0
                val currentPriceInBase = currentPrice * exchangeRate
                val currentValue = totalShares * currentPriceInBase
                val costInBase = totalCost * exchangeRate

                val profitLoss = currentValue - costInBase
                val profitLossRatio = if (costInBase > 0) profitLoss / costInBase else 0.0

                // 注意：currentValue 已在上方逐条持仓循环中加入 totalStockValue，这里不再重复累加

                positionSummaries.add(
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
                )
            }

            val totalAssets = totalCash + totalStockValue
            val stockRatio = if (totalAssets > 0) totalStockValue / totalAssets else 0.0
            if (totalCash > 0) {
                val cashFromAccounts = totalCash - assetRecords
                    .filter { it.assetType == AssetType.CASH || it.assetType == AssetType.TIME_DEPOSIT }
                    .sumOf { record ->
                        record.currentValue * (priceRepository.getExchangeRate(record.currency, baseCurrency) ?: 1.0)
                    }
                if (cashFromAccounts > 0) {
                    riskBucketTotals[RiskBucket.CASH] = (riskBucketTotals[RiskBucket.CASH] ?: 0.0) + cashFromAccounts
                    riskBucketCounts[RiskBucket.CASH] = (riskBucketCounts[RiskBucket.CASH] ?: 0) + accounts.count { it.type != AccountType.LIABILITY && it.balance > 0 }
                }
            }
            val riskSummaries = RiskBucket.entries.map { bucket ->
                val value = riskBucketTotals[bucket] ?: 0.0
                RiskBucketSummary(
                    riskBucket = bucket,
                    totalValue = value,
                    recordCount = riskBucketCounts[bucket] ?: 0,
                    percentage = if (totalAssets > 0) value / totalAssets else 0.0
                )
            }

            val targetAllocationMap = parseTargetAllocation(settings.targetAllocation)
            // 转换为百分比（0-1）再传给再平衡计算
            val totalRiskBucketValue = riskBucketTotals.values.sum()
            val currentAllocationMap = if (totalRiskBucketValue > 0) {
                riskBucketTotals.mapValues { it.value / totalRiskBucketValue }
                    .mapKeys { it.key.name }
            } else {
                riskBucketTotals.mapKeys { it.key.name }.mapValues { 0.0 }
            }
            val rebalanceRecommendations = calculateRebalanceRecommendations(
                currentAllocationMap,
                targetAllocationMap,
                settings.rebalanceThreshold
            )

            _portfolioSummary.value = PortfolioSummary(
                totalAssets = totalAssets,
                cashAssets = totalCash,
                stockAssets = totalStockValue,
                stockRatio = stockRatio,
                baseCurrency = baseCurrency,
                rebalanceThreshold = settings.rebalanceThreshold,
                needsRebalance = rebalanceRecommendations.isNotEmpty(),
                targetAllocation = settings.targetAllocation,
                allocations = currentAllocationMap,
                rebalanceRecommendations = rebalanceRecommendations,
                accounts = accountSummaries,
                riskBuckets = riskSummaries,
                assetRecords = assetRecordSummaries.sortedByDescending { it.currentValue },
                holdings = holdingSummaries.sortedByDescending { it.currentValue },
                positions = positionSummaries,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            _error.value = e.message ?: "计算失败"
        } finally {
            _isLoading.value = false
        }
    }

    fun addAccount(account: Account) {
        viewModelScope.launch {
            database.accountDao().insert(account)
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            database.accountDao().update(account)
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            database.accountDao().deleteById(accountId)
        }
    }

    fun addPosition(position: Position) {
        viewModelScope.launch {
            database.positionDao().insert(position)
            // 记录买入交易流水
            val transaction = Transaction(
                accountId = position.accountId,
                symbol = position.symbol,
                type = TransactionType.BUY,
                shares = position.shares,
                price = position.averageCost,
                amount = position.totalCost,
                currency = position.currency,
                note = "持仓买入: ${position.symbol}"
            )
            database.transactionDao().insert(transaction)
        }
    }

    fun updatePosition(position: Position) {
        viewModelScope.launch {
            database.positionDao().update(position)
        }
    }

    fun deletePosition(positionId: String) {
        viewModelScope.launch {
            database.positionDao().deleteById(positionId)
        }
    }

    fun addAssetRecord(record: AssetRecord) {
        viewModelScope.launch {
            database.assetRecordDao().insert(record)

            // 为股票/ETF/基金记录买入流水和初始价格历史
            if (record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                val transaction = Transaction(
                    accountId = record.accountId,
                    symbol = record.name,  // 使用名称作为代码
                    type = TransactionType.BUY,
                    shares = record.quantity,
                    price = record.averageCost,
                    amount = record.cost,
                    currency = record.currency,
                    note = "资产记录买入: ${record.name}",
                    recordId = record.id  // 精确追溯
                )
                database.transactionDao().insert(transaction)

                // 写入初始价格历史，记录买入成本作为基准点
                val priceHistory = PriceHistory(
                    recordId = record.id,
                    price = record.currentPrice,
                    cost = record.averageCost  // 单位成本作为基准
                )
                database.priceHistoryDao().insert(priceHistory)
            }
        }
    }

    fun updateAssetRecord(record: AssetRecord) {
        viewModelScope.launch {
            // 如果是股票/ETF/基金且价格有变化，保存价格历史
            if (record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                val existing = database.assetRecordDao().getRecordById(record.id)
                if (existing != null && existing.currentPrice != record.currentPrice) {
                    val priceHistory = PriceHistory(
                        recordId = record.id,
                        price = record.currentPrice,
                        cost = existing.averageCost  // 使用单位成本（平均成本），与 price 对应
                    )
                    database.priceHistoryDao().insert(priceHistory)
                }
            }
            database.assetRecordDao().update(record)
        }
    }

    fun deleteAssetRecord(recordId: String) {
        viewModelScope.launch {
            // 注意：删除资产记录不等于卖出！
            // 如果用户想卖出，应该调用 sellAssetRecord() 方法
            // 这里只是纯粹删除记录，不产生任何交易流水
            database.assetRecordDao().deleteById(recordId)
        }
    }

    /**
     * 卖出资产记录（产生真实的卖出流水）
     * 平均成本法：按比例减少股数和成本
     */
    fun sellAssetRecord(recordId: String, sellQuantity: Double? = null) {
        viewModelScope.launch {
            val record = database.assetRecordDao().getRecordById(recordId) ?: return@launch
            val quantityToSell = sellQuantity?.coerceIn(0.0, record.quantity) ?: record.quantity

            // 校验卖出数量
            if (quantityToSell <= 0) return@launch

            if (record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
                // 记录卖出流水
                val transaction = Transaction(
                    accountId = record.accountId,
                    symbol = record.name,
                    type = TransactionType.SELL,
                    shares = quantityToSell,
                    price = record.currentPrice,
                    amount = quantityToSell * record.currentPrice,
                    currency = record.currency,
                    note = "卖出资产: ${record.name}",
                    recordId = record.id  // 精确追溯
                )
                database.transactionDao().insert(transaction)
            }

            // 删除或部分删除记录
            val remainingQty = record.quantity - quantityToSell
            if (remainingQty <= 0) {
                database.assetRecordDao().deleteById(recordId)
            } else {
                // 按比例结转成本：remaining_cost = (remaining_qty / original_qty) * original_cost
                val costPerUnit = record.cost / record.quantity
                val remainingCost = remainingQty * costPerUnit
                val updated = record.copy(
                    quantity = remainingQty,
                    cost = remainingCost,
                    updatedAt = System.currentTimeMillis()
                )
                database.assetRecordDao().update(updated)
            }
        }
    }

    /**
     * 卖出持仓
     * 平均成本法：按比例减少股数和成本
     */
    fun sellPosition(positionId: String, sharesToSell: Double) {
        viewModelScope.launch {
            val position = database.positionDao().getPositionById(positionId)
            if (position != null && sharesToSell <= position.shares) {
                val currentPrice = priceRepository.getPrice(position.symbol)?.price ?: position.averageCost
                val sellAmount = sharesToSell * currentPrice

                // 记录卖出交易流水
                val transaction = Transaction(
                    accountId = position.accountId,
                    symbol = position.symbol,
                    type = TransactionType.SELL,
                    shares = sharesToSell,
                    price = currentPrice,
                    amount = sellAmount,
                    currency = position.currency,
                    note = "卖出持仓: ${position.symbol}"
                )
                database.transactionDao().insert(transaction)

                // 更新持仓数量和成本
                val newShares = position.shares - sharesToSell
                val costPerShare = position.totalCost / position.shares
                val newTotalCost = newShares * costPerShare
                if (newShares <= 0) {
                    database.positionDao().deleteById(positionId)
                } else {
                    val updated = position.copy(shares = newShares, totalCost = newTotalCost)
                    database.positionDao().update(updated)
                }
            }
        }
    }

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            database.settingsDao().update(newSettings)
            _settings.value = newSettings
        }
    }

    suspend fun refreshPrices() {
        _isLoading.value = true
        _error.value = null
        try {
            val settings = _settings.value
            val baseCurrency = settings.baseCurrency

            // 获取所有账户涉及的非基准货币
            val accounts = database.accountDao().getAllAccounts().first()
            val allAssetRecords = database.assetRecordDao().getAllRecords().first()
            val allPositions = database.positionDao().getAllPositions().first()

            // 收集所有涉及的币种：账户币种 + 资产记录币种 + 持仓币种
            val currencies = (accounts.map { it.currency } +
                    allAssetRecords.map { it.currency } +
                    allPositions.map { it.currency })
                .distinct()
                .filter { it != baseCurrency }
            val rates = currencies.associate { "${it}${baseCurrency}" to 1.0 }

            // 获取旧 Position 的股票代码（使用 allPositions）
            val positions = allPositions.map { it.symbol }.distinct()

            // 获取股票/ETF/基金 AssetRecord 的名称作为代码（使用已获取的 allAssetRecords）
            val tradableTypes = setOf(AssetType.STOCK.name, AssetType.ETF.name, AssetType.FUND.name)
            val assetRecordCodes = allAssetRecords
                .filter { it.assetType.name in tradableTypes }
                .map { it.name }
                .distinct()

            // 合并所有需要刷新的代码
            val allSymbols = (positions + assetRecordCodes).distinct()

            // 批量刷新价格
            val result = priceRepository.refreshAllPrices(allSymbols, rates)

            // 如果有失败，记录部分失败信息
            val failureMessages = mutableListOf<String>()
            if (result.symbolsFailed.isNotEmpty()) {
                failureMessages.add("价格失败: ${result.symbolsFailed.joinToString(", ")}")
            }
            if (result.ratesFailed.isNotEmpty()) {
                failureMessages.add("汇率失败: ${result.ratesFailed.joinToString(", ")}")
            }

            // 回写 AssetRecord 当前价格（更新所有持有该股票的记录）
            // allAssetRecords 已在前面获取，此处直接使用
            for (code in assetRecordCodes) {
                val price = priceRepository.getPrice(code)
                if (price != null && price.price > 0) {
                    val matchingRecords = allAssetRecords.filter {
                        it.name == code && it.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
                    }
                    for (existing in matchingRecords) {
                        val updated = existing.copy(
                            currentPrice = price.price,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.assetRecordDao().update(updated)
                    }
                }
            }

            // 重新计算（allPositions 和 allAssetRecords 已在前面获取）
            calculatePortfolio(accounts, allPositions, allAssetRecords, settings)

            // 如果有部分失败但整体没抛异常，仍提示用户
            if (failureMessages.isNotEmpty()) {
                _error.value = failureMessages.joinToString("；")
            }
        } catch (e: Exception) {
            _error.value = "价格刷新失败: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    class Factory(
        private val database: AppDatabase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                database,
                PriceRepository(database.priceDao())
            ) as T
        }
    }

    private data class PortfolioInputs(
        val accounts: List<Account>,
        val positions: List<Position>,
        val assetRecords: List<AssetRecord>,
        val settings: Settings
    )
}
