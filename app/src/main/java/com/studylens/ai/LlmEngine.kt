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
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
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

    /**
     * Engine.createConversation() unconditionally requires a vision encoder section in the
     * model (visionBackend is a mandatory, non-nullable field in EngineConfig - confirmed via
     * bytecode inspection, no "none" option exists), so it only works for genuinely multimodal
     * models. Engine.createSession() has no such requirement (SessionConfig carries no vision
     * fields at all) and IS able to load text-only models - confirmed on-device: Engine
     * .initialize() already succeeds for a text-only model, only createConversation() was
     * failing with "TF_LITE_VISION_ENCODER not found". So: try Conversation first (richer API,
     * multi-turn, tool calling), and if that specific error occurs, fall back to the simpler
     * Session API for that model for the rest of its lifetime. Image input is impossible in
     * the Session path (correctly so - the model has no vision encoder to use).
     */
    private sealed class ChatHandle {
        data class ViaConversation(val conversation: Conversation) : ChatHandle()
        data class ViaSession(val session: Session) : ChatHandle()
    }

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var chatHandle: ChatHandle? = null

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
            when (val handle = chatHandle) {
                is ChatHandle.ViaConversation -> handle.conversation.close()
                is ChatHandle.ViaSession -> handle.session.close()
                null -> Unit
            }
            engine?.close()
            chatHandle = null
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
    // getOrCreateChatHandle). Confirmed via logcat + inspecting the litertlm-android:0.17.0
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

    private fun getOrCreateChatHandle(modelFile: File): ChatHandle {
        chatHandle?.let { if (loadedFilename == modelFile.name) return it }
        synchronized(this) {
            chatHandle?.let { if (loadedFilename == modelFile.name) return it }
            when (val old = chatHandle) { // switching models - release the old one first
                is ChatHandle.ViaConversation -> old.conversation.close()
                is ChatHandle.ViaSession -> old.session.close()
                null -> Unit
            }
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

                    val handle: ChatHandle = try {
                        ChatHandle.ViaConversation(
                            newEngine.createConversation(
                                ConversationConfig(
                                    samplerConfig = if (backend is Backend.NPU) {
                                        null // matches Google's own code - NPU path skips sampler config
                                    } else {
                                        SamplerConfig(topK = 40, topP = 0.9, temperature = 0.8)
                                    }
                                )
                            )
                        )
                    } catch (conversationError: Exception) {
                        if (conversationError.message?.contains("VISION_ENCODER") != true) throw conversationError
                        // Text-only model - Conversation always requires a vision encoder
                        // section (visionBackend is mandatory), but Session has no such
                        // requirement, and initialize() already succeeded above.
                        ChatHandle.ViaSession(newEngine.createSession(SessionConfig()))
                    }

                    engine = newEngine
                    chatHandle = handle
                    loadedFilename = modelFile.name
                    activeBackendName = name
                    return handle
                } catch (e: Exception) {
                    lastError = e
                    // this backend isn't available on this build/device - step down and try the next
                }
            }
            throw lastError ?: IllegalStateException("No backend could initialize the model")
        }
    }

    /**
     * Actually tries to load the currently-active model file right now, instead of waiting
     * for the first chat message to discover it's broken (e.g. a format the Engine can't
     * read - .task files' compatibility with this Engine API is unverified, unlike the
     * proven-working .litertlm container). Returns null on success, or a user-facing error
     * message on failure - the model picker uses this to roll back to the previous model
     * with a clear reason instead of silently leaving the user on a model that will never
     * actually answer anything.
     */
    suspend fun verifyActiveModelLoads(): String? = supervisorScope {
        val modelFile = currentModelFile()
        if (!modelFile.exists()) return@supervisorScope "Model file not found on disk."
        try {
            // getOrCreateChatHandle() is a plain blocking call with no suspension points, so
            // wrapping it directly in withTimeoutOrNull wouldn't actually let us bail early on
            // a true native hang (there'd be nothing for cancellation to interrupt until the
            // call returns on its own). Racing it via async{}.await() gives a real suspension
            // point, so a stuck load can't leave the caller (and the model picker UI) waiting
            // forever.
            //
            // supervisorScope, not coroutineScope: a plain coroutineScope propagates a failed
            // async child's exception up through the job hierarchy immediately (crashing the
            // app before .await() is even reached, bypassing this try/catch entirely - this
            // is exactly what happened when an unsupported model file threw inside the async
            // block). supervisorScope defers that failure until .await() is actually called,
            // which is what lets us catch it below.
            val deferred = async(Dispatchers.IO) { getOrCreateChatHandle(modelFile) }
            val loaded = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) { deferred.await() }
            if (loaded == null) {
                deferred.cancel() // best-effort - the orphaned native call may keep running regardless
                invalidate() // drop whatever half-initialized state it left behind
                "Timed out after ${MODEL_LOAD_TIMEOUT_MS / 1000}s trying to load this model - it may not be supported on this device."
            } else {
                null
            }
        } catch (e: Exception) {
            e.message?.takeIf { it.isNotBlank() } ?: "This model failed to load (${e.javaClass.simpleName})."
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
            when (val handle = getOrCreateChatHandle(modelFile)) {
                is ChatHandle.ViaConversation -> generateViaConversation(handle.conversation, prompt, image)
                is ChatHandle.ViaSession -> generateViaSession(handle.session, prompt, image)
            }
        } catch (e: Exception) {
            "Sorry, I couldn't generate an explanation just now. Please try again."
        }
    }

    private suspend fun generateViaConversation(conv: Conversation, prompt: String, image: Bitmap?): String {
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
        return result ?: run {
            // The model/GPU hung past the timeout - drop this conversation so the next
            // attempt starts clean instead of piling onto a stuck backend.
            invalidate()
            if (image != null) {
                "This device's GPU is taking too long to read that photo. Try a clearer, closer photo of just the problem, or ask as a text question instead."
            } else {
                "This is taking longer than expected. Please try again."
            }
        }
    }

    private suspend fun generateViaSession(session: Session, prompt: String, image: Bitmap?): String {
        if (image != null) {
            // This model has no vision encoder at all (that's exactly why it's on the
            // Session path instead of Conversation) - there's no way to make it read an
            // image, so say so clearly instead of silently ignoring the photo.
            return "This model can only read text, not images - switch to a multimodal model (like Qwen2-VL 2B) to analyze photos."
        }
        return try {
            supervisorScope {
                // Same reasoning as verifyActiveModelLoads(): generateContent() is a plain
                // blocking call, so it's raced via async{}.await() (a real suspension point)
                // under supervisorScope (so a failure inside doesn't crash the app before
                // .await() is reached - see the comment there for the full explanation).
                val deferred = async(Dispatchers.IO) {
                    session.generateContent(listOf(InputData.Text(prompt)))
                }
                val result = withTimeoutOrNull(VISION_TIMEOUT_MS) { deferred.await() }
                if (result == null) {
                    deferred.cancel()
                    invalidate()
                    "This is taking longer than expected. Please try again."
                } else {
                    result
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
        const val MODEL_LOAD_TIMEOUT_MS = 60_000L
    }
}
