package com.iitg.autologin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Failure notification (one slot, replaced on each failure, cleared on success). */
object Notifier {
    private const val CHANNEL = "login_status"
    private const val ID = 1

    fun failure(ctx: Context, text: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Login status", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Autodroid couldn't log in")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(ID, n)
            LogStore.log("Notifier", "notification sent: $text")
        } catch (e: Exception) {
            LogStore.log("Notifier", "notification failed", e)
        }
    }

    fun clear(ctx: Context) {
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ID)
        } catch (e: Exception) {
            LogStore.log("Notifier", "clear failed: ${e.message}")
        }
    }
}
