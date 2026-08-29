package com.finunity.data.repository

import androidx.room.withTransaction
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionOrigin
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.data.model.HoldingCostState
import com.finunity.data.model.HoldingTradeCalculation
import com.finunity.data.model.calculateHoldingTrade
import com.finunity.data.model.normalizeSecurityCode
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.UUID

/** A persisted holding snapshot from an import or a manual declaration. */
data class HoldingSnapshotCommand(
    val record: AssetRecord,
    val sourceType: HoldingSourceType,
    val sourceAccountId: String = "",
    val sourceRecordId: String = "",
    val importBatchId: String = "",
    val sourceFingerprint: String = "",
    val syncedAt: Long? = null
)

data class SnapshotBatchRowResult(
    val rowIndex: Int,
    val recordId: String? = null,
    val error: String? = null
)

data class SnapshotBatchResult(
    val rows: List<SnapshotBatchRowResult>,
    val committed: Boolean
)

/** A user-initiated trade. All trades mutate holdings, cash and their audit row atomically. */
data class HoldingTradeCommand(
    val accountId: String,
    val securityCode: String,
    val name: String,
    val assetType: AssetType,
    val riskBucket: RiskBucket,
    val isBuy: Boolean,
    val quantity: Double,
    val price: Double,
    /** Currency of the traded instrument and its matching cash bucket. */
    val currency: String? = null,
    val fee: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val origin: TransactionOrigin = TransactionOrigin.TRADE,
    val sourceFingerprint: String = "",
    val category: CashFlowCategory = CashFlowCategory.INVESTMENT,
    val importBatchId: String = ""
)

sealed interface LedgerResult {
    data class Success(val recordIds: List<String>) : LedgerResult
    data class Error(val message: String) : LedgerResult
}

/**
 * The only write pipeline for current holdings.
 *
 * AssetRecord is the current holding projection. Imports replace their own source snapshot through
 * [HoldingSnapshotCommand.sourceFingerprint]; trades update all matching source rows in one Room
 * transaction and write a TRADE audit event. This avoids the old split between a free-standing
 * transaction list and an unrelated holding table.
 */
