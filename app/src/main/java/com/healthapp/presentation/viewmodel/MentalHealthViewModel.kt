package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.model.MoodEntry
import com.healthapp.domain.repository.MoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class MentalHealthUiState(
    val loading: Boolean = true,
    val entries: List<MoodEntry> = emptyList(),
    val savedToday: Boolean = false,
    val error: UiText? = null
) {
    val today: MoodEntry? get() = entries.firstOrNull { it.date == LocalDate.now() }
}

@HiltViewModel
class MentalHealthViewModel @Inject constructor(
    private val repository: MoodRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(MentalHealthUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeEntries()
                .catch { e -> mutable.update { it.copy(loading = false, error = e.message?.let(UiText::Raw)) } }
                .collect { list -> mutable.update { it.copy(loading = false, entries = list) } }
        }
    }

    fun save(mood: String, stress: Int, note: String) {
        viewModelScope.launch {
            repository.save(MoodEntry(LocalDate.now(), mood, stress, note.trim(), LocalDateTime.now()))
                .onSuccess { mutable.update { it.copy(savedToday = true, error = null) } }
                .onFailure { e -> mutable.update { it.copy(error = e.message?.let(UiText::Raw)) } }
        }
    }

    /** Editing the form after saving clears the "saved" confirmation. */
    fun markDirty() = mutable.update { it.copy(savedToday = false) }
}
