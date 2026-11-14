package com.healthapp.data.repository

import com.healthapp.data.local.dao.ReminderDao
import com.healthapp.data.local.entity.ReminderEntity
import com.healthapp.data.reminder.ReminderScheduler
import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.model.ReminderType
import com.healthapp.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val accountManager: AccountManager,
    private val scheduler: ReminderScheduler
) : ReminderRepository {

    override suspend fun createReminder(reminder: Reminder): Result<Reminder> = runCatching {
        reminderDao.insertReminder(reminder.toEntity(accountManager.getCurrentAccountId()))
        scheduler.schedule(reminder)
        reminder
    }

    override suspend fun updateReminder(reminder: Reminder): Result<Unit> = runCatching {
        val existing = reminderDao.getReminderById(reminder.id)
        val accountId = existing?.accountId ?: accountManager.getCurrentAccountId()
        reminderDao.insertReminder(reminder.toEntity(accountId, existing?.createdAt))
        if (reminder.isEnabled) scheduler.schedule(reminder) else scheduler.cancel(reminder.id)
    }

    override suspend fun deleteReminder(reminderId: String): Result<Unit> = runCatching {
        reminderDao.deleteReminder(reminderId)
        scheduler.cancel(reminderId)
    }

    override fun observeReminders(userId: String): Flow<List<Reminder>> =
        reminderDao.observeRemindersByAccountId(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledReminders(userId: String): Result<List<Reminder>> = runCatching {
        reminderDao.getEnabledReminders(userId).map { it.toDomain() }
    }

    /** Re-enqueues every enabled reminder (e.g. after app update or data restore). */
    suspend fun rescheduleAll(): Result<Unit> = runCatching {
        getEnabledReminders(accountManager.getCurrentAccountId()).getOrThrow().forEach { scheduler.schedule(it) }
    }

    companion object {
        fun encodeDays(days: List<DayOfWeek>?): String? =
            days?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

        fun decodeDays(raw: String?): List<DayOfWeek>? =
            raw?.split(',', '[', ']', '"', ' ')
                ?.mapNotNull { token -> token.takeIf { it.isNotBlank() }?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } }
                ?.takeIf { it.isNotEmpty() }
    }

    private fun Reminder.toEntity(accountId: String, created: java.time.LocalDateTime? = null) = ReminderEntity(
        id = id,
        accountId = accountId,
        title = title,
        reminderType = reminderType.name,
        reminderTime = reminderTime,
        isEnabled = isEnabled,
        daysOfWeek = encodeDays(daysOfWeek),
        createdAt = created ?: createdAt
    )

    private fun ReminderEntity.toDomain() = Reminder(
        id = id,
        title = title,
        reminderType = runCatching { ReminderType.valueOf(reminderType) }.getOrDefault(ReminderType.HABIT),
        reminderTime = reminderTime,
        isEnabled = isEnabled,
        daysOfWeek = decodeDays(daysOfWeek),
        createdAt = createdAt
    )
}
