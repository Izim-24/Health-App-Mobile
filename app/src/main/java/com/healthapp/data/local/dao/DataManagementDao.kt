package com.healthapp.data.local.dao

import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.healthapp.data.local.entity.*

/** Account-wide reads for export and deletes for "erase my data". */
@Dao
interface DataManagementDao {
    @Query("SELECT * FROM user_profiles WHERE accountId = :accountId") suspend fun profiles(accountId: String): List<UserProfileEntity>
    @Query("SELECT * FROM habits WHERE accountId = :accountId") suspend fun habits(accountId: String): List<HabitEntity>
    @Query("SELECT * FROM habit_logs WHERE habitId IN (SELECT id FROM habits WHERE accountId = :accountId)") suspend fun habitLogs(accountId: String): List<HabitLogEntity>
    @Query("SELECT * FROM meals WHERE accountId = :accountId") suspend fun meals(accountId: String): List<MealEntity>
    @Query("SELECT * FROM meal_items WHERE mealId IN (SELECT id FROM meals WHERE accountId = :accountId)") suspend fun mealItems(accountId: String): List<MealItemEntity>
    @Query("SELECT * FROM water_logs WHERE accountId = :accountId") suspend fun waterLogs(accountId: String): List<WaterLogEntity>
    @Query("SELECT * FROM sleep_logs WHERE accountId = :accountId") suspend fun sleepLogs(accountId: String): List<SleepLogEntity>
    @Query("SELECT * FROM activity_logs WHERE accountId = :accountId") suspend fun activityLogs(accountId: String): List<ActivityLogEntity>
    @Query("SELECT * FROM weight_logs WHERE accountId = :accountId") suspend fun weightLogs(accountId: String): List<WeightLogEntity>
    @Query("SELECT * FROM vital_logs WHERE accountId = :accountId") suspend fun vitalLogs(accountId: String): List<VitalLogEntity>
    @Query("SELECT * FROM reminders WHERE accountId = :accountId") suspend fun reminders(accountId: String): List<ReminderEntity>
    @Query("SELECT * FROM mood_entries WHERE accountId = :accountId ORDER BY date") suspend fun moods(accountId: String): List<MoodEntity>
    @Query("SELECT * FROM medications WHERE accountId = :accountId") suspend fun medications(accountId: String): List<MedicationEntity>
    @Query("SELECT * FROM medication_logs WHERE medicationId IN (SELECT id FROM medications WHERE accountId = :accountId)") suspend fun medicationLogs(accountId: String): List<MedicationLogEntity>
    /** Foods used by the account's meals (AI-scanned and custom foods live only here), so a restore keeps their names. */
    @Query("SELECT * FROM foods WHERE id IN (SELECT foodId FROM meal_items WHERE mealId IN (SELECT id FROM meals WHERE accountId = :accountId))") suspend fun foodsUsed(accountId: String): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProfile(p: UserProfileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabits(r: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitLogs(r: List<HabitLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFoods(r: List<FoodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMealItems(r: List<MealItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeals(r: List<MealEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWater(r: List<WaterLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSleep(r: List<SleepLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putActivity(r: List<ActivityLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWeight(r: List<WeightLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putVitals(r: List<VitalLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminders(r: List<ReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMedications(r: List<MedicationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMedicationLogs(r: List<MedicationLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMoods(r: List<MoodEntity>)

    @Query("DELETE FROM habit_logs WHERE habitId IN (SELECT id FROM habits WHERE accountId = :accountId)") suspend fun deleteHabitLogs(accountId: String)
    @Query("DELETE FROM habits WHERE accountId = :accountId") suspend fun deleteHabits(accountId: String)
    @Query("DELETE FROM meal_items WHERE mealId IN (SELECT id FROM meals WHERE accountId = :accountId)") suspend fun deleteMealItems(accountId: String)
    @Query("DELETE FROM meals WHERE accountId = :accountId") suspend fun deleteMeals(accountId: String)
    @Query("DELETE FROM water_logs WHERE accountId = :accountId") suspend fun deleteWater(accountId: String)
    @Query("DELETE FROM sleep_logs WHERE accountId = :accountId") suspend fun deleteSleep(accountId: String)
    @Query("DELETE FROM activity_logs WHERE accountId = :accountId") suspend fun deleteActivity(accountId: String)
    @Query("DELETE FROM weight_logs WHERE accountId = :accountId") suspend fun deleteWeight(accountId: String)
    @Query("DELETE FROM vital_logs WHERE accountId = :accountId") suspend fun deleteVitals(accountId: String)
    @Query("DELETE FROM reminders WHERE accountId = :accountId") suspend fun deleteReminders(accountId: String)
    @Query("DELETE FROM mood_entries WHERE accountId = :accountId") suspend fun deleteMoods(accountId: String)
    @Query("DELETE FROM user_profiles WHERE accountId = :accountId") suspend fun deleteProfile(accountId: String)
    @Query("DELETE FROM medication_logs WHERE medicationId IN (SELECT id FROM medications WHERE accountId = :accountId)") suspend fun deleteMedicationLogs(accountId: String)
    @Query("DELETE FROM medications WHERE accountId = :accountId") suspend fun deleteMedications(accountId: String)

    /**
     * Restores a backup in one transaction: all rows or none. Rows replace existing ones with the same id, so
     * restoring the same file twice changes nothing. Meal items go in before meals (meal lists observe meals).
     */
    @Transaction
    suspend fun importAll(b: ImportRows) {
        b.profile?.let { deleteProfile(it.accountId); putProfile(it) }
        putHabits(b.habits); putHabitLogs(b.habitLogs)
        putFoods(b.foods); putMealItems(b.mealItems); putMeals(b.meals)
        putWater(b.water); putSleep(b.sleep); putActivity(b.activity); putWeight(b.weight); putVitals(b.vitals)
        putReminders(b.reminders); putMedications(b.medications); putMedicationLogs(b.medicationLogs); putMoods(b.moods)
    }

    @Transaction
    suspend fun deleteAllForAccount(accountId: String) {
        deleteHabitLogs(accountId); deleteHabits(accountId)
        deleteMealItems(accountId); deleteMeals(accountId)
        deleteWater(accountId); deleteSleep(accountId); deleteActivity(accountId)
        deleteWeight(accountId); deleteVitals(accountId); deleteReminders(accountId)
        deleteMoods(accountId); deleteProfile(accountId)
        deleteMedicationLogs(accountId); deleteMedications(accountId)
    }
}

/** Everything a backup restores, already moved to the current account. */
class ImportRows(
    val profile: UserProfileEntity?, val habits: List<HabitEntity>, val habitLogs: List<HabitLogEntity>,
    val foods: List<FoodEntity>, val meals: List<MealEntity>, val mealItems: List<MealItemEntity>,
    val water: List<WaterLogEntity>, val sleep: List<SleepLogEntity>, val activity: List<ActivityLogEntity>,
    val weight: List<WeightLogEntity>, val vitals: List<VitalLogEntity>, val reminders: List<ReminderEntity>,
    val medications: List<MedicationEntity>, val medicationLogs: List<MedicationLogEntity>, val moods: List<MoodEntity>
) {
    val count: Int get() = (if (profile != null) 1 else 0) + habits.size + habitLogs.size + meals.size + mealItems.size +
        water.size + sleep.size + activity.size + weight.size + vitals.size + reminders.size + medications.size +
        medicationLogs.size + moods.size
}
