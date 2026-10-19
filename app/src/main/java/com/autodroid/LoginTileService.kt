package com.autodroid

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * "Login now" tile: tapping it lights the tile up, runs the same action as the Login now button,
 * then shows the result and turns the tile off again.
 * Android binds this service only while the panel is visible, so nothing runs in the background.
 */
class LoginTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val poll = object : Runnable {
        override fun run() {
            render()
            if (isRunning() && ++ticks < 120) handler.postDelayed(this, 1000)
        }
    }

    private fun isRunning(): Boolean = try {
        val since = CredentialStore(this).manualRunningSince
        since != 0L && System.currentTimeMillis() - since < 120_000
    } catch (e: Exception) {
        false
    }

    override fun onStartListening() {
        render()
        if (isRunning()) {
            ticks = 0
            handler.removeCallbacks(poll)
            handler.postDelayed(poll, 1000)
        }
    }

    override fun onStopListening() {
        handler.removeCallbacks(poll)
    }

    override fun onClick() {
        LogStore.log("Tile", "Login now tile tapped")
        try {
            val store = CredentialStore(this)
            store.userLoggedOut = false
            store.lastStatus = "Logging in..."
            store.manualRunningSince = System.currentTimeMillis()
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
            val running = isRunning()
            val recent = System.currentTimeMillis() - store.manualDoneAt < 60_000
            tile.label = "Login now"
            // Lit only while a login is running; otherwise off, with the last result as the subtitle.
            tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = when {
                    running -> "Logging in..."
                    recent && store.manualOk -> "Logged in"
                    recent -> "Failed: " + store.manualMsg.take(18)
                    else -> "Tap to log in"
                }
            }
        } catch (e: Exception) {
            LogStore.log("Tile", "render failed: ${e.message}")
        }
        tile.updateTile()
    }
}
