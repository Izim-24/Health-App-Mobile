package com.healthapp.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.healthapp.domain.model.ThemeMode

private fun colorScheme(c: HealthColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary, onPrimary = Color.White, primaryContainer = c.primarySoft, onPrimaryContainer = c.primary,
        secondary = c.violet.strong, onSecondary = Color.White, secondaryContainer = c.violet.soft, onSecondaryContainer = c.violet.strong,
        tertiary = c.orange.strong, onTertiary = Color.White, tertiaryContainer = c.orange.soft, onTertiaryContainer = c.orange.strong,
        background = c.background, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceAlt,
        outline = c.line, outlineVariant = c.line,
        error = c.coral.strong, errorContainer = c.coral.soft
    )
}

private val HealthShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HealthAppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkHealthColors else LightHealthColors

    // System bars follow the in-app theme (also when it differs from the system setting).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalHealthColors provides colors) {
        MaterialTheme(colorScheme = colorScheme(colors), typography = HealthAppTypography, shapes = HealthShapes, content = content)
    }
}
