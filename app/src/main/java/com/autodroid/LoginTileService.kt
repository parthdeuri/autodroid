package com.autodroid

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tapping it does the same as "Login now" in the app.
 * Android binds this service only while the panel is visible, so nothing runs in the background.
 */
class LoginTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val poll = object : Runnable {
        override fun run() {
            render()
            if (++ticks < 25) handler.postDelayed(this, 1000)
        }
    }

    override fun onStartListening() {
        render()
    }

    override fun onStopListening() {
        handler.removeCallbacks(poll)
    }

    override fun onClick() {
        LogStore.log("Tile", "tile tapped: login now")
        try {
            val store = CredentialStore(this)
            store.userLoggedOut = false
            store.lastStatus = "Logging in..."
            Scheduler.enqueueNow(this, "manual")
        } catch (e: Exception) {
            LogStore.log("Tile", "could not start login", e)
        }
        ticks = 0
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun render() {
        val tile = qsTile ?: return
        try {
            val store = CredentialStore(this)
            tile.label = "Login now"
            tile.state = if (store.loggedIn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = when {
                    !store.enabled -> "App is off"
                    store.loggedIn -> "Logged in"
                    else -> store.lastStatus.take(28)
                }
            }
        } catch (e: Exception) {
            LogStore.log("Tile", "render failed: ${e.message}")
        }
        tile.updateTile()
    }
}
