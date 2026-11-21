package com.healthapp.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthapp.data.repository.MedicationRepositoryImpl
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
interface MedicationAlarmDeps {
    fun medicationRepositoryImpl(): MedicationRepositoryImpl
    fun medicationScheduler(): MedicationScheduler
}

/** Fired by an exact alarm at a dose time: show the notification and arm the next occurrence. */
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOSE) return
        val medId = intent.getStringExtra(MedicationWorker.KEY_MED_ID) ?: return
        val time = intent.getStringExtra(MedicationWorker.KEY_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return
        val pending = goAsync()
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, MedicationAlarmDeps::class.java)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val med = deps.medicationRepositoryImpl().getMedication(medId)
                if (med != null && med.isActive && time in med.times) {
                    MedicationWorker.notify(context, med, LocalDate.now(), time)
                    deps.medicationScheduler().scheduleSlot(med, time, LocalDateTime.now().plusMinutes(1))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val ACTION_DOSE = "com.healthapp.action.MEDICATION_DOSE" }
}

/**
 * Exact alarms do not survive a reboot, an app update or a clock change, and they must be re-armed when
 * the user grants the exact-alarm permission. Re-schedule every active medication in those cases.
 */
class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, MedicationAlarmDeps::class.java)
        CoroutineScope(Dispatchers.IO).launch {
            try { deps.medicationRepositoryImpl().rescheduleAll() } finally { pending.finish() }
        }
    }

    companion object {
        // The exact-alarm action is API 31+; it is only a string, so older devices simply never send it.
        @android.annotation.SuppressLint("InlinedApi")
        private val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
