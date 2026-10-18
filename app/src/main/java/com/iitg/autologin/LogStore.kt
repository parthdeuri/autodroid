package com.iitg.autologin

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Append-only file logger (files/autologin.log), rotated at ~512 KB. Never log passwords. */
object LogStore {
    private const val MAX_BYTES = 512 * 1024L
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(ctx: Context) {
        file = File(ctx.applicationContext.filesDir, "autologin.log")
    }

    fun file(): File? = file

    @Synchronized
    fun log(tag: String, msg: String, t: Throwable? = null) {
        Log.i("IITGAutologin", "[$tag] $msg", t)
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText("... (rotated)\n$keep")
            }
            val sb = StringBuilder()
            sb.append(fmt.format(Date())).append(" [").append(tag).append("] ").append(msg).append('\n')
            if (t != null) sb.append(Log.getStackTraceString(t)).append('\n')
            f.appendText(sb.toString())
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    fun read(maxChars: Int = 40000): String {
        val f = file ?: return "(log not initialised)"
        return try {
            if (f.exists()) f.readText().takeLast(maxChars) else "(log is empty)"
        } catch (e: Exception) {
            "(cannot read log: ${e.message})"
        }
    }

    @Synchronized
    fun clear() {
        try {
            file?.writeText("")
        } catch (ignored: Exception) {
        }
    }
}
