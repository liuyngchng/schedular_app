package com.rd.applauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.rd.applauncher.model.ExecutionLog
import com.rd.applauncher.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

class LauncherService : Service() {

    companion object {
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_APP_NAME = "target_app_name"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        val appName = intent?.getStringExtra(EXTRA_APP_NAME)

        if (packageName != null) {
            handleAlarm(packageName, appName ?: packageName, startId)
        } else {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun handleAlarm(packageName: String, appName: String, startId: Int) {
        Log.d("LauncherService", "Alarm fired: $appName ($packageName)")

        val delayMs = Random.nextInt(1, 6) * 60_000L
        Log.d("LauncherService", "Random delay: ${delayMs / 1000}s")

        val pm = getSystemService(POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val cpuLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AppLauncher:Delay"
        )
        cpuLock.acquire(delayMs + 30_000L)

        CoroutineScope(Dispatchers.Main).launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                try {
                    delay(delayMs)
                } finally {
                    cpuLock.release()
                }

                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AppLauncher:Alarm"
                )
                wakeLock = wl
                wl.acquire(10 * 60_000L)

                // Log
                try {
                    (application as AppLauncherApp).logRepository.addLog(
                        ExecutionLog(packageName, "[闹钟触发] $appName", System.currentTimeMillis())
                    )
                } catch (e: Exception) {
                    Log.e("LauncherService", "log failed", e)
                }

                // Launch target app
                launchTarget(packageName, appName, startId)

            } catch (e: Exception) {
                Log.e("LauncherService", "handleAlarm failed", e)
                try {
                    (application as AppLauncherApp).logRepository.addLog(
                        ExecutionLog(packageName, "[执行异常] $appName: ${e.message}", System.currentTimeMillis())
                    )
                } catch (_: Exception) {}
            } finally {
                wakeLock?.release()
                // Reschedule next alarms regardless of success/failure
                try {
                    rescheduleAlarms()
                } catch (e: Exception) {
                    Log.e("LauncherService", "reschedule failed", e)
                }
                stopSelf(startId)
            }
        }
    }

    private fun launchTarget(packageName: String, appName: String, startId: Int) {
        if (LauncherAccessibilityService.launchApp(packageName, appName)) return

        // Accessibility service not available — use AlarmManager to trigger BridgeActivity.
        // Direct startActivity() from a background Service is blocked on Android 10+,
        // but AlarmManager-triggered PendingIntent works reliably.
        val bridgeIntent = Intent(this, BridgeActivity::class.java).apply {
            putExtra(BridgeActivity.EXTRA_PACKAGE, packageName)
            putExtra(BridgeActivity.EXTRA_APP_NAME, appName)
        }
        val pi = PendingIntent.getActivity(
            this,
            startId,
            bridgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 100, pi)
    }

    private suspend fun rescheduleAlarms() {
        val app = application as AppLauncherApp
        val sched = app.scheduleRepository.schedule.first()
        if (sched != null && sched.enabled) {
            AlarmReceiver.schedule(this, sched)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
