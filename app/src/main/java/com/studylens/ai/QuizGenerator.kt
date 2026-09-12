package com.studylens.ai

import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture

class QuizGenerator(private val llmEngine: LlmEngine) {
    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        return listOf(
            QuizQuestion(
                id = 1L,
                captureId = capture.id,
                topic = "Study Topic",
                question = "What is the key takeaway from the captured study material?",
                options = listOf("Concept A", "Concept B", "Concept C", "Concept D"),
                correctAnswer = "Concept A"
            )
        )
    }
}
