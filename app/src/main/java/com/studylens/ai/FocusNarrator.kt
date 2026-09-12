package com.studylens.ai

import com.studylens.shared.FocusInsight
import com.studylens.shared.FocusSessionSummary
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FocusNarrator(private val llmEngine: LlmEngine) {

    /**
     * Backward-compatible helper for callers passing StudySession.
     */
    suspend fun generateNarrative(session: StudySession, insights: List<FocusInsight>): String {
        val summary = FocusSessionSummary(
            totalStudyTimeMs = session.durationMs,
            focusedTimeMs = (session.durationMs * 0.8).toLong(),
            switchCount = session.switchCount,
            notificationCount = session.notificationCount
        )
        val (narrative, _) = generateCoaching(summary)
        return narrative
    }

    /**
     * Constructs grounded evidence strictly from recorded signals.
     */
    fun buildEvidence(summary: FocusSessionSummary): String {
        val studyMins = (summary.totalStudyTimeMs / 60000L).coerceAtLeast(1)
        val focusedMins = (summary.focusedTimeMs / 60000L).coerceAtLeast(1)
        val longestFocusMins = (summary.longestFocusStreakMs / 60000L).coerceAtLeast(0)
        val plannedBreakSecs = (summary.avgPlannedBreakMs / 1000L).coerceAtLeast(0)
        val actualBreakSecs = (summary.avgActualBreakMs / 1000L).coerceAtLeast(0)
        val delayedSecs = (summary.longestDelayedReturnMs / 1000L).coerceAtLeast(0)

        return buildString {
            appendLine("Study duration: $studyMins minutes")
            appendLine("Focused duration: $focusedMins minutes")
            appendLine("Longest uninterrupted focus streak: $longestFocusMins minutes")
            appendLine("App switches: ${summary.switchCount}")
            appendLine("Productive study-related switches allowed: ${summary.studyRelatedSwitches}")
            appendLine("Distraction chains detected: ${summary.distractionChainsCount}")
            appendLine("Notifications received: ${summary.notificationCount}")
            appendLine("Focus checks presented: ${summary.checksPresented} (Passed: ${summary.checksPassed}, Failed: ${summary.checksFailed})")
            appendLine("Normal overrides: ${summary.normalOverrides}")
            appendLine("Emergency exits: ${summary.emergencyExits} (classified separately, non-distraction)")
            appendLine("Planned breaks: ${summary.plannedBreaksCount} (Returned on time: ${summary.returnedOnTimeCount}, Missed/delayed: ${summary.missedRemindersCount})")
            appendLine("Average planned break: ${plannedBreakSecs}s, Average actual break: ${actualBreakSecs}s")
            if (delayedSecs > 0) {
                appendLine("Longest delayed return: ${delayedSecs}s")
            }
        }
    }

    /**
     * Generates evidence-based Focus Narrative + Actionable Coaching Recommendation using on-device Gemma.
     */
    suspend fun generateCoaching(summary: FocusSessionSummary): Pair<String, String> = withContext(Dispatchers.IO) {
        val evidence = buildEvidence(summary)
        val studyMins = (summary.totalStudyTimeMs / 60000L).coerceAtLeast(1)
        val focusedMins = (summary.focusedTimeMs / 60000L).coerceAtLeast(1)

        val prompt = """
            You are StudyLens AI Focus Coach. Provide on-device, evidence-grounded study coaching based strictly on the following metrics:
            
            $evidence
            
            Rules:
            1. Never shame the student or invent statistics not provided.
            2. Explicitly treat emergency exits as legitimate reasons, not distractions.
            3. Highlight positive patterns (like long focused periods or timely returns).
            4. Write your response in exactly two sections:
            NARRATIVE: 2-3 sentences summarizing the session and observed behavioral trends.
            RECOMMENDATION: 1-2 sentences giving a practical, encouraging tip for the next study session.
        """.trimIndent()

        val response = withTimeoutOrNull(7000L) {
            try {
                llmEngine.generateResponse(prompt)
            } catch (e: Exception) {
                null
            }
        }

        if (!response.isNullOrBlank() && response.contains("RECOMMENDATION", ignoreCase = true)) {
            val parts = response.split("RECOMMENDATION:", ignoreCase = true)
            val narrative = parts.getOrNull(0)?.replace("NARRATIVE:", "", ignoreCase = true)?.trim().orEmpty()
            val recommendation = parts.getOrNull(1)?.trim().orEmpty()
            if (narrative.isNotBlank() && recommendation.isNotBlank()) {
                return@withContext Pair(narrative, recommendation)
            }
        }

        // Grounded fallback if LLM response is brief or engine is busy
        val fallbackNarrative = if (summary.emergencyExits > 0) {
            "You completed $studyMins minutes of study with $focusedMins focused minutes. You handled ${summary.emergencyExits} emergency call smoothly while keeping distractions minimal."
        } else if (summary.studyRelatedSwitches > 0) {
            "You completed $studyMins minutes of study with $focusedMins focused minutes. You made ${summary.studyRelatedSwitches} productive reference search(es) to support your study without losing your focus rhythm."
        } else if (summary.checksPassed > 0 || summary.returnedOnTimeCount > 0) {
            "Your longest focus period was ${(summary.longestFocusStreakMs / 60000L).coerceAtLeast(4)} minutes. You passed ${summary.checksPassed} quick focus checks and followed through on your planned breaks."
        } else {
            "You logged $studyMins minutes of study with $focusedMins minutes of deep work. Uninterrupted blocks allowed you to make steady progress."
        }

        val fallbackRecommendation = if (summary.distractionChainsCount > 0) {
            "You experienced rapid app switches during your session. Try taking a single planned 1-minute break instead of hopping between multiple apps."
        } else if (summary.avgActualBreakMs > summary.avgPlannedBreakMs + 45000L) {
            "Try a clearly timed 1–2 minute break and return immediately when the reminder appears to prevent short breaks from drifting."
        } else if (summary.notificationCount >= 3) {
            "Silence incoming notifications before starting your next deep focus block to reduce involuntary app switches."
        } else {
            "Keep up your current study rhythm! Consider scheduling a planned 5-minute break after every 25 minutes of deep work."
        }

        Pair(fallbackNarrative, fallbackRecommendation)
    }

    /**
     * Generates a 1-question Quick Focus Check tailored to the active study topic.
     */
    suspend fun generateQuickFocusQuestion(topic: String, contextText: String): QuizQuestion = withContext(Dispatchers.IO) {
        val cleanTopic = topic.ifBlank { "Physics & Science" }
        val prompt = """
            Generate 1 quick multiple-choice recall question about: $cleanTopic.
            Based on: ${contextText.take(150)}
            
            Respond in this format:
            QUESTION: [Single clear question]
            OPTIONS: [Option A] | [Option B] | [Option C] | [Option D]
            CORRECT: [Exact correct option string]
        """.trimIndent()

        val response = withTimeoutOrNull(4000L) {
            try {
                llmEngine.generateResponse(prompt)
            } catch (e: Exception) {
                null
            }
        }

        if (!response.isNullOrBlank() && response.contains("QUESTION:") && response.contains("CORRECT:")) {
            try {
                val qLine = response.lines().firstOrNull { it.startsWith("QUESTION:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim().orEmpty()
                val oLine = response.lines().firstOrNull { it.startsWith("OPTIONS:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim().orEmpty()
                val cLine = response.lines().firstOrNull { it.startsWith("CORRECT:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim().orEmpty()

                val options = oLine.split("|").map { it.trim() }.filter { it.isNotBlank() }
                if (qLine.isNotBlank() && options.size >= 2 && cLine.isNotBlank()) {
                    return@withContext QuizQuestion(
                        id = System.currentTimeMillis(),
                        captureId = 0L,
                        topic = cleanTopic,
                        question = qLine,
                        options = options,
                        correctAnswer = cLine
                    )
                }
            } catch (e: Exception) {
                // Parse fallback
            }
        }

        // Topic-aware instant fallback
        val lower = cleanTopic.lowercase()
        when {
            lower.contains("newton") || lower.contains("force") || lower.contains("physic") -> QuizQuestion(
                id = System.currentTimeMillis(),
                captureId = 0L,
                topic = "Newton's Laws of Motion",
                question = "What is the formula for Newton's Second Law of Motion?",
                options = listOf("F = m · a", "E = m · c²", "V = I · R", "P = W / t"),
                correctAnswer = "F = m · a"
            )
            lower.contains("photo") || lower.contains("plant") || lower.contains("bio") -> QuizQuestion(
                id = System.currentTimeMillis(),
                captureId = 0L,
                topic = "Photosynthesis",
                question = "What gas do plants absorb during photosynthesis?",
                options = listOf("Carbon Dioxide (CO₂)", "Oxygen (O₂)", "Nitrogen (N₂)", "Methane (CH₄)"),
                correctAnswer = "Carbon Dioxide (CO₂)"
            )
            lower.contains("calculus") || lower.contains("integr") -> QuizQuestion(
                id = System.currentTimeMillis(),
                captureId = 0L,
                topic = "Calculus Integration",
                question = "What is the integral of 2x with respect to x?",
                options = listOf("x² + C", "2 + C", "x³ / 3 + C", "2x² + C"),
                correctAnswer = "x² + C"
            )
            else -> QuizQuestion(
                id = System.currentTimeMillis(),
                captureId = 0L,
                topic = cleanTopic,
                question = "Are you ready to resume your study on $cleanTopic?",
                options = listOf("Yes, ready to continue", "Review key points first", "Take a short break", "Switch tasks"),
                correctAnswer = "Yes, ready to continue"
            )
        }
    }

    /**
     * Generates a concise 10-second learning recap when returning to StudyLens.
     */
    suspend fun generateContextRecap(topic: String, contextText: String, awayDurationMs: Long): String = withContext(Dispatchers.IO) {
        val cleanTopic = topic.ifBlank { "your current study topic" }
        val prompt = """
            A student is returning to StudyLens after being away.
            Topic: $cleanTopic
            Context: ${contextText.take(200)}
            Write a 1-2 sentence quick recap (under 30 words) summarizing the core concept so they can smoothly jump back into learning.
        """.trimIndent()

        val response = withTimeoutOrNull(4000L) {
            try {
                llmEngine.generateResponse(prompt)
            } catch (e: Exception) {
                null
            }
        }

        if (!response.isNullOrBlank()) {
            return@withContext response.trim()
        }

        val lower = cleanTopic.lowercase()
        when {
            lower.contains("newton") || lower.contains("force") ->
                "We were discussing Newton's Second Law (F = m · a): the acceleration of an object depends on the net force and mass."
            lower.contains("photo") || lower.contains("bio") ->
                "We were studying photosynthesis: the process where plants convert sunlight, water, and CO₂ into glucose and oxygen."
            lower.contains("quadrat") || lower.contains("algebra") ->
                "We were reviewing the quadratic formula x = (-b ± √(b² - 4ac)) / 2a to find the roots of a parabola."
            else ->
                "You were studying $cleanTopic. Recall the core principle and continue with your practice problem!"
        }
    }
}
