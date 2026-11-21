package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.WaterIntake
import com.healthapp.domain.repository.WaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WaterTrackingUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val waterLogs: List<WaterIntake> = emptyList(),
    val totalWaterToday: Int = 0,
    val waterGoal: Int = 0,
    val progressPercentage: Float = 0f,
    val date: String = LocalDate.now().toString()
)

@HiltViewModel
class WaterTrackingViewModel @Inject constructor(
    private val waterRepository: WaterRepository,
    private val accountManager: AccountSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaterTrackingUiState())
    val uiState: StateFlow<WaterTrackingUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        loadWaterData()
        viewModelScope.launch {
            while (true) {
                val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1000L))
                loadWaterData()
            }
        }
    }

    fun loadWaterData() {
        val today = LocalDate.now().toString()
        _uiState.update { it.copy(isLoading = true) }
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                val goal = waterRepository.getDailyGoal(accountId)
                _uiState.update { it.copy(waterGoal = goal) }
                waterRepository.observeWaterLogsForDate(accountId, today).collect { logs ->
                    val total = logs.sumOf { it.amountMl }
                    val currentGoal = _uiState.value.waterGoal
                    val progress = if (currentGoal > 0) (total.toFloat() / currentGoal).coerceIn(0f, 1f) else 0f

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            waterLogs = logs,
                            totalWaterToday = total,
                            progressPercentage = progress,
                            date = today
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message?.let(UiText::Raw)) }
            }
        }
    }

    fun addWaterIntake(amountMl: Int) {
        if (amountMl !in 1..5000) {
            _uiState.update { it.copy(error = UiText.Res(R.string.err_water_amount)) }
            return
        }
        viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                waterRepository.addWaterIntake(accountId, amountMl).getOrThrow()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) }
            }
        }
    }

    fun addQuickWater(option: Int) {
        when (option) {
            1 -> addWaterIntake(100)  // 100ml
            2 -> addWaterIntake(250)  // 250ml
            3 -> addWaterIntake(500)  // 500ml
        }
    }

    fun deleteWaterLog(logId: String) {
        viewModelScope.launch {
            try {
                waterRepository.deleteWaterLog(logId).getOrThrow()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message?.let(UiText::Raw)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setGoal(ml: Int) {
        if (ml !in 100..10000) {
            _uiState.update { it.copy(error = UiText.Res(R.string.err_water_goal)) }
            return
        }
        viewModelScope.launch {
            val accountId = accountManager.getCurrentAccountId()
            waterRepository.setDailyGoal(accountId, ml)
            _uiState.update { it.copy(waterGoal = ml, progressPercentage = (it.totalWaterToday.toFloat() / ml).coerceIn(0f, 1f)) }
        }
    }
}
