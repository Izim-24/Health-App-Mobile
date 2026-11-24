package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ReportPeriod(@StringRes val label: Int) { DAY(R.string.period_day), WEEK(R.string.period_week), MONTH(R.string.period_month) }

data class ReportsUiState(
    val loading: Boolean = true,
    val period: ReportPeriod = ReportPeriod.DAY,
    val range: String = "",
    val nutritionDays: Int = 0,
    val avgCalories: Int = 0,
    val avgProtein: Int = 0,
    val avgCarbs: Int = 0,
    val avgFat: Int = 0,
    val activityDays: Int = 0,
    val avgSteps: Int = 0,
    val totalExerciseMinutes: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val waterDays: Int = 0,
    val avgWaterMl: Int = 0,
    val completedHabits: Int = 0,
    val activeHabits: Int = 0,
    val error: UiText? = null,
    /** The period's numbers, kept for the PDF export. */
    val report: com.healthapp.domain.model.PeriodReport? = null,
    val exporting: Boolean = false,
    val exportError: UiText? = null,
    /** A finished PDF waiting to be shared (consumed by the screen). */
    val pdfFile: java.io.File? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val accountManager: AccountSession,
    private val reports: ReportRepository,
    private val exporter: com.healthapp.domain.repository.ReportExportRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(ReportsUiState())
    val state = mutable.asStateFlow()

    init { select(ReportPeriod.DAY) }

    fun select(period: ReportPeriod) {
        mutable.value = mutable.value.copy(loading = true, period = period, error = null)
        viewModelScope.launch {
            runCatching {
                val accountId = accountManager.getCurrentAccountId()
                val end = LocalDate.now()
                val start = when (period) {
                    ReportPeriod.DAY -> end
                    ReportPeriod.WEEK -> end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ReportPeriod.MONTH -> end.withDayOfMonth(1)
                }
                val r = reports.periodReport(accountId, start, end)
                ReportsUiState(
                    loading = false, period = period, range = if (start == end) end.toString() else "$start – $end",
                    nutritionDays = r.nutritionDays, avgCalories = r.avgCalories, avgProtein = r.avgProtein,
                    avgCarbs = r.avgCarbs, avgFat = r.avgFat,
                    activityDays = r.activityDays, avgSteps = r.avgSteps,
                    totalExerciseMinutes = r.totalExerciseMinutes, totalCaloriesBurned = r.totalCaloriesBurned,
                    waterDays = r.waterDays, avgWaterMl = r.avgWaterMl,
                    completedHabits = r.completedHabits, activeHabits = r.activeHabits, report = r
                )
            }.onSuccess { if (mutable.value.period == period) mutable.value = it }
                .onFailure { mutable.value = mutable.value.copy(loading = false, error = it.message?.let(UiText::Raw) ?: UiText.Res(R.string.err_load_report)) }
        }
    }

    /** Writes the shown period to a PDF; the screen opens the share sheet when [ReportsUiState.pdfFile] is set. */
    fun exportPdf() {
        val report = mutable.value.report ?: return
        if (mutable.value.exporting) return
        mutable.value = mutable.value.copy(exporting = true, exportError = null)
        viewModelScope.launch {
            exporter.exportPdf(report)
                .onSuccess { file -> mutable.value = mutable.value.copy(exporting = false, pdfFile = file) }
                .onFailure { e -> mutable.value = mutable.value.copy(exporting = false, exportError = UiText.Res(R.string.msg_pdf_failed, listOf(e.message.orEmpty()))) }
        }
    }

    fun pdfConsumed() { mutable.value = mutable.value.copy(pdfFile = null) }
}
