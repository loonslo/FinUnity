package com.finunity.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AllocationTarget
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Settings
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.model.HoldingSummary
import com.finunity.data.model.HoldingRedlineInput
import com.finunity.data.model.PortfolioSummary
import com.finunity.data.model.PositionSummary
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.PortfolioCalculator
import com.finunity.data.model.RiskBucketSummary
import com.finunity.data.model.evaluateHoldingRedlines
import com.finunity.data.model.evaluateDrawdownLadder
import com.finunity.data.model.evaluateSignalRules
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.PriceHealth
import com.finunity.data.local.entity.PriceStatus
import com.finunity.data.local.entity.evaluatePriceHealth
import com.finunity.data.local.entity.parseTargetAllocationOrDefault
import com.finunity.data.local.entity.calculateRebalanceRecommendations
import com.finunity.data.repository.PriceRepository
import com.finunity.data.repository.RefreshResult
import com.finunity.data.repository.HoldingLedgerRepository
import com.finunity.data.repository.HoldingSnapshotCommand
import com.finunity.data.repository.SnapshotBatchResult
import com.finunity.data.repository.HoldingTradeCommand
import com.finunity.data.repository.LedgerResult
import com.finunity.data.model.normalizeSecurityCode
import com.finunity.data.local.entity.PriceHistory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val database: AppDatabase,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val holdingLedger = HoldingLedgerRepository(database, priceRepository)

    private val _portfolioSummary = MutableStateFlow<PortfolioSummary?>(null)
    val portfolioSummary: StateFlow<PortfolioSummary?> = _portfolioSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _priceHealth = MutableStateFlow(PriceHealth(PriceStatus.NORMAL))
    val priceHealth: StateFlow<PriceHealth> = _priceHealth.asStateFlow()
    private val _lastPriceRefreshPartial = MutableStateFlow(false)

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
            combine(database.priceDao().observeAllPrices(), _lastPriceRefreshPartial) { prices, partial ->
                evaluatePriceHealth(prices, partialFailure = partial)
            }.collect { _priceHealth.value = it }
        }
        viewModelScope.launch {
            combine(
                database.accountDao().getAllAccounts(),
                database.assetRecordDao().getAllRecords(),
                database.allocationTargetDao().getAllTargets(),
                _settings
            ) { accounts, assetRecords, allocationTargets, settings ->
                PortfolioInputs(accounts, assetRecords, allocationTargets, settings)
            }.combine(database.assetSnapshotDao().getAllSnapshots()) { inputs, snapshots ->
                inputs.copy(snapshots = snapshots)
            }.collect { inputs ->
                calculatePortfolio(
                    accounts = inputs.accounts,
                    assetRecords = inputs.assetRecords,
                    allocationTargets = inputs.allocationTargets,
                    settings = inputs.settings,
                    snapshots = inputs.snapshots
                )
            }
        }
    }

    private suspend fun calculatePortfolio(
        accounts: List<Account>,
        assetRecords: List<AssetRecord>,
        allocationTargets: List<AllocationTarget>,
        settings: Settings,
        snapshots: List<AssetSnapshot>
    ) {
        _isLoading.value = true
        _error.value = null

        try {
            val baseCurrency = settings.baseCurrency
            // v17 起 Position 仅作为遗留表保留，所有活跃持仓统一由 AssetRecord 承载。
            val calculator = PortfolioCalculator(accounts, emptyList(), assetRecords, priceRepository, baseCurrency)

            // 验证一致性（汇率失败会导致 NaN）
            val consistency = calculator.verifyConsistency()
            if (!consistency.isConsistent) {
                _error.value = "部分数据汇率获取失败: ${consistency.issues.first()}"
            }

            val totals = calculator.computePortfolioTotals()
            val grossAssets = totals.grossAssets
            val liabilities = totals.liabilities
            val netWorth = totals.netWorth
            val defensiveAssets = totals.bucketValues[RiskBucket.DEFENSIVE] ?: 0.0
            val balancedAssets = totals.bucketValues[RiskBucket.BALANCED] ?: 0.0
            val aggressiveAssets = totals.bucketValues[RiskBucket.AGGRESSIVE] ?: 0.0
            val todayChangeMetrics = calculator.computeTodayChangeMetrics()
            val todayChange = todayChangeMetrics.amount
            val stockRatio = if (grossAssets > 0) aggressiveAssets / grossAssets else 0.0

            // 使用 PortfolioCalculator 统一计算
            val accountSummaries = calculator.computeAccountSummaries()
            val assetRecordSummaries = calculator.computeAssetRecordSummaries()
            val holdingSummaries = calculator.computeHoldingSummaries()
            val positionSummaries = emptyList<PositionSummary>()
            val mergedHoldingSummaries = calculator.computeMergedHoldingSummaries()
            // 总览明细仍展示锁定专款；偏离与再平衡只使用可投策略盘。
            val riskSummaries = calculator.computeRiskBucketSummaries(grossAssets)
            val lockedAssets = calculator.computeLockedValue()
            val strategyAssets = (grossAssets - lockedAssets).coerceAtLeast(0.0)
            val strategyRiskSummaries = calculator.computeRiskBucketSummaries(
                totalAssets = strategyAssets,
                excludeLocked = true
            )

            val targetAllocationMap = parseTargetAllocationOrDefault(settings.targetAllocation)
            val totalStrategyBucketValue = strategyRiskSummaries.sumOf { it.totalValue }
            val currentAllocationMap = if (strategyAssets > 0 && totalStrategyBucketValue > 0) {
                strategyRiskSummaries.associate { it.riskBucket.name to it.percentage }
            } else {
                RiskBucket.entries.associate { it.name to 0.0 }
            }
            val rebalanceRecommendations = calculateRebalanceRecommendations(
                currentAllocationMap,
                targetAllocationMap,
                settings.rebalanceThreshold
            )

            val landingPoints = calculator.computeLandingPoints(allocationTargets)
            val holdingRedlineAlerts = evaluateHoldingRedlines(
                holdings = assetRecordSummaries.filterNot { it.record.locked }.map {
                    HoldingRedlineInput(
                        name = it.record.name,
                        currentValue = it.currentValue,
                        cost = it.costInBaseCurrency,
                        assetType = it.record.assetType,
                        subCategory = it.record.subCategory,
                        industryTag = it.record.industryTag
                    )
                },
                strategyAssets = strategyAssets
            )
            val availableAmmo = assetRecordSummaries
                .filter { !it.record.locked && it.record.subCategory.trim() == "弹药" }
                .sumOf { it.currentValue }
            val drawdownAdvice = evaluateDrawdownLadder(
                currentAssets = grossAssets,
                historicalTotals = snapshots.map { it.grossAssets },
                availableAmmo = availableAmmo
            )
            val signalAlerts = evaluateSignalRules(assetRecords).alerts

            _portfolioSummary.value = PortfolioSummary(
                totalAssets = grossAssets,
                cashAssets = defensiveAssets,
                stockAssets = aggressiveAssets,
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
                mergedHoldings = mergedHoldingSummaries,
                landingPoints = landingPoints,
                holdingRedlineAlerts = holdingRedlineAlerts,
                signalAlerts = signalAlerts,
                drawdownAdvice = drawdownAdvice,
                lockedAssets = lockedAssets,
                maxAggressiveRatio = settings.maxAggressiveRatio,
                todayChange = todayChange,
                lastUpdated = System.currentTimeMillis(),
                grossAssets = grossAssets,
                liabilities = liabilities,
                netWorth = netWorth,
                defensiveAssets = defensiveAssets,
                balancedAssets = balancedAssets,
                aggressiveAssets = aggressiveAssets,
                strategyAssetsValue = strategyAssets,
                trackedYesterdayValue = todayChangeMetrics.trackedYesterdayValue,
                missingExchangeRateCurrencies = calculator.missingExchangeRateCurrencies
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
            // 标记新手引导已完成
            if (!_settings.value.onboarded) {
                val updated = _settings.value.copy(onboarded = true)
                database.settingsDao().update(updated)
                _settings.value = updated
            }
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

    private fun reportLedgerResult(result: LedgerResult) {
        if (result is LedgerResult.Error) _error.value = result.message
    }

    /** Awaitable write APIs used by screens that must not report success before the transaction commits. */
    suspend fun recordCashInAndWait(accountId: String, amount: Double, note: String? = null): LedgerResult {
        return holdingLedger.recordCashMovement(
            accountId,
            amount,
            TransactionType.DEPOSIT,
            note?.ifBlank { null } ?: "入金"
        )
    }

    suspend fun recordCashOutAndWait(accountId: String, amount: Double, note: String? = null): LedgerResult {
        return holdingLedger.recordCashMovement(
            accountId,
            amount,
            TransactionType.WITHDRAW,
            note?.ifBlank { null } ?: "出金"
        )
    }

    suspend fun recordIncomeAndWait(
        accountId: String,
        amount: Double,
        category: com.finunity.data.local.entity.CashFlowCategory,
        note: String? = null
    ): LedgerResult {
        val type = if (category == com.finunity.data.local.entity.CashFlowCategory.DIVIDEND) {
            TransactionType.DIVIDEND
        } else TransactionType.DEPOSIT
        return holdingLedger.recordCashMovement(
            accountId,
            amount,
            type,
            note?.ifBlank { category.displayName } ?: category.displayName,
            category = category
        )
    }

    suspend fun recordExpenseAndWait(
        accountId: String,
        amount: Double,
        category: com.finunity.data.local.entity.CashFlowCategory,
        note: String? = null
    ): LedgerResult = holdingLedger.recordCashMovement(
        accountId,
        amount,
        TransactionType.WITHDRAW,
        note?.ifBlank { category.displayName } ?: category.displayName,
        category = category
    )

    suspend fun recordLiabilityPaymentAndWait(accountId: String, amount: Double, note: String? = null): LedgerResult =
        holdingLedger.recordLiabilityPayment(accountId, amount, note)

    suspend fun transferCashAndWait(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        note: String? = null
    ): LedgerResult {
        return holdingLedger.transferCash(fromAccountId, toAccountId, amount, note?.ifBlank { null })
    }

    fun addAssetRecord(record: AssetRecord) {
        viewModelScope.launch {
            reportLedgerResult(
                holdingLedger.upsertSnapshot(
                    HoldingSnapshotCommand(
                        record = record,
                        sourceType = record.sourceType,
                        sourceAccountId = record.sourceAccountId,
                        sourceRecordId = record.sourceRecordId,
                        importBatchId = record.importBatchId,
                        sourceFingerprint = record.sourceFingerprint,
                        syncedAt = record.syncedAt
                    )
                )
            )
        }
    }

    /**
     * 买入加仓：在现有持仓上追加数量与成本，重算均价并记录买入流水。
     * 录入模型为"声明持仓"，此处不自动扣减账户现金（资金来源不强假设）。
     */
    fun buyMoreAssetRecord(
        recordId: String,
        addQuantity: Double,
        buyPrice: Double,
        fee: Double = 0.0,
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ) {
        viewModelScope.launch {
            val record = database.assetRecordDao().getRecordById(recordId) ?: return@launch
            reportLedgerResult(
                holdingLedger.recordTrade(
                    HoldingTradeCommand(
                        accountId = record.accountId,
                        securityCode = record.securityCode.ifBlank { record.name },
                        name = record.name,
                        assetType = record.assetType,
                        riskBucket = record.riskBucket,
                        isBuy = true,
                        quantity = addQuantity,
                        price = buyPrice,
                        currency = record.currency,
                        fee = fee,
                        timestamp = timestamp,
                        note = note?.takeIf { it.isNotBlank() }
                    )
                )
            )
        }
    }

    suspend fun addAssetRecordAndWait(record: AssetRecord): LedgerResult = holdingLedger.upsertSnapshot(
        HoldingSnapshotCommand(
            record = record,
            sourceType = record.sourceType,
            sourceAccountId = record.sourceAccountId,
            sourceRecordId = record.sourceRecordId,
            importBatchId = record.importBatchId,
            sourceFingerprint = record.sourceFingerprint,
            syncedAt = record.syncedAt
        )
    )

    /** 批量导入统一走 HoldingLedger 的单事务入口；失败时整批回滚并返回失败行。 */
    suspend fun importAssetRecordsBatch(records: List<AssetRecord>): SnapshotBatchResult {
        val result = holdingLedger.upsertSnapshotBatch(
            records.map { record ->
                HoldingSnapshotCommand(
                    record = record,
                    sourceType = record.sourceType,
                    sourceAccountId = record.sourceAccountId,
                    sourceRecordId = record.sourceRecordId,
                    importBatchId = record.importBatchId,
                    sourceFingerprint = record.sourceFingerprint,
                    syncedAt = record.syncedAt
                )
            }
        )
        if (!result.committed) {
            _error.value = result.rows.firstOrNull { it.error != null }?.let { "第 ${it.rowIndex + 1} 行：${it.error}" }
        }
        return result
    }

    suspend fun buyMoreAssetRecordAndWait(
        recordId: String,
        addQuantity: Double,
        buyPrice: Double,
        fee: Double = 0.0,
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ): LedgerResult {
        val record = database.assetRecordDao().getRecordById(recordId)
            ?: return LedgerResult.Error("资产记录不存在")
        return holdingLedger.recordTrade(
            HoldingTradeCommand(
                accountId = record.accountId,
                securityCode = record.securityCode.ifBlank { record.name },
                name = record.name,
                assetType = record.assetType,
                riskBucket = record.riskBucket,
                isBuy = true,
                quantity = addQuantity,
                price = buyPrice,
                currency = record.currency,
                fee = fee,
                timestamp = timestamp,
                note = note?.takeIf { it.isNotBlank() }
            )
        )
    }

    /**
     * 原型「记一笔」的统一入口。
     *
     * 流水以证券编码定位账户内的原始持仓：买入时加权成本或新建记录，卖出时按平均成本
     * 扣减并拦截超额卖出。不同账户的记录保留为独立来源，概览层再统一按编码合并。
     * 返回非空文本表示校验失败，便于 UI 直接展示。
     */
    suspend fun recordTradeBySecurityCode(
        accountId: String,
        securityCode: String,
        name: String,
        isBuy: Boolean,
        quantity: Double,
        price: Double,
        riskBucket: RiskBucket,
        timestamp: Long = System.currentTimeMillis(),
        currency: String? = null,
        fee: Double = 0.0,
        note: String? = null
    ): String? = when (
        val result = holdingLedger.recordTrade(
            HoldingTradeCommand(
                accountId = accountId,
                securityCode = securityCode,
                name = name,
                assetType = AssetType.ETF,
                riskBucket = riskBucket,
                isBuy = isBuy,
                quantity = quantity,
                price = price,
                currency = currency,
                fee = fee,
                note = note?.takeIf { it.isNotBlank() },
                timestamp = timestamp
            )
        )
    ) {
        is LedgerResult.Error -> result.message
        is LedgerResult.Success -> null
    }

    fun updateAssetRecord(record: AssetRecord) {
        viewModelScope.launch {
            val existing = database.assetRecordDao().getRecordById(record.id)
            if (existing == null) {
                _error.value = "资产记录不存在"
                return@launch
            }
            reportLedgerResult(
                holdingLedger.upsertSnapshot(
                    HoldingSnapshotCommand(
                        record = record,
                        sourceType = existing.sourceType,
                        sourceAccountId = existing.sourceAccountId,
                        sourceRecordId = existing.sourceRecordId,
                        importBatchId = existing.importBatchId,
                        sourceFingerprint = existing.sourceFingerprint,
                        syncedAt = existing.syncedAt
                    )
                )
            )
        }
    }

    fun deleteAssetRecord(recordId: String) {
        viewModelScope.launch {
            database.withTransaction {
                database.transactionDao().deleteByRecordId(recordId)
                database.priceHistoryDao().deleteByRecordId(recordId)
                database.assetRecordDao().deleteById(recordId)
            }
        }
    }

    /** 新增/更新落点目标（subCategory 为主键，重名即覆盖） */
    fun saveAllocationTarget(target: AllocationTarget) {
        viewModelScope.launch {
            if (target.subCategory.isBlank()) {
                _error.value = "请填写落点名称"
                return@launch
            }
            database.allocationTargetDao().upsert(target.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /** 删除落点目标（不影响已归该落点的持仓，只是不再跟踪目标） */
    fun deleteAllocationTarget(subCategory: String) {
        viewModelScope.launch {
            database.allocationTargetDao().deleteBySubCategory(subCategory)
        }
    }

    fun recordCashIn(accountId: String, amount: Double, note: String? = null) {
        viewModelScope.launch {
            reportLedgerResult(
                holdingLedger.recordCashMovement(
                    accountId, amount, TransactionType.DEPOSIT, note?.ifBlank { null } ?: "入金"
                )
            )
        }
    }

    fun recordCashOut(accountId: String, amount: Double, note: String? = null) {
        viewModelScope.launch {
            reportLedgerResult(
                holdingLedger.recordCashMovement(
                    accountId, amount, TransactionType.WITHDRAW, note?.ifBlank { null } ?: "出金"
                )
            )
        }
    }

    fun transferCash(fromAccountId: String, toAccountId: String, amount: Double, note: String? = null) {
        viewModelScope.launch {
            reportLedgerResult(
                holdingLedger.transferCash(fromAccountId, toAccountId, amount, note?.ifBlank { null })
            )
        }
    }

    /**
     * 卖出资产记录（产生真实的卖出流水）
     * 平均成本法：按比例减少股数和成本
     */
    fun sellAssetRecord(
        recordId: String,
        sellQuantity: Double? = null,
        sellPrice: Double? = null,
        fee: Double = 0.0,
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ) {
        viewModelScope.launch {
            val record = database.assetRecordDao().getRecordById(recordId) ?: return@launch
            val quantityToSell = sellQuantity ?: record.quantity
            val actualPrice = sellPrice?.takeIf { it > 0.0 } ?: record.currentPrice
            reportLedgerResult(
                holdingLedger.recordTrade(
                    HoldingTradeCommand(
                        accountId = record.accountId,
                        securityCode = record.securityCode.ifBlank { record.name },
                        name = record.name,
                        assetType = record.assetType,
                        riskBucket = record.riskBucket,
                        isBuy = false,
                        quantity = quantityToSell,
                        price = actualPrice,
                        currency = record.currency,
                        fee = fee,
                        timestamp = timestamp,
                        note = note?.takeIf { it.isNotBlank() }
                    )
                )
            )
        }
    }

    suspend fun sellAssetRecordAndWait(
        recordId: String,
        sellQuantity: Double? = null,
        sellPrice: Double? = null,
        fee: Double = 0.0,
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ): LedgerResult {
        val record = database.assetRecordDao().getRecordById(recordId)
            ?: return LedgerResult.Error("资产记录不存在")
        val quantityToSell = sellQuantity ?: record.quantity
        val actualPrice = sellPrice?.takeIf { it > 0.0 } ?: record.currentPrice
        return holdingLedger.recordTrade(
            HoldingTradeCommand(
                accountId = record.accountId,
                securityCode = record.securityCode.ifBlank { record.name },
                name = record.name,
                assetType = record.assetType,
                riskBucket = record.riskBucket,
                isBuy = false,
                quantity = quantityToSell,
                price = actualPrice,
                currency = record.currency,
                fee = fee,
                timestamp = timestamp,
                note = note?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            database.settingsDao().update(newSettings)
            _settings.value = newSettings
        }
    }

    fun toggleAmountsVisible() {
        viewModelScope.launch {
            val updated = _settings.value.copy(amountsVisible = !_settings.value.amountsVisible)
            database.settingsDao().update(updated)
            _settings.value = updated
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

            // Position 仅保留为旧版本兼容表；价格和汇率都以 AssetRecord 为准。
            val currencies = (accounts.map { it.currency } +
                    allAssetRecords.map { it.currency })
                .distinct()
                .filter { it != baseCurrency }
            val rates = currencies.associate { "${it}${baseCurrency}" to 1.0 }

            // 基金净值来源不稳定且通常不受 Yahoo 代码接口支持；保留用户手动价格，不在每日刷新中制造错误噪音。
            val tradableTypes = setOf(AssetType.STOCK.name, AssetType.ETF.name)
            val assetRecordCodes = allAssetRecords
                .filter { it.assetType.name in tradableTypes }
                .map { record -> normalizeSecurityCode(record.securityCode.ifBlank { record.name }) }
                .distinct()

            // 合并所有需要刷新的代码
            val allSymbols = assetRecordCodes

            // 批量刷新价格
            val result = priceRepository.refreshAllPrices(allSymbols, rates)
            _lastPriceRefreshPartial.value = result.isPartialFailure
            val requestedSymbols = allSymbols.toSet()
            _priceHealth.value = evaluatePriceHealth(
                database.priceDao().getAllPrices(),
                requestedSymbols = requestedSymbols + rates.keys.map { "${it}=X" },
                partialFailure = result.isPartialFailure
            )

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
            for (code in result.successfulSymbols) {
                val price = priceRepository.getPrice(code)
                if (price != null && price.price > 0 && !price.isFallback) {
                    val matchingRecords = allAssetRecords.filter { record ->
                        normalizeSecurityCode(record.securityCode.ifBlank { record.name }) == code &&
                            record.assetType in listOf(AssetType.STOCK, AssetType.ETF)
                    }
                    for (existing in matchingRecords) {
                        val updated = existing.copy(
                            currentPrice = price.price,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.assetRecordDao().update(updated)
                        database.priceHistoryDao().insert(
                            PriceHistory(
                                recordId = existing.id,
                                price = price.price,
                                cost = existing.averageCost
                            )
                        )
                    }
                }
            }

            // 重新读取更新后的 asset records（价格已回写，需重新获取以反映最新价格）
            val updatedAssetRecords = database.assetRecordDao().getAllRecords().first()
            val allocationTargets = database.allocationTargetDao().getAllTargets().first()
            val snapshots = database.assetSnapshotDao().getAllSnapshots().first()

            calculatePortfolio(accounts, updatedAssetRecords, allocationTargets, settings, snapshots)

            // 如果有部分失败但整体没抛异常，仍提示用户
            if (failureMessages.isNotEmpty()) {
                _error.value = failureMessages.joinToString("；")
            }
        } catch (e: Exception) {
            _error.value = "价格刷新失败，可稍后重试或手动输入价格 · ${e.message}"
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
            .filter { it.origin != com.finunity.data.local.entity.TransactionOrigin.SNAPSHOT_IMPORT }
        val currentBalance = if (account.type == com.finunity.data.local.entity.AccountType.LIABILITY) {
            account.balance
        } else {
            database.assetRecordDao().getRecordsByAccount(accountId).first()
                .filter { it.assetType == AssetType.CASH && it.currency.equals(account.currency, ignoreCase = true) }
                .sumOf { it.currentValue }
        }
        val first = transactions.firstOrNull()
        val openingBalance = when {
            account.type == com.finunity.data.local.entity.AccountType.LIABILITY && account.initialPrincipal > 0.0 -> account.initialPrincipal
            first?.balanceAfter != null -> first.balanceAfter - reconciliationDelta(first, account.type)
            else -> null
        }
        if (openingBalance == null) {
            return ReconciliationResult(
                isBalanced = false,
                currentBalance = currentBalance,
                computedBalance = 0.0,
                difference = 0.0,
                issues = listOf("缺少期初余额或起始快照，无法可靠核对"),
                status = ReconciliationStatus.INSUFFICIENT_DATA
            )
        }

        // 按时间顺序从期初余额累加，不能把没有依据的 0 当成起点。
        val opening = checkNotNull(openingBalance) // null 已在上面的数据不足分支返回
        var computedBalance = opening
        val issues = mutableListOf<String>()

        for (tx in transactions) {
            if (account.type == com.finunity.data.local.entity.AccountType.LIABILITY) {
                if (tx.type == TransactionType.LIABILITY_PAYMENT) computedBalance = (computedBalance - tx.amount).coerceAtLeast(0.0)
            } else {
                when (tx.type) {
                    TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.DIVIDEND, TransactionType.SELL -> {
                        computedBalance += tx.amount
                    }
                    TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT, TransactionType.BUY, TransactionType.FEE,
                    TransactionType.LIABILITY_PAYMENT -> {
                        computedBalance -= tx.amount
                    }
                }
            }

            // 验证交易后的余额记录（如果有的话）
            tx.balanceAfter?.takeIf { account.type != com.finunity.data.local.entity.AccountType.LIABILITY }?.let { recorded ->
                if (kotlin.math.abs(recorded - computedBalance) > 0.01) {
                    issues.add("交易 ${tx.id} 记录余额 $recorded 与推导余额 $computedBalance 不符")
                    computedBalance = recorded // 以记录为准
                }
            }
        }

        val difference = computedBalance - currentBalance

        val result = ReconciliationResult(
            isBalanced = kotlin.math.abs(difference) < 0.01,
            currentBalance = currentBalance,
            computedBalance = computedBalance,
            difference = difference,
            issues = issues,
            status = if (kotlin.math.abs(difference) < 0.01 && issues.isEmpty()) {
                ReconciliationStatus.CONSISTENT
            } else {
                ReconciliationStatus.INCONSISTENT
            }
        )

        // 仅保留给审计场景使用，普通账户金额不通过 balance 参与资产统计。
        if (autoFix && !result.isBalanced && account.type == com.finunity.data.local.entity.AccountType.LIABILITY) {
            val updated = account.copy(balance = computedBalance)
            database.accountDao().update(updated)
        }

        return result
    }

    /**
     * 获取账户的计算余额（从交易流水中推导）
     */
    suspend fun getComputedBalance(accountId: String): Double? {
        val account = database.accountDao().getAccountById(accountId) ?: return null
        val transactions = database.transactionDao().getTransactionsForReconciliation(accountId)
            .filter { it.origin != com.finunity.data.local.entity.TransactionOrigin.SNAPSHOT_IMPORT }
        val first = transactions.firstOrNull()
        val openingBalance = when {
            account.type == com.finunity.data.local.entity.AccountType.LIABILITY && account.initialPrincipal > 0.0 -> account.initialPrincipal
            first?.balanceAfter != null -> first.balanceAfter - reconciliationDelta(first, account.type)
            else -> return null
        }
        val transactionsToApply = if (
            account.type == com.finunity.data.local.entity.AccountType.LIABILITY && account.initialPrincipal > 0.0
        ) transactions else transactions.drop(1)
        return transactionsToApply.fold(openingBalance) { balance, tx ->
            if (account.type == com.finunity.data.local.entity.AccountType.LIABILITY) {
                if (tx.type == TransactionType.LIABILITY_PAYMENT) (balance - tx.amount).coerceAtLeast(0.0) else balance
            } else {
                balance + reconciliationDelta(tx, account.type)
            }
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
        val assetRecords: List<AssetRecord>,
        val allocationTargets: List<AllocationTarget>,
        val settings: Settings,
        val snapshots: List<AssetSnapshot> = emptyList()
    )
}

private fun reconciliationDelta(
    transaction: com.finunity.data.local.entity.Transaction,
    accountType: com.finunity.data.local.entity.AccountType
): Double {
    if (accountType == com.finunity.data.local.entity.AccountType.LIABILITY) return 0.0
    return when (transaction.type) {
        TransactionType.DEPOSIT,
        TransactionType.TRANSFER_IN,
        TransactionType.DIVIDEND,
        TransactionType.SELL -> transaction.amount
        TransactionType.WITHDRAW,
        TransactionType.TRANSFER_OUT,
        TransactionType.BUY,
        TransactionType.FEE,
        TransactionType.LIABILITY_PAYMENT -> -transaction.amount
    }
}

enum class ReconciliationStatus { CONSISTENT, INCONSISTENT, INSUFFICIENT_DATA }

/**
 * 余额核对结果
 */
data class ReconciliationResult(
    val isBalanced: Boolean,
    val currentBalance: Double,
    val computedBalance: Double,
    val difference: Double,
    val issues: List<String>,
    val status: ReconciliationStatus = if (isBalanced) ReconciliationStatus.CONSISTENT else ReconciliationStatus.INCONSISTENT
)
