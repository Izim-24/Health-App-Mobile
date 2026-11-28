package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.SleepRecord
import com.healthapp.domain.model.ActivityRecord
import com.healthapp.domain.model.ActivityType
import com.healthapp.domain.model.ActivityIntensity
import com.healthapp.domain.model.VitalMeasurement
import com.healthapp.domain.model.VitalType
import com.healthapp.domain.model.WeightRecord
import com.healthapp.domain.repository.SleepRepository
import com.healthapp.domain.repository.ActivityRepository
import com.healthapp.domain.repository.VitalRepository
import com.healthapp.domain.repository.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class HealthTrackingUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val vitals: List<VitalMeasurement> = emptyList(),
    val weights: List<WeightRecord> = emptyList(),
    val sleeps: List<SleepRecord> = emptyList(),
    val activities: List<ActivityRecord> = emptyList()
)

@HiltViewModel
class HealthTrackingViewModel @Inject constructor(
    private val accountManager: AccountSession,
    private val vitalRepository: VitalRepository,
    private val weightRepository: WeightRepository,
    private val sleepRepository: SleepRepository,
    private val activityRepository: ActivityRepository,
    private val healthAlerts: com.healthapp.domain.repository.HealthAlertRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthTrackingUiState())
    val uiState: StateFlow<HealthTrackingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = accountManager.getCurrentAccountId()
                launch {
                    vitalRepository.observeAll(id).collect { values ->
                        _uiState.update { it.copy(vitals = values, isLoading = false) }
                    }
                }
                launch {
                    weightRepository.observeWeightRecords(id).collect { values -> _uiState.update { it.copy(weights = values) } }
                }
                launch {
                    sleepRepository.observeSleepRecords(id).collect { values -> _uiState.update { it.copy(sleeps = values) } }
                }
                launch {
                    activityRepository.observeActivities(id).collect { values -> _uiState.update { it.copy(activities = values) } }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message?.let(UiText::Raw)) }
            }
        }
    }

    /** [at] = when it was measured (the entry dialog lets the user pick an earlier time today or on a past day). */
    fun addVital(type: VitalType, first: Double, second: Double? = null, unit: String, at: LocalDateTime = LocalDateTime.now()) {
        viewModelScope.launch {
            val id = accountManager.getCurrentAccountId()
            val result = vitalRepository.record(VitalMeasurement(UUID.randomUUID().toString(), id, type, first, second, unit, "MANUAL", at))
            result.onFailure { e -> _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) } }
            // A recent typed reading is checked at once; the result shows in the "recent alert" card (no notification:
            // the user is here). Readings logged afterwards for an earlier time describe the past, so they are not flagged.
            val recent = !at.isBefore(LocalDateTime.now().minusHours(com.healthapp.domain.usecase.VitalAlerts.RECENT_HOURS))
            if (result.isSuccess && recent && (type == VitalType.HEART_RATE || type == VitalType.BLOOD_PRESSURE)) runCatching {
                healthAlerts.checkManual(
                    hr = if (type == VitalType.HEART_RATE) com.healthapp.domain.usecase.HrSample(at, first.toInt()) else null,
                    bp = if (type == VitalType.BLOOD_PRESSURE && second != null) com.healthapp.domain.usecase.BpReading(at, first.toInt(), second.toInt()) else null
                )
            }
        }
    }

    fun addWeight(weightKg: Double, at: LocalDateTime = LocalDateTime.now()) {
        viewModelScope.launch {
            val id = accountManager.getCurrentAccountId()
            weightRepository.recordWeight(id, WeightRecord(UUID.randomUUID().toString(), weightKg, at, at.toLocalDate().toString()))
                .onFailure { e -> _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) } }
        }
    }

    fun addSleep(bedtimeText: String, wakeupText: String, quality: Int?) {
        viewModelScope.launch {
            try {
                val sleepDate = LocalDate.now()
                val bedtime = LocalTime.parse(bedtimeText)
                val wakeup = LocalTime.parse(wakeupText)
                val bedtimeDate = if (bedtime >= wakeup) sleepDate.minusDays(1) else sleepDate
                val bedtimeAt = LocalDateTime.of(bedtimeDate, bedtime)
                val wakeupAt = LocalDateTime.of(sleepDate, wakeup)
                val duration = java.time.Duration.between(bedtimeAt, wakeupAt).toMinutes().toInt()
                require(duration in 1..(24 * 60))
                val id = accountManager.getCurrentAccountId()
                val record = SleepRecord(UUID.randomUUID().toString(), bedtimeAt, wakeupAt, duration, quality, "Nhập thủ công", sleepDate.toString())
                sleepRepository.recordSleep(id, record).onFailure { e -> _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) } }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = UiText.Res(R.string.err_sleep_times)) }
            }
        }
    }

    fun addSteps(steps: Int, durationMinutes: Int) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val id = accountManager.getCurrentAccountId()
            val activity = ActivityRecord(UUID.randomUUID().toString(), ActivityType.WALKING, durationMinutes, steps, 0.0, 0, ActivityIntensity.MODERATE, now, now.toLocalDate().toString())
            activityRepository.recordActivity(id, activity).onFailure { e -> _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) } }
        }
    }

    fun deleteVital(id: String) { viewModelScope.launch { vitalRepository.delete(id) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
