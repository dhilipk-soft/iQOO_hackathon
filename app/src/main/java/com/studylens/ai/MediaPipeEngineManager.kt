package com.studylens.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MediaPipeEngineManager(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _generationState = MutableSharedFlow<GenerationState>(replay = 1)
    val generationState: SharedFlow<GenerationState> = _generationState.asSharedFlow()

    /**
     * Bootstraps the MediaPipe Engine. Call this ONLY after model file is ready on disk.
     */
    suspend fun initialize(absolutePath: String, isMultimodal: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            _engineState.value = EngineState.LoadingIntoMemory

            // 1. Initialize Base Engine
            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(absolutePath)
                .setMaxTokens(2048)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            val instance = LlmInference.createFromOptions(context, inferenceOptions)
            llmInference = instance

            // 2. Configure Session Options
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.7f)
                .setTopK(40)
                .build()

            // 3. Mount Session
            session = LlmInferenceSession.createFromOptions(instance, sessionOptions)
            _engineState.value = EngineState.Ready
            Log.i("MediaPipeEngineManager", "Engine initialized successfully on GPU.")

        } catch (e: Exception) {
            Log.e("MediaPipeEngineManager", "Engine Init Failed", e)
            _engineState.value = EngineState.Error(e.localizedMessage ?: "Unknown initialization error")
        }
    }

    /**
     * Directly processes text and optional Bitmap arrays bypassing standard OCR pipelines.
     */
    suspend fun sendPrompt(prompt: String, imageBitmap: Bitmap? = null) = withContext(Dispatchers.IO) {
        if (session == null || _engineState.value !is EngineState.Ready) {
            _generationState.emit(GenerationState.Error("Engine not ready."))
            return@withContext
        }

        try {
            _generationState.emit(GenerationState.Generating)
            val activeSession = session!!

            // Multimodal routing
            if (imageBitmap != null) {
                val mpImage = BitmapImageBuilder(imageBitmap).build()
                activeSession.addImage(mpImage)
            }

            // Core Text Chunking
            activeSession.addQueryChunk(prompt)

            // Stream response natively
            activeSession.generateResponseAsync { partialResult, done ->
                if (done) {
                    _generationState.tryEmit(GenerationState.Done(partialResult ?: ""))
                } else {
                    _generationState.tryEmit(GenerationState.Partial(partialResult ?: ""))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaPipeEngineManager", "Generation Failed", e)
            _generationState.emit(GenerationState.Error(e.localizedMessage ?: "Inference crashed"))
        }
    }

    /**
     * Must be called in ViewModel onCleared() to prevent memory leaks.
     */
    fun close() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w("MediaPipeEngineManager", "Session close exception", e)
        }
        session = null
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w("MediaPipeEngineManager", "Inference close exception", e)
        }
        llmInference = null
        _engineState.value = EngineState.Uninitialized
    }
}
