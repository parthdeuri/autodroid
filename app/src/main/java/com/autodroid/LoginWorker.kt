package com.autodroid

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters

class LoginWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val tag = "Worker"
    private var loginSucceeded = false

    override fun doWork(): Result {
        val reason = inputData.getString("reason") ?: "?"
        val result = run(reason)
        if (reason == "manual" && result != Result.retry()) finishManual()
        return result
    }

    /** Tell the user how a manual login (button or tile) ended, and let the tile reset itself. */
    private fun finishManual() {
        val ctx = applicationContext
        try {
            val store = CredentialStore(ctx)
            val ok = loginSucceeded
            val msg = if (ok) "Logged in" else store.lastStatus
            store.manualRunningSince = 0L
            store.manualDoneAt = System.currentTimeMillis()
            store.manualOk = ok
            store.manualMsg = msg
            LogStore.log(tag, "manual login finished: ${if (ok) "success" else "failure"} ($msg)")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "Autodroid: $msg", Toast.LENGTH_LONG).show()
                TileService.requestListeningState(ctx, ComponentName(ctx, LoginTileService::class.java))
            }
        } catch (e: Exception) {
            LogStore.log(tag, "finishManual failed", e)
        }
    }

    private fun run(reason: String): Result {
        val ctx = applicationContext
        LogStore.log(tag, "start reason=$reason attempt=$runAttemptCount")

        val store = try {
            CredentialStore(ctx)
        } catch (e: Exception) {
            LogStore.log(tag, "cannot open secure storage", e)
            return Result.failure()
        }
        if (!store.enabled && reason != "manual") {
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
        val check = store.checkMin.toLong()
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
                LogStore.log(tag, "just connected to saved network '$ssid'; will log in if the portal asks")
            }
        }

        val now = System.currentTimeMillis()
        val ageMin = if (store.lastLoginAt == 0L) -1L else (now - store.lastLoginAt) / 60000
        val code = PortalClient.probe(net)
        // Android reports "no internet" on this Wi-Fi: renew now.
        val force = reason == "manual" || reason == "no-internet"
        if (code == 200) {
            if (!force) {
                if (ageMin < 0) {
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.lastStatus = "Online (login time unknown)"
                    LogStore.log(tag, "internet OK, no recorded login; re-check in $check min")
                    Scheduler.enqueueIn(ctx, check, "online-recheck")
                    return Result.success()
                }
                if (ageMin < renew) {
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.lastStatus = "Online (last login $ageMin min ago)"
                    LogStore.log(tag, "internet OK, last login $ageMin min ago; no action")
                    Scheduler.enqueueIn(ctx, minOf(check, renew - ageMin).coerceAtLeast(1), "renew-check")
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
                    loginSucceeded = true
                    store.lastLoginAt = System.currentTimeMillis()
                    store.loggedIn = true; store.lastSsid = ssid ?: ""
                    store.userLoggedOut = false
                    Notifier.clear(ctx)
                    store.lastStatus = "Logged in OK"
                    LogStore.log(tag, "login verified (internet reachable)")
                    Scheduler.enqueueIn(ctx, minOf(check, renew), "renew-check")
                    Result.success()
                } else {
                    LogStore.log(tag, "portal accepted login but probe=$after (no internet yet)")
                    retryOrFail(store, "Login accepted but no internet (probe $after)")
                }
            }
            is PortalClient.Result.BadCredentials -> {
                store.lastStatus = "Invalid credentials - fix them in the app"
                Notifier.failure(ctx, "Invalid username or password. Fix them in the app.")
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
            is PortalClient.Result.Failed -> {
                if (code == 200 && r.msg.startsWith("magic token not found")) {
                    loginSucceeded = true
                    store.loggedIn = true
                    store.lastStatus = "Portal says already logged in"
                    LogStore.log(tag, "no magic token but keepalive says logged in; nothing to renew")
                    Result.success()
                } else {
                    retryOrFail(store, "Login failed: ${r.msg}")
                }
            }
        }
    }

    private fun retryOrFail(store: CredentialStore, status: String): Result {
        store.lastStatus = status
        store.loggedIn = false
        return if (runAttemptCount >= store.maxAttempts - 1) {
            LogStore.log(tag, "giving up after ${runAttemptCount + 1} attempts: $status; new round in ${store.retryWaitMin} min")
            Notifier.failure(applicationContext, "$status. Will retry in ${store.retryWaitMin} min.")
            Scheduler.enqueueIn(applicationContext, store.retryWaitMin.toLong(), "retry-round")
            Result.failure()
        } else {
            LogStore.log(tag, "will retry (attempt ${runAttemptCount + 1}): $status")
            Result.retry()
        }
    }

}
