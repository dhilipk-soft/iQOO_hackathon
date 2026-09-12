package com.studylens.input.focus

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.studylens.input.data.AppDatabase
import com.studylens.shared.NotificationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationCollector(
    private val context: Context,
    private val database: AppDatabase
) {

    fun isNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun getNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun getNotificationEvents(startTime: Long, endTime: Long): List<NotificationEvent> = withContext(Dispatchers.IO) {
        try {
            database.focusSignalsDao().getNotificationEvents(startTime, endTime)
                .map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
