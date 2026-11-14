package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.repository.AccountManager
import com.healthapp.data.local.dao.UserProfileDao
import com.healthapp.data.local.dao.ReminderDao
import com.healthapp.data.local.entity.ReminderEntity
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
    val reminders: List<ReminderEntity> = emptyList(),
    val scoreDetails: com.healthapp.domain.usecase.HealthScoreResult? = null,
    val loadedOnce: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val habitRepository: HabitRepository,
    private val activityRepository: ActivityRepository,
    private val waterRepository: WaterRepository,
    private val sleepRepository: SleepRepository,
    private val weightRepository: WeightRepository,
    private val vitalRepository: VitalRepository,
    private val userProfileDao: UserProfileDao,
    private val reminderDao: ReminderDao,
    private val accountManager: AccountManager,
    healthConnect: com.healthapp.data.healthconnect.HealthConnectManager,
    private val medicationRepository: com.healthapp.data.repository.MedicationRepository
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
                val todayReminders = reminderDao.getEnabledReminders(accountId).filter { reminder ->
                    reminder.daysOfWeek.isNullOrBlank() || reminder.daysOfWeek.contains(LocalDate.now().dayOfWeek.name)
                }
                val profile = userProfileDao.getProfileByAccountId(accountId)
                val weight = weightRepository.getWeightForDate(accountId, today).getOrNull()?.weight ?: profile?.weight?.takeIf { it > 0 }
                val sleep = sleepRepository.getSleepForDate(accountId, today).getOrNull()?.durationMinutes
                val latestVitals = vitalRepository.observeAll(accountId).first().groupBy { it.type }
                val latestHeartRate = latestVitals[VitalType.HEART_RATE]?.maxByOrNull { it.recordedAt }
                val latestBloodPressure = latestVitals[VitalType.BLOOD_PRESSURE]?.maxByOrNull { it.recordedAt }
                val latestGlucose = latestVitals[VitalType.GLUCOSE]?.maxByOrNull { it.recordedAt }
                val height = profile?.height?.takeIf { it > 0 }
                val bmi = if (weight != null && height != null) weight / ((height / 100.0) * (height / 100.0)) else null

                val scoreDetails = runCatching { computeScore(accountId, bmi, latestBloodPressure) }.getOrNull()

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadedOnce = true,
                        scoreDetails = scoreDetails,
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

    /** Summarises the last 7 days into [HealthScoreInput]. See HealthScore for the method. */
    private suspend fun computeScore(
        accountId: String,
        bmi: Double?,
        latestBp: com.healthapp.domain.model.VitalMeasurement?
    ): com.healthapp.domain.usecase.HealthScoreResult {
        val today = LocalDate.now()
        val weekStart = today.minusDays(6)

        // Sleep: nights whose wake-up date is in the last 7 days.
        val nights = sleepRepository.observeSleepRecords(accountId).first()
            .filter { r -> runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { !it.isBefore(weekStart) && !it.isAfter(today) } == true }
            .groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.durationMinutes } }

        // Steps: full days (yesterday back 7 days); today only if it's the only data, since it's partial.
        val stepsByDay = activityRepository.observeActivities(accountId).first()
            .groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.steps } }.filterValues { it > 0 }
        val fullDays = (1L..7L).mapNotNull { stepsByDay[today.minusDays(it).toString()] }
        val stepDays = fullDays.ifEmpty { listOfNotNull(stepsByDay[today.toString()]) }

        // Blood pressure: only a reading from the last 30 days counts.
        val bp = latestBp?.takeIf { it.recordedAt.toLocalDate().isAfter(today.minusDays(30)) }

        // Habits: count only days each habit existed; today counts only once completed.
        var habitDone = 0
        var habitPossible = 0
        val completedToday = habitRepository.getCompletedHabitIdsForDate(accountId, today.toString()).getOrDefault(emptySet())
        habitRepository.observeHabits(accountId).first().filter { it.isEnabled }.forEach { habit ->
            val age = java.time.temporal.ChronoUnit.DAYS.between(habit.createdAt.toLocalDate(), today).toInt() + 1
            val window = age.coerceIn(1, 7)
            val done = ((habitRepository.getHabitCompletionRate(habit.id, 7).getOrDefault(0.0)) * 7).toInt().coerceAtMost(window)
            val doneToday = habit.id in completedToday
            habitDone += done
            habitPossible += if (doneToday) window else window - 1
        }

        // Medication: 7-day adherence, excluding doses not yet due.
        val meds = medicationRepository.observeMedications().first()
        val adherence = if (meds.isEmpty()) null else com.healthapp.domain.usecase.MedicationSchedule.adherence(
            meds, medicationRepository.observeLogs(weekStart, today).first(), weekStart, today, java.time.LocalDateTime.now()
        )

        return com.healthapp.domain.usecase.HealthScore.calculate(
            com.healthapp.domain.usecase.HealthScoreInput(
                avgSleepMinutes = nights.values.takeIf { it.isNotEmpty() }?.average(), sleepNights = nights.size,
                avgDailySteps = stepDays.takeIf { it.isNotEmpty() }?.average(), stepDays = stepDays.size,
                bmi = bmi,
                systolic = bp?.value?.toInt(), diastolic = bp?.secondaryValue?.toInt(),
                habitCompletedDays = habitDone, habitPossibleDays = habitPossible,
                medsTaken = adherence?.taken ?: 0, medsDue = adherence?.scheduled ?: 0
            )
        )
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
