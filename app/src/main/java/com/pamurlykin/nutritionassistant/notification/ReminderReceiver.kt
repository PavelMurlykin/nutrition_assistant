package com.pamurlykin.nutritionassistant.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pamurlykin.nutritionassistant.AppContainer
import com.pamurlykin.nutritionassistant.R
import com.pamurlykin.nutritionassistant.util.StorageFormats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleReminder(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(context: Context, intent: Intent) {
        val userId = intent.getLongExtra(NotificationScheduler.EXTRA_USER_ID, -1L)
        val reminderType = intent.getStringExtra(NotificationScheduler.EXTRA_REMINDER_TYPE).orEmpty()
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(NotificationScheduler.EXTRA_MESSAGE).orEmpty()
        if (userId <= 0L || reminderType.isBlank()) {
            return
        }

        if (
            BuildCompat.requiresNotificationPermission() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val container = AppContainer(context.applicationContext)
        val date = StorageFormats.today()
        val marked = container.repository.markNotificationSent(
            userId = userId,
            type = reminderType,
            date = date,
        )
        if (!marked) {
            return
        }

        container.notificationScheduler.createNotificationChannel()
        NotificationManagerCompat.from(context).notify(
            (userId * 1000 + reminderType.hashCode()).toInt(),
            NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build(),
        )
    }
}
