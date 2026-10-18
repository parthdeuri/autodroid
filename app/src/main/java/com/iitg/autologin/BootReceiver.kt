package com.iitg.autologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers the network callback after reboot / app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LogStore.log("BootReceiver", "received ${intent.action}")
        Scheduler.ensure(context)
        Scheduler.enqueueNow(context, "boot")
    }
}
