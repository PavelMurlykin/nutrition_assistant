package com.pamurlykin.nutritionassistant.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pamurlykin.nutritionassistant.data.local.UserEntity
import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания Nutrition Assistant",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Напоминания о еде, воде, сне и дневной статистике."
        }
        manager.createNotificationChannel(channel)
    }

    private fun schedule(user: UserEntity, reminderType: ReminderType) {
        val reminderTime = when (reminderType.scheduleSlot) {
            ScheduleSlot.BREAKFAST -> user.breakfastTime
            ScheduleSlot.LUNCH -> user.lunchTime
            ScheduleSlot.DINNER -> user.dinnerTime
            ScheduleSlot.TOILET -> user.toiletTime
            ScheduleSlot.WAKEUP,
            ScheduleSlot.BED,
            null -> StorageFormats.plusMinutes(user.wakeupTime, 30)
        }

        val firstTriggerAt = nextTriggerMillis(reminderTime)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTriggerAt,
            AlarmManager.INTERVAL_DAY,
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
