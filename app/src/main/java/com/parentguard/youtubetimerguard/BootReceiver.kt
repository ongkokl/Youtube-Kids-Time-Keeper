package com.parentguard.youtubetimerguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.isEnabled(context)) {
            try {
                context.startForegroundService(Intent(context, MonitorService::class.java))
            } catch (_: Exception) {
                // User can reopen the app if the device vendor blocks autostart.
            }
        }
    }
}
