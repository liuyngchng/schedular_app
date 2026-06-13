package com.example.applauncher

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

        // Full-screen notification for screen-on effect
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            AlarmReceiver.ALARM_CHANNEL_ID,
            "定时触发",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "定时启动应用通知"
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)

        val bridgeIntent = Intent(this, BridgeActivity::class.java).apply {
            putExtra(BridgeActivity.EXTRA_PACKAGE, packageName)
            putExtra(BridgeActivity.EXTRA_APP_NAME, appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPI = PendingIntent.getActivity(
            this, 0, bridgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, AlarmReceiver.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("定时启动")
            .setContentText("即将启动 $appName")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPI, true)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        nm.notify(AlarmReceiver.ALARM_NOTIFY_ID, notification)

        // Directly start BridgeActivity — AccessibilityService is not subject to
        // background activity launch restrictions
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
