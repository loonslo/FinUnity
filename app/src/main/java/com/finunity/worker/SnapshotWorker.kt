package com.finunity.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
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

            // 获取当前资产数据（包含旧 Position 和新 AssetRecord）
            val accounts = database.accountDao().getAllAccounts().first()
            val positions = database.positionDao().getAllPositions().first()
            val assetRecords = database.assetRecordDao().getAllRecords().first()
            val settings = database.settingsDao().getSettingsOnce()

            if (accounts.isEmpty() && positions.isEmpty() && assetRecords.isEmpty()) {
                Log.d(TAG, "No assets to snapshot")
                return Result.success()
            }

            // 计算总资产
            val baseCurrency = settings?.baseCurrency ?: "CNY"
            var totalCash = 0.0
            var totalStockValue = 0.0

            // 计算现金（账户余额），负债账户扣减
            for (account in accounts) {
                val rate = priceRepository.getExchangeRate(account.currency, baseCurrency) ?: 1.0
                if (account.type == com.finunity.data.local.entity.AccountType.LIABILITY) {
                    totalCash -= account.balance * rate  // 负债减少总资产
                } else {
                    totalCash += account.balance * rate
                }
            }

            // 计算旧 Position 持仓市值
            for (position in positions) {
                val currency = position.currency
                val currentPrice = priceRepository.getPrice(position.symbol)?.price ?: position.averageCost
                val exchangeRate = priceRepository.getExchangeRate(currency, baseCurrency) ?: 1.0
                totalStockValue += position.shares * currentPrice * exchangeRate
            }

            // 计算 AssetRecord 市值，按资产类型分类
            for (record in assetRecords) {
                val exchangeRate = priceRepository.getExchangeRate(record.currency, baseCurrency) ?: 1.0
                val valueInBase = record.currentValue * exchangeRate
                when (record.assetType) {
                    AssetType.STOCK, AssetType.ETF, AssetType.FUND -> totalStockValue += valueInBase
                    AssetType.CASH, AssetType.TIME_DEPOSIT -> totalCash += valueInBase
                }
            }

            val totalAssets = totalCash + totalStockValue
            val stockRatio = if (totalAssets > 0) totalStockValue / totalAssets else 0.0

            // 总成本 = 旧持仓成本 + 资产记录成本（按基准货币汇率换算）
            val positionsCost = positions.sumOf { position ->
                val exchangeRate = priceRepository.getExchangeRate(position.currency, baseCurrency) ?: 1.0
                position.totalCost * exchangeRate
            }
            val assetRecordsCost = assetRecords.sumOf { record ->
                val exchangeRate = priceRepository.getExchangeRate(record.currency, baseCurrency) ?: 1.0
                record.cost * exchangeRate
            }
            val totalCost = positionsCost + assetRecordsCost

            val snapshot = com.finunity.data.local.entity.AssetSnapshot(
                totalAssets = totalAssets,
                cashAssets = totalCash,
                stockAssets = totalStockValue,
                stockRatio = stockRatio,
                baseCurrency = baseCurrency,
                totalCost = totalCost,
                notes = "自动快照"
            )

            database.assetSnapshotDao().insert(snapshot)
            Log.d(TAG, "Asset snapshot saved: total=$totalAssets, cost=$totalCost")

            // 清理旧快照（保留2年）
            historyRepository.cleanupOldSnapshots()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot failed: ${e.message}")
            Result.retry()
        }
    }
}