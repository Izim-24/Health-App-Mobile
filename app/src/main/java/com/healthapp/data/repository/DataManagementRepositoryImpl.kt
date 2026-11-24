package com.healthapp.data.repository

import com.healthapp.data.local.entity.*
import com.healthapp.data.local.dao.ImportRows
import com.healthapp.R
import com.google.gson.JsonDeserializer
import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.healthapp.data.local.dao.DataManagementDao
import com.healthapp.data.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Exports all of the current account's data to a JSON file, and erases it on request. */
@Singleton
class DataManagementRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataManagementDao,
    private val accountManager: AccountManager,
    private val scheduler: ReminderScheduler,
    private val healthConnect: com.healthapp.data.healthconnect.HealthConnectManager,
    private val medications: MedicationRepositoryImpl
) : com.healthapp.domain.repository.DataManagementRepository {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ -> JsonPrimitive(src.toString()) })
        .create()

    /** Writes the export into the app cache (shared via FileProvider) and returns the file. */
    override suspend fun exportToFile(): Result<File> = runCatching {
        val accountId = accountManager.getCurrentAccountId()
        val payload = linkedMapOf(
            "exportedAt" to LocalDateTime.now(),
            "format" to FORMAT,
            "profile" to dao.profiles(accountId).firstOrNull(),
            "habits" to dao.habits(accountId),
            "habitLogs" to dao.habitLogs(accountId),
            "meals" to dao.meals(accountId),
            "mealItems" to dao.mealItems(accountId),
            "water" to dao.waterLogs(accountId),
            "waterGoalMl" to context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE).getInt("goal_$accountId", 0),
            "sleep" to dao.sleepLogs(accountId),
            "activity" to dao.activityLogs(accountId),
            "weight" to dao.weightLogs(accountId),
            "vitals" to dao.vitalLogs(accountId),
            "reminders" to dao.reminders(accountId),
            "medications" to dao.medications(accountId),
            "medicationLogs" to dao.medicationLogs(accountId),
            "moodCheckIns" to dao.moods(accountId),
            "foods" to dao.foodsUsed(accountId)
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest export on disk
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        File(dir, "health-export-$stamp.json").apply { writeText(gson.toJson(payload)) }
    }

    private val reader = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, JsonDeserializer { json, _, _ -> LocalDateTime.parse(json.asString) })
        .create()

    override suspend fun importFromJson(json: String): Result<Int> = runCatching {
        val file = try {
            reader.fromJson(json, BackupFile::class.java)
        } catch (e: Exception) {
            null
        }
        require(file?.format == FORMAT) { com.healthapp.core.AppLocale.localized(context).getString(R.string.err_not_backup) }
        val acc = accountManager.getCurrentAccountId()
        // Rows keep their ids (so a second restore replaces instead of duplicating) but move to this phone's account.
        val rows = ImportRows(
            profile = file!!.profile?.copy(accountId = acc),
            habits = file.habits.orEmpty().map { it.copy(accountId = acc) },
            habitLogs = file.habitLogs.orEmpty(),
            foods = file.foods.orEmpty(),
            meals = file.meals.orEmpty().map { it.copy(accountId = acc) },
            mealItems = file.mealItems.orEmpty(),
            water = file.water.orEmpty().map { it.copy(accountId = acc) },
            sleep = file.sleep.orEmpty().map { it.copy(accountId = acc) },
            activity = file.activity.orEmpty().map { it.copy(accountId = acc) },
            weight = file.weight.orEmpty().map { it.copy(accountId = acc) },
            vitals = file.vitals.orEmpty().map { it.copy(accountId = acc) },
            reminders = file.reminders.orEmpty().map { it.copy(accountId = acc) },
            // Backups from before v6 have no pill fields; Gson leaves pillsPerDose at 0, so restore the default.
            medications = file.medications.orEmpty().map { it.copy(accountId = acc, pillsPerDose = it.pillsPerDose.coerceAtLeast(1)) },
            medicationLogs = file.medicationLogs.orEmpty(),
            moods = file.moodCheckIns.orEmpty().map { it.copy(accountId = acc) }
        )
        dao.importAll(rows)
        file.waterGoalMl?.takeIf { it > 0 }?.let {
            context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE).edit().putInt("goal_$acc", it).apply()
        }
        // Restored reminders and medications need their notifications armed on this phone.
        rows.reminders.filter { it.isEnabled }.forEach { e ->
            scheduler.schedule(
                com.healthapp.domain.model.Reminder(
                    id = e.id, title = e.title,
                    reminderType = runCatching { com.healthapp.domain.model.ReminderType.valueOf(e.reminderType) }
                        .getOrDefault(com.healthapp.domain.model.ReminderType.HABIT),
                    reminderTime = e.reminderTime, isEnabled = true,
                    daysOfWeek = ReminderRepositoryImpl.decodeDays(e.daysOfWeek), createdAt = e.createdAt
                )
            )
        }
        medications.rescheduleAll()
        rows.count
    }

    override suspend fun deleteAllData(): Result<Unit> = runCatching {
        val accountId = accountManager.getCurrentAccountId()
        scheduler.cancelAll()
        healthConnect.disconnect(removeImported = true) // otherwise the next sync would re-import
        dao.deleteAllForAccount(accountId)
        context.getSharedPreferences(MENTAL_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    companion object {
        const val FORMAT = "healthapp-export-v1"
        const val MENTAL_PREFS = "mental_health_private"
        const val WATER_PREFS = "water_goals"
    }
}

/** Shape of an export file (see [DataManagementRepositoryImpl.exportToFile]); every list is optional. Kept by R8. */
class BackupFile(
    val format: String? = null,
    val profile: UserProfileEntity? = null,
    val habits: List<HabitEntity>? = null,
    val habitLogs: List<HabitLogEntity>? = null,
    val meals: List<MealEntity>? = null,
    val mealItems: List<MealItemEntity>? = null,
    val foods: List<FoodEntity>? = null,
    val water: List<WaterLogEntity>? = null,
    val waterGoalMl: Int? = null,
    val sleep: List<SleepLogEntity>? = null,
    val activity: List<ActivityLogEntity>? = null,
    val weight: List<WeightLogEntity>? = null,
    val vitals: List<VitalLogEntity>? = null,
    val reminders: List<ReminderEntity>? = null,
    val medications: List<MedicationEntity>? = null,
    val medicationLogs: List<MedicationLogEntity>? = null,
    val moodCheckIns: List<MoodEntity>? = null
)
