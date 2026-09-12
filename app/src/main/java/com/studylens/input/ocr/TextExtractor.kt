package com.studylens.input.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ReadabilityAssessment(
    val isReadable: Boolean,
    val score: Int, // 0 to 100
    val text: String,
    val textDetected: Boolean,
    val pageAligned: Boolean,
    val lineCount: Int,
    val wordCount: Int,
    val tips: List<String> = emptyList()
)

class TextExtractor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun assessReadability(bitmap: Bitmap, rotationDegrees: Int = 0): ReadabilityAssessment = withContext(Dispatchers.Default) {
        Log.d(TAG, "Assessing readability from Bitmap (${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees)...")
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        val blocks = visionText.textBlocks
                        val lineCount = blocks.sumOf { it.lines.size }
                        val words = if (text.isBlank()) emptyList() else text.split("\\s+".toRegex()).filter { it.isNotBlank() }
                        val wordCount = words.size

                        val textDetected = wordCount >= 3 && text.length >= 12
                        val pageAligned = textDetected && lineCount >= 1

                        if (textDetected) {
                            val score = (88 + (wordCount.coerceAtMost(25) * 0.3).toInt()).coerceIn(85, 96)
                            Log.d(TAG, "Readability assessed: HIGH ($score%), textDetected=true, pageAligned=true, chars=${text.length}")
                            continuation.resume(
                                ReadabilityAssessment(
                                    isReadable = true,
                                    score = score,
                                    text = text,
                                    textDetected = true,
                                    pageAligned = pageAligned,
                                    lineCount = lineCount,
                                    wordCount = wordCount,
                                    tips = emptyList()
                                )
                            )
                        } else {
                            val score = 24
                            Log.w(TAG, "Readability assessed: LOW ($score%), text unreadable or empty.")
                            continuation.resume(
                                ReadabilityAssessment(
                                    isReadable = false,
                                    score = score,
                                    text = text,
                                    textDetected = false,
                                    pageAligned = false,
                                    lineCount = lineCount,
                                    wordCount = wordCount,
                                    tips = listOf(
                                        "Move closer to the page",
                                        "Avoid glare and harsh shadows",
                                        "Keep the page flat and steady"
                                    )
                                )
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit text recognition failed during readability assessment", e)
                        continuation.resume(
                            ReadabilityAssessment(
                                isReadable = false,
                                score = 15,
                                text = "",
                                textDetected = false,
                                pageAligned = false,
                                lineCount = 0,
                                wordCount = 0,
                                tips = listOf(
                                    "Move closer to the page",
                                    "Avoid glare and harsh shadows",
                                    "Keep the page flat and steady"
                                )
                            )
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during ML Kit text recognition setup", e)
                continuation.resume(
                    ReadabilityAssessment(
                        isReadable = false,
                        score = 15,
                        text = "",
                        textDetected = false,
                        pageAligned = false,
                        lineCount = 0,
                        wordCount = 0,
                        tips = listOf(
                            "Move closer to the page",
                            "Avoid glare and harsh shadows",
                            "Keep the page flat and steady"
                        )
                    )
                )
            }
        }
    }

    suspend fun extractText(bitmap: Bitmap, rotationDegrees: Int = 0): String = withContext(Dispatchers.Default) {
        assessReadability(bitmap, rotationDegrees).text
    }

    fun release() {
        try {
            Log.d(TAG, "Releasing ML Kit TextRecognizer...")
            recognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TextRecognizer", e)
        }
    }

    companion object {
        private const val TAG = "TextExtractor"
    }
}
