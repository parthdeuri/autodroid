package com.autodroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

object WifiUtil {
    fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    fun hasFineLocation(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Connected SSID without quotes, or null when it cannot be read (no permission / location off). */
    fun currentSsid(ctx: Context): String? {
        if (!hasFineLocation(ctx)) return null
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val raw = wm.connectionInfo?.ssid ?: return null
        val s = raw.removeSurrounding("\"")
        return if (s.isBlank() || s == "<unknown ssid>") null else s
    }
}
