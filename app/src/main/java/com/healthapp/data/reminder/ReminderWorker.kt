package com.healthapp.data.reminder

import kotlinx.coroutines.flow.first
import com.healthapp.domain.usecase.WaterPace
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthapp.R
import com.healthapp.data.local.HealthDatabase
import com.healthapp.data.repository.ReminderRepositoryImpl
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.model.ReminderType
import com.healthapp.presentation.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun database(): HealthDatabase
        fun scheduler(): ReminderScheduler
        fun waterRepository(): com.healthapp.domain.repository.WaterRepository
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val entity = deps.database().reminderDao().getReminderById(id) ?: return Result.success()
        if (!entity.isEnabled) return Result.success()

        val reminder = Reminder(
            id = entity.id,
            title = entity.title,
            reminderType = runCatching { ReminderType.valueOf(entity.reminderType) }.getOrDefault(ReminderType.HABIT),
            reminderTime = entity.reminderTime,
            isEnabled = true,
            daysOfWeek = ReminderRepositoryImpl.decodeDays(entity.daysOfWeek),
            createdAt = entity.createdAt
        )
        // Smart water reminders: skip when on pace for the daily goal, otherwise say how far behind.
        var body: String? = null
        if (reminder.reminderType == ReminderType.WATER) {
            val water = deps.waterRepository()
            val goal = runCatching { water.getDailyGoal(entity.accountId) }.getOrDefault(0)
            val intake = runCatching {
                water.observeWaterLogsForDate(entity.accountId, java.time.LocalDate.now().toString()).first().sumOf { it.amountMl }
            }.getOrDefault(0)
            val now = java.time.LocalTime.now()
            if (!WaterPace.shouldRemind(intake, goal, now)) {
                deps.scheduler().schedule(reminder, java.time.LocalDateTime.now().plusMinutes(1))
                return Result.success()
            }
            WaterPace.behindBy(intake, goal, now)?.let { behind ->
                body = com.healthapp.core.AppLocale.localized(applicationContext).getString(R.string.notif_water_behind, intake, goal, behind)
            }
        }
        notify(applicationContext, reminder, body)
        // Queue the next occurrence (+1 minute guard so we never re-fire the same slot).
        deps.scheduler().schedule(reminder, java.time.LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val CHANNEL_ID = "health_reminders"

        fun ensureChannel(context: Context) {
            // minSdk is 26, so notification channels always exist.
            val manager = context.getSystemService(NotificationManager::class.java)
            val res = com.healthapp.core.AppLocale.localized(context)
            // Re-creating the channel updates its name if the language changed.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = res.getString(R.string.channel_reminders_desc)
                }
            )
        }

        fun bodyFor(context: Context, type: ReminderType): String = com.healthapp.core.AppLocale.localized(context).getString(
            when (type) {
                ReminderType.WATER -> R.string.notif_water
                ReminderType.EXERCISE -> R.string.notif_exercise
                ReminderType.SLEEP -> R.string.notif_sleep
                ReminderType.HABIT -> R.string.notif_habit
                ReminderType.MEAL -> R.string.notif_meal
            }
        )

        fun notify(context: Context, reminder: Reminder, body: String? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, reminder.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(reminder.title)
                .setContentText(body ?: bodyFor(context, reminder.reminderType))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        }
    }
}
