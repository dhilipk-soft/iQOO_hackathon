package com.studylens.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps the on-device Gemma model via MediaPipe LLM Inference.
 */
class LlmEngine(private val context: Context) {

    private val modelFile = File(context.filesDir, "Gemma3-1B-IT_q4_ekv1280_sm8850.bin")

    private var llmInference: LlmInference? = null

    var activeBackendName: String = "NPU/GPU"
        private set

    private fun getOrCreateInference(): LlmInference {
        llmInference?.let { return it }
        synchronized(this) {
            llmInference?.let { return it }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()
            val inference = LlmInference.createFromOptions(context, options)
            llmInference = inference
            return inference
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                return@withContext "The on-device model isn't loaded on this device yet."
            }
            val inference = getOrCreateInference()
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }
}
