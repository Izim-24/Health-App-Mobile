package com.healthapp.domain.usecase

import com.healthapp.data.repository.ReminderRepositoryImpl
import com.healthapp.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderScheduleTest {
    // 2026-11-14 is a Saturday.
    private val saturdayMorning = LocalDateTime.of(2026, 11, 14, 8, 0)

    @Test fun parseTime_acceptsValidAndRejectsInvalid() {
        assertEquals(LocalTime.of(9, 5), ReminderSchedule.parseTime("09:05"))
        assertEquals(LocalTime.of(7, 30), ReminderSchedule.parseTime("7:30"))
        assertNull(ReminderSchedule.parseTime("24:00"))
        assertNull(ReminderSchedule.parseTime("12:60"))
        assertNull(ReminderSchedule.parseTime("noon"))
    }

    @Test fun daily_laterToday() {
        assertEquals(LocalDateTime.of(2026, 11, 14, 9, 0), ReminderSchedule.nextTrigger(saturdayMorning, LocalTime.of(9, 0), null))
    }

    @Test fun daily_alreadyPassedGoesToTomorrow() {
        assertEquals(LocalDateTime.of(2026, 11, 15, 7, 0), ReminderSchedule.nextTrigger(saturdayMorning, LocalTime.of(7, 0), emptyList()))
    }

    @Test fun exactNowIsNotReturned() {
        assertEquals(LocalDateTime.of(2026, 11, 15, 8, 0), ReminderSchedule.nextTrigger(saturdayMorning, LocalTime.of(8, 0), null))
    }

    @Test fun weekdaysOnly_skipsWeekend() {
        val next = ReminderSchedule.nextTrigger(saturdayMorning, LocalTime.of(9, 0),
            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        assertEquals(LocalDateTime.of(2026, 11, 16, 9, 0), next)
    }

    @Test fun singleDay_sameDayPassed_waitsAWeek() {
        val next = ReminderSchedule.nextTrigger(saturdayMorning, LocalTime.of(6, 0), listOf(DayOfWeek.SATURDAY))
        assertEquals(LocalDateTime.of(2026, 11, 21, 6, 0), next)
    }

    @Test fun daysEncoding_roundTripsAndReadsLegacyJson() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertEquals(days, ReminderRepositoryImpl.decodeDays(ReminderRepositoryImpl.encodeDays(days)))
        assertEquals(days, ReminderRepositoryImpl.decodeDays("[\"MONDAY\", \"FRIDAY\"]"))
        assertNull(ReminderRepositoryImpl.encodeDays(emptyList()))
        assertNull(ReminderRepositoryImpl.decodeDays(null))
    }
}
