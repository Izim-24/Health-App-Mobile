package com.healthapp.domain.repository

import com.healthapp.domain.model.*
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    suspend fun createHabit(habit: Habit): Result<Habit>
    suspend fun updateHabit(habit: Habit): Result<Unit>
    suspend fun deleteHabit(habitId: String): Result<Unit>
    suspend fun getHabit(habitId: String): Result<Habit>
    fun observeHabits(userId: String): Flow<List<Habit>>
    suspend fun markHabitComplete(habitId: String, date: String): Result<Unit>
    suspend fun setHabitCompleted(habitId: String, date: String, completed: Boolean): Result<Unit>
    suspend fun getCompletedHabitIdsForDate(userId: String, date: String): Result<Set<String>>
    suspend fun getHabitStreak(habitId: String): Result<Int>
    suspend fun getHabitCompletionRate(habitId: String, days: Int = 30): Result<Double>
    suspend fun getCompletedHabitsForDate(userId: String, date: String): Result<Int>
    suspend fun getTotalActiveHabits(userId: String): Result<Int>
    /** Dates (YYYY-MM-DD) on which the habit was completed. */
    suspend fun getCompletionDates(habitId: String): Result<Set<String>>
}

interface NutritionRepository {
    suspend fun addMeal(userId: String, meal: Meal): Result<Meal>
    suspend fun updateMeal(userId: String, meal: Meal): Result<Unit>
    suspend fun deleteMeal(mealId: String): Result<Unit>
    suspend fun getMeal(mealId: String): Result<Meal>
    fun observeMealsByDate(userId: String, date: String): Flow<List<Meal>>
    suspend fun getNutritionSummary(userId: String, date: String): Result<NutritionSummary>
    /** Built-in dishes and stored foods (accent-insensitive); with [online], also Open Food Facts products. */
    suspend fun searchFoods(query: String, online: Boolean = true): Result<List<Food>>
    suspend fun addCustomFood(food: Food): Result<Food>
    suspend fun seedDefaultFoods(): Result<Unit>
}

interface WaterRepository {
    suspend fun addWaterIntake(userId: String, amountMl: Int): Result<WaterIntake>
    suspend fun getTotalWaterForDate(userId: String, date: String): Result<Int>
    fun observeWaterLogsForDate(userId: String, date: String): Flow<List<WaterIntake>>
    suspend fun deleteWaterLog(logId: String): Result<Unit>
    /** Daily goal in ml; 0 = not set. */
    suspend fun getDailyGoal(userId: String): Int
    suspend fun setDailyGoal(userId: String, ml: Int)
}

interface SleepRepository {
    suspend fun recordSleep(userId: String, sleep: SleepRecord): Result<SleepRecord>
    suspend fun updateSleep(userId: String, sleep: SleepRecord): Result<Unit>
    suspend fun deleteSleep(sleepId: String): Result<Unit>
    suspend fun getSleepForDate(userId: String, date: String): Result<SleepRecord?>
    fun observeSleepRecords(userId: String): Flow<List<SleepRecord>>
    suspend fun getAverageSleepDuration(userId: String, days: Int = 7): Result<Int>
}

interface ActivityRepository {
    suspend fun recordActivity(userId: String, activity: ActivityRecord): Result<ActivityRecord>
    suspend fun updateActivity(userId: String, activity: ActivityRecord): Result<Unit>
    suspend fun deleteActivity(activityId: String): Result<Unit>
    fun observeActivities(userId: String): Flow<List<ActivityRecord>>
    fun observeActivitiesByDate(userId: String, date: String): Flow<List<ActivityRecord>>
    suspend fun getTotalStepsForDate(userId: String, date: String): Result<Int>
    suspend fun getTotalDurationForDate(userId: String, date: String): Result<Int>
    suspend fun getTotalCaloriesBurnedForDate(userId: String, date: String): Result<Int>
}

interface WeightRepository {
    suspend fun recordWeight(userId: String, weight: WeightRecord): Result<WeightRecord>
    suspend fun updateWeight(userId: String, weight: WeightRecord): Result<Unit>
    suspend fun deleteWeight(weightId: String): Result<Unit>
    suspend fun getWeightForDate(userId: String, date: String): Result<WeightRecord?>
    fun observeWeightRecords(userId: String): Flow<List<WeightRecord>>
    suspend fun getWeightTrend(userId: String, days: Int = 30): Result<List<WeightRecord>>
}

interface VitalRepository {
    suspend fun record(measurement: VitalMeasurement): Result<VitalMeasurement>
    fun observeAll(userId: String): Flow<List<VitalMeasurement>>
    /** Readings of one [type] taken at or after [from], newest first. */
    suspend fun since(userId: String, type: VitalType, from: java.time.LocalDateTime): List<VitalMeasurement>
    suspend fun delete(id: String): Result<Unit>
}

interface ReminderRepository {
    suspend fun createReminder(reminder: Reminder): Result<Reminder>
    suspend fun updateReminder(reminder: Reminder): Result<Unit>
    suspend fun deleteReminder(reminderId: String): Result<Unit>
    fun observeReminders(userId: String): Flow<List<Reminder>>
    suspend fun getEnabledReminders(userId: String): Result<List<Reminder>>
}

interface AIRepository {
    suspend fun analyzeFoodImage(imageUri: String): Result<AIFoodAnalysis>
    suspend fun chatWithAssistant(userId: String, query: String): Result<String>
}

