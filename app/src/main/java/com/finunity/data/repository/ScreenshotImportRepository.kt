package com.finunity.data.repository

import android.content.Context
import android.net.Uri
import com.finunity.data.model.HoldingScreenshotParser
import com.finunity.data.model.ParsedScreenshotHolding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Reads a user-selected holding screenshot entirely on device. No broker account is contacted. */
class ScreenshotImportRepository(private val context: Context) {
    suspend fun recognize(uri: Uri): List<ParsedScreenshotHolding> = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            HoldingScreenshotParser.parse(recognizer.process(image).await().text)
        } finally {
            recognizer.close()
        }
    }
}
