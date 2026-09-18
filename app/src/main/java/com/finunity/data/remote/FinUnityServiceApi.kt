package com.finunity.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface FinUnityServiceApi {
    @POST("auth/session")
    suspend fun createSession(@Body request: SessionRequest): ApiEnvelope<SessionData>

    @POST("auth/session:refresh")
    suspend fun refreshSession(@Body request: RefreshSessionRequest): ApiEnvelope<SessionData>

    @GET("app/config")
    suspend fun getAppConfig(
        @Query("platform") platform: String = "ANDROID",
        @Query("version_code") versionCode: Int
    ): ApiEnvelope<AppConfigData>

    @GET("instruments/search")
    suspend fun searchInstruments(
        @Query("q") query: String,
        @Query("types") types: String? = null,
        @Query("markets") markets: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): ApiEnvelope<InstrumentSearchData>

    @POST("instruments/resolve")
    suspend fun resolveInstruments(@Body request: ResolveInstrumentsRequest): ApiEnvelope<ResolveInstrumentsData>

    @POST("market/sync")
    suspend fun syncMarket(@Body request: MarketSyncRequest): ApiEnvelope<MarketSyncData>

    @Multipart
    @POST("ocr/holdings:parse")
    suspend fun parseHoldingScreenshot(
        @Part image: MultipartBody.Part,
        @Part("client_request_id") clientRequestId: RequestBody,
        @Part("locale") locale: RequestBody,
        @Part("default_currency") defaultCurrency: RequestBody?,
        @Part("broker_hint") brokerHint: RequestBody?,
        @Part("parse_mode") parseMode: RequestBody
    ): ApiEnvelope<OcrHoldingsData>

    @DELETE("ocr/documents/{documentId}")
    suspend fun deleteOcrDocument(
        @Path("documentId") documentId: String
    ): ApiEnvelope<DeleteOcrDocumentData>
}

data class ApiEnvelope<T>(
    val requestId: String = "",
    val data: T? = null,
    val warnings: List<ApiWarning> = emptyList(),
    val partial: Boolean = false,
    val serverTime: String? = null,
    val error: ApiError? = null
)

data class ApiWarning(val code: String = "", val message: String = "")

data class ApiError(
    val code: String = "UNKNOWN",
    val message: String = "",
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null,
    val details: List<String> = emptyList()
)

data class SessionRequest(
    val installationId: String,
    val platform: String = "ANDROID",
    val appVersion: String,
    val versionCode: Int,
    val deviceLocale: String,
    val integrityToken: String? = null
)

data class RefreshSessionRequest(val refreshToken: String)

data class SessionData(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long
)

data class AppConfigData(
    val minimumSupportedVersionCode: Int = 1,
    val latestVersionCode: Int = 1,
    val maintenance: Boolean = false,
    val features: Map<String, Boolean> = emptyMap(),
    val privacyPolicyUrl: String? = null
)

data class ResolveInstrumentsRequest(val queries: List<ResolveInstrumentQuery>)

data class InstrumentSearchData(
    val items: List<ServiceInstrument> = emptyList(),
    val nextCursor: String? = null
)

data class ResolveInstrumentQuery(
    val clientRef: String,
    val text: String,
    val typeHint: String? = null,
    val marketHint: String? = null
)

data class ResolveInstrumentsData(val items: List<ResolvedInstrumentItem> = emptyList())

data class ResolvedInstrumentItem(
    val clientRef: String,
    val status: String,
    val instrument: ServiceInstrument? = null,
    val confidence: Double? = null,
    val candidates: List<ServiceInstrument> = emptyList(),
    val reviewReasons: List<String> = emptyList()
)

data class ServiceInstrument(
    val instrumentId: String,
    val symbol: String? = null,
    val canonicalSymbol: String,
    val name: String,
    val shortName: String? = null,
    val type: String,
    val market: String,
    val currency: String,
    val status: String = "UNKNOWN",
    val fundCode: String? = null
)

data class MarketSyncRequest(
    val clientRequestId: String,
    val baseCurrency: String,
    val instruments: List<MarketSyncInstrumentRequest>,
    val fxPairs: List<FxPairRequest>,
    val include: List<String> = listOf("LATEST_VALUE", "PREVIOUS_CLOSE", "BASIC_METADATA"),
    val allowDelayed: Boolean = true
)

data class MarketSyncInstrumentRequest(
    val clientRef: String,
    val instrumentId: String? = null,
    val inputCode: String,
    val assetType: String
)

data class FxPairRequest(val clientRef: String, val base: String, val quote: String)

data class MarketSyncData(
    val instruments: List<MarketSyncInstrumentResult> = emptyList(),
    val fxRates: List<FxRateResult> = emptyList()
)

data class MarketSyncInstrumentResult(
    val clientRef: String,
    val status: String,
    val instrument: ServiceInstrument? = null,
    val value: ServiceMarketValue? = null,
    val error: ApiError? = null
)

data class ServiceMarketValue(
    val valueType: String,
    val current: String?,
    val previousClose: String? = null,
    val valueDate: String? = null,
    val sourceTime: String? = null,
    val receivedAt: String? = null,
    val quality: String = "UNKNOWN",
    val delaySeconds: Long? = null,
    val source: String = "FINUNITY"
)

data class FxRateResult(
    val clientRef: String,
    val status: String,
    val pair: String,
    val rate: String? = null,
    val rateType: String? = null,
    val sourceTime: String? = null,
    val receivedAt: String? = null,
    val quality: String = "UNKNOWN",
    val source: String = "FINUNITY",
    val error: ApiError? = null
)

data class OcrHoldingsData(
    val documentId: String,
    val documentType: String = "HOLDINGS",
    val imageSha256: String? = null,
    val asOfDate: String? = null,
    val rows: List<OcrHoldingRow> = emptyList(),
    val warnings: List<ApiWarning> = emptyList()
)

data class DeleteOcrDocumentData(val deleted: Boolean)

data class OcrHoldingRow(
    val rowId: String,
    val rawText: String = "",
    val name: String? = null,
    val securityCode: OcrSecurityCode? = null,
    val assetType: String? = null,
    val quantity: String? = null,
    val currentPrice: String? = null,
    val marketValue: String? = null,
    val totalCost: String? = null,
    val currency: String? = null,
    val confidence: Double = 0.0,
    val fieldConfidence: Map<String, Double> = emptyMap(),
    val reviewReasons: List<String> = emptyList()
)

data class OcrSecurityCode(
    val raw: String? = null,
    val normalized: String? = null,
    val instrumentId: String? = null,
    val market: String? = null,
    val status: String = "NOT_FOUND",
    val confidence: Double = 0.0,
    val candidates: List<ServiceInstrument> = emptyList(),
    val needsConfirmation: Boolean = true
)
