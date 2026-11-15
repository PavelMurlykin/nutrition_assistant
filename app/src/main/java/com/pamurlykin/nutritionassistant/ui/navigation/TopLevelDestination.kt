package com.pamurlykin.nutritionassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SCHEDULE("schedule", "Расписание", Icons.Rounded.Alarm),
    CALENDAR("daily_stats", "Календарь", Icons.Rounded.CalendarMonth),
    CATALOG("catalog", "Меню", Icons.Rounded.Menu),
}
