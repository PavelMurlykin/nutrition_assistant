package com.pamurlykin.nutritionassistant.data.model

import com.pamurlykin.nutritionassistant.data.local.*
import java.time.Duration
import java.time.LocalDateTime

data class ScaleChoice(val code: String, val label: String)
/** Immutable self-report definitions, version 1. These short daily scales are not diagnostic questionnaires. */
object HealthScales {
    const val VERSION = 1
    val metrics = linkedMapOf("stress" to "Стресс", "activity" to "Физическая активность", "wellbeing" to "Самочувствие", "illness" to "Признаки болезни")
    val five = listOf("Очень плохое", "Плохое", "Среднее", "Хорошее", "Очень хорошее").mapIndexed { i, title -> ScaleChoice((i + 1).toString(), title) }
    val stress = listOf("Отсутствовал", "Слабый", "Умеренный", "Сильный", "Очень сильный").mapIndexed { i, title -> ScaleChoice(i.toString(), title) }
    val activity = listOf(ScaleChoice("none", "Не было"), ScaleChoice("light", "Лёгкая"), ScaleChoice("moderate", "Умеренная"), ScaleChoice("vigorous", "Высокая"))
    val illness = listOf(ScaleChoice("none", "Нет признаков"), ScaleChoice("present", "Есть признаки"), ScaleChoice("unknown", "Не знаю"))
    val impact = listOf(ScaleChoice("mild", "Лёгкое"), ScaleChoice("moderate", "Умеренное"), ScaleChoice("severe", "Тяжёлое"))
    val symptoms = listOf(ScaleChoice("digestive", "Пищеварительные"), ScaleChoice("respiratory", "Респираторные"), ScaleChoice("fever", "Повышение температуры"), ScaleChoice("other", "Другие"))
    val kinds = listOf(ScaleChoice("walking", "Ходьба"), ScaleChoice("running", "Бег"), ScaleChoice("cycling", "Велосипед"), ScaleChoice("strength", "Силовая нагрузка"), ScaleChoice("housework", "Домашние дела"), ScaleChoice("other", "Другая"))
    val urgency = listOf("Нет", "Умеренная", "Сильная").mapIndexed { i, label -> ScaleChoice(i.toString(), label) }
    val pain = listOf("Нет", "Слабая", "Умеренная", "Сильная").mapIndexed { i, label -> ScaleChoice(i.toString(), label) }
    fun choices(metric: String): List<ScaleChoice> = when (metric) { "stress" -> stress; "activity" -> activity; "wellbeing", "sleep" -> five; "illness" -> illness; "urgency" -> urgency; "pain" -> pain; else -> error("Неизвестная шкала.") }
    fun label(metric: String, value: String): String = requireNotNull(choices(metric).find { it.code == value }) { "Недопустимое значение шкалы." }.label
    fun dictionary(): Map<String, Any?> = (metrics.keys + "sleep" + listOf("urgency", "pain", "illness_impact", "symptom_codes", "activity_kind", "bristol")).associateWith { name ->
        val values = when (name) { "urgency" -> urgency; "pain" -> pain; "illness_impact" -> impact; "symptom_codes" -> symptoms; "activity_kind" -> kinds;
            "bristol" -> (1..7).map { ScaleChoice(it.toString(), StoolCondition.label(it)) }; else -> choices(name) }
        mapOf("version" to VERSION.toLong(), "values" to values.associate { it.code to it.label })
    }
}

