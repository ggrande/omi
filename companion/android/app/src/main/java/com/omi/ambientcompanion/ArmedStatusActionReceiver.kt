package com.omi.ambientcompanion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ArmedStatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> AmbientForegroundMicService.start(context, "armed_notification_start")
            ACTION_SYNC -> SyncWorker.drainAsync(context)
            ACTION_PRIVATE -> {
                AmbientForegroundMicService.command(context, AmbientForegroundMicService.ACTION_PRIVATE)
                ArmedStatusNotifier.show(context, "Private mode. Mic is idle.")
                AuditLog(context).record("private_mode_enabled", mapOf("source" to "armed_notification"))
            }
        }
    }

    companion object {
        const val ACTION_START = "com.omi.ambientcompanion.ARMED_START"
        const val ACTION_SYNC = "com.omi.ambientcompanion.ARMED_SYNC"
        const val ACTION_PRIVATE = "com.omi.ambientcompanion.ARMED_PRIVATE"
    }
}
