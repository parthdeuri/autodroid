package com.autodroid

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings toggle for the whole auto-login feature, the same as the switch in the app.
 * ON registers the Wi-Fi listener and scheduled checks; OFF cancels them all.
 */
class AutoLoginTileService : TileService() {
    override fun onStartListening() {
        render()
    }

    override fun onClick() {
        try {
            val store = CredentialStore(this)
            val turnOn = !store.enabled
            LogStore.log("Tile", "auto-login tile tapped: turning ${if (turnOn) "ON" else "OFF"}")
            Scheduler.setEnabled(this, turnOn)
        } catch (e: Exception) {
            LogStore.log("Tile", "toggle failed", e)
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        try {
            val on = CredentialStore(this).enabled
            tile.label = "Auto-login"
            tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "On" else "Off"
        } catch (e: Exception) {
            LogStore.log("Tile", "render failed: ${e.message}")
        }
        tile.updateTile()
    }
}
