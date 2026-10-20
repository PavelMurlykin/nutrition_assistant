package com.pamurlykin.nutritionassistant.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pamurlykin.nutritionassistant.data.local.UserEntity
import com.pamurlykin.nutritionassistant.util.StorageFormats
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationScheduler(
    private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll(user: UserEntity) {
        createNotificationChannel()
        // Version 1 also scheduled sleep quality with request code 4.
        alarmManager.cancel(PendingIntent.getBroadcast(context, (user.userId * 100 + 4).toInt(),
            Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        ReminderType.entries.forEach { reminderType ->
            schedule(user, reminderType)
        }
    }

    fun cancelAll(userId: Long) {
        ReminderType.entries.forEach { reminderType ->
            alarmManager.cancel(createPendingIntent(userId, reminderType))
        }
    }

    fun reschedule(user: UserEntity) {
        cancelAll(user.userId)
        scheduleAll(user)
    }

    fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания Nutrition Assistant",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Напоминания о завтраке, обеде, ужине и туалете."
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(user: UserEntity, reminderType: ReminderType) {
        if (!reminderType.scheduleSlot.enabled(user)) {
            alarmManager.cancel(createPendingIntent(user.userId, reminderType))
            return
        }
        val reminderTime = reminderType.scheduleSlot.time(user)
        val firstTriggerAt = nextTriggerMillis(reminderTime)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            firstTriggerAt,
            createPendingIntent(user.userId, reminderType),
        )
    }

    private fun nextTriggerMillis(time: String): Long {
        val localTime = StorageFormats.parseTime(time)
        val now = LocalDateTime.now()
        val candidate = now.toLocalDate().atTime(localTime)
        val nextTime = if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
        return nextTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun createPendingIntent(
        userId: Long,
        reminderType: ReminderType,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_REMINDER_TYPE, reminderType.storageValue)
            putExtra(EXTRA_TITLE, reminderType.title)
            putExtra(EXTRA_MESSAGE, reminderType.message)
        }
        return PendingIntent.getBroadcast(
            context,
            (userId * 100 + reminderType.ordinal).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "nutrition_reminders"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
    }
}
