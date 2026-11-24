package com.healthapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthapp.domain.model.ThemeMode
import com.healthapp.presentation.ui.components.EmptyState
import com.healthapp.presentation.ui.theme.HealthAppTheme
import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IllustrationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyState_showsTextAndDecorativeIllustration() {
        compose.setContent { HealthAppTheme { EmptyState(Illustration.MEDS, "No medications yet", "Add one to get reminders.") } }
        compose.onNodeWithText("No medications yet").assertIsDisplayed()
        compose.onNodeWithText("Add one to get reminders.").assertIsDisplayed()
        // Decorative: TalkBack should not stop on the picture.
        compose.onAllNodesWithContentDescription("", substring = true).fetchSemanticsNodes().let { assert(it.isEmpty()) }
    }

    @Test fun everyIllustrationRendersInLightAndDark() {
        // Parses every path of every illustration in both palettes (a bad path string would throw here).
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                    HealthAppTheme(themeMode = mode) {
                        Illustration.entries.forEach { IllustrationImage(it) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
