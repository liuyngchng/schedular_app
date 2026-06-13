package com.example.applauncher.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.applauncher.AppLauncherApp
import com.example.applauncher.BridgeActivity
import com.example.applauncher.model.ExecutionLog
import com.example.applauncher.model.Schedule
import com.example.applauncher.model.TimeSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName

        Log.d("AlarmReceiver", "Alarm fired: $appName ($packageName)")

        // Log alarm fire event so user can see it even if BridgeActivity fails
        val app = context.applicationContext as AppLauncherApp
        CoroutineScope(Dispatchers.IO).launch {
            app.logRepository.addLog(
                ExecutionLog(packageName, "[闹钟触发] $appName", System.currentTimeMillis())
            )
        }

        try {
            val bridgeIntent = Intent(context, BridgeActivity::class.java).apply {
                putExtra(BridgeActivity.EXTRA_PACKAGE, packageName)
                putExtra(BridgeActivity.EXTRA_APP_NAME, appName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(bridgeIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start BridgeActivity", e)
            CoroutineScope(Dispatchers.IO).launch {
                app.logRepository.addLog(
                    ExecutionLog(packageName, "[启动失败] $appName", System.currentTimeMillis())
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_APP_NAME = "app_name"
        private const val RC_SLOT1 = 1001
        private const val RC_SLOT2 = 2001

        fun hasAlarms(context: Context, schedule: Schedule): Boolean {
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, AlarmReceiver::class.java)
                    val pi = PendingIntent.getBroadcast(
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
                    add(Calendar.MINUTE, (1..5).random())
                    if (before(Calendar.getInstance())) {
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(EXTRA_PACKAGE_NAME, schedule.packageName)
                    putExtra(EXTRA_APP_NAME, schedule.appName)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCodeBase + dayOfWeek,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY * 7,
                    pendingIntent
                )
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (base in listOf(RC_SLOT1, RC_SLOT2)) {
                for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                    val intent = Intent(context, AlarmReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(
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
