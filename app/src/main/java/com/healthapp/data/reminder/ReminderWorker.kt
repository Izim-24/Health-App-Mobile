package com.healthapp.data.reminder

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
        notify(applicationContext, reminder)
        // Queue the next occurrence (+1 minute guard so we never re-fire the same slot).
        deps.scheduler().schedule(reminder, java.time.LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val CHANNEL_ID = "health_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Nhắc nhở sức khỏe", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Nhắc uống nước, vận động, ngủ, bữa ăn và thói quen"
                    }
                )
            }
        }

        fun bodyFor(type: ReminderType): String = when (type) {
            ReminderType.WATER -> "Đến giờ uống một cốc nước rồi."
            ReminderType.EXERCISE -> "Đứng dậy và vận động vài phút nhé."
            ReminderType.SLEEP -> "Chuẩn bị đi ngủ để có giấc ngủ đủ giấc."
            ReminderType.HABIT -> "Đừng quên hoàn thành thói quen hôm nay."
            ReminderType.MEAL -> "Đến giờ ăn — nhớ ghi lại bữa ăn của bạn."
        }

        fun notify(context: Context, reminder: Reminder) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, reminder.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(reminder.title)
                .setContentText(bodyFor(reminder.reminderType))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        }
    }
}
