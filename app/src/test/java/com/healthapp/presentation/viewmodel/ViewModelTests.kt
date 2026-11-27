package com.healthapp.presentation.viewmodel

import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.model.HealthProfile
import com.healthapp.domain.model.MoodEntry
import com.healthapp.domain.model.PeriodReport
import com.healthapp.domain.repository.AccountSession
import com.healthapp.domain.repository.MoodRepository
import com.healthapp.domain.repository.ProfileRepository
import com.healthapp.domain.repository.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

// ---- In-memory fakes for the domain interfaces ----

private class FakeSession : AccountSession {
    override suspend fun getCurrentAccountId() = "acc"
}

private class FakeProfiles(var stored: HealthProfile? = null) : ProfileRepository {
    var saves = 0
    override suspend fun getProfile(accountId: String) = stored
    override suspend fun saveProfile(accountId: String, profile: HealthProfile) { stored = profile; saves++ }
}

private class FakeMoods : MoodRepository {
    val rows = MutableStateFlow<Map<LocalDate, MoodEntry>>(emptyMap())
    override fun observeEntries(): Flow<List<MoodEntry>> = rows.map { it.values.sortedByDescending { e -> e.date } }
    override suspend fun save(entry: MoodEntry): Result<Unit> = runCatching {
        require(entry.mood.isNotBlank()) { "Chọn một cảm xúc." }
        rows.value = rows.value + (entry.date to entry)
    }
}

private class FakeReports : ReportRepository {
    var lastRange: Pair<LocalDate, LocalDate>? = null
    override suspend fun periodReport(accountId: String, start: LocalDate, end: LocalDate): PeriodReport {
        lastRange = start to end
        return PeriodReport(start, end, nutritionDays = 2, avgCalories = 1850, avgSteps = 7200, activityDays = 2, completedHabits = 5, activeHabits = 3)
    }
}

private class FakeExporter(val fail: Boolean = false) : com.healthapp.domain.repository.ReportExportRepository {
    val exported = mutableListOf<PeriodReport>()
    override suspend fun exportPdf(report: PeriodReport): Result<java.io.File> = runCatching {
        check(!fail) { "disk full" }
        exported += report
        java.io.File("report.pdf")
    }
}

