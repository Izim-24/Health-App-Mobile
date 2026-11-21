package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.repository.ReminderRepository
import com.healthapp.domain.model.NutritionSummary
import com.healthapp.domain.model.DailySummary
import com.healthapp.domain.model.Habit
import com.healthapp.domain.repository.NutritionRepository
import com.healthapp.domain.repository.HabitRepository
import com.healthapp.domain.repository.ActivityRepository
import com.healthapp.domain.repository.WaterRepository
import com.healthapp.domain.repository.SleepRepository
import com.healthapp.domain.repository.WeightRepository
import com.healthapp.domain.repository.VitalRepository
import com.healthapp.domain.model.VitalType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.healthapp.domain.usecase.BpReading
import com.healthapp.domain.usecase.HabitHistory
import com.healthapp.domain.usecase.ScoreData
import com.healthapp.domain.usecase.ScoreHistory
import com.healthapp.domain.usecase.WeekScore
import com.healthapp.domain.usecase.WeightReading
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val nutrition: NutritionSummary? = null,
    val habits: List<Habit> = emptyList(),
    val habitCompletedToday: Int = 0,
    val totalHabitsActive: Int = 0,
    val waterIntakeMl: Int? = null,
    val waterGoal: Int? = null,
    val totalSteps: Int? = null,
    val stepsGoal: Int? = null,
    val totalActivityMinutes: Int = 0,
    val date: String = LocalDate.now().toString(),
    val userName: String? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val bmi: Double? = null,
    val sleepDurationMinutes: Int? = null,
    val healthScore: Int? = null,
    val heartRateBpm: Int? = null,
    val bloodPressureSystolic: Int? = null,
    val bloodPressureDiastolic: Int? = null,
    val glucoseValue: Double? = null,
    val glucoseUnit: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val scoreDetails: com.healthapp.domain.usecase.HealthScoreResult? = null,
    /** Weekly scores, oldest first (last = current 7 days), and what changed since last week. */
    val scoreHistory: List<com.healthapp.domain.usecase.WeekScore> = emptyList(),
    val scoreInsights: List<com.healthapp.domain.usecase.ScoreInsight> = emptyList(),
    val loadedOnce: Boolean = false
)

