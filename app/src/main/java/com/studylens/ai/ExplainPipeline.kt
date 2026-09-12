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

    // Step 1 (retrieve, only if online) -> Step 2 (combine) -> Step 3 (generate, always local)
    override suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult {
        val retrievedContext = if (isOnline) {
            retrievalClient.fetchOnlineContext(capture.extractedText, BuildConfig.OPENROUTER_API_KEY)
        } else {
            ""
        }

        val prompt = buildString {
            append("You are a patient tutor explaining to a student with limited internet access. ")
            append("Explain simply, in plain language, in 3-4 sentences.\n\n")
            append("Content: ${capture.extractedText}\n")
            if (retrievedContext.isNotBlank()) {
                append("\nAdditional current context you may use if relevant:\n$retrievedContext\n")
            }
        }
        val explanation = llmEngine.generateResponse(prompt) // always runs, on-device, this is the guarantee

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = explanation,
            usedOnlineContext = retrievedContext.isNotBlank()
        )
    }

    override suspend fun answerFollowUp(capture: StudyCapture, explanation: String, question: String): String {
        val prompt = "Based on text: ${capture.extractedText} and explanation: $explanation\nAnswer question: $question"
        return llmEngine.generateResponse(prompt)
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }
}
