package com.healthapp.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** A strong colour for icons/text/fills, its tint for backgrounds, and a gradient for hero cards. */
@Immutable
data class Accent(val strong: Color, val soft: Color, val gradient: Brush)

/** Every colour the app uses, for one theme (light or dark). */
@Immutable
data class HealthColors(
    val isDark: Boolean,
    val background: Color, val surface: Color, val surfaceAlt: Color,
    val ink: Color, val muted: Color, val subtle: Color, val line: Color,
    val primary: Color, val primaryDark: Color, val primarySoft: Color,
    val emerald: Accent, val coral: Accent, val orange: Accent, val sky: Accent,
    val violet: Accent, val pink: Accent, val amber: Accent, val indigo: Accent,
    val heroGradient: Brush
)

private data class AccentSpec(val strong: Long, val lightSoft: Long, val from: Long, val to: Long)

private val specs = mapOf(
    "emerald" to AccentSpec(0xFF12B886, 0xFFE3FAF2, 0xFF20C997, 0xFF0CA678),
    "coral" to AccentSpec(0xFFFA5252, 0xFFFFEDED, 0xFFFF8787, 0xFFFA5252),
    "orange" to AccentSpec(0xFFFD7E14, 0xFFFFF1E3, 0xFFFFA94D, 0xFFF76707),
    "sky" to AccentSpec(0xFF228BE6, 0xFFE5F3FF, 0xFF4DABF7, 0xFF1C7ED6),
    "violet" to AccentSpec(0xFF7048E8, 0xFFF1ECFF, 0xFF9775FA, 0xFF6741D9),
    "pink" to AccentSpec(0xFFE64980, 0xFFFFEBF3, 0xFFF783AC, 0xFFD6336C),
    "amber" to AccentSpec(0xFFF59F00, 0xFFFFF7DB, 0xFFFFD43B, 0xFFF59F00),
    "indigo" to AccentSpec(0xFF4263EB, 0xFFEBEFFF, 0xFF748FFC, 0xFF4263EB)
)

private fun build(dark: Boolean): HealthColors {
    val surface = if (dark) Color(0xFF1A1D27) else Color.White
    fun accent(key: String): Accent {
        val s = specs.getValue(key)
        val strong = Color(s.strong)
        // Dark: brighten the strong colour slightly for contrast; tint = strong at low alpha over the dark surface.
        val darkStrong = Color(s.from)
        return Accent(
            strong = if (dark) darkStrong else strong,
            soft = if (dark) strong.copy(alpha = 0.22f).compositeOver(surface) else Color(s.lightSoft),
            gradient = Brush.linearGradient(listOf(Color(s.from), Color(s.to)))
        )
    }
    return HealthColors(
        isDark = dark,
        background = if (dark) Color(0xFF0F1118) else Color(0xFFF6F7FB),
        surface = surface,
        surfaceAlt = if (dark) Color(0xFF242836) else Color(0xFFF0F2F8),
        ink = if (dark) Color(0xFFECEEF6) else Color(0xFF181B2C),
        muted = if (dark) Color(0xFFA3A9BE) else Color(0xFF6B7088),
        subtle = if (dark) Color(0xFF6F7590) else Color(0xFFA3A8BD),
        line = if (dark) Color(0xFF2B3040) else Color(0xFFE6E8F0),
        primary = if (dark) Color(0xFF20C997) else Color(0xFF12B886),
        primaryDark = Color(0xFF0B8A65),
        primarySoft = if (dark) Color(0xFF12B886).copy(alpha = 0.22f).compositeOver(surface) else Color(0xFFE3FAF2),
        emerald = accent("emerald"), coral = accent("coral"), orange = accent("orange"), sky = accent("sky"),
        violet = accent("violet"), pink = accent("pink"), amber = accent("amber"), indigo = accent("indigo"),
        heroGradient = Brush.linearGradient(listOf(Color(0xFF7048E8), Color(0xFF4263EB), Color(0xFF15AABF)))
    )
}

val LightHealthColors = build(dark = false)
val DarkHealthColors = build(dark = true)

/** Provided by [HealthAppTheme]; defaults to light so previews/tests without the theme still render. */
val LocalHealthColors = staticCompositionLocalOf { LightHealthColors }

/**
 * Bright, multi-colour palette on a clean neutral base, light or dark. Each health area has its own accent.
 * Values come from the current theme, so every screen switches automatically.
 */
object HealthPalette {
    private val c: HealthColors @Composable @ReadOnlyComposable get() = LocalHealthColors.current

    val Background: Color @Composable @ReadOnlyComposable get() = c.background
    val Surface: Color @Composable @ReadOnlyComposable get() = c.surface
    val SurfaceAlt: Color @Composable @ReadOnlyComposable get() = c.surfaceAlt
    val Ink: Color @Composable @ReadOnlyComposable get() = c.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = c.muted
    val Subtle: Color @Composable @ReadOnlyComposable get() = c.subtle
    val Line: Color @Composable @ReadOnlyComposable get() = c.line

    val Primary: Color @Composable @ReadOnlyComposable get() = c.primary
    val PrimaryDark: Color @Composable @ReadOnlyComposable get() = c.primaryDark
    val PrimarySoft: Color @Composable @ReadOnlyComposable get() = c.primarySoft

    val Emerald: Accent @Composable @ReadOnlyComposable get() = c.emerald   // overall, habits
    val Coral: Accent @Composable @ReadOnlyComposable get() = c.coral       // heart, blood pressure
    val Orange: Accent @Composable @ReadOnlyComposable get() = c.orange     // activity, calories
    val Sky: Accent @Composable @ReadOnlyComposable get() = c.sky           // water, glucose
    val Violet: Accent @Composable @ReadOnlyComposable get() = c.violet     // sleep, AI
    val Pink: Accent @Composable @ReadOnlyComposable get() = c.pink         // mind
    val Amber: Accent @Composable @ReadOnlyComposable get() = c.amber       // nutrition, tips
    val Indigo: Accent @Composable @ReadOnlyComposable get() = c.indigo     // reports, medication

    /** Hero gradient for the dashboard's overview card. */
    val HeroGradient: Brush @Composable @ReadOnlyComposable get() = c.heroGradient
}
