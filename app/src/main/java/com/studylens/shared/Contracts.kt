package com.studylens.shared

import kotlinx.coroutines.flow.StateFlow

interface InputProvider {
    suspend fun captureAndExtractText(): StudyCapture
    val isOnline: StateFlow<Boolean>
}

interface StudyBrain {
    suspend fun explain(capture: StudyCapture, isOnline: Boolean): ExplanationResult
    // conversationContext = the running transcript (original explanation + all prior Q&A),
    // not just the first explanation - needed so follow-ups don't lose earlier context.
    suspend fun answerFollowUp(capture: StudyCapture, conversationContext: String, question: String, isOnline: Boolean): ExplanationResult
    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion>
}
