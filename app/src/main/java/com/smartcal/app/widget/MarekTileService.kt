package com.smartcal.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class MarekTileService : TileService() {

    private var wakeActive = false

    private val wakeStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_STOPPED) {
                wakeActive = false
                updateTileState()
            }
        }
    }

    companion object {
        const val ACTION_BUBBLE_CLOSED = "com.smartcal.app.BUBBLE_CLOSED"
    }

    override fun onStartListening() {
        super.onStartListening()
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeStoppedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(wakeStoppedReceiver, filter)
        }
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        runCatching { unregisterReceiver(wakeStoppedReceiver) }
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        if (wakeActive) {
            WakeWordService.stop(this)
            MarekBubbleService.stop(this)
            wakeActive = false
        } else {
            WakeWordService.start(this)
            MarekBubbleService.startAutoListen(this)
            wakeActive = true
        }
        updateTileState()
    }

    private fun updateTileState() {
        qsTile?.apply {
            label = "Lemon"
            contentDescription = "Asystent Lemon"
            state = if (wakeActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
