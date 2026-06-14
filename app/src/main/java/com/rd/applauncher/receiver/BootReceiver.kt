package com.rd.applauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rd.applauncher.AppLauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val app = context.applicationContext as AppLauncherApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedule = app.scheduleRepository.schedule.first()
                if (schedule != null) {
                    AlarmReceiver.schedule(context, schedule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
