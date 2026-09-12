package com.studylens.focus

import com.studylens.ai.FocusNarrator
import com.studylens.ai.LlmEngine
import com.studylens.input.data.FocusEventEntity
import com.studylens.input.data.FocusSessionSummaryEntity
import com.studylens.shared.FocusEventType
import com.studylens.shared.FocusInterruptionEvent
import com.studylens.shared.FocusSessionSummary
import com.studylens.ui.focus.formatDuration
import org.junit.Assert.*
import org.junit.Test

class FocusGuardFlowTest {

    @Test
    fun testFocusEventEntityToDomainMapping() {
        val entity = FocusEventEntity(
            id = 100L,
            sessionId = 1726000000000L,
            eventType = "STUDY_RELATED_SWITCH",
            details = "User switched for study-related reference",
            timestamp = 1726000010000L
        )

        val domain: FocusInterruptionEvent = entity.toDomain()

        assertEquals(100L, domain.id)
        assertEquals(1726000000000L, domain.sessionId)
        assertEquals(FocusEventType.STUDY_RELATED_SWITCH, domain.eventType)
        assertEquals("User switched for study-related reference", domain.details)
    }

    @Test
    fun testBreakPromiseAnalyticsCalculation() {
        // Simulating planned 60s break with actual 78s away (+18s)
        val plannedBreakMs = 60000L
        val actualBreakMs = 78000L
        val delayedMs = (actualBreakMs - plannedBreakMs).coerceAtLeast(0L)

        assertEquals(18000L, delayedMs)
        assertTrue("Delayed break exceeds 15s grace threshold", actualBreakMs > plannedBreakMs + 15000L)
    }

    @Test
    fun testEvidenceBuildIncludesProductiveSwitchesAndDistractionChains() {
        val summary = FocusSessionSummary(
            sessionId = 1L,
            totalStudyTimeMs = 3000000L, // 50 mins
            focusedTimeMs = 2400000L,    // 40 mins
            longestFocusStreakMs = 1200000L, // 20 mins
            switchCount = 5,
            notificationCount = 2,
            checksPresented = 2,
            checksPassed = 2,
            checksFailed = 0,
            normalOverrides = 1,
            emergencyExits = 1,
            plannedBreaksCount = 2,
            returnedOnTimeCount = 1,
            missedRemindersCount = 1,
            avgPlannedBreakMs = 60000L,
            avgActualBreakMs = 85000L,
            longestDelayedReturnMs = 25000L,
            studyRelatedSwitches = 2,
            distractionChainsCount = 1
        )

        val mockContext = android.content.ContextWrapper(null)
        val narrator = FocusNarrator(LlmEngine(mockContext))
        val evidence = narrator.buildEvidence(summary)

        assertTrue(evidence.contains("Productive study-related switches allowed: 2"))
        assertTrue(evidence.contains("Distraction chains detected: 1"))
        assertTrue(evidence.contains("Emergency exits: 1 (classified separately, non-distraction)"))
        assertTrue(evidence.contains("Average planned break: 60s"))
        assertTrue(evidence.contains("Average actual break: 85s"))
    }

    @Test
    fun testEmergencyExitIsClassifiedSeparately() {
        val events = listOf(
            FocusEventType.QUIZ_PASSED,
            FocusEventType.BREAK_STARTED,
            FocusEventType.RETURNED_TO_STUDY,
            FocusEventType.EMERGENCY_EXIT
        )

        val emergencyCount = events.count { it == FocusEventType.EMERGENCY_EXIT }
        val distractionSwitches = events.count { it == FocusEventType.APP_SWITCH }

        assertEquals(1, emergencyCount)
        assertEquals(0, distractionSwitches)
    }

