package com.studylens.shared

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

interface InputProvider {
    suspend fun captureAndExtractText(): StudyCapture
    val isOnline: StateFlow<Boolean>
}

interface StudyBrain {
    // image != null means the model reads the photo directly (multimodal) instead of
    // going through OCR first - capture.extractedText may be blank in that case.
    suspend fun explain(capture: StudyCapture, isOnline: Boolean, image: Bitmap? = null): ExplanationResult
    // conversationContext = the running transcript (original explanation + all prior Q&A),
    // not just the first explanation - needed so follow-ups don't lose earlier context.
    suspend fun answerFollowUp(capture: StudyCapture, conversationContext: String, question: String, isOnline: Boolean): ExplanationResult
    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion>
}
