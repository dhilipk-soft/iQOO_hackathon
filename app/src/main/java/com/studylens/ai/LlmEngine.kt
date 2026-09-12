package com.studylens.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * On-device Gemma model via LiteRT-LM. Supports both text-only and multimodal
 * (image + text) prompts through the Engine/Conversation/Content API — modeled directly
 * on Google's own AI Edge Gallery app source (LlmChatModelHelper.kt), not guessed.
 *
 * Tries NPU -> GPU -> CPU for the main backend, same resilience pattern as before. Vision
 * specifically is forced to GPU per Google's own comment in that file: "must be GPU for
 * Gemma 3n".
 *
 * generateResponse(prompt) with no image behaves exactly as the old text-only engine did -
 * existing callers (ExplainPipeline, QuizGenerator) don't need to change.
 */
class LlmEngine(private val context: Context) {

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    @Volatile
    private var loadedFilename: String? = null

    /** Which backend actually loaded — read this for the Device Vitals strip (§3b). */
    @Volatile
    var activeBackendName: String = "none"
        private set

    private fun currentModelFile(): File =
        File(context.filesDir, ModelDownloadManager.getActiveModelFilename(context))

    /**
     * Call this right after the user picks a different downloaded model (model picker
     * screen). Closes the currently-loaded engine so the next generateResponse() call
     * loads the new file instead of silently continuing to use whatever was in memory.
     */
    fun invalidate() {
        synchronized(this) {
            conversation?.close()
            engine?.close()
            conversation = null
            engine = null
            loadedFilename = null
            activeBackendName = "none"
        }
    }

    private fun backendsToTry(): List<Pair<String, Backend>> = listOf(
        "NPU" to Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
        "GPU" to Backend.GPU(),
        "CPU" to Backend.CPU()
    )

    private fun getOrCreateConversation(modelFile: File): Conversation {
        conversation?.let { if (loadedFilename == modelFile.name) return it }
        synchronized(this) {
            conversation?.let { if (loadedFilename == modelFile.name) return it }
            conversation?.close() // switching models - release the old one first
            engine?.close()

            var lastError: Exception? = null
            for ((name, backend) in backendsToTry()) {
                try {
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        visionBackend = Backend.GPU(), // must be GPU for Gemma 3n (per Google's own code)
                        maxNumTokens = 1536
                    )
                    val newEngine = Engine(config)
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation(
                        ConversationConfig(
                            samplerConfig = if (backend is Backend.NPU) {
                                null // matches Google's own code - NPU path skips sampler config
                            } else {
                                SamplerConfig(topK = 40, topP = 0.9, temperature = 0.8)
                            }
                        )
                    )
                    engine = newEngine
                    conversation = newConversation
                    loadedFilename = modelFile.name
                    activeBackendName = name
                    return newConversation
                } catch (e: Exception) {
                    lastError = e
                    // this backend isn't available on this build/device - step down and try the next
                }
            }
            throw lastError ?: IllegalStateException("No backend could initialize the model")
        }
    }

    /** Text-only call - unchanged behavior for existing callers. */
    suspend fun generateResponse(prompt: String): String = generateResponse(prompt, image = null)

    /**
     * Multimodal call - pass a captured photo directly to the model instead of routing it
     * through OCR first. image = null is identical to the text-only path above.
     * Always returns something usable - never throws, never hangs indefinitely.
     */
    suspend fun generateResponse(prompt: String, image: Bitmap?): String = withContext(Dispatchers.IO) {
        val modelFile = currentModelFile()
        try {
            if (!modelFile.exists()) {
                return@withContext "The on-device model isn't loaded on this device yet."
            }
            val conv = getOrCreateConversation(modelFile)
            val contents = mutableListOf<Content>()
            if (image != null) {
                contents.add(Content.ImageBytes(image.toPngByteArray()))
            }
            if (prompt.isNotBlank()) {
                contents.add(Content.Text(prompt))
            }

            suspendCancellableCoroutine { cont ->
                val response = StringBuilder()
                conv.sendMessageAsync(
                    Contents.of(contents),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            response.append(message.toString())
                        }

                        override fun onDone() {
                            if (cont.isActive) cont.resume(response.toString())
                        }

                        override fun onError(throwable: Throwable) {
                            if (cont.isActive) {
                                cont.resume("Sorry, I couldn't generate an explanation just now. Please try again.")
                            }
                        }
                    },
                    emptyMap()
                )
            }
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
