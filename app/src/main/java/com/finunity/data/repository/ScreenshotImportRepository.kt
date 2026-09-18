package com.finunity.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.finunity.data.model.OcrConfidence
import com.finunity.data.model.ParsedScreenshotHolding
import com.finunity.data.remote.NetworkModule
import com.finunity.data.remote.requireData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.UUID

/** Uploads one user-approved screenshot to the FinUnity dedicated service. */
class ScreenshotImportRepository(private val context: Context) {
    suspend fun recognize(
        uri: Uri,
        defaultCurrency: String? = null,
        brokerHint: String? = null
    ): List<ParsedScreenshotHolding> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase(Locale.ROOT)
            ?: throw IllegalArgumentException("无法确定图片格式")
        if (mimeType !in SUPPORTED_TYPES) {
            throw IllegalArgumentException("不支持的图片格式：$mimeType")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("无法读取图片尺寸")
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width < MIN_IMAGE_EDGE || height < MIN_IMAGE_EDGE) {
            throw IllegalArgumentException("图片尺寸过小，至少需要 480×480")
        }
        if (width.toLong() * height.toLong() > MAX_IMAGE_PIXELS) {
            throw IllegalArgumentException("图片像素超过 20MP")
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val result = input.readBytes()
            if (result.size > MAX_IMAGE_BYTES) throw IllegalArgumentException("图片超过 10MB")
            result
        } ?: throw IllegalArgumentException("无法读取所选图片")
        if (bytes.isEmpty()) throw IllegalArgumentException("图片内容为空")

        val requestId = UUID.randomUUID().toString()
        val image = MultipartBody.Part.createFormData(
            "image",
            "holding-screenshot.${extensionFor(mimeType)}",
            bytes.toRequestBody(mimeType.toMediaType())
        )
        val textType = "text/plain; charset=utf-8".toMediaType()
        val envelope = NetworkModule.authorized { api ->
            api.parseHoldingScreenshot(
                image = image,
                clientRequestId = requestId.toRequestBody(textType),
                locale = "zh-CN".toRequestBody(textType),
                defaultCurrency = defaultCurrency?.uppercase(Locale.ROOT)?.toRequestBody(textType),
                brokerHint = brokerHint?.toRequestBody(textType),
                parseMode = "HOLDINGS".toRequestBody(textType)
            )
        }
        val data = envelope.requireData()
        if (data.rows.isEmpty()) {
            throw IllegalStateException("[OCR_NO_HOLDINGS] 专属服务未识别出持仓 (requestId=${envelope.requestId})")
        }
        val parsed = data.rows.map { row ->
            val quantity = row.quantity.strictPositive("quantity", row.rowId, envelope.requestId)
            val currentPrice = row.currentPrice.strictPositive("current_price", row.rowId, envelope.requestId)
            val marketValue = row.marketValue.strictPositive("market_value", row.rowId, envelope.requestId)
            val totalCost = row.totalCost.toNullableNonNegative()
            val code = row.securityCode?.normalized.orEmpty()
            val reasons = buildList {
                addAll(row.reviewReasons)
                if (row.name.isNullOrBlank()) add("缺少资产名称")
                if (code.isBlank()) add("缺少可用证券代码")
                if (totalCost == null) add("截图未提供明确总成本")
                if (row.securityCode?.needsConfirmation != false) add("证券代码需要确认")
            }.distinct()
            ParsedScreenshotHolding(
                name = row.name.orEmpty(),
                securityCode = code,
                quantity = quantity,
                currentPrice = currentPrice,
                marketValue = marketValue,
                totalCost = totalCost,
                currency = row.currency?.uppercase(Locale.ROOT).orEmpty(),
                needsReview = reasons.isNotEmpty() || row.confidence < HIGH_CONFIDENCE,
                rawText = row.rawText,
                rawSecurityCode = row.securityCode?.raw.orEmpty(),
                confidence = when {
                    row.confidence >= HIGH_CONFIDENCE -> OcrConfidence.HIGH
                    row.confidence >= MEDIUM_CONFIDENCE -> OcrConfidence.MEDIUM
                    else -> OcrConfidence.LOW
                },
                reviewReason = reasons.joinToString("；").ifBlank { null },
                instrumentId = row.securityCode?.instrumentId.orEmpty(),
                requestId = envelope.requestId
            )
        }
        val deleteEnvelope = NetworkModule.authorized { api ->
            api.deleteOcrDocument(data.documentId)
        }
        if (!deleteEnvelope.requireData().deleted) {
            throw IllegalStateException(
                "[OCR_DELETE_FAILED] 专属服务未确认删除图片 (requestId=${deleteEnvelope.requestId})"
            )
        }
        parsed
    }

    private fun String?.strictPositive(field: String, rowId: String, requestId: String): Double {
        return this?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            ?: throw IllegalStateException(
                "[OCR_INVALID_FIELD] $rowId.$field 缺失或非法 (requestId=$requestId)"
            )
    }

    private fun String?.toNullableNonNegative(): Double? = this?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "img"
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MIN_IMAGE_EDGE = 480
        private const val MAX_IMAGE_PIXELS = 20_000_000L
        private const val HIGH_CONFIDENCE = 0.85
        private const val MEDIUM_CONFIDENCE = 0.60
        private val SUPPORTED_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
        )
    }
}
