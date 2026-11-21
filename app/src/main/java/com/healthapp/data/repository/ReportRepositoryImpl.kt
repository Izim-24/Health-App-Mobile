package com.healthapp.data.repository

import com.healthapp.data.local.dao.ActivityLogDao
import com.healthapp.data.local.dao.HabitLogDao
import com.healthapp.data.local.dao.MealDao
import com.healthapp.data.local.dao.WaterLogDao
import com.healthapp.domain.model.PeriodReport
import com.healthapp.domain.repository.ReportRepository
import java.time.LocalDate
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val mealDao: MealDao,
    private val activityLogDao: ActivityLogDao,
    private val waterLogDao: WaterLogDao,
    private val habitLogDao: HabitLogDao
) : ReportRepository {

    override suspend fun periodReport(accountId: String, start: LocalDate, end: LocalDate): PeriodReport {
        val dates = generateSequence(start) { it.plusDays(1).takeIf { d -> !d.isAfter(end) } }.map { it.toString() }.toList()
        val mealsByDay = dates.map { mealDao.getMealsByDate(accountId, it) }.filter { it.isNotEmpty() }
        val activityByDay = dates.map { activityLogDao.getActivityLogsByDate(accountId, it) }.filter { it.isNotEmpty() }
        val waterByDay = dates.map { waterLogDao.getWaterLogsByDate(accountId, it) }.filter { it.isNotEmpty() }
        val meals = mealsByDay.flatten()
        val activity = activityByDay.flatten()
        fun per(days: Int) = days.coerceAtLeast(1)
        return PeriodReport(
            start = start, end = end,
            nutritionDays = mealsByDay.size,
            avgCalories = meals.sumOf { it.totalCalories } / per(mealsByDay.size),
            avgProtein = (meals.sumOf { it.totalProtein } / per(mealsByDay.size)).toInt(),
            avgCarbs = (meals.sumOf { it.totalCarbs } / per(mealsByDay.size)).toInt(),
            avgFat = (meals.sumOf { it.totalFat } / per(mealsByDay.size)).toInt(),
            activityDays = activityByDay.size,
            avgSteps = activity.sumOf { it.steps } / per(activityByDay.size),
            totalExerciseMinutes = activity.sumOf { it.durationMinutes },
            totalCaloriesBurned = activity.sumOf { it.caloriesBurned },
            waterDays = waterByDay.size,
            avgWaterMl = waterByDay.flatten().sumOf { it.amountMl } / per(waterByDay.size),
            completedHabits = dates.sumOf { habitLogDao.getCompletedHabitsCount(accountId, it) },
            activeHabits = habitLogDao.getTotalActiveHabits(accountId)
        )
    }
}
