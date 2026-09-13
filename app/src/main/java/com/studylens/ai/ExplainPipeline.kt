package com.studylens.ai

import android.graphics.Bitmap
import com.studylens.BuildConfig
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudyIntent
import com.studylens.shared.VerifiedCitation
import com.studylens.ui.FollowUpMessage

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    private fun appendCitations(text: String, citations: List<VerifiedCitation>): String {
        if (citations.isEmpty()) return text
        return text + "\n\n📚 Verified Sources:\n" + citations.joinToString("\n") { "• ${it.title} (${it.domain})\n  ${it.url}" }
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

            lower.contains("even number") || (lower.contains("even") && !lower.contains("evening")) -> """
                An even number is an integer that is exactly divisible by 2 with no remainder, written as n = 2k (for k ∈ ℤ).

                • Parity Criterion: n mod 2 == 0. Any number ending in 0, 2, 4, 6, 8 is even.
                • Edge Cases: 0 is an even integer (0 = 2 × 0); negative numbers like -2, -4 are even.
                • Prime Property: 2 is the ONLY even prime number in all of mathematics.
            """.trimIndent()

            else -> """
                Overview for '$topic':

                • Core Concept: $topic is a key subject in science and technology.
                • Key Takeaway: Review main principles, historical context, and practical applications.
            """.trimIndent()
        }
    }

    override suspend fun explain(
        capture: StudyCapture,
        isOnline: Boolean,
        image: Bitmap?,
        preferredIntent: StudyIntent
    ): ExplanationResult {
        // 1. Math instant evaluation check
        val mathResult = evaluateMathExpression(capture.extractedText)
        if (mathResult != null) {
            val structured = StructuredStudyResponseParser.parse(
                rawOutput = mathResult,
                fallbackTopic = capture.extractedText,
                inferredIntent = StudyIntent.STEP_BY_STEP_SOLVER,
                citations = emptyList()
            )
            return ExplanationResult(
                captureId = capture.id,
                finalExplanation = mathResult,
                usedOnlineContext = false,
                structuredResponse = structured,
                citations = emptyList()
            )
        }

        val topicText = capture.extractedText
        val resolvedIntent = StudyIntentClassifier.classify(topicText, preferredIntent)

        val retrieval = if (isOnline && topicText.isNotBlank()) {
            retrievalClient.fetchOnlineContext(
                topicText,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        val profile = llmEngine.getActiveProfile()
        val prompt = buildStructuredPrompt(
            topic = topicText,
            image = image,
            intent = resolvedIntent,
            retrievalFacts = retrieval.factsText,
            profile = profile
        )

        var rawResponse = llmEngine.generateResponse(prompt, image)

        val isFailedResponse = rawResponse.isBlank() ||
                rawResponse.startsWith("Sorry,", ignoreCase = true) ||
                rawResponse.contains("couldn't generate an explanation", ignoreCase = true) ||
                rawResponse.contains("taking longer than expected", ignoreCase = true) ||
                rawResponse.contains("model isn't loaded", ignoreCase = true) ||
                isTrivialOrEcho(rawResponse, topicText)

        if (isFailedResponse) {
            if (isOnline) {
                val onlineAnswer = retrievalClient.generateOnlineExplanation(
                    prompt = prompt,
                    openRouterKey = BuildConfig.OPENROUTER_API_KEY,
                    groqKey = BuildConfig.GROQ_API_KEY
                )
                if (!onlineAnswer.isNullOrBlank()) {
                    rawResponse = onlineAnswer
                }
            } else if (retrieval.factsText.isNotBlank()) {
                rawResponse = retrieval.factsText
            } else {
                rawResponse = generateFallbackExplanation(topicText)
            }
        }

        val structured = StructuredStudyResponseParser.parse(
            rawOutput = rawResponse,
            fallbackTopic = topicText.ifBlank { "Study Session" },
            inferredIntent = resolvedIntent,
            citations = retrieval.citations
        )

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = structured.coreConcept,
            usedOnlineContext = retrieval.factsText.isNotBlank(),
            structuredResponse = structured,
            citations = retrieval.citations
        )
    }

    override suspend fun answerFollowUp(
        capture: StudyCapture,
        conversationContext: String,
        question: String,
        isOnline: Boolean,
        preferredIntent: StudyIntent
    ): ExplanationResult {
        // 1. Math Evaluation Check
        val mathResult = evaluateMathExpression(question)
        if (mathResult != null) {
            val structured = StructuredStudyResponseParser.parse(
                rawOutput = mathResult,
                fallbackTopic = question,
                inferredIntent = StudyIntent.STEP_BY_STEP_SOLVER,
                citations = emptyList()
            )
            return ExplanationResult(
                captureId = capture.id,
                finalExplanation = mathResult,
                usedOnlineContext = false,
                structuredResponse = structured,
                citations = emptyList()
            )
        }

        // 2. Query Resolution: ONLY resolve mainTopic if question contains pronouns
        val mainTopic = capture.extractedText.ifBlank { "Study Topic" }
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

        val resolvedIntent = StudyIntentClassifier.classify(question, preferredIntent)
        val profile = llmEngine.getActiveProfile()
        val trimmedContext = if (isImplicitFollowUp(question)) conversationContext.takeLast(400) else conversationContext.takeLast(1000)

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
            append("Format your response using structured tags:\n")
            append("[INTENT: ${resolvedIntent.name}]\n")
            append("[TITLE: Follow-up on $question]\n\n")
            append("### 🎯 CORE PRINCIPLE\n")
            append("Answer the question directly, thoroughly and accurately in 3-5 sentences.\n\n")
            append("### 🔍 STEP-BY-STEP BREAKDOWN\n")
            append("If applicable, list key steps or points (1. 2. 3.).\n\n")
            append("### ⚠️ COMMON PITFALLS\n")
            append("List any common confusion or mistake related to this.\n\n")
            append("Direct Answer:")
        }

        var rawAnswer = llmEngine.generateResponse(prompt)

        val isFailedAnswer = rawAnswer.isBlank() ||
                rawAnswer.startsWith("Sorry,", ignoreCase = true) ||
                rawAnswer.contains("couldn't generate an explanation", ignoreCase = true) ||
                rawAnswer.contains("taking longer than expected", ignoreCase = true) ||
                rawAnswer.contains("model isn't loaded", ignoreCase = true) ||
                isTrivialOrEcho(rawAnswer, question)

        if (isFailedAnswer) {
            if (isOnline) {
                val onlineAnswer = retrievalClient.generateOnlineExplanation(
                    prompt = prompt,
                    openRouterKey = BuildConfig.OPENROUTER_API_KEY,
                    groqKey = BuildConfig.GROQ_API_KEY
                )
                if (!onlineAnswer.isNullOrBlank()) {
                    rawAnswer = onlineAnswer
                }
            } else if (retrieval.factsText.isNotBlank()) {
                rawAnswer = retrieval.factsText
            } else {
                rawAnswer = generateFallbackExplanation(searchQuery)
            }
        }

        val structured = StructuredStudyResponseParser.parse(
            rawOutput = rawAnswer,
            fallbackTopic = question,
            inferredIntent = resolvedIntent,
            citations = retrieval.citations
        )

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = structured.coreConcept,
            usedOnlineContext = retrieval.factsText.isNotBlank(),
            structuredResponse = structured,
            citations = retrieval.citations
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

        val structured = StructuredStudyResponseParser.parse(
            rawOutput = summaryText,
            fallbackTopic = "Session Summary",
            inferredIntent = StudyIntent.REVISION_SUMMARY,
            citations = emptyList()
        )

        return ExplanationResult(
            captureId = System.currentTimeMillis(),
            finalExplanation = structured.coreConcept,
            usedOnlineContext = false,
            structuredResponse = structured,
            citations = emptyList()
        )
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }

    private fun buildStructuredPrompt(
        topic: String,
        image: Bitmap?,
        intent: StudyIntent,
        retrievalFacts: String,
        profile: ModelProfile = ModelProfile.STANDARD
    ): String = buildString {
        append(buildSystemHeader(profile))
        append(" ")
        if (image != null) {
            append("Analyze the attached textbook page or diagram carefully. ")
        }
        append("Your response MUST use the following structured tags and headings:\n\n")
        append("[INTENT: ${intent.name}]\n")
        append("[TITLE: Short descriptive topic title]\n")
        append("[SUBJECT: Academic subject e.g. Physics, Algebra, Biology, Computer Science]\n\n")

        append("### 🎯 CORE PRINCIPLE\n")
        when (intent) {
            StudyIntent.STEP_BY_STEP_SOLVER ->
                append("Clearly restate the problem and define all given variables.\n\n")
            StudyIntent.REVISION_SUMMARY ->
                append("Executive summary of the key concept and core definitions.\n\n")
            StudyIntent.CODE_AND_ALGORITHM ->
                append("Explain the algorithmic approach, data structures, and methodology.\n\n")
            else ->
                append("Explain the fundamental concept clearly and intuitively for a student.\n\n")
        }

        append("### 📐 FORMULA & GIVEN\n")
        if (intent == StudyIntent.CODE_AND_ALGORITHM) {
            append("Provide the clean code snippet with clear comments.\n\n")
        } else {
            append("State the primary governing formula, equation, or theorem.\n\n")
        }

        append("### 🔍 STEP-BY-STEP BREAKDOWN\n")
        append("1. First step or derivation\n2. Next step\n3. Final result or conclusion\n\n")

        append("### 💡 REAL-WORLD ANALOGY\n")
        append("Provide an intuitive real-world analogy that makes the concept unforgettable.\n\n")

        append("### ⚠️ COMMON PITFALLS\n")
        append("• Key mistake students frequently make on exams\n• What to watch out for\n\n")

        append("### ❓ CHECK YOUR UNDERSTANDING\n")
        append("Pose a single quick conceptual question testing the student's understanding.\n")
        append("[ANSWER: The concise correct answer and explanation]\n\n")

        if (topic.isNotBlank()) {
            append("Student Material / Query:\n$topic\n\n")
        }

        if (retrievalFacts.isNotBlank()) {
            append("Verified online background facts (synthesize into the explanation):\n$retrievalFacts\n\n")
        }
    }
}

data class SummaryContextResult(
    val transcript: String,
    val mode: String,
    val totalMessagesEvaluated: Int
)
