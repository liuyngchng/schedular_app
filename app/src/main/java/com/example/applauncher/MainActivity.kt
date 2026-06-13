package com.example.applauncher

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.applauncher.model.Diagnostics
import com.example.applauncher.receiver.AlarmReceiver
import com.example.applauncher.ui.screens.HomeScreen
import com.example.applauncher.ui.theme.AppLauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestExactAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        scheduleIfReady()
    }

    private val requestBatteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        scheduleIfReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AppLauncherApp

        setContent {
            AppLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val schedule by app.scheduleRepository.schedule.collectAsState(
                        initial = null
                    )
                    val diagnostics = schedule?.let { runDiagnostics(it) }
                    HomeScreen(
                        currentSchedule = schedule,
                        diagnostics = diagnostics,
                        onSave = { sched ->
                            CoroutineScope(Dispatchers.IO).launch {
                                app.scheduleRepository.save(sched)
                            }
                            scheduleAlarms(sched)
                            android.widget.Toast.makeText(this@MainActivity, "设置已保存", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onToggleEnabled = { enabled ->
                            CoroutineScope(Dispatchers.IO).launch {
                                app.scheduleRepository.setEnabled(enabled)
                                val sched = app.scheduleRepository.schedule.first()
                                if (enabled && sched != null) {
                                    scheduleAlarms(sched)
                                } else {
                                    AlarmReceiver.cancel(this@MainActivity)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleIfReady()
    }

    private fun scheduleIfReady() {
        if (!hasExactAlarmPermission()) {
            requestExactAlarmPermission()
            return
        }
        if (!hasBatteryOptimizationWhitelist()) {
            requestBatteryOptimizationWhitelist()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val app = application as AppLauncherApp
            val sched = app.scheduleRepository.schedule.first()
            if (sched != null && sched.enabled) {
                scheduleAlarms(sched)
            }
        }
    }

    private fun scheduleAlarms(schedule: com.example.applauncher.model.Schedule) {
        if (!hasExactAlarmPermission()) {
            requestExactAlarmPermission()
            return
        }
        AlarmReceiver.schedule(this, schedule)
    }

    private fun hasExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            requestExactAlarmLauncher.launch(intent)
        }
    }

    private fun hasBatteryOptimizationWhitelist(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationWhitelist() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        requestBatteryOptimizationLauncher.launch(intent)
    }

    private fun runDiagnostics(schedule: com.example.applauncher.model.Schedule): Diagnostics {
        val app = application as AppLauncherApp
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val hasLockScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceSecure
        } else {
            @Suppress("DEPRECATION")
            km.isKeyguardSecure
        }
        val targetInstalled = packageManager.getLaunchIntentForPackage(schedule.packageName) != null
        val alarmsOk = schedule.enabled && AlarmReceiver.hasAlarms(this, schedule)

        // Detect missed schedules
        val missedSchedules = mutableListOf<String>()
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_WEEK)
        val logs = app.logRepository.logs.value

        fun hasRecentLog(day: Int, hour: Int, minute: Int): Boolean {
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // The alarm adds 1-5 min jitter, so check a wider window
                add(Calendar.MINUTE, -5)
            }
            val windowStart = targetCal.timeInMillis
            targetCal.add(Calendar.MINUTE, 25) // 5 min before to 20 min after
            val windowEnd = targetCal.timeInMillis

            return logs.any { log ->
                log.packageName == schedule.packageName
                        && log.timestamp in windowStart..windowEnd
            }
        }

        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            if (day != today) continue
            val slots = listOf(schedule.slot1 to "时段一", schedule.slot2 to "时段二")
            for ((slot, label) in slots) {
                val slotTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, slot.startHour)
                    set(Calendar.MINUTE, slot.startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // Only flag if we're 15+ minutes past the scheduled time
                if (now.after(slotTime) && now.timeInMillis - slotTime.timeInMillis > 15 * 60 * 1000) {
                    if (!hasRecentLog(day, slot.startHour, slot.startMinute)) {
                        val df = SimpleDateFormat("HH:mm", Locale.getDefault())
                        missedSchedules.add("$label ${df.format(Date(slotTime.timeInMillis))} 未触发")
                    }
                }
            }
        }

        return Diagnostics(
            exactAlarmGranted = hasExactAlarmPermission(),
            batteryWhitelisted = hasBatteryOptimizationWhitelist(),
            hasLockScreen = hasLockScreen,
            targetAppInstalled = targetInstalled,
            targetAppName = schedule.appName,
            alarmsRegistered = alarmsOk,
            missedSchedules = missedSchedules,
            scheduleEnabled = schedule.enabled,
            hasSchedule = true
        )
    }
}
