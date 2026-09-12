package com.studylens.input.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.studylens.input.data.AppDatabase
import com.studylens.input.data.AppEventEntity
import com.studylens.input.data.StudySessionEntity
import com.studylens.shared.AppEvent
import com.studylens.shared.NotificationEvent
import com.studylens.shared.StudySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageCollector(
    private val context: Context,
    private val database: AppDatabase
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun getUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun queryAppEvents(startTime: Long, endTime: Long): List<AppEvent> = withContext(Dispatchers.IO) {
        if (!isUsageAccessGranted() || usageStatsManager == null) {
            return@withContext emptyList()
        }

        val eventsList = mutableListOf<AppEvent>()
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                // We focus on ACTIVITY_RESUMED (1) and ACTIVITY_PAUSED (2)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
                ) {
                    val appEvent = AppEvent(
                        id = 0,
                        packageName = event.packageName ?: "unknown",
                        eventType = event.eventType,
                        timestamp = event.timeStamp
                    )
                    eventsList.add(appEvent)

                    // Also persist into local database
                    database.focusSignalsDao().insertAppEvent(
                        AppEventEntity(
                            packageName = appEvent.packageName,
                            eventType = appEvent.eventType,
                            timestamp = appEvent.timestamp
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Degrade gracefully without crashing
        }
        eventsList
    }

    suspend fun buildStudySession(
        startTime: Long,
        endTime: Long,
        notifications: List<NotificationEvent>
    ): StudySession = withContext(Dispatchers.IO) {
        val appEvents = queryAppEvents(startTime, endTime)
        val myPackage = context.packageName

        var switchCount = 0
        var isStudyLensInForeground = true

        for (event in appEvents) {
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.packageName != myPackage && isStudyLensInForeground) {
                    // User switched away from StudyLens
                    switchCount++
                    isStudyLensInForeground = false
                } else if (event.packageName == myPackage && !isStudyLensInForeground) {
                    // User returned back to StudyLens
                    isStudyLensInForeground = true
                }
            }
        }

        val durationMs = (endTime - startTime).coerceAtLeast(0L)
        val notificationCount = notifications.size

        val entity = StudySessionEntity(
            startTime = startTime,
            endTime = endTime,
            durationMs = durationMs,
            switchCount = switchCount,
            notificationCount = notificationCount
        )

        val id = database.focusSignalsDao().insertStudySession(entity)

        StudySession(
            id = id,
            startTime = startTime,
            endTime = endTime,
            durationMs = durationMs,
            switchCount = switchCount,
            notificationCount = notificationCount
        )
    }
}
