package com.smartcal.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartcal.app.data.model.CalEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventReminderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun schedule(event: CalEvent, reminderMinutesBefore: Int = 15) {
        val triggerAt = event.startTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() - reminderMinutesBefore * 60_000L

        if (triggerAt <= System.currentTimeMillis()) return

        val pi = buildPendingIntent(event)
        // setAndAllowWhileIdle fires even in Doze mode, no extra permission needed
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(event: CalEvent) {
        alarmManager.cancel(buildPendingIntent(event))
    }

    private fun buildPendingIntent(event: CalEvent): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_TIME, event.startTime.format(timeFmt))
            putExtra(ReminderReceiver.EXTRA_ID, event.id)
        }
        return PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
