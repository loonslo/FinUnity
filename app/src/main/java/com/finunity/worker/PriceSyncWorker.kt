package com.finunity.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Settings
import com.finunity.data.model.normalizeSecurityCode
import com.finunity.data.repository.PriceRepository
import com.finunity.data.repository.MarketAssetRequest
import com.finunity.data.remote.FinUnityServiceException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 价格同步 Worker
 * 每日执行一次，通过 FinUnity 专属服务刷新股票、ETF、基金净值和汇率。
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

            // 优先使用显式证券编码；旧记录回退到名称，兼容历史数据。
            val tradableTypes = listOf(AssetType.STOCK.name, AssetType.ETF.name, AssetType.FUND.name)
            val tradableRecords = database.assetRecordDao().getRecordsByTypes(tradableTypes)
            val marketRequests = tradableRecords.map { record ->
                MarketAssetRequest(
                    clientRef = record.id,
                    code = normalizeSecurityCode(record.securityCode.ifBlank { record.name }),
                    assetType = record.assetType.name,
                    instrumentId = record.instrumentId.takeIf(String::isNotBlank)
                )
            }
            val allSymbols = marketRequests.map { it.code }.distinct()
            Log.d(TAG, "Found ${marketRequests.size} service market requests, total ${allSymbols.size} symbols")

            // 获取所有账户的货币类型，构建汇率刷新列表
            val accounts = database.accountDao().getAllAccounts().first()
            val settings = database.settingsDao().getSettingsOnce() ?: Settings()
            val baseCurrency = settings.baseCurrency

            // 收集所有涉及的币种：账户币种 + 资产记录币种 + 持仓币种
            val currencies = (accounts.map { it.currency } +
                    tradableRecords.map { it.currency })
                .distinct()
                .filter { it != baseCurrency }
            val currencyPairs = currencies.map { "${it}${baseCurrency}" }.toSet()

            // 单次请求专属服务，由服务端聚合行情、基金净值和汇率。
            val result = priceRepository.refreshMarketData(marketRequests, currencyPairs, baseCurrency)
            Log.d(
                TAG,
                "Price sync result: symbols ${result.symbolsRefreshed}/${allSymbols.size}, " +
                    "rates ${result.ratesRefreshed}/${currencyPairs.size}, " +
                    "failedSymbols=${result.symbolsFailed}, failedRates=${result.ratesFailed}, " +
                    "failureCodes=${result.failureCodes}, requestId=${result.requestId}"
            )

            // 为股票/ETF/基金 AssetRecord 保存价格历史
            saveAssetRecordPriceHistory(
                database,
                priceRepository,
                tradableRecords,
                result.successfulSymbols
            )

            if (!result.isPartialFailure) {
                Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS - 1) {
                // 成功项已经落库，失败项保留旧缓存并让 WorkManager 使用统一指数退避重试。
                Result.retry()
            } else {
                // 达到上限不能伪报成功；旧价格仍保留在 prices 表中供离线展示。
                Log.e(TAG, "Price sync reached retry limit; old cache is retained")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Price sync failed: ${e.message}")
            val retryable = (e as? FinUnityServiceException)?.retryable ?: true
            if (!retryable) {
                Log.e(TAG, "Non-retryable service error; WorkManager will not retry")
                Result.failure()
            } else if (runAttemptCount < MAX_ATTEMPTS - 1) {
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
        tradableRecords: List<AssetRecord>,
        successfulSymbols: Set<String>
    ) {
        try {
            Log.d(TAG, "Found ${tradableRecords.size} tradable asset records for price history")

            for (record in tradableRecords) {
                try {
                    val securityCode = normalizeSecurityCode(record.securityCode.ifBlank { record.name })
                    if (securityCode !in successfulSymbols) continue
                    val price = priceRepository.getPrice(securityCode)
                    if (price != null && price.price > 0 && !price.isFallback) {
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
                            instrumentId = price.instrumentId.ifBlank { record.instrumentId },
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
        /**
         * 安排定期价格同步
         * 每天执行一次（符合"股票每日更新一次"的产品定位），需要网络，指数退避重试。
         * 使用 UPDATE 策略，确保从旧的 15 分钟周期升级到每日周期。
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PriceSyncWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
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
