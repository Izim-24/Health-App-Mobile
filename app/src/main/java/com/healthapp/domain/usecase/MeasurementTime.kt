package com.healthapp.domain.usecase

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When a typed reading was taken: "today / yesterday …" plus "HH:mm", so a morning blood-pressure reading can be
 * logged in the afternoon. Never in the future, at most [MAX_DAYS_AGO] days back.
 */
object MeasurementTime {
    const val MAX_DAYS_AGO = 6
    /** "07:30", "7.30", "7,30", "7h30" or digits only ("0730", "730"): Android's number keyboard has no ":". */
    private val PATTERN = Regex("""^(\d{1,2})[:.,hH]?(\d{2})$""")

    /** Null when [time] isn't a time like those above, the moment is still to come, or [daysAgo] is out of range. */
    fun resolve(daysAgo: Int, time: String, now: LocalDateTime): LocalDateTime? {
        if (daysAgo !in 0..MAX_DAYS_AGO) return null
        val m = PATTERN.matchEntire(time.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        val at = now.toLocalDate().minusDays(daysAgo.toLong()).atTime(LocalTime.of(h, min))
        return if (at.isAfter(now)) null else at
    }

    /** "09:05", to prefill the field with the current time. */
    fun format(t: LocalDateTime): String = "%02d:%02d".format(t.hour, t.minute)
}
