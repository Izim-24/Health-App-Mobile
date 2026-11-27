package com.healthapp.domain.repository

import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.HcState
import com.healthapp.domain.model.HcSyncSummary
import com.healthapp.domain.model.HealthProfile
import com.healthapp.domain.model.Medication
import com.healthapp.domain.model.MoodEntry
import com.healthapp.domain.model.PeriodReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/*
 * Contracts the presentation layer depends on. Implementations live in data/ and are bound in di/.
 * Pure Kotlin/JVM types only — no Android classes cross this boundary.
 */

/** The signed-in (currently: single local) account. */
interface AccountSession {
    suspend fun getCurrentAccountId(): String
}

interface ProfileRepository {
    suspend fun getProfile(accountId: String): HealthProfile?
    suspend fun saveProfile(accountId: String, profile: HealthProfile)
}

interface ReportRepository {
    /** Averages over the days in [start]..[end] that have data (days with no entries are not counted as zero). */
    suspend fun periodReport(accountId: String, start: LocalDate, end: LocalDate): PeriodReport
}

interface HealthAlertRepository {
    /** Newest first, at most 20, simulated alerts included (flagged). */
    val recent: kotlinx.coroutines.flow.Flow<List<com.healthapp.domain.usecase.VitalAlert>>
    /**
     * Runs [com.healthapp.domain.usecase.VitalAlerts] (adding the profile age and, for real data, stored blood pressure of
     * the last 7 days), applies the cooldown, posts a notification for each due alert when [notify] and records them.
     * Returns the due alerts.
     */
    suspend fun check(
        hr: List<com.healthapp.domain.usecase.HrSample>,
        steps: List<com.healthapp.domain.usecase.StepSample>,
        bp: List<com.healthapp.domain.usecase.BpReading>,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        notify: Boolean = true,
        simulated: Boolean = false,
        /** Workouts from Health Connect: time inside one counts as exercise. */
        sessions: List<com.healthapp.domain.usecase.ExerciseWindow> = emptyList()
    ): List<com.healthapp.domain.usecase.VitalAlert>
    /**
     * A reading the user typed: single-reading rules only (very fast heart rate, BP crisis/high). Shown in the app's
     * recent list, never notified and never touching the cooldown of watch data. Returns the alerts.
     */
    suspend fun checkManual(
        hr: com.healthapp.domain.usecase.HrSample?,
        bp: com.healthapp.domain.usecase.BpReading?
    ): List<com.healthapp.domain.usecase.VitalAlert>
    /** Forgets the simulator's cooldown so a new scenario can alert again. */
    fun resetSimulation()
    suspend fun clearRecent()
}

interface ReportExportRepository {
    /** Lays [report] out as a one-page PDF in the app cache (shared via FileProvider) and returns the file. */
    suspend fun exportPdf(report: PeriodReport): Result<File>
}

interface MedicationRepository {
    suspend fun observeMedications(): Flow<List<Medication>>
    suspend fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>>
    suspend fun getMedication(id: String): Medication?
    suspend fun save(medication: Medication): Result<Unit>
    suspend fun setActive(medication: Medication, active: Boolean): Result<Unit>
    suspend fun delete(medication: Medication): Result<Unit>
    /** Records TAKEN/SKIPPED for a dose slot; null clears it (undo). */
    suspend fun logDose(medicationId: String, date: LocalDate, time: LocalTime, status: DoseStatus?): Result<Unit>
}

interface MoodRepository {
    fun observeEntries(): Flow<List<MoodEntry>>
    suspend fun save(entry: MoodEntry): Result<Unit>
}

interface DataManagementRepository {
    /** Writes all of the account's data to a JSON file (for sharing) and returns it. */
    suspend fun exportToFile(): Result<File>
    /** Restores an export made by this app (merge: same ids are replaced). Returns the number of records restored. */
    suspend fun importFromJson(json: String): Result<Int>
    suspend fun deleteAllData(): Result<Unit>
}

/** Read-only import from Health Connect. */
interface HealthSyncRepository {
    val state: StateFlow<HcState>
    /** Health Connect permission strings this app requests. */
    val permissions: Set<String>
    suspend fun refresh()
    suspend fun onPermissionsResult(granted: Set<String>)
    suspend fun sync(force: Boolean): Result<HcSyncSummary>
    suspend fun syncIfDue(minIntervalMin: Long = 15)
    suspend fun disconnect(removeImported: Boolean)
    fun clearError()
}

/** Sample data for demos. Rows are tagged so they can be removed without touching real entries. */
interface DemoDataRepository {
    /** Replaces any previous sample data with the last 14 days; returns the number of days. */
    suspend fun seed(): Result<Int>
    suspend fun clear(): Result<Unit>
    suspend fun hasDemoData(): Boolean
}

/** Small app-level preferences (DataStore). */
interface AppSettingsRepository {
    val settings: Flow<com.healthapp.domain.model.AppSettings>
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setThemeMode(mode: com.healthapp.domain.model.ThemeMode)
}
