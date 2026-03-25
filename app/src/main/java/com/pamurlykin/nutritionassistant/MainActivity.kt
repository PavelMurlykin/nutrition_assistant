package com.pamurlykin.nutritionassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pamurlykin.nutritionassistant.ui.NutritionAssistantApp
import com.pamurlykin.nutritionassistant.ui.theme.NutritionAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as NutritionAssistantApplication).container

        setContent {
            NutritionAssistantTheme {
                NutritionAssistantApp(container = container)
            }
        }
    }
}
