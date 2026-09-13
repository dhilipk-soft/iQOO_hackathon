package com.studylens.ai

import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.studylens.shared.VerifiedCitation

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

typealias WebCitation = VerifiedCitation

fun extractCleanDomain(url: String): Pair<String, Boolean> {
    val cleanUrl = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    val host = cleanUrl.split("/").firstOrNull()?.split(":")?.firstOrNull()?.lowercase() ?: "Web Source"
    val isEducational = host.endsWith(".edu") || host.contains("khanacademy") ||
            host.contains("wikipedia") || host.contains("britannica") ||
            host.contains("nature.com") || host.contains("sciencedirect") ||
            host.contains("arxiv") || host.contains("mit.edu") || host.contains("stanford.edu") ||
            host.contains("geeksforgeeks") || host.contains("coursera") || host.contains("edx.org")

    val displayName = when {
        host.contains("wikipedia.org") -> "Wikipedia"
        host.contains("khanacademy.org") -> "Khan Academy"
        host.contains("britannica.com") -> "Encyclopaedia Britannica"
        host.contains("nature.com") -> "Nature Journal"
        host.contains("sciencedirect.com") -> "ScienceDirect"
        host.contains("arxiv.org") -> "arXiv Research"
        host.contains("mit.edu") -> "MIT OpenCourseWare"
        host.contains("stanford.edu") -> "Stanford Edu"
        host.contains("geeksforgeeks.org") -> "GeeksforGeeks"
        else -> host.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    return Pair(displayName, isEducational)
}

data class RetrievalResult(
    val factsText: String,
    val citations: List<VerifiedCitation> = emptyList()
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
        val baseResult = if (groqResult != null && groqResult.factsText.isNotBlank()) {
            Log.i(TAG, "Groq succeeded (${groqResult.factsText.length} chars, ${groqResult.citations.size} citations)")
            if (groqResult.citations.isNotEmpty()) {
                groqResult
            } else {
                val openRouterResult = fetchOpenRouterContext(topic, openRouterKey)
                if (openRouterResult.citations.isNotEmpty()) {
                    RetrievalResult(groqResult.factsText, openRouterResult.citations)
                } else {
                    groqResult
                }
            }
        } else {
            Log.i(TAG, "Groq gave nothing usable, falling back to OpenRouter")
            fetchOpenRouterContext(topic, openRouterKey)
        }

        // Only return citations that were actually retrieved from web sources.
        // Never fabricate default citations - an empty list is better than fake references.
        return baseResult
    }

    suspend fun generateOnlineExplanation(
        prompt: String,
        openRouterKey: String,
        groqKey: String
    ): String? {
        if (groqKey.isNotBlank()) {
            try {
                val groqResp = withTimeoutOrNull(7000L) {
                    groqApi.queryOnlineContext(
                        apiKey = "Bearer $groqKey",
                        request = hashMapOf(
                            "model" to "openai/gpt-oss-20b",
                            "temperature" to 0.3,
                            "max_tokens" to 1200,
                            "messages" to listOf(
                                mapOf(
                                    "role" to "system",
                                    "content" to "You are an expert tutor in StudyLens. Provide a clear, comprehensive educational explanation using the requested tags: [INTENT], [TITLE], [SUBJECT], ### 🎯 CORE PRINCIPLE, ### 📐 FORMULA & GIVEN, ### 🔍 STEP-BY-STEP BREAKDOWN, ### 💡 REAL-WORLD ANALOGY, ### ⚠️ COMMON PITFALLS, ### ❓ CHECK YOUR UNDERSTANDING. For coding tasks, provide complete working code with edge cases handled."
                                ),
                                mapOf("role" to "user", "content" to prompt)
                            )
                        )
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val choices = groqResp?.get("choices") as? List<Map<String, Any>>
                val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
                val content = message?.get("content") as? String
                if (!content.isNullOrBlank()) {
                    Log.i(TAG, "Groq online explanation succeeded (${content.length} chars)")
                    return content
                }
            } catch (e: Exception) {
                Log.w(TAG, "Groq online generation failed: ${e.message}")
            }
        }

        if (openRouterKey.isNotBlank()) {
            try {
                val openRouterResp = withTimeoutOrNull(8000L) {
                    openRouterApi.queryOnlineContext(
                        apiKey = "Bearer $openRouterKey",
                        request = hashMapOf(
                            "model" to "perplexity/sonar",
                            "temperature" to 0.3,
                            "max_tokens" to 1000,
                            "messages" to listOf(
                                mapOf(
                                    "role" to "system",
                                    "content" to "You are an expert educational tutor in StudyLens."
                                ),
                                mapOf("role" to "user", "content" to prompt)
                            )
                        )
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val choices = openRouterResp?.get("choices") as? List<Map<String, Any>>
                val message = choices?.firstOrNull()?.get("message") as? Map<String, Any>
                val content = message?.get("content") as? String
                if (!content.isNullOrBlank()) {
                    Log.i(TAG, "OpenRouter online explanation succeeded (${content.length} chars)")
                    return stripInlineCitationMarkers(content)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter online generation failed: ${e.message}")
            }
        }

        return null
    }

    companion object {
        // No default citation fabrication - only real retrieved citations are used.
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
            if (!url.startsWith("http")) return@mapNotNull null
            val (domain, isEdu) = extractCleanDomain(url)
            val title = (urlCitation["title"] as? String)?.takeIf { it.isNotBlank() } ?: domain
            VerifiedCitation(title = title, url = url, domain = domain, isEducational = isEdu)
        }.distinctBy { it.domain }
         .take(3)

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
                    if (!url.startsWith("http")) return@mapNotNull null
                    val (domain, isEdu) = extractCleanDomain(url)
                    val title = (result["title"] as? String)?.takeIf { it.isNotBlank() } ?: domain
                    VerifiedCitation(title = title, url = url, domain = domain, isEducational = isEdu)
                }
            }.distinctBy { it.domain }
             .take(3)
        } catch (e: Exception) {
            emptyList()
        }

        return RetrievalResult(stripInlineCitationMarkers(content), citations)
    }
}
