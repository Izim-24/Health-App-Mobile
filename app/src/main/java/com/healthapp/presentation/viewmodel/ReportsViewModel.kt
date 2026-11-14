package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.local.dao.ActivityLogDao
import com.healthapp.data.local.dao.HabitLogDao
import com.healthapp.data.local.dao.MealDao
import com.healthapp.data.local.dao.WaterLogDao
import com.healthapp.data.repository.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ReportPeriod(val label: String) { DAY("Ngày"), WEEK("Tuần"), MONTH("Tháng") }

data class ReportsUiState(
    val loading: Boolean = true,
    val period: ReportPeriod = ReportPeriod.DAY,
    val range: String = "",
    val nutritionDays: Int = 0,
    val avgCalories: Int = 0,
    val avgProtein: Int = 0,
    val avgCarbs: Int = 0,
    val avgFat: Int = 0,
    val activityDays: Int = 0,
    val avgSteps: Int = 0,
    val totalExerciseMinutes: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val waterDays: Int = 0,
    val avgWaterMl: Int = 0,
    val completedHabits: Int = 0,
    val activeHabits: Int = 0,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val mealDao: MealDao,
    private val activityLogDao: ActivityLogDao,
    private val waterLogDao: WaterLogDao,
    private val habitLogDao: HabitLogDao
) : ViewModel() {
    private val mutable = MutableStateFlow(ReportsUiState())
    val state = mutable.asStateFlow()

    init { select(ReportPeriod.DAY) }

    fun select(period: ReportPeriod) {
        mutable.value = mutable.value.copy(loading = true, period = period, error = null)
        viewModelScope.launch {
            runCatching {
                val accountId = accountManager.getCurrentAccountId()
                val end = LocalDate.now()
                val start = when (period) {
                    ReportPeriod.DAY -> end
                    ReportPeriod.WEEK -> end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ReportPeriod.MONTH -> end.withDayOfMonth(1)
                }
                val dates = generateSequence(start) { it.plusDays(1).takeIf { day -> !day.isAfter(end) } }.toList()
                val mealsByDay = dates.map { mealDao.getMealsByDate(accountId, it.toString()) }.filter { it.isNotEmpty() }
                val activitiesByDay = dates.map { activityLogDao.getActivityLogsByDate(accountId, it.toString()) }.filter { it.isNotEmpty() }
                val waterByDay = dates.map { waterLogDao.getWaterLogsByDate(accountId, it.toString()) }.filter { it.isNotEmpty() }
                val activeHabits = habitLogDao.getTotalActiveHabits(accountId)
                val completed = dates.sumOf { habitLogDao.getCompletedHabitsCount(accountId, it.toString()) }
                ReportsUiState(
                    loading = false, period = period, range = if (start == end) end.toString() else "$start – $end",
                    nutritionDays = mealsByDay.size,
                    avgCalories = mealsByDay.flatMap { it }.sumOf { it.totalCalories } / mealsByDay.size.coerceAtLeast(1),
                    avgProtein = (mealsByDay.flatMap { it }.sumOf { it.totalProtein } / mealsByDay.size.coerceAtLeast(1)).toInt(),
                    avgCarbs = (mealsByDay.flatMap { it }.sumOf { it.totalCarbs } / mealsByDay.size.coerceAtLeast(1)).toInt(),
                    avgFat = (mealsByDay.flatMap { it }.sumOf { it.totalFat } / mealsByDay.size.coerceAtLeast(1)).toInt(),
                    activityDays = activitiesByDay.size,
                    avgSteps = activitiesByDay.flatMap { it }.sumOf { it.steps } / activitiesByDay.size.coerceAtLeast(1),
                    totalExerciseMinutes = activitiesByDay.flatMap { it }.sumOf { it.durationMinutes },
                    totalCaloriesBurned = activitiesByDay.flatMap { it }.sumOf { it.caloriesBurned },
                    waterDays = waterByDay.size,
                    avgWaterMl = waterByDay.flatMap { it }.sumOf { it.amountMl } / waterByDay.size.coerceAtLeast(1),
                    completedHabits = completed, activeHabits = activeHabits
                )
            }.onSuccess { if (mutable.value.period == period) mutable.value = it }
                .onFailure { mutable.value = mutable.value.copy(loading = false, error = it.message ?: "Không tải được báo cáo") }
        }
    }
}
