package com.studylens.ai

import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    override suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult {
        val onlineContext = if (isOnline) {
            retrievalClient.fetchOnlineContext(capture.extractedText, "")
        } else {
            ""
        }
        val prompt = "Explain the following concept succinctly:\nText: ${capture.extractedText}\nContext: $onlineContext"
        val explanation = llmEngine.generateResponse(prompt)

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = explanation,
            usedOnlineContext = isOnline && onlineContext.isNotEmpty()
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
