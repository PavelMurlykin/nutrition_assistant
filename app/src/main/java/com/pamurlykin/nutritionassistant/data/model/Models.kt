package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.FeelingEntity
import com.pamurlykin.nutritionassistant.data.local.MealEntity
import com.pamurlykin.nutritionassistant.data.local.MedicineEntity
import com.pamurlykin.nutritionassistant.data.local.SleepEntity
import com.pamurlykin.nutritionassistant.data.local.StoolEntity
import com.pamurlykin.nutritionassistant.data.local.UserEntity

object LocalUserDefaults {
    const val USER_ID: Long = 1L
}

enum class MealType(
    val storageValue: String,
    val title: String,
    val isPrimary: Boolean,
) {
    BREAKFAST("breakfast", "Завтрак", true),
    LUNCH("lunch", "Обед", true),
    DINNER("dinner", "Ужин", true),
    SNACK("snack", "Перекус", false),
}

enum class ScheduleSlot(
    val title: String,
) {
    BREAKFAST("Завтрак"),
    LUNCH("Обед"),
    DINNER("Ужин"),
    TOILET("Туалет"),
    WAKEUP("Подъем"),
    BED("Отбой"),
}

data class DailyStatsSnapshot(
    val date: String,
    val user: UserEntity,
    val breakfast: MealEntity?,
    val lunch: MealEntity?,
    val dinner: MealEntity?,
    val snacks: List<MealEntity>,
    val medicines: List<MedicineEntity>,
    val stools: List<StoolEntity>,
    val feelings: List<FeelingEntity>,
    val waterCount: Int,
    val sleep: SleepEntity,
)
