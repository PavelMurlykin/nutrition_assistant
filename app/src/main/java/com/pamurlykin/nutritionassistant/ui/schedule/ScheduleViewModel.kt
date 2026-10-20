package com.pamurlykin.nutritionassistant.ui.schedule

import androidx.lifecycle.*
import com.pamurlykin.nutritionassistant.data.local.UserEntity
import com.pamurlykin.nutritionassistant.data.model.*
import com.pamurlykin.nutritionassistant.data.repository.NutritionRepository
import com.pamurlykin.nutritionassistant.notification.NotificationScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ScheduleViewModel(private val repository: NutritionRepository, private val notificationScheduler: NotificationScheduler) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val user = repository.observeUser().catch { error.value = it.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserEntity(LocalUserDefaults.USER_ID))
    fun updateTime(slot: ScheduleSlot, time: String) = update {
        repository.updateScheduleSlot(LocalUserDefaults.USER_ID, slot, time)
    }
    fun setEnabled(slot: ScheduleSlot, enabled: Boolean) = update { repository.setReminderEnabled(slot, enabled) }
    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action(); notificationScheduler.reschedule(repository.getUser()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error.value = failure.message ?: "Не удалось изменить расписание." }
        }
    }
    companion object {
        fun factory(repository: NutritionRepository, notificationScheduler: NotificationScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduleViewModel(repository, notificationScheduler) as T
            }
    }
}
