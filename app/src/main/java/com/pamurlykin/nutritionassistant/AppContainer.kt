package com.pamurlykin.nutritionassistant

import android.content.Context
import com.pamurlykin.nutritionassistant.data.local.NutritionDatabase
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.notification.NotificationScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NutritionDatabase by lazy {
        NutritionDatabase.create(appContext)
    }

    val repository: NutritionRepository by lazy {
        NutritionRepository(database)
    }

    val notificationScheduler: NotificationScheduler by lazy {
        NotificationScheduler(appContext)
    }
}