class HoldingLedgerRepository(
    private val database: AppDatabase,
    private val priceRepository: PriceRepository? = null
) {

    suspend fun upsertSnapshot(command: HoldingSnapshotCommand): LedgerResult = database.withTransaction {
        upsertSnapshotInTransaction(command)
    }

    private suspend fun upsertSnapshotInTransaction(command: HoldingSnapshotCommand): LedgerResult {
        val input = command.record
        if (input.accountId.isBlank()) return LedgerResult.Error("请选择归属账户")
        if (input.quantity < 0.0 || input.cost < 0.0 || input.currentPrice < 0.0) {
            return LedgerResult.Error("数量、成本和当前价不能为负数")
        }
        if (input.assetType != AssetType.CASH && input.name.isBlank()) {
            return LedgerResult.Error("请填写资产名称")
        }

        val account = database.accountDao().getAccountById(input.accountId)
            ?: return LedgerResult.Error("归属账户不存在")
        val inputCurrency = normalizeCurrency(input.currency, account.currency)
        val code = input.securityCode.trim().ifBlank { input.name.trim() }
        val normalizedCode = normalizeSecurityCode(code)
        val sourceAccountId = command.sourceAccountId.ifBlank { input.accountId }
        val fingerprint = command.sourceFingerprint.ifBlank {
            "${command.sourceType.name}:$sourceAccountId:$normalizedCode"
        }
        val allRecords = database.assetRecordDao().getAllRecords().first()
        val existing = allRecords.firstOrNull { it.id == input.id }
            ?: allRecords.firstOrNull { it.sourceFingerprint == fingerprint }
        val now = System.currentTimeMillis()
        val saved = (existing?.copy(
            accountId = input.accountId,
            assetType = input.assetType,
            riskBucket = input.riskBucket,
            name = input.name.trim(),
            securityCode = code,
            quantity = input.quantity,
            cost = input.cost,
            currentPrice = input.currentPrice,
            currency = inputCurrency,
            subCategory = input.subCategory,
            industryTag = input.industryTag,
            purchaseRestricted = input.purchaseRestricted,
            peRatio = input.peRatio,
            dividendYield = input.dividendYield,
            premiumRate = input.premiumRate,
            locked = input.locked,
            sourceType = command.sourceType,
            sourceAccountId = sourceAccountId,
            sourceRecordId = command.sourceRecordId,
            // Updating an existing fingerprint must not make the old record look like a
            // newly-created row; otherwise rolling back a later import could delete it.
            importBatchId = existing.importBatchId,
            sourceFingerprint = fingerprint,
            syncedAt = command.syncedAt,
            updatedAt = now
        ) ?: input.copy(
            id = if (input.id.isBlank()) UUID.randomUUID().toString() else input.id,
            name = input.name.trim(),
            securityCode = code,
            currency = inputCurrency,
            sourceType = command.sourceType,
            sourceAccountId = sourceAccountId,
            sourceRecordId = command.sourceRecordId,
            importBatchId = command.importBatchId,
            sourceFingerprint = fingerprint,
            syncedAt = command.syncedAt,
            createdAt = now,
            updatedAt = now
        ))

        val previousPrice = existing?.currentPrice
        if (existing == null) database.assetRecordDao().insert(saved) else database.assetRecordDao().update(saved)
        writeValuationHistory(saved, previousPrice)

        // Snapshot rows are audit events, not trading events. They must never be replayed as buys.
        if (existing == null && saved.assetType != AssetType.CASH) {
            database.transactionDao().insert(
                Transaction(
                    accountId = saved.accountId,
                    symbol = saved.securityCode,
                    type = TransactionType.BUY,
                    shares = saved.quantity,
                    price = saved.averageCost,
                    amount = saved.cost,
                    currency = saved.currency,
                    note = "${command.sourceType.name} 快照录入 ${saved.name}",
                    recordId = saved.id,
                    origin = TransactionOrigin.SNAPSHOT_IMPORT,
                    sourceFingerprint = "SNAPSHOT:${saved.sourceFingerprint}",
                    importBatchId = command.importBatchId,
                    category = CashFlowCategory.INVESTMENT
                )
            )
        }
        return LedgerResult.Success(listOf(saved.id))
    }

    /**
     * 批量快照导入的唯一入口。任何一行失败都会回滚整个批次，避免 UI 把部分成功报成全量成功。
     * 已存在的 sourceFingerprint 会更新原记录而不会创建重复流水，因而重复导入保持幂等。
     */
    suspend fun upsertSnapshotBatch(commands: List<HoldingSnapshotCommand>): SnapshotBatchResult {
        if (commands.isEmpty()) return SnapshotBatchResult(emptyList(), committed = true)
        val completed = mutableListOf<SnapshotBatchRowResult>()
        return try {
            database.withTransaction {
                commands.forEachIndexed { index, command ->
                    when (val result = upsertSnapshotInTransaction(command)) {
                        is LedgerResult.Success -> completed += SnapshotBatchRowResult(index, result.recordIds.singleOrNull())
                        is LedgerResult.Error -> {
                            completed += SnapshotBatchRowResult(index, error = result.message)
                            throw BatchImportRollbackException
                        }
                    }
                }
            }
            SnapshotBatchResult(completed, committed = true)
        } catch (_: BatchImportRollbackException) {
            SnapshotBatchResult(completed, committed = false)
        }
    }

    private object BatchImportRollbackException : RuntimeException()

    suspend fun recordTrade(command: HoldingTradeCommand): LedgerResult = database.withTransaction {
        if (command.sourceFingerprint.isNotBlank() &&
            database.transactionDao().getBySourceFingerprint(command.sourceFingerprint) != null
        ) {
            return@withTransaction LedgerResult.Success(emptyList())
        }
        val code = command.securityCode.trim()
        val name = command.name.trim()
        if (command.accountId.isBlank()) return@withTransaction LedgerResult.Error("请选择成交账户")
        if (code.isBlank() || name.isBlank()) return@withTransaction LedgerResult.Error("请填写名称和证券编码")
        if (command.quantity <= 0.0 || command.price <= 0.0 || command.fee < 0.0) {
            return@withTransaction LedgerResult.Error("数量、成交价必须大于 0，费用不能为负数")
        }
        val account = database.accountDao().getAccountById(command.accountId)
            ?: return@withTransaction LedgerResult.Error("成交账户不存在")
        val normalizedCode = normalizeSecurityCode(code)
        val allMatchingRecords = database.assetRecordDao().getAllRecords().first()
            .filter {
                it.accountId == command.accountId &&
                    normalizeSecurityCode(it.securityCode.ifBlank { it.name }) == normalizedCode
            }
            .sortedBy { it.createdAt }

        val currencies = allMatchingRecords
            .map { normalizeCurrency(it.currency, account.currency) }
            .distinct()
        if (command.currency.isNullOrBlank() && currencies.size > 1) {
            return@withTransaction LedgerResult.Error("同一编码存在多个币种，请从对应币种资产发起交易")
        }
        val tradeCurrency = normalizeCurrency(command.currency, currencies.singleOrNull() ?: account.currency)
        val records = allMatchingRecords.filter {
            normalizeCurrency(it.currency, account.currency) == tradeCurrency
        }
        if (!command.isBuy && records.isEmpty()) {
            return@withTransaction LedgerResult.Error("该账户没有编码 $code 的可卖持仓")
        }

        val totalQuantity = records.sumOf { it.quantity }
        val totalCost = records.sumOf { it.cost }
        val calculation = calculateHoldingTrade(
            existing = if (records.isEmpty()) null else HoldingCostState(totalQuantity, totalCost),
            isBuy = command.isBuy,
            quantity = command.quantity,
            price = command.price
        )
        if (calculation is HoldingTradeCalculation.Error) {
            return@withTransaction if (!command.isBuy && command.quantity > totalQuantity) {
                LedgerResult.Error("超出可卖数量（当前 ${formatQuantity(totalQuantity)}）")
            } else {
                LedgerResult.Error(calculation.message)
            }
        }
        val next = (calculation as HoldingTradeCalculation.Success).state
        val amount = command.quantity * command.price
        if (command.fee > amount) return@withTransaction LedgerResult.Error("费用不能大于成交金额")

        val cashDelta = if (command.isBuy) -amount - command.fee else amount - command.fee
        val cashResult = adjustCash(command.accountId, cashDelta, tradeCurrency)
        if (cashResult is LedgerResult.Error) return@withTransaction cashResult
        val cashBalanceAfter = database.assetRecordDao().getRecordsByAccount(command.accountId).first()
            .filter { it.assetType == AssetType.CASH && it.currency.equals(tradeCurrency, ignoreCase = true) }
            .sumOf { it.currentValue }

        val changedIds = mutableListOf<String>()
        val recordId: String?
        if (command.isBuy) {
            val target = records.firstOrNull { it.sourceType == HoldingSourceType.TRADE } ?: records.firstOrNull()
            val saved = if (target == null) {
                AssetRecord(
                    accountId = command.accountId,
                    assetType = command.assetType,
                    riskBucket = command.riskBucket,
                    name = name,
                    securityCode = code,
                    quantity = next!!.quantity,
                    cost = next.totalCost,
                    currentPrice = command.price,
                    currency = tradeCurrency,
                    sourceType = HoldingSourceType.TRADE,
                    sourceAccountId = command.accountId,
                    sourceRecordId = UUID.randomUUID().toString(),
                    importBatchId = "trade",
                    sourceFingerprint = "TRADE:${command.accountId}:$normalizedCode"
                ).also { database.assetRecordDao().insert(it) }
            } else {
                target.copy(
                    name = name,
                    securityCode = code,
                    quantity = target.quantity + command.quantity,
                    cost = target.cost + amount,
                    currentPrice = command.price,
                    updatedAt = System.currentTimeMillis()
                ).also { database.assetRecordDao().update(it) }
            }
            changedIds += saved.id
            writeValuationHistory(saved, target?.currentPrice)
            recordId = saved.id
        } else {
            val totalRemaining = next?.quantity ?: 0.0
            var soldAllocated = 0.0
            records.forEachIndexed { index, record ->
                val sellForRecord = if (index == records.lastIndex) {
                    command.quantity - soldAllocated
                } else {
                    command.quantity * record.quantity / totalQuantity
                }.coerceIn(0.0, record.quantity)
                soldAllocated += sellForRecord
                val remaining = (record.quantity - sellForRecord).coerceAtLeast(0.0)
                if (remaining <= 0.0000001) {
                    database.assetRecordDao().deleteById(record.id)
                } else {
                    val updated = record.copy(
                        quantity = remaining,
                        cost = if (totalRemaining > 0.0) record.cost * remaining / record.quantity else 0.0,
                        currentPrice = command.price,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.assetRecordDao().update(updated)
                    changedIds += updated.id
                    writeValuationHistory(updated, record.currentPrice)
                }
            }
            recordId = records.singleOrNull()?.id
        }

        database.transactionDao().insert(
            Transaction(
                accountId = command.accountId,
                symbol = code,
                type = if (command.isBuy) TransactionType.BUY else TransactionType.SELL,
                shares = command.quantity,
                price = command.price,
                amount = amount,
                currency = tradeCurrency,
                timestamp = command.timestamp,
                note = command.note ?: "${if (command.isBuy) "买入" else "卖出"} $name · $code",
                recordId = recordId,
                balanceAfter = cashBalanceAfter + command.fee,
                origin = command.origin,
                sourceFingerprint = command.sourceFingerprint,
                category = command.category,
                importBatchId = command.importBatchId
            )
        )
        if (command.fee > 0.0) {
            database.transactionDao().insert(
                Transaction(
                    accountId = command.accountId,
                    symbol = code,
                    type = TransactionType.FEE,
                    shares = null,
                    price = null,
                    amount = command.fee,
                    currency = tradeCurrency,
                    timestamp = command.timestamp,
                    note = "${if (command.isBuy) "买入" else "卖出"} $name 费用",
                    recordId = recordId,
                    balanceAfter = cashBalanceAfter,
                    origin = command.origin,
                    sourceFingerprint = command.sourceFingerprint.takeIf { it.isNotBlank() }?.plus(":fee") ?: "",
                    category = CashFlowCategory.FEE,
                    importBatchId = command.importBatchId
                )
            )
        }
        LedgerResult.Success(changedIds)
    }

    suspend fun recordCashMovement(
        accountId: String,
        amount: Double,
        type: TransactionType,
        note: String? = null,
        origin: TransactionOrigin = TransactionOrigin.CASH_FLOW,
        sourceFingerprint: String = "",
        category: CashFlowCategory = CashFlowCategory.OTHER,
        importBatchId: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): LedgerResult = database.withTransaction {
        if (sourceFingerprint.isNotBlank() && database.transactionDao().getBySourceFingerprint(sourceFingerprint) != null) {
            return@withTransaction LedgerResult.Success(emptyList())
        }
        if (amount <= 0.0) return@withTransaction LedgerResult.Error("金额必须大于 0")
        val account = database.accountDao().getAccountById(accountId)
            ?: return@withTransaction LedgerResult.Error("账户不存在")
        val delta = when (type) {
            TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.DIVIDEND -> amount
            TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT, TransactionType.FEE -> -amount
            else -> return@withTransaction LedgerResult.Error("不支持的现金流水类型")
        }
        val cashResult = adjustCash(accountId, delta, account.currency)
        if (cashResult is LedgerResult.Error) return@withTransaction cashResult
        val balanceAfter = database.assetRecordDao().getRecordsByAccount(accountId).first()
            .filter { it.assetType == AssetType.CASH && it.currency.equals(account.currency, ignoreCase = true) }
            .sumOf { it.currentValue }
        database.transactionDao().insert(
            Transaction(
                accountId = accountId,
                symbol = null,
                type = type,
                shares = null,
                price = null,
                amount = amount,
                currency = account.currency,
                timestamp = timestamp,
                note = note,
                recordId = null,
                balanceAfter = balanceAfter,
                origin = origin,
                sourceFingerprint = sourceFingerprint,
                category = normalizeCategory(type, category),
                importBatchId = importBatchId
            )
        )
        LedgerResult.Success(emptyList())
    }

    /** Record a repayment against a liability account without inventing a cash asset. */
    suspend fun recordLiabilityPayment(
        accountId: String,
        amount: Double,
        note: String? = null,
        origin: TransactionOrigin = TransactionOrigin.CASH_FLOW,
        sourceFingerprint: String = "",
        importBatchId: String = ""
    ): LedgerResult = database.withTransaction {
        if (sourceFingerprint.isNotBlank() && database.transactionDao().getBySourceFingerprint(sourceFingerprint) != null) {
            return@withTransaction LedgerResult.Success(emptyList())
        }
        if (amount <= 0.0) return@withTransaction LedgerResult.Error("还款金额必须大于 0")
        val account = database.accountDao().getAccountById(accountId)
            ?: return@withTransaction LedgerResult.Error("负债账户不存在")
        if (account.type != com.finunity.data.local.entity.AccountType.LIABILITY) {
            return@withTransaction LedgerResult.Error("只能对负债账户记录还款")
        }
        if (amount > account.balance + 0.01) {
            return@withTransaction LedgerResult.Error("还款金额不能超过当前待还金额")
        }
        database.accountDao().update(account.copy(balance = (account.balance - amount).coerceAtLeast(0.0)))
        database.transactionDao().insert(
            Transaction(
                accountId = accountId,
                symbol = null,
                type = TransactionType.LIABILITY_PAYMENT,
                shares = null,
                price = null,
                amount = amount,
                currency = account.currency,
                note = note?.ifBlank { "负债还款" } ?: "负债还款",
                origin = origin,
                sourceFingerprint = sourceFingerprint,
                category = CashFlowCategory.LOAN_REPAYMENT,
                importBatchId = importBatchId,
                balanceAfter = (account.balance - amount).coerceAtLeast(0.0)
            )
        )
        LedgerResult.Success(emptyList())
    }

    suspend fun transferCash(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        note: String? = null,
        origin: TransactionOrigin = TransactionOrigin.CASH_FLOW,
        sourceFingerprint: String = ""
    ): LedgerResult {
        if (amount <= 0.0) return LedgerResult.Error("金额必须大于 0")
        if (sourceFingerprint.isNotBlank() &&
            database.transactionDao().getBySourceFingerprint("$sourceFingerprint:out") != null
        ) {
            return LedgerResult.Success(emptyList())
        }

        val from = database.accountDao().getAccountById(fromAccountId)
            ?: return LedgerResult.Error("转出账户不存在")
        val to = database.accountDao().getAccountById(toAccountId)
            ?: return LedgerResult.Error("转入账户不存在")
        if (fromAccountId == toAccountId) return LedgerResult.Error("转入账户不能与转出账户相同")

        val fromCurrency = normalizeCurrency(from.currency, "CNY")
        val toCurrency = normalizeCurrency(to.currency, "CNY")
        val rate = if (fromCurrency == toCurrency) {
            1.0
        } else {
            resolveExchangeRate(fromCurrency, toCurrency)
                ?: return LedgerResult.Error("无法获取 $fromCurrency→$toCurrency 汇率，请联网后重试")
        }
        val receivedAmount = amount * rate
        if (!receivedAmount.isFinite() || receivedAmount <= 0.0) {
            return LedgerResult.Error("汇率无效，请稍后重试")
        }

        return database.withTransaction {
            if (sourceFingerprint.isNotBlank() &&
                database.transactionDao().getBySourceFingerprint("$sourceFingerprint:out") != null
            ) {
                return@withTransaction LedgerResult.Success(emptyList())
            }

            val currentFrom = database.accountDao().getAccountById(fromAccountId)
                ?: return@withTransaction LedgerResult.Error("转出账户不存在")
            val currentTo = database.accountDao().getAccountById(toAccountId)
                ?: return@withTransaction LedgerResult.Error("转入账户不存在")
            val outResult = adjustCash(fromAccountId, -amount, fromCurrency)
            if (outResult is LedgerResult.Error) return@withTransaction outResult
            val inResult = adjustCash(toAccountId, receivedAmount, toCurrency)
            if (inResult is LedgerResult.Error) return@withTransaction inResult

            val rateNote = if (fromCurrency == toCurrency) {
                ""
            } else {
                "，汇率 ${String.format(Locale.US, "%.6f", rate)}，到账 ${formatAmount(receivedAmount)} $toCurrency"
            }
            val transferNote = note?.ifBlank { null }
                ?: "${currentFrom.name} 转入 ${currentTo.name}$rateNote"
            database.transactionDao().insert(
                Transaction(
                    accountId = fromAccountId,
                    symbol = null,
                    type = TransactionType.TRANSFER_OUT,
                    shares = null,
                    price = null,
                    amount = amount,
                    currency = fromCurrency,
                    note = transferNote,
                    origin = origin,
                    sourceFingerprint = sourceFingerprint.takeIf { it.isNotBlank() }?.plus(":out") ?: "",
                    category = CashFlowCategory.TRANSFER,
                    importBatchId = ""
                )
            )
            database.transactionDao().insert(
                Transaction(
                    accountId = toAccountId,
                    symbol = null,
                    type = TransactionType.TRANSFER_IN,
                    shares = null,
                    price = null,
                    amount = receivedAmount,
                    currency = toCurrency,
                    note = transferNote,
                    origin = origin,
                    sourceFingerprint = sourceFingerprint.takeIf { it.isNotBlank() }?.plus(":in") ?: "",
                    category = CashFlowCategory.TRANSFER,
                    importBatchId = ""
                )
            )
            LedgerResult.Success(emptyList())
        }
    }

    private suspend fun resolveExchangeRate(fromCurrency: String, toCurrency: String): Double? {
        val repository = priceRepository ?: return null
        val direct = repository.getExchangeRate(fromCurrency, toCurrency)
        if (direct != null && direct.isFinite() && direct > 0.0) return direct
        val inverse = repository.getExchangeRate(toCurrency, fromCurrency)
        return inverse?.takeIf { it.isFinite() && it > 0.0 }?.let { 1.0 / it }
    }

    private suspend fun adjustCash(accountId: String, delta: Double, currency: String): LedgerResult {
        val records = database.assetRecordDao().getAllRecords().first()
        val existing = records.firstOrNull {
            it.accountId == accountId &&
                it.assetType == AssetType.CASH &&
                it.currency.equals(currency, ignoreCase = true) &&
                it.name == "现金"
        }
        val current = existing?.currentValue ?: 0.0
        val next = current + delta
        if (next < -0.01) return LedgerResult.Error("现金余额不足，请先记录入金或调低买入金额")
        if (existing == null && next > 0.0) {
            database.assetRecordDao().insert(
                AssetRecord(
                    accountId = accountId,
                    assetType = AssetType.CASH,
                    riskBucket = RiskBucket.DEFENSIVE,
                    name = "现金",
                    quantity = next,
                    cost = next,
                    currentPrice = 1.0,
                    currency = currency,
                    sourceType = HoldingSourceType.CASH_FLOW,
                    sourceAccountId = accountId,
                    sourceRecordId = "cash:$currency",
                    importBatchId = "cash-flow",
                    sourceFingerprint = "CASH:$accountId:$currency"
                )
            )
        } else if (existing != null && next <= 0.01) {
            database.assetRecordDao().deleteById(existing.id)
        } else if (existing != null) {
            database.assetRecordDao().update(existing.copy(quantity = next, cost = next, currentPrice = 1.0, updatedAt = System.currentTimeMillis()))
        }
        return LedgerResult.Success(emptyList())
    }

    private suspend fun writeValuationHistory(record: AssetRecord, oldPrice: Double?) {
        // Manual valuation history also matters for deposits, property, vehicles and policies.
        if (record.assetType == AssetType.CASH) return
        if (oldPrice == null || oldPrice != record.currentPrice) {
            database.priceHistoryDao().insert(
                PriceHistory(recordId = record.id, price = record.currentPrice, cost = record.averageCost)
            )
        }
    }

    private fun normalizeCategory(type: TransactionType, category: CashFlowCategory): CashFlowCategory =
        if (category != CashFlowCategory.OTHER) category else when (type) {
            TransactionType.DEPOSIT -> CashFlowCategory.OTHER_INCOME
            TransactionType.WITHDRAW -> CashFlowCategory.OTHER_EXPENSE
            TransactionType.DIVIDEND -> CashFlowCategory.DIVIDEND
            TransactionType.FEE -> CashFlowCategory.FEE
            TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> CashFlowCategory.TRANSFER
            else -> category
        }

    private fun formatQuantity(quantity: Double): String =
        String.format(java.util.Locale.US, "%.4f", quantity).trimEnd('0').trimEnd('.')

    private fun formatAmount(amount: Double): String =
        String.format(Locale.US, "%.2f", amount)

    private fun normalizeCurrency(value: String?, fallback: String): String {
        return value.orEmpty().trim().uppercase().ifBlank {
            fallback.trim().uppercase().ifBlank { "CNY" }
        }
    }
}
