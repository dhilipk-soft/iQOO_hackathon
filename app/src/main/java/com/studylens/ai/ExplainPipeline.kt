package com.studylens.ai

import com.studylens.BuildConfig
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    private fun appendCitations(text: String, citations: List<WebCitation>): String {
        if (citations.isEmpty()) return text
        return text + "\n\n📚 Sources:\n" + citations.joinToString("\n") { "• ${it.title}\n  ${it.url}" }
    }

    // Step 1 (retrieve, only if online) -> Step 2 (combine) -> Step 3 (generate, always local)
    override suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult {
        val retrieval = if (isOnline) {
            retrievalClient.fetchOnlineContext(capture.extractedText, BuildConfig.OPENROUTER_API_KEY)
        } else {
            RetrievalResult("")
        }

        val prompt = buildString {
            append("You are a patient tutor explaining to a student with limited internet access. ")
            append("Give a clear, detailed explanation - aim for 5-8 sentences (more if the topic genuinely ")
            append("needs it). Be thorough, don't pad with filler, but don't be overly brief either.\n\n")
            append("Content: ${capture.extractedText}\n")
            if (retrieval.factsText.isNotBlank()) {
                append("\nAdditional current context you may use if relevant:\n${retrieval.factsText}\n")
            }
        }
        val explanation = llmEngine.generateResponse(prompt) // always runs, on-device, this is the guarantee

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
        // Retrieve using the actual follow-up question, not the original captured text -
        // that's what's actually relevant to this specific turn of the conversation.
        val retrieval = if (isOnline) {
            retrievalClient.fetchOnlineContext(question, BuildConfig.OPENROUTER_API_KEY)
        } else {
            RetrievalResult("")
        }

        // Keep only the most recent part of a long-running conversation so the prompt stays
        // small enough to leave the model room to actually answer.
        val trimmedContext = conversationContext.takeLast(1500)

        val prompt = buildString {
            append("You are continuing a tutoring conversation. Here is the conversation so far:\n")
            append("$trimmedContext\n\n")
            append("The student now asks: \"$question\"\n\n")
            append("Answer this new question directly and thoroughly (aim for 5-8 sentences where the ")
            append("topic warrants it). Use the conversation above for context ONLY if the new question ")
            append("is actually related to it - if it's a new, unrelated topic, just answer it on its own ")
            append("terms using your own knowledge. Do not repeat the question back, and do not just ")
            append("restate earlier answers.")
            if (retrieval.factsText.isNotBlank()) {
                append("\n\nAdditional current context you may use if relevant:\n${retrieval.factsText}")
            }
        }
        val answer = llmEngine.generateResponse(prompt)

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = appendCitations(answer, retrieval.citations),
            usedOnlineContext = retrieval.factsText.isNotBlank()
        )
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }
}
