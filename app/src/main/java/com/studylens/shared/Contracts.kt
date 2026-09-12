package com.studylens.shared

import kotlinx.coroutines.flow.StateFlow

interface InputProvider {
    suspend fun captureAndExtractText(): StudyCapture
    val isOnline: StateFlow<Boolean>
}

interface StudyBrain {
    suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult
    suspend fun answerFollowUp(capture: StudyCapture, explanation: String, question: String): String
    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion>
}
