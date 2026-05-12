package com.finunity.data.repository

import android.util.Log
import com.finunity.data.local.dao.PriceDao
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.PriceConfidence
import com.finunity.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批量刷新结果
 */
data class RefreshResult(
    val symbolsRefreshed: Int,
    val symbolsFailed: List<String>,
    val ratesRefreshed: Int,
    val ratesFailed: List<String>,
    val isPartialFailure: Boolean = symbolsFailed.isNotEmpty() || ratesFailed.isNotEmpty()
)

/**
 * 熔断器状态
 */
enum class CircuitState {
    CLOSED,   // 正常，允许请求
    OPEN,     // 熔断，拒绝请求（连续失败）
    HALF_OPEN // 半开，允许一个请求测试恢复
}

/**
 * 价格仓库
 * 统一管理价格数据：缓存优先，网络获取最新
 * 支持熔断器、批处理、过期回退
 */
class PriceRepository(
    private val priceDao: PriceDao
) {
    private val api = NetworkModule.yahooFinanceApi

    companion object {
        private const val TAG = "PriceRepository"
        private const val CIRCUIT_FAILURE_THRESHOLD = 5
        private const val CIRCUIT_RESET_TIMEOUT_MS = 5 * 60 * 1000L // 5分钟
    }

    // 熔断器状态
    private var circuitFailureCount = 0
    private var circuitOpenedAt = 0L

    /**
     * 熔断器是否允许请求
     */
    private fun isCircuitAllowingRequest(): Boolean {
        if (circuitFailureCount < CIRCUIT_FAILURE_THRESHOLD) return true

        // 检查是否超过恢复超时
        val elapsed = System.currentTimeMillis() - circuitOpenedAt
        return elapsed > CIRCUIT_RESET_TIMEOUT_MS
    }

    /**
     * 记录熔断失败
     */
    private fun recordCircuitFailure() {
        circuitFailureCount++
        if (circuitFailureCount >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenedAt = System.currentTimeMillis()
            Log.w(TAG, "Circuit breaker opened after $circuitFailureCount failures")
        }
    }

    /**
     * 重置熔断器
     */
    private fun resetCircuit() {
        circuitFailureCount = 0
    }

    /**
     * 获取股票价格
     * 优先返回缓存，缓存过期或不存在则从网络获取
     * 过期缓存会标记 isFallback=true
     */
    suspend fun getPrice(symbol: String): Price? = withContext(Dispatchers.IO) {
        // 1. 先查缓存
        val cached = priceDao.getPrice(symbol)
        if (cached != null && !cached.isStale()) {
            return@withContext cached
        }

        // 2. 缓存过期或不存在，检查熔断器
        if (!isCircuitAllowingRequest()) {
            Log.w(TAG, "Circuit breaker open, returning stale cache for $symbol")
            if (cached != null) {
                return@withContext cached.copy(isFallback = true)
            }
            return@withContext null
        }

        // 3. 从网络获取
        try {
            val response = api.getStockPrice(symbol)
            val result = response.chart?.result?.firstOrNull()
            val marketPrice = result?.meta?.regularMarketPrice

            if (marketPrice != null) {
                val price = Price(
                    symbol = symbol,
                    price = marketPrice,
                    currency = result.meta.currency ?: "USD",
                    updatedAt = System.currentTimeMillis(),
                    isFallback = false
                )
                priceDao.insert(price)
                resetCircuit() // 成功则重置熔断器
                return@withContext price
            } else {
                val errorDesc = result?.meta?.currency ?: "unknown"
                Log.w(TAG, "No price data for $symbol, currency: $errorDesc")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch price for $symbol: ${e.message}")
            recordCircuitFailure()
        }

        // 4. 网络失败，返回过期缓存（标记为回退）
        if (cached != null) {
            Log.d(TAG, "Using stale cache for $symbol (isFallback=true)")
            return@withContext cached.copy(isFallback = true)
        }

        return@withContext null
    }

    /**
     * 获取汇率
     */
    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String): Double? = withContext(Dispatchers.IO) {
        if (fromCurrency == toCurrency) {
            return@withContext 1.0
        }

        val symbol = "${fromCurrency}${toCurrency}=X"

        // 检查熔断器
        if (!isCircuitAllowingRequest()) {
            val cached = priceDao.getPrice(symbol)
            if (cached != null) return@withContext cached.price
            return@withContext null
        }

        val cached = priceDao.getPrice(symbol)
        if (cached != null && !cached.isStale()) {
            return@withContext cached.price
        }

        try {
            val response = api.getExchangeRate(symbol)
            val result = response.chart?.result?.firstOrNull()
            val rate = result?.meta?.regularMarketPrice

            if (rate != null) {
                val price = Price(
                    symbol = symbol,
                    price = rate,
                    currency = "${fromCurrency}/${toCurrency}",
                    updatedAt = System.currentTimeMillis(),
                    isFallback = false
                )
                priceDao.insert(price)
                resetCircuit()
                return@withContext rate
            }
        } catch (e: Exception) {
            recordCircuitFailure()
            if (cached != null) {
                return@withContext cached.price
            }
        }

        return@withContext null
    }

    /**
     * 批量获取股票价格
     */
    suspend fun getPrices(symbols: List<String>): Map<String, Price> = withContext(Dispatchers.IO) {
        symbols.associateWith { symbol ->
            getPrice(symbol)
        }.mapNotNull { (symbol, price) ->
            price?.let { symbol to it }
        }.toMap()
    }

    /**
     * 批量刷新所有价格（用于 WorkManager）
     * 分批处理，每个符号独立重试
     */
    suspend fun refreshAllPrices(symbols: List<String>, rates: Map<String, Double>): RefreshResult = withContext(Dispatchers.IO) {
        val symbolsFailed = mutableListOf<String>()
        var symbolsRefreshed = 0

        // 分批处理：每批 5 个符号，避免触发速率限制
        val batchSize = 5
        for (i in symbols.indices step batchSize) {
            val batch = symbols.subList(i, minOf(i + batchSize, symbols.size))
            for (symbol in batch) {
                try {
                    val response = api.getStockPrice(symbol)
                    val result = response.chart?.result?.firstOrNull()
                    val marketPrice = result?.meta?.regularMarketPrice

                    if (marketPrice != null) {
                        val price = Price(
                            symbol = symbol,
                            price = marketPrice,
                            currency = result.meta.currency ?: "USD",
                            updatedAt = System.currentTimeMillis(),
                            isFallback = false
                        )
                        priceDao.insert(price)
                        symbolsRefreshed++
                    } else {
                        symbolsFailed.add(symbol)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh price for $symbol: ${e.message}")
                    symbolsFailed.add(symbol)
                    recordCircuitFailure()
                }
            }
        }

        // 刷新汇率
        val ratesFailed = mutableListOf<String>()
        var ratesRefreshed = 0
        for ((currencyPair, _) in rates) {
            try {
                val symbol = "${currencyPair}=X"
                val response = api.getExchangeRate(symbol)
                val result = response.chart?.result?.firstOrNull()
                val exchangeRate = result?.meta?.regularMarketPrice

                if (exchangeRate != null) {
                    val price = Price(
                        symbol = symbol,
                        price = exchangeRate,
                        currency = currencyPair,
                        updatedAt = System.currentTimeMillis(),
                        isFallback = false
                    )
                    priceDao.insert(price)
                    ratesRefreshed++
                } else {
                    ratesFailed.add(currencyPair)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh rate for $currencyPair: ${e.message}")
                ratesFailed.add(currencyPair)
                recordCircuitFailure()
            }
        }

        RefreshResult(
            symbolsRefreshed = symbolsRefreshed,
            symbolsFailed = symbolsFailed,
            ratesRefreshed = ratesRefreshed,
            ratesFailed = ratesFailed,
            isPartialFailure = symbolsFailed.isNotEmpty() || ratesFailed.isNotEmpty()
        )
    }

    /**
     * 获取价格置信度
     */
    suspend fun getPriceConfidence(symbol: String): PriceConfidence? = withContext(Dispatchers.IO) {
        val cached = priceDao.getPrice(symbol)
        cached?.confidence()
    }
}
