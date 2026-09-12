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

class TextExtractor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractText(bitmap: Bitmap, rotationDegrees: Int = 0): String = withContext(Dispatchers.Default) {
        Log.d(TAG, "Starting ML Kit text extraction from Bitmap (${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees)...")
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        Log.d(TAG, "ML Kit text extraction success! Extracted length: ${text.length} chars.")
                        if (text.isNotEmpty()) {
                            Log.d(TAG, "Extracted text preview: ${text.take(100)}...")
                        }
                        continuation.resume(text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit text extraction failed", e)
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during ML Kit text recognition setup", e)
                continuation.resume("")
            }
        }
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

