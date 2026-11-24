package com.healthapp.domain.usecase

import java.time.Duration
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * "Smart" water reminders: compare what you've drunk with an even pace toward the daily goal over the
 * waking day (07:00–22:00). If you're on pace a water reminder is skipped; if not, it says how far behind.
 */
object WaterPace {
    val DAY_START: LocalTime = LocalTime.of(7, 0)
    val DAY_END: LocalTime = LocalTime.of(22, 0)

    /** Millilitres you'd have had by [now] drinking evenly: 0 before 07:00, the whole goal from 22:00. */
    fun expectedBy(goalMl: Int, now: LocalTime): Int {
        if (goalMl <= 0 || !now.isAfter(DAY_START)) return 0
        if (!now.isBefore(DAY_END)) return goalMl
        val elapsed = Duration.between(DAY_START, now).toMinutes().toDouble()
        val day = Duration.between(DAY_START, DAY_END).toMinutes().toDouble()
        return (goalMl * elapsed / day).roundToInt()
    }

    /** How many ml behind the even pace; 0 when on track; null when there is no goal to judge against. */
    fun behindBy(intakeMl: Int, goalMl: Int, now: LocalTime): Int? =
        if (goalMl <= 0) null else (expectedBy(goalMl, now) - intakeMl).coerceAtLeast(0)

    /** Without a goal every reminder fires (nothing to compare with); with one, only when you're behind. */
    fun shouldRemind(intakeMl: Int, goalMl: Int, now: LocalTime): Boolean {
        val behind = behindBy(intakeMl, goalMl, now) ?: return true
        return behind > 0
    }
}
