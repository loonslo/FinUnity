package com.finunity.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Yahoo Finance API 接口
 * 用于获取股票价格和汇率
 *
 * 注意：Yahoo chart 接口的 symbol 是**路径参数**（/v8/finance/chart/AAPL），
 * 不是查询参数。早期写成 ?symbol= 会导致永远拿不到数据。
 */
interface YahooFinanceApi {

    /**
     * 获取单个股票价格
     * @param symbol 股票代码，如 "AAPL"、"600519.SS"、"0700.HK"
     * @param interval 价格间隔
     * @param range 取数范围
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getStockPrice(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "1d"
    ): YahooFinanceResponse

    /**
     * 获取汇率
     * @param symbol 汇率代码，如 "USDCNY=X"
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getExchangeRate(
        @Path(value = "symbol", encoded = true) symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "1d"
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
