package com.studylens.input

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.studylens.input.capture.CameraCapture
import com.studylens.input.data.AppDatabase
import com.studylens.input.data.StudyCaptureEntity
import com.studylens.input.network.NetworkHealthChecker
import com.studylens.input.ocr.TextExtractor
import com.studylens.shared.InputProvider
import com.studylens.shared.StudyCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class InputProviderImpl(
    private val context: Context,
    val cameraCapture: CameraCapture,
    val textExtractor: TextExtractor,
    val networkHealthChecker: NetworkHealthChecker,
    private val database: AppDatabase
) : InputProvider {

    override val isOnline: StateFlow<Boolean> = networkHealthChecker.isOnline

    override suspend fun captureAndExtractText(): StudyCapture = withContext(Dispatchers.IO) {
        Log.d(TAG, "captureAndExtractText called...")
        val bitmap: Bitmap? = cameraCapture.captureFrame()
        val text = if (bitmap != null) {
            Log.d(TAG, "Frame captured, passing bitmap to TextExtractor...")
            val extracted = textExtractor.extractText(bitmap)
            if (extracted.isNotBlank()) {
                Log.d(TAG, "TextExtractor successfully returned ${extracted.length} chars.")
                extracted
            } else {
                Log.w(TAG, "TextExtractor returned blank text. Using fallback message.")
                "No readable text detected in camera capture."
            }
        } else {
            Log.w(TAG, "Camera frame unavailable. Using sample topic fallback.")
            "Camera frame unavailable. Sample study topic: Newton's Laws of Motion."
        }

        val timestamp = System.currentTimeMillis()
        val entity = StudyCaptureEntity(
            extractedText = text,
            timestamp = timestamp
        )
        val id = database.studyCaptureDao().insert(entity)
        Log.i(TAG, "Persisted StudyCapture to DB with ID = $id, timestamp = $timestamp")

        StudyCapture(
            id = id,
            extractedText = text,
            timestamp = timestamp
        )
    }

    suspend fun processTextDirectly(text: String): StudyCapture = withContext(Dispatchers.IO) {
        Log.d(TAG, "processTextDirectly called with ${text.length} chars...")
        val timestamp = System.currentTimeMillis()
        val entity = StudyCaptureEntity(
            extractedText = text,
            timestamp = timestamp
        )
        val id = database.studyCaptureDao().insert(entity)
        Log.i(TAG, "Persisted direct text StudyCapture to DB with ID = $id")

        StudyCapture(
            id = id,
            extractedText = text,
            timestamp = timestamp
        )
    }

    companion object {
        private const val TAG = "InputProviderImpl"
    }
}

