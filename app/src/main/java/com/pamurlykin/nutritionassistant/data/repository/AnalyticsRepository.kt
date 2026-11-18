package com.pamurlykin.nutritionassistant.data.repository

import androidx.room.withTransaction
import com.pamurlykin.nutritionassistant.data.export.DiaryExportOptions
import com.pamurlykin.nutritionassistant.data.export.DiarySection
import com.pamurlykin.nutritionassistant.data.local.*
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.util.StorageFormats
import kotlinx.coroutines.flow.*

class AnalyticsRepository(private val db: NutritionDatabase) {
    fun observe(userId: Long) = db.analyticsDao().observe(userId).filterNotNull().map { it.toHistory() }

    suspend fun readExportData(options: DiaryExportOptions) = db.withTransaction {
        val userId = LocalUserDefaults.USER_ID
        val start = options.start.toString()
        val end = options.end.toString()
        val selected = options.sections
        val dao = db.analyticsDao()
        val history = AnalysisHistory(
            meals = if (DiarySection.FOOD in selected) dao.mealsInRange(userId, start, end) else emptyList(),
            medicines = if (DiarySection.MEDICINE in selected) dao.medicinesInRange(userId, start, end) else emptyList(),
            stools = if (DiarySection.STOOL in selected) dao.stoolsInRange(userId, start, end) else emptyList(),
            health = if (DiarySection.HEALTH in selected) dao.healthInRange(userId, start, end) else emptyList(),
            sleeps = if (DiarySection.SLEEP in selected) dao.sleepsInRange(userId, start, end) else emptyList(),
            statuses = if (DiarySection.COMPLETENESS in selected) dao.statusesInRange(userId, start, end) else emptyList(),
        )
        val water = if (DiarySection.WATER in selected) db.waterDao().getInRange(userId, start, end) else emptyList()
        history to water
    }

    private fun AnalyticsRows.toHistory() = AnalysisHistory(meals, medicines, stools, health, sleeps, statuses, targets)

    suspend fun saveTarget(userId: Long, id: Long?, catalogId: Long, scope: String, source: String?, comment: String?, active: Boolean = true) = db.withTransaction {
        val entry = requireNotNull(db.catalogDao().get(catalogId))
        require(entry.kind in setOf("dish", "product") && scope in setOf("entry", "ingredient"))
        require(scope != "ingredient" || entry.kind == "product")
        val old = id?.let { requireNotNull(db.analyticsDao().get(it)) } ?: db.analyticsDao().find(userId, catalogId, scope)
        require(old == null || old.userId == userId && old.catalogId == catalogId && old.scope == scope)
        val value = (old ?: TrackingTargetEntity(userId = userId, catalogId = catalogId, scope = scope, nameSnapshot = listOf(entry.name,entry.brand,entry.variantLabel).filter { it.isNotBlank() }.joinToString(" · "))).copy(
            sourceNote = if (id==null && source.isNullOrBlank()) old?.sourceNote else cleanComment(source), comment = if (id==null && comment.isNullOrBlank()) old?.comment else cleanComment(comment), active = active, updatedAt = StorageFormats.nowTimestamp())
        if (old == null) db.analyticsDao().insert(value) else old.id.also { db.analyticsDao().update(value) }
    }
    suspend fun setActive(userId: Long, id: Long, active: Boolean) = db.withTransaction {
        val old = requireNotNull(db.analyticsDao().get(id)); require(old.userId == userId)
        db.analyticsDao().update(old.copy(active = active, updatedAt = StorageFormats.nowTimestamp()))
    }
}
