package com.pamurlykin.nutritionassistant.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pamurlykin.nutritionassistant.data.local.UserEntity
import com.pamurlykin.nutritionassistant.data.model.LocalUserDefaults
import com.pamurlykin.nutritionassistant.data.model.ScheduleSlot
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.notification.NotificationScheduler
import com.pamurlykin.nutritionassistant.util.StorageFormats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val user: UserEntity = UserEntity(userId = LocalUserDefaults.USER_ID),
    val sleepQualityTime: String = StorageFormats.plusMinutes("07:00", 30),
)

class ScheduleViewModel(
    private val repository: NutritionRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = repository.observeUser()
        .map { user ->
            ScheduleUiState(
                user = user,
                sleepQualityTime = StorageFormats.plusMinutes(user.wakeupTime, 30),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleUiState(),
        )

    init {
        viewModelScope.launch {
            repository.ensureUser(LocalUserDefaults.USER_ID)
            notificationScheduler.scheduleAll(repository.getUser(LocalUserDefaults.USER_ID))
        }
    }

    fun updateTime(slot: ScheduleSlot, time: String) {
        viewModelScope.launch {
            repository.updateScheduleSlot(LocalUserDefaults.USER_ID, slot, time)
            notificationScheduler.reschedule(repository.getUser(LocalUserDefaults.USER_ID))
        }
    }

    companion object {
        fun factory(
            repository: NutritionRepository,
            notificationScheduler: NotificationScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScheduleViewModel(repository, notificationScheduler) as T
        }
    }
}
