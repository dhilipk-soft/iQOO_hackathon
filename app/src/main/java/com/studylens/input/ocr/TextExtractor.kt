package com.studylens.input.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class TextExtractor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap, rotationDegrees: Int = 0): String = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        continuation.resume(text)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                continuation.resume("")
            }
        }
    }

    fun release() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
    }
}
