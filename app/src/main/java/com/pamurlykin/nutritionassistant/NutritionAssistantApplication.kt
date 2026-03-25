package com.pamurlykin.nutritionassistant

import android.app.Application

class NutritionAssistantApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}
