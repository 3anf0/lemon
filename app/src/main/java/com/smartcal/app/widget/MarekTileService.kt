package com.smartcal.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile — toggles the Lemon floating bubble on/off.
 * Listens for BUBBLE_CLOSED broadcast so it knows when bubble was
 * dismissed via trash (stopSelf) and resets its state correctly.
 */
class MarekTileService : TileService() {

    private var bubbleActive = false

    // Receiver — bubble tells us when it closes itself (e.g. trash drop)
    private val bubbleClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_BUBBLE_CLOSED) {
                bubbleActive = false
                qsTile?.apply {
                    label = "Lemon"
                    state = Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    companion object {
        const val ACTION_BUBBLE_CLOSED = "com.smartcal.app.BUBBLE_CLOSED"
    }

    override fun onStartListening() {
        super.onStartListening()

        // Register receiver for bubble-closed events
        val filter = IntentFilter(ACTION_BUBBLE_CLOSED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleClosedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(bubbleClosedReceiver, filter)
        }

        qsTile?.apply {
            label              = "Lemon"
            contentDescription = "Dymek asystenta Lemon"
            state              = if (bubbleActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        runCatching { unregisterReceiver(bubbleClosedReceiver) }
        qsTile?.apply {
            state = if (bubbleActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        // Check overlay permission first
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        if (bubbleActive) {
            // Turn OFF
            MarekBubbleService.stop(this)
            bubbleActive = false
            qsTile?.apply {
                label = "Lemon"
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        } else {
            // Turn ON — start bubble, collapse shade
            MarekBubbleService.start(this)
            bubbleActive = true
            qsTile?.apply {
                label = "Lemon"
                state = Tile.STATE_ACTIVE
                updateTile()
            }
        }
    }
}
