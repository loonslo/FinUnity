package com.finunity.data.repository

import androidx.room.withTransaction
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AllocationTarget
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.legacyBucketToThreeBucket
import com.finunity.data.local.entity.validateTargetAllocation
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.RecurringRule
import com.finunity.data.model.normalizeSecurityCode
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

private const val CURRENT_BACKUP_VERSION = 9

/**
 * 备份数据结构
 */
data class BackupData(
    val version: Int = CURRENT_BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val accounts: List<Account> = emptyList(),
    val assetRecords: List<AssetRecord> = emptyList(),
    /** Retained only so older backup readers can still parse the file. Import migrates these rows. */
    val positions: List<Position> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val prices: List<Price> = emptyList(),
    val priceHistory: List<PriceHistory> = emptyList(),
    val assetSnapshots: List<AssetSnapshot> = emptyList(),
    val allocationTargets: List<AllocationTarget> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList(),
    val settings: Settings? = Settings()
)

data class BackupSummary(
    val version: Int,
    val exportedAt: Long,
    val accountCount: Int,
    val assetCount: Int,
    val transactionCount: Int,
    val snapshotCount: Int,
    val recurringRuleCount: Int
)

/**
 * 备份/恢复仓库
 * 将全部本地数据导出为 JSON 并支持从 JSON 恢复
 */
class BackupRepository(private val db: AppDatabase) {

    private val gson = Gson()

    /**
     * 导出全部数据为 JSON 字符串
     */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().getAllAccounts().first()
        val assetRecords = db.assetRecordDao().getAllRecords().first()
        val positions = db.positionDao().getAllPositions().first()
        val transactions = db.transactionDao().getAllTransactions().first()
        val prices = db.priceDao().getAllPrices()
        val priceHistory = db.priceHistoryDao().getAllHistory().first()
        val assetSnapshots = db.assetSnapshotDao().getAllSnapshots().first()
        val allocationTargets = db.allocationTargetDao().getAllTargets().first()
        val recurringRules = db.recurringRuleDao().getAll().first()
        val settings = db.settingsDao().getSettingsOnce() ?: Settings()

