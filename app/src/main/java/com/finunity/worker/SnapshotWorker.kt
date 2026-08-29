package com.finunity.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.model.PortfolioCalculator
import com.finunity.data.repository.HistoryRepository
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 资产快照 Worker
 * 每天自动保存资产快照，用于历史分析
 */
class SnapshotWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SnapshotWorker"
        private const val WORK_NAME = "asset_snapshot_worker"

        /**
         * 安排每日快照
         * 注意：这是补充性快照，与价格同步分离
         */
        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            // 每天执行一次
            val workRequest = PeriodicWorkRequestBuilder<SnapshotWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            Log.d(TAG, "Daily snapshot scheduled")
        }

        /**
         * 计算初始延迟，使任务在每天固定时间执行（如早上9点）
         */
        private fun calculateInitialDelay(): Long {
            val calendar = java.util.Calendar.getInstance()
            val now = calendar.timeInMillis

            // 设置目标时间为每天9:00
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)

            // 如果今天9点已过，则安排明天9点
            if (calendar.timeInMillis <= now) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            return calendar.timeInMillis - now
        }

        /**
         * 手动触发一次快照
         */
        fun snapshotNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SnapshotWorker>()
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)
            Log.d(TAG, "Manual snapshot triggered")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting asset snapshot")
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val priceRepository = PriceRepository(database.priceDao())
            val historyRepository = HistoryRepository(database)

            // 获取当前资产数据；快照与首页共用 PortfolioCalculator，避免两套统计口径。
            val accounts = database.accountDao().getAllAccounts().first()
            val assetRecords = database.assetRecordDao().getAllRecords().first()
            val settings = database.settingsDao().getSettingsOnce()

            if (accounts.isEmpty() && assetRecords.isEmpty()) {
                Log.d(TAG, "No assets to snapshot")
                return Result.success()
            }

            val baseCurrency = settings?.baseCurrency ?: "CNY"
            val calculator = PortfolioCalculator(
                accounts = accounts,
                positions = emptyList(),
                assetRecords = assetRecords,
                priceRepository = priceRepository,
                baseCurrency = baseCurrency
            )
            val totals = calculator.computePortfolioTotals()
            val lockedAssets = calculator.computeLockedValue()
            val totalCost = calculator.computeTotalCost()
            val defensiveAssets = totals.bucketValues[com.finunity.data.local.entity.RiskBucket.DEFENSIVE] ?: 0.0
            val balancedAssets = totals.bucketValues[com.finunity.data.local.entity.RiskBucket.BALANCED] ?: 0.0
            val aggressiveAssets = totals.bucketValues[com.finunity.data.local.entity.RiskBucket.AGGRESSIVE] ?: 0.0
            val stockRatio = if (totals.grossAssets > 0) aggressiveAssets / totals.grossAssets else 0.0

            val snapshot = com.finunity.data.local.entity.AssetSnapshot(
                totalAssets = totals.grossAssets,
                cashAssets = defensiveAssets,
                stockAssets = aggressiveAssets,
                stockRatio = stockRatio,
                baseCurrency = baseCurrency,
                totalCost = totalCost,
                notes = "自动快照",
                grossAssets = totals.grossAssets,
                liabilities = totals.liabilities,
                netWorth = totals.netWorth,
                defensiveAssets = defensiveAssets,
                balancedAssets = balancedAssets,
                aggressiveAssets = aggressiveAssets,
                strategyAssets = (totals.grossAssets - lockedAssets).coerceAtLeast(0.0),
                lockedAssets = lockedAssets,
                calculationVersion = "three-bucket-v1"
            )

            database.assetSnapshotDao().insert(snapshot)
            Log.d(TAG, "Asset snapshot saved: gross=${totals.grossAssets}, net=${totals.netWorth}, cost=$totalCost")

            // 清理旧快照（保留2年）
            historyRepository.cleanupOldSnapshots()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot failed: ${e.message}")
            Result.retry()
        }
    }
}
