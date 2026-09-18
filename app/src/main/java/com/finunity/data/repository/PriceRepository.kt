package com.finunity.data.repository

import com.finunity.data.local.dao.PriceDao
import com.finunity.data.local.entity.Price
import com.finunity.data.local.entity.PriceConfidence
import com.finunity.data.remote.ApiEnvelope
import com.finunity.data.remote.FxPairRequest
import com.finunity.data.remote.MarketSyncData
import com.finunity.data.remote.MarketSyncInstrumentRequest
import com.finunity.data.remote.MarketSyncRequest
import com.finunity.data.remote.NetworkModule
import com.finunity.data.remote.requireData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class RefreshResult(
    val symbolsRefreshed: Int,
    val symbolsFailed: List<String>,
    val ratesRefreshed: Int,
    val ratesFailed: List<String>,
    val isPartialFailure: Boolean = symbolsFailed.isNotEmpty() || ratesFailed.isNotEmpty(),
    val successfulSymbols: Set<String> = emptySet(),
    val successfulRates: Set<String> = emptySet(),
    val failureCodes: Map<String, String> = emptyMap(),
    val requestId: String = ""
)

data class MarketAssetRequest(
    val clientRef: String,
    val code: String,
    val assetType: String,
    val instrumentId: String? = null
)

fun interface MarketSyncSource {
    suspend fun sync(request: MarketSyncRequest): ApiEnvelope<MarketSyncData>
}

object FinUnityMarketSyncSource : MarketSyncSource {
    override suspend fun sync(request: MarketSyncRequest): ApiEnvelope<MarketSyncData> =
        NetworkModule.authorized { it.syncMarket(request) }
}

/**
 * FinUnity 专属服务行情仓库。
 *
 * 缓存读取不会隐式发起网络请求；只有显式刷新才调用 /market/sync。
 * 服务报错直接向上抛出，绝不生成 0、成本价或 1:1 汇率作为假数据。
 */
