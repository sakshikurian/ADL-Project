package com.ecotracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecotracker.data.LocalEcoRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val repository = LocalEcoRepository(context.applicationContext)
            if (repository.areNotificationsEnabled()) {
                val (hour, minute) = repository.getReminderTime()
                ReminderScheduler.scheduleReminder(context, hour, minute)
            }
        }
    }
}
