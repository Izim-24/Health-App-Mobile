package com.healthapp.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Medication(
    val id: String,
    val name: String,
    val dosage: String,              // free text, e.g. "500 mg", "1 viên"
    val instructions: String? = null, // e.g. "sau ăn"
    val times: List<LocalTime>,       // daily dose slots, sorted
    val daysOfWeek: List<DayOfWeek>? = null, // null = every day
    val startDate: LocalDate,
    val endDate: LocalDate? = null,   // inclusive; null = ongoing
    val isActive: Boolean = true,
    val createdAt: LocalDateTime
)

enum class DoseStatus { TAKEN, SKIPPED }

data class DoseLog(
    val id: String,
    val medicationId: String,
    val date: LocalDate,
    val time: LocalTime,
    val status: DoseStatus,
    val recordedAt: LocalDateTime
)

/** One scheduled dose on a given day, with its logged status (null = not yet logged). */
data class ScheduledDose(
    val medication: Medication,
    val date: LocalDate,
    val time: LocalTime,
    val status: DoseStatus?
)
