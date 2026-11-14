package com.healthapp.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.healthapp.domain.model.Medication
import com.healthapp.domain.usecase.MedicationSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One unique WorkManager job per (medication, dose time); [MedicationWorker] re-queues the next day's slot. */
@Singleton
class MedicationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(med: Medication, now: LocalDateTime = LocalDateTime.now()) {
        med.times.forEach { scheduleSlot(med, it, now) }
    }

    fun scheduleSlot(med: Medication, time: LocalTime, now: LocalDateTime = LocalDateTime.now()) {
        val name = workName(med.id, time)
        val next = MedicationSchedule.nextDose(med, time, now)
        if (next == null) {
            WorkManager.getInstance(context).cancelUniqueWork(name)
            return
        }
        val request = OneTimeWorkRequestBuilder<MedicationWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                MedicationWorker.KEY_MED_ID to med.id,
                MedicationWorker.KEY_TIME to MedicationSchedule.formatTime(time)
            ))
            .addTag(ReminderScheduler.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(med: Medication) {
        med.times.forEach { WorkManager.getInstance(context).cancelUniqueWork(workName(med.id, it)) }
    }

    companion object {
        fun workName(id: String, time: LocalTime) = "med_${id}_${MedicationSchedule.formatTime(time)}"
    }
}
