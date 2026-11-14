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

/** Bulk upserts for imported rows, and removal of rows whose id starts with "hc_". */
@Dao
interface HealthConnectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertActivities(rows: List<ActivityLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSleep(rows: List<SleepLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWeights(rows: List<WeightLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVitals(rows: List<VitalLogEntity>)

    @Transaction
    suspend fun upsertAll(activities: List<ActivityLogEntity>, sleep: List<SleepLogEntity>, weights: List<WeightLogEntity>, vitals: List<VitalLogEntity>) {
        upsertActivities(activities); upsertSleep(sleep); upsertWeights(weights); upsertVitals(vitals)
    }

    @Query("DELETE FROM activity_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteActivities(accountId: String)
    @Query("DELETE FROM sleep_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteSleep(accountId: String)
    @Query("DELETE FROM weight_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteWeights(accountId: String)
    @Query("DELETE FROM vital_logs WHERE accountId = :accountId AND substr(id, 1, 3) = 'hc_'") suspend fun deleteVitals(accountId: String)

    @Transaction
    suspend fun deleteImported(accountId: String) {
        deleteActivities(accountId); deleteSleep(accountId); deleteWeights(accountId); deleteVitals(accountId)
    }
}