private class FakeHealthAlerts : com.healthapp.domain.repository.HealthAlertRepository {
    val flow = MutableStateFlow<List<com.healthapp.domain.usecase.VitalAlert>>(emptyList())
    val checks = mutableListOf<Boolean>() // simulated flag of each check() call
    /** Every alert check() produced; check() applies the real VitalAlerts rules (age 28) to what it receives. */
    val returned = mutableListOf<com.healthapp.domain.usecase.VitalAlert>()
    var resets = 0
    override val recent: Flow<List<com.healthapp.domain.usecase.VitalAlert>> = flow
    override suspend fun check(hr: List<com.healthapp.domain.usecase.HrSample>, steps: List<com.healthapp.domain.usecase.StepSample>,
                               bp: List<com.healthapp.domain.usecase.BpReading>, now: java.time.LocalDateTime, notify: Boolean, simulated: Boolean): List<com.healthapp.domain.usecase.VitalAlert> {
        checks += simulated
        val out = com.healthapp.domain.usecase.VitalAlerts.heartAlerts(hr, steps, 28, now) + com.healthapp.domain.usecase.VitalAlerts.bpAlerts(bp, now)
        returned += out
        return out
    }
    override fun resetSimulation() { resets++ }
    override suspend fun clearRecent() { flow.value = emptyList() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTests {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // ---------------- Profile ----------------

    @Test fun profile_loadsSavedProfileIntoForm() {
        val vm = ProfileViewModel(FakeSession(), FakeProfiles(HealthProfile(fullName = "Lan", heightCm = 160, weightKg = 52.5, gender = Gender.FEMALE)))
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("Lan", s.name)
        assertEquals("160", s.heightCm)
        assertEquals("52.5", s.weightKg)
        assertEquals(Gender.FEMALE, s.gender)
    }

    @Test fun profile_rejectsImplausibleInput_withoutSaving() {
        val repo = FakeProfiles()
        val vm = ProfileViewModel(FakeSession(), repo)
        vm.update("An", "1990-01-01", "20", "70", "")   // 20 cm tall is invalid
        vm.save()
        assertNotNull(vm.state.value.error)
        assertEquals(0, repo.saves)
    }

    @Test fun profile_calculateTargets_thenSave_persistsCaloriesAndMacros() {
        val repo = FakeProfiles()
        val vm = ProfileViewModel(FakeSession(), repo)
        vm.update("An", "1996-01-01", "175", "70", "")
        vm.setGender(Gender.MALE)
        vm.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE)
        vm.calculateTargets()
        val kcal = vm.state.value.calorieGoal.toInt()
        assertTrue("TDEE should be ~2500 kcal, was $kcal", kcal in 2400..2650)
        vm.save()
        assertTrue(vm.state.value.saved)
        assertEquals(kcal, repo.stored!!.targetCalories)
        assertEquals(112.0, repo.stored!!.targetProtein, 0.5) // 1.6 g/kg
    }

    @Test fun profile_calculateTargets_requiresGenderAndActivity() {
        val vm = ProfileViewModel(FakeSession(), FakeProfiles())
        vm.update("An", "1996-01-01", "175", "70", "")
        vm.calculateTargets()
        assertNotNull(vm.state.value.error)
        assertEquals("", vm.state.value.calorieGoal)
    }

    // ---------------- Mental health ----------------

    @Test fun mood_saveShowsTodayAndConfirmation_editingClearsIt() {
        val vm = MentalHealthViewModel(FakeMoods())
        assertNull(vm.state.value.today)
        vm.save("Vui", 3, "  đi dạo  ")
        val today = vm.state.value.today!!
        assertEquals("Vui", today.mood)
        assertEquals("đi dạo", today.note) // trimmed
        assertTrue(vm.state.value.savedToday)
        vm.markDirty()
        assertFalse(vm.state.value.savedToday)
    }

    @Test fun mood_secondSaveSameDayReplacesFirst() {
        val vm = MentalHealthViewModel(FakeMoods())
        vm.save("Mệt", 6, "")
        vm.save("Bình yên", 2, "")
        assertEquals(1, vm.state.value.entries.size)
        assertEquals("Bình yên", vm.state.value.today!!.mood)
    }

    @Test fun mood_invalidSaveSurfacesError() {
        val vm = MentalHealthViewModel(FakeMoods())
        vm.save("", 3, "")
        assertEquals(com.healthapp.presentation.ui.text.UiText.Raw("Chọn một cảm xúc."), vm.state.value.error)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    // ---------------- Reports ----------------

    @Test fun reports_weekStartsOnMonday_andMapsRepositoryValues() {
        val repo = FakeReports()
        val vm = ReportsViewModel(FakeSession(), repo, FakeExporter())
        vm.select(ReportPeriod.WEEK)
        val (start, end) = repo.lastRange!!
        assertEquals(java.time.DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.now(), end)
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(1850, s.avgCalories)
        assertEquals(7200, s.avgSteps)
        assertEquals(5, s.completedHabits)
    }

    @Test fun reports_monthStartsOnFirstDay() {
        val repo = FakeReports()
        ReportsViewModel(FakeSession(), repo, FakeExporter()).select(ReportPeriod.MONTH)
        assertEquals(1, repo.lastRange!!.first.dayOfMonth)
    }

    @Test fun reports_exportPdf_usesShownPeriod_andIsConsumedOnce() {
        val exporter = FakeExporter()
        val vm = ReportsViewModel(FakeSession(), FakeReports(), exporter)
        vm.select(ReportPeriod.WEEK)
        vm.exportPdf()
        assertEquals(1, exporter.exported.size)
        assertEquals(java.time.DayOfWeek.MONDAY, exporter.exported.single().start.dayOfWeek)
        assertNotNull(vm.state.value.pdfFile)
        assertFalse(vm.state.value.exporting)
        vm.pdfConsumed()
        assertNull(vm.state.value.pdfFile)
    }

    @Test fun reports_exportFailure_keepsReportVisible() {
        val vm = ReportsViewModel(FakeSession(), FakeReports(), FakeExporter(fail = true))
        vm.exportPdf()
        val s = vm.state.value
        assertNotNull(s.exportError)
        assertNull(s.error)          // the report cards stay on screen
        assertEquals(1850, s.avgCalories)
        assertNull(s.pdfFile)
    }

    @Test fun healthConnect_backgroundSyncNeedsConnectionAndPermission() {
        val base = com.healthapp.domain.model.HcState(enabled = true, granted = setOf("read_steps"))
        assertFalse(base.backgroundSync)
        assertTrue(base.copy(backgroundSupported = true, backgroundGranted = true).backgroundSync)
        assertFalse(base.copy(enabled = false, backgroundGranted = true).backgroundSync)
    }

    // ---------------- Health alerts ----------------

    @Test fun healthAlerts_exposesRecent_andClears() {
        val repo = FakeHealthAlerts()
        val now = java.time.LocalDateTime.of(2026, 11, 28, 9, 0)
        repo.flow.value = listOf(
            com.healthapp.domain.usecase.VitalAlert(com.healthapp.domain.usecase.AlertKind.BP_CRISIS, now, 186, 118),
            com.healthapp.domain.usecase.VitalAlert(com.healthapp.domain.usecase.AlertKind.REST_TACHY, now.minusHours(1), 125)
        )
        val vm = HealthAlertsViewModel(repo)
        assertEquals(2, vm.alerts.value.size)
        vm.clear()
        assertTrue(vm.alerts.value.isEmpty())
    }

    // ---------------- Watch simulator ----------------

    private fun simulator(repo: FakeHealthAlerts) = WatchSimulatorViewModel(
        repo, FakeProfiles(HealthProfile(fullName = "An", dateOfBirth = "1998-03-14")), FakeSession()
    )

    @Test fun watchSimulator_restTachy_raisesAlert() = kotlinx.coroutines.test.runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = FakeHealthAlerts()
        val vm = simulator(repo)
        vm.start(com.healthapp.domain.usecase.WatchScenario.REST_TACHY)
        advanceTimeBy(15_000); runCurrent()
        vm.stop()
        assertTrue(repo.returned.map { it.kind }.toString(), com.healthapp.domain.usecase.AlertKind.REST_TACHY in repo.returned.map { it.kind })
        assertTrue(repo.checks.isNotEmpty() && repo.checks.all { it })
        assertEquals(1, repo.resets)
        assertEquals(com.healthapp.domain.usecase.AlertKind.REST_TACHY, vm.state.value.lastAlert?.kind)
    }

    @Test fun watchSimulator_run_noAlert() = kotlinx.coroutines.test.runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = FakeHealthAlerts()
        val vm = simulator(repo)
        vm.start(com.healthapp.domain.usecase.WatchScenario.RUN)
        advanceTimeBy(20_000); runCurrent()
        vm.stop()
        assertTrue(repo.returned.isEmpty())
        assertTrue(repo.checks.size >= 19)
        assertEquals(com.healthapp.domain.usecase.ActivityState.ACTIVE, vm.state.value.activity)
        assertFalse(vm.state.value.running)
    }

    @Test fun watchSimulator_restTachyAfterRun_stillWarnsWithin14s() = kotlinx.coroutines.test.runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val repo = FakeHealthAlerts()
        val vm = simulator(repo)
        vm.start(com.healthapp.domain.usecase.WatchScenario.RUN)
        advanceTimeBy(20_000); runCurrent()
        repo.returned.clear()
        vm.start(com.healthapp.domain.usecase.WatchScenario.REST_TACHY) // an incident scenario starts fresh
        advanceTimeBy(15_000); runCurrent()
        vm.stop()
        assertTrue(repo.returned.map { it.kind }.toString(), com.healthapp.domain.usecase.AlertKind.REST_TACHY in repo.returned.map { it.kind })
    }
}

