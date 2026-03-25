package com.pamurlykin.nutritionassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SCHEDULE("schedule", "Расписание", Icons.Rounded.Alarm),
    DAILY_STATS("daily_stats", "Статистика", Icons.Rounded.CalendarMonth),
}