data class HealthDraft(val metric: String, val value: String?, val period: String = "day", val durationMinutes: Int? = null,
    val activityKind: String? = null, val illnessImpact: String? = null, val symptomCodes: List<String> = emptyList(), val context: EventContext = EventContext()) {
    fun validated(date: String, allowLegacy: Boolean = false, preserveLongComment: Boolean = false): HealthDraft {
        require(metric in HealthScales.metrics && period in setOf("day", "moment"))
        val clean = if (preserveLongComment) context.copy(recordedTime = context.recordedTime.validated(date)) else context.validated(date)
        require(period != "day" || clean.recordedTime.time == null) { "Оценка за день не имеет точного часа." }
        if (value == null) require(allowLegacy && metric == "wellbeing" && period == "moment" && !clean.comment.isNullOrBlank()) { "Выберите значение шкалы." }
        else HealthScales.label(metric, value)
        require(durationMinutes == null || durationMinutes in 1..1440) { "Продолжительность — от 1 до 1440 минут." }
        require(activityKind == null || HealthScales.kinds.any { it.code == activityKind })
        require(metric == "activity" && value != "none" || durationMinutes == null && activityKind == null) { "Для отсутствующей нагрузки не задаются минуты и вид." }
        require(illnessImpact == null || HealthScales.impact.any { it.code == illnessImpact })
        require(symptomCodes.distinct().size == symptomCodes.size && symptomCodes.all { code -> HealthScales.symptoms.any { it.code == code } })
        require(metric == "illness" && value == "present" || illnessImpact == null && symptomCodes.isEmpty()) { "Влияние и симптомы задаются только при наличии признаков болезни." }
        return copy(context = clean, symptomCodes = symptomCodes.sorted())
    }
}

data class StoolDetails(val urgency: Int? = null, val pain: Int? = null) {
    fun validated(): StoolDetails { require(urgency == null || urgency in 0..2); require(pain == null || pain in 0..3); return this }
}

data class SleepDraft(val quality: Int? = null, val comment: String? = null, val awakenings: Int? = null,
    val bedTime: String? = null, val wakeupTime: String? = null, val bedDate: String? = null, val wakeupDate: String? = null) {
    fun validated(date: String, allowLegacyComment: Boolean = false): SleepDraft {
        validateDay(date)
        require(quality == null || quality in 1..5); require(awakenings == null || awakenings in 0..1000)
        require((bedTime == null) == (wakeupTime == null)) { "Укажите обе границы интервала или оставьте время неизвестным." }
        require((bedDate == null) == (wakeupDate == null))
        if (bedTime == null) require(bedDate == null)
        else {
            require(bedTime.matches(Regex("[0-9]{2}:[0-9]{2}")) && requireNotNull(wakeupTime).matches(Regex("[0-9]{2}:[0-9]{2}")))
            java.time.LocalTime.parse(bedTime); java.time.LocalTime.parse(wakeupTime)
            if (bedDate != null) { SleepPeriod(bedDate, requireNotNull(wakeupDate)).validate(bedTime, requireNotNull(wakeupTime)); require(wakeupDate == date) }
        }
        val clean = if (allowLegacyComment) comment else cleanComment(comment)
        require(quality != null || clean != null || awakenings != null || bedTime != null) { "Оцените сон или добавьте сведения о ночи." }
        return copy(comment = clean)
    }
}
fun SleepEntity.toDraft() = SleepDraft(qualityRating, qualityDescription, awakenings, bedTime, wakeupTime, bedDate, wakeupDate)
fun HealthEntryEntity.toDraft() = HealthDraft(metric, valueCode, period, durationMinutes, activityKind, illnessImpact, symptomCodes,
    EventContext(RecordedTime(actualTime, utcOffset, timeZone), comment))
fun SleepEntity.bedIntervalMinutes(): Long? = if (bedDate != null && wakeupDate != null && bedTime != null && wakeupTime != null)
    Duration.between(LocalDateTime.parse(bedDate + "T" + bedTime), LocalDateTime.parse(wakeupDate + "T" + wakeupTime)).toMinutes() else null

data class ActivitySummary(val maximum: String?, val knownMinutes: Long, val unknownDurationCount: Int)
fun activitySummary(events: List<HealthEntryEntity>): ActivitySummary {
    val rows = events.filter { it.metric == "activity" && it.period == "moment" }
    val rank = HealthScales.activity.map { it.code }
    return ActivitySummary(rows.mapNotNull { it.valueCode }.maxByOrNull { rank.indexOf(it) },
        rows.sumOf { it.durationMinutes?.toLong() ?: 0L }, rows.count { it.valueCode != "none" && it.durationMinutes == null })
}
