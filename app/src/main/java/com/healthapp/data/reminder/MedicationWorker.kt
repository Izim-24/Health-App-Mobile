package com.healthapp.data.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.healthapp.domain.repository.MedicationRepository
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.presentation.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MedicationDeps {
    fun medicationRepository(): MedicationRepository
    fun medicationScheduler(): MedicationScheduler
}

class MedicationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_MED_ID) ?: return Result.failure()
        val time = inputData.getString(KEY_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, MedicationDeps::class.java)
        val med = deps.medicationRepository().getMedication(id) ?: return Result.success()
        if (!med.isActive || time !in med.times) return Result.success()

        notify(applicationContext, med, LocalDate.now(), time)
        deps.medicationScheduler().scheduleSlot(med, time, LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    companion object {
        const val KEY_MED_ID = "med_id"
        const val KEY_TIME = "time"

        fun notificationId(medId: String, time: LocalTime) = "$medId|$time".hashCode()

        fun notify(context: Context, med: Medication, date: LocalDate, time: LocalTime) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ReminderWorker.ensureChannel(context)
            val notifId = notificationId(med.id, time)
            val open = PendingIntent.getActivity(
                context, notifId,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            fun action(status: DoseStatus) = PendingIntent.getBroadcast(
                context, notifId * 31 + status.ordinal,
                Intent(context, MedicationActionReceiver::class.java)
                    .putExtra(KEY_MED_ID, med.id)
                    .putExtra(KEY_TIME, time.toString())
                    .putExtra(MedicationActionReceiver.KEY_DATE, date.toString())
                    .putExtra(MedicationActionReceiver.KEY_STATUS, status.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val res = com.healthapp.core.AppLocale.localized(context)
            val body = listOfNotNull(med.dosage.takeIf { it.isNotBlank() }, med.instructions?.takeIf { it.isNotBlank() }).joinToString(" · ")
            val notification = NotificationCompat.Builder(context, ReminderWorker.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(res.getString(R.string.notif_med_title, med.name))
                .setContentText(body.ifBlank { res.getString(R.string.notif_med_dose_at, time.toString()) })
                .setContentIntent(open)
                .addAction(0, res.getString(R.string.action_taken), action(DoseStatus.TAKEN))
                .addAction(0, res.getString(R.string.action_skip_dose), action(DoseStatus.SKIPPED))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}

/** One-time "running low" notice, posted when supply drops to [MedicationSchedule.LOW_SUPPLY_DAYS] days or fewer. */
internal fun MedicationWorker.Companion.notifyLowSupply(context: Context, med: Medication, pills: Int, days: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    ReminderWorker.ensureChannel(context)
    val res = com.healthapp.core.AppLocale.localized(context)
    val open = PendingIntent.getActivity(
        context, ("refill_" + med.id).hashCode(),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, ReminderWorker.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(res.getString(R.string.notif_low_title, med.name))
        .setContentText(res.getString(R.string.notif_low_body, pills, days))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()
    NotificationManagerCompat.from(context).notify(("refill_" + med.id).hashCode(), notification)
}

/** Handles "Taken" / "Skip" taps on a dose notification without opening the app. */
class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(MedicationWorker.KEY_MED_ID) ?: return
        val time = intent.getStringExtra(MedicationWorker.KEY_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return
        val date = intent.getStringExtra(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val status = intent.getStringExtra(KEY_STATUS)?.let { runCatching { DoseStatus.valueOf(it) }.getOrNull() } ?: return
        val pending = goAsync()
        val repo = EntryPointAccessors.fromApplication(context.applicationContext, MedicationDeps::class.java).medicationRepository()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.logDose(medId, date, time, status)
                NotificationManagerCompat.from(context).cancel(MedicationWorker.notificationId(medId, time))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_DATE = "date"
        const val KEY_STATUS = "status"
    }
}
