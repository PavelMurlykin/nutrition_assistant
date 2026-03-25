package com.pamurlykin.nutritionassistant.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pamurlykin.nutritionassistant.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer(context.applicationContext)
                container.notificationScheduler.createNotificationChannel()
                container.repository.getAllUsers().forEach { user ->
                    container.notificationScheduler.scheduleAll(user)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
