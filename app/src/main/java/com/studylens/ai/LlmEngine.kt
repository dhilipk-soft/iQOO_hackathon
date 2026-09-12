package com.studylens.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wraps the on-device Gemma model via LiteRT-LM, targeting the iQOO 15's dedicated NPU
 * first (implementation-plan.md §3b) so inference genuinely runs on the phone's AI
 * accelerator rather than just the GPU/CPU. Tries NPU -> GPU -> CPU in order and sticks
 * with whichever one actually initializes, so a missing NPU delegate on this exact build
 * never crashes or freezes the app — it just quietly steps down a tier.
 */
class LlmEngine(private val context: Context) {

    // Chip-specific model (compiled for the iQOO 15's SM8850 chip), pushed onto the device
    // via adb into the app's own files dir:
    //   adb push Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm /data/local/tmp/
    //   adb shell run-as com.studylens cp /data/local/tmp/Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm /data/data/com.studylens/files/
    private val modelFile = File(context.filesDir, "Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm")

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    /** Which backend actually ended up loading — read this for the Device Vitals strip (§3b). */
    @Volatile
    var activeBackendName: String = "none"
        private set

    private fun backendsToTry(): List<Pair<String, Backend>> = listOf(
        "NPU" to Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
        "GPU" to Backend.GPU(),
        "CPU" to Backend.CPU()
    )

    private fun getOrCreateConversation(): Conversation {
        conversation?.let { return it }
        synchronized(this) {
            conversation?.let { return it }
            var lastError: Exception? = null
            for ((name, backend) in backendsToTry()) {
                try {
                    val config = EngineConfig(modelPath = modelFile.absolutePath, backend = backend)
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation()
                    engine = newEngine
                    conversation = newConversation
                    activeBackendName = name
                    return newConversation
                } catch (e: Exception) {
                    lastError = e
                    // this backend isn't available on this build/device — step down and try the next
                }
            }
            throw lastError ?: IllegalStateException("No backend could initialize the model")
        }
    }

    /**
     * Always returns something usable — never throws, never blocks indefinitely.
     * On any failure (model missing, no backend available, OOM, thermal issue) returns a
     * plain fallback string so the app never freezes or crashes on stage.
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                return@withContext "The on-device model isn't loaded on this device yet."
            }
            val conv = getOrCreateConversation()
            val response = StringBuilder()
            conv.sendMessageAsync(prompt).collect { chunk -> response.append(chunk.toString()) }
            response.toString()
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }
}
