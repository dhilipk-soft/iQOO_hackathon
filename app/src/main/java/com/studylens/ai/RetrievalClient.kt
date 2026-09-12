package com.studylens.ai

import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun queryOnlineContext(
        @Header("Authorization") apiKey: String,
        @Body request: Map<String, Any>
    ): Map<String, Any>
}

/**
 * Retrieval step of the RAG pipeline (implementation-plan.md §3a) — only called when
 * NetworkHealthChecker says isOnline. Asks for short FACTS, not a finished explanation;
 * the local model in LlmEngine always does the actual explaining. Any failure or slow
 * response just yields an empty string — the caller then proceeds offline-only.
 */
class RetrievalClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(OpenRouterApi::class.java)

    suspend fun fetchOnlineContext(topic: String, apiKey: String): String {
        if (apiKey.isBlank()) return ""
        return try {
            withTimeoutOrNull(6000L) {
                val response = api.queryOnlineContext(
                    apiKey = "Bearer $apiKey",
                    request = mapOf(
                        "model" to "perplexity/sonar:online",
                        "messages" to listOf(
                            mapOf(
                                "role" to "system",
                                "content" to "Return 2-3 short, current factual bullet points about " +
                                    "this topic for a student. No explanation, no greeting, just facts."
                            ),
                            mapOf("role" to "user", "content" to "Topic: $topic")
                        )
                    )
                )
                extractContent(response)
            }.orEmpty()
        } catch (e: Exception) {
            "" // retrieval failed or timed out — fine, generation still runs locally without it
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractContent(response: Map<String, Any>): String {
        val choices = response["choices"] as? List<Map<String, Any>> ?: return ""
        val message = choices.firstOrNull()?.get("message") as? Map<String, Any> ?: return ""
        return message["content"] as? String ?: ""
    }
}
