package com.healthapp.domain.usecase

import com.healthapp.domain.model.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderSchedule {

    /** Parses "HH:mm"; returns null for anything else. */
    fun parseTime(value: String): LocalTime? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(value.trim()) ?: return null
        val (h, m) = match.destructured
        val hour = h.toInt()
        val minute = m.toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Next moment strictly after [now] when a reminder at [time] should fire.
     * An empty or null [days] means every day.
     */
    fun nextTrigger(now: LocalDateTime, time: LocalTime, days: Collection<DayOfWeek>?): LocalDateTime {
        val allowed = days?.takeIf { it.isNotEmpty() }?.map { java.time.DayOfWeek.valueOf(it.name) }?.toSet()
        var candidate = now.toLocalDate().atTime(time)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        repeat(8) {
            if (allowed == null || candidate.dayOfWeek in allowed) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }
}
