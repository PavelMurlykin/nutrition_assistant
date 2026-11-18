package com.pamurlykin.nutritionassistant.ui.daily

import androidx.lifecycle.*
import com.pamurlykin.nutritionassistant.data.local.CatalogItem
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import java.time.LocalDate
import java.time.YearMonth
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DailyStatsUiState(val selectedDate: LocalDate = LocalDate.now(), val snapshot: DailyStatsSnapshot? = null)

class DailyStatsViewModel(
    private val repository: NutritionRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val selectedDate = savedState.getStateFlow("selectedDate", LocalDate.now().toString())
    private val visibleMonth = savedState.getStateFlow("visibleMonth", YearMonth.from(LocalDate.parse(selectedDate.value)).toString())
    private val today = MutableStateFlow(LocalDate.now())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val catalog = repository.observeCatalogGraph().catch { error.value = it.message ?: "Не удалось открыть справочник." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysisDays = savedState.getStateFlow("analysisDays", 90)
    val analysisHistory = repository.observeAnalyticsHistory().map<AnalysisHistory,AnalysisHistory?> { it }
        .catch { error.value = it.message ?: "Не удалось открыть историю анализа." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val explainableReport = combine(analysisHistory, selectedDate, analysisDays) { history, date, days -> Triple(history,date,days) }
        .mapLatest { (history,date,days) -> history?.let { withContext(Dispatchers.Default) { ExplainableAnalyzer.analyze(it,date,days) } } }
        .catch { error.value = it.message ?: "Не удалось рассчитать наблюдения." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setAnalysisDays(days: Int) { require(days in setOf(30,90,365)); savedState["analysisDays"] = days }
    fun saveTrackingTarget(id: Long?, catalogId: Long, scope: String, source: String?, comment: String?, active: Boolean, onSuccess: () -> Unit) = perform(onSuccess) {
        repository.analytics.saveTarget(LocalUserDefaults.USER_ID,id,catalogId,scope,source,comment,active)
    }
    fun activateTarget(id: Long, active: Boolean) = perform { repository.analytics.setActive(LocalUserDefaults.USER_ID,id,active) }

    val seedConflicts = repository.observeSeedConflicts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentIds = repository.observeRecentCatalogIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentMeals = repository.observeRecentMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun resolveSeed(key: String, targetId: Long?) = perform { repository.resolveSeed(key, targetId) }
    fun favorite(id: Long, value: Boolean) = perform { repository.setFavorite(id, value) }
    fun saveDraft(draft: CatalogDraft, onSuccess: (CatalogItem) -> Unit) {
        var result: CatalogItem? = null
        perform({ onSuccess(requireNotNull(result)) }) { result = repository.foodCatalog.save(draft.id, draft.kind, draft.name, draft.foodType, draft.details, draft.components) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = selectedDate.flatMapLatest { value ->
        val date = LocalDate.parse(value)
        repository.observeDailyStats(LocalUserDefaults.USER_ID, date.toString())
            .map { DailyStatsUiState(date, it) }
            .onStart { emit(DailyStatsUiState(date)) }
            .catch { error.value = it.message ?: "Не удалось открыть дневник." }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStatsUiState(LocalDate.parse(selectedDate.value)))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthCounts = visibleMonth.flatMapLatest { value ->
        val month = YearMonth.parse(value)
        val start = calendarStart(month)
        repository.observeCalendarCounts(start.toString(), start.plusDays(41).toString())
            .map { month to (it as List<CalendarDayCounts>?) }
            .onStart { emit(month to null) }
            .catch { error.value = it.message ?: "Не удалось открыть календарь." }
    }
    val calendar = combine(monthCounts, selectedDate, today) { (month, counts), date, currentDay ->
        buildCalendarMonth(month, LocalDate.parse(date), currentDay, counts.orEmpty(), isLoaded = counts != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        buildCalendarMonth(YearMonth.parse(visibleMonth.value), LocalDate.parse(selectedDate.value), today.value, isLoaded = false))

    fun setDate(date: LocalDate) {
        if (date.year !in 1..9999) return
        savedState["visibleMonth"] = YearMonth.from(date).toString()
        savedState["selectedDate"] = date.toString()
    }
    fun previousMonth() = moveMonth(-1)
    fun nextMonth() = moveMonth(1)
    private fun moveMonth(delta: Long) {
        // The sports calendar selects the first day when switching months.
        val month = YearMonth.parse(visibleMonth.value).plusMonths(delta)
        if (month.year in 1..9999) setDate(month.atDay(1))
    }
    fun goToToday() { refreshToday(); setDate(today.value) }
    fun refreshToday() { today.value = LocalDate.now() }
    private fun perform(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action(); onSuccess() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error.value = failure.message ?: "Не удалось сохранить запись." }
            finally { busy.value = false }
        }
    }
    fun saveCatalog(id: Long?, kind: String, name: String, type: String, productIds: List<Long>, onSuccess: (CatalogItem) -> Unit) {
        var result: CatalogItem? = null
        perform({ onSuccess(requireNotNull(result)) }) { result = repository.saveCatalogEntry(id, kind, name, type, productIds) }
    }
    fun observeProductLinks(catalogId: Long) = repository.productLinks.observe(catalogId)
    fun saveProductLink(catalogId: Long, id: Long?, draft: ProductLinkDraft, onSuccess: () -> Unit) =
        perform(onSuccess) { repository.productLinks.save(catalogId, id, draft) }
    fun deleteProductLink(catalogId: Long, id: Long) = perform { repository.productLinks.delete(catalogId, id) }

    fun restoreCatalog(id: Long) = perform { repository.restoreCatalogEntry(id) }
    fun archiveCatalog(id: Long) = perform { repository.archiveCatalogEntry(id) }
    fun saveMeal(type: MealType, id: Long?, ids: List<Long>, onSuccess: () -> Unit,
        context: EventContext? = null, portions: Map<Long, Portion> = emptyMap(), comments: Map<Long, String?> = emptyMap(),
        refresh: Set<Long> = emptySet(), sourceId: Long? = null, expectedHashes: Map<Long, String> = emptyMap()) {
        val date = selectedDate.value
        perform(onSuccess) { repository.saveMeal(date, type, id, ids, context, portions, comments, refresh, sourceId, expectedHashes) }
    }
    fun saveMedicines(id: Long?, selections: List<MedicineSelection>, onSuccess: () -> Unit, context: EventContext? = null) {
        val date = selectedDate.value
        perform(onSuccess) { repository.saveMedicines(date, id, selections, context) }
    }
    fun deleteMeal(id: Long) = perform { repository.deleteMeal(LocalUserDefaults.USER_ID, id) }
    fun deleteMedicine(id: Long) = perform { repository.deleteMedicine(LocalUserDefaults.USER_ID, id) }
    fun saveDayHealth(values: Map<String, HealthDraft?>, sleepQuality: Int?, sleepComment: String?, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) { repository.health.saveDay(LocalUserDefaults.USER_ID, date, values, sleepQuality, sleepComment) }
    }
    fun saveHealth(id: Long?, draft: HealthDraft, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) { repository.health.save(LocalUserDefaults.USER_ID, date, id, draft) }
    }
    fun confirmDay(food: Boolean, symptoms: Boolean, summary: String?, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) { repository.confirmDay(LocalUserDefaults.USER_ID, date, food, symptoms, summary) }
    }
    fun saveSleepDraft(draft: SleepDraft, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) {
            if (draft.quality == null && draft.comment.isNullOrBlank() && draft.awakenings == null && draft.bedTime == null) repository.health.deleteSleep(LocalUserDefaults.USER_ID, date)
            else repository.health.saveSleep(LocalUserDefaults.USER_ID, date, draft)
        }
    }
    fun saveStool(id: Long?, quality: Int, onSuccess: () -> Unit, context: EventContext? = null, details: StoolDetails? = null) {
        val date = selectedDate.value
        perform(onSuccess) {
            if (id == null) repository.addStool(LocalUserDefaults.USER_ID, date, quality, context ?: EventContext(), details ?: StoolDetails())
            else repository.updateStool(LocalUserDefaults.USER_ID, id, quality, context, details)
        }
    }
    fun deleteStool(id: Long) = perform { repository.deleteStool(LocalUserDefaults.USER_ID, id) }
    fun saveFeeling(id: Long?, text: String, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) {
            if (id == null) repository.addFeeling(LocalUserDefaults.USER_ID, date, text)
            else repository.updateFeeling(LocalUserDefaults.USER_ID, id, text)
        }
    }
    fun deleteFeeling(id: Long) = perform { repository.deleteFeeling(LocalUserDefaults.USER_ID, id) }
    fun setWater(count: Int, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) { repository.setWaterForDay(LocalUserDefaults.USER_ID, date, count) }
    }
    fun incrementWater(delta: Int) {
        val date = selectedDate.value
        perform { repository.incrementWater(LocalUserDefaults.USER_ID, date, delta) }
    }
    fun saveSleep(wakeup: String, bed: String, quality: String?, onSuccess: () -> Unit) {
        val date = selectedDate.value
        perform(onSuccess) { repository.upsertSleep(LocalUserDefaults.USER_ID, date, wakeup, bed, quality) }
    }
    fun deleteSleep() {
        val date = selectedDate.value
        perform { repository.deleteSleep(LocalUserDefaults.USER_ID, date) }
    }
    companion object {
        fun factory(repository: NutritionRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                DailyStatsViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }
}
