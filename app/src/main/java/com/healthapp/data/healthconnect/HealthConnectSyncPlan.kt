package com.healthapp.data.healthconnect

import java.time.LocalDateTime

/** What Health Connect reported since the last sync (from its changes token). */
sealed interface HcChange {
    /** A record was added or edited; [time] = when it happened (start time for intervals). */
    data class Upsert(val time: LocalDateTime) : HcChange
    /** A record was deleted in the source app. */
    data class Deletion(val recordId: String) : HcChange
}

/** Types (permission strings) whose last read failed, and where their re-read must start. */
data class HcPending(val types: Set<String>, val from: LocalDateTime)

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

    /**
     * [newTypesGranted]: the user allowed data types that were never imported, so the full 30 days are read (deletions the
     * old token reported are still applied). Nothing is ever read further back than the 30-day window.
     */
    fun plan(
        changes: List<HcChange>?, tokenExpired: Boolean, lastSync: LocalDateTime?, now: LocalDateTime,
        newTypesGranted: Boolean = false
    ): SyncPlan {
        val fullStart = HealthConnectMapper.syncStart(null, now)
        if (changes == null || tokenExpired) {
            return SyncPlan(false, HealthConnectMapper.syncStart(if (newTypesGranted) null else lastSync, now), emptyList())
        }
        val deleteIds = changes.filterIsInstance<HcChange.Deletion>().flatMap { deletionIds(it.recordId) }
        // A deletion doesn't say when the record was, and hourly heart rate / daily steps are built from many records:
        // re-read the whole window so those totals are rebuilt without the deleted data.
        if (newTypesGranted || deleteIds.isNotEmpty()) return SyncPlan(false, fullStart, deleteIds)
        if (changes.isEmpty()) return SyncPlan(true, null, emptyList())
        val alertWindow = now.minusHours(ALERT_HOURS)
        val earliestDay = changes.filterIsInstance<HcChange.Upsert>().minOfOrNull { it.time }?.toLocalDate()?.atStartOfDay()
        val from = if (earliestDay == null || earliestDay.isAfter(alertWindow)) alertWindow else earliestDay
        return SyncPlan(false, maxOf(from, fullStart), emptyList())
    }

    /** After a failed change-log read the old token is kept, so its changes (deletions included) are read next time. */
    fun tokenToSave(old: String?, next: String?, changesFailed: Boolean): String? = if (changesFailed) old else next

    /**
     * Types whose read failed this time ([failed], permission strings) are re-read next time from the earliest start
     * still owed; types that read fine, or are no longer [granted], are dropped. Null = nothing pending.
     */
    fun pending(previous: HcPending?, failed: Set<String>, granted: Set<String>, from: LocalDateTime): HcPending? {
        val still = failed intersect granted
        if (still.isEmpty()) return null
        val start = if (previous != null && (previous.types intersect still).isNotEmpty()) minOf(previous.from, from) else from
        return HcPending(still, start)
    }

    /** Where to start reading one type: earlier if it still owes data from a failed sync, never before the 30-day window. */
    fun fromFor(type: String, from: LocalDateTime, pending: HcPending?, now: LocalDateTime): LocalDateTime {
        val base = if (pending != null && type in pending.types) minOf(from, pending.from) else from
        return maxOf(base, HealthConnectMapper.syncStart(null, now))
    }

    /** Imported rows of a type that Health Connect no longer returns for the span just read (deleted at the source). */
    fun staleIds(stored: List<String>, returned: Collection<String>): List<String> {
        val keep = returned.toSet()
        return stored.filter { it !in keep }
    }

    /** Daily step totals are recomputed for whole days, so their read starts at midnight. */
    fun stepsFrom(from: LocalDateTime): LocalDateTime = from.toLocalDate().atStartOfDay()

    /** Hourly heart-rate averages are recomputed for whole hours. */
    fun heartFrom(from: LocalDateTime): LocalDateTime = from.truncatedTo(java.time.temporal.ChronoUnit.HOURS)

    /** A deleted record could be any uid-keyed type, so every candidate row id is listed. */
    fun deletionIds(recordId: String): List<String> = UID_PREFIXES.map { "${HealthConnectMapper.ID_PREFIX}${it}_$recordId" }
}
