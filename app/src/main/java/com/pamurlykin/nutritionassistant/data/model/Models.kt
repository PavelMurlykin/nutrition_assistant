package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.*
import java.util.Locale

object LocalUserDefaults { const val USER_ID: Long = 1L }

enum class MealType(val storageValue: String, val title: String, val isPrimary: Boolean) {
    BREAKFAST("breakfast", "Завтрак", true), LUNCH("lunch", "Обед", true),
    DINNER("dinner", "Ужин", true), SNACK("snack", "Перекус", false),
}

enum class FoodType(val storageValue: String, val title: String) {
    HOME("home", "Домашняя"), RESTAURANT("restaurant", "Ресторанная"), STORE("store", "Магазинная");
    companion object {
        fun titleFor(value: String): String = entries.find { it.storageValue == value }?.title ?: "Тип не указан (старая запись)"
    }
}

enum class ScheduleSlot(val title: String) {
    BREAKFAST("Завтрак"), LUNCH("Обед"), DINNER("Ужин"), TOILET("Туалет");
    fun enabled(user: UserEntity): Boolean = when (this) {
        BREAKFAST -> user.breakfastEnabled; LUNCH -> user.lunchEnabled
        DINNER -> user.dinnerEnabled; TOILET -> user.toiletEnabled
    }
    fun time(user: UserEntity): String = when (this) {
        BREAKFAST -> user.breakfastTime; LUNCH -> user.lunchTime
        DINNER -> user.dinnerTime; TOILET -> user.toiletTime
    }
}

object CatalogKind { const val DISH = "dish"; const val MEDICINE = "medicine" }
fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).replace('ё', 'е')
fun catalogMatches(entry: CatalogEntryEntity, query: String): Boolean =
    normalizeName(entry.name).contains(normalizeName(query))

data class MedicineSelection(val catalogId: Long, val dosage: String = "")

data class DailyStatsSnapshot(
    val date: String,
    val user: UserEntity,
    val meals: List<MealWithItems>,
    val medicines: List<MedicineWithItems>,
    val stools: List<StoolEntity>,
    val feelings: List<FeelingEntity>,
    val waterCount: Int,
    val sleep: SleepEntity?,
    val analysis: StoolAnalysis,
)
