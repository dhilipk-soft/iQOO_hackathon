package com.studylens.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps the on-device Gemma model via MediaPipe LLM Inference. This is the guaranteed
 * path — it must work with zero network connectivity, so any failure here falls back to
 * a plain string rather than throwing (see implementation-plan.md §3a/§3b).
 */
class LlmEngine(private val context: Context) {

    // Model file is pushed onto the device via adb into the app's own files dir:
    //   adb push gemma3-1b-it-int4.task /data/local/tmp/
    //   adb shell run-as com.studylens cp /data/local/tmp/gemma3-1b-it-int4.task /data/data/com.studylens/files/
    private val modelFile = File(context.filesDir, "gemma3-1b-it-int4.task")

    @Volatile
    private var llmInference: LlmInference? = null

    /** Which backend actually loaded — read this for the Device Vitals strip (§3b). */
    var activeBackendName: String = "none"
        private set

    private fun getOrCreateInference(): LlmInference {
        llmInference?.let { return it }
        synchronized(this) {
            llmInference?.let { return it }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                // 512 was too tight - it bounds prompt+response combined, so once a prompt
                // included prior context (follow-ups, retrieved facts) there was barely any
                // room left for the actual answer, causing short/truncated/degenerate output.
                .setMaxTokens(1536)
                .setPreferredBackend(LlmInference.Backend.GPU) // explicit hardware acceleration
                .build()
            val inference = LlmInference.createFromOptions(context, options)
            llmInference = inference
            activeBackendName = "GPU"
            return inference
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                return@withContext "The on-device model isn't loaded on this device yet."
            }
            getOrCreateInference().generateResponse(prompt)
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }
}
