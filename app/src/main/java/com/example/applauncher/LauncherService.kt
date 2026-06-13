package com.example.applauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.applauncher.model.ExecutionLog
import com.example.applauncher.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LauncherService : Service() {

    companion object {
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_APP_NAME = "target_app_name"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Low-importance channel for persistent service notification
        val channel = NotificationChannel(
            "app_launcher_service",
            "定时启动器",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持定时启动器在后台运行"
        }
        nm.createNotificationChannel(channel)
        // High-importance channel for full-screen alarm
        val alarmChannel = NotificationChannel(
            AlarmReceiver.ALARM_CHANNEL_ID,
            "定时触发",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "定时启动应用通知"
            setBypassDnd(true)
        }
        nm.createNotificationChannel(alarmChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        val appName = intent?.getStringExtra(EXTRA_APP_NAME)

        if (packageName != null) {
            // Alarm fired — launch target app via full-screen notification
            handleAlarm(packageName, appName ?: packageName)
        } else {
            // Regular start — just show persistent notification
            showPersistentNotification()
        }
        return START_STICKY
    }

    private fun handleAlarm(packageName: String, appName: String) {
        Log.d("LauncherService", "Alarm fired: $appName ($packageName)")

        // Try AccessibilityService first (highest priority, no restrictions)
        if (LauncherAccessibilityService.launchApp(packageName, appName)) return

        // Fallback: handle launch ourselves (foreground context, no restrictions)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AppLauncher:Alarm"
        )
        wakeLock.acquire(10000L)

        // Log alarm trigger
        CoroutineScope(Dispatchers.IO).launch {
            (application as AppLauncherApp).logRepository.addLog(
                ExecutionLog(packageName, "[闹钟触发] $appName", System.currentTimeMillis())
            )
        }

        // Full-screen notification for screen-on effect
        val bridgeIntent = Intent(this, BridgeActivity::class.java).apply {
            putExtra(BridgeActivity.EXTRA_PACKAGE, packageName)
            putExtra(BridgeActivity.EXTRA_APP_NAME, appName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullScreenPI = PendingIntent.getActivity(
            this, 0, bridgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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

        // Directly start BridgeActivity — foreground service is not subject to
        // background activity launch restrictions
        try {
            startActivity(bridgeIntent)
        } catch (e: Exception) {
            Log.e("LauncherService", "startActivity failed", e)
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
                AlarmReceiver.schedule(this@LauncherService, sched)
            }
        }

        wakeLock.release()
    }

    private fun showPersistentNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, "app_launcher_service")
            .setContentTitle("定时启动器")
            .setContentText("正在后台运行，等待定时触发")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
