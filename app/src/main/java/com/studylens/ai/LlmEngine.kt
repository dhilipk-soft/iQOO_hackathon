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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

/**
 * On-device Gemma model via LiteRT-LM. Supports both text-only and multimodal
 * (image + text) prompts through the Engine/Conversation/Content API — modeled directly
 * on Google's own AI Edge Gallery app source (LlmChatModelHelper.kt), not guessed.
 *
 * Tries GPU -> CPU for the main backend (NPU is skipped - see backendsToTry() for why).
 * Vision specifically is forced to GPU per Google's own comment in that file: "must be
 * GPU for Gemma 3n".
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

    // NPU deliberately left out: this build has no Qualcomm QNN/QAIRT dispatch library or
    // compiler plugin bundled (confirmed via logcat - "No dispatch library found", "No
    // compiler plugin found"), and the downloaded models carry no TF_LITE_AUX NPU section
    // either. Re-add "NPU" to Backend.NPU(...) once the vendor QAIRT .so files are bundled
    // in jniLibs/arm64-v8a AND an NPU-compiled model is used.
    //
    // GPU is also deliberately left out for the TEXT backend (not visionBackend below - see
    // getOrCreateConversation). Confirmed via logcat + inspecting the litertlm-android:0.17.0
    // AAR directly (it contains ONLY liblitertlm_jni.so, nothing else) that this device's
    // profile - WebGPU-only Adreno, no OpenCL - has no working token sampler: the runtime
    // needs a GPU-resident top-k sampler when the decode graph runs on GPU
    // (libLiteRtTopKWebGpuSampler.so), that plugin isn't shipped in this AAR at all, its
    // OpenCL-sampler fallback also isn't shipped, and the final "static" fallback under that
    // is itself broken on no-OpenCL devices ("Can not find OpenCL library on this device").
    // This isn't a resource/timing issue that a retry fixes - it's a deterministic failure on
    // every single generation call, and it happens inside sendMessageAsync(), past the point
    // our try/catch here can detect and step down from. So: text decode must run on CPU/XNNPACK
    // (unaffected - no GPU-resident sampler negotiation happens there). Image encoding still
    // uses GPU via visionBackend since it's not autoregressive and doesn't sample tokens.
    private fun backendsToTry(): List<Pair<String, Backend>> = listOf(
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
                        // CPU, not GPU - confirmed via logcat that GPU vision execution hangs
                        // and fails on this device/library combo: "Failed to register input
                        // tensor buffer: Failed to lock the tensor buffer... Timed out waiting
                        // for future: 10s" inside vision_litert_compiled_model_executor.cc.
                        // This is the same class of GPU/WebGPU instability as the text-decode
                        // sampler bug above - the model LOADS fine on GPU (100% node delegation
                        // logs), but hangs/fails during actual execution. Downscaling the image
                        // did not prevent it, so it's a GPU sync bug, not an image-size issue.
                        // "Must be GPU for Gemma 3n" (Google's own comment) doesn't apply here -
                        // this app uses Qwen2-VL-2B, not Gemma 3n.
                        visionBackend = Backend.CPU(),
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
                // Camera/gallery photos can be 8-12MP - feeding that straight into the vision
                // encoder is what was hanging the GPU for 10s+ and freezing the UI (fence
                // timeouts, "Failed to lock tensor buffer" in logcat). Downscaling first keeps
                // the vision encoder's input in the size range it actually expects.
                contents.add(Content.ImageBytes(image.downscaleForVisionModel().toPngByteArray()))
            }
            if (prompt.isNotBlank()) {
                contents.add(Content.Text(prompt))
            }

            val result = withTimeoutOrNull(VISION_TIMEOUT_MS) {
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
            }
            result ?: run {
                // The model/GPU hung past the timeout - drop this conversation so the next
                // attempt starts clean instead of piling onto a stuck backend.
                invalidate()
                if (image != null) {
                    "This device's GPU is taking too long to read that photo. Try a clearer, closer photo of just the problem, or ask as a text question instead."
                } else {
                    "This is taking longer than expected. Please try again."
                }
            }
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }

    /** Caps the longest edge to keep the vision encoder's workload (and GPU time) bounded -
     * full camera resolution isn't needed to read a textbook page or problem. */
    private fun Bitmap.downscaleForVisionModel(): Bitmap {
        val longestEdge = maxOf(width, height)
        if (longestEdge <= MAX_IMAGE_EDGE_PX) return this
        val scale = MAX_IMAGE_EDGE_PX.toFloat() / longestEdge
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private companion object {
        const val MAX_IMAGE_EDGE_PX = 1024
        const val VISION_TIMEOUT_MS = 45_000L
    }
}
