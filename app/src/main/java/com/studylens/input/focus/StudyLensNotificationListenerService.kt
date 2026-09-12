package com.studylens.input.focus

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.studylens.input.data.AppDatabase
import com.studylens.input.data.NotificationEventEntity
import com.studylens.shared.NotificationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class StudyLensNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Ignore notifications from StudyLens itself

        val timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entity = NotificationEventEntity(
                    packageName = pkg,
                    timestamp = timestamp
                )
                val id = db.focusSignalsDao().insertNotificationEvent(entity)
                _notificationEvents.emit(NotificationEvent(id, pkg, timestamp))
            } catch (e: Exception) {
                // Ignore DB logging failure
            }
        }
    }

    companion object {
        private val _notificationEvents = MutableSharedFlow<NotificationEvent>(replay = 10)
        val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()
    }
}
