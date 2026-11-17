package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.StorageFormats

class HealthRepository(private val database: NutritionDatabase) {
    private val observations = database.feelingDao()
    private val sleeps = database.sleepDao()
    private suspend fun invalidate(userId: Long, date: String) = database.dayStatusDao().invalidateSymptoms(userId, date, StorageFormats.nowTimestamp())
    suspend fun save(userId: Long, date: String, id: Long?, draft: HealthDraft): Long = database.withTransaction {
        validateDay(date)
        val existing = id?.let { requireNotNull(observations.getById(it)) }
            ?: if (draft.period == "day") observations.daily(userId, date, draft.metric) else null
        require(existing == null || existing.userId == userId && existing.date == date && existing.metric == draft.metric)
        val clean = draft.validated(date, allowLegacy = existing?.valueCode == null && existing != null, preserveLongComment = existing != null && existing.comment == draft.context.comment)
        if (clean.period == "day") require(observations.daily(userId, date, clean.metric)?.let { it.id == existing?.id } != false) { "Дневная оценка уже есть. Измените существующую запись." }
        val value = (existing ?: HealthEntryEntity(userId = userId, date = date)).copy(metric = clean.metric,
            valueCode = clean.value, valueLabel = clean.value?.let { HealthScales.label(clean.metric, it) },
            scaleVersion = if (clean.value == null) null else HealthScales.VERSION, period = clean.period,
            daySlot = if (clean.period == "day") 1 else null, durationMinutes = clean.durationMinutes, activityKind = clean.activityKind,
            illnessImpact = clean.illnessImpact, symptomCodes = clean.symptomCodes, comment = clean.context.comment,
            actualTime = clean.context.recordedTime.time, utcOffset = clean.context.recordedTime.utcOffset, timeZone = clean.context.recordedTime.timeZone,
            timePrecision = clean.context.recordedTime.precision, updatedAt = StorageFormats.nowTimestamp())
        val result = if (existing == null) observations.insert(value) else existing.id.also { observations.update(value) }
        invalidate(userId, date); result
    }
    suspend fun saveDay(userId: Long, date: String, values: Map<String, HealthDraft?>, sleepQuality: Int?, sleepComment: String?) = database.withTransaction {
        validateDay(date)
        require(values.keys == HealthScales.metrics.keys)
        values.forEach { (metric, draft) ->
            if (draft == null) observations.clearDaily(userId, date, metric)
            else { require(draft.metric == metric && draft.period == "day" && draft.value != null); save(userId, date, null, draft) }
        }
        val previous = sleeps.getForDay(userId, date)
        if (previous != null || sleepQuality != null || !sleepComment.isNullOrBlank()) {
            val draft = (previous?.toDraft() ?: SleepDraft()).copy(quality = sleepQuality, comment = sleepComment)
            if (draft.quality == null && draft.comment.isNullOrBlank() && draft.awakenings == null && draft.bedTime == null) sleeps.deleteForDay(userId, date)
            else saveSleep(userId, date, draft)
        }
        invalidate(userId, date)
    }
    suspend fun saveSleep(userId: Long, date: String, draft: SleepDraft) = database.withTransaction {
        val old = sleeps.getForDay(userId, date)
        val clean = draft.validated(date, allowLegacyComment = old != null && old.qualityDescription == draft.comment)
        sleeps.upsert((old ?: SleepEntity(userId = userId, date = date)).copy(qualityRating = clean.quality,
            qualityLabel = clean.quality?.let { HealthScales.label("sleep", it.toString()) }, scaleVersion = if (clean.quality == null) null else 1,
            qualityDescription = clean.comment, awakenings = clean.awakenings, bedTime = clean.bedTime, wakeupTime = clean.wakeupTime,
            bedDate = clean.bedDate, wakeupDate = clean.wakeupDate, updatedAt = StorageFormats.nowTimestamp()))
        invalidate(userId, date)
    }
    suspend fun deleteSleep(userId: Long, date: String) = database.withTransaction { sleeps.deleteForDay(userId, date); invalidate(userId, date) }
}
