package com.autodroid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Factory defaults for every editable setting. */
object Defaults {
    const val RENEW_MIN = 25
    const val CHECK_MIN = 10
    const val MAX_ATTEMPTS = 3
    const val BACKOFF_SEC = 15
    const val RETRY_WAIT_MIN = 2
}

/** Credentials, SSID allow-list and last-status, kept in Keystore-backed encrypted prefs. */
class CredentialStore(ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx.applicationContext,
        "secure_prefs",
        MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var username: String
        get() = prefs.getString("user", "") ?: ""
        set(v) = prefs.edit().putString("user", v).apply()

    var password: String
        get() = prefs.getString("pass", "") ?: ""
        set(v) = prefs.edit().putString("pass", v).apply()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** True after the user pressed Log out; auto-login stays paused until they log in again. */
    var userLoggedOut: Boolean
        get() = prefs.getBoolean("user_logged_out", false)
        set(v) = prefs.edit().putBoolean("user_logged_out", v).apply()

    var loggedIn: Boolean
        get() = prefs.getBoolean("logged_in", false)
        set(v) = prefs.edit().putBoolean("logged_in", v).apply()

    /** Minutes between scheduled re-logins (portal session lasts ~20). */
    var renewMin: Int
        get() = prefs.getInt("renew_min", Defaults.RENEW_MIN)
        set(v) = prefs.edit().putInt("renew_min", v.coerceAtLeast(1)).apply()

    /** Minutes between keepalive checks while logged in. */
    var checkMin: Int
        get() = prefs.getInt("check_min", Defaults.CHECK_MIN)
        set(v) = prefs.edit().putInt("check_min", v.coerceAtLeast(1)).apply()

    /** Login attempts per round before giving up and notifying. */
    var maxAttempts: Int
        get() = prefs.getInt("max_attempts", Defaults.MAX_ATTEMPTS)
        set(v) = prefs.edit().putInt("max_attempts", v.coerceIn(1, 10)).apply()

    /** Seconds before the first retry inside a round (grows exponentially). */
    var backoffSec: Int
        get() = prefs.getInt("backoff_sec", Defaults.BACKOFF_SEC)
        set(v) = prefs.edit().putInt("backoff_sec", v.coerceAtLeast(10)).apply()

    /** Minutes to wait before starting a new round after a failed one. */
    var retryWaitMin: Int
        get() = prefs.getInt("retry_wait_min", Defaults.RETRY_WAIT_MIN)
        set(v) = prefs.edit().putInt("retry_wait_min", v.coerceAtLeast(1)).apply()

    /** SSID we were last connected to, so reconnecting to a saved network is noticed. */
    var lastSsid: String
        get() = prefs.getString("last_ssid", "") ?: ""
        set(v) = prefs.edit().putString("last_ssid", v).apply()

    /** Set by the Login now tile when it starts a login; cleared by the worker when that login ends. */
    var manualRunningSince: Long
        get() = prefs.getLong("manual_running", 0L)
        set(v) = prefs.edit().putLong("manual_running", v).apply()

    var manualDoneAt: Long
        get() = prefs.getLong("manual_done_at", 0L)
        set(v) = prefs.edit().putLong("manual_done_at", v).apply()

    var manualOk: Boolean
        get() = prefs.getBoolean("manual_ok", false)
        set(v) = prefs.edit().putBoolean("manual_ok", v).apply()

    var manualMsg: String
        get() = prefs.getString("manual_msg", "") ?: ""
        set(v) = prefs.edit().putString("manual_msg", v).apply()

    var lastLoginAt: Long
        get() = prefs.getLong("last_login", 0L)
        set(v) = prefs.edit().putLong("last_login", v).apply()

    var lastStatus: String
        get() = prefs.getString("status", "No attempt yet") ?: ""
        set(v) = prefs.edit().putString("status", v).apply()

    val ssids: Set<String>
        get() = prefs.getStringSet("ssids", emptySet())?.toSet() ?: emptySet()

    fun addSsid(s: String) = prefs.edit().putStringSet("ssids", ssids + s).apply()

    fun removeSsid(s: String) = prefs.edit().putStringSet("ssids", ssids - s).apply()
}
