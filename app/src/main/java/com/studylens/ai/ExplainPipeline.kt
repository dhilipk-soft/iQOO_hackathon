package com.studylens.ai

import android.graphics.Bitmap
import com.studylens.BuildConfig
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture

import com.studylens.shared.StudyIntent
import com.studylens.shared.VerifiedCitation

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    private fun appendCitations(text: String, citations: List<VerifiedCitation>): String {
        if (citations.isEmpty()) return text
        return text + "\n\n📚 Verified Sources:\n" + citations.joinToString("\n") { "• ${it.title} (${it.domain})\n  ${it.url}" }
    }

    override suspend fun explain(
        capture: StudyCapture,
        isOnline: Boolean,
        image: Bitmap?,
        preferredIntent: StudyIntent
    ): ExplanationResult {
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

        val prompt = buildStructuredPrompt(
            topic = topicText,
            image = image,
            intent = resolvedIntent,
            retrievalFacts = retrieval.factsText
        )

        var rawResponse = llmEngine.generateResponse(prompt, image)

        val isFailedResponse = rawResponse.isBlank() ||
                rawResponse.startsWith("Sorry,", ignoreCase = true) ||
                rawResponse.contains("couldn't generate an explanation", ignoreCase = true) ||
                rawResponse.contains("taking longer than expected", ignoreCase = true) ||
                rawResponse.contains("model isn't loaded", ignoreCase = true)

        if (isFailedResponse && isOnline) {
            val onlineAnswer = retrievalClient.generateOnlineExplanation(
                prompt = prompt,
                openRouterKey = BuildConfig.OPENROUTER_API_KEY,
                groqKey = BuildConfig.GROQ_API_KEY
            )
            if (!onlineAnswer.isNullOrBlank()) {
                rawResponse = onlineAnswer
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
        val resolvedIntent = StudyIntentClassifier.classify(question, preferredIntent)

        val retrieval = if (isOnline) {
            retrievalClient.fetchOnlineContext(
                question,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        val trimmedContext = conversationContext.takeLast(1500)

        val prompt = buildString {
            append("You are an expert tutor continuing an interactive study session.\n")
            append("Previous conversation context:\n$trimmedContext\n\n")
            append("Student question: \"$question\"\n\n")
            append("Format your response using structured tags:\n")
            append("[INTENT: ${resolvedIntent.name}]\n")
            append("[TITLE: Follow-up on $question]\n\n")
            append("### 🎯 CORE PRINCIPLE\n")
            append("Answer the question directly, thoroughly and accurately in 3-5 sentences.\n\n")
            append("### 🔍 STEP-BY-STEP BREAKDOWN\n")
            append("If applicable, list key steps or points (1. 2. 3.).\n\n")
            append("### ⚠️ COMMON PITFALLS\n")
            append("List any common confusion or mistake related to this.\n\n")
            if (retrieval.factsText.isNotBlank()) {
                append("Verified research facts to synthesize:\n${retrieval.factsText}\n")
            }
        }

        var rawAnswer = llmEngine.generateResponse(prompt)

        val isFailedResponse = rawAnswer.isBlank() ||
                rawAnswer.startsWith("Sorry,", ignoreCase = true) ||
                rawAnswer.contains("couldn't generate an explanation", ignoreCase = true) ||
                rawAnswer.contains("taking longer than expected", ignoreCase = true) ||
                rawAnswer.contains("model isn't loaded", ignoreCase = true)

        if (isFailedResponse && isOnline) {
            val onlineAnswer = retrievalClient.generateOnlineExplanation(
                prompt = prompt,
                openRouterKey = BuildConfig.OPENROUTER_API_KEY,
                groqKey = BuildConfig.GROQ_API_KEY
            )
            if (!onlineAnswer.isNullOrBlank()) {
                rawAnswer = onlineAnswer
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

    private fun buildStructuredPrompt(
        topic: String,
        image: Bitmap?,
        intent: StudyIntent,
        retrievalFacts: String
    ): String = buildString {
        append("You are an expert educational tutor in StudyLens. ")
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

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }
}
