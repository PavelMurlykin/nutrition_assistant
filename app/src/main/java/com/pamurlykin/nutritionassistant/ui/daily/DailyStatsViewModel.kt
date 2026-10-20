package com.pamurlykin.nutritionassistant.ui.daily

import androidx.lifecycle.*
import com.pamurlykin.nutritionassistant.data.local.CatalogEntryEntity
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DailyStatsUiState(val selectedDate: LocalDate = LocalDate.now(), val snapshot: DailyStatsSnapshot? = null)

class DailyStatsViewModel(private val repository: NutritionRepository) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val catalog = repository.observeCatalog().catch { error.value = it.message ?: "Не удалось открыть справочник." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = selectedDate.flatMapLatest { date ->
        repository.observeDailyStats(LocalUserDefaults.USER_ID, date.toString())
            .map { DailyStatsUiState(date, it) }
            .onStart { emit(DailyStatsUiState(date)) }
            .catch { error.value = it.message ?: "Не удалось открыть дневник." }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyStatsUiState())

    fun setDate(date: LocalDate) { selectedDate.value = date }
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
    fun saveCatalog(id: Long?, kind: String, name: String, type: String, ingredients: List<String>, onSuccess: (CatalogEntryEntity) -> Unit) {
        var result: CatalogEntryEntity? = null
        perform({ onSuccess(requireNotNull(result)) }) { result = repository.saveCatalogEntry(id, kind, name, type, ingredients) }
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DailyStatsViewModel(repository) as T
        }
    }
}