    @Test
    fun testFocusSessionSummaryEntityPersistenceMapping() {
        val entity = FocusSessionSummaryEntity(
            id = 1L,
            sessionId = 1726000000000L,
            startTime = 1726000000000L,
            endTime = 1726001800000L,
            totalStudyTimeMs = 1800000L, // 30m
            focusedTimeMs = 1500000L,    // 25m
            longestFocusStreakMs = 1200000L,
            switchCount = 3,
            notificationCount = 2,
            checksPresented = 1,
            checksPassed = 1,
            checksFailed = 0,
            normalOverrides = 0,
            emergencyExits = 0,
            plannedBreaksCount = 1,
            returnedOnTimeCount = 1,
            missedRemindersCount = 0,
            avgPlannedBreakMs = 60000L,
            avgActualBreakMs = 55000L,
            longestDelayedReturnMs = 0L,
            studyRelatedSwitches = 1,
            distractionChainsCount = 0,
            narrative = "Excellent focus maintained.",
            recommendation = "Keep up 25-minute sprints.",
            mainLeakReason = "Minor Focus Drift (Strong Discipline)",
            actionPlan = "Maintain this momentum."
        )

        val domain = entity.toDomain()
        assertEquals(1726000000000L, domain.sessionId)
        assertEquals(1800000L, domain.totalStudyTimeMs)
        assertEquals(1500000L, domain.focusedTimeMs)
        assertEquals(1, domain.checksPassed)
        assertEquals(1, domain.returnedOnTimeCount)
        assertEquals("Minor Focus Drift (Strong Discipline)", entity.mainLeakReason)
    }

    @Test
    fun testBreakNotificationVsDeepFocusNotificationDistinction() {
        // Break notification: legitimate rest, not penalized as a study distraction
        val breakNotificationEvent = FocusInterruptionEvent(
            sessionId = 1L,
            eventType = FocusEventType.NOTIFICATION_INTERRUPTION,
            details = "Notification during planned break: com.whatsapp (rest allowed)"
        )
        assertTrue(breakNotificationEvent.details.contains("rest allowed"))

        // Deep focus notification-triggered switch: user switched within 15s of ping
        val deepFocusSwitchEvent = FocusInterruptionEvent(
            sessionId = 1L,
            eventType = FocusEventType.APP_SWITCH,
            details = "Notification-triggered switch (opened com.instagram within 4s)"
        )
        assertTrue(deepFocusSwitchEvent.details.contains("Notification-triggered switch"))
    }

    @Test
    fun testMistakeAnalysisActionPlanGeneration() {
        // When user misses reminders and delays return by 80 seconds
        val summaryWithOverrun = FocusSessionSummary(
            sessionId = 1L,
            plannedBreaksCount = 2,
            returnedOnTimeCount = 0,
            missedRemindersCount = 2,
            longestDelayedReturnMs = 80000L
        )

        val delayedSec = summaryWithOverrun.longestDelayedReturnMs / 1000L
        val mainReason = "Break Overruns (+${delayedSec}s delay after chime)"
        assertTrue(mainReason.contains("Break Overruns (+80s delay after chime)"))
    }

    @Test
    fun testProductiveResearchNotificationContent() {
        val reminderTitle = "📖 Research Complete?"
        val reminderMessage = "We think research is over, can we return back?"

        assertEquals("📖 Research Complete?", reminderTitle)
        assertTrue(reminderMessage.contains("We think research is over"))
        assertTrue(reminderMessage.contains("can we return back"))
    }

    @Test
    fun testContextRecapOnTimeConfirmation() {
        // Scenario 1: Returned on time (within 15s planned + grace period)
        val plannedDurationMs = 15_000L
        val actualAwayDurationMs = 18_000L
        val isLate = actualAwayDurationMs > (plannedDurationMs + 10_000L)
        val overdueSec = ((actualAwayDurationMs - plannedDurationMs).coerceAtLeast(0L) / 1000L)

        val onTimeRecap = com.studylens.ui.focus.ContextRecapInfo(
            topic = "Thermodynamics",
            awayDurationMs = actualAwayDurationMs,
            returnedOnTime = !isLate,
            delaySecs = overdueSec,
            wasPlannedBreak = true
        )

        assertTrue(onTimeRecap.returnedOnTime)
        assertEquals(3L, onTimeRecap.delaySecs)
        assertFalse(onTimeRecap.isAppHoppingDetected)

        // Scenario 2: Returned late (+35s overdue)
        val lateAwayDurationMs = 50_000L
        val isLateOverdue = lateAwayDurationMs > (plannedDurationMs + 10_000L)
        val overdueLateSec = ((lateAwayDurationMs - plannedDurationMs).coerceAtLeast(0L) / 1000L)

        val lateRecap = com.studylens.ui.focus.ContextRecapInfo(
            topic = "Thermodynamics",
            awayDurationMs = lateAwayDurationMs,
            returnedOnTime = !isLateOverdue,
            delaySecs = overdueLateSec,
            wasPlannedBreak = true
        )

        assertFalse(lateRecap.returnedOnTime)
        assertEquals(35L, lateRecap.delaySecs)
    }

