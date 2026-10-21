package com.ecotracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.ui.alerts.AiAlertsActivity

object AiAlertsManager {

    const val CHANNEL_ID = "ecotracker_ai_alerts"
    private const val NOTIFICATION_ID = 3001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AI Eco Alerts & Suggestions"
            val descriptionText = "Personalized sustainability suggestions, streak alerts, and goal reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun triggerInstantAiAlert(
        context: Context,
        title: String = "🌿 EcoTracker AI Recommendation",
        message: String = "Campus weather is ideal! Walking saves ~0.35 kg CO₂ today. Keep your daily streak alive!"
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, AiAlertsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
        }
    }

    /**
     * Checks repository state and triggers a milestone or streak alert if appropriate
     */
    fun evaluateAndNotify(context: Context) {
        val state = LocalEcoRepository(context).getState()
        val title: String
        val msg: String

        if (state.streakDays > 0) {
            title = "🔥 ${state.streakDays}-Day Eco Streak!"
            msg = "Don't let your streak break today. Log your walking route or reusable bottle to protect your momentum."
        } else {
            title = "🌱 Start Your Eco Streak Today"
            msg = "Log your first eco habit in EcoTracker today to begin earning streak badges!"
        }

        triggerInstantAiAlert(context, title, msg)
    }
}
