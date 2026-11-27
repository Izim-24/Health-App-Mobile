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

/**
 * Which alerts show in full (explanation, F.A.S.T, Call 115): the newest, plus every urgent one of the last 3 hours,
 * so tapping an urgent notification always reaches its call button even if a milder alert arrived later.
 */
fun alertsToExpand(alerts: List<VitalAlert>, now: java.time.LocalDateTime): Set<Int> =
    alerts.indices.filter { i ->
        i == 0 || (alerts[i].level == com.healthapp.domain.usecase.AlertLevel.URGENT && !alerts[i].at.isBefore(now.minusHours(3)))
    }.toSet()

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
