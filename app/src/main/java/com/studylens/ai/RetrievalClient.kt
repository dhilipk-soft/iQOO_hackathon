package com.studylens.ai

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

private const val TAG = "RetrievalClient"

// Asks for real synthesis across multiple sources, not just a couple of one-line facts -
// the student-facing explanation downstream (ExplainPipeline) is instructed to actually
// expand on this in detail when it's present, so it needs enough substance to work with.
private const val RETRIEVAL_SYSTEM_PROMPT =
    "Search the web and give a detailed, well-organized summary of the most current, " +
        "relevant information on this topic for a student - draw on multiple sources, " +
        "cover distinct angles (recent developments, key figures, context, implications) " +
        "rather than one generic point. Write 6-10 substantive bullet points, each a full " +
        "sentence with real detail, not just a keyword. No greeting, no meta-commentary " +
        "about your search process - just the information itself."

// HashMap, not Map, for the @Body type - Kotlin's Map<K, V> interface declares V as `out V`,
// which erases to a wildcard (`Map<String, ?>`) in the compiled JVM signature. Retrofit's
// body-converter lookup explicitly rejects wildcard types and throws IllegalArgumentException
// before making any network call at all - confirmed via logcat, this was silently failing
// every single retrieval call from the app (while curl testing against the same endpoints
// worked fine, since curl doesn't go through Retrofit's Kotlin-type reflection). HashMap is a
// concrete, invariant class, so it compiles to a plain (non-wildcard) generic signature.
interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun queryOnlineContext(
        @Header("Authorization") apiKey: String,
        @Body request: HashMap<String, Any>
    ): HashMap<String, Any>
}

interface GroqApi {
    @POST("openai/v1/chat/completions")
    suspend fun queryOnlineContext(
        @Header("Authorization") apiKey: String,
        @Body request: HashMap<String, Any>
    ): HashMap<String, Any>
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
 *
 * Hybrid strategy: Groq's "compound" model runs its own agentic web search per-call for
 * free-tier credits, so it's tried FIRST — this is the whole point of the hybrid, since
 * OpenRouter/Perplexity Sonar costs real money per call and the project can't fund heavy
 * usage there. Groq compound is known to be less reliable (rate limits shared across its
 * internal sub-models, occasional "Request Entity Too Large" errors seen in testing), so
 * any failure there — timeout, error, or just an empty response — falls through to
 * OpenRouter/Sonar as the paid-but-reliable backup, rather than giving up and going offline.
 */
class RetrievalClient {
    private val openRouterApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenRouterApi::class.java)

