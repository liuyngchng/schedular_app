package com.example.applauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.applauncher.AppLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as AppLauncherApp
        CoroutineScope(Dispatchers.IO).launch {
            val schedule = app.scheduleRepository.schedule.first()
            if (schedule != null) {
                AlarmReceiver.schedule(context, schedule)
            }
        }
    }
}
