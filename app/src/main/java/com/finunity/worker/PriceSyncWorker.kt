package com.finunity.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Settings
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 价格同步 Worker
 * 每5分钟执行一次，自动刷新股票价格并保存历史
 */
class PriceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting price sync")
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val priceRepository = PriceRepository(database.priceDao())

            // 获取所有持仓的股票代码
            val symbols = database.positionDao().getAllSymbols()

            // 获取股票/ETF/基金 AssetRecord 的名称作为代码
            val tradableTypes = listOf(AssetType.STOCK.name, AssetType.ETF.name, AssetType.FUND.name)
            val tradableRecords = database.assetRecordDao().getRecordsByTypes(tradableTypes)
            val assetRecordCodes = tradableRecords.map { it.name }

            // 合并所有需要刷新的代码
            val allSymbols = (symbols + assetRecordCodes).distinct()
            Log.d(TAG, "Found ${symbols.size} position symbols and ${assetRecordCodes.size} asset record codes, total ${allSymbols.size}")

            // 获取所有账户的货币类型，构建汇率刷新列表
            val accounts = database.accountDao().getAllAccounts().first()
            val settings = database.settingsDao().getSettingsOnce() ?: Settings()
            val baseCurrency = settings.baseCurrency

            // 收集所有涉及的币种：账户币种 + 资产记录币种 + 持仓币种
            val allPositions = database.positionDao().getAllPositions().first()
            val currencies = (accounts.map { it.currency } +
                    tradableRecords.map { it.currency } +
                    allPositions.map { it.currency })
                .distinct()
                .filter { it != baseCurrency }
            val rates = currencies.associate { "${it}${baseCurrency}" to 1.0 }

            // 批量刷新价格和汇率
            priceRepository.refreshAllPrices(allSymbols, rates)
            Log.d(TAG, "Price sync completed successfully")

            // 为股票/ETF/基金 AssetRecord 保存价格历史
            saveAssetRecordPriceHistory(database, priceRepository, tradableRecords)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Price sync failed: ${e.message}")
            if (runAttemptCount < MAX_ATTEMPTS - 1) {
                Result.retry()
            } else {
                Log.e(TAG, "Price sync failed after $MAX_ATTEMPTS attempts, giving up")
                Result.failure()
            }
        }
    }

    /**
     * 为股票/ETF/基金 AssetRecord 保存价格历史
     * 注意：tradableRecords 已在 doWork 中获取，此处直接使用
     */
    private suspend fun saveAssetRecordPriceHistory(
        database: AppDatabase,
        priceRepository: PriceRepository,
        tradableRecords: List<AssetRecord>
    ) {
        try {
            Log.d(TAG, "Found ${tradableRecords.size} tradable asset records for price history")

            for (record in tradableRecords) {
                try {
                    // 使用记录名称作为股票代码获取价格
                    val price = priceRepository.getPrice(record.name)
                    if (price != null && price.price > 0) {
                        // 保存价格历史：cost 存储单位成本（平均成本），与 unit price 对应
                        val priceHistory = PriceHistory(
                            recordId = record.id,
                            price = price.price,
                            cost = record.averageCost  // 单位成本/平均成本
                        )
                        database.priceHistoryDao().insert(priceHistory)

                        // 回写 AssetRecord 当前价格
                        val updated = record.copy(
                            currentPrice = price.price,
                            updatedAt = System.currentTimeMillis()
                        )
                        database.assetRecordDao().update(updated)
                        Log.d(TAG, "Saved price history for ${record.name}: ${price.price}, updated currentPrice")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save price history for ${record.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process asset record price history: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PriceSyncWorker"
        private const val WORK_NAME = "price_sync_worker"
        private const val MAX_ATTEMPTS = 3
        // 指数退避：1min, 5min, 15min
        private val BACKOFF_DELAYS = listOf(1L, 5L, 15L)

        /**
         * 安排定期价格同步
         * 每15分钟执行一次，指数退避重试
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PriceSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        /**
         * 立即执行一次同步（带指数退避）
         */
        fun syncNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<PriceSyncWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)
        }

        /**
         * 取消定期同步
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}
