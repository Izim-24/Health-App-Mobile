package com.healthapp.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = HealthPalette.Primary,
    onPrimary = Color.White,
    primaryContainer = HealthPalette.PrimarySoft,
    onPrimaryContainer = HealthPalette.PrimaryDark,
    secondary = HealthPalette.Violet.strong,
    onSecondary = Color.White,
    secondaryContainer = HealthPalette.Violet.soft,
    onSecondaryContainer = HealthPalette.Violet.strong,
    tertiary = HealthPalette.Orange.strong,
    onTertiary = Color.White,
    tertiaryContainer = HealthPalette.Orange.soft,
    onTertiaryContainer = Color(0xFF8A3B00),
    background = HealthPalette.Background,
    onBackground = HealthPalette.Ink,
    surface = HealthPalette.Surface,
    onSurface = HealthPalette.Ink,
    surfaceVariant = HealthPalette.SurfaceAlt,
    onSurfaceVariant = HealthPalette.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = HealthPalette.SurfaceAlt,
    outline = HealthPalette.Line,
    outlineVariant = HealthPalette.Line,
    error = HealthPalette.Coral.strong,
    errorContainer = HealthPalette.Coral.soft
)

private val HealthShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Light theme only for now: the custom palette has no dark variant yet. */
@Composable
fun HealthAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = HealthAppTypography,
        shapes = HealthShapes,
        content = content
    )
}