private const val HISTORY_WEEKS = 8

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val habitRepository: HabitRepository,
    private val activityRepository: ActivityRepository,
    private val waterRepository: WaterRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRepository,
    private val vitalRepository: VitalRepository,
    private val profileRepository: ProfileRepository,
    private val reminderRepository: ReminderRepository,
    private val accountManager: AccountSession,
    healthConnect: com.healthapp.domain.repository.HealthSyncRepository,
    private val medicationRepository: com.healthapp.domain.repository.MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
        // Reload when a Health Connect import finishes (it runs in the background on app start).
        viewModelScope.launch {
            healthConnect.state.map { it.lastSync }.distinctUntilChanged().drop(1).collect { refreshDashboard() }
        }
    }

    fun refreshDashboard() {
        val today = LocalDate.now().toString()
        _uiState.update { it.copy(isLoading = !it.loadedOnce) }

        viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()

                // Load nutrition summary
                val nutritionResult = nutritionRepository.getNutritionSummary(accountId, today)

                // Load activity data
                val durationResult = activityRepository.getTotalDurationForDate(accountId, today)
                val activities = activityRepository.observeActivitiesByDate(accountId, today).first()
                val steps = activities.takeIf { it.isNotEmpty() }?.sumOf { it.steps }

                // Load water intake
                val waterLogs = waterRepository.observeWaterLogsForDate(accountId, today).first()
                val waterTotal = waterLogs.takeIf { it.isNotEmpty() }?.sumOf { it.amountMl }

                // Load habit completion
                val completedHabitsResult = habitRepository.getCompletedHabitsForDate(accountId, today)
                val totalHabitsResult = habitRepository.getTotalActiveHabits(accountId)
                val activeHabits = habitRepository.observeHabits(accountId).first().filter { it.isEnabled }
                val completedHabitIds = habitRepository.getCompletedHabitIdsForDate(accountId, today).getOrDefault(emptySet())
                val todayReminders = reminderRepository.getEnabledReminders(accountId).getOrDefault(emptyList()).filter { reminder ->
                    reminder.daysOfWeek.isNullOrEmpty() || reminder.daysOfWeek.any { it.name == LocalDate.now().dayOfWeek.name }
                }
                val profile = profileRepository.getProfile(accountId)
                // Latest logged weight (people weigh every few days, not daily), else the profile value.
                val weight = weightRepository.observeWeightRecords(accountId).first().maxByOrNull { it.recordedAt }?.weight
                    ?: profile?.weightKg?.takeIf { it > 0 }
                val sleep = sleepRepository.getSleepForDate(accountId, today).getOrNull()?.durationMinutes
                val latestVitals = vitalRepository.observeAll(accountId).first().groupBy { it.type }
                val latestHeartRate = latestVitals[VitalType.HEART_RATE]?.maxByOrNull { it.recordedAt }
                val latestBloodPressure = latestVitals[VitalType.BLOOD_PRESSURE]?.maxByOrNull { it.recordedAt }
                val latestGlucose = latestVitals[VitalType.GLUCOSE]?.maxByOrNull { it.recordedAt }
                val height = profile?.heightCm?.takeIf { it > 0 }
                val bmi = if (weight != null && height != null) weight / ((height / 100.0) * (height / 100.0)) else null

                val history = runCatching { computeHistory(accountId, profile?.weightKg, height) }.getOrNull().orEmpty()
                val scoreDetails = history.lastOrNull()?.result
                val insights = history.lastOrNull()?.let { ScoreHistory.insights(it, history.getOrNull(history.size - 2)) }.orEmpty()

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadedOnce = true,
                        scoreDetails = scoreDetails,
                        scoreHistory = history,
                        scoreInsights = insights,
                        nutrition = nutritionResult.getOrNull(),
                        totalSteps = steps,
                        totalActivityMinutes = durationResult.getOrDefault(0),
                        waterIntakeMl = waterTotal,
                        habitCompletedToday = completedHabitsResult.getOrDefault(0),
                        totalHabitsActive = totalHabitsResult.getOrDefault(0),
                        habits = activeHabits.map { it.copy(isCompletedToday = it.id in completedHabitIds) },
                        reminders = todayReminders,
                        date = today,
                        userName = profile?.fullName?.takeIf { it.isNotBlank() },
                        heightCm = height,
                        weightKg = weight,
                        bmi = bmi,
                        sleepDurationMinutes = sleep,
                        // Transparent guideline-based score; null until at least 3 areas have data.
                        healthScore = scoreDetails?.score,
                        heartRateBpm = latestHeartRate?.value?.toInt(),
                        bloodPressureSystolic = latestBloodPressure?.value?.toInt(),
                        bloodPressureDiastolic = latestBloodPressure?.secondaryValue?.toInt(),
                        glucoseValue = latestGlucose?.value,
                        glucoseUnit = latestGlucose?.unit
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Loads the raw records once and scores the current 7 days plus the 7 weeks before, all with the
     * same rules (see [ScoreHistory] / HealthScore), so the history is comparable with today's score.
     */
    private suspend fun computeHistory(accountId: String, profileWeightKg: Double?, heightCm: Int?): List<WeekScore> {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        fun date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

        val sleep = sleepRepository.observeSleepRecords(accountId).first()
            .mapNotNull { r -> date(r.date)?.let { it to r.durationMinutes } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val steps = activityRepository.observeActivities(accountId).first()
            .mapNotNull { r -> date(r.date)?.let { it to r.steps } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val bp = vitalRepository.observeAll(accountId).first()
            .filter { it.type == VitalType.BLOOD_PRESSURE && it.secondaryValue != null }
            .map { BpReading(it.recordedAt, it.value.toInt(), it.secondaryValue!!.toInt()) }
        val weights = weightRepository.observeWeightRecords(accountId).first().map { WeightReading(it.recordedAt, it.weight) }
        val habits = habitRepository.observeHabits(accountId).first().filter { it.isEnabled }.map { habit ->
            HabitHistory(
                createdOn = habit.createdAt.toLocalDate(),
                completedOn = habitRepository.getCompletionDates(habit.id).getOrDefault(emptySet()).mapNotNull { date(it) }.toSet()
            )
        }
        val meds = medicationRepository.observeMedications().first()
        val logs = if (meds.isEmpty()) emptyList()
        else medicationRepository.observeLogs(today.minusWeeks(HISTORY_WEEKS.toLong()), today).first()

        val data = ScoreData(sleep, steps, bp, weights, profileWeightKg?.takeIf { it > 0 }, heightCm, habits, meds, logs)
        return ScoreHistory.weekly(data, now, HISTORY_WEEKS)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setHabitCompleted(habitId: String, completed: Boolean) {
        viewModelScope.launch {
            val date = LocalDate.now().toString()
            habitRepository.setHabitCompleted(habitId, date, completed).onSuccess {
                _uiState.update { state ->
                    val habits = state.habits.map { habit -> if (habit.id == habitId) habit.copy(isCompletedToday = completed) else habit }
                    state.copy(habits = habits, habitCompletedToday = habits.count { it.isCompletedToday })
                }
            }.onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }
}
