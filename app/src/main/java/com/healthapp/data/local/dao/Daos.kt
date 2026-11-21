package com.healthapp.data.local.dao

import androidx.room.*
import com.healthapp.data.local.entity.AccountEntity
import com.healthapp.data.local.entity.UserProfileEntity
import com.healthapp.data.local.entity.HabitEntity
import com.healthapp.data.local.entity.HabitLogEntity
import com.healthapp.data.local.entity.FoodEntity
import com.healthapp.data.local.entity.MealEntity
import com.healthapp.data.local.entity.MealItemEntity
import com.healthapp.data.local.entity.WaterLogEntity
import com.healthapp.data.local.entity.SleepLogEntity
import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.data.local.entity.WeightLogEntity
import com.healthapp.data.local.entity.ReminderEntity
import com.healthapp.data.local.entity.VitalLogEntity
import com.healthapp.data.local.entity.MoodEntity
import kotlinx.coroutines.flow.Flow

// Account DAO
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE email = :email")
    suspend fun getAccountByEmail(email: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
}

// User Profile DAO
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE accountId = :accountId")
    suspend fun getProfileByAccountId(accountId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE accountId = :accountId")
    fun observeProfileByAccountId(accountId: String): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE accountId = :accountId")
    suspend fun deleteProfile(accountId: String)
}

