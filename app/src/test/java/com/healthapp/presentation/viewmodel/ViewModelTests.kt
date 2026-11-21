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
        assertEquals("Chọn một cảm xúc.", vm.state.value.error)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    // ---------------- Reports ----------------

    @Test fun reports_weekStartsOnMonday_andMapsRepositoryValues() {
        val repo = FakeReports()
        val vm = ReportsViewModel(FakeSession(), repo)
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
        ReportsViewModel(FakeSession(), repo).select(ReportPeriod.MONTH)
        assertEquals(1, repo.lastRange!!.first.dayOfMonth)
    }
}
