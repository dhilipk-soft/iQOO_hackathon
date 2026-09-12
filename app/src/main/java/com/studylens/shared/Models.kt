package com.studylens.shared

data class StudyCapture(val id: Long, val extractedText: String, val timestamp: Long)

data class ExplanationResult(
    val captureId: Long,
    val finalExplanation: String,
    val usedOnlineContext: Boolean
)

data class QuizQuestion(
    val id: Long, val captureId: Long, val topic: String,
    val question: String, val options: List<String>?, val correctAnswer: String
)
data class QuizAttempt(val questionId: Long, val userAnswer: String, val isCorrect: Boolean, val timestamp: Long)

data class InferenceStats(
    val tokensPerSecond: Double, val latencyMs: Long,
    val ramUsedMb: Long, val thermalStatus: String
)

// Focus Insights (build after 7 PM)
data class AppEvent(val id: Long, val packageName: String, val eventType: Int, val timestamp: Long)
data class NotificationEvent(val id: Long, val packageName: String, val timestamp: Long)
data class StudySession(
    val id: Long, val startTime: Long, val endTime: Long,
    val durationMs: Long, val switchCount: Int, val notificationCount: Int
)
data class FocusInsight(val type: String, val evidence: String, var narrative: String? = null)
