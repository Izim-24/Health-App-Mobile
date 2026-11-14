package com.healthapp.domain.usecase

import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationScheduleTest {
    // 2026-11-14 is a Saturday.
    private val sat = LocalDate.of(2026, 11, 14)
    private val am = LocalTime.of(8, 0)
    private val pm = LocalTime.of(20, 0)

    private fun med(
        id: String = "m1", name: String = "Metformin", times: List<LocalTime> = listOf(am, pm),
        days: List<DayOfWeek>? = null, start: LocalDate = sat.minusDays(10), end: LocalDate? = null, active: Boolean = true
    ) = Medication(id, name, "500 mg", null, times, days, start, end, active, LocalDateTime.of(2026, 1, 1, 0, 0))

    private fun log(date: LocalDate, time: LocalTime, status: DoseStatus = DoseStatus.TAKEN, id: String = "m1") =
        DoseLog("$id-$date-$time", id, date, time, status, date.atTime(time))

    @Test fun parseTimes_sortsDedupsAndRejectsBad() {
        assertEquals(listOf(am, pm), MedicationSchedule.parseTimes("20:00, 08:00, 8:00"))
        assertNull(MedicationSchedule.parseTimes("08:00, 25:00"))
        assertNull(MedicationSchedule.parseTimes("  "))
    }

    @Test fun scheduledOn_respectsCourseDatesDaysAndActive() {
        assertTrue(MedicationSchedule.isScheduledOn(med(), sat))
        assertFalse(MedicationSchedule.isScheduledOn(med(start = sat.plusDays(1)), sat))
        assertFalse(MedicationSchedule.isScheduledOn(med(end = sat.minusDays(1)), sat))
        assertTrue(MedicationSchedule.isScheduledOn(med(end = sat), sat)) // end date inclusive
        assertFalse(MedicationSchedule.isScheduledOn(med(days = listOf(DayOfWeek.MONDAY)), sat))
        assertTrue(MedicationSchedule.isScheduledOn(med(days = listOf(DayOfWeek.SATURDAY)), sat))
        assertFalse(MedicationSchedule.isScheduledOn(med(active = false), sat))
    }

    @Test fun dosesFor_joinsLogsAndOrdersByTime() {
        val vitD = med(id = "m2", name = "Vitamin D", times = listOf(LocalTime.of(12, 0)))
        val doses = MedicationSchedule.dosesFor(sat, listOf(med(), vitD), listOf(log(sat, am), log(sat.minusDays(1), pm)))
        assertEquals(listOf(am, LocalTime.of(12, 0), pm), doses.map { it.time })
        assertEquals(listOf(DoseStatus.TAKEN, null, null), doses.map { it.status })
    }

    @Test fun nextDose_laterTodayTomorrowOrNoneAfterEnd() {
        val now = sat.atTime(9, 0)
        assertEquals(sat.atTime(pm), MedicationSchedule.nextDose(med(), pm, now))
        assertEquals(sat.plusDays(1).atTime(am), MedicationSchedule.nextDose(med(), am, now))
        assertNull(MedicationSchedule.nextDose(med(end = sat), am, now))
        assertEquals(sat.plusDays(2).atTime(am), MedicationSchedule.nextDose(med(days = listOf(DayOfWeek.MONDAY)), am, now))
        assertEquals(sat.plusDays(5).atTime(am), MedicationSchedule.nextDose(med(start = sat.plusDays(5)), am, now))
    }

    @Test fun adherence_ignoresUnloggedDosesBeforeMedicationWasAdded() {
        val addedAt9 = med(start = sat).copy(createdAt = sat.atTime(9, 0))
        val now = sat.atTime(21, 0)
        assertEquals(Adherence(0, 1), MedicationSchedule.adherence(listOf(addedAt9), emptyList(), sat, sat, now)) // only 20:00
        assertEquals(Adherence(1, 2), MedicationSchedule.adherence(listOf(addedAt9), listOf(log(sat, am)), sat, sat, now)) // logged 08:00 counts
    }

    @Test fun adherence_excludesDosesNotYetDue() {
        val now = sat.atTime(9, 0)
        // Fri: 2 doses (1 taken, 1 skipped); Sat: only 08:00 is due so far (taken).
        val logs = listOf(log(sat.minusDays(1), am), log(sat.minusDays(1), pm, DoseStatus.SKIPPED), log(sat, am))
        val a = MedicationSchedule.adherence(listOf(med()), logs, sat.minusDays(1), sat, now)
        assertEquals(2, a.taken)
        assertEquals(3, a.scheduled)
        assertEquals(2f / 3, a.rate!!, 0.001f)
        assertNull(MedicationSchedule.adherence(emptyList(), emptyList(), sat, sat, now).rate)
    }
}
