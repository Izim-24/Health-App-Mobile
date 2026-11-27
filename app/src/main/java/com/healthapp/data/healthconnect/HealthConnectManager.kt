package com.healthapp.data.healthconnect

import com.healthapp.R
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthapp.data.local.dao.HealthConnectDao
import com.healthapp.data.repository.AccountManager
import com.healthapp.domain.model.HcAvailability
import com.healthapp.domain.model.HcState
import com.healthapp.domain.model.HcSyncSummary
import com.healthapp.domain.repository.HealthSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Read-only import from Health Connect into the local Room database.
 * Data never leaves the device; nothing is written back to Health Connect.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HealthConnectDao,
    private val accountManager: AccountManager,
    private val alerts: com.healthapp.domain.repository.HealthAlertRepository
) : HealthSyncRepository {
    companion object {
        val READ_TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class, ActiveCaloriesBurnedRecord::class, SleepSessionRecord::class,
            HeartRateRecord::class, WeightRecord::class, BloodPressureRecord::class, BloodGlucoseRecord::class,
            ExerciseSessionRecord::class, OxygenSaturationRecord::class, RestingHeartRateRecord::class,
            BodyTemperatureRecord::class, RespiratoryRateRecord::class, HydrationRecord::class
        )
        val PERMISSIONS: Set<String> = READ_TYPES.map { HealthPermission.getReadPermission(it) }.toSet()
        /** Lets the periodic worker read while the app is closed (Health Connect in Android 14+ with SDK extension 13). */
        const val BACKGROUND_PERMISSION = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        private const val PREFS = "health_connect"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        /** Health Connect changes token, valid for the permission set stored next to it. */
        private const val KEY_TOKEN = "changes_token"
        private const val KEY_TOKEN_PERMS = "changes_token_perms"
        private const val MAX_RECORDS_PER_TYPE = 5000
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(HcState())
    override val state: StateFlow<HcState> = mutable.asStateFlow()
    override val permissions: Set<String> get() = if (backgroundSupported()) PERMISSIONS + BACKGROUND_PERMISSION else PERMISSIONS

    /** Background reads exist only in the platform Health Connect from SDK extension 13 (Android 14 QPR / 15+). */
    private fun backgroundSupported(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            android.os.ext.SdkExtensions.getExtensionVersion(android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 13

    private val client: HealthConnectClient? get() =
        if (availability() == HcAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NEEDS_UPDATE
        else -> HcAvailability.NOT_SUPPORTED
    }


    /** Re-reads availability, granted permissions and stored sync info. */
    override suspend fun refresh() {
        val all = runCatching { client?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
        val supported = backgroundSupported()
        mutable.update {
            it.copy(
                availability = availability(),
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                granted = all.intersect(PERMISSIONS),
                backgroundSupported = supported,
                backgroundGranted = supported && BACKGROUND_PERMISSION in all,
                lastSync = prefs.getString(KEY_LAST_SYNC, null)?.let { s -> runCatching { LocalDateTime.parse(s) }.getOrNull() }
            )
        }
        // Keep the periodic import in step with the permissions (they can change in Health Connect at any time).
        runCatching { HealthConnectSyncWorker.update(context, state.value.backgroundSync) }
    }

    /** Called with the permissions the user granted in the system dialog. */
    override suspend fun onPermissionsResult(granted: Set<String>) {
        if (granted.intersect(PERMISSIONS).isNotEmpty()) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            refresh()
            sync(force = true)
        } else {
            refresh()
            mutable.update { it.copy(error = com.healthapp.core.AppLocale.localized(context).getString(R.string.err_hc_no_permission)) }
        }
    }

    /** Auto-sync: runs only when connected and the last sync is older than [minIntervalMin] minutes. */
    override suspend fun syncIfDue(minIntervalMin: Long) {
        refresh()
        val s = state.value
        if (!s.connected) return
        val last = s.lastSync
        if (last != null && last.isAfter(LocalDateTime.now().minusMinutes(minIntervalMin))) return
        sync(force = false)
    }

    override suspend fun sync(force: Boolean): Result<HcSyncSummary> = mutex.withLock {
        val hc = client ?: return Result.failure(IllegalStateException(com.healthapp.core.AppLocale.localized(context).getString(R.string.err_hc_unavailable)))
        mutable.update { it.copy(syncing = true, error = null) }
        val result = runCatching {
            val granted = hc.permissionController.getGrantedPermissions()
            val grantedTypes = READ_TYPES.filter { HealthPermission.getReadPermission(it) in granted }
            fun has(type: KClass<out Record>) = type in grantedTypes

            val accountId = accountManager.getCurrentAccountId()
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            fun Instant.local() = LocalDateTime.ofInstant(this, zone)

            // 1) What changed since the last sync? A token is only valid for the permission set it was made for.
            val permsKey = grantedTypes.joinToString(",") { it.simpleName.orEmpty() }
            val savedToken = prefs.getString(KEY_TOKEN, null)?.takeIf { !force && prefs.getString(KEY_TOKEN_PERMS, null) == permsKey }
            val feed = savedToken?.let { readChanges(hc, it, zone) }
            val plan = HealthConnectSyncPlan.plan(feed?.changes, tokenExpired = feed?.expired == true,
                lastSync = if (force) null else state.value.lastSync, now = now)
            // A fresh token is taken before a full read, so nothing written meanwhile is missed next time.
            val nextToken = if (feed != null && !feed.expired) feed.nextToken
                else if (grantedTypes.isNotEmpty()) runCatching { hc.getChangesToken(ChangesTokenRequest(grantedTypes.toSet())) }.getOrNull()
                else null
            if (plan.skip) {
                saveToken(nextToken, permsKey)
                prefs.edit().putString(KEY_LAST_SYNC, now.toString()).apply()
                return@runCatching HcSyncSummary(0, 0, 0, 0, 0, 0)
            }
            val from = plan.from ?: HealthConnectMapper.syncStart(null, now)
            val range = TimeRangeFilter.between(from.atZone(zone).toInstant(), now.atZone(zone).toInstant())
            // 2) Read each granted type on its own: one failing type must not stop the others.
            var failures = 0
            suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
                runCatching { block() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; failures++; emptyList() }

            // Daily totals (Health Connect de-duplicates overlapping sources in aggregates).
            val aggregateDays = if (has(StepsRecord::class) || has(ActiveCaloriesBurnedRecord::class)) safe {
                val metrics = buildSet {
                    if (has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
                    if (has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                }
                hc.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(from, now), Period.ofDays(1))
                ).map { bucket ->
                    HcDaySteps(
                        bucket.startTime.toLocalDate(),
                        bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                        bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    )
                }
            } else emptyList()
            val rawSteps = if (has(StepsRecord::class)) safe { readAll(hc, StepsRecord::class, range) }
                .map { HcRawSteps(it.metadata.dataOrigin.packageName, it.startTime.local(), it.count, it.endTime.local()) } else emptyList()
            val rawStepsFallback = HealthConnectMapper.dailyStepsFromRaw(rawSteps)
            val days = HealthConnectMapper.mergeDailySteps(aggregateDays, rawStepsFallback)
                .mapNotNull { HealthConnectMapper.steps(accountId, it, now) }

            val sleep = if (has(SleepSessionRecord::class)) safe { readAll(hc, SleepSessionRecord::class, range) }
                .mapNotNull { HealthConnectMapper.sleep(accountId, HcSleep(it.metadata.id, it.startTime.local(), it.endTime.local())) } else emptyList()

            val heartSamples = if (has(HeartRateRecord::class)) safe { readAll(hc, HeartRateRecord::class, range) }
                .flatMap { r -> r.samples.map { HcHeartSample(it.time.local(), it.beatsPerMinute) } } else emptyList()
            val heart = HealthConnectMapper.heartRateHourly(accountId, heartSamples)

            val weights = if (has(WeightRecord::class)) safe { readAll(hc, WeightRecord::class, range) }
                .mapNotNull { HealthConnectMapper.weight(accountId, HcWeight(it.metadata.id, it.time.local(), it.weight.inKilograms)) } else emptyList()

            val bp = if (has(BloodPressureRecord::class)) safe { readAll(hc, BloodPressureRecord::class, range) }
                .map { HealthConnectMapper.bloodPressure(accountId, HcBloodPressure(it.metadata.id, it.time.local(), it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury, it.metadata.dataOrigin.packageName)) } else emptyList()

            val glucose = if (has(BloodGlucoseRecord::class)) safe { readAll(hc, BloodGlucoseRecord::class, range) }
                .map { HealthConnectMapper.glucose(accountId, HcGlucose(it.metadata.id, it.time.local(), it.level.inMillimolesPerLiter, it.metadata.dataOrigin.packageName)) } else emptyList()

            val exercises = if (has(ExerciseSessionRecord::class)) safe { readAll(hc, ExerciseSessionRecord::class, range) }
                .map { HcExercise(it.metadata.id, it.startTime.local(), it.endTime.local(), it.exerciseType, it.title) } else emptyList()
            val spo2 = if (has(OxygenSaturationRecord::class)) safe { readAll(hc, OxygenSaturationRecord::class, range) }
                .mapNotNull { HealthConnectMapper.spo2(accountId, HcSpo2(it.metadata.id, it.time.local(), it.percentage.value, it.metadata.dataOrigin.packageName)) } else emptyList()
            val restingHr = if (has(RestingHeartRateRecord::class)) safe { readAll(hc, RestingHeartRateRecord::class, range) }
                .mapNotNull { HealthConnectMapper.restingHeartRate(accountId, HcRestingHr(it.metadata.id, it.time.local(), it.beatsPerMinute, it.metadata.dataOrigin.packageName)) } else emptyList()
            val temperature = if (has(BodyTemperatureRecord::class)) safe { readAll(hc, BodyTemperatureRecord::class, range) }
                .mapNotNull { HealthConnectMapper.temperature(accountId, HcTemperature(it.metadata.id, it.time.local(), it.temperature.inCelsius, it.metadata.dataOrigin.packageName)) } else emptyList()
            val respiration = if (has(RespiratoryRateRecord::class)) safe { readAll(hc, RespiratoryRateRecord::class, range) }
                .mapNotNull { HealthConnectMapper.respiratoryRate(accountId, HcRespiratory(it.metadata.id, it.time.local(), it.rate, it.metadata.dataOrigin.packageName)) } else emptyList()
            val water = if (has(HydrationRecord::class)) safe { readAll(hc, HydrationRecord::class, range) }
                .mapNotNull { HealthConnectMapper.hydration(accountId, HcHydration(it.metadata.id, it.startTime.local(), it.volume.inLiters)) } else emptyList()
            val workouts = exercises.mapNotNull { HealthConnectMapper.exercise(accountId, it) }


            // 3) Apply: deletions from the source apps first, then the new and edited rows.
            if (plan.deleteIds.isNotEmpty()) dao.deleteByIds(plan.deleteIds)
            dao.upsertAll(days + workouts, sleep, weights, heart + bp + glucose + spo2 + restingHr + temperature + respiration, water)
            prefs.edit().putString(KEY_LAST_SYNC, now.toString()).apply()
            // Keep the old token after a failed type, so its changes are read again next time.
            if (failures == 0) saveToken(nextToken, permsKey)
            // Early warnings from the watch: raw minute-level heart rate + steps of the last 2 hours (not the hourly averages).
            runCatching {
                alerts.check(
                    hr = HealthConnectMapper.alertHeart(heartSamples, now),
                    steps = HealthConnectMapper.alertSteps(rawSteps, now),
                    bp = bp.map { com.healthapp.domain.usecase.BpReading(it.recordedAt, it.value.toInt(), it.secondaryValue?.toInt() ?: 0) },
                    now = now,
                    sessions = exercises.filter { !it.end.isBefore(now.minusHours(2)) }.map { HealthConnectMapper.exerciseWindow(it) }
                )
            }
            HcSyncSummary(days.size, sleep.size, heart.size, weights.size, bp.size, glucose.size,
                spo2.size + restingHr.size + temperature.size + respiration.size + water.size + workouts.size)
        }
        mutable.update { s ->
            result.fold(
                onSuccess = { s.copy(syncing = false, lastSummary = it, lastSync = LocalDateTime.now(), error = null) },
                onFailure = { s.copy(syncing = false, error = com.healthapp.core.AppLocale.localized(context).getString(R.string.err_hc_sync_failed, it.message.orEmpty())) }
            )
        }
        result
    }

    private class ChangeFeed(val changes: List<HcChange>, val nextToken: String?, val expired: Boolean)

    /** Pages through Health Connect's change log since [token]; an expired or rejected token means "read everything". */
    private suspend fun readChanges(hc: HealthConnectClient, token: String, zone: ZoneId): ChangeFeed {
        val out = mutableListOf<HcChange>()
        var current = token
        try {
            while (true) {
                val page = hc.getChanges(current)
                if (page.changesTokenExpired) return ChangeFeed(emptyList(), null, expired = true)
                page.changes.forEach { c ->
                    when (c) {
                        is UpsertionChange -> out += HcChange.Upsert(LocalDateTime.ofInstant(recordTime(c.record), zone))
                        is DeletionChange -> out += HcChange.Deletion(c.recordId)
                    }
                }
                current = page.nextChangesToken
                if (!page.hasMore) break
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return ChangeFeed(emptyList(), null, expired = true)
        }
        return ChangeFeed(out, current, expired = false)
    }

    /** When a changed record happened (interval records: start). Unknown types count as "now". */
    private fun recordTime(r: Record): Instant = when (r) {
        is StepsRecord -> r.startTime
        is ActiveCaloriesBurnedRecord -> r.startTime
        is SleepSessionRecord -> r.startTime
        is HeartRateRecord -> r.startTime
        is ExerciseSessionRecord -> r.startTime
        is HydrationRecord -> r.startTime
        is WeightRecord -> r.time
        is BloodPressureRecord -> r.time
        is BloodGlucoseRecord -> r.time
        is OxygenSaturationRecord -> r.time
        is RestingHeartRateRecord -> r.time
        is BodyTemperatureRecord -> r.time
        is RespiratoryRateRecord -> r.time
        else -> Instant.now()
    }

    private fun saveToken(token: String?, permsKey: String) {
        if (token == null) prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_PERMS).apply()
        else prefs.edit().putString(KEY_TOKEN, token).putString(KEY_TOKEN_PERMS, permsKey).apply()
    }

    private suspend fun <T : Record> readAll(hc: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = hc.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null && out.size < MAX_RECORDS_PER_TYPE)
        return out
    }

    /** Stops syncing, revokes access and (optionally) removes everything that was imported. */
    override suspend fun disconnect(removeImported: Boolean) {
        prefs.edit().remove(KEY_ENABLED).remove(KEY_LAST_SYNC).remove(KEY_TOKEN).remove(KEY_TOKEN_PERMS).apply()
        runCatching { client?.permissionController?.revokeAllPermissions() }
        if (removeImported) dao.deleteImported(accountManager.getCurrentAccountId())
        mutable.update { HcState(availability = availability()) }
        refresh()
    }

    override fun clearError() = mutable.update { it.copy(error = null) }
}
