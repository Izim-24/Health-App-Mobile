package com.healthapp.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one notification per (medication, dose time).
 *
 * - **Exact alarms** (`AlarmManager.setExactAndAllowWhileIdle`) when the user allows them: the dose
 *   reminder fires on time even in Doze. Alarms are cleared by a reboot, so [MedicationBootReceiver]
 *   reschedules them.
 * - **WorkManager** otherwise: survives reboots on its own but may be delayed by Doze/battery limits.
 *
 * Each firing posts the notification and queues the next occurrence of the same slot.
 */
@Singleton
class MedicationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Android 12+ requires the user to allow exact alarms; earlier versions always can. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(med: Medication, now: LocalDateTime = LocalDateTime.now()) {
        med.times.forEach { scheduleSlot(med, it, now) }
    }

    fun scheduleSlot(med: Medication, time: LocalTime, now: LocalDateTime = LocalDateTime.now()) {
        val next = MedicationSchedule.nextDose(med, time, now)
        if (next == null) {
            cancelSlot(med.id, time)
            return
        }
        if (canScheduleExact()) {
            // Exact path: never leave a duplicate WorkManager job behind.
            WorkManager.getInstance(context).cancelUniqueWork(workName(med.id, time))
            val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(med.id, time))
        } else {
            alarmManager.cancel(alarmIntent(med.id, time))
            val request = OneTimeWorkRequestBuilder<MedicationWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    MedicationWorker.KEY_MED_ID to med.id,
                    MedicationWorker.KEY_TIME to MedicationSchedule.formatTime(time)
                ))
                .addTag(ReminderScheduler.TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(med.id, time), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel(med: Medication) {
        med.times.forEach { cancelSlot(med.id, it) }
    }

    private fun cancelSlot(id: String, time: LocalTime) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id, time))
        alarmManager.cancel(alarmIntent(id, time))
    }

    /** Same request code + extras for schedule and cancel, so the PendingIntent matches. */
    private fun alarmIntent(medId: String, time: LocalTime): PendingIntent = PendingIntent.getBroadcast(
        context,
        workName(medId, time).hashCode(),
        Intent(context, MedicationAlarmReceiver::class.java)
            .setAction(MedicationAlarmReceiver.ACTION_DOSE)
            .putExtra(MedicationWorker.KEY_MED_ID, medId)
            .putExtra(MedicationWorker.KEY_TIME, MedicationSchedule.formatTime(time)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        fun workName(id: String, time: LocalTime) = "med_${id}_${MedicationSchedule.formatTime(time)}"
    }
}
