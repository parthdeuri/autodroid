package com.iitg.autologin

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val TAG = "Scheduler"

    /** Register system-held Wi-Fi callback + periodic fallback. Safe to call repeatedly. */
    fun ensure(ctx: Context) {
        if (!CredentialStore(ctx).enabled) {
            LogStore.log(TAG, "ensure(): app is switched OFF; nothing scheduled")
            return
        }
        registerNetworkCallback(ctx)
        val req = PeriodicWorkRequestBuilder<LoginWorker>(15, TimeUnit.MINUTES)
            .setInputData(reason("periodic"))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("login-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        LogStore.log(TAG, "ensure(): callback registered, periodic 15 min work kept")
    }

    private fun callbackIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, NetworkReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    /** Switch off: drop the system callback and cancel all pending work. */
    fun disable(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callbackIntent(ctx))
        } catch (e: Exception) {
            LogStore.log(TAG, "unregisterNetworkCallback: ${e.message}")
        }
        WorkManager.getInstance(ctx).cancelAllWork()
        LogStore.log(TAG, "disable(): callback removed, all work cancelled")
    }

    private fun registerNetworkCallback(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callbackIntent(ctx))
        } catch (e: Exception) {
            LogStore.log(TAG, "registerNetworkCallback failed", e)
        }
    }

    private fun reason(r: String) = Data.Builder().putString("reason", r).build()

    fun cancelLater(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork("login-later")
        LogStore.log(TAG, "pending renew check cancelled")
    }

    fun enqueueNow(ctx: Context, why: String) {
        val req = OneTimeWorkRequestBuilder<LoginWorker>()
            .setInputData(reason(why))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CredentialStore(ctx).backoffSec.toLong(), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("login-now", ExistingWorkPolicy.KEEP, req)
        LogStore.log(TAG, "enqueueNow($why)")
    }

    fun enqueueIn(ctx: Context, minutes: Long, why: String) {
        val req = OneTimeWorkRequestBuilder<LoginWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(reason(why))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CredentialStore(ctx).backoffSec.toLong(), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("login-later", ExistingWorkPolicy.REPLACE, req)
        LogStore.log(TAG, "next check in $minutes min ($why)")
    }
}
