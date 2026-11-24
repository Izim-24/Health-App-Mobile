package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.MedicationRepository
import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.DoseLog
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.domain.model.ScheduledDose
import com.healthapp.domain.usecase.Adherence
import com.healthapp.domain.usecase.MedicationSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class MedicationUiState(
    val loading: Boolean = true,
    val medications: List<Medication> = emptyList(),
    val todayDoses: List<ScheduledDose> = emptyList(),
    val todayAdherence: Adherence = Adherence(0, 0),
    val weekAdherence: Adherence = Adherence(0, 0),
    val error: UiText? = null
)

/** Raw editor input; validated in [MedicationViewModel.save]. */
data class MedicationDraft(
    val id: String? = null,
    val name: String = "",
    val dosage: String = "",
    val instructions: String = "",
    val times: String = "08:00",
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val durationDays: String = "",
    val pillsLeft: String = "",
    val pillsPerDose: String = "1"
)

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repository: MedicationRepository
) : ViewModel() {

    private val mutable = MutableStateFlow(MedicationUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val today = LocalDate.now()
                combine(repository.observeMedications(), repository.observeLogs(today.minusDays(6), today)) { meds, logs -> meds to logs }
                    .collect { (meds, logs) -> recompute(meds, logs) }
            }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message?.let(UiText::Raw)) } }
        }
    }

    private fun recompute(meds: List<Medication>, logs: List<DoseLog>) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        mutable.update {
            it.copy(
                loading = false,
                medications = meds,
                todayDoses = MedicationSchedule.dosesFor(today, meds, logs),
                todayAdherence = MedicationSchedule.adherence(meds, logs, today, today, now),
                weekAdherence = MedicationSchedule.adherence(meds, logs, today.minusDays(6), today, now)
            )
        }
    }

    fun draftFor(med: Medication): MedicationDraft = MedicationDraft(
        id = med.id,
        name = med.name,
        dosage = med.dosage,
        instructions = med.instructions.orEmpty(),
        times = med.times.joinToString(", ") { MedicationSchedule.formatTime(it) },
        days = med.daysOfWeek?.toSet() ?: DayOfWeek.entries.toSet(),
        durationDays = med.endDate?.let { (java.time.temporal.ChronoUnit.DAYS.between(med.startDate, it) + 1).toString() }.orEmpty(),
        pillsLeft = med.pillsLeft?.toString().orEmpty(),
        pillsPerDose = med.pillsPerDose.toString()
    )

    /** Returns an error message, or null when saved. */
    fun save(draft: MedicationDraft): UiText? {
        val name = draft.name.trim()
        if (name.isEmpty()) return UiText.Res(R.string.err_med_name)
        val times = MedicationSchedule.parseTimes(draft.times) ?: return UiText.Res(R.string.err_med_times)
        if (times.size > 12) return UiText.Res(R.string.err_med_max_times)
        if (draft.days.isEmpty()) return UiText.Res(R.string.err_med_days)
        val duration = draft.durationDays.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return UiText.Res(R.string.err_med_duration) }
        if (duration != null && duration !in 1..3650) return UiText.Res(R.string.err_med_duration_range)
        val pillsLeft = draft.pillsLeft.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull()?.takeIf { n -> n in 0..9999 } ?: return UiText.Res(R.string.err_pills) }
        val pillsPerDose = draft.pillsPerDose.trim().ifEmpty { "1" }.toIntOrNull()?.takeIf { it in 1..20 } ?: return UiText.Res(R.string.err_pills)

        val existing = state.value.medications.firstOrNull { it.id == draft.id }
        val start = existing?.startDate ?: LocalDate.now()
        val med = Medication(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.take(80),
            dosage = draft.dosage.trim().take(40),
            instructions = draft.instructions.trim().take(120).ifEmpty { null },
            times = times,
            daysOfWeek = draft.days.takeIf { it.size < 7 }?.let { d -> DayOfWeek.entries.filter { it in d } },
            startDate = start,
            endDate = duration?.let { start.plusDays(it - 1L) },
            isActive = existing?.isActive ?: true,
            createdAt = existing?.createdAt ?: LocalDateTime.now(),
            pillsLeft = pillsLeft,
            pillsPerDose = pillsPerDose
        )
        viewModelScope.launch {
            repository.save(med).onFailure { e -> mutable.update { it.copy(error = e.message?.let(UiText::Raw) ?: UiText.Res(R.string.err_med_save)) } }
        }
        return null
    }

    /** Tapping the current status again clears it (undo). */
    fun mark(dose: ScheduledDose, status: DoseStatus) {
        val newStatus = if (dose.status == status) null else status
        viewModelScope.launch {
            repository.logDose(dose.medication.id, dose.date, dose.time, newStatus)
                .onFailure { e -> mutable.update { it.copy(error = e.message?.let(UiText::Raw)) } }
        }
    }

    fun setActive(med: Medication, active: Boolean) {
        viewModelScope.launch { repository.setActive(med, active).onFailure { e -> mutable.update { it.copy(error = e.message?.let(UiText::Raw)) } } }
    }

    fun delete(med: Medication) {
        viewModelScope.launch { repository.delete(med).onFailure { e -> mutable.update { it.copy(error = e.message?.let(UiText::Raw)) } } }
    }

    fun clearError() = mutable.update { it.copy(error = null) }
}
