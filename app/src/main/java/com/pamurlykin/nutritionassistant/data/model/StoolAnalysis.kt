package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.MealWithItems
import com.pamurlykin.nutritionassistant.data.local.StoolEntity
import java.time.LocalDate

enum class StoolCondition(val title: String) {
    CONSTIPATION("Признаки запора"), DIARRHEA("Жидкий стул / понос"), NORMAL("Оформленный стул"), SOFT("Мягкий стул"), UNKNOWN("Не оценен");
    companion object {
        fun fromQuality(value: Int): StoolCondition = when (value) {
            1, 2 -> CONSTIPATION; 6, 7 -> DIARRHEA; 3, 4 -> NORMAL; 5 -> SOFT; else -> UNKNOWN
        }
        fun label(value: Int): String = when (value) {
            1 -> "1 · Отдельные твердые комочки (запор)"
            2 -> "2 · Комковатый плотный стул (запор)"
            3 -> "3 · Оформленный, с трещинами"
            4 -> "4 · Гладкий, мягкий, оформленный"
            5 -> "5 · Мягкие отдельные кусочки"
            6 -> "6 · Кашицеобразный (понос)"
            7 -> "7 · Водянистый (понос)"
            else -> "Не оценен (старая запись)"
        }
    }
}

data class FoodAssociation(
    val factor: String,
    val affectedWith: Int,
    val daysWith: Int,
    val affectedWithout: Int,
    val daysWithout: Int,
) {
    val repeated: Boolean get() = affectedWith >= 2 && daysWithout >= 2 &&
        affectedWith.toDouble() / daysWith > affectedWithout.toDouble() / daysWithout
    val difference: Double get() = affectedWith.toDouble() / daysWith -
        (if (daysWithout == 0) 0.0 else affectedWithout.toDouble() / daysWithout)
}

data class ConditionAnalysis(val condition: StoolCondition, val factors: List<FoodAssociation>)
data class StoolAnalysis(
    val previousDate: String,
    val previousMeals: List<MealWithItems>,
    val conditions: List<ConditionAnalysis>,
    val stoolCount: Int,
    val normalCount: Int,
    val constipationCount: Int,
    val diarrheaCount: Int,
    val softCount: Int,
    val unknownCount: Int,
)

/** Calendar-day associations. Missing stool or food days are never treated as healthy controls. */
object StoolAnalyzer {
    fun analyze(date: String, meals: List<MealWithItems>, stools: List<StoolEntity>): StoolAnalysis {
        val selected = LocalDate.parse(date)
        val previous = selected.minusDays(1).toString()
        val mealsByDate = meals.filter { it.event.date < date }.groupBy { it.event.date }
        val history = stools.filter { it.date <= date }.groupBy { it.date }
        val factorsByDay = mealsByDate.mapValues { (_, events) -> factors(events) }
        val observed = history.mapNotNull { (day, entries) ->
            val foodDay = LocalDate.parse(day).minusDays(1).toString()
            val exposure = factorsByDay[foodDay].orEmpty()
            val outcomes = entries.map { StoolCondition.fromQuality(it.quality) }.filter { it != StoolCondition.UNKNOWN }.toSet()
            if (exposure.isEmpty() || outcomes.isEmpty()) null else Triple(day, exposure, outcomes)
        }
        val today = history[date].orEmpty()
        val conditions = today.map { StoolCondition.fromQuality(it.quality) }.distinct()
            .filter { it == StoolCondition.CONSTIPATION || it == StoolCondition.DIARRHEA }
            .map { condition ->
                ConditionAnalysis(condition, factorsByDay[previous].orEmpty().map { factor ->
                    val with = observed.filter { factor in it.second }
                    val without = observed.filter { factor !in it.second }
                    FoodAssociation(factor, with.count { condition in it.third }, with.size,
                        without.count { condition in it.third }, without.size)
                }.sortedWith(compareByDescending<FoodAssociation> { it.repeated }.thenByDescending { it.difference }.thenBy { it.factor }))
            }
        return StoolAnalysis(previous, mealsByDate[previous].orEmpty(), conditions, today.size,
            today.count { StoolCondition.fromQuality(it.quality) == StoolCondition.NORMAL },
            today.count { StoolCondition.fromQuality(it.quality) == StoolCondition.CONSTIPATION },
            today.count { StoolCondition.fromQuality(it.quality) == StoolCondition.DIARRHEA },
            today.count { StoolCondition.fromQuality(it.quality) == StoolCondition.SOFT },
            today.count { StoolCondition.fromQuality(it.quality) == StoolCondition.UNKNOWN })
    }

    private fun factors(meals: List<MealWithItems>): Set<String> = buildSet {
        meals.flatMap { it.items }.forEach { item ->
            add("Блюдо: ${normalizeName(item.name)} (${FoodType.titleFor(item.foodType)})")
            if (item.foodType in FoodType.entries.map { it.storageValue }) add("Тип еды: ${FoodType.titleFor(item.foodType)}")
            item.ingredients.forEach { add("Ингредиент: ${normalizeName(it)}") }
        }
    }
}
