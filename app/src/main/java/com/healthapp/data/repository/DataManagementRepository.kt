package com.healthapp.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
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
class DataManagementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataManagementDao,
    private val accountManager: AccountManager,
    private val scheduler: ReminderScheduler,
    private val healthConnect: com.healthapp.data.healthconnect.HealthConnectManager
) {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ -> JsonPrimitive(src.toString()) })
        .create()

    /** Writes the export into the app cache (shared via FileProvider) and returns the file. */
    suspend fun exportToFile(): Result<File> = runCatching {
        val accountId = accountManager.getCurrentAccountId()
        val mentalRaw = context.getSharedPreferences(MENTAL_PREFS, Context.MODE_PRIVATE).getString("entries", "{}") ?: "{}"
        val payload = linkedMapOf(
            "exportedAt" to LocalDateTime.now(),
            "format" to "healthapp-export-v1",
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
            "moodCheckIns" to runCatching { gson.fromJson(mentalRaw, JsonElement::class.java) }.getOrNull()
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest export on disk
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        File(dir, "health-export-$stamp.json").apply { writeText(gson.toJson(payload)) }
    }

    suspend fun deleteAllData(): Result<Unit> = runCatching {
        val accountId = accountManager.getCurrentAccountId()
        scheduler.cancelAll()
        healthConnect.disconnect(removeImported = true) // otherwise the next sync would re-import
        dao.deleteAllForAccount(accountId)
        context.getSharedPreferences(MENTAL_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    companion object {
        const val MENTAL_PREFS = "mental_health_private"
        const val WATER_PREFS = "water_goals"
    }
}
