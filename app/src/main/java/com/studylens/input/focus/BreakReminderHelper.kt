package com.studylens.input.focus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.studylens.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class BreakReminderHelper(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingReminderRunnable: Runnable? = null
    private var pendingOverdueRunnable: Runnable? = null

    init {
        createNotificationChannel(context)
    }

    fun vibrateDevice(pattern: LongArray = longArrayOf(0, 300, 150, 300)) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            // Gracefully handled if vibrator unavailable
        }
    }

    fun scheduleBreakReminder(
        delayMs: Long,
        title: String = "🔔 Focus Reminder",
        reminderText: String = "Your break is over. Ready to get back to your study session?"
    ) {
        cancelBreakReminder()

        // 1. Show native ongoing countdown timer in notification shade
        showCountdownNotification(
            context = context,
            delayMs = delayMs,
            title = "☕ Planned Break Active",
            contentText = "Break timer is ticking in background. Return when time is up!"
        )

        // 2. Schedule exact system RTC_WAKEUP alarm to guarantee wakeup even in deep background
        scheduleExactBreakAlarm(
            context = context,
            delayMs = delayMs,
            title = title,
            message = reminderText,
            isResearch = false
        )

        // 3. Fallback in-process handler for instant foreground triggering
        val runnable = Runnable {
            dismissCountdownNotification(context)
            startContinuousAlarm(context)
            showCompletionNotification(context, title, reminderText)
            emitReminderFired(System.currentTimeMillis())
        }
        pendingReminderRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    fun scheduleResearchReminder(
        delayMs: Long,
        overdueDelayMs: Long = 15_000L,
        title: String = "📖 Research Complete?",
        reminderText: String = "We think research is over, can we return back?",
        overdueTitle: String = "⏱️ Research Overdue!",
        onOverdue: (() -> Unit)? = null
    ) {
        cancelBreakReminder()

        val estimatedSec = (delayMs / 1000L).coerceAtLeast(1L)
        val overdueMessage = "It was more than ${estimatedSec}s than estimated, can you please return?"

        // 1. Show native ongoing countdown timer in notification shade
        showCountdownNotification(
            context = context,
            delayMs = delayMs,
            title = "📖 Productive Research Active",
            contentText = "Research timer running ($estimatedSec s). Return when research is done!"
        )

        // 2. Schedule exact system RTC_WAKEUP alarm to guarantee wakeup even in deep background
        scheduleExactBreakAlarm(
            context = context,
            delayMs = delayMs,
            title = title,
            message = reminderText,
            isResearch = true
        )

        // 3. Fallback in-process handler
        val initialRunnable = Runnable {
            dismissCountdownNotification(context)
            startContinuousAlarm(context)
            showCompletionNotification(context, title, reminderText)
            emitReminderFired(System.currentTimeMillis())

            val overdueRunnable = Runnable {
                startContinuousAlarm(context)
                showOverdueNotification(context, overdueTitle, overdueMessage)
                onOverdue?.invoke()
            }
            pendingOverdueRunnable = overdueRunnable
            handler.postDelayed(overdueRunnable, overdueDelayMs)
        }

        pendingReminderRunnable = initialRunnable
        handler.postDelayed(initialRunnable, delayMs)
    }

    fun cancelBreakReminder() {
        pendingReminderRunnable?.let {
            handler.removeCallbacks(it)
            pendingReminderRunnable = null
        }
        pendingOverdueRunnable?.let {
            handler.removeCallbacks(it)
            pendingOverdueRunnable = null
        }
        stopContinuousAlarm(context)
        dismissCountdownNotification(context)
        cancelExactAlarms(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        notificationManager?.cancel(OVERDUE_NOTIFICATION_ID)
    }

    fun startContinuousAlarm() {
        Companion.startContinuousAlarm(context)
    }

    fun stopContinuousAlarm() {
        Companion.stopContinuousAlarm(context)
    }

    fun showAppHoppingNotification(switchCount: Int) {
        vibrateDevice(longArrayOf(0, 400, 200, 400))

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_APP_HOPPING, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Rapid App Hopping Detected"
        val message = "You switched apps $switchCount times in 3 minutes. Take a breath and return to deep focus!"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_revert,
                "Return to Study 🚀",
                pendingIntent
            )

        try {
            NotificationManagerCompat.from(context).notify(HOPPING_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted; handled gracefully
        }
    }

    companion object {
        const val CHANNEL_ID = "studylens_focus_channel"
        const val TIMER_NOTIFICATION_ID = 2000
        const val NOTIFICATION_ID = 2001
        const val HOPPING_NOTIFICATION_ID = 2002
        const val OVERDUE_NOTIFICATION_ID = 2003

        const val REQUEST_CODE = 3001
        const val ALARM_REQUEST_CODE = 4001
        const val OVERDUE_ALARM_REQUEST_CODE = 4002
        const val DISMISS_REQUEST_CODE = 4003

        const val ACTION_BREAK_TIMER_EXPIRED = "com.studylens.ACTION_BREAK_TIMER_EXPIRED"
        const val ACTION_RESEARCH_OVERDUE = "com.studylens.ACTION_RESEARCH_OVERDUE"
        const val ACTION_DISMISS_ALARM = "com.studylens.ACTION_DISMISS_ALARM"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_IS_RESEARCH = "extra_is_research"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_FROM_BREAK_REMINDER = "extra_from_break_reminder"
        const val EXTRA_FROM_APP_HOPPING = "extra_from_app_hopping"

        private val _reminderFiredFlow = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 5)
        val reminderFiredFlow: SharedFlow<Long> = _reminderFiredFlow.asSharedFlow()

        @Volatile
        private var activeMediaPlayer: MediaPlayer? = null
        @Volatile
        private var activeRingtone: Ringtone? = null
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        fun emitReminderFired(timestamp: Long) {
            _reminderFiredFlow.tryEmit(timestamp)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Focus Reminders"
                val descriptionText = "Notifications for study break reminders, live countdown timers, and context recovery"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 400, 1000, 400)
                    setSound(soundUri, audioAttributes)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        }

        @Synchronized
        fun startContinuousAlarm(context: Context) {
            try {
                // 1. Acquire WakeLock so CPU does not sleep during alarm
                acquireWakeLock(context)

                // 2. Continuous repeating vibration loop (repeat index = 0)
                startVibrationLoop(context)

                // 3. Continuous loud audible alarm sound via MediaPlayer routed to hardware STREAM_ALARM
                if (activeMediaPlayer == null) {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, alarmUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                    activeMediaPlayer = mp
                }
            } catch (e: Exception) {
                // Fallback to RingtoneManager if MediaPlayer device-specific initialization fails
                try {
                    if (activeRingtone == null) {
                        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone?.isLooping = true
                        }
                        ringtone?.play()
                        activeRingtone = ringtone
                    }
                } catch (ignored: Exception) {}
            }
        }

        @Synchronized
        fun stopContinuousAlarm(context: Context? = null) {
            try {
                // 1. Stop and release MediaPlayer
                activeMediaPlayer?.apply {
                    try {
                        if (isPlaying) stop()
                    } catch (ignored: Exception) {}
                    release()
                }
                activeMediaPlayer = null

                // 2. Stop fallback Ringtone
                activeRingtone?.stop()
                activeRingtone = null

                // 3. Stop repeating vibration
                context?.let { ctx ->
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                        vm?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    vibrator?.cancel()
                }

                // 4. Release WakeLock
                releaseWakeLock()

                // 5. Cancel exact alarms
                context?.let { ctx ->
                    cancelExactAlarms(ctx)
                }
            } catch (e: Exception) {
                // Gracefully handled
            }
        }

        private fun startVibrationLoop(context: Context) {
            try {
                val pattern = longArrayOf(0, 1000, 400, 1000, 400)
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val vibAttrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(pattern, 0), // 0 means loop continuously
                        vibAttrs
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(pattern, 0),
                        audioAttrs
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                // Gracefully handled
            }
        }

        private fun acquireWakeLock(context: Context) {
            try {
                if (wakeLock == null) {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "StudyLens:BreakAlarmWakeLock"
                    )?.apply {
                        acquire(3 * 60 * 1000L) // 3 minutes timeout safety
                    }
                }
            } catch (e: Exception) {}
        }

        private fun releaseWakeLock() {
            try {
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
                wakeLock = null
            } catch (e: Exception) {}
        }

        fun scheduleExactBreakAlarm(
            context: Context,
            delayMs: Long,
            title: String,
            message: String,
            isResearch: Boolean
        ) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, BreakAlarmReceiver::class.java).apply {
                    action = ACTION_BREAK_TIMER_EXPIRED
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                    putExtra(EXTRA_IS_RESEARCH, isResearch)
                    putExtra(EXTRA_DURATION_MS, delayMs)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMillis = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (e: Exception) {}
        }

        fun scheduleExactOverdueAlarm(
            context: Context,
            delayMs: Long,
            title: String,
            message: String
        ) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, BreakAlarmReceiver::class.java).apply {
                    action = ACTION_RESEARCH_OVERDUE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_MESSAGE, message)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    OVERDUE_ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMillis = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (e: Exception) {}
        }

        fun cancelExactAlarms(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val breakIntent = Intent(context, BreakAlarmReceiver::class.java).apply {
                    action = ACTION_BREAK_TIMER_EXPIRED
                }
                val breakPendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    breakIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(breakPendingIntent)

                val overdueIntent = Intent(context, BreakAlarmReceiver::class.java).apply {
                    action = ACTION_RESEARCH_OVERDUE
                }
                val overduePendingIntent = PendingIntent.getBroadcast(
                    context,
                    OVERDUE_ALARM_REQUEST_CODE,
                    overdueIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(overduePendingIntent)
            } catch (e: Exception) {}
        }

        fun showCountdownNotification(
            context: Context,
            delayMs: Long,
            title: String,
            contentText: String
        ) {
            val targetTime = System.currentTimeMillis() + delayMs
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FROM_BREAK_REMINDER, true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE + 2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(contentText)
                .setWhen(targetTime)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_revert,
                    "Return to Study 🚀",
                    pendingIntent
                )

            try {
                NotificationManagerCompat.from(context).notify(TIMER_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {}
        }

        fun dismissCountdownNotification(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(TIMER_NOTIFICATION_ID)
        }

        fun showCompletionNotification(
            context: Context,
            title: String,
            message: String
        ) {
            val returnIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FROM_BREAK_REMINDER, true)
            }

            val returnPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                returnIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, BreakAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                DISMISS_REQUEST_CODE,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(returnPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_revert,
                    "Return to Study 🚀",
                    returnPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss Alarm ⏹️",
                    dismissPendingIntent
                )

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {}
        }

        fun showOverdueNotification(
            context: Context,
            title: String,
            message: String
        ) {
            val returnIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FROM_BREAK_REMINDER, true)
            }

            val returnPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE + 3,
                returnIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, BreakAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                DISMISS_REQUEST_CODE + 1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(returnPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_revert,
                    "Return to Study 🚀",
                    returnPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss Alarm ⏹️",
                    dismissPendingIntent
                )

            try {
                NotificationManagerCompat.from(context).notify(OVERDUE_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {}
        }
    }
}
