package com.pamurlykin.nutritionassistant.notification

import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot

enum class ReminderType(
    val storageValue: String,
    val title: String,
    val message: String,
    val scheduleSlot: ScheduleSlot?,
) {
    BREAKFAST(
        storageValue = "breakfast",
        title = "Время завтрака",
        message = "Добавь запись о завтраке в дневник питания.",
        scheduleSlot = ScheduleSlot.BREAKFAST,
    ),
    LUNCH(
        storageValue = "lunch",
        title = "Время обеда",
        message = "Зафиксируй обед и отметь самочувствие после него.",
        scheduleSlot = ScheduleSlot.LUNCH,
    ),
    DINNER(
        storageValue = "dinner",
        title = "Время ужина",
        message = "Не забудь записать ужин и воду.",
        scheduleSlot = ScheduleSlot.DINNER,
    ),
    TOILET(
        storageValue = "toilet",
        title = "Запись про туалет",
        message = "Отметь поход и качество стула по шкале Бристоля.",
        scheduleSlot = ScheduleSlot.TOILET,
    ),
    SLEEP_QUALITY(
        storageValue = "sleep_quality",
        title = "Качество сна",
        message = "Опиши, как ты спал и как чувствуешь себя после сна.",
        scheduleSlot = null,
    ),
}
