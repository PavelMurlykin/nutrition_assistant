package com.pamurlykin.nutritionassistant.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.nutritionassistant.data.model.DailyStatsSnapshot
import com.pamurlykin.nutritionassistant.data.model.LocalUserDefaults
import com.pamurlykin.nutritionassistant.data.model.MealType
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.util.StorageFormats
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DailyStatsUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val snapshot: DailyStatsSnapshot? = null,
)

class DailyStatsViewModel(
    private val repository: NutritionRepository,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DailyStatsUiState> = selectedDate
        .flatMapLatest { date ->
            val storageDate = StorageFormats.formatDateForStorage(date)
            repository.observeDailyStats(LocalUserDefaults.USER_ID, storageDate)
                .map { snapshot ->
                    DailyStatsUiState(
                        selectedDate = date,
                        snapshot = snapshot,
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyStatsUiState(),
        )

    init {
        viewModelScope.launch {
            repository.ensureUser(LocalUserDefaults.USER_ID)
            repository.ensureSleepForDay(
                userId = LocalUserDefaults.USER_ID,
                date = currentDate(),
            )
        }
    }

    fun setDate(date: LocalDate) {
        selectedDate.value = date
        viewModelScope.launch {
            repository.ensureSleepForDay(
                userId = LocalUserDefaults.USER_ID,
                date = StorageFormats.formatDateForStorage(date),
            )
        }
    }

    fun upsertMeal(mealType: MealType, description: String) {
        viewModelScope.launch {
            repository.upsertMeal(LocalUserDefaults.USER_ID, currentDate(), mealType, description)
        }
    }

    fun updateMeal(mealId: Long, description: String) {
        viewModelScope.launch {
            repository.updateMeal(LocalUserDefaults.USER_ID, mealId, description)
        }
    }

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch {
            repository.deleteMeal(LocalUserDefaults.USER_ID, mealId)
        }
    }

    fun addMedicine(name: String, dosage: String?) {
        viewModelScope.launch {
            repository.addMedicine(LocalUserDefaults.USER_ID, currentDate(), name, dosage)
        }
    }

    fun updateMedicine(medicineId: Long, name: String, dosage: String?) {
        viewModelScope.launch {
            repository.updateMedicine(LocalUserDefaults.USER_ID, medicineId, name, dosage)
        }
    }

    fun deleteMedicine(medicineId: Long) {
        viewModelScope.launch {
            repository.deleteMedicine(LocalUserDefaults.USER_ID, medicineId)
        }
    }

    fun addStool(quality: Int) {
        viewModelScope.launch {
            repository.addStool(LocalUserDefaults.USER_ID, currentDate(), quality)
        }
    }

    fun updateStool(stoolId: Long, quality: Int) {
        viewModelScope.launch {
            repository.updateStool(LocalUserDefaults.USER_ID, stoolId, quality)
        }
    }

    fun deleteStool(stoolId: Long) {
        viewModelScope.launch {
            repository.deleteStool(LocalUserDefaults.USER_ID, stoolId)
        }
    }

    fun addFeeling(description: String) {
        viewModelScope.launch {
            repository.addFeeling(LocalUserDefaults.USER_ID, currentDate(), description)
        }
    }

    fun updateFeeling(feelingId: Long, description: String) {
        viewModelScope.launch {
            repository.updateFeeling(LocalUserDefaults.USER_ID, feelingId, description)
        }
    }

    fun deleteFeeling(feelingId: Long) {
        viewModelScope.launch {
            repository.deleteFeeling(LocalUserDefaults.USER_ID, feelingId)
        }
    }

    fun setWater(value: Int) {
        viewModelScope.launch {
            repository.setWaterForDay(LocalUserDefaults.USER_ID, currentDate(), value)
        }
    }

    fun incrementWater(delta: Int) {
        viewModelScope.launch {
            repository.incrementWater(LocalUserDefaults.USER_ID, currentDate(), delta)
        }
    }

    fun upsertSleep(wakeupTime: String, bedTime: String, qualityDescription: String?) {
        viewModelScope.launch {
            repository.upsertSleep(
                userId = LocalUserDefaults.USER_ID,
                date = currentDate(),
                wakeupTime = wakeupTime,
                bedTime = bedTime,
                qualityDescription = qualityDescription,
            )
        }
    }

    private fun currentDate(): String = StorageFormats.formatDateForStorage(selectedDate.value)

    companion object {
        fun factory(repository: NutritionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DailyStatsViewModel(repository) as T
            }
    }
}
