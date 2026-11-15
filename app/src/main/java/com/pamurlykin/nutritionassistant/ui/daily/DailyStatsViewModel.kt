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
    val catalog = repository.observeCatalog().catch { error.value = it.message ?: "Не удалось открыть справочник." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            .map { month to it }
            .onStart { emit(month to emptyList()) }
            .catch { error.value = it.message ?: "Не удалось открыть календарь." }
    }
    val calendar = combine(monthCounts, selectedDate, today) { (month, counts), date, currentDay ->
        buildCalendarMonth(month, LocalDate.parse(date), currentDay, counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        buildCalendarMonth(YearMonth.parse(visibleMonth.value), LocalDate.parse(selectedDate.value), today.value))

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
    fun archiveCatalog(id: Long) = perform { repository.archiveCatalogEntry(id) }
    fun saveMeal(type: MealType, id: Long?, ids: List<Long>, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) { repository.saveMeal(date, type, id, ids) }
    }
    fun saveMedicines(id: Long?, selections: List<MedicineSelection>, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) { repository.saveMedicines(date, id, selections) }
    }
    fun deleteMeal(id: Long) = perform { repository.deleteMeal(LocalUserDefaults.USER_ID, id) }
    fun deleteMedicine(id: Long) = perform { repository.deleteMedicine(LocalUserDefaults.USER_ID, id) }
    fun saveStool(id: Long?, quality: Int, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) {
            if (id == null) repository.addStool(LocalUserDefaults.USER_ID, date, quality)
            else repository.updateStool(LocalUserDefaults.USER_ID, id, quality)
        }
    }
    fun deleteStool(id: Long) = perform { repository.deleteStool(LocalUserDefaults.USER_ID, id) }
    fun saveFeeling(id: Long?, text: String, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) {
            if (id == null) repository.addFeeling(LocalUserDefaults.USER_ID, date, text)
            else repository.updateFeeling(LocalUserDefaults.USER_ID, id, text)
        }
    }
    fun deleteFeeling(id: Long) = perform { repository.deleteFeeling(LocalUserDefaults.USER_ID, id) }
    fun setWater(count: Int, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) { repository.setWaterForDay(LocalUserDefaults.USER_ID, date, count) }
    }
    fun incrementWater(delta: Int) {
        val date = selectedDate.value.toString()
        perform { repository.incrementWater(LocalUserDefaults.USER_ID, date, delta) }
    }
    fun saveSleep(wakeup: String, bed: String, quality: String?, onSuccess: () -> Unit) {
        val date = selectedDate.value.toString()
        perform(onSuccess) { repository.upsertSleep(LocalUserDefaults.USER_ID, date, wakeup, bed, quality) }
    }
    fun deleteSleep() {
        val date = selectedDate.value.toString()
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
