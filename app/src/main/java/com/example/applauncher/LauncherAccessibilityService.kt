package com.example.applauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.applauncher.model.ExecutionLog
import com.example.applauncher.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LauncherAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: LauncherAccessibilityService? = null

        fun launchApp(packageName: String, appName: String): Boolean {
            val inst = instance ?: return false
            inst.doLaunch(packageName, appName)
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AccessibilityService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun doLaunch(packageName: String, appName: String) {
        Log.d("AccessibilityService", "Launching: $appName ($packageName)")

        // Acquire wake lock
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AppLauncher:Accessibility"
        )
        wakeLock.acquire(10000L)

        // Log alarm trigger
        CoroutineScope(Dispatchers.IO).launch {
            (application as AppLauncherApp).logRepository.addLog(
                ExecutionLog(packageName, "[闹钟触发] $appName", System.currentTimeMillis())
            )
        }

        // Directly start BridgeActivity — AccessibilityService is not subject to
        // background activity launch restrictions
        val bridgeIntent = Intent(this, BridgeActivity::class.java).apply {
            putExtra(BridgeActivity.EXTRA_PACKAGE, packageName)
            putExtra(BridgeActivity.EXTRA_APP_NAME, appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(bridgeIntent)
        } catch (e: Exception) {
            Log.e("AccessibilityService", "startActivity failed", e)
            CoroutineScope(Dispatchers.IO).launch {
                (application as AppLauncherApp).logRepository.addLog(
                    ExecutionLog(packageName, "[启动失败] $appName", System.currentTimeMillis())
                )
            }
        }

        // Re-schedule for next week
        CoroutineScope(Dispatchers.IO).launch {
            val app = application as AppLauncherApp
            val sched = app.scheduleRepository.schedule.first()
            if (sched != null && sched.enabled) {
                AlarmReceiver.schedule(this@LauncherAccessibilityService, sched)
            }
        }

        wakeLock.release()
    }
}
