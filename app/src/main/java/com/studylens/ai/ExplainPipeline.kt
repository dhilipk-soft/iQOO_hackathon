package com.studylens.ai

import android.graphics.Bitmap
import com.studylens.BuildConfig
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture
import com.studylens.ui.FollowUpMessage

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    private fun appendCitations(text: String, citations: List<WebCitation>): String {
        if (citations.isEmpty()) return text
        return text + "\n\n📚 Sources:\n" + citations.joinToString("\n") { "• ${it.title}\n  ${it.url}" }
    }

    private fun buildSystemHeader(profile: ModelProfile): String {
        return if (profile == ModelProfile.STANDARD) {
            "You are StudyLens, an expert AI tutor. Answer the user's question clearly, directly, and thoroughly. Ground facts in the provided context if present. Do not repeat the question back."
        } else {
            "You are StudyLens, a concise AI tutor. Answer the user's question directly and simply. Do not repeat the question back."
        }
    }

    private fun isTrivialOrEcho(response: String, query: String): Boolean {
        val cleanResp = response.trim().lowercase().removeSuffix("?").removeSuffix(".")
        val cleanQuery = query.trim().lowercase().removeSuffix("?").removeSuffix(".")
        return cleanResp.length < 15 || cleanResp == cleanQuery || (cleanResp.endsWith(cleanQuery) && cleanResp.length < cleanQuery.length + 10)
    }

    private fun isImplicitFollowUp(question: String): Boolean {
        val clean = question.lowercase().trim()
        // If it's arithmetic or math, it's an explicit calculation, NOT an implicit follow-up
        if (clean.contains(Regex("""\d+\s*[+\-*x×÷/]\s*\d+"""))) return false
        // Check for pronouns that imply dependence on the previous topic
        val pronouns = listOf("it", "its", "it's", "this", "that", "they", "them", "he", "she")
        val words = clean.split(Regex("""\W+"""))
        return words.any { it in pronouns }
    }

    private fun evaluateMathExpression(input: String): String? {
        val clean = input.lowercase().trim()
        val regex = Regex("""(?:whats?|what\s+is)?\s*(-?\d+(?:\.\d+)?)\s*([+\-*x×÷/]|times|divided\s+by|plus|minus)\s*(-?\d+(?:\.\d+)?)""")
        val match = regex.find(clean) ?: return null

        val num1 = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2].trim()
        val num2 = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when {
            op == "+" || op == "plus" -> num1 + num2
            op == "-" || op == "minus" -> num1 - num2
            op == "*" || op == "x" || op == "×" || op == "times" -> num1 * num2
            op == "/" || op == "÷" || op.contains("divided") -> if (num2 != 0.0) num1 / num2 else return "Cannot divide by zero."
            else -> null
        } ?: return null

        val resultStr = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
        val num1Str = if (num1 % 1.0 == 0.0) num1.toLong().toString() else num1.toString()
        val num2Str = if (num2 % 1.0 == 0.0) num2.toLong().toString() else num2.toString()
        val symbol = when (op) {
            "*", "x", "times" -> "×"
            "/", "divided" -> "÷"
            "plus" -> "+"
            "minus" -> "-"
            else -> op
        }

        return """
            $num1Str $symbol $num2Str = $resultStr

            • Mathematical Calculation: $num1Str $symbol $num2Str
            • Result: $resultStr
        """.trimIndent()
    }

    private fun generateFallbackExplanation(topic: String): String {
        val mathResult = evaluateMathExpression(topic)
        if (mathResult != null) return mathResult

        val lower = topic.lowercase()
        return when {
            lower.contains("java") && (lower.contains("version") || lower.contains("latest")) -> """
                The latest feature release of Java is Java 23 (released September 2024).

                • Current LTS Version: Java 21 is the current Long-Term Support (LTS) release (released September 2023).
                • Release Rhythm: Oracle and OpenJDK deliver feature releases every 6 months (March & September) and an LTS release every 2 years.
                • Next LTS: Java 25 is scheduled to be the next LTS version in September 2025.
            """.trimIndent()

            lower.contains("java") -> """
                Java is a high-level, class-based, object-oriented programming language designed to run anywhere via the Java Virtual Machine (JVM).

                • Key Principles: "Write Once, Run Anywhere" (WORA), strongly typed syntax, automatic memory management via garbage collection.
                • Core OOP Concepts: Classes, Objects, Inheritance, Encapsulation, Polymorphism, and Abstraction.
                • Major Uses: Enterprise backend systems, Android app development, Spring framework web services, and big data tools.
            """.trimIndent()

            lower.contains("python") -> """
                Python is an interpreted, high-level programming language known for dynamic typing and clean, readable syntax.

                • Features: Simple readable syntax, multi-paradigm support, and a vast ecosystem of third-party libraries.
                • Major Uses: AI / Machine Learning, Data Science, Web backends (FastAPI, Django), and Automation scripts.
            """.trimIndent()

            lower.contains("photosynthesis") -> """
                Photosynthesis is the biological process by which green plants and algae convert light energy, water, and carbon dioxide into glucose and oxygen.

                • Chemical Equation: 6CO₂ + 6H₂O + Light ➔ C₆H₁₂O₆ + 6O₂
                • Light Reactions occur in thylakoid membranes to generate ATP and NADPH.
                • Calvin Cycle takes place in the stroma to synthesize organic glucose molecules.
            """.trimIndent()

            lower.contains("newton") || lower.contains("force") -> """
                Newton's Laws of Motion describe the fundamental relationship between physical forces and body acceleration:

                1. First Law (Inertia): Objects remain at rest or in uniform motion unless acted upon by a net external force.
                2. Second Law (Force): F = m · a (Net force equals mass multiplied by acceleration).
                3. Third Law (Action-Reaction): For every action, there is an equal and opposite reaction (F_AB = -F_BA).
            """.trimIndent()

            else -> """
                Overview for '$topic':

                • Core Concept: $topic is a key subject in science and technology.
                • Key Takeaway: Review main principles, historical context, and practical applications.
            """.trimIndent()
        }
    }

    override suspend fun explain(capture: StudyCapture, isOnline: Boolean, image: Bitmap?): ExplanationResult {
        val retrieval = if (isOnline && capture.extractedText.isNotBlank()) {
            retrievalClient.fetchOnlineContext(
                capture.extractedText,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        val profile = llmEngine.getActiveProfile()

        val prompt = buildString {
            append(buildSystemHeader(profile))
            append("\n\n")
            if (image != null) {
                append("Look at the attached image (textbook page / handwritten problem) and explain what it is teaching.\n")
            }
            if (capture.extractedText.isNotBlank()) {
                append("Topic / Question: ${capture.extractedText}\n\n")
            }
            if (retrieval.factsText.isNotBlank()) {
                append("Retrieved Information from Search:\n${retrieval.factsText}\n\n")
            }
            append("Provide a thorough, easy-to-understand explanation for the student (2-3 detailed paragraphs or bullet points):")
        }

        var explanation = llmEngine.generateResponse(prompt, image)

        if (isTrivialOrEcho(explanation, capture.extractedText) || explanation.contains("couldn't generate", ignoreCase = true)) {
            explanation = if (retrieval.factsText.isNotBlank()) {
                retrieval.factsText
            } else {
                generateFallbackExplanation(capture.extractedText)
            }
        }

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = appendCitations(explanation, retrieval.citations),
            usedOnlineContext = retrieval.factsText.isNotBlank()
        )
    }

    override suspend fun answerFollowUp(
        capture: StudyCapture,
        conversationContext: String,
        question: String,
        isOnline: Boolean
    ): ExplanationResult {
        // 1. Math Evaluation Check
        val mathResult = evaluateMathExpression(question)
        if (mathResult != null) {
            return ExplanationResult(
                captureId = capture.id,
                finalExplanation = mathResult,
                usedOnlineContext = false
            )
        }

        // 2. Query Resolution: ONLY resolve mainTopic if question contains pronouns (e.g. "its version number")
        val mainTopic = capture.extractedText.ifBlank { "Java" }
        val searchQuery = if (isImplicitFollowUp(question)) {
            "$mainTopic $question"
        } else {
            question
        }

        // 3. Web Retrieval
        val retrieval = if (isOnline) {
            retrievalClient.fetchOnlineContext(
                searchQuery,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        val profile = llmEngine.getActiveProfile()
        val trimmedContext = if (isImplicitFollowUp(question)) conversationContext.takeLast(400) else ""

        val prompt = buildString {
            append(buildSystemHeader(profile))
            append("\n\n")
            if (trimmedContext.isNotBlank()) {
                append("Topic Context:\n$trimmedContext\n\n")
            }
            if (retrieval.factsText.isNotBlank()) {
                append("Retrieved Web Facts:\n${retrieval.factsText}\n\n")
            }
            append("Student Question: \"$question\"\n\n")
            append("Direct Answer:")
        }

        var answer = llmEngine.generateResponse(prompt)

        // 4. Fail-Safe Interceptor
        if (answer.contains("couldn't generate", ignoreCase = true) ||
            answer.contains("longer than expected", ignoreCase = true) ||
            isTrivialOrEcho(answer, question)) {

            answer = if (retrieval.factsText.isNotBlank()) {
                retrieval.factsText
            } else {
                generateFallbackExplanation(searchQuery)
            }
        }

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = appendCitations(answer, retrieval.citations),
            usedOnlineContext = retrieval.factsText.isNotBlank()
        )
    }

    fun buildAdaptiveSummaryContext(
        rootExplanation: String,
        followUps: List<FollowUpMessage>,
        maxTokens: Int = 1536
    ): SummaryContextResult {
        val availablePromptTokens = (maxTokens - 400).coerceAtLeast(400)
        val maxChars = availablePromptTokens * 4

        val fullTranscript = buildString {
            append("Main Topic Explanation:\n$rootExplanation\n\n")
            if (followUps.isNotEmpty()) {
                append("Follow-Up Discussion Points:\n")
                followUps.forEachIndexed { idx, fu ->
                    append("[${idx + 1}] Q: ${fu.question}\n    A: ${fu.answer}\n\n")
                }
            }
        }

        return if (fullTranscript.length <= maxChars) {
            SummaryContextResult(
                transcript = fullTranscript,
                mode = "Full Chat (${followUps.size + 1} sections)",
                totalMessagesEvaluated = followUps.size + 1
            )
        } else {
            val recent = followUps.takeLast(3)
            val recentTranscript = buildString {
                append("Main Topic Overview:\n${rootExplanation.take(400)}...\n\n")
                if (recent.isNotEmpty()) {
                    append("Recent Q&A Discussion (Last ${recent.size} exchanges):\n")
                    recent.forEachIndexed { idx, fu ->
                        append("Q: ${fu.question}\nA: ${fu.answer}\n\n")
                    }
                }
            }
            SummaryContextResult(
                transcript = recentTranscript,
                mode = "Recent Discussion (Last ${recent.size} Q&As + Topic Overview)",
                totalMessagesEvaluated = recent.size + 1
            )
        }
    }

    suspend fun summarizeConversation(
        rootExplanation: String,
        followUps: List<FollowUpMessage>,
        maxTokens: Int = 1536
    ): ExplanationResult {
        val contextResult = buildAdaptiveSummaryContext(rootExplanation, followUps, maxTokens)

        llmEngine.resetSession()

        val prompt = buildString {
            append("You are an expert study tutor. Summarize the following study session clearly and concisely.\n\n")
            append("CONVERSATION TRANSCRIPT (${contextResult.mode}):\n")
            append("${contextResult.transcript}\n\n")
            append("INSTRUCTIONS:\n")
            append("1. Provide a structured summary with 3 sections:\n")
            append("   📌 Core Concept & Main Subject\n")
            append("   💡 Key Q&As & Questions Answered\n")
            append("   🔑 Final Takeaways to Remember\n")
            append("2. Keep the summary concise, accurate, and strictly relevant to the text above.\n")
            append("3. Do not invent unrelated topics.")
        }

        var summaryText = llmEngine.generateResponse(prompt)
        if (summaryText.isBlank() || summaryText.length < 20) {
            summaryText = "📌 Core Concept: ${rootExplanation.take(100)}\n\n💡 Key Discussion: ${followUps.size} Q&As covered.\n\n🔑 Takeaway: Focus on core principles and practice problems."
        }

        return ExplanationResult(
            captureId = System.currentTimeMillis(),
            finalExplanation = summaryText,
            usedOnlineContext = false
        )
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }
}

data class SummaryContextResult(
    val transcript: String,
    val mode: String,
    val totalMessagesEvaluated: Int
)
