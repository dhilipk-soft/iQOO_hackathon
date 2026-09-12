package com.studylens.ui.focus

import com.studylens.shared.QuizQuestion

enum class SwitchIntent(val label: String, val description: String) {
    STUDY_NEED("I need this for my study", "Open reference or look up a concept"),
    SHORT_BREAK("I need a short break", "Take a timed mental pause"),
    IMPORTANT_NOTIFICATION("Important message or update", "Check an urgent notification"),
    EMERGENCY_CALL("Urgent emergency call", "Immediate exit with zero delay"),
    DISTRACTED("I'm getting distracted", "Take a quick focus check first")
}

sealed class FocusGuardDialogPhase {
    object SelectIntent : FocusGuardDialogPhase()
    data class QuickFocusCheck(
        val question: QuizQuestion,
        val selectedAnswer: String? = null,
        val isEvaluated: Boolean = false,
        val isCorrect: Boolean = false
    ) : FocusGuardDialogPhase()
    data class ChooseBreakDuration(val isAfterPassedQuiz: Boolean = false) : FocusGuardDialogPhase()
    object EmergencyExitConfirm : FocusGuardDialogPhase()
    object IncorrectAnswerReview : FocusGuardDialogPhase()
    data class StudyNeedAllowed(val note: String = "Good luck with your research. We'll be ready when you return!") : FocusGuardDialogPhase()
    data class ChooseResearchDuration(val defaultSeconds: Long = 15L) : FocusGuardDialogPhase()
}

data class ActiveBreakInfo(
    val plannedDurationMs: Long,
    val startTime: Long,
    val isReminderTriggered: Boolean = false,
    val isResearch: Boolean = false
)

data class ContextRecapInfo(
    val topic: String,
    val awayDurationMs: Long,
    val recapText: String? = null,
    val isLoading: Boolean = false,
    val returnedOnTime: Boolean = false,
    val delaySecs: Long = 0L,
    val wasPlannedBreak: Boolean = false,
    val isAppHoppingDetected: Boolean = false,
    val hopCount: Int = 0
)