// Habit DAO
@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeHabitsByAccountId(accountId: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE accountId = :accountId ORDER BY createdAt DESC")
    suspend fun getHabitsByAccountId(accountId: String): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabitById(habitId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :habitId")
    fun observeHabitById(habitId: String): Flow<HabitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabit(habitId: String)

    @Query("SELECT COUNT(*) FROM habits WHERE accountId = :accountId AND isEnabled = 1")
    suspend fun getActiveHabitCount(accountId: String): Int
}

// Habit Log DAO
@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY recordedAt DESC")
    fun observeHabitLogsByHabitId(habitId: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND completionDate = :date")
    suspend fun getHabitLog(habitId: String, date: String): HabitLogEntity?

    @Query("""
        SELECT COUNT(*) FROM habit_logs hl
        INNER JOIN habits h ON hl.habitId = h.id
        WHERE h.accountId = :accountId AND hl.completionDate = :date AND hl.status = 1
    """)
    suspend fun getCompletedHabitsCount(accountId: String, date: String): Int

    @Query("""
        SELECT hl.habitId FROM habit_logs hl
        INNER JOIN habits h ON hl.habitId = h.id
        WHERE h.accountId = :accountId AND hl.completionDate = :date AND hl.status = 1
    """)
    suspend fun getCompletedHabitIds(accountId: String, date: String): List<String>

    @Query("""
        SELECT COUNT(*) FROM habits
        WHERE accountId = :accountId AND isEnabled = 1
    """)
    suspend fun getTotalActiveHabits(accountId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Update
    suspend fun updateHabitLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND completionDate = :date")
    suspend fun deleteHabitLog(habitId: String, date: String)

    @Query("""
        SELECT COUNT(*) FROM habit_logs
        WHERE habitId = :habitId AND status = 1
        AND completionDate >= :startDate AND completionDate <= :endDate
    """)
    suspend fun getCompletedCountInRange(habitId: String, startDate: String, endDate: String): Int
}

// Food DAO
@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY name ASC")
    fun observeAllFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :foodId")
    suspend fun getFoodById(foodId: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchFoods(query: String): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Update
    suspend fun updateFood(food: FoodEntity)

    @Query("DELETE FROM foods WHERE id = :foodId")
    suspend fun deleteFood(foodId: String)
}

// Meal DAO
@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeMealsByAccountId(accountId: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE accountId = :accountId AND mealDate = :date ORDER BY createdAt ASC")
    fun observeMealsByDate(accountId: String, date: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE accountId = :accountId AND mealDate = :date ORDER BY createdAt ASC")
    suspend fun getMealsByDate(accountId: String, date: String): List<MealEntity>

    @Query("SELECT * FROM meals WHERE id = :mealId")
    suspend fun getMealById(mealId: String): MealEntity?

    @Query("""
        SELECT COALESCE(SUM(totalCalories), 0) FROM meals
        WHERE accountId = :accountId AND mealDate = :date
    """)
    suspend fun getTotalCaloriesForDate(accountId: String, date: String): Int

    @Query("""
        SELECT COALESCE(SUM(totalProtein), 0) FROM meals
        WHERE accountId = :accountId AND mealDate = :date
    """)
    suspend fun getTotalProteinForDate(accountId: String, date: String): Double

    @Query("""
        SELECT COALESCE(SUM(totalCarbs), 0) FROM meals
        WHERE accountId = :accountId AND mealDate = :date
    """)
    suspend fun getTotalCarbsForDate(accountId: String, date: String): Double

    @Query("""
        SELECT COALESCE(SUM(totalFat), 0) FROM meals
        WHERE accountId = :accountId AND mealDate = :date
    """)
    suspend fun getTotalFatForDate(accountId: String, date: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: String)
}

// Meal Item DAO
@Dao
interface MealItemDao {
    @Query("SELECT * FROM meal_items WHERE mealId = :mealId")
    fun observeMealItemsByMealId(mealId: String): Flow<List<MealItemEntity>>

    @Query("SELECT * FROM meal_items WHERE mealId = :mealId")
    suspend fun getMealItemsByMealId(mealId: String): List<MealItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealItem(item: MealItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealItems(items: List<MealItemEntity>)

    @Update
    suspend fun updateMealItem(item: MealItemEntity)

    @Query("DELETE FROM meal_items WHERE id = :itemId")
    suspend fun deleteMealItem(itemId: String)

    @Query("DELETE FROM meal_items WHERE mealId = :mealId")
    suspend fun deleteMealItemsByMealId(mealId: String)
}

// Water Log DAO
@Dao
interface WaterLogDao {
    @Query("SELECT * FROM water_logs WHERE accountId = :accountId AND date = :date ORDER BY recordedAt DESC")
    fun observeWaterLogsByDate(accountId: String, date: String): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs WHERE accountId = :accountId AND date = :date ORDER BY recordedAt DESC")
    suspend fun getWaterLogsByDate(accountId: String, date: String): List<WaterLogEntity>

    @Query("""
        SELECT COALESCE(SUM(amountMl), 0) FROM water_logs
        WHERE accountId = :accountId AND date = :date
    """)
    suspend fun getTotalWaterForDate(accountId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterLog(log: WaterLogEntity)

    @Update
    suspend fun updateWaterLog(log: WaterLogEntity)

    @Query("DELETE FROM water_logs WHERE id = :logId")
    suspend fun deleteWaterLog(logId: String)
}

// Sleep Log DAO
@Dao
interface SleepLogDao {
    @Query("SELECT * FROM sleep_logs WHERE accountId = :accountId ORDER BY bedtime DESC")
    fun observeSleepLogsByAccountId(accountId: String): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_logs WHERE accountId = :accountId AND date = :date")
    suspend fun getSleepLogByDate(accountId: String, date: String): SleepLogEntity?

    @Query("""
        SELECT * FROM sleep_logs
        WHERE accountId = :accountId
        AND date >= :startDate AND date <= :endDate
        ORDER BY bedtime DESC
    """)
    suspend fun getSleepLogsInRange(accountId: String, startDate: String, endDate: String): List<SleepLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepLog(log: SleepLogEntity)

    @Update
    suspend fun updateSleepLog(log: SleepLogEntity)

    @Query("DELETE FROM sleep_logs WHERE id = :logId")
    suspend fun deleteSleepLog(logId: String)
}

// Activity Log DAO
@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs WHERE accountId = :accountId ORDER BY recordedAt DESC")
    fun observeActivityLogsByAccountId(accountId: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE accountId = :accountId AND date = :date ORDER BY recordedAt DESC")
    fun observeActivityLogsByDate(accountId: String, date: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE accountId = :accountId AND date = :date ORDER BY recordedAt DESC")
    suspend fun getActivityLogsByDate(accountId: String, date: String): List<ActivityLogEntity>

    @Query("""
        SELECT COALESCE(SUM(durationMinutes), 0) FROM activity_logs
        WHERE accountId = :accountId AND date = :date
    """)
    suspend fun getTotalDurationForDate(accountId: String, date: String): Int

    @Query("""
        SELECT COALESCE(SUM(steps), 0) FROM activity_logs
        WHERE accountId = :accountId AND date = :date
    """)
    suspend fun getTotalStepsForDate(accountId: String, date: String): Int

    @Query("""
        SELECT COALESCE(SUM(caloriesBurned), 0) FROM activity_logs
        WHERE accountId = :accountId AND date = :date
    """)
    suspend fun getTotalCaloriesBurnedForDate(accountId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity)

    @Update
    suspend fun updateActivityLog(log: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE id = :logId")
    suspend fun deleteActivityLog(logId: String)
}

// Weight Log DAO
@Dao
interface WeightLogDao {
    @Query("SELECT * FROM weight_logs WHERE accountId = :accountId ORDER BY recordedAt DESC")
    fun observeWeightLogsByAccountId(accountId: String): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs WHERE accountId = :accountId AND date = :date")
    suspend fun getWeightLogByDate(accountId: String, date: String): WeightLogEntity?

    @Query("SELECT * FROM weight_logs WHERE accountId = :accountId ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getRecentWeightLogs(accountId: String, limit: Int): List<WeightLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLogEntity)

    @Update
    suspend fun updateWeightLog(log: WeightLogEntity)

    @Query("DELETE FROM weight_logs WHERE id = :logId")
    suspend fun deleteWeightLog(logId: String)
}

@Dao
interface VitalLogDao {
    @Query("SELECT * FROM vital_logs WHERE accountId = :accountId AND type = :type ORDER BY recordedAt DESC")
    fun observeByType(accountId: String, type: String): Flow<List<VitalLogEntity>>

    @Query("SELECT * FROM vital_logs WHERE accountId = :accountId ORDER BY recordedAt DESC")
    fun observeAll(accountId: String): Flow<List<VitalLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: VitalLogEntity)

    @Query("DELETE FROM vital_logs WHERE id = :id")
    suspend fun delete(id: String)
}

// Reminder DAO
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE accountId = :accountId ORDER BY reminderTime ASC")
    fun observeRemindersByAccountId(accountId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE accountId = :accountId AND isEnabled = 1 ORDER BY reminderTime ASC")
    suspend fun getEnabledReminders(accountId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: String)
}


@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entries WHERE accountId = :accountId ORDER BY date DESC")
    fun observeEntries(accountId: String): Flow<List<MoodEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<MoodEntity>)
}
