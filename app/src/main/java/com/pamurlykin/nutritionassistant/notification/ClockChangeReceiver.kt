package com.pamurlykin.nutritionassistant.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pamurlykin.nutritionassistant.NutritionAssistantApplication
import kotlinx.coroutines.*

class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as NutritionAssistantApplication).container
                container.notificationScheduler.reschedule(container.repository.getUser())
            } finally { result.finish() }
        }
    }
}
