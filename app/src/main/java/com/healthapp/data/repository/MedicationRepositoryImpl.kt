package com.healthapp.data.repository

import com.healthapp.data.local.dao.MedicationDao
import com.healthapp.data.local.entity.MedicationEntity
import com.healthapp.data.local.entity.MedicationLogEntity
import com.healthapp.data.reminder.MedicationScheduler
import com.healthapp.data.reminder.notifyLowSupply
import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.domain.repository.MedicationRepository
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
class MedicationRepositoryImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: MedicationDao,
    private val accountManager: AccountManager,
    private val scheduler: MedicationScheduler
) : MedicationRepository {
    override suspend fun observeMedications(): Flow<List<Medication>> =
        dao.observeMedications(accountManager.getCurrentAccountId()).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun observeLogs(from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        dao.observeLogs(accountManager.getCurrentAccountId(), from.toString(), to.toString())
            .map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getMedication(id: String): Medication? = dao.getMedication(id)?.toDomain()

    override suspend fun save(medication: Medication): Result<Unit> = runCatching {
        val existing = dao.getMedication(medication.id)
        val previous = existing?.toDomain()
        val accountId = existing?.accountId ?: accountManager.getCurrentAccountId()
        dao.upsertMedication(medication.toEntity(accountId))
        previous?.let { scheduler.cancel(it) } // times may have changed
        scheduler.schedule(medication)
    }

    override suspend fun setActive(medication: Medication, active: Boolean): Result<Unit> = save(medication.copy(isActive = active))

    override suspend fun delete(medication: Medication): Result<Unit> = runCatching {
        scheduler.cancel(medication)
        dao.deleteMedication(medication.id)
    }

    /** Records TAKEN/SKIPPED for a dose slot; null clears the log (undo). */
    override suspend fun logDose(medicationId: String, date: LocalDate, time: LocalTime, status: DoseStatus?): Result<Unit> = runCatching {
        val t = MedicationSchedule.formatTime(time)
        val wasTaken = dao.getLog(medicationId, date.toString(), t)?.status == DoseStatus.TAKEN.name
        adjustSupply(medicationId, wasTaken, nowTaken = status == DoseStatus.TAKEN)
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

    /** Updates pills on hand for a dose change; posts one "running low" notification when supply crosses the limit. */
    private suspend fun adjustSupply(medicationId: String, wasTaken: Boolean, nowTaken: Boolean) {
        val med = dao.getMedication(medicationId)?.toDomain() ?: return
        val newPills = MedicationSchedule.pillsAfter(med, wasTaken, nowTaken) ?: return
        if (newPills == med.pillsLeft) return
        dao.setPillsLeft(medicationId, newPills)
        val before = MedicationSchedule.supply(med)
        val after = MedicationSchedule.supply(med.copy(pillsLeft = newPills))
        if (after != null && after.low && before?.low != true) {
            com.healthapp.data.reminder.MedicationWorker.notifyLowSupply(context, med, after.pillsLeft, after.daysLeft)
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
                createdAt = createdAt,
                pillsLeft = pillsLeft,
                pillsPerDose = pillsPerDose
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
            createdAt = createdAt,
            pillsLeft = pillsLeft,
            pillsPerDose = pillsPerDose.coerceAtLeast(1)
        )

        fun MedicationLogEntity.toDomain(): DoseLog? = runCatching {
            DoseLog(id, medicationId, LocalDate.parse(date), LocalTime.parse(time), DoseStatus.valueOf(status), recordedAt)
        }.getOrNull()
    }
}
