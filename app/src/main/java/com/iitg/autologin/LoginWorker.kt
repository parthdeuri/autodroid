package com.iitg.autologin

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class LoginWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val tag = "Worker"
    private var patient = false

    override fun doWork(): Result {
        val ctx = applicationContext
        val reason = inputData.getString("reason") ?: "?"
        LogStore.log(tag, "start reason=$reason attempt=$runAttemptCount")

        val store = try {
            CredentialStore(ctx)
        } catch (e: Exception) {
            LogStore.log(tag, "cannot open secure storage", e)
            return Result.failure()
        }
        if (!store.enabled) {
            store.lastStatus = "Off"
            LogStore.log(tag, "app switched OFF; nothing to do")
            return Result.success()
        }
        if (store.userLoggedOut && reason != "manual") {
            store.loggedIn = false
            store.lastStatus = "Logged out. Tap Login now to resume."
            LogStore.log(tag, "user logged out; auto-login paused")
            return Result.success()
        }
        if (store.username.isBlank() || store.password.isBlank()) {
            store.lastStatus = "No credentials saved"
            LogStore.log(tag, "no credentials saved; nothing to do")
            return Result.success()
        }

        val renew = store.renewMin.toLong()
        val net = WifiUtil.wifiNetwork(ctx)
        if (net == null) {
            store.lastSsid = ""
            store.lastStatus = "Not connected to Wi-Fi"
            LogStore.log(tag, "not on Wi-Fi; nothing to do")
            return Result.success()
        }

        val ssid = WifiUtil.currentSsid(ctx)
        val allowed = store.ssids
        if (ssid == null) {
            store.lastStatus = "Can't read Wi-Fi name (check permissions); trying portal anyway"
            LogStore.log(
                tag,
                "SSID unreadable (location permission fine=${WifiUtil.hasFineLocation(ctx)} " +
                    "background=${WifiUtil.hasBackgroundLocation(ctx)}, or location is off); " +
                    "falling back to portal reachability check"
            )
        } else if (ssid !in allowed) {
            store.lastStatus = "Skipped: '$ssid' is not in your Wi-Fi list"
            LogStore.log(tag, "SSID '$ssid' not in allow-list $allowed; skipping")
            return Result.success()
        } else {
            LogStore.log(tag, "SSID '$ssid' is allowed")
            if (store.lastSsid != ssid) {
                patient = true
                LogStore.log(tag, "just connected to saved network '$ssid'; will log in if the portal asks")
            }
        }

        val now = System.currentTimeMillis()
        val ageMin = if (store.lastLoginAt == 0L) -1L else (now - store.lastLoginAt) / 60000
        val code = PortalClient.probe(net)
        if (code == 200) {
            if (reason != "manual") {
                if (ageMin < 0) {
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.lastStatus = "Online (login time unknown)"
                    LogStore.log(tag, "internet OK, no recorded login; re-check in 10 min")
                    Scheduler.enqueueIn(ctx, 10, "online-recheck")
                    return Result.success()
                }
                if (ageMin < renew) {
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.lastStatus = "Online (last login $ageMin min ago)"
                    LogStore.log(tag, "internet OK, last login $ageMin min ago; no action")
                    Scheduler.enqueueIn(ctx, (renew - ageMin).coerceAtLeast(1), "renew-check")
                    return Result.success()
                }
            }
            LogStore.log(tag, "internet OK but re-logging in (reason=$reason, session age $ageMin min)")
        } else {
            store.loggedIn = false
            store.lastStatus = "Not logged in (probe $code); logging in..."
            LogStore.log(tag, "no internet (probe=$code); attempting portal login")
        }

        return when (val r = PortalClient.login(net, store.username, store.password)) {
            is PortalClient.Result.Success -> {
                Thread.sleep(1500)
                val after = PortalClient.probe(net)
                if (after == 200) {
                    store.lastLoginAt = System.currentTimeMillis()
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.userLoggedOut = false
                    store.lastStatus = "Logged in OK"
                    LogStore.log(tag, "login verified (internet reachable)")
                    Scheduler.enqueueIn(ctx, renew, "renew")
                    Result.success()
                } else {
                    LogStore.log(tag, "portal accepted login but probe=$after (no internet yet)")
                    retryOrFail(store, "Login accepted but no internet (probe $after)")
                }
            }
            is PortalClient.Result.BadCredentials -> {
                store.lastStatus = "Invalid credentials - fix them in the app"
                LogStore.log(tag, "invalid credentials; not retrying")
                Result.success()
            }
            is PortalClient.Result.Unreachable -> {
                if (ssid == null) {
                    store.lastStatus = "Not on IITG portal (${r.msg})"
                    LogStore.log(tag, "portal unreachable and SSID unknown -> probably not IITG; ignoring (${r.msg})")
                    Result.success()
                } else {
                    retryOrFail(store, "Portal unreachable: ${r.msg}")
                }
            }
            is PortalClient.Result.Failed -> retryOrFail(store, "Login failed: ${r.msg}")
        }
    }

    private fun retryOrFail(store: CredentialStore, status: String): Result {
        store.lastStatus = status
        store.loggedIn = false
        return if (runAttemptCount >= (if (patient) 5 else 3)) {
            LogStore.log(tag, "giving up after ${runAttemptCount + 1} attempts: $status")
            Result.failure()
        } else {
            LogStore.log(tag, "will retry (attempt ${runAttemptCount + 1}): $status")
            Result.retry()
        }
    }

}
