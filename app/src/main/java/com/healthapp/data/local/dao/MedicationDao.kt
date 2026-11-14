package com.healthapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.healthapp.data.local.entity.MedicationEntity
import com.healthapp.data.local.entity.MedicationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE accountId = :accountId ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    fun observeMedications(accountId: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE accountId = :accountId AND isActive = 1")
    suspend fun getActiveMedications(accountId: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: String): MedicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(medication: MedicationEntity)

    @Query("DELETE FROM medication_logs WHERE medicationId = :id")
    suspend fun deleteLogsFor(id: String)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedicationRow(id: String)

    @Transaction
    suspend fun deleteMedication(id: String) {
        deleteLogsFor(id)
        deleteMedicationRow(id)
    }

    @Query("""
        SELECT l.* FROM medication_logs l INNER JOIN medications m ON m.id = l.medicationId
        WHERE m.accountId = :accountId AND l.date BETWEEN :from AND :to
    """)
    fun observeLogs(accountId: String, from: String, to: String): Flow<List<MedicationLogEntity>>

    /** Replaces any existing log for the same (medicationId, date, time) slot via the unique index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: MedicationLogEntity)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId AND date = :date AND time = :time")
    suspend fun deleteLog(medicationId: String, date: String, time: String)
}
