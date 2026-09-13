package com.studylens.ai

import android.graphics.Bitmap
import android.util.Log
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

    companion object {
        private const val TAG = "ExplainPipeline"
    }

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
        if (clean.contains("this chat") || clean.contains("this conversation") || clean.contains("we discuss") || clean.contains("we talk") || clean.contains("whole context") || clean.contains("all context")) {
            return false
        }
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

            Regex("""\b(even\s+numbers?|parity|even\s+and\s+odd|odd\s+and\s+even|what\s+is\s+an?\s+even\s+number|is\s+-?\d+\s+even)\b""").containsMatchIn(lower) -> """
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
        preferredIntent: StudyIntent,
        image: Bitmap?
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

<<<<<<< HEAD
        // 2. Query Resolution:
        // - If there's an image, the question IS about the image; do NOT contaminate with old text topic.
        // - Only prepend mainTopic if this is an implicit pronoun follow-up (no image).
        val mainTopic = capture.extractedText.ifBlank { "Study Topic" }
        val searchQuery = when {
            image != null -> question.ifBlank { "Explain this image in detail." }  // Image = isolated query
            isImplicitFollowUp(question) -> "$mainTopic $question"               // Pronoun follow-up
            else -> question
        }

        val resolvedIntent = StudyIntentClassifier.classify(question, preferredIntent)
        val profile = llmEngine.getActiveProfile()
        val trimmedContext = if (image != null) {
            // For image-based follow-ups, do NOT include previous topic context
            ""
        } else if (isImplicitFollowUp(question)) {
            conversationContext.takeLast(1500)
        } else {
            conversationContext.takeLast(3000)
        }

        val prompt = buildString {
            append(buildSystemHeader(profile))
            append("\n\n")
            if (image != null) {
                append("Analyze the attached image carefully and answer the student's question about it.\n\n")
            } else if (trimmedContext.isNotBlank()) {
                append("Topic Context:\n$trimmedContext\n\n")
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

        // 3. Online-first strategy: When online, use the web-powered generator first
        var rawAnswer = ""
        var usedOnline = false
        var retrieval = RetrievalResult("")

        if (isOnline) {
            // 3a. For non-image queries, also fetch web context for citations
            if (image == null) {
                retrieval = retrievalClient.fetchOnlineContext(
                    searchQuery,
                    BuildConfig.OPENROUTER_API_KEY,
                    BuildConfig.GROQ_API_KEY
                )
            }
            // 3b. Try online generation first (Groq/OpenRouter LLM)
            val onlineAnswer = retrievalClient.generateOnlineExplanation(
                prompt = if (retrieval.factsText.isNotBlank()) {
                    "$prompt\n\nWeb Context:\n${retrieval.factsText}"
                } else {
                    prompt
                },
                openRouterKey = BuildConfig.OPENROUTER_API_KEY,
                groqKey = BuildConfig.GROQ_API_KEY
            )
            if (!onlineAnswer.isNullOrBlank()) {
                rawAnswer = onlineAnswer
                usedOnline = true
                Log.i(TAG, "answerFollowUp: used online generation (${rawAnswer.length} chars)")
=======
        // Keep only recent context (last 350 chars) so the on-device SLM stays comfortably
        // within prefill limits and avoids KV cache overflow.
        val trimmedContext = conversationContext.takeLast(350).trim()

        val prompt = buildString {
            append("You are StudyLens, a knowledgeable and friendly educational AI tutor.\n\n")
            append("Student's Question:\n\"$question\"\n\n")

            if (retrieval.factsText.isNotBlank()) {
                append("Live Information & Enriched Facts:\n")
                append("${retrieval.factsText.trim()}\n\n")
                append("Task: Explain the answer to \"$question\" thoroughly and clearly for the student, incorporating the live facts above.\n")
            } else {
                append("Task: Explain the answer to \"$question\" clearly, thoroughly, and directly for the student using your knowledge.\n")
            }

            append("Guidelines:\n")
            append("- Focus completely on answering \"$question\". Provide a clear definition, core principles, and helpful examples.\n")
            append("- If this is a new question or topic, explain it directly. Do NOT repeat, summarize, or revert to earlier topics unless specifically asked to compare them.\n")
            append("- Do NOT start with \"Based on the context you provided\" or mention these system guidelines.\n\n")

            if (trimmedContext.isNotBlank()) {
                append("Earlier Conversation (for background reference only, if the question refers to previous messages):\n")
                append("$trimmedContext\n\n")
>>>>>>> 6151f416595a7c84b23fc5a115b4ff341926ae5c
            }

            append("Tutor explanation for student:")
        }

        // 4. Fallback to on-device multimodal LLM (with image if present)
        if (rawAnswer.isBlank()) {
            val onDeviceAnswer = llmEngine.generateResponse(prompt, image)
            val isFailedAnswer = onDeviceAnswer.isBlank() ||
                    onDeviceAnswer.startsWith("Sorry,", ignoreCase = true) ||
                    onDeviceAnswer.contains("couldn't generate an explanation", ignoreCase = true) ||
                    onDeviceAnswer.contains("taking longer than expected", ignoreCase = true) ||
                    onDeviceAnswer.contains("model isn't loaded", ignoreCase = true) ||
                    isTrivialOrEcho(onDeviceAnswer, question)

            rawAnswer = if (!isFailedAnswer) {
                onDeviceAnswer
            } else if (retrieval.factsText.isNotBlank()) {
                retrieval.factsText
            } else {
                generateFallbackExplanation(searchQuery)
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
            usedOnlineContext = usedOnline,
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
            append("Main Initial Topic:\n${rootExplanation.take(500)}\n\n")
            if (followUps.isNotEmpty()) {
                append("Topics and Questions Discussed in Session:\n")
                followUps.forEachIndexed { idx, fu ->
                    val cleanSnippet = fu.answer.trim().lines().firstOrNull { it.isNotBlank() }?.take(160) ?: fu.answer.take(160)
                    append("[${idx + 1}] Question: ${fu.question}\n    Summary: $cleanSnippet\n\n")
                }
            }
        }

        return SummaryContextResult(
            transcript = if (fullTranscript.length > maxChars) fullTranscript.take(maxChars) else fullTranscript,
            mode = "Full Session Summary (${followUps.size + 1} topics evaluated)",
            totalMessagesEvaluated = followUps.size + 1
        )
    }

    suspend fun summarizeConversation(
        rootExplanation: String,
        followUps: List<FollowUpMessage>,
        maxTokens: Int = 1536
    ): ExplanationResult {
        val contextResult = buildAdaptiveSummaryContext(rootExplanation, followUps, maxTokens)

        llmEngine.resetSession()

        val prompt = buildString {
            append("You are an expert study tutor. Summarize the following study session clearly and concisely covering all distinct topics.\n\n")
            append("CONVERSATION TRANSCRIPT (${contextResult.mode}):\n")
            append("${contextResult.transcript}\n\n")
            append("INSTRUCTIONS:\n")
            append("1. Provide a structured summary covering ALL topics discussed above in 3 sections:\n")
            append("   📌 Core Concepts & Main Subjects Covered\n")
            append("   💡 Key Q&As & Questions Answered\n")
            append("   🔑 Final Takeaways to Remember\n")
            append("2. Keep the summary concise, accurate, and strictly relevant to all topics discussed.\n")
            append("3. Do not invent unrelated topics.")
        }

        var summaryText = llmEngine.generateResponse(prompt)
        if (summaryText.isBlank() || summaryText.length < 20) {
            val allTopics = (listOf(rootExplanation.take(60)) + followUps.map { it.question }).distinct()
            summaryText = "📌 Core Concepts & Scope: Multi-topic study session covering: ${allTopics.joinToString(" • ")}.\n\n💡 Key Q&As: Addressed ${followUps.size} key questions comprehensively.\n\n🔑 Final Takeaways: Review and compare foundational characteristics and practical implementations across each topic."
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
