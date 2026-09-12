package com.studylens.input.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered by AlarmManager.RTC_WAKEUP exact alarms.
 * Guarantees that when the break timer reaches zero, the device wakes up
 * and executes continuous audible alarm ringing and forceful repeating vibration,
 * even if StudyLens was in the background, minimized, or under Doze battery restrictions.
 */
class BreakAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            BreakReminderHelper.ACTION_BREAK_TIMER_EXPIRED -> {
                val title = intent.getStringExtra(BreakReminderHelper.EXTRA_TITLE)
                    ?: "🔔 Focus Reminder"
                val message = intent.getStringExtra(BreakReminderHelper.EXTRA_MESSAGE)
                    ?: "Your break is over. Ready to get back to your study session?"
                val isResearch = intent.getBooleanExtra(BreakReminderHelper.EXTRA_IS_RESEARCH, false)
                val delayMs = intent.getLongExtra(BreakReminderHelper.EXTRA_DURATION_MS, 15_000L)

                // 1. Dismiss the ongoing countdown notification in notification bar
                BreakReminderHelper.dismissCountdownNotification(context)

                // 2. Start continuous loud alarm ringtone and repeating vibration
                BreakReminderHelper.startContinuousAlarm(context)

                // 3. Display heads-up completion alert with action buttons
                BreakReminderHelper.showCompletionNotification(context, title, message)
                BreakReminderHelper.emitReminderFired(System.currentTimeMillis())

                // 4. If productive research, schedule overdue escalation alarm at +15s
                if (isResearch) {
                    val estimatedSec = (delayMs / 1000L).coerceAtLeast(1L)
                    val overdueTitle = "⏱️ Research Overdue!"
                    val overdueMessage = "It was more than ${estimatedSec}s than estimated, can you please return?"
                    BreakReminderHelper.scheduleExactOverdueAlarm(
                        context,
                        15_000L,
                        overdueTitle,
                        overdueMessage
                    )
                }
            }
            BreakReminderHelper.ACTION_RESEARCH_OVERDUE -> {
                val title = intent.getStringExtra(BreakReminderHelper.EXTRA_TITLE)
                    ?: "⏱️ Research Overdue!"
                val message = intent.getStringExtra(BreakReminderHelper.EXTRA_MESSAGE)
                    ?: "It was more than 15s than estimated, can you please return?"

                // Re-trigger alarm ring and continuous vibration
                BreakReminderHelper.startContinuousAlarm(context)
                BreakReminderHelper.showOverdueNotification(context, title, message)
            }
            BreakReminderHelper.ACTION_DISMISS_ALARM -> {
                // Student clicked "Dismiss Alarm ⏹️"
                BreakReminderHelper.stopContinuousAlarm(context)
            }
        }
    }
}
