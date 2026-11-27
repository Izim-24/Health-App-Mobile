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

    /** [newTypesGranted]: the user allowed data types that were never imported, so the full 30 days are read. */
    fun plan(
        changes: List<HcChange>?, tokenExpired: Boolean, lastSync: LocalDateTime?, now: LocalDateTime,
        newTypesGranted: Boolean = false
    ): SyncPlan {
        if (changes == null || tokenExpired) {
            return SyncPlan(false, HealthConnectMapper.syncStart(if (newTypesGranted) null else lastSync, now), emptyList())
        }
        if (changes.isEmpty()) return SyncPlan(true, null, emptyList())
        val alertWindow = now.minusHours(ALERT_HOURS)
        val earliestDay = changes.filterIsInstance<HcChange.Upsert>().minOfOrNull { it.time }?.toLocalDate()?.atStartOfDay()
        val from = if (earliestDay == null || earliestDay.isAfter(alertWindow)) alertWindow else earliestDay
        val deleteIds = changes.filterIsInstance<HcChange.Deletion>().flatMap { deletionIds(it.recordId) }
        // A deletion doesn't say when the record was, and hourly heart rate / daily steps are built from many records:
        // re-read the whole 30 days so those totals are rebuilt without the deleted data.
        if (deleteIds.isNotEmpty()) return SyncPlan(false, HealthConnectMapper.syncStart(null, now), deleteIds)
        return SyncPlan(false, from, deleteIds)
    }

    /** Daily step totals are recomputed for whole days, so their read starts at midnight. */
    fun stepsFrom(from: LocalDateTime): LocalDateTime = from.toLocalDate().atStartOfDay()

    /** Hourly heart-rate averages are recomputed for whole hours. */
    fun heartFrom(from: LocalDateTime): LocalDateTime = from.truncatedTo(java.time.temporal.ChronoUnit.HOURS)

    /** A deleted record could be any uid-keyed type, so every candidate row id is listed. */
    fun deletionIds(recordId: String): List<String> = UID_PREFIXES.map { "${HealthConnectMapper.ID_PREFIX}${it}_$recordId" }
}
