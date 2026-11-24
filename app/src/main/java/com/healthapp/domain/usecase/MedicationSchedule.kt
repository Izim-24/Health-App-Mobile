package com.healthapp.domain.usecase

import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.domain.model.ScheduledDose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Adherence(val taken: Int, val scheduled: Int) {
    val rate: Float? get() = if (scheduled > 0) taken.toFloat() / scheduled else null
}

/** How long the pills on hand last at the current schedule. */
data class Supply(val pillsLeft: Int, val daysLeft: Int) {
    val low: Boolean get() = daysLeft <= MedicationSchedule.LOW_SUPPLY_DAYS
}

object MedicationSchedule {
    /** "Running low" when the pills on hand cover this many days or fewer. */
    const val LOW_SUPPLY_DAYS = 5

    /**
     * Days of supply = pills ÷ pills used per day. Pills per day averages the weekly schedule
     * (e.g. one pill on Mon/Wed/Fri = 3/7 a day). Null when supply isn't tracked or nothing is scheduled.
     */
    fun supply(med: Medication): Supply? {
        val pills = med.pillsLeft ?: return null
        val daysPerWeek = med.daysOfWeek?.size ?: 7
        val perDay = med.times.size * med.pillsPerDose * daysPerWeek / 7.0
        if (perDay <= 0.0) return null
        return Supply(pills, kotlin.math.floor(pills / perDay).toInt())
    }

    /** Pills after marking a dose: taken removes pillsPerDose (never below 0); undoing a taken dose puts them back. */
    fun pillsAfter(med: Medication, wasTaken: Boolean, nowTaken: Boolean): Int? {
        val pills = med.pillsLeft ?: return null
        return when {
            nowTaken && !wasTaken -> (pills - med.pillsPerDose).coerceAtLeast(0)
            wasTaken && !nowTaken -> pills + med.pillsPerDose
            else -> pills
        }
    }


    /** Parses "08:00, 20:30" into sorted, de-duplicated times; null if any entry is invalid or none given. */
    fun parseTimes(raw: String): List<LocalTime>? {
        val parts = raw.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val times = parts.map { ReminderSchedule.parseTime(it) ?: return null }
        return times.distinct().sorted()
    }

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun isScheduledOn(med: Medication, date: LocalDate): Boolean {
        if (!med.isActive) return false
        if (date.isBefore(med.startDate)) return false
        if (med.endDate != null && date.isAfter(med.endDate)) return false
        val days = med.daysOfWeek?.takeIf { it.isNotEmpty() } ?: return true
        return days.any { it.name == date.dayOfWeek.name }
    }

    /** All doses due on [date] across [meds], ordered by time, joined with any existing logs. */
    /**
     * The dose to surface on the home screen: the earliest one today not yet marked taken or skipped.
     * An overdue dose comes before a later upcoming one, so a missed morning pill isn't hidden.
     */
    fun upNext(dosesToday: List<ScheduledDose>): ScheduledDose? =
        dosesToday.filter { it.status == null }.minByOrNull { it.time }

    fun dosesFor(date: LocalDate, meds: List<Medication>, logs: List<DoseLog>): List<ScheduledDose> {
        val byKey = logs.filter { it.date == date }.associateBy { it.medicationId to it.time }
        return meds.filter { isScheduledOn(it, date) }
            .flatMap { med -> med.times.map { t -> ScheduledDose(med, date, t, byKey[med.id to t]?.status) } }
            .sortedWith(compareBy({ it.time }, { it.medication.name }))
    }

    /** Next dose moment strictly after [now] for this medication, or null if the course has ended. */
    fun nextDose(med: Medication, time: LocalTime, now: LocalDateTime): LocalDateTime? {
        if (!med.isActive) return null
        var date = maxOf(now.toLocalDate(), med.startDate)
        repeat(8) {
            val candidate = date.atTime(time)
            if (med.endDate != null && date.isAfter(med.endDate)) return null
            if (candidate.isAfter(now) && isScheduledOn(med, date)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    /**
     * Adherence over [from]..[to] inclusive. Doses later than [now] are not yet due and are excluded,
     * so taking today's morning pill does not count the evening one as missed. Doses that fell before the
     * medication was added are only counted if the user logged them.
     */
    fun adherence(meds: List<Medication>, logs: List<DoseLog>, from: LocalDate, to: LocalDate, now: LocalDateTime): Adherence {
        var taken = 0
        var scheduled = 0
        var d = from
        while (!d.isAfter(to)) {
            dosesFor(d, meds, logs).forEach { dose ->
                val at = dose.date.atTime(dose.time)
                val beforeAdded = at.isBefore(dose.medication.createdAt) && dose.status == null
                if (!at.isAfter(now) && !beforeAdded) {
                    scheduled++
                    if (dose.status == DoseStatus.TAKEN) taken++
                }
            }
            d = d.plusDays(1)
        }
        return Adherence(taken, scheduled)
    }
}
