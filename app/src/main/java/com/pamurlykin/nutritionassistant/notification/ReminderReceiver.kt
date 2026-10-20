package com.pamurlykin.nutritionassistant.notification

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pamurlykin.nutritionassistant.*
import com.pamurlykin.nutritionassistant.util.StorageFormats
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { handleReminder(context, intent) } finally { result.finish() }
        }
    }

    private suspend fun handleReminder(context: Context, intent: Intent) {
        val userId = intent.getLongExtra(NotificationScheduler.EXTRA_USER_ID, -1L)
        val type = ReminderType.entries.find { it.storageValue == intent.getStringExtra(NotificationScheduler.EXTRA_REMINDER_TYPE) } ?: return
        if (userId != com.pamurlykin.nutritionassistant.data.model.LocalUserDefaults.USER_ID) return
        val container = (context.applicationContext as NutritionAssistantApplication).container
        val user = container.repository.getUser(userId)
        // Recalculate tomorrow in local time, including daylight saving changes.
        container.notificationScheduler.schedule(user, type)
        if (!type.scheduleSlot.enabled(user)) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (BuildCompat.requiresNotificationPermission() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        container.notificationScheduler.createNotificationChannel()
        val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .getNotificationChannel(NotificationScheduler.CHANNEL_ID)
        if (channel?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return
        if (!container.repository.markNotificationSent(userId, type.storageValue, StorageFormats.today())) return
        val openDiary = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify((userId * 100 + type.ordinal).toInt(),
            NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(type.title).setContentText(type.message)
                .setContentIntent(openDiary).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
}
