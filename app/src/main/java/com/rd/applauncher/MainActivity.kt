package com.rd.applauncher

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.Context
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rd.applauncher.model.Diagnostics
import com.rd.applauncher.receiver.AlarmReceiver
import com.rd.applauncher.ui.screens.HomeScreen
import com.rd.applauncher.ui.theme.AppLauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                    var diagnosticsVersion by remember { mutableIntStateOf(0) }
                    val diagnostics = remember(schedule, diagnosticsVersion) {
                        schedule?.let { runDiagnostics(it) }
                    }
                    HomeScreen(
                        currentSchedule = schedule,
                        diagnostics = diagnostics,
                        onRefreshDiagnostics = { diagnosticsVersion++ },
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

    private fun scheduleAlarms(schedule: com.rd.applauncher.model.Schedule) {
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

    private fun runDiagnostics(schedule: com.rd.applauncher.model.Schedule): Diagnostics {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val hasLockScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceSecure
        } else {
            @Suppress("DEPRECATION")
            km.isKeyguardSecure
        }
        val targetInstalled = packageManager.getLaunchIntentForPackage(schedule.packageName) != null
        val alarmsOk = schedule.enabled && AlarmReceiver.hasAlarms(this)

        // Check if our AccessibilityService is enabled
        val accessibilityEnabled = try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(packageName) == true
        } catch (_: Exception) {
            false
        }

        // Check if overlay permission is granted
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        return Diagnostics(
            exactAlarmGranted = hasExactAlarmPermission(),
            batteryWhitelisted = hasBatteryOptimizationWhitelist(),
            hasLockScreen = hasLockScreen,
            targetAppInstalled = targetInstalled,
            targetAppName = schedule.appName,
            alarmsRegistered = alarmsOk,
            scheduleEnabled = schedule.enabled,
            hasSchedule = true,
            accessibilityEnabled = accessibilityEnabled,
            overlayGranted = overlayGranted
        )
    }
}
