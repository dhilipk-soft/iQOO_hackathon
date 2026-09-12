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

data class WebCitation(val title: String, val url: String)

data class RetrievalResult(
    val factsText: String,
    val citations: List<WebCitation> = emptyList()
)

/**
 * Retrieval step of the RAG pipeline (implementation-plan.md §3a) — only called when
 * NetworkHealthChecker says isOnline. Asks for short FACTS, not a finished explanation;
 * the local model in LlmEngine always does the actual explaining. Any failure or slow
 * response just yields an empty result — the caller then proceeds offline-only.
 */
class RetrievalClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(OpenRouterApi::class.java)

    suspend fun fetchOnlineContext(topic: String, apiKey: String): RetrievalResult {
        if (apiKey.isBlank()) return RetrievalResult("")
        return try {
            withTimeoutOrNull(8000L) {
                val response = api.queryOnlineContext(
                    apiKey = "Bearer $apiKey",
                    request = mapOf(
                        "model" to "perplexity/sonar", // already web-connected, no ":online" suffix needed
                        "max_tokens" to 300, // required - omitting this defaults to the model max and errors on limited credits
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
                extractResult(response)
            } ?: RetrievalResult("")
        } catch (e: Exception) {
            RetrievalResult("") // retrieval failed or timed out - fine, generation still runs locally without it
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractResult(response: Map<String, Any>): RetrievalResult {
        val choices = response["choices"] as? List<Map<String, Any>> ?: return RetrievalResult("")
        val message = choices.firstOrNull()?.get("message") as? Map<String, Any> ?: return RetrievalResult("")
        val content = message["content"] as? String ?: ""

        val annotations = message["annotations"] as? List<Map<String, Any>> ?: emptyList()
        val citations = annotations.mapNotNull { annotation ->
            val urlCitation = annotation["url_citation"] as? Map<String, Any> ?: return@mapNotNull null
            val url = urlCitation["url"] as? String ?: return@mapNotNull null
            val title = (urlCitation["title"] as? String)?.takeIf { it.isNotBlank() } ?: url
            WebCitation(title, url)
        }.distinctBy { it.url }

        return RetrievalResult(content, citations)
    }
}
