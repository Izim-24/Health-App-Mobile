package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.model.ReminderType
import com.healthapp.domain.repository.ReminderRepository
import com.healthapp.domain.usecase.ReminderSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class RemindersUiState(
    val loading: Boolean = true,
    val reminders: List<Reminder> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val accountManager: AccountSession
) : ViewModel() {

    private val mutable = MutableStateFlow(RemindersUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                repository.observeReminders(accountManager.getCurrentAccountId()).collect { list ->
                    mutable.update { it.copy(loading = false, reminders = list) }
                }
            }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun save(id: String?, title: String, type: ReminderType, time: String, days: List<DayOfWeek>) {
        if (ReminderSchedule.parseTime(time) == null) {
            mutable.update { it.copy(error = "Giờ nhắc không hợp lệ (HH:mm).") }
            return
        }
        val existing = state.value.reminders.firstOrNull { it.id == id }
        val reminder = Reminder(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title.trim().ifBlank { defaultTitle(type) },
            reminderType = type,
            reminderTime = time,
            isEnabled = existing?.isEnabled ?: true,
            daysOfWeek = days.takeIf { it.isNotEmpty() && it.size < 7 },
            createdAt = existing?.createdAt ?: LocalDateTime.now()
        )
        viewModelScope.launch {
            val result = if (existing == null) repository.createReminder(reminder).map { } else repository.updateReminder(reminder)
            result.onFailure { e -> mutable.update { it.copy(error = e.message ?: "Không thể lưu nhắc nhở") } }
        }
    }

    fun toggle(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isEnabled = enabled))
                .onFailure { e -> mutable.update { it.copy(error = e.message) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id).onFailure { e -> mutable.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() = mutable.update { it.copy(error = null) }

    companion object {
        fun defaultTitle(type: ReminderType) = when (type) {
            ReminderType.WATER -> "Uống nước"
            ReminderType.EXERCISE -> "Vận động"
            ReminderType.SLEEP -> "Đi ngủ"
            ReminderType.HABIT -> "Thói quen"
            ReminderType.MEAL -> "Bữa ăn"
        }
    }
}
