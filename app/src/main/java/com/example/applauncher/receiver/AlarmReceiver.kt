package com.example.applauncher.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.applauncher.LauncherService
import com.example.applauncher.model.Schedule
import com.example.applauncher.model.TimeSlot
import java.util.Calendar
import kotlin.random.Random

class AlarmReceiver : BroadcastReceiver() {
    // Not used as broadcast target — alarms go directly to LauncherService.
    // This class only holds the static scheduling helpers.

    override fun onReceive(context: Context, intent: Intent) {}

    companion object {
        const val ALARM_CHANNEL_ID = "app_launcher_alarm"
        const val ALARM_NOTIFY_ID = 999
        private const val RC_SLOT1 = 1001
        private const val RC_SLOT2 = 2001

        fun hasAlarms(context: Context, schedule: Schedule): Boolean {
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, LauncherService::class.java)
                    val pi = PendingIntent.getService(
                        context, base + day, intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (pi != null) return true
                }
            }
            return false
        }

        fun schedule(context: Context, schedule: Schedule) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            cancel(context)

            if (!schedule.enabled) return

            scheduleSlot(context, alarmManager, schedule, schedule.slot1, RC_SLOT1)
            scheduleSlot(context, alarmManager, schedule, schedule.slot2, RC_SLOT2)
        }

        private fun scheduleSlot(
            context: Context,
            alarmManager: AlarmManager,
            schedule: Schedule,
            slot: TimeSlot,
            requestCodeBase: Int
        ) {
            for (dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    set(Calendar.HOUR_OF_DAY, slot.startHour)
                    set(Calendar.MINUTE, slot.startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(Calendar.getInstance())) {
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, LauncherService::class.java).apply {
                    putExtra(LauncherService.EXTRA_PACKAGE, schedule.packageName)
                    putExtra(LauncherService.EXTRA_APP_NAME, schedule.appName)
                }

                val pendingIntent = PendingIntent.getService(
                    context,
                    requestCodeBase + dayOfWeek,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Add 1-5 minute random offset to avoid exact crowd timing
                val offsetMs = Random.nextInt(1, 6) * 60_000L
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis + offsetMs, pendingIntent),
                    pendingIntent
                )
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, LauncherService::class.java)
                    val pendingIntent = PendingIntent.getService(
                        context,
                        base + day,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    pendingIntent?.let {
                        alarmManager.cancel(it)
                        it.cancel()
                    }
                }
            }
        }
    }
}
