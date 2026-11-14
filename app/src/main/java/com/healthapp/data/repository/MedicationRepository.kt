package com.healthapp.data.repository

import com.healthapp.data.local.dao.MedicationDao
import com.healthapp.data.local.entity.MedicationEntity
import com.healthapp.data.local.entity.MedicationLogEntity
import com.healthapp.data.reminder.MedicationScheduler
import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.domain.usecase.MedicationSchedule
import com.healthapp.domain.usecase.ReminderSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val dao: MedicationDao,
    private val accountManager: AccountManager,
    private val scheduler: MedicationScheduler
) {
    suspend fun observeMedications(): Flow<List<Medication>> =
        dao.observeMedications(accountManager.getCurrentAccountId()).map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        dao.observeLogs(accountManager.getCurrentAccountId(), from.toString(), to.toString())
            .map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun getMedication(id: String): Medication? = dao.getMedication(id)?.toDomain()

    suspend fun save(medication: Medication): Result<Unit> = runCatching {
        val existing = dao.getMedication(medication.id)
        val previous = existing?.toDomain()
        val accountId = existing?.accountId ?: accountManager.getCurrentAccountId()
        dao.upsertMedication(medication.toEntity(accountId))
        previous?.let { scheduler.cancel(it) } // times may have changed
        scheduler.schedule(medication)
    }

    suspend fun setActive(medication: Medication, active: Boolean): Result<Unit> = save(medication.copy(isActive = active))

    suspend fun delete(medication: Medication): Result<Unit> = runCatching {
        scheduler.cancel(medication)
        dao.deleteMedication(medication.id)
    }

    /** Records TAKEN/SKIPPED for a dose slot; null clears the log (undo). */
    suspend fun logDose(medicationId: String, date: LocalDate, time: LocalTime, status: DoseStatus?): Result<Unit> = runCatching {
        val t = MedicationSchedule.formatTime(time)
        if (status == null) {
            dao.deleteLog(medicationId, date.toString(), t)
        } else {
            dao.upsertLog(
                MedicationLogEntity(
                    id = "$medicationId|$date|$t",
                    medicationId = medicationId,
                    date = date.toString(),
                    time = t,
                    status = status.name,
                    recordedAt = LocalDateTime.now()
                )
            )
        }
    }

    suspend fun rescheduleAll(): Result<Unit> = runCatching {
        dao.getActiveMedications(accountManager.getCurrentAccountId()).mapNotNull { it.toDomain() }.forEach { scheduler.schedule(it) }
    }

    companion object {
        fun MedicationEntity.toDomain(): Medication? = runCatching {
            Medication(
                id = id,
                name = name,
                dosage = dosage,
                instructions = instructions,
                times = MedicationSchedule.parseTimes(times) ?: emptyList(),
                daysOfWeek = ReminderRepositoryImpl.decodeDays(daysOfWeek),
                startDate = LocalDate.parse(startDate),
                endDate = endDate?.let { LocalDate.parse(it) },
                isActive = isActive,
                createdAt = createdAt
            )
        }.getOrNull()

        fun Medication.toEntity(accountId: String) = MedicationEntity(
            id = id,
            accountId = accountId,
            name = name,
            dosage = dosage,
            instructions = instructions,
            times = times.joinToString(",") { MedicationSchedule.formatTime(it) },
            daysOfWeek = ReminderRepositoryImpl.encodeDays(daysOfWeek),
            startDate = startDate.toString(),
            endDate = endDate?.toString(),
            isActive = isActive,
            createdAt = createdAt
        )

        fun MedicationLogEntity.toDomain(): DoseLog? = runCatching {
            DoseLog(id, medicationId, LocalDate.parse(date), LocalTime.parse(time), DoseStatus.valueOf(status), recordedAt)
        }.getOrNull()
    }
}
