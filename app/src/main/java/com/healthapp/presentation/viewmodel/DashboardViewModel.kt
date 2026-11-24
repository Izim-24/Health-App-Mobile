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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
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
    val loadedOnce: Boolean = false,
    /** True while the score/history (step 2 of loading) is being calculated. */
    val scoreLoading: Boolean = false,
    /** Earliest dose today not yet marked; null when nothing is pending. */
    val nextDose: com.healthapp.domain.model.ScheduledDose? = null,
    val dosesToday: Int = 0,
    val dosesDoneToday: Int = 0
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

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Loading is driven by the screen (on each resume), so it happens exactly once per visit.
        // Reload when a Health Connect import finishes (it runs in the background on app start).
        viewModelScope.launch {
            healthConnect.state.map { it.lastSync }.distinctUntilChanged().drop(1).collect { refreshDashboard() }
        }
    }

    private var loadJob: Job? = null

    /**
     * Loads in two steps so the screen appears quickly: today's cards first (independent queries run in
     * parallel), then the health score and its 8-week history. A new refresh cancels one still running.
     */
    fun refreshDashboard() {
        val today = LocalDate.now().toString()
        _uiState.update { it.copy(isLoading = !it.loadedOnce, scoreLoading = true) }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val accountId = accountManager.getCurrentAccountId()
                val t = coroutineScope {
                    val nutrition = async { nutritionRepository.getNutritionSummary(accountId, today).getOrNull() }
                    val duration = async { activityRepository.getTotalDurationForDate(accountId, today).getOrDefault(0) }
                    val activities = async { activityRepository.observeActivitiesByDate(accountId, today).first() }
                    val water = async { waterRepository.observeWaterLogsForDate(accountId, today).first() }
                    val completedCount = async { habitRepository.getCompletedHabitsForDate(accountId, today).getOrDefault(0) }
                    val activeCount = async { habitRepository.getTotalActiveHabits(accountId).getOrDefault(0) }
                    val habits = async { habitRepository.observeHabits(accountId).first().filter { it.isEnabled } }
                    val doneIds = async { habitRepository.getCompletedHabitIdsForDate(accountId, today).getOrDefault(emptySet()) }
                    val reminders = async { reminderRepository.getEnabledReminders(accountId).getOrDefault(emptyList()) }
                    val profile = async { profileRepository.getProfile(accountId) }
                    val weights = async { weightRepository.observeWeightRecords(accountId).first() }
                    val sleep = async { sleepRepository.getSleepForDate(accountId, today).getOrNull()?.durationMinutes }
                    val vitals = async { vitalRepository.observeAll(accountId).first().groupBy { it.type } }
                    val waterGoal = async { runCatching { waterRepository.getDailyGoal(accountId) }.getOrNull() }
                    val meds = async { medicationRepository.observeMedications().first() }
                    val medLogs = async { medicationRepository.observeLogs(LocalDate.now(), LocalDate.now()).first() }
                    TodayData(
                        nutrition.await(), duration.await(), activities.await(), water.await(), completedCount.await(),
                        activeCount.await(), habits.await(), doneIds.await(), reminders.await(), profile.await(),
                        weights.await(), sleep.await(), vitals.await(), waterGoal.await(),
                        com.healthapp.domain.usecase.MedicationSchedule.dosesFor(LocalDate.now(), meds.await(), medLogs.await())
                    )
                }

                // Latest logged weight (people weigh every few days, not daily), else the profile value.
                val weight = t.weights.maxByOrNull { it.recordedAt }?.weight ?: t.profile?.weightKg?.takeIf { it > 0 }
                val height = t.profile?.heightCm?.takeIf { it > 0 }
                val bmi = if (weight != null && height != null) weight / ((height / 100.0) * (height / 100.0)) else null
                val latestHeartRate = t.vitals[VitalType.HEART_RATE]?.maxByOrNull { it.recordedAt }
                val latestBloodPressure = t.vitals[VitalType.BLOOD_PRESSURE]?.maxByOrNull { it.recordedAt }
                val latestGlucose = t.vitals[VitalType.GLUCOSE]?.maxByOrNull { it.recordedAt }
                val dayName = LocalDate.now().dayOfWeek.name

                // Step 1: today's cards are on screen now.
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadedOnce = true,
                        nutrition = t.nutrition,
                        totalSteps = t.activities.takeIf { it.isNotEmpty() }?.sumOf { it.steps },
                        totalActivityMinutes = t.duration,
                        waterIntakeMl = t.water.takeIf { it.isNotEmpty() }?.sumOf { it.amountMl },
                        habitCompletedToday = t.completedCount,
                        totalHabitsActive = t.activeCount,
                        habits = t.habits.map { it.copy(isCompletedToday = it.id in t.doneIds) },
                        reminders = t.reminders.filter { r -> r.daysOfWeek.isNullOrEmpty() || r.daysOfWeek.any { it.name == dayName } },
                        date = today,
                        userName = t.profile?.fullName?.takeIf { it.isNotBlank() },
                        heightCm = height,
                        weightKg = weight,
                        bmi = bmi,
                        sleepDurationMinutes = t.sleep,
                        heartRateBpm = latestHeartRate?.value?.toInt(),
                        bloodPressureSystolic = latestBloodPressure?.value?.toInt(),
                        bloodPressureDiastolic = latestBloodPressure?.secondaryValue?.toInt(),
                        glucoseValue = latestGlucose?.value,
                        glucoseUnit = latestGlucose?.unit,
                        waterGoal = t.waterGoal?.takeIf { it > 0 },
                        nextDose = com.healthapp.domain.usecase.MedicationSchedule.upNext(t.doses),
                        dosesToday = t.doses.size,
                        dosesDoneToday = t.doses.count { it.status != null }
                    )
                }

                // Step 2: the score and its history (needs every record of the last 8 weeks).
                val history = runCatching { computeHistory(accountId, t.profile?.weightKg, height) }.getOrNull().orEmpty()
                val scoreDetails = history.lastOrNull()?.result
                val insights = history.lastOrNull()?.let { ScoreHistory.insights(it, history.getOrNull(history.size - 2)) }.orEmpty()
                _uiState.update {
                    it.copy(
                        scoreLoading = false,
                        scoreDetails = scoreDetails,
                        scoreHistory = history,
                        scoreInsights = insights,
                        // Transparent guideline-based score; null until at least 3 areas have data.
                        healthScore = scoreDetails?.score
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, scoreLoading = false, error = e.message) }
            }
        }
    }

    private class TodayData(
        val nutrition: NutritionSummary?, val duration: Int, val activities: List<com.healthapp.domain.model.ActivityRecord>,
        val water: List<com.healthapp.domain.model.WaterIntake>, val completedCount: Int, val activeCount: Int,
        val habits: List<Habit>, val doneIds: Set<String>, val reminders: List<Reminder>,
        val profile: com.healthapp.domain.model.HealthProfile?, val weights: List<com.healthapp.domain.model.WeightRecord>,
        val sleep: Int?, val vitals: Map<VitalType, List<com.healthapp.domain.model.VitalMeasurement>>,
        val waterGoal: Int?, val doses: List<com.healthapp.domain.model.ScheduledDose>
    )

    /** Marks a dose from the home screen, then reloads (score, adherence and supply all change). */
    fun markDose(dose: com.healthapp.domain.model.ScheduledDose, status: com.healthapp.domain.model.DoseStatus) {
        viewModelScope.launch {
            medicationRepository.logDose(dose.medication.id, dose.date, dose.time, status)
            refreshDashboard()
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
