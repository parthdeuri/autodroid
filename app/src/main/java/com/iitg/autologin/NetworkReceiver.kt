package com.iitg.autologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the system whenever the Wi-Fi network's state/capabilities change. */
class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras?.keySet()?.joinToString() ?: "none"
        LogStore.log("NetworkReceiver", "Wi-Fi network event (extras: $extras)")
        Scheduler.enqueueNow(context, "network-event")
    }
}
