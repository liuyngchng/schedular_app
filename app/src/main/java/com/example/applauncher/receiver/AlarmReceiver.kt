package com.example.applauncher.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.applauncher.BridgeActivity
import com.example.applauncher.model.Schedule
import com.example.applauncher.model.TimeSlot
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    // No longer used as a receiver — alarms now target BridgeActivity directly.
    // This class remains for lifecycle callbacks (boot) and static helpers.

    override fun onReceive(context: Context, intent: Intent) {}

    companion object {
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_APP_NAME = "app_name"
        private const val RC_SLOT1 = 1001
        private const val RC_SLOT2 = 2001

        fun hasAlarms(context: Context, schedule: Schedule): Boolean {
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, BridgeActivity::class.java)
                    val pi = PendingIntent.getActivity(
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

                val intent = Intent(context, BridgeActivity::class.java).apply {
                    putExtra(BridgeActivity.EXTRA_PACKAGE, schedule.packageName)
                    putExtra(BridgeActivity.EXTRA_APP_NAME, schedule.appName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    requestCodeBase + dayOfWeek,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                    pendingIntent
                )
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, BridgeActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
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