        val backup = BackupData(
            accounts = accounts,
            assetRecords = assetRecords,
            positions = positions,
            transactions = transactions,
            prices = prices,
            priceHistory = priceHistory,
            assetSnapshots = assetSnapshots,
            allocationTargets = allocationTargets,
            recurringRules = recurringRules,
            settings = settings
        )
        gson.toJson(backup)
    }

    /**
     * 从 JSON 字符串恢复数据。失败返回 Result.failure，不会破坏现有数据。
     * 使用数据库事务保证全部成功或全部回滚。
     */
    suspend fun import(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            require(json.isNotBlank()) { "备份文件为空" }
            val backup = gson.fromJson(normalizeLegacyJson(json), BackupData::class.java)
                ?: throw IllegalArgumentException("备份文件为空")
            require(backup.version == 0 || backup.version <= CURRENT_BACKUP_VERSION) {
                "备份版本 ${backup.version} 高于当前版本 $CURRENT_BACKUP_VERSION，请升级应用后再恢复"
            }

            val accounts = backup.accounts.orEmpty().map { it.normalized() }
            require(accounts.map { it.id }.distinct().size == accounts.size) { "账户 ID 重复" }
            require(accounts.all { it.id.isNotBlank() && it.name.isNotBlank() }) { "账户数据不完整" }
            val accountIds = accounts.map { it.id }.toSet()

            val assetRecords = backup.assetRecords.orEmpty().map {
                it.normalized(accounts)
            }.toMutableList()
            val assetIds = assetRecords.map { it.id }.toMutableSet()
            require(assetIds.size == assetRecords.size) { "资产记录 ID 重复" }

            // v1-v6 backups may only contain the legacy positions table. Migrate those rows into
            // the active AssetRecord ledger instead of restoring data that the current UI ignores.
            backup.positions.orEmpty().forEach { position ->
                val code = normalizeSecurityCode(position.symbol.orEmpty())
                val alreadyImported = assetRecords.any {
                    it.id == position.id ||
                        (it.accountId == position.accountId &&
                            normalizeSecurityCode(it.securityCode.ifBlank { it.name }) == code &&
                            it.currency.equals(position.currency, ignoreCase = true))
                }
                if (!alreadyImported) {
                    require(accountIds.contains(position.accountId)) { "旧持仓关联了不存在的账户" }
                    val migrated = position.toAssetRecord()
                    require(assetIds.add(migrated.id)) { "资产 ID 重复" }
                    assetRecords += migrated
                }
            }

            require(assetRecords.all { accountIds.contains(it.accountId) }) { "资产记录关联了不存在的账户" }
            val transactions = backup.transactions.orEmpty().map {
                it.normalized(accounts)
            }
            require(transactions.map { it.id }.distinct().size == transactions.size) { "交易流水 ID 重复" }
            require(transactions.all { accountIds.contains(it.accountId) }) { "交易流水关联了不存在的账户" }
            require(transactions.all { it.recordId.isNullOrBlank() || assetIds.contains(it.recordId) }) {
                "交易流水关联了不存在的资产记录"
            }
            val recurringRules = backup.recurringRules.orEmpty().map {
                require(accountIds.contains(it.accountId)) { "周期规则关联了不存在的账户" }
                it.copy(currency = it.currency.orEmpty().trim().uppercase().ifBlank { accounts.first { account -> account.id == it.accountId }.currency })
            }
            val prices = backup.prices.orEmpty()
            require(prices.map { it.symbol }.distinct().size == prices.size) { "价格缓存 ID 重复" }
            val priceHistory = backup.priceHistory.orEmpty()
            require(priceHistory.map { it.id }.distinct().size == priceHistory.size) { "价格历史 ID 重复" }
            require(priceHistory.all { assetIds.contains(it.recordId) }) { "价格历史关联了不存在的资产记录" }
            require(backup.assetSnapshots.orEmpty().map { it.id }.distinct().size == backup.assetSnapshots.orEmpty().size) {
                "资产快照 ID 重复"
            }
            require(backup.allocationTargets.orEmpty().map { it.subCategory }.distinct().size == backup.allocationTargets.orEmpty().size) {
                "落点目标 ID 重复"
            }
            require(backup.recurringRules.orEmpty().map { it.id }.distinct().size == backup.recurringRules.orEmpty().size) {
                "周期规则 ID 重复"
            }
            validateFiniteAmounts(accounts, assetRecords, transactions, prices, priceHistory, backup.assetSnapshots.orEmpty(), backup.allocationTargets.orEmpty(), recurringRules)

            db.withTransaction {
                // 按外键依赖逆序清空：交易 → 价格/价格历史 → 资产记录 → 持仓 → 账户
                db.transactionDao().deleteAll()
                db.priceDao().deleteAll()
                db.priceHistoryDao().deleteAll()
                db.assetRecordDao().deleteAll()
                db.positionDao().deleteAll()
                db.accountDao().deleteAll()
                db.allocationTargetDao().deleteAll()
                db.recurringRuleDao().deleteAll()
                db.assetSnapshotDao().deleteAll()

                // 按外键依赖顺序插入：账户 → 资产记录 → 交易 → 价格历史
                accounts.forEach { db.accountDao().insert(it) }
                assetRecords.forEach { db.assetRecordDao().insert(it) }
                transactions.forEach { db.transactionDao().insert(it) }
                prices.forEach { db.priceDao().insert(it) }
                priceHistory.forEach { db.priceHistoryDao().insert(it) }
                backup.assetSnapshots.orEmpty().forEach { db.assetSnapshotDao().insert(it) }
                backup.allocationTargets.orEmpty().forEach { db.allocationTargetDao().upsert(it) }
                recurringRules.forEach { db.recurringRuleDao().insert(it) }
                db.settingsDao().insert(backup.settings ?: Settings())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("文件无法识别：${e.message}"))
        }
    }

    /** 读取文件摘要，供覆盖恢复前的确认对话框展示。 */
    fun summarize(json: String): Result<BackupSummary> = runCatching {
        require(json.isNotBlank()) { "备份文件为空" }
        val backup = gson.fromJson(normalizeLegacyJson(json), BackupData::class.java)
            ?: throw IllegalArgumentException("备份文件为空")
        require(backup.version == 0 || backup.version <= CURRENT_BACKUP_VERSION) {
            "备份版本 ${backup.version} 高于当前版本 $CURRENT_BACKUP_VERSION，请升级应用后再恢复"
        }
        BackupSummary(
            version = backup.version,
            exportedAt = backup.exportedAt,
            accountCount = backup.accounts.orEmpty().size,
            assetCount = backup.assetRecords.orEmpty().size + backup.positions.orEmpty().size,
            transactionCount = backup.transactions.orEmpty().size,
            snapshotCount = backup.assetSnapshots.orEmpty().size,
            recurringRuleCount = backup.recurringRules.orEmpty().size
        )
    }

    private fun Account.normalized(): Account = copy(
        name = name.orEmpty().trim(),
        currency = currency.orEmpty().trim().uppercase().ifBlank { "CNY" }
    )

    private fun AssetRecord.normalized(accounts: List<Account>): AssetRecord {
        val account = accounts.firstOrNull { it.id == accountId }
            ?: throw IllegalArgumentException("资产记录关联了不存在的账户")
        return copy(
            id = id.orEmpty().ifBlank { UUID.randomUUID().toString() },
            accountId = accountId.orEmpty(),
            name = name.orEmpty().trim(),
            securityCode = securityCode.orEmpty().trim(),
            currency = currency.orEmpty().trim().uppercase().ifBlank { account.currency },
            subCategory = subCategory.orEmpty(),
            industryTag = industryTag.orEmpty(),
            sourceAccountId = sourceAccountId.orEmpty(),
            sourceRecordId = sourceRecordId.orEmpty(),
            importBatchId = importBatchId.orEmpty(),
            sourceFingerprint = sourceFingerprint.orEmpty()
        )
    }

    private fun Position.toAssetRecord(): AssetRecord = AssetRecord(
        id = id,
        accountId = accountId,
        assetType = AssetType.STOCK,
        riskBucket = RiskBucket.AGGRESSIVE,
        name = symbol.orEmpty(),
        securityCode = symbol.orEmpty(),
        quantity = shares,
        cost = totalCost,
        currentPrice = averageCost,
        currency = currency.orEmpty().trim().uppercase().ifBlank { "CNY" },
        createdAt = createdAt,
        updatedAt = createdAt,
        sourceType = HoldingSourceType.LEGACY_MIGRATION,
        sourceAccountId = accountId,
        sourceRecordId = id,
        importBatchId = "backup-migration",
        sourceFingerprint = "legacy:$accountId:${normalizeSecurityCode(symbol.orEmpty())}"
    )

    private fun Transaction.normalized(accounts: List<Account>): Transaction {
        val account = accounts.firstOrNull { it.id == accountId }
            ?: throw IllegalArgumentException("交易流水关联了不存在的账户")
        return copy(
            accountId = accountId.orEmpty(),
            symbol = symbol?.trim(),
            currency = currency.orEmpty().trim().uppercase().ifBlank { account.currency },
            note = note?.trim(),
            recordId = recordId?.trim(),
            sourceFingerprint = sourceFingerprint.orEmpty(),
            category = category,
            importBatchId = importBatchId.orEmpty()
        )
    }

    private fun validateFiniteAmounts(
        accounts: List<Account>,
        assetRecords: List<AssetRecord>,
        transactions: List<Transaction>,
        prices: List<Price>,
        priceHistory: List<PriceHistory>,
        snapshots: List<AssetSnapshot>,
        targets: List<AllocationTarget>,
        recurringRules: List<RecurringRule>
    ) {
        accounts.forEach { require(it.balance.isFinite()) { "账户余额非法" } }
        assetRecords.forEach {
            require(it.quantity.isFinite() && it.cost.isFinite() && it.currentPrice.isFinite()) { "资产金额非法" }
        }
        transactions.forEach {
            require(it.amount.isFinite() && (it.shares == null || it.shares.isFinite()) && (it.price == null || it.price.isFinite())) { "交易金额非法" }
        }
        prices.forEach { require(it.price.isFinite() && it.previousClose.isFinite()) { "价格缓存金额非法" } }
        priceHistory.forEach { require(it.price.isFinite() && it.cost.isFinite()) { "价格历史金额非法" } }
        snapshots.forEach {
            require(it.grossAssets.isFinite() && it.liabilities.isFinite() && it.netWorth.isFinite()) { "快照金额非法" }
        }
        targets.forEach { require(it.targetAmount.isFinite() && it.capAmount.isFinite()) { "目标金额非法" } }
        recurringRules.forEach { require(it.amount.isFinite()) { "周期规则金额非法" } }
    }

    /**
     * Gson bypasses Kotlin constructors, so defaults on newly added non-null fields are not
     * applied when reading an older backup. Materialize those defaults before deserialization.
     */
    private fun normalizeLegacyJson(json: String): String {
        @Suppress("DEPRECATION")
        val root = JsonParser().parse(json).asJsonObject
        root.getAsJsonArray("accounts")?.forEach { element ->
            element.asJsonObject.ensureString("type", "OTHER")
            element.asJsonObject.ensureString("currency", "CNY")
            element.asJsonObject.ensureString("sourceType", "MANUAL")
            element.asJsonObject.ensureString("externalSourceId", "")
            element.asJsonObject.ensureString("syncState", "NOT_APPLICABLE")
            element.asJsonObject.ensureNumber("initialPrincipal", 0)
            element.asJsonObject.ensureNumber("annualInterestRate", 0)
            element.asJsonObject.ensureNumber("dueDayOfMonth", 0)
            element.asJsonObject.ensureNumber("minimumPayment", 0)
        }
        root.getAsJsonArray("assetRecords")?.forEach { element ->
            element.asJsonObject.ensureString("assetType", "STOCK")
            normalizeRiskBucket(element.asJsonObject, "assetRecords")
            element.asJsonObject.ensureString("securityCode", "")
            element.asJsonObject.ensureString("subCategory", "")
            element.asJsonObject.ensureString("industryTag", "")
            element.asJsonObject.ensureString("sourceType", "MANUAL")
            element.asJsonObject.ensureString("sourceAccountId", "")
            element.asJsonObject.ensureString("sourceRecordId", "")
            element.asJsonObject.ensureString("importBatchId", "")
            element.asJsonObject.ensureString("sourceFingerprint", "")
        }
        root.getAsJsonArray("allocationTargets")?.forEach { element ->
            normalizeRiskBucket(element.asJsonObject, "allocationTargets")
        }
        root.getAsJsonArray("assetSnapshots")?.forEach { element ->
            val snapshot = element.asJsonObject
            snapshot.ensureNumber("grossAssets", snapshot.get("totalAssets")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0)
            snapshot.ensureNumber("liabilities", 0.0)
            snapshot.ensureNumber("netWorth", snapshot.get("totalAssets")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0)
            snapshot.ensureNumber("defensiveAssets", snapshot.get("cashAssets")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0)
            snapshot.ensureNumber("balancedAssets", 0.0)
            snapshot.ensureNumber("aggressiveAssets", snapshot.get("stockAssets")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0)
            snapshot.ensureNumber("strategyAssets", snapshot.get("totalAssets")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0)
            snapshot.ensureNumber("lockedAssets", 0.0)
            snapshot.ensureString("calculationVersion", "legacy-v1")
        }
        root.getAsJsonArray("transactions")?.forEach { element ->
            element.asJsonObject.ensureString("type", "DEPOSIT")
            element.asJsonObject.ensureString("origin", "LEGACY_MIGRATION")
            element.asJsonObject.ensureString("currency", "CNY")
            element.asJsonObject.ensureString("sourceFingerprint", "")
            element.asJsonObject.ensureString("category", "OTHER")
            element.asJsonObject.ensureString("importBatchId", "")
        }
        val settings = root.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also {
            root.add("settings", it)
        }
        settings.ensureNumber("id", 1)
        settings.ensureString("baseCurrency", "CNY")
        settings.ensureString("targetAllocation", "DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3")
        settings.ensureNumber("rebalanceThreshold", 0.05)
        settings.ensureBoolean("onboarded", false)
        settings.ensureBoolean("amountsVisible", true)
        settings.ensureNumber("maxAggressiveRatio", 0.70)
        settings.addProperty("targetAllocation", normalizeTargetAllocation(settings.get("targetAllocation").asString))
        return root.toString()
    }

    private fun normalizeRiskBucket(element: JsonObject, collection: String) {
        val raw = element.get("riskBucket")?.takeIf { it.isJsonPrimitive }?.asString ?: "AGGRESSIVE"
        val bucket = legacyBucketToThreeBucket(raw)
            ?: throw IllegalArgumentException("$collection 中存在未知风险桶 '$raw'")
        element.addProperty("riskBucket", bucket.name)
    }

    private fun normalizeTargetAllocation(value: String): String {
        if (value.isBlank()) return "DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3"
        val merged = linkedMapOf<String, Double>()
        val rawKeys = mutableSetOf<String>()
        value.split(',').forEach { pair ->
            val parts = pair.split(':', limit = 2)
            require(parts.size == 2) { "目标配置格式非法" }
            val rawKey = parts[0].trim().uppercase()
            val key = legacyBucketToThreeBucket(rawKey)?.name
                ?: throw IllegalArgumentException("目标配置包含未知桶 '$rawKey'")
            val duplicateAllowed =
                (rawKey == "CONSERVATIVE" && rawKeys.contains("INSURANCE")) ||
                    (rawKey == "INSURANCE" && rawKeys.contains("CONSERVATIVE"))
            require(key !in merged || duplicateAllowed) { "目标配置桶 '$key' 重复" }
            val ratio = parts[1].trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?: throw IllegalArgumentException("目标配置比例非法")
            merged[key] = (merged[key] ?: 0.0) + ratio
            rawKeys += rawKey
        }
        val normalized = listOf("DEFENSIVE", "BALANCED", "AGGRESSIVE")
            .mapNotNull { key -> merged[key]?.let { "$key:$it" } }
            .joinToString(",")
        val validation = validateTargetAllocation(normalized)
        require(validation.isValid) { validation.errors.joinToString("；") }
        return normalized
    }

    private fun JsonObject.ensureString(name: String, value: String) {
        if (!has(name) || get(name).isJsonNull) addProperty(name, value)
    }

    private fun JsonObject.ensureNumber(name: String, value: Number) {
        if (!has(name) || get(name).isJsonNull) addProperty(name, value)
    }

    private fun JsonObject.ensureBoolean(name: String, value: Boolean) {
        if (!has(name) || get(name).isJsonNull) addProperty(name, value)
    }
}
