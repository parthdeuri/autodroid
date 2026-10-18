package com.iitg.autologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Fired by the system whenever the Wi-Fi network's state/capabilities change. */
class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras?.keySet()?.joinToString() ?: "none"
        LogStore.log("NetworkReceiver", "Wi-Fi network event (extras: $extras)")
        if (!CredentialStore(context).enabled) {
            LogStore.log("NetworkReceiver", "app switched OFF; ignoring")
            return
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = WifiUtil.wifiNetwork(context)
        val validated = wifi?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
        LogStore.log("NetworkReceiver", "Wi-Fi internet validated=$validated")
        Scheduler.enqueueNow(context, if (validated == false) "no-internet" else "network-event")
    }
}
