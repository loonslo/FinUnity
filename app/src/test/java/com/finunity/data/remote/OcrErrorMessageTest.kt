package com.finunity.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrErrorMessageTest {
    @Test fun dailyQuotaHasClearMessage() {
        assertEquals("今日识别次数已用完，请明日再试", ocrErrorMessage(429, "OCR_QUOTA_EXCEEDED", "upstream"))
    }

    @Test fun rateLimitDoesNotPretendDailyQuotaIsExhausted() {
        assertEquals("识别请求过于频繁，请稍后再试", ocrErrorMessage(429, "RATE_LIMITED", "upstream"))
    }

    @Test fun disabledAndUnavailableHaveClearMessages() {
        assertEquals("截图识别暂不可用，请稍后再试", ocrErrorMessage(503, "OCR_DISABLED", "upstream"))
    }

    @Test fun otherFailuresPreserveServerMessage() {
        assertEquals("图片太小", ocrErrorMessage(422, "IMAGE_TOO_SMALL", "图片太小"))
    }
}
