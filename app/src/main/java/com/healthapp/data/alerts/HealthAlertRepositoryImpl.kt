package com.healthapp.data.alerts

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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthapp.R
import com.healthapp.core.AppLocale
import com.healthapp.domain.model.VitalType
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.HealthAlertRepository
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.repository.VitalRepository
import com.healthapp.domain.usecase.ActivityState
import com.healthapp.domain.usecase.AlertKind
import com.healthapp.domain.usecase.AlertLevel
import com.healthapp.domain.usecase.BpReading
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.domain.usecase.HrSample
import com.healthapp.domain.usecase.StepSample
import com.healthapp.domain.usecase.VitalAlert
import com.healthapp.domain.usecase.VitalAlerts
import com.healthapp.presentation.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns [VitalAlerts] results into notifications. Keeps the last 20 alerts and, for real data, when each
 * kind was last sent (SharedPreferences), so repeated Health Connect syncs of the same hours don't re-notify.
 * The simulator's cooldown lives in memory only and is reset per scenario.
 */
@Singleton
class HealthAlertRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: AccountSession,
    private val profiles: ProfileRepository,
    private val vitals: VitalRepository
) : HealthAlertRepository {

    /** Gson-friendly copy of a [VitalAlert] (kept by R8). */
    class Stored(
        val kind: String = "", val at: String = "", val value: Int = 0, val secondary: Int? = null,
        val state: String? = null, val simulated: Boolean = false
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()
    private val recentFlow = MutableStateFlow(loadRecent())
    private val simulatedSent = mutableMapOf<AlertKind, LocalDateTime>()

    override val recent: Flow<List<VitalAlert>> = recentFlow.asStateFlow()

    override suspend fun check(
        hr: List<HrSample>, steps: List<StepSample>, bp: List<BpReading>,
        now: LocalDateTime, notify: Boolean, simulated: Boolean
    ): List<VitalAlert> = mutex.withLock {
        val accountId = session.getCurrentAccountId()
        val age = profiles.getProfile(accountId)?.dateOfBirth
            ?.let { runCatching { HealthCalculations.ageOn(LocalDate.parse(it), now.toLocalDate()) }.getOrNull() }
        // Real data: add stored readings so the 7-day trend sees the whole week. Simulations stay self-contained.
        val readings = if (simulated) bp else (bp + storedBp(accountId, now)).distinctBy { Triple(it.at, it.systolic, it.diastolic) }
        val candidates = VitalAlerts.heartAlerts(hr, steps, age, now) + VitalAlerts.bpAlerts(readings, now)
        val lastSent = if (simulated) simulatedSent else loadLastSent()
        val due = VitalAlerts.dueAlerts(candidates, lastSent, now).map { it.copy(simulated = simulated) }
        if (due.isEmpty()) return@withLock due

        if (simulated) due.forEach { simulatedSent[it.kind] = now }
        else saveLastSent(lastSent + due.associate { it.kind to now })
        val updated = (due.sortedByDescending { it.level } + recentFlow.value).take(MAX_RECENT)
        recentFlow.value = updated
        saveRecent(updated)
        if (notify) due.forEach { post(it) }
        due
    }

    override fun resetSimulation() = simulatedSent.clear()

    override suspend fun clearRecent() {
        recentFlow.value = emptyList()
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private suspend fun storedBp(accountId: String, now: LocalDateTime): List<BpReading> =
        runCatching { vitals.observeAll(accountId).first() }.getOrDefault(emptyList())
            .filter { it.type == VitalType.BLOOD_PRESSURE && it.secondaryValue != null && !it.recordedAt.isBefore(now.minusDays(7)) }
            .map { BpReading(it.recordedAt, it.value.toInt(), it.secondaryValue!!.toInt()) }

    private fun post(alert: VitalAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val res = AppLocale.localized(context)
        val title = res.getString(titleRes(alert.kind))
        val body = res.getString(bodyRes(alert.kind), alert.value, alert.secondary ?: 0)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ROUTE, ROUTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_BASE + alert.kind.ordinal, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (alert.simulated) res.getString(R.string.alert_sim_prefix, title) else title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(if (alert.level == AlertLevel.URGENT) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + alert.kind.ordinal, notification)
    }

    private fun loadRecent(): List<VitalAlert> = runCatching {
        val type = object : TypeToken<List<Stored>>() {}.type
        gson.fromJson<List<Stored>>(prefs.getString(KEY_RECENT, null) ?: return emptyList(), type).mapNotNull { s ->
            runCatching {
                VitalAlert(AlertKind.valueOf(s.kind), LocalDateTime.parse(s.at), s.value, s.secondary,
                    s.state?.let { ActivityState.valueOf(it) }, s.simulated)
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun saveRecent(list: List<VitalAlert>) {
        val stored = list.map { Stored(it.kind.name, it.at.toString(), it.value, it.secondary, it.state?.name, it.simulated) }
        prefs.edit().putString(KEY_RECENT, gson.toJson(stored)).apply()
    }

    private fun loadLastSent(): Map<AlertKind, LocalDateTime> = AlertKind.entries.mapNotNull { k ->
        prefs.getString(KEY_SENT + k.name, null)?.let { runCatching { k to LocalDateTime.parse(it) }.getOrNull() }
    }.toMap()

    private fun saveLastSent(map: Map<AlertKind, LocalDateTime>) {
        val edit = prefs.edit()
        map.forEach { (k, t) -> edit.putString(KEY_SENT + k.name, t.toString()) }
        edit.apply()
    }

    companion object {
        const val CHANNEL_ID = "health_alerts"
        const val ROUTE = "health_alerts"
        private const val PREFS = "health_alerts"
        private const val KEY_RECENT = "recent"
        private const val KEY_SENT = "sent_"
        private const val MAX_RECENT = 20
        private const val NOTIFICATION_BASE = 7300

        fun ensureChannel(context: Context) {
            val res = AppLocale.localized(context)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, res.getString(R.string.channel_health_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = res.getString(R.string.channel_health_alerts_desc)
                }
            )
        }

        fun titleRes(kind: AlertKind): Int = when (kind) {
            AlertKind.REST_TACHY -> R.string.alert_title_rest_tachy
            AlertKind.REST_TACHY_SEVERE -> R.string.alert_title_rest_tachy_severe
            AlertKind.REST_BRADY -> R.string.alert_title_rest_brady
            AlertKind.OVER_EXERTION -> R.string.alert_title_over_exertion
            AlertKind.ABOVE_MAX_HR -> R.string.alert_title_above_max_hr
            AlertKind.BP_CRISIS -> R.string.alert_title_bp_crisis
            AlertKind.BP_HIGH -> R.string.alert_title_bp_high
            AlertKind.BP_SUSTAINED -> R.string.alert_title_bp_sustained
        }

        /** Every body takes (value, secondary); HR bodies that don't need the second number ignore it. */
        fun bodyRes(kind: AlertKind): Int = when (kind) {
            AlertKind.REST_TACHY -> R.string.alert_body_rest_tachy
            AlertKind.REST_TACHY_SEVERE -> R.string.alert_body_rest_tachy_severe
            AlertKind.REST_BRADY -> R.string.alert_body_rest_brady
            AlertKind.OVER_EXERTION -> R.string.alert_body_over_exertion
            AlertKind.ABOVE_MAX_HR -> R.string.alert_body_above_max_hr
            AlertKind.BP_CRISIS -> R.string.alert_body_bp_crisis
            AlertKind.BP_HIGH -> R.string.alert_body_bp_high
            AlertKind.BP_SUSTAINED -> R.string.alert_body_bp_sustained
        }
    }
}
