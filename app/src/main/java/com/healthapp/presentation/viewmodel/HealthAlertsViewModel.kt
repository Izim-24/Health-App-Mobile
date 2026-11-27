package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.HealthAlertRepository
import com.healthapp.domain.usecase.VitalAlert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Recent heart-rate / blood-pressure alerts, newest first (also used by the card on the Health tab). */
@HiltViewModel
class HealthAlertsViewModel @Inject constructor(
    private val repo: HealthAlertRepository
) : ViewModel() {
    val alerts: StateFlow<List<VitalAlert>> = repo.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clear() {
        viewModelScope.launch { repo.clearRecent() }
    }
}
