package com.iitg.autologin

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
