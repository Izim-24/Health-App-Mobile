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
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import java.time.Duration
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
        /** Types whose last read failed and where their re-read must start. */
        private const val KEY_PENDING_TYPES = "pending_types"
        private const val KEY_PENDING_FROM = "pending_from"
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

            // 1) What changed since the last sync? Keys are permission strings (stable across R8 builds).
            val grantedPerms = grantedTypes.map { perm(it) }.toSet()
            val permsKey = grantedPerms.sorted().joinToString(",")
            val savedPerms = prefs.getString(KEY_TOKEN_PERMS, null)
            val knownPerms = savedPerms?.split(",")?.filter { it.isNotBlank() }?.toSet()
            // Types allowed since the last sync (or since an update added new types) have no history here yet.
            val newTypesGranted = knownPerms == null || grantedPerms.any { it !in knownPerms }
            // The old token stays usable when access was only added (it covers the old types), so their deletions are not lost.
            val oldToken = prefs.getString(KEY_TOKEN, null)?.takeIf { knownPerms != null && grantedPerms.containsAll(knownPerms) }
            val feed = oldToken?.let { readChanges(hc, it, zone) }
            val plan = HealthConnectSyncPlan.plan(
                feed?.changes?.takeIf { !feed.failed }, tokenExpired = feed?.expired == true || feed?.failed == true,
                lastSync = if (force) null else state.value.lastSync, now = now, newTypesGranted = newTypesGranted || force
            )
            // A fresh token is taken before a full read, so nothing written meanwhile is missed next time.
            val nextToken = if (feed != null && !feed.expired && !feed.failed) feed.nextToken
                else if (grantedTypes.isNotEmpty()) runCatching { hc.getChangesToken(ChangesTokenRequest(grantedTypes.toSet())) }.getOrNull()
                else null
            fun storeToken() {
                if (feed?.failed == true) return   // keep the old token: its changes are read again next time
                saveToken(HealthConnectSyncPlan.tokenToSave(oldToken, nextToken, changesFailed = false), permsKey)
            }
            val pendingBefore = loadPending()
            if (plan.skip && pendingBefore == null) {
                storeToken()
                prefs.edit().putString(KEY_LAST_SYNC, now.toString()).apply()
                return@runCatching state.value.lastSummary ?: HcSyncSummary(0, 0, 0, 0, 0, 0)
            }
            val from = plan.from ?: now.minusHours(2)
            fun rangeFrom(start: LocalDateTime) = TimeRangeFilter.between(start.atZone(zone).toInstant(), now.atZone(zone).toInstant())
            /** Start for one type: earlier when it still owes data from a failed sync (never before the 30-day window). */
            fun fromOf(type: KClass<out Record>) = HealthConnectSyncPlan.fromFor(perm(type), from, pendingBefore, now)
            /** Record types are read from the start of their day, so a stored row of that span is either returned or gone. */
            fun dayOf(type: KClass<out Record>) = HealthConnectSyncPlan.stepsFrom(fromOf(type))
            fun rangeOf(type: KClass<out Record>) = rangeFrom(dayOf(type))
            val stepsFrom = HealthConnectSyncPlan.stepsFrom(fromOf(StepsRecord::class))
            val heartFrom = HealthConnectSyncPlan.heartFrom(fromOf(HeartRateRecord::class))
            val alertFrom = now.minusHours(2)
            // 2) Read each granted type on its own: one failing type must not stop the others.
            val failedTypes = mutableSetOf<KClass<out Record>>()
            var stepsTruncated = false
            val truncatedTypes = mutableSetOf<KClass<out Record>>()
            suspend fun <T> safe(type: KClass<out Record>, block: suspend () -> List<T>): List<T> =
                runCatching { block() }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    failedTypes += type; emptyList()
                }
            suspend fun <T : Record> readType(type: KClass<T>): List<T> =
                safe(type) { readAll(hc, type, rangeOf(type)) { truncatedTypes += type } }

            // Daily totals (Health Connect de-duplicates overlapping sources in aggregates).
            val aggregateDays = if (has(StepsRecord::class) || has(ActiveCaloriesBurnedRecord::class)) safe(StepsRecord::class) {
                val metrics = buildSet {
                    if (has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
                    if (has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                }
                hc.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(stepsFrom, now), Period.ofDays(1))
                ).map { bucket ->
                    HcDaySteps(
                        bucket.startTime.toLocalDate(),
                        bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L,
                        bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    )
                }
            } else emptyList()
            val rawSteps = if (has(StepsRecord::class)) safe(StepsRecord::class) {
                readAll(hc, StepsRecord::class, rangeFrom(stepsFrom)) { stepsTruncated = true }
            }.map { HcRawSteps(it.metadata.dataOrigin.packageName, it.startTime.local(), it.count, it.endTime.local()) } else emptyList()
            // A capped raw read would rebuild days from part of their records: then trust the aggregate only.
            val rawStepsFallback = if (stepsTruncated) emptyMap() else HealthConnectMapper.dailyStepsFromRaw(rawSteps, from = stepsFrom)
            val days = HealthConnectMapper.mergeDailySteps(aggregateDays, rawStepsFallback)
                .mapNotNull { HealthConnectMapper.steps(accountId, it, now) }

            val sleep = if (has(SleepSessionRecord::class)) readType(SleepSessionRecord::class)
                .mapNotNull { HealthConnectMapper.sleep(accountId, HcSleep(it.metadata.id, it.startTime.local(), it.endTime.local())) } else emptyList()

            // Hourly rows from Health Connect's hourly averages (no record cap); raw samples only for the alerts' 2 hours.
            val heart = if (has(HeartRateRecord::class)) safe(HeartRateRecord::class) {
                hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(HeartRateRecord.BPM_AVG), rangeFrom(heartFrom), Duration.ofHours(1)))
                    .map { HcHourAvg(it.startTime.local(), it.result[HeartRateRecord.BPM_AVG] ?: 0L) }
                    .let { HealthConnectMapper.heartRateHourlyFromAggregate(accountId, it, now) }
            } else emptyList()
            val heartSamples = if (has(HeartRateRecord::class)) safe(HeartRateRecord::class) { readAll(hc, HeartRateRecord::class, rangeFrom(alertFrom)) }
                .flatMap { r -> r.samples.map { HcHeartSample(it.time.local(), it.beatsPerMinute) } } else emptyList()

            val weights = if (has(WeightRecord::class)) readType(WeightRecord::class)
                .mapNotNull { HealthConnectMapper.weight(accountId, HcWeight(it.metadata.id, it.time.local(), it.weight.inKilograms)) } else emptyList()

            val bp = if (has(BloodPressureRecord::class)) readType(BloodPressureRecord::class)
                .map { HealthConnectMapper.bloodPressure(accountId, HcBloodPressure(it.metadata.id, it.time.local(), it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury, it.metadata.dataOrigin.packageName)) } else emptyList()

            val glucose = if (has(BloodGlucoseRecord::class)) readType(BloodGlucoseRecord::class)
                .map { HealthConnectMapper.glucose(accountId, HcGlucose(it.metadata.id, it.time.local(), it.level.inMillimolesPerLiter, it.metadata.dataOrigin.packageName)) } else emptyList()

            val exercises = if (has(ExerciseSessionRecord::class)) readType(ExerciseSessionRecord::class)
                .map { HcExercise(it.metadata.id, it.startTime.local(), it.endTime.local(), it.exerciseType, it.title) } else emptyList()
            val spo2 = if (has(OxygenSaturationRecord::class)) readType(OxygenSaturationRecord::class)
                .mapNotNull { HealthConnectMapper.spo2(accountId, HcSpo2(it.metadata.id, it.time.local(), it.percentage.value, it.metadata.dataOrigin.packageName)) } else emptyList()
            val restingHr = if (has(RestingHeartRateRecord::class)) readType(RestingHeartRateRecord::class)
                .mapNotNull { HealthConnectMapper.restingHeartRate(accountId, HcRestingHr(it.metadata.id, it.time.local(), it.beatsPerMinute, it.metadata.dataOrigin.packageName)) } else emptyList()
            val temperature = if (has(BodyTemperatureRecord::class)) readType(BodyTemperatureRecord::class)
                .mapNotNull { HealthConnectMapper.temperature(accountId, HcTemperature(it.metadata.id, it.time.local(), it.temperature.inCelsius, it.metadata.dataOrigin.packageName)) } else emptyList()
            val respiration = if (has(RespiratoryRateRecord::class)) readType(RespiratoryRateRecord::class)
                .mapNotNull { HealthConnectMapper.respiratoryRate(accountId, HcRespiratory(it.metadata.id, it.time.local(), it.rate, it.metadata.dataOrigin.packageName)) } else emptyList()
            val water = if (has(HydrationRecord::class)) readType(HydrationRecord::class)
                .mapNotNull { HealthConnectMapper.hydration(accountId, HcHydration(it.metadata.id, it.startTime.local(), it.volume.inLiters)) } else emptyList()
            val workouts = exercises.mapNotNull { HealthConnectMapper.exercise(accountId, it) }


            // 3) Apply: deletions from the source apps first, then the new and edited rows.
            // Reconcile: imported rows of the span just read that Health Connect no longer has were deleted at the source
            // (covers deletions a lost or expired changes token never reported). Only for complete, successful reads.
            suspend fun stale(type: KClass<out Record>, returned: List<String>, stored: suspend () -> List<String>): List<String> =
                if (!has(type) || type in failedTypes || type in truncatedTypes) emptyList()
                else HealthConnectSyncPlan.staleIds(stored(), returned)
            fun vitalPattern(prefix: String) = "hc\\_${prefix}\\_%"
            val staleIds = buildList {
                addAll(stale(SleepSessionRecord::class, sleep.map { it.id }) { dao.importedSleepIdsSince(accountId, dayOf(SleepSessionRecord::class)) })
                addAll(stale(WeightRecord::class, weights.map { it.id }) { dao.importedWeightIdsSince(accountId, dayOf(WeightRecord::class)) })
                addAll(stale(BloodPressureRecord::class, bp.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("bp"), dayOf(BloodPressureRecord::class)) })
                addAll(stale(BloodGlucoseRecord::class, glucose.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("glu"), dayOf(BloodGlucoseRecord::class)) })
                addAll(stale(OxygenSaturationRecord::class, spo2.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("spo2"), dayOf(OxygenSaturationRecord::class)) })
                addAll(stale(RestingHeartRateRecord::class, restingHr.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("rhr"), dayOf(RestingHeartRateRecord::class)) })
                addAll(stale(BodyTemperatureRecord::class, temperature.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("temp"), dayOf(BodyTemperatureRecord::class)) })
                addAll(stale(RespiratoryRateRecord::class, respiration.map { it.id }) { dao.importedVitalIdsSince(accountId, vitalPattern("resp"), dayOf(RespiratoryRateRecord::class)) })
                addAll(stale(HydrationRecord::class, water.map { it.id }) { dao.importedWaterIdsSince(accountId, dayOf(HydrationRecord::class)) })
                addAll(stale(ExerciseSessionRecord::class, workouts.map { it.id }) {
                    dao.importedWorkoutIdsSince(accountId, dayOf(ExerciseSessionRecord::class).toLocalDate().toString())
                })
            }
            dao.applySync(
                accountId, plan.deleteIds + staleIds,
                stepsFromDate = stepsFrom.toLocalDate().toString().takeIf { has(StepsRecord::class) && StepsRecord::class !in failedTypes && !stepsTruncated },
                heartFrom = heartFrom.takeIf { has(HeartRateRecord::class) && HeartRateRecord::class !in failedTypes },
                activities = days + workouts, sleep = sleep, weights = weights,
                vitals = heart + bp + glucose + spo2 + restingHr + temperature + respiration, water = water
            )
            prefs.edit().putString(KEY_LAST_SYNC, now.toString()).apply()
            // The token always moves on; a type that failed is remembered and re-read from where it was owed.
            storeToken()
            savePending(HealthConnectSyncPlan.pending(pendingBefore, failedTypes.map { perm(it) }.toSet(), grantedPerms, from))
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

    private class ChangeFeed(val changes: List<HcChange>, val nextToken: String?, val expired: Boolean, val failed: Boolean = false)

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
            // A temporary error (rate limit, provider busy) is not an expiry: keep the token and try it again next time.
            return ChangeFeed(emptyList(), null, expired = false, failed = true)
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

    private fun perm(type: KClass<out Record>): String = HealthPermission.getReadPermission(type)

    private fun loadPending(): HcPending? {
        val types = prefs.getString(KEY_PENDING_TYPES, null)?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val from = prefs.getString(KEY_PENDING_FROM, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        return if (types.isEmpty() || from == null) null else HcPending(types, from)
    }

    private fun savePending(p: HcPending?) {
        if (p == null) prefs.edit().remove(KEY_PENDING_TYPES).remove(KEY_PENDING_FROM).apply()
        else prefs.edit().putString(KEY_PENDING_TYPES, p.types.joinToString(",")).putString(KEY_PENDING_FROM, p.from.toString()).apply()
    }

    /** Reads up to [MAX_RECORDS_PER_TYPE] records; [onTruncated] runs when more were available. */
    private suspend fun <T : Record> readAll(hc: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter, onTruncated: () -> Unit = {}): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = hc.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null && out.size < MAX_RECORDS_PER_TYPE)
        if (token != null) onTruncated()
        return out
    }

    /** Stops syncing, revokes access and (optionally) removes everything that was imported. */
    override suspend fun disconnect(removeImported: Boolean) {
        prefs.edit().remove(KEY_ENABLED).remove(KEY_LAST_SYNC).remove(KEY_TOKEN).remove(KEY_TOKEN_PERMS)
            .remove(KEY_PENDING_TYPES).remove(KEY_PENDING_FROM).apply()
        runCatching { client?.permissionController?.revokeAllPermissions() }
        if (removeImported) dao.deleteImported(accountManager.getCurrentAccountId())
        mutable.update { HcState(availability = availability()) }
        refresh()
    }

    override fun clearError() = mutable.update { it.copy(error = null) }
}
