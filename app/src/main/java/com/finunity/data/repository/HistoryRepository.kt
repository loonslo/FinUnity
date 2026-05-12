package com.finunity.data.repository

import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.model.PortfolioSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 历史分析仓库
 * 管理资产快照，计算收益和分析
 */
class HistoryRepository(private val database: AppDatabase) {

    /**
     * 保存当前资产状态为快照
     */
    suspend fun saveSnapshot(portfolio: PortfolioSummary): AssetSnapshot = withContext(Dispatchers.IO) {
        val totalCost = calculateTotalCost(portfolio.baseCurrency)
        val snapshot = AssetSnapshot(
            totalAssets = portfolio.totalAssets,
            cashAssets = portfolio.cashAssets,
            stockAssets = portfolio.stockAssets,
            stockRatio = portfolio.stockRatio,
            baseCurrency = portfolio.baseCurrency,
            totalCost = totalCost
        )
        database.assetSnapshotDao().insert(snapshot)
        snapshot
    }

    /**
     * 获取所有快照
     */
    fun getAllSnapshots(): Flow<List<AssetSnapshot>> {
        return database.assetSnapshotDao().getAllSnapshots()
    }

    /**
     * 获取最近 N 条快照
     */
    fun getRecentSnapshots(limit: Int = 30): Flow<List<AssetSnapshot>> {
        return database.assetSnapshotDao().getRecentSnapshots(limit)
    }

    /**
     * 获取指定时间段内的快照
     */
    fun getSnapshotsSince(startTime: Long): Flow<List<AssetSnapshot>> {
        return database.assetSnapshotDao().getSnapshotsSince(startTime)
    }

    /**
     * 计算累计收益
     */
    suspend fun calculateTotalReturn(): Double = withContext(Dispatchers.IO) {
        val latest = database.assetSnapshotDao().getLatestSnapshot() ?: return@withContext 0.0
        val totalCost = latest.totalCost
        if (totalCost <= 0) return@withContext 0.0
        (latest.totalAssets - totalCost) / totalCost
    }

    /**
     * 获取月度变化
     */
    suspend fun getMonthlyChange(): MonthlyChange? = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.add(Calendar.MONTH, -1)
        val oneMonthAgo = calendar.timeInMillis

        val snapshots = database.assetSnapshotDao().getSnapshotsSince(oneMonthAgo).first()
        if (snapshots.size < 2) return@withContext null

        val oldest = snapshots.first()
        val newest = snapshots.last()

        val assetChange = newest.totalAssets - oldest.totalAssets
        val percentageChange = if (oldest.totalAssets > 0) {
            (assetChange / oldest.totalAssets) * 100
        } else 0.0

        MonthlyChange(
            startAssets = oldest.totalAssets,
            endAssets = newest.totalAssets,
            change = assetChange,
            percentageChange = percentageChange,
            baseCurrency = newest.baseCurrency
        )
    }

    /**
     * 清理旧快照（保留最近2年）
     */
    suspend fun cleanupOldSnapshots() = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -2)
        val twoYearsAgo = calendar.timeInMillis
        database.assetSnapshotDao().deleteOlderThan(twoYearsAgo)
    }

    private suspend fun calculateTotalCost(baseCurrency: String): Double {
        val priceRepository = PriceRepository(database.priceDao())
        val positions = database.positionDao().getAllPositions().first()
        val assetRecords = database.assetRecordDao().getAllRecords().first()
        val positionsCost = positions.sumOf { position ->
            val exchangeRate = priceRepository.getExchangeRate(position.currency, baseCurrency) ?: 1.0
            position.totalCost * exchangeRate
        }
        val assetRecordsCost = assetRecords.sumOf { record ->
            val exchangeRate = priceRepository.getExchangeRate(record.currency, baseCurrency) ?: 1.0
            record.cost * exchangeRate
        }
        return positionsCost + assetRecordsCost
    }
}

/**
 * 月度变化数据
 */
data class MonthlyChange(
    val startAssets: Double,
    val endAssets: Double,
    val change: Double,
    val percentageChange: Double,
    val baseCurrency: String
)