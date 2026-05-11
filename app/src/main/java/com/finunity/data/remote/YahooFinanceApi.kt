package com.finunity.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Yahoo Finance API 接口
 * 用于获取股票价格和汇率
 */
interface YahooFinanceApi {

    /**
     * 获取单个股票价格
     * @param symbol 股票代码，如 "AAPL"
     * @param interval 价格间隔
     * @param includePrePost 是否包含盘前盘后
     */
    @GET("v8/finance/chart/")
    suspend fun getStockPrice(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("includePrePost") includePrePost: String = "false"
    ): YahooFinanceResponse

    /**
     * 获取汇率
     * @param from 源货币，如 "USD"
     * @param to 目标货币，如 "CNY"
     */
    @GET("v8/finance/chart/")
    suspend fun getExchangeRate(
        @Query("symbol") symbol: String,  // 如 "USDCNY=X"
        @Query("interval") interval: String = "1d"
    ): YahooFinanceResponse
}

data class YahooFinanceResponse(
    val chart: ChartResult?
)

data class ChartResult(
    val result: List<ChartData>?,
    val error: ChartError?
)

data class ChartData(
    val meta: StockMeta?
)

data class StockMeta(
    val symbol: String?,
    val regularMarketPrice: Double?,
    val currency: String?
)

data class ChartError(
    val code: String?,
    val description: String?
)
