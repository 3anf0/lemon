package com.smartcal.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartcal.app.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val time  = intent.getStringExtra(EXTRA_TIME)  ?: ""
        val id    = intent.getLongExtra(EXTRA_ID, 0L)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Za 15 min: $title")
            .setContentText("Wydarzenie o $time")
            .setSmallIcon(R.drawable.ic_tile_marek)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(id.toInt(), notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Przypomnienia o wydarzeniach",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Powiadomienia 15 min przed wydarzeniem" }
            )
        }
    }

    companion object {
        const val CHANNEL_ID   = "reminders_channel"
        const val EXTRA_TITLE  = "title"
        const val EXTRA_TIME   = "time"
        const val EXTRA_ID     = "event_id"
    }
}