    private val groqApi = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqApi::class.java)

    suspend fun fetchOnlineContext(topic: String, openRouterKey: String, groqKey: String): RetrievalResult {
        val groqResult = fetchGroqContext(topic, groqKey)
        if (groqResult != null && groqResult.factsText.isNotBlank()) {
            Log.i(TAG, "Groq succeeded, using its result (${groqResult.factsText.length} chars)")
            return groqResult
        }
        Log.i(TAG, "Groq gave nothing usable, falling back to OpenRouter")
        return fetchOpenRouterContext(topic, openRouterKey)
    }

    private suspend fun fetchGroqContext(topic: String, apiKey: String): RetrievalResult? {
        if (apiKey.isBlank()) {
            Log.w(TAG, "Groq: no API key configured, skipping")
            return null
        }
        return try {
            val result = withTimeoutOrNull(6000L) {
                val response = groqApi.queryOnlineContext(
                    apiKey = "Bearer $apiKey",
                    request = hashMapOf(
                        // compound-mini, not the full compound model - it does less internal
                        // tool-call orchestration, so it's more likely to finish inside our
                        // timeout budget. Full compound was observed hanging past 6s doing its
                        // own agentic web-search loop internally.
                        "model" to "groq/compound-mini",
                        "max_tokens" to 700,
                        "temperature" to 0.3,
                        "messages" to listOf(
                            mapOf(
                                "role" to "system",
                                "content" to RETRIEVAL_SYSTEM_PROMPT
                            ),
                            mapOf("role" to "user", "content" to "Topic: $topic")
                        )
                    )
                )
                extractGroqResult(response)
            }
            if (result == null) Log.w(TAG, "Groq: timed out after 6s")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Groq failed: ${e.javaClass.simpleName}: ${e.message}")
            null // Groq unavailable/erroring - caller falls back to OpenRouter
        }
    }

    private suspend fun fetchOpenRouterContext(topic: String, apiKey: String): RetrievalResult {
        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenRouter: no API key configured, skipping - going fully offline")
            return RetrievalResult("")
        }
        return try {
            val result = withTimeoutOrNull(8000L) {
                val response = openRouterApi.queryOnlineContext(
                    apiKey = "Bearer $apiKey",
                    request = hashMapOf(
                        "model" to "perplexity/sonar", // already web-connected, no ":online" suffix needed
                        "max_tokens" to 700, // required - omitting this defaults to the model max and errors on limited credits
                        "messages" to listOf(
                            mapOf(
                                "role" to "system",
                                "content" to RETRIEVAL_SYSTEM_PROMPT
                            ),
                            mapOf("role" to "user", "content" to "Topic: $topic")
                        )
                    )
                )
                extractOpenRouterResult(response)
            }
            if (result == null) {
                Log.w(TAG, "OpenRouter: timed out after 8s - going fully offline for this turn")
                RetrievalResult("")
            } else {
                Log.i(TAG, "OpenRouter succeeded (${result.factsText.length} chars, ${result.citations.size} citations)")
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenRouter failed: ${e.javaClass.simpleName}: ${e.message}")
            RetrievalResult("") // retrieval failed or timed out - fine, generation still runs locally without it
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractOpenRouterResult(response: Map<String, Any>): RetrievalResult {
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

        return RetrievalResult(stripInlineCitationMarkers(content), citations)
    }

    // Perplexity Sonar embeds its own numbered inline markers like "...milestone[1][3][8]"
    // directly in the content, positionally tied to ITS OWN citation ordering - not ours
    // (our appended "Sources" list is unordered bullets, and this text also gets fed into
    // the local model's prompt, which can copy these meaningless-out-of-context numbers
    // verbatim into the final answer the student sees). Stripped here, once, at the source,
    // rather than trying to catch it everywhere downstream.
    private fun stripInlineCitationMarkers(text: String): String =
        text.replace(Regex("\\[\\d+]"), "").replace(Regex(" {2,}"), " ").trim()

    // Groq compound's tool-search result shape is less standardized/documented than
    // OpenRouter's annotations - parsed defensively so a shape mismatch just means no
    // citations, not a crash or a lost result. The factsText (guaranteed OpenAI-compatible
    // message.content) is what actually matters for grounding.
    @Suppress("UNCHECKED_CAST")
    private fun extractGroqResult(response: Map<String, Any>): RetrievalResult {
        val choices = response["choices"] as? List<Map<String, Any>> ?: return RetrievalResult("")
        val message = choices.firstOrNull()?.get("message") as? Map<String, Any> ?: return RetrievalResult("")
        val content = message["content"] as? String ?: ""

        val citations = try {
            val executedTools = message["executed_tools"] as? List<Map<String, Any>> ?: emptyList()
            executedTools.flatMap { tool ->
                val results = (tool["search_results"] as? Map<String, Any>)?.get("results") as? List<Map<String, Any>>
                    ?: emptyList()
                results.mapNotNull { result ->
                    val url = result["url"] as? String ?: return@mapNotNull null
                    val title = (result["title"] as? String)?.takeIf { it.isNotBlank() } ?: url
                    WebCitation(title, url)
                }
            }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }

        return RetrievalResult(stripInlineCitationMarkers(content), citations)
    }
}