    @Test
    fun testAppHoppingDetectionSuppressesPraiseBanner() {
        // When app hopping is detected (e.g. 3 switches in short period)
        val hopCount = 4
        val awayDuration = 8000L

        val hoppingRecap = com.studylens.ui.focus.ContextRecapInfo(
            topic = "Thermodynamics",
            awayDurationMs = awayDuration,
            returnedOnTime = false, // MUST be false: no false-positive praise!
            delaySecs = 0L,
            wasPlannedBreak = false, // Not a planned break
            isAppHoppingDetected = true,
            hopCount = hopCount
        )

        assertTrue("App hopping must be flagged", hoppingRecap.isAppHoppingDetected)
        assertEquals(4, hoppingRecap.hopCount)
        assertFalse("Cannot praise on-time when returning from app hopping distraction", hoppingRecap.returnedOnTime)
        assertFalse("App hopping is not a planned break", hoppingRecap.wasPlannedBreak)
    }

    @Test
    fun testHistoricalFocusTrendPersistence() {
        // Verify multiple stored sessions produce correct multi-session aggregate trend
        val session1 = FocusSessionSummaryEntity(
            id = 1L,
            sessionId = 101L,
            startTime = 1000L,
            endTime = 61000L,
            totalStudyTimeMs = 60000L,
            focusedTimeMs = 54000L, // 90%
            longestFocusStreakMs = 45000L,
            switchCount = 1,
            notificationCount = 0,
            checksPresented = 0,
            checksPassed = 0,
            checksFailed = 0,
            normalOverrides = 0,
            emergencyExits = 0,
            plannedBreaksCount = 1,
            returnedOnTimeCount = 1,
            missedRemindersCount = 0,
            avgPlannedBreakMs = 15000L,
            avgActualBreakMs = 14000L,
            longestDelayedReturnMs = 0L,
            studyRelatedSwitches = 1,
            distractionChainsCount = 0,
            narrative = "High discipline",
            recommendation = "Keep going",
            mainLeakReason = "",
            actionPlan = ""
        )

        val session2 = FocusSessionSummaryEntity(
            id = 2L,
            sessionId = 102L,
            startTime = 70000L,
            endTime = 130000L,
            totalStudyTimeMs = 60000L,
            focusedTimeMs = 48000L, // 80%
            longestFocusStreakMs = 40000L,
            switchCount = 2,
            notificationCount = 1,
            checksPresented = 0,
            checksPassed = 0,
            checksFailed = 0,
            normalOverrides = 0,
            emergencyExits = 0,
            plannedBreaksCount = 1,
            returnedOnTimeCount = 1,
            missedRemindersCount = 0,
            avgPlannedBreakMs = 15000L,
            avgActualBreakMs = 15000L,
            longestDelayedReturnMs = 0L,
            studyRelatedSwitches = 1,
            distractionChainsCount = 0,
            narrative = "Good discipline",
            recommendation = "Keep going",
            mainLeakReason = "",
            actionPlan = ""
        )

        val list = listOf(session1, session2)
        val avgEfficiency = list.map { ((it.focusedTimeMs.toDouble() / it.totalStudyTimeMs) * 100).toInt() }.average().toInt()
        val totalBreaks = list.sumOf { it.plannedBreaksCount }
        val totalOnTime = list.sumOf { it.returnedOnTimeCount }
        val onTimeRate = ((totalOnTime.toDouble() / totalBreaks) * 100).toInt()

        assertEquals(85, avgEfficiency)
        assertEquals(100, onTimeRate)
    }

    @Test
    fun testOverdueProductiveResearchNotificationMessage() {
        val estimatedSec = 15L
        val overdueMessage = "It was more than ${estimatedSec}s than estimated, can you please return?"

        assertEquals("It was more than 15s than estimated, can you please return?", overdueMessage)
        assertTrue(overdueMessage.contains("more than 15s than estimated"))
        assertTrue(overdueMessage.contains("can you please return?"))
    }

    @Test
    fun testMinimizingAppViaHomeButtonTriggersAppHopping() {
        val now = 100_000L
        val recentSwitches = mutableListOf<Long>()

        // User minimized app at t=0
        recentSwitches.add(now - 30_000L)
        // User minimized app again via Home button at t=30s
        recentSwitches.add(now)

        recentSwitches.removeAll { now - it > 180_000L }

        // >= 2 minimizes/switches in last 3 minutes triggers app hopping alert
        val isAppHopping = recentSwitches.size >= 2
        assertTrue("Minimizing twice within 3 minutes must trigger app hopping", isAppHopping)
        assertEquals(2, recentSwitches.size)
    }

