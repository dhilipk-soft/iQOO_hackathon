package com.studylens.ai

import android.content.Context

class LlmEngine(private val context: Context) {
    suspend fun generateResponse(prompt: String): String {
        // MediaPipe LlmInference wrapper
        return "On-device LLM response placeholder for: $prompt"
    }
}