class PriceRepository(
    private val priceDao: PriceDao,
    private val source: MarketSyncSource = FinUnityMarketSyncSource
) {
    suspend fun getPrice(symbol: String): Price? = withContext(Dispatchers.IO) {
        priceDao.getPrice(symbol)?.let { cached ->
            if (cached.isStale()) cached.copy(isFallback = true) else cached
        }
    }

    suspend fun getExchangeRate(fromCurrency: String, toCurrency: String): Double? =
        withContext(Dispatchers.IO) {
            val from = fromCurrency.trim().uppercase(Locale.ROOT)
            val to = toCurrency.trim().uppercase(Locale.ROOT)
            if (from == to) return@withContext 1.0
            val cached = priceDao.getPrice("${from}${to}=X") ?: return@withContext null
            if (cached.isStale() || cached.errorCode != null || cached.isFallback) return@withContext null
            cached.price
        }

    suspend fun getPrices(symbols: List<String>): Map<String, Price> = withContext(Dispatchers.IO) {
        symbols.distinct().mapNotNull { symbol -> getPrice(symbol)?.let { symbol to it } }.toMap()
    }

    suspend fun refreshMarketData(
        instruments: List<MarketAssetRequest>,
        currencyPairs: Set<String>,
        baseCurrency: String
    ): RefreshResult = withContext(Dispatchers.IO) {
        val uniqueInstruments = instruments
            .filter { it.code.isNotBlank() }
            .distinctBy { listOf(it.instrumentId.orEmpty(), it.code, it.assetType).joinToString("|") }
        val normalizedPairs = currencyPairs.map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.length == 6 }
            .distinct()

        if (uniqueInstruments.isEmpty() && normalizedPairs.isEmpty()) {
            return@withContext RefreshResult(0, emptyList(), 0, emptyList())
        }

        val request = MarketSyncRequest(
            clientRequestId = UUID.randomUUID().toString(),
            baseCurrency = baseCurrency.uppercase(Locale.ROOT),
            instruments = uniqueInstruments.map {
                MarketSyncInstrumentRequest(
                    clientRef = it.clientRef,
                    instrumentId = it.instrumentId?.takeIf(String::isNotBlank),
                    inputCode = it.code,
                    assetType = it.assetType
                )
            },
            fxPairs = normalizedPairs.map {
                FxPairRequest(clientRef = it, base = it.take(3), quote = it.drop(3))
            }
        )

        val envelope = source.sync(request)
        val data = envelope.requireData()
        val failureCodes = linkedMapOf<String, String>()
        val successfulSymbols = linkedSetOf<String>()
        val requestedByRef = uniqueInstruments.associateBy { it.clientRef }
        val returnedInstrumentRefs = mutableSetOf<String>()

        data.instruments.forEach { result ->
            returnedInstrumentRefs += result.clientRef
            val requested = requestedByRef[result.clientRef] ?: return@forEach
            val serviceValue = result.value
            val numericValue = serviceValue?.current.toStrictPositiveDecimal()
            val sourceTime = parseInstant(serviceValue?.sourceTime)
            val receivedAt = parseInstant(serviceValue?.receivedAt)
            val previousClose = serviceValue?.previousClose.toNullableDecimal()
            val contractError = when {
                result.status != "OK" -> result.error?.code ?: result.status
                result.instrument == null -> "MISSING_INSTRUMENT"
                numericValue == null -> "INVALID_VALUE"
                serviceValue?.previousClose != null && previousClose == null -> "INVALID_PREVIOUS_CLOSE"
                sourceTime == null -> "INVALID_SOURCE_TIME"
                receivedAt == null -> "INVALID_RECEIVED_AT"
                result.instrument.currency.isBlank() -> "MISSING_CURRENCY"
                serviceValue?.valueType.isNullOrBlank() -> "MISSING_VALUE_TYPE"
                serviceValue?.quality.isNullOrBlank() -> "MISSING_QUALITY"
                serviceValue?.source.isNullOrBlank() -> "MISSING_SOURCE"
                else -> null
            }
            if (contractError != null) {
                val code = result.error?.code
                    ?: contractError
                failureCodes[requested.code] = code
                markCachedFailure(requested.code, code)
                return@forEach
            }

            val price = Price(
                symbol = requested.code,
                price = numericValue!!,
                previousClose = previousClose ?: 0.0,
                currency = result.instrument!!.currency.uppercase(Locale.ROOT),
                updatedAt = receivedAt!!,
                isFallback = false,
                instrumentId = result.instrument.instrumentId,
                source = serviceValue!!.source,
                sourceTime = sourceTime,
                receivedAt = receivedAt,
                quality = serviceValue.quality,
                valueType = serviceValue.valueType,
                errorCode = null
            )
            priceDao.insert(price)
            successfulSymbols += requested.code
        }

        uniqueInstruments.filter { it.clientRef !in returnedInstrumentRefs }.forEach {
            failureCodes[it.code] = "MISSING_ITEM"
            markCachedFailure(it.code, "MISSING_ITEM")
        }

        val successfulRates = linkedSetOf<String>()
        val returnedRateRefs = mutableSetOf<String>()
        data.fxRates.forEach { result ->
            returnedRateRefs += result.clientRef
            val numericRate = result.rate.toStrictPositiveDecimal()
            val sourceTime = parseInstant(result.sourceTime)
            val receivedAt = parseInstant(result.receivedAt)
            val contractError = when {
                result.status != "OK" -> result.error?.code ?: result.status
                numericRate == null -> "INVALID_VALUE"
                sourceTime == null -> "INVALID_SOURCE_TIME"
                receivedAt == null -> "INVALID_RECEIVED_AT"
                result.pair.isBlank() -> "MISSING_PAIR"
                result.quality.isBlank() -> "MISSING_QUALITY"
                result.source.isBlank() -> "MISSING_SOURCE"
                else -> null
            }
            if (contractError != null) {
                val code = result.error?.code
                    ?: contractError
                failureCodes[result.clientRef] = code
                markCachedFailure("${result.clientRef}=X", code)
                return@forEach
            }
            priceDao.insert(
                Price(
                    symbol = "${result.clientRef}=X",
                    price = numericRate!!,
                    currency = result.pair,
                    updatedAt = receivedAt!!,
                    isFallback = false,
                    source = result.source,
                    sourceTime = sourceTime,
                    receivedAt = receivedAt,
                    quality = result.quality,
                    valueType = "FX_RATE",
                    errorCode = null
                )
            )
            successfulRates += result.clientRef
        }
        normalizedPairs.filter { it !in returnedRateRefs }.forEach {
            failureCodes[it] = "MISSING_ITEM"
            markCachedFailure("${it}=X", "MISSING_ITEM")
        }

        val symbolFailures = uniqueInstruments.map { it.code }.filter { it !in successfulSymbols }
        val rateFailures = normalizedPairs.filter { it !in successfulRates }
        RefreshResult(
            symbolsRefreshed = successfulSymbols.size,
            symbolsFailed = symbolFailures,
            ratesRefreshed = successfulRates.size,
            ratesFailed = rateFailures,
            successfulSymbols = successfulSymbols,
            successfulRates = successfulRates,
            failureCodes = failureCodes,
            requestId = envelope.requestId
        )
    }

    /** Compatibility entry for callers without asset type metadata. */
    suspend fun refreshAllPrices(symbols: List<String>, rates: Map<String, Double>): RefreshResult =
        refreshMarketData(
            instruments = symbols.distinct().map { MarketAssetRequest(it, it, "STOCK") },
            currencyPairs = rates.keys,
            baseCurrency = rates.keys.firstOrNull()?.takeLast(3) ?: "CNY"
        )

    suspend fun getPriceConfidence(symbol: String): PriceConfidence? = withContext(Dispatchers.IO) {
        priceDao.getPrice(symbol)?.confidence()
    }

    private suspend fun markCachedFailure(symbol: String, errorCode: String) {
        priceDao.getPrice(symbol)?.let { cached ->
            priceDao.insert(cached.copy(isFallback = true, errorCode = errorCode))
        }
    }

    private fun String?.toStrictPositiveDecimal(): Double? = this?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

    private fun String?.toNullableDecimal(): Double? = this?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }

    private fun parseInstant(value: String?): Long? = try {
        value?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: Exception) {
        null
    }
}
