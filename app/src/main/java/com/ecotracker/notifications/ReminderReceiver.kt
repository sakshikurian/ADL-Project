package com.ecotracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecotracker.data.LocalEcoRepository

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = LocalEcoRepository(context.applicationContext)
        if (!repository.areNotificationsEnabled()) {
            ReminderScheduler.cancelReminder(context)
            return
        }

        // Display the notification
        NotificationHelper.showReminderNotification(context)

        // Reschedule for the next day
        val (hour, minute) = repository.getReminderTime()
        ReminderScheduler.scheduleReminder(context, hour, minute)
    }
}
