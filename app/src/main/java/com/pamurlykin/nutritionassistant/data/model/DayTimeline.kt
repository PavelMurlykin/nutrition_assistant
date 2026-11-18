package com.pamurlykin.nutritionassistant.data.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Creation timestamps intentionally do not participate in clinical event ordering. */
data class TimelineEntry(val key: String, val title: String, val details: List<String>, val comment: String?,
    val actualTime: String? = null, val utcOffset: String? = null, val timeZone: String? = null, val wholePeriod: Boolean = false) {
    fun instant(date: String): Instant? = if (actualTime != null && utcOffset != null)
        LocalDateTime.parse(date + "T" + actualTime).toInstant(ZoneOffset.of(utcOffset)) else null
}
data class DayTimeline(val timed: List<TimelineEntry>, val unknownTime: List<TimelineEntry>, val wholePeriod: List<TimelineEntry>)
fun buildDayTimeline(day: DailyStatsSnapshot): DayTimeline {
    val entries = buildList {
        day.meals.forEach { meal ->
            val event = meal.event
            add(TimelineEntry("meal-${event.id}", MealType.entries.single { it.storageValue == event.mealType }.title,
                meal.items.map { item -> item.historyDisplayName() + (item.quantity?.let { " · " + quantityTitle(it, item.unit) } ?: "") + (item.comment?.let { " · $it" } ?: "") },
                event.comment, event.actualTime, event.utcOffset, event.timeZone))
        }
        day.medicines.forEach { intake ->
            val event = intake.event
            add(TimelineEntry("medicine-${event.id}", "Приём лекарств", intake.items.map { item ->
                item.name + (item.dosage?.let { " · $it" } ?: "") + (item.quantity?.let { " · " + quantityTitle(it, item.unit) } ?: "") + (item.comment?.let { " · $it" } ?: "") },
                event.comment, event.actualTime, event.utcOffset, event.timeZone))
        }
        day.stools.forEach { stool -> add(TimelineEntry("stool-${stool.id}", stool.qualityLabel ?: StoolCondition.label(stool.quality),
            listOfNotNull(stool.urgency?.let { "Срочность: " + HealthScales.label("urgency", it.toString()) }, stool.pain?.let { "Боль: " + HealthScales.label("pain", it.toString()) }),
            stool.comment, stool.actualTime, stool.utcOffset, stool.timeZone)) }
        day.healthEntries.forEach { health -> add(TimelineEntry("health-${health.id}", HealthScales.metrics.getValue(health.metric) + ": " + (health.valueLabel ?: "Без оценки (старая запись)"),
            listOfNotNull(health.durationMinutes?.let { "$it мин" }, health.activityKind?.let { code -> HealthScales.kinds.single { it.code == code }.label },
                health.illnessImpact?.let { code -> "Влияние: " + HealthScales.impact.single { it.code == code }.label },
                health.symptomCodes.takeIf { it.isNotEmpty() }?.joinToString(", ") { code -> HealthScales.symptoms.single { it.code == code }.label }),
            health.comment, health.actualTime, health.utcOffset, health.timeZone, health.period == "day")) }
        day.sleep?.let { sleep -> add(TimelineEntry("sleep", "Сон: " + (sleep.qualityLabel ?: "Не оценено"),
            listOfNotNull(sleep.bedTime?.let { "Отбой: " + (sleep.bedDate ?: "дата неизвестна") + " " + it + "; подъём: " + (sleep.wakeupDate ?: "дата неизвестна") + " " + sleep.wakeupTime },
                sleep.awakenings?.let { "Пробуждения: $it" }), sleep.qualityDescription, wholePeriod = true)) }
    }
    return DayTimeline(entries.filter { !it.wholePeriod && it.instant(day.date) != null }.sortedWith(compareBy<TimelineEntry> { it.instant(day.date) }.thenBy { it.key }),
        entries.filter { !it.wholePeriod && it.instant(day.date) == null }.sortedBy { it.key }, entries.filter { it.wholePeriod }.sortedBy { it.key })
}
