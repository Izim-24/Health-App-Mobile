package com.healthapp.data.healthconnect

import java.time.LocalDateTime

/** What Health Connect reported since the last sync (from its changes token). */
sealed interface HcChange {
    /** A record was added or edited; [time] = when it happened (start time for intervals). */
    data class Upsert(val time: LocalDateTime) : HcChange
    /** A record was deleted in the source app. */
    data class Deletion(val recordId: String) : HcChange
}

/** [skip]: nothing new; otherwise read every granted type from [from], then delete [deleteIds]. */
data class SyncPlan(val skip: Boolean, val from: LocalDateTime?, val deleteIds: List<String>)

/**
 * Decides how much to read on a sync. With a valid changes token only the changed span is re-read (from the start of
 * the earliest changed day, because steps are daily totals, but at least the last 2 hours, which the health alerts
 * need). Without a token, or when it expired, it falls back to the full window of [HealthConnectMapper.syncStart].
 */
object HealthConnectSyncPlan {
    /** Row prefixes whose ids end with the Health Connect record uid (see HealthConnectMapper). */
    val UID_PREFIXES = listOf("sleep", "w", "bp", "glu", "spo2", "rhr", "temp", "resp", "water", "ex")

    private const val ALERT_HOURS = 2L

    fun plan(changes: List<HcChange>?, tokenExpired: Boolean, lastSync: LocalDateTime?, now: LocalDateTime): SyncPlan {
        if (changes == null || tokenExpired) return SyncPlan(false, HealthConnectMapper.syncStart(lastSync, now), emptyList())
        if (changes.isEmpty()) return SyncPlan(true, null, emptyList())
        val alertWindow = now.minusHours(ALERT_HOURS)
        val earliestDay = changes.filterIsInstance<HcChange.Upsert>().minOfOrNull { it.time }?.toLocalDate()?.atStartOfDay()
        val from = if (earliestDay == null || earliestDay.isAfter(alertWindow)) alertWindow else earliestDay
        val deleteIds = changes.filterIsInstance<HcChange.Deletion>().flatMap { deletionIds(it.recordId) }
        return SyncPlan(false, from, deleteIds)
    }

    /** A deleted record could be any uid-keyed type, so every candidate row id is listed. */
    fun deletionIds(recordId: String): List<String> = UID_PREFIXES.map { "${HealthConnectMapper.ID_PREFIX}${it}_$recordId" }
}
