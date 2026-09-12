package com.studylens.ai

import com.studylens.shared.FocusInsight
import com.studylens.shared.StudySession

class FocusNarrator(private val llmEngine: LlmEngine) {
    suspend fun generateNarrative(session: StudySession, insights: List<FocusInsight>): String {
        return "You stayed focused for ${session.durationMs / 60000} minutes with ${session.switchCount} app switches."
    }
}
