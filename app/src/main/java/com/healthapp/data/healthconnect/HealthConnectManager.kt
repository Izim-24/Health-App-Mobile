package com.healthapp.data.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthapp.data.local.dao.HealthConnectDao
import com.healthapp.data.repository.AccountManager
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

enum class HcAvailability { AVAILABLE, NEEDS_UPDATE, NOT_SUPPORTED }

data class HcSyncSummary(val days: Int, val sleep: Int, val heartRate: Int, val weight: Int, val bloodPressure: Int, val glucose: Int) {
    val total get() = days + sleep + heartRate + weight + bloodPressure + glucose
}

data class HcState(
    val availability: HcAvailability = HcAvailability.NOT_SUPPORTED,
    val enabled: Boolean = false,
    val granted: Set<String> = emptySet(),
    val syncing: Boolean = false,
    val lastSync: LocalDateTime? = null,
    val lastSummary: HcSyncSummary? = null,
    val error: String? = null
) {
    val connected get() = enabled && granted.isNotEmpty()
}

/**
 * Read-only import from Health Connect into the local Room database.
 * Data never leaves the device; nothing is written back to Health Connect.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HealthConnectDao,
    private val accountManager: AccountManager
) {
    companion object {
        val READ_TYPES: List<KClass<out Record>> = listOf(
            StepsRecord::class, ActiveCaloriesBurnedRecord::class, SleepSessionRecord::class,
            HeartRateRecord::class, WeightRecord::class, BloodPressureRecord::class, BloodGlucoseRecord::class
        )
        val PERMISSIONS: Set<String> = READ_TYPES.map { HealthPermission.getReadPermission(it) }.toSet()
        private const val PREFS = "health_connect"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val AUTO_SYNC_MIN_INTERVAL_MIN = 15L
        private const val MAX_RECORDS_PER_TYPE = 5000
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(HcState())
    val state: StateFlow<HcState> = mutable.asStateFlow()

    private val client: HealthConnectClient? get() =
        if (availability() == HcAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NEEDS_UPDATE
        else -> HcAvailability.NOT_SUPPORTED
    }

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Re-reads availability, granted permissions and stored sync info. */
    suspend fun refresh() {
        val granted = runCatching { client?.permissionController?.getGrantedPermissions()?.intersect(PERMISSIONS) }.getOrNull().orEmpty()
        mutable.update {
            it.copy(
                availability = availability(),
                enabled = prefs.getBoolean(KEY_ENABLED, false),
                granted = granted,
                lastSync = prefs.getString(KEY_LAST_SYNC, null)?.let { s -> runCatching { LocalDateTime.parse(s) }.getOrNull() }
            )
        }
    }

    /** Called with the permissions the user granted in the system dialog. */
    suspend fun onPermissionsResult(granted: Set<String>) {
        if (granted.intersect(PERMISSIONS).isNotEmpty()) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            refresh()
            sync(force = true)
        } else {
            refresh()
            mutable.update { it.copy(error = "Chưa cấp quyền đọc dữ liệu nào trong Health Connect.") }
        }
    }

    /** Foreground auto-sync: runs only when connected and the last sync is older than 15 minutes. */
    suspend fun syncIfDue() {
        refresh()
        val s = state.value
        if (!s.connected) return
        val last = s.lastSync
        if (last != null && last.isAfter(LocalDateTime.now().minusMinutes(AUTO_SYNC_MIN_INTERVAL_MIN))) return
        sync(force = false)
    }

    suspend fun sync(force: Boolean): Result<HcSyncSummary> = mutex.withLock {
        val hc = client ?: return Result.failure(IllegalStateException("Health Connect không khả dụng trên thiết bị này."))
        mutable.update { it.copy(syncing = true, error = null) }
        val result = runCatching {
            val granted = hc.permissionController.getGrantedPermissions()
            fun has(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

            val accountId = accountManager.getCurrentAccountId()
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            val from = HealthConnectMapper.syncStart(if (force) null else state.value.lastSync, now)
            val range = TimeRangeFilter.between(from.atZone(zone).toInstant(), now.atZone(zone).toInstant())
            fun Instant.local() = LocalDateTime.ofInstant(this, zone)

            // Daily totals (Health Connect de-duplicates overlapping sources in aggregates).
            val aggregateDays = if (has(StepsRecord::class) || has(ActiveCaloriesBurnedRecord::class)) {
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
            val rawStepsFallback = if (has(StepsRecord::class)) HealthConnectMapper.dailyStepsFromRaw(
                readAll(hc, StepsRecord::class, range).map { HcRawSteps(it.metadata.dataOrigin.packageName, it.startTime.local(), it.count) }
            ) else emptyMap()
            val days = HealthConnectMapper.mergeDailySteps(aggregateDays, rawStepsFallback)
                .mapNotNull { HealthConnectMapper.steps(accountId, it, now) }

            val sleep = if (has(SleepSessionRecord::class)) readAll(hc, SleepSessionRecord::class, range)
                .mapNotNull { HealthConnectMapper.sleep(accountId, HcSleep(it.metadata.id, it.startTime.local(), it.endTime.local())) } else emptyList()

            val heart = if (has(HeartRateRecord::class)) HealthConnectMapper.heartRateHourly(accountId,
                readAll(hc, HeartRateRecord::class, range).flatMap { r -> r.samples.map { HcHeartSample(it.time.local(), it.beatsPerMinute) } }
            ) else emptyList()

            val weights = if (has(WeightRecord::class)) readAll(hc, WeightRecord::class, range)
                .mapNotNull { HealthConnectMapper.weight(accountId, HcWeight(it.metadata.id, it.time.local(), it.weight.inKilograms)) } else emptyList()

            val bp = if (has(BloodPressureRecord::class)) readAll(hc, BloodPressureRecord::class, range)
                .map { HealthConnectMapper.bloodPressure(accountId, HcBloodPressure(it.metadata.id, it.time.local(), it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)) } else emptyList()

            val glucose = if (has(BloodGlucoseRecord::class)) readAll(hc, BloodGlucoseRecord::class, range)
                .map { HealthConnectMapper.glucose(accountId, HcGlucose(it.metadata.id, it.time.local(), it.level.inMillimolesPerLiter)) } else emptyList()

            dao.upsertAll(days, sleep, weights, heart + bp + glucose)
            prefs.edit().putString(KEY_LAST_SYNC, now.toString()).apply()
            HcSyncSummary(days.size, sleep.size, heart.size, weights.size, bp.size, glucose.size)
        }
        mutable.update { s ->
            result.fold(
                onSuccess = { s.copy(syncing = false, lastSummary = it, lastSync = LocalDateTime.now(), error = null) },
                onFailure = { s.copy(syncing = false, error = "Đồng bộ thất bại: ${it.message}") }
            )
        }
        result
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
    suspend fun disconnect(removeImported: Boolean) {
        prefs.edit().remove(KEY_ENABLED).remove(KEY_LAST_SYNC).apply()
        runCatching { client?.permissionController?.revokeAllPermissions() }
        if (removeImported) dao.deleteImported(accountManager.getCurrentAccountId())
        mutable.update { HcState(availability = availability()) }
        refresh()
    }

    fun clearError() = mutable.update { it.copy(error = null) }
}
