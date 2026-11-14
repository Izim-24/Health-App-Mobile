package com.healthapp.presentation.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A strong colour for icons/text/fills and its pale tint for backgrounds. */
data class Accent(val strong: Color, val soft: Color, val gradient: Brush)

private fun accent(strong: Long, soft: Long, gradFrom: Long, gradTo: Long) =
    Accent(Color(strong), Color(soft), Brush.linearGradient(listOf(Color(gradFrom), Color(gradTo))))

/**
 * Bright, multi-colour palette on a clean neutral base. Each health area has its own accent so
 * screens are recognisable at a glance; green stays the brand colour but is no longer the only one.
 */
object HealthPalette {
    // Neutrals
    val Background = Color(0xFFF6F7FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF0F2F8)
    val Ink = Color(0xFF181B2C)
    val Muted = Color(0xFF6B7088)
    val Subtle = Color(0xFFA3A8BD)
    val Line = Color(0xFFE6E8F0)

    // Brand
    val Primary = Color(0xFF12B886)       // fresh emerald
    val PrimaryDark = Color(0xFF0B8A65)
    val PrimarySoft = Color(0xFFE3FAF2)

    // Area accents
    val Emerald = accent(0xFF12B886, 0xFFE3FAF2, 0xFF20C997, 0xFF0CA678)   // overall, habits
    val Coral = accent(0xFFFA5252, 0xFFFFEDED, 0xFFFF8787, 0xFFFA5252)     // heart, blood pressure
    val Orange = accent(0xFFFD7E14, 0xFFFFF1E3, 0xFFFFA94D, 0xFFF76707)    // activity, calories
    val Sky = accent(0xFF228BE6, 0xFFE5F3FF, 0xFF4DABF7, 0xFF1C7ED6)       // water, glucose
    val Violet = accent(0xFF7048E8, 0xFFF1ECFF, 0xFF9775FA, 0xFF6741D9)    // sleep, AI
    val Pink = accent(0xFFE64980, 0xFFFFEBF3, 0xFFF783AC, 0xFFD6336C)      // mind
    val Amber = accent(0xFFF59F00, 0xFFFFF7DB, 0xFFFFD43B, 0xFFF59F00)     // nutrition, tips
    val Indigo = accent(0xFF4263EB, 0xFFEBEFFF, 0xFF748FFC, 0xFF4263EB)    // reports, medication

    /** Hero gradient for the dashboard's overview card. */
    val HeroGradient = Brush.linearGradient(listOf(Color(0xFF7048E8), Color(0xFF4263EB), Color(0xFF15AABF)))
}
