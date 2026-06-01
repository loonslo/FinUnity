package com.finunity.data.repository

import androidx.room.withTransaction
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Settings
import com.finunity.data.local.entity.Transaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 备份数据结构
 */
data class BackupData(
    val version: Int = 2,  // v2: 增加 priceHistory, assetSnapshots
    val exportedAt: Long = System.currentTimeMillis(),
    val accounts: List<Account>,
    val assetRecords: List<AssetRecord>,
    val positions: List<Position>,
    val transactions: List<Transaction>,
    val priceHistory: List<PriceHistory> = emptyList(),
    val assetSnapshots: List<AssetSnapshot> = emptyList(),
    val settings: Settings
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
        val priceHistory = db.priceHistoryDao().getAllHistory().first()
        val assetSnapshots = db.assetSnapshotDao().getAllSnapshots().first()
        val settings = db.settingsDao().getSettingsOnce() ?: Settings()

        val backup = BackupData(
            accounts = accounts,
            assetRecords = assetRecords,
            positions = positions,
            transactions = transactions,
            priceHistory = priceHistory,
            assetSnapshots = assetSnapshots,
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
            val backup = gson.fromJson(json, BackupData::class.java)

            db.withTransaction {
                // 按外键依赖逆序清空：交易 → 价格历史 → 资产记录 → 持仓 → 账户
                db.transactionDao().deleteAll()
                db.priceHistoryDao().deleteAll()
                db.assetRecordDao().deleteAll()
                db.positionDao().deleteAll()
                db.accountDao().deleteAll()

                // 按外键依赖顺序插入：账户 → 资产记录/持仓 → 交易 → 价格历史
                backup.accounts.forEach { db.accountDao().insert(it) }
                backup.assetRecords.forEach { db.assetRecordDao().insert(it) }
                backup.positions.forEach { db.positionDao().insert(it) }
                backup.transactions.forEach { db.transactionDao().insert(it) }
                backup.priceHistory.forEach { db.priceHistoryDao().insert(it) }
                backup.assetSnapshots.forEach { db.assetSnapshotDao().insert(it) }
                db.settingsDao().update(backup.settings)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("文件无法识别：${e.message}"))
        }
    }
}
