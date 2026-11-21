package com.healthapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthapp.R
import com.healthapp.domain.usecase.HealthScore
import com.healthapp.domain.usecase.HealthScoreInput
import com.healthapp.domain.usecase.ScoreData
import com.healthapp.domain.usecase.ScoreHistory
import androidx.compose.ui.test.onNodeWithContentDescription
import com.healthapp.presentation.ui.dashboard.HealthScoreSheet
import com.healthapp.presentation.ui.theme.HealthAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI test for the health-score breakdown (the screen examiners tap first). */
@RunWith(AndroidJUnit4::class)
class HealthScoreSheetUiTest {
    @get:Rule val compose = createComposeRule()

    // Expected text comes from the app's resources, so the test passes in Vietnamese and in English.
    private val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = ctx.getString(id, *args)

    @Test fun showsScoreAndAreas_andTappingMissingAreaNavigates() {
        val result = HealthScore.calculate(
            HealthScoreInput(avgSleepMinutes = 450.0, sleepNights = 5, avgDailySteps = 9000.0, stepDays = 5, bmi = 21.0)
        )
        var opened: String? = null
        compose.setContent { HealthAppTheme { HealthScoreSheet(result, onDismiss = {}, onOpen = { opened = it }) } }

        // Score 100 also appears on every area row, so check the unique "/100 · grade" line.
        compose.onNodeWithText(str(R.string.score_of_100_grade, str(R.string.grade_excellent)), substring = true).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.area_sleep)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.area_blood_pressure)).performScrollTo().performClick() // the one missing area
        compose.waitForIdle()
        assertEquals("health", opened)
    }

    @Test fun showsWeeklyHistoryAndInsights() {
        val today = java.time.LocalDate.of(2026, 11, 22)
        val days = { from: Long, to: Long, v: Int -> (from..to).associate { today.minusDays(it) to v } }
        val data = ScoreData(
            sleepMinutesByDate = days(0, 6, 450) + days(7, 13, 330),
            stepsByDate = days(0, 13, 8000), heightCm = 165, profileWeightKg = 58.0
        )
        val weeks = ScoreHistory.weekly(data, today.atTime(21, 0), 4)
        val insights = ScoreHistory.insights(weeks.last(), weeks[weeks.size - 2])
        compose.setContent {
            HealthAppTheme { HealthScoreSheet(weeks.last().result, onDismiss = {}, onOpen = {}, history = weeks, insights = insights) }
        }
        compose.onNodeWithText(str(R.string.weeks_recent, 4)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.week_bar_cd, str(R.string.week_now), ""), substring = true).assertIsDisplayed()
        // The sheet opens half-expanded, so the insight row is composed but may sit below the fold.
        val change = (insights.first() as com.healthapp.domain.usecase.ScoreInsight.ScoreChange).diff
        compose.onNodeWithText(str(R.string.insight_score_up, change)).assertExists()
    }

    @Test fun notEnoughData_explainsWhatIsNeeded() {
        val result = HealthScore.calculate(HealthScoreInput(bmi = 21.0))
        compose.setContent { HealthAppTheme { HealthScoreSheet(result, onDismiss = {}, onOpen = {}) } }
        compose.onNodeWithText(str(R.string.score_min_areas_have, HealthScore.MIN_AREAS, 1)).assertIsDisplayed()
    }
}
