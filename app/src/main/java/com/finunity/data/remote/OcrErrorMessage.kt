package com.finunity.data.remote

/** Keep screenshot quota and maintenance feedback separate from market/auth failures. */
internal fun ocrErrorMessage(httpStatus: Int?, errorCode: String, fallback: String): String = when {
    httpStatus == 429 && errorCode == "OCR_QUOTA_EXCEEDED" -> "今日识别次数已用完，请明日再试"
    httpStatus == 429 -> "识别请求过于频繁，请稍后再试"
    httpStatus == 503 || errorCode == "OCR_DISABLED" -> "截图识别暂不可用，请稍后再试"
    else -> fallback
}
