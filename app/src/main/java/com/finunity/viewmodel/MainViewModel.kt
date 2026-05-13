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
import com.finunity.data.model.PortfolioCalculator
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
            val calculator = PortfolioCalculator(accounts, positions, assetRecords, priceRepository, baseCurrency)

            // 验证一致性（汇率失败会导致 NaN）
            val consistency = calculator.verifyConsistency()
            if (!consistency.isConsistent) {
                _error.value = "部分数据汇率获取失败: ${consistency.issues.first()}"
            }

            val totalAssets = calculator.computeTotalAssets()
            val totalStockValue = calculator.computeStockValue()
            val totalCash = totalAssets - totalStockValue
            val stockRatio = if (totalAssets > 0) totalStockValue / totalAssets else 0.0

            // 使用 PortfolioCalculator 统一计算
            val accountSummaries = calculator.computeAccountSummaries()
            val assetRecordSummaries = calculator.computeAssetRecordSummaries()
            val holdingSummaries = calculator.computeHoldingSummaries()
            val positionSummaries = calculator.computePositionSummaries()
            val riskSummaries = calculator.computeRiskBucketSummaries(totalAssets)

            val targetAllocationMap = parseTargetAllocation(settings.targetAllocation)
            val totalRiskBucketValue = riskSummaries.sumOf { it.totalValue }
            val currentAllocationMap = if (totalRiskBucketValue > 0) {
                riskSummaries.associate { it.riskBucket.name to it.percentage }
            } else {
                RiskBucket.entries.associate { it.name to 0.0 }
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

            // 超额卖出返回错误
            if (sellQuantity != null && sellQuantity > record.quantity) {
                _error.value = "卖出数量 ${sellQuantity} 超过可用数量 ${record.quantity}"
                return@launch
            }

            val quantityToSell = sellQuantity ?: record.quantity

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
            if (sharesToSell <= 0) {
                _error.value = "卖出数量必须大于 0"
                return@launch
            }
            val position = database.positionDao().getPositionById(positionId)
            if (position == null) {
                _error.value = "持仓不存在"
                return@launch
            }
            if (sharesToSell > position.shares) {
                _error.value = "卖出数量 ${sharesToSell} 超过持仓数量 ${position.shares}"
                return@launch
            }

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

            // 重新读取更新后的 asset records（价格已回写，需重新获取以反映最新价格）
            val updatedAssetRecords = database.assetRecordDao().getAllRecords().first()

            // 重新计算（allPositions 和 updatedAssetRecords 已在前面获取）
            calculatePortfolio(accounts, allPositions, updatedAssetRecords, settings)

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

    /**
     * 对交易流水进行余额核对。
     * 当前只用于审计，不参与普通账户资产统计；普通账户金额由 AssetRecord 表达。
     */
    suspend fun reconcileAccountBalance(accountId: String, autoFix: Boolean = false): ReconciliationResult {
        val account = database.accountDao().getAccountById(accountId) ?: return ReconciliationResult(
            isBalanced = false,
            currentBalance = 0.0,
            computedBalance = 0.0,
            difference = 0.0,
            issues = listOf("账户不存在")
        )

        val transactions = database.transactionDao().getTransactionsForReconciliation(accountId)

        // 按时间顺序累加计算余额
        var computedBalance = 0.0
        val issues = mutableListOf<String>()

        for (tx in transactions) {
            when (tx.type) {
                TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.DIVIDEND, TransactionType.SELL -> {
                    computedBalance += tx.amount
                }
                TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT, TransactionType.BUY, TransactionType.FEE -> {
                    computedBalance -= tx.amount
                }
            }

            // 验证交易后的余额记录（如果有的话）
            tx.balanceAfter?.let { recorded ->
                if (kotlin.math.abs(recorded - computedBalance) > 0.01) {
                    issues.add("交易 ${tx.id} 记录余额 $recorded 与推导余额 $computedBalance 不符")
                    computedBalance = recorded // 以记录为准
                }
            }
        }

        val currentBalance = account.balance
        val difference = computedBalance - currentBalance

        val result = ReconciliationResult(
            isBalanced = kotlin.math.abs(difference) < 0.01,
            currentBalance = currentBalance,
            computedBalance = computedBalance,
            difference = difference,
            issues = issues
        )

        // 仅保留给审计场景使用，普通账户金额不通过 balance 参与资产统计。
        if (autoFix && !result.isBalanced) {
            val updated = account.copy(balance = computedBalance)
            database.accountDao().update(updated)
        }

        return result
    }

    /**
     * 获取账户的计算余额（从交易流水中推导）
     */
    suspend fun getComputedBalance(accountId: String): Double {
        return database.transactionDao().getComputedBalance(accountId)
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

/**
 * 余额核对结果
 */
data class ReconciliationResult(
    val isBalanced: Boolean,
    val currentBalance: Double,
    val computedBalance: Double,
    val difference: Double,
    val issues: List<String>
)
