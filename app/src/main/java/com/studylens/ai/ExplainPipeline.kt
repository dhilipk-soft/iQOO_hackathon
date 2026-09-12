package com.studylens.ai

import android.util.Log
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    override suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult {
        Log.d(TAG, "explain requested for Capture ID=${capture.id}, isOnline=$isOnline")
        val queryText = capture.extractedText.ifBlank { "General Topic" }

        val onlineContext = if (isOnline) {
            Log.d(TAG, "Fetching online web enrichment context...")
            retrievalClient.fetchOnlineContext(queryText, "")
        } else {
            Log.d(TAG, "Offline mode active, skipping online retrieval.")
            ""
        }

        val explanation = llmEngine.generateResponse(queryText)
        Log.i(TAG, "Explanation generated successfully (length=${explanation.length} chars).")

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = explanation,
            usedOnlineContext = isOnline && onlineContext.isNotEmpty()
        )
    }

    override suspend fun answerFollowUp(capture: StudyCapture, explanation: String, question: String): String {
        Log.d(TAG, "answerFollowUp called with question: '$question'")
        val answer = llmEngine.generateResponse(question)
        Log.i(TAG, "Follow-up answer generated (length=${answer.length} chars).")
        return answer
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        Log.d(TAG, "generateQuiz requested for Capture ID=${capture.id}")
        val quizGen = QuizGenerator(llmEngine)
        val questions = quizGen.generateQuiz(capture)
        Log.i(TAG, "Quiz generated: ${questions.size} questions.")
        return questions
    }

    companion object {
        private const val TAG = "ExplainPipeline"
    }
}