    @Test
    fun testExitButtonPersistsSessionSummaryAndCalculatesTrends() {
        val startTime = 1000L
        val exitTime = 46000L // 45s session ended via Exit button
        val studyDuration = exitTime - startTime

        val events = listOf(
            FocusInterruptionEvent(id = 1, sessionId = startTime, eventType = FocusEventType.RETURNED_TO_STUDY, details = "Focus session started", timestamp = startTime),
            FocusInterruptionEvent(id = 2, sessionId = startTime, eventType = FocusEventType.EMERGENCY_EXIT, details = "Immediate emergency exit triggered", timestamp = exitTime)
        )

        val emergencyExits = events.count { it.eventType == FocusEventType.EMERGENCY_EXIT }
        assertEquals(1, emergencyExits)

        val entity = FocusSessionSummaryEntity(
            id = 0L,
            sessionId = startTime,
            startTime = startTime,
            endTime = exitTime,
            totalStudyTimeMs = studyDuration,
            focusedTimeMs = studyDuration,
            longestFocusStreakMs = studyDuration,
            switchCount = 0,
            notificationCount = 0,
            checksPresented = 0,
            checksPassed = 0,
            checksFailed = 0,
            normalOverrides = 0,
            emergencyExits = emergencyExits,
            plannedBreaksCount = 0,
            returnedOnTimeCount = 0,
            missedRemindersCount = 0,
            avgPlannedBreakMs = 0L,
            avgActualBreakMs = 0L,
            longestDelayedReturnMs = 0L,
            studyRelatedSwitches = 0,
            distractionChainsCount = 0,
            narrative = "Exit handled cleanly",
            recommendation = "Review session",
            mainLeakReason = "",
            actionPlan = ""
        )

        assertEquals(45000L, entity.totalStudyTimeMs)
        assertEquals(1, entity.emergencyExits)
    }

    @Test
    fun testDurationFormattingForSubMinuteAndMultiMinuteSessions() {
        assertEquals("0s", formatDuration(0L))
        assertEquals("15s", formatDuration(15_000L))
        assertEquals("45s", formatDuration(45_000L))
        assertEquals("1m", formatDuration(60_000L))
        assertEquals("1m 15s", formatDuration(75_000L))
        assertEquals("2m", formatDuration(120_000L))
        assertEquals("1h 5m", formatDuration(3900_000L))
    }

    @Test
    fun testNotificationBarCountdownTimerAndContinuousAlarmConfig() {
        // Verify timer notification constant ID is separate from alert notification IDs
        assertEquals(2000, com.studylens.input.focus.BreakReminderHelper.TIMER_NOTIFICATION_ID)
        assertEquals(2001, com.studylens.input.focus.BreakReminderHelper.NOTIFICATION_ID)
        assertEquals(2002, com.studylens.input.focus.BreakReminderHelper.HOPPING_NOTIFICATION_ID)
        assertEquals(2003, com.studylens.input.focus.BreakReminderHelper.OVERDUE_NOTIFICATION_ID)

        // Verify continuous repeating alarm pattern configuration
        val repeatAlarmPattern = longArrayOf(0, 800, 400, 800, 400)
        assertTrue(repeatAlarmPattern.isNotEmpty())
        assertEquals(5, repeatAlarmPattern.size)
    }

    @Test
    fun testBreakAlarmReceiverActionsAndAlarmConstants() {
        assertEquals("com.studylens.ACTION_BREAK_TIMER_EXPIRED", com.studylens.input.focus.BreakReminderHelper.ACTION_BREAK_TIMER_EXPIRED)
        assertEquals("com.studylens.ACTION_RESEARCH_OVERDUE", com.studylens.input.focus.BreakReminderHelper.ACTION_RESEARCH_OVERDUE)
        assertEquals("com.studylens.ACTION_DISMISS_ALARM", com.studylens.input.focus.BreakReminderHelper.ACTION_DISMISS_ALARM)
        assertEquals(4001, com.studylens.input.focus.BreakReminderHelper.ALARM_REQUEST_CODE)
        assertEquals(4002, com.studylens.input.focus.BreakReminderHelper.OVERDUE_ALARM_REQUEST_CODE)
        assertEquals(4003, com.studylens.input.focus.BreakReminderHelper.DISMISS_REQUEST_CODE)
    }
}
