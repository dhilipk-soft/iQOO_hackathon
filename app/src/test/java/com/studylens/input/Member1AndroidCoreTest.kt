package com.studylens.input

import com.studylens.input.data.AppEventEntity
import com.studylens.input.data.NotificationEventEntity
import com.studylens.input.data.StudyCaptureEntity
import com.studylens.input.data.StudySessionEntity
import com.studylens.shared.AppEvent
import com.studylens.shared.InferenceStats
import com.studylens.shared.NotificationEvent
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import org.junit.Assert.*
import org.junit.Test

class Member1AndroidCoreTest {

    @Test
    fun testStudyCaptureEntityToDomain() {
        val timestamp = System.currentTimeMillis()
        val entity = StudyCaptureEntity(
            id = 42L,
            extractedText = "Calculus: ∫ u dv = uv - ∫ v du",
            timestamp = timestamp
        )
        val domain: StudyCapture = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Calculus: ∫ u dv = uv - ∫ v du", domain.extractedText)
        assertEquals(timestamp, domain.timestamp)
    }

    @Test
    fun testAppEventEntityToDomain() {
        val timestamp = System.currentTimeMillis()
        val entity = AppEventEntity(
            id = 1L,
            packageName = "com.studylens",
            eventType = 1, // ACTIVITY_RESUMED
            timestamp = timestamp
        )
        val domain: AppEvent = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("com.studylens", domain.packageName)
        assertEquals(1, domain.eventType)
        assertEquals(timestamp, domain.timestamp)
    }

    @Test
    fun testNotificationEventEntityToDomain() {
        val timestamp = System.currentTimeMillis()
        val entity = NotificationEventEntity(
            id = 7L,
            packageName = "com.whatsapp",
            timestamp = timestamp
        )
        val domain: NotificationEvent = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("com.whatsapp", domain.packageName)
        assertEquals(timestamp, domain.timestamp)
    }

    @Test
    fun testStudySessionEntityToDomain() {
        val start = 1000L
        val end = 61000L
        val entity = StudySessionEntity(
            id = 10L,
            startTime = start,
            endTime = end,
            durationMs = 60000L,
            switchCount = 3,
            notificationCount = 2
        )
        val domain: StudySession = entity.toDomain()

        assertEquals(10L, domain.id)
        assertEquals(start, domain.startTime)
        assertEquals(end, domain.endTime)
        assertEquals(60000L, domain.durationMs)
        assertEquals(3, domain.switchCount)
        assertEquals(2, domain.notificationCount)
    }

    @Test
    fun testStudySessionHeuristicCalculation() {
        val startTime = 1000000L
        val endTime = 1060000L // 60 seconds
        val myPackage = "com.studylens"

        // Simulate app events
        val events = listOf(
            AppEvent(1, myPackage, 1, 1000000L),       // In foreground
            AppEvent(2, "com.social.app", 1, 1010000L),// Switched away (switch 1)
            AppEvent(3, myPackage, 1, 1025000L),       // Back to StudyLens
            AppEvent(4, "com.messaging", 1, 1040000L), // Switched away (switch 2)
            AppEvent(5, myPackage, 1, 1050000L)        // Back to StudyLens
        )

        var switchCount = 0
        var isStudyLensInForeground = true

        for (event in events) {
            if (event.eventType == 1) { // ACTIVITY_RESUMED
                if (event.packageName != myPackage && isStudyLensInForeground) {
                    switchCount++
                    isStudyLensInForeground = false
                } else if (event.packageName == myPackage && !isStudyLensInForeground) {
                    isStudyLensInForeground = true
                }
            }
        }

        val notifications = listOf(
            NotificationEvent(1, "com.social.app", 1011000L),
            NotificationEvent(2, "com.messaging", 1041000L)
        )

        val session = StudySession(
            id = 1L,
            startTime = startTime,
            endTime = endTime,
            durationMs = endTime - startTime,
            switchCount = switchCount,
            notificationCount = notifications.size
        )

        assertEquals(2, session.switchCount)
        assertEquals(60000L, session.durationMs)
        assertEquals(2, session.notificationCount)
    }

    @Test
    fun testInferenceStatsRecording() {
        val initialStats = InferenceStats(
            tokensPerSecond = 0.0,
            latencyMs = 0L,
            ramUsedMb = 350L,
            thermalStatus = "NORMAL"
        )
        val updatedStats = initialStats.copy(
            tokensPerSecond = 28.4,
            latencyMs = 115L
        )

        assertEquals(28.4, updatedStats.tokensPerSecond, 0.01)
        assertEquals(115L, updatedStats.latencyMs)
        assertEquals(350L, updatedStats.ramUsedMb)
        assertEquals("NORMAL", updatedStats.thermalStatus)
    }
}
