package com.healthapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun showsScoreAndAreas_andTappingMissingAreaNavigates() {
        val result = HealthScore.calculate(
            HealthScoreInput(avgSleepMinutes = 450.0, sleepNights = 5, avgDailySteps = 9000.0, stepDays = 5, bmi = 21.0)
        )
        var opened: String? = null
        compose.setContent { HealthAppTheme { HealthScoreSheet(result, onDismiss = {}, onOpen = { opened = it }) } }

        // Score 100 also appears on every area row, so check the unique "/100 · grade" line.
        compose.onNodeWithText("/100 · ${result.grade}", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Giấc ngủ").assertIsDisplayed()
        compose.onNodeWithText("Huyết áp").performScrollTo().performClick() // the one missing area
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
        compose.onNodeWithText("4 tuần gần đây").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tuần đến Nay", substring = true).assertIsDisplayed()
        // The sheet opens half-expanded, so the insight row is composed but may sit below the fold.
        compose.onNodeWithText("Điểm tăng", substring = true).assertExists()
    }

    @Test fun notEnoughData_explainsWhatIsNeeded() {
        val result = HealthScore.calculate(HealthScoreInput(bmi = 21.0))
        compose.setContent { HealthAppTheme { HealthScoreSheet(result, onDismiss = {}, onOpen = {}) } }
        compose.onNodeWithText("Cần ít nhất ${HealthScore.MIN_AREAS} chỉ số", substring = true).assertIsDisplayed()
    }
}
