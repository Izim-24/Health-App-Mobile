package com.healthapp.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.usecase.ReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules each reminder as a unique one-shot WorkManager job at its next trigger time.
 * [ReminderWorker] posts the notification and enqueues the following occurrence.
 * WorkManager persists jobs across reboots, so no boot receiver is needed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule(reminder: Reminder, now: LocalDateTime = LocalDateTime.now()) {
        if (!reminder.isEnabled) {
            cancel(reminder.id)
            return
        }
        val time = ReminderSchedule.parseTime(reminder.reminderTime) ?: return
        val next = ReminderSchedule.nextTrigger(now, time, reminder.daysOfWeek)
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reminderId))
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "health_reminder"
        fun workName(id: String) = "reminder_$id"
    }
}
