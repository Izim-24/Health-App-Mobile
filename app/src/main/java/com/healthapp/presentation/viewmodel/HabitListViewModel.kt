package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.repository.AccountManager
import com.healthapp.domain.model.Habit
import com.healthapp.domain.model.HabitFrequency
import com.healthapp.domain.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class HabitListUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val habits: List<Habit> = emptyList(),
    val date: String = LocalDateTime.now().toLocalDate().toString()
)

@HiltViewModel
class HabitListViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val accountManager: AccountManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitListUiState())
    val uiState: StateFlow<HabitListUiState> = _uiState.asStateFlow()
    private var habitsJob: Job? = null

    init {
        loadHabits()
    }

    fun loadHabits() {
        val date = LocalDateTime.now().toLocalDate().toString()
        _uiState.update { it.copy(isLoading = true, date = date, error = null) }
        habitsJob?.cancel()
        habitsJob = viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                habitRepository.observeHabits(accountId).collectLatest { habits ->
                    val completedIds = habitRepository.getCompletedHabitIdsForDate(accountId, date).getOrDefault(emptySet())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            habits = habits.map { habit -> habit.copy(isCompletedToday = habit.id in completedIds) }
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Reloading cancels the previous collector; that is not an error to show.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createHabit(name: String, frequency: HabitFrequency, reminderTime: String? = null, goalValue: Double? = null, goalUnit: String? = null) {
        viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                val habit = Habit(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    name = name,
                    frequency = frequency,
                    reminderTime = reminderTime,
                    createdAt = LocalDateTime.now(),
                    goalValue = goalValue,
                    goalUnit = goalUnit
                )
                habitRepository.createHabit(habit)
                loadHabits()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateHabit(habit: Habit, name: String, goalValue: Double?, goalUnit: String?) {
        viewModelScope.launch {
            val result = habitRepository.updateHabit(habit.copy(name = name, goalValue = goalValue, goalUnit = goalUnit))
            result.onFailure { _uiState.update { state -> state.copy(error = it.message) } }
            loadHabits()
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            try {
                habitRepository.deleteHabit(habitId)
                loadHabits()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun markHabitComplete(habitId: String, date: String) {
        setHabitCompleted(habitId, date, true)
    }

    fun setHabitCompleted(habitId: String, date: String, completed: Boolean) {
        viewModelScope.launch {
            try {
                val result = habitRepository.setHabitCompleted(habitId, date, completed)
                result.getOrThrow()
                loadHabits()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
