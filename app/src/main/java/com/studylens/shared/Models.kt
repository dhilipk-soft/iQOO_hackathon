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

enum class FocusEventType {
    APP_SWITCH,
    QUIZ_PRESENTED,
    QUIZ_PASSED,
    QUIZ_FAILED,
    STAY_AFTER_QUIZ,
    QUIZ_OVERRIDE,
    STUDY_RELATED_SWITCH,
    NORMAL_OVERRIDE,
    EMERGENCY_EXIT,
    BREAK_STARTED,
    REMINDER_SCHEDULED,
    REMINDER_TRIGGERED,
    REMINDER_MISSED,
    RETURNED_TO_STUDY,
    BREAK_COMPLETED,
    NO_RETURN_DETECTED,
    DISTRACTION_CHAIN_DETECTED,
    NOTIFICATION_INTERRUPTION,
    CONTEXT_RECAP_SHOWN,
    CONTEXT_RECAP_USED
}

data class FocusInterruptionEvent(
    val id: Long = 0,
    val sessionId: Long = 0,
    val eventType: FocusEventType,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class FocusMistakeAnalysis(
    val mainLeakReason: String,
    val breakOverrunRateText: String,
    val notificationTriggersCount: Int,
    val distractionChainCount: Int,
    val quizMissesCount: Int,
    val actionPlan: String
)

data class HistoricalFocusTrend(
    val totalSessionsRecorded: Int,
    val avgFocusEfficiencyPct: Int,
    val returnOnTimeRatePct: Int,
    val avgBreakDelaySecs: Long,
    val trendDirection: String, // "IMPROVING", "STABLE", "ATTENTION_NEEDED"
    val summaryText: String
)

data class FocusSessionSummary(
    val sessionId: Long = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val totalStudyTimeMs: Long = 0,
    val focusedTimeMs: Long = 0,
    val longestFocusStreakMs: Long = 0,
    val switchCount: Int = 0,
    val notificationCount: Int = 0,
    val checksPresented: Int = 0,
    val checksPassed: Int = 0,
    val checksFailed: Int = 0,
    val normalOverrides: Int = 0,
    val emergencyExits: Int = 0,
    val plannedBreaksCount: Int = 0,
    val returnedOnTimeCount: Int = 0,
    val missedRemindersCount: Int = 0,
    val avgPlannedBreakMs: Long = 0,
    val avgActualBreakMs: Long = 0,
    val longestDelayedReturnMs: Long = 0,
    val studyRelatedSwitches: Int = 0,
    val distractionChainsCount: Int = 0,
    val timelineEvents: List<FocusInterruptionEvent> = emptyList(),
    val narrative: String = "",
    val recommendation: String = "",
    val isFocusModeActive: Boolean = false,
    val mistakeAnalysis: FocusMistakeAnalysis? = null,
    val historicalTrend: HistoricalFocusTrend? = null,
    val notificationBreakdown: List<FocusNotificationSummaryItem> = emptyList()
)

data class FocusNotificationSummaryItem(
    val packageName: String,
    val appName: String,
    val totalCount: Int,
    val deepFocusCount: Int,
    val breakCount: Int,
    val ledToSwitchCount: Int
)

