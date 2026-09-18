package com.finunity.data.remote

import android.content.Context
import com.finunity.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val PREFS = "finunity_service_session"
    private const val ACCESS_TOKEN = "access_token"
    private const val ACCESS_EXPIRES_AT = "access_expires_at"
    private const val REFRESH_TOKEN = "refresh_token"
    private const val REFRESH_EXPIRES_AT = "refresh_expires_at"
    private const val INSTALLATION_ID = "installation_id"
    private const val EXPIRY_SKEW_MS = 60_000L

    @Volatile
    private var appContext: Context? = null
    private val sessionMutex = Mutex()
    private val responseGson: Gson by lazy {
        GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val context: Context
        get() = appContext ?: throw ServiceConfigurationException("FinUnity 专属服务尚未初始化")

    private val baseUrl: String
        get() {
            val configured = BuildConfig.FINUNITY_API_BASE_URL.trim()
            if (configured.isBlank()) {
                throw ServiceConfigurationException(
                    "FINUNITY_API_BASE_URL 未配置，无法请求 FinUnity 专属服务"
                )
            }
            if (!configured.startsWith("https://")) {
                throw ServiceConfigurationException("FINUNITY_API_BASE_URL 必须使用 HTTPS")
            }
            return if (configured.endsWith('/')) configured else "$configured/"
        }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            // 不记录 BODY，避免持仓、Token 或 OCR 内容进入日志。
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(ACCESS_TOKEN, null)
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("X-Request-ID", UUID.randomUUID().toString())
            .apply {
                if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }
            .build()
        chain.proceed(request)
    }

    private val apiInstance: FinUnityServiceApi by lazy {
        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FinUnityServiceApi::class.java)
    }

    val serviceApi: FinUnityServiceApi
        get() = apiInstance

    suspend fun <T> authorized(block: suspend (FinUnityServiceApi) -> T): T {
        try {
            ensureSession()
        } catch (error: HttpException) {
            throw error.toServiceException()
        }
        return try {
            block(serviceApi)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error.toServiceException()
            clearAccessToken()
            try {
                ensureSession(forceRefresh = true)
                block(serviceApi)
            } catch (retryError: HttpException) {
                throw retryError.toServiceException()
            }
        }
    }

    private fun HttpException.toServiceException(): FinUnityServiceException {
        val raw = response()?.errorBody()?.string()
        val parsed = runCatching {
            raw?.takeIf(String::isNotBlank)?.let {
                responseGson.fromJson(it, ApiEnvelope::class.java)
            }
        }.getOrNull()
        val parsedError = parsed?.error
        val requestId = parsed?.requestId?.takeIf(String::isNotBlank)
            ?: response()?.headers()?.get("X-Request-ID")
            ?: "http-${code()}"
        val retryAfter = parsedError?.retryAfterSeconds
            ?: response()?.headers()?.get("Retry-After")?.toLongOrNull()
        return FinUnityServiceException(
            code = parsedError?.code ?: "HTTP_${code()}",
            message = parsedError?.message?.takeIf(String::isNotBlank) ?: message(),
            retryable = parsedError?.retryable ?: (code() == 429 || code() >= 500),
            requestId = requestId,
            httpStatus = code(),
            retryAfterSeconds = retryAfter
        )
    }

    private suspend fun ensureSession(forceRefresh: Boolean = false) = sessionMutex.withLock {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val accessToken = prefs.getString(ACCESS_TOKEN, null)
        val accessExpiresAt = prefs.getLong(ACCESS_EXPIRES_AT, 0L)
        if (!forceRefresh && !accessToken.isNullOrBlank() && accessExpiresAt - EXPIRY_SKEW_MS > now) {
            return@withLock
        }

        val refreshToken = prefs.getString(REFRESH_TOKEN, null)
        val refreshExpiresAt = prefs.getLong(REFRESH_EXPIRES_AT, 0L)
        val envelope = if (!refreshToken.isNullOrBlank() && refreshExpiresAt > now) {
            try {
                serviceApi.refreshSession(RefreshSessionRequest(refreshToken))
            } catch (error: HttpException) {
                if (error.code() !in setOf(401, 403)) throw error
                createSession(prefs.getString(INSTALLATION_ID, null))
            }
        } else {
            createSession(prefs.getString(INSTALLATION_ID, null))
        }
        val session = envelope.requireData()
        val savedAt = System.currentTimeMillis()
        prefs.edit()
            .putString(ACCESS_TOKEN, session.accessToken)
            .putLong(ACCESS_EXPIRES_AT, savedAt + session.expiresInSeconds * 1000L)
            .putString(REFRESH_TOKEN, session.refreshToken)
            .putLong(REFRESH_EXPIRES_AT, savedAt + session.refreshExpiresInSeconds * 1000L)
            .apply()
    }

    private suspend fun createSession(existingInstallationId: String?): ApiEnvelope<SessionData> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installationId = existingInstallationId?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(INSTALLATION_ID, it).apply()
            }
        return serviceApi.createSession(
            SessionRequest(
                installationId = installationId,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                deviceLocale = Locale.getDefault().toLanguageTag()
            )
        )
    }

    private fun clearAccessToken() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(ACCESS_TOKEN)
            .remove(ACCESS_EXPIRES_AT)
            .apply()
    }
}

class ServiceConfigurationException(message: String) : IllegalStateException(message)

class FinUnityServiceException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val requestId: String,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null
) : IllegalStateException("[$code] $message (requestId=$requestId)")

fun <T> ApiEnvelope<T>.requireData(): T {
    if (requestId.isBlank()) {
        throw FinUnityServiceException(
            code = "MISSING_REQUEST_ID",
            message = "专属服务响应缺少 request_id",
            retryable = false,
            requestId = "missing"
        )
    }
    error?.let { throw FinUnityServiceException(it.code, it.message, it.retryable, requestId) }
    return data ?: throw FinUnityServiceException(
        code = "EMPTY_RESPONSE",
        message = "专属服务未返回 data",
        retryable = false,
        requestId = requestId
    )
}
