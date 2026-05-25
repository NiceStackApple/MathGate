package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted. Initializing screen time monitor...")
            val prefs = AppPreferences(context)
            if (prefs.isSetupCompleted) {
                ScreenTimeService.start(context)
            }
        }
    }
}
