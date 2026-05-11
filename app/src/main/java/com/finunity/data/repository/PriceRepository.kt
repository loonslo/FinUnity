package com.finunity.data.repository

import android.util.Log
import com.finunity.data.local.dao.PriceDao
import com.finunity.data.local.entity.Price
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
    val ratesFailed: List<String>
)

/**
 * 价格仓库
 * 统一管理价格数据：缓存优先，网络获取最新
 */
class PriceRepository(
    private val priceDao: PriceDao
) {
    private val api = NetworkModule.yahooFinanceApi

    companion object {
        private const val TAG = "PriceRepository"
    }

    /**
     * 获取股票价格
     * 优先返回缓存，缓存过期或不存在则从网络获取
     */
    suspend fun getPrice(symbol: String): Price? = withContext(Dispatchers.IO) {
        // 1. 先查缓存
        val cached = priceDao.getPrice(symbol)
        if (cached != null && !cached.isStale()) {
            return@withContext cached
        }

        // 2. 缓存过期或不存在，从网络获取
        try {
            val response = api.getStockPrice(symbol)
            val result = response.chart?.result?.firstOrNull()
            val marketPrice = result?.meta?.regularMarketPrice

            if (marketPrice != null) {
                val price = Price(
                    symbol = symbol,
                    price = marketPrice,
                    currency = result.meta.currency ?: "USD",
                    updatedAt = System.currentTimeMillis()
                )
                priceDao.insert(price)
                return@withContext price
            } else {
                // API 返回了但没有价格数据
                val errorDesc = result?.meta?.currency ?: "unknown"
                Log.w(TAG, "No price data for $symbol, currency: $errorDesc")
            }
        } catch (e: Exception) {
            // 网络失败，返回缓存（即使过期）
            Log.e(TAG, "Failed to fetch price for $symbol: ${e.message}")
            if (cached != null) {
                Log.d(TAG, "Using stale cache for $symbol")
                return@withContext cached
            }
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
                    updatedAt = System.currentTimeMillis()
                )
                priceDao.insert(price)
                return@withContext rate
            }
        } catch (e: Exception) {
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
     * 刷新所有价格（用于 WorkManager）
     */
    suspend fun refreshAllPrices(symbols: List<String>, rates: Map<String, Double>): RefreshResult = withContext(Dispatchers.IO) {
        val symbolsFailed = mutableListOf<String>()
        var symbolsRefreshed = 0

        symbols.forEach { symbol ->
            try {
                val response = api.getStockPrice(symbol)
                val result = response.chart?.result?.firstOrNull()
                val marketPrice = result?.meta?.regularMarketPrice

                if (marketPrice != null) {
                    val price = Price(
                        symbol = symbol,
                        price = marketPrice,
                        currency = result.meta.currency ?: "USD",
                        updatedAt = System.currentTimeMillis()
                    )
                    priceDao.insert(price)
                    symbolsRefreshed++
                } else {
                    symbolsFailed.add(symbol)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh price for $symbol: ${e.message}")
                symbolsFailed.add(symbol)
            }
        }

        // 刷新汇率
        val ratesFailed = mutableListOf<String>()
        var ratesRefreshed = 0
        rates.forEach { (currencyPair, _) ->
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
                        updatedAt = System.currentTimeMillis()
                    )
                    priceDao.insert(price)
                    ratesRefreshed++
                } else {
                    ratesFailed.add(currencyPair)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh rate for $currencyPair: ${e.message}")
                ratesFailed.add(currencyPair)
            }
        }

        RefreshResult(
            symbolsRefreshed = symbolsRefreshed,
            symbolsFailed = symbolsFailed,
            ratesRefreshed = ratesRefreshed,
            ratesFailed = ratesFailed
        )
    }
}
