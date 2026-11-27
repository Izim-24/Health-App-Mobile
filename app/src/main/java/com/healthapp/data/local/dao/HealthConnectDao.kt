package com.healthapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.healthapp.data.local.entity.ActivityLogEntity
import com.healthapp.data.local.entity.SleepLogEntity
import com.healthapp.data.local.entity.VitalLogEntity
import com.healthapp.data.local.entity.WeightLogEntity
import com.healthapp.data.local.entity.WaterLogEntity

/** Bulk upserts for imported rows, and removal of rows whose id starts with "hc_". */
@Dao
interface HealthConnectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertActivities(rows: List<ActivityLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSleep(rows: List<SleepLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWeights(rows: List<WeightLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVitals(rows: List<VitalLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWater(rows: List<WaterLogEntity>)

    @Transaction
    suspend fun upsertAll(
        activities: List<ActivityLogEntity>, sleep: List<SleepLogEntity>, weights: List<WeightLogEntity>,
        vitals: List<VitalLogEntity>, water: List<WaterLogEntity> = emptyList()
    ) {
        upsertActivities(activities); upsertSleep(sleep); upsertWeights(weights); upsertVitals(vitals); upsertWater(water)
    }

    @Query("DELETE FROM activity_logs WHERE id IN (:ids)") suspend fun deleteActivitiesByIds(ids: List<String>)
    @Query("DELETE FROM sleep_logs WHERE id IN (:ids)") suspend fun deleteSleepByIds(ids: List<String>)
    @Query("DELETE FROM weight_logs WHERE id IN (:ids)") suspend fun deleteWeightsByIds(ids: List<String>)
    @Query("DELETE FROM vital_logs WHERE id IN (:ids)") suspend fun deleteVitalsByIds(ids: List<String>)
    @Query("DELETE FROM water_logs WHERE id IN (:ids)") suspend fun deleteWaterByIds(ids: List<String>)

    @Query("DELETE FROM activity_logs WHERE accountId = :accountId AND id LIKE 'hc\\_day\\_%' ESCAPE '\\' AND date >= :fromDate")
    suspend fun deleteStepDaysFrom(accountId: String, fromDate: String)
    @Query("DELETE FROM vital_logs WHERE accountId = :accountId AND id LIKE 'hc\\_hr\\_%' ESCAPE '\\' AND recordedAt >= :from")
    suspend fun deleteHourlyHeartRateFrom(accountId: String, from: java.time.LocalDateTime)

    /**
     * One sync in one transaction: deletions from the source apps, then the re-read daily steps / hourly heart rate
     * replace what was stored for the same span (so totals lose deleted records), then the new rows.
     */
    @Transaction
    suspend fun applySync(
        accountId: String, deleteIds: List<String>, stepsFromDate: String?, heartFrom: java.time.LocalDateTime?,
        activities: List<ActivityLogEntity>, sleep: List<SleepLogEntity>, weights: List<WeightLogEntity>,
        vitals: List<VitalLogEntity>, water: List<WaterLogEntity>
    ) {
        if (deleteIds.isNotEmpty()) deleteByIds(deleteIds)
        stepsFromDate?.let { deleteStepDaysFrom(accountId, it) }
        heartFrom?.let { deleteHourlyHeartRateFrom(accountId, it) }
        upsertAll(activities, sleep, weights, vitals, water)
    }

    /** Removes rows whose Health Connect record was deleted in its source app (ids from HealthConnectSyncPlan). */
    @Transaction
    suspend fun deleteByIds(ids: List<String>) {
        ids.chunked(500).forEach { c ->
            deleteActivitiesByIds(c); deleteSleepByIds(c); deleteWeightsByIds(c); deleteVitalsByIds(c); deleteWaterByIds(c)
        }
    }

    @Query("DELETE FROM activity_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteActivities(accountId: String)
    @Query("DELETE FROM sleep_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteSleep(accountId: String)
    @Query("DELETE FROM weight_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteWeights(accountId: String)
    @Query("DELETE FROM vital_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteVitals(accountId: String)
    @Query("DELETE FROM water_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteWater(accountId: String)

    @Transaction
    suspend fun deleteImported(accountId: String) {
        deleteActivities(accountId); deleteSleep(accountId); deleteWeights(accountId); deleteVitals(accountId); deleteWater(accountId)
    }
}
