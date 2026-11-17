package com.pamurlykin.nutritionassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Save
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SCHEDULE("schedule", "Расписание", Icons.Rounded.Alarm),
    CALENDAR("daily_stats", "Календарь", Icons.Rounded.CalendarMonth),
    CATALOG("catalog", "Меню", Icons.Rounded.RestaurantMenu),
    DATA("data", "Данные", Icons.Rounded.Save),
}
