package com.example.applauncher

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import com.example.applauncher.model.ExecutionLog
import com.example.applauncher.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BridgeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName ?: "未知"

        // Log alarm fired immediately so user sees exact trigger time
        if (packageName != null) {
            CoroutineScope(Dispatchers.IO).launch {
                (application as AppLauncherApp).logRepository.addLog(
                    ExecutionLog(packageName, "[闹钟触发] $appName", System.currentTimeMillis())
                )
            }
        }

        // Wake up and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Dismiss keyguard
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            val lock = km.newKeyguardLock("AppLauncher")
            @Suppress("DEPRECATION")
            lock.disableKeyguard()
        }

        // Acquire wake lock
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AppLauncher:Bridge"
        )
        wakeLock.acquire(5000L)

        if (packageName == null) {
            Log.e("BridgeActivity", "Missing target package name in intent")
            CoroutineScope(Dispatchers.IO).launch {
                (application as AppLauncherApp).logRepository.addLog(
                    ExecutionLog("", "[缺少目标包名]", System.currentTimeMillis())
                )
            }
            wakeLock.release()
            finish()
            return
        }

        // Check if keyguard is still locked
        val isKeyguardLocked =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.isKeyguardLocked
            } else {
                @Suppress("DEPRECATION")
                km.isKeyguardLocked
            }
        if (isKeyguardLocked) {
            Log.d("BridgeActivity", "Keyguard is locked — target may appear behind lock screen")
        }

        // Launch target app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(launchIntent)
                CoroutineScope(Dispatchers.IO).launch {
                    (application as AppLauncherApp).logRepository.addLog(
                        ExecutionLog(packageName, appName, System.currentTimeMillis())
                    )
                }
            } catch (e: Exception) {
                Log.e("BridgeActivity", "startActivity failed for $packageName", e)
                CoroutineScope(Dispatchers.IO).launch {
                    (application as AppLauncherApp).logRepository.addLog(
                        ExecutionLog(packageName, "[启动失败] 无法启动 $appName: ${e.message}", System.currentTimeMillis())
                    )
                }
            }
        } else {
            Log.e("BridgeActivity", "Target app not installed: $packageName")
            CoroutineScope(Dispatchers.IO).launch {
                (application as AppLauncherApp).logRepository.addLog(
                    ExecutionLog(packageName, "[应用不存在] $appName 可能已被卸载", System.currentTimeMillis())
                )
            }
        }

        // Re-schedule alarms for next week
        CoroutineScope(Dispatchers.IO).launch {
            val app = application as AppLauncherApp
            val sched = app.scheduleRepository.schedule.first()
            if (sched != null && sched.enabled) {
                AlarmReceiver.schedule(this@BridgeActivity, sched)
            }
        }

        wakeLock.release()
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_APP_NAME = "target_app_name"
    }
}
