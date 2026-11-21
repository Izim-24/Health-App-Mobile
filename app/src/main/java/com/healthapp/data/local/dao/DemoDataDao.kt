package com.healthapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.healthapp.data.local.entity.*

/** Bulk insert/remove of sample data. Every demo row's id starts with "demo_". */
@Dao
interface DemoDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun foods(rows: List<FoodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun mealItems(rows: List<MealItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun meals(rows: List<MealEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun water(rows: List<WaterLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun sleep(rows: List<SleepLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun activity(rows: List<ActivityLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun weight(rows: List<WeightLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun vitals(rows: List<VitalLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun habits(rows: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun habitLogs(rows: List<HabitLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun medications(rows: List<MedicationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun medicationLogs(rows: List<MedicationLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun moods(rows: List<MoodEntity>)

    @Query("SELECT COUNT(*) FROM meals WHERE substr(id, 1, 5) = 'demo_'") suspend fun demoMealCount(): Int

    @Query("DELETE FROM meal_items WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearMealItems()
    @Query("DELETE FROM meals WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearMeals()
    @Query("DELETE FROM foods WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearFoods()
    @Query("DELETE FROM water_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearWater()
    @Query("DELETE FROM sleep_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearSleep()
    @Query("DELETE FROM activity_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearActivity()
    @Query("DELETE FROM weight_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearWeight()
    @Query("DELETE FROM vital_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearVitals()
    @Query("DELETE FROM habit_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearHabitLogs()
    @Query("DELETE FROM habits WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearHabits()
    @Query("DELETE FROM medication_logs WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearMedicationLogs()
    @Query("DELETE FROM medications WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearMedications()
    @Query("DELETE FROM mood_entries WHERE substr(id, 1, 5) = 'demo_'") suspend fun clearMoods()

    @Transaction
    suspend fun clearAll() {
        clearMealItems(); clearMeals(); clearFoods(); clearWater(); clearSleep(); clearActivity(); clearWeight()
        clearVitals(); clearHabitLogs(); clearHabits(); clearMedicationLogs(); clearMedications(); clearMoods()
    }
}
