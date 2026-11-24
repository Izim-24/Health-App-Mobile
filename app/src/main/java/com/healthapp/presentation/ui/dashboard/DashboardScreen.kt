package com.healthapp.presentation.ui.dashboard

import androidx.compose.ui.res.pluralStringResource
import com.healthapp.domain.model.ScheduledDose
import com.healthapp.domain.model.DoseStatus
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.annotation.StringRes
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.NutritionSummary
import com.healthapp.domain.usecase.HealthScore
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.DashboardViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showScore by remember { mutableStateOf(false) }
    if (showScore) HealthScoreSheet(
        uiState.scoreDetails, onDismiss = { showScore = false }, onOpen = { route -> showScore = false; navController.navigate(route) },
        history = uiState.scoreHistory, insights = uiState.scoreInsights
    )

    // Reload when returning from other screens, where data may have been added.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // The only place the dashboard loads: once when it first appears, and again whenever the user comes back
        // to it (from another screen or from the background), so edits made elsewhere always show up.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshDashboard()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Tells Android when the first screen is really ready (logged as "Fully drawn"; used for startup metrics).
    androidx.activity.compose.ReportDrawnWhen { !uiState.isLoading && !uiState.scoreLoading }

    when {
        uiState.isLoading -> DashboardPlaceholder()
        uiState.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.dash_load_error), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
            Text(uiState.error.orEmpty(), color = HealthPalette.Muted)
            TextButton(onClick = { viewModel.refreshDashboard() }) { Text(stringResource(R.string.action_retry)) }
        }
        else -> Column(
            Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            // Greeting + avatar
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted
                    )
                    Text(stringResource(R.string.dash_greeting, stringResource(greeting()), uiState.userName ?: stringResource(R.string.dash_you)), style = MaterialTheme.typography.headlineSmall, color = HealthPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val profileLabel = stringResource(R.string.nav_profile)
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(HealthPalette.HeroGradient)
                        .clickable(onClickLabel = profileLabel) { navController.navigate("profile") }
                        .semantics(mergeDescendants = true) { contentDescription = profileLabel; role = Role.Button },
                    contentAlignment = Alignment.Center
                ) {
                    val initial = uiState.userName?.firstOrNull()?.uppercase()
                    if (initial != null) Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    else Icon(Icons.Rounded.Person, null, tint = Color.White)
                }
            }

            ScoreHero(uiState.healthScore, uiState.scoreDetails, uiState.scoreLoading, onClick = { showScore = true })
            Spacer(Modifier.height(12.dp))
            if (uiState.dosesToday > 0) {
                UpNextCard(uiState.nextDose, uiState.dosesDoneToday, uiState.dosesToday,
                    onTaken = { viewModel.markDose(it, DoseStatus.TAKEN) }, onOpen = { navController.navigate("medications") })
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))

            // Quick actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Rounded.Medication, stringResource(R.string.quick_meds), HealthPalette.Indigo) { navController.navigate("medications") }
                QuickAction(Icons.Rounded.WaterDrop, stringResource(R.string.quick_water), HealthPalette.Sky) { navController.navigate("water") }
                QuickAction(Icons.Rounded.AutoAwesome, stringResource(R.string.quick_ai), HealthPalette.Violet) { navController.navigate("ai_chat") }
                QuickAction(Icons.Rounded.NotificationsActive, stringResource(R.string.quick_reminders), HealthPalette.Amber) { navController.navigate("reminders") }
            }
            Spacer(Modifier.height(22.dp))

            SectionHeader(stringResource(R.string.dash_today_stats), action = stringResource(R.string.action_details)) { navController.navigate("health") }
            val toHealth = { navController.navigate("health") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.AutoMirrored.Rounded.DirectionsWalk, stringResource(R.string.stat_steps), uiState.totalSteps?.let { grouped(it) } ?: "—",
                    stringResource(if (uiState.totalSteps != null) R.string.stat_steps_today else R.string.not_recorded), HealthPalette.Orange, Modifier.weight(1f), toHealth)
                val goal = uiState.waterGoal
                // With a goal, "0 ml of 2000 ml" plus a bar reads well; without one, keep "—" for nothing logged.
                val waterValue = if (goal != null || uiState.waterIntakeMl != null) stringResource(R.string.ml_value, uiState.waterIntakeMl ?: 0) else "—"
                StatTile(Icons.Rounded.WaterDrop, stringResource(R.string.stat_water), waterValue,
                    if (goal != null) stringResource(R.string.water_of_goal, goal)
                    else stringResource(if (uiState.waterIntakeMl == null) R.string.not_recorded else R.string.today_lower),
                    HealthPalette.Sky, Modifier.weight(1f),
                    onClick = { navController.navigate("water") }, progress = goal?.let { (uiState.waterIntakeMl ?: 0).toFloat() / it })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Bedtime, stringResource(R.string.stat_sleep), uiState.sleepDurationMinutes?.let { stringResource(R.string.duration_hm, it / 60, it % 60) } ?: "—",
                    stringResource(if (uiState.sleepDurationMinutes == null) R.string.not_recorded else R.string.last_night), HealthPalette.Violet, Modifier.weight(1f), toHealth)
                StatTile(Icons.Rounded.Favorite, stringResource(R.string.stat_heart_rate), uiState.heartRateBpm?.let { stringResource(R.string.bpm_value, it) } ?: "—",
                    stringResource(if (uiState.heartRateBpm == null) R.string.no_reading else R.string.latest_reading), HealthPalette.Coral, Modifier.weight(1f), toHealth)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val bp = if (uiState.bloodPressureSystolic != null && uiState.bloodPressureDiastolic != null) "${uiState.bloodPressureSystolic}/${uiState.bloodPressureDiastolic}" else null
                StatTile(Icons.Rounded.MonitorHeart, stringResource(R.string.stat_bp), bp ?: "—", if (bp == null) stringResource(R.string.no_reading) else "mmHg", HealthPalette.Pink, Modifier.weight(1f), toHealth)
                StatTile(Icons.Rounded.Bloodtype, stringResource(R.string.stat_glucose), uiState.glucoseValue?.let { oneDecimal(it) } ?: "—",
                    uiState.glucoseUnit ?: stringResource(R.string.no_reading), HealthPalette.Indigo, Modifier.weight(1f), toHealth)
            }
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth(), onClick = { navController.navigate("profile") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.MonitorWeight, HealthPalette.Emerald)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.weight_bmi), style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                        Text(uiState.weightKg?.let { stringResource(R.string.kg_value, oneDecimal(it)) } ?: stringResource(R.string.not_set), style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink)
                    }
                    uiState.bmi?.let { Pill(stringResource(R.string.bmi_value, oneDecimal(it)), HealthPalette.Emerald) }
                }
            }
            Spacer(Modifier.height(22.dp))

            SectionHeader(stringResource(R.string.section_nutrition), action = stringResource(R.string.action_diary)) { navController.navigate("nutrition") }
            uiState.nutrition?.let { NutritionSummaryCard(it, compact = true) }
                ?: AppCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.dash_no_nutrition), Modifier.padding(18.dp), color = HealthPalette.Muted) }
            Spacer(Modifier.height(22.dp))

            SectionHeader(stringResource(R.string.section_habits), action = stringResource(R.string.action_manage)) { navController.navigate("habits") }
            HabitsCard(uiState.habitCompletedToday, uiState.totalHabitsActive, uiState.habits.filterNot { it.isCompletedToday }.take(3).map { it.id to it.name },
                onCheck = { id -> viewModel.setHabitCompleted(id, true) }, onOpen = { navController.navigate("habits") })
            Spacer(Modifier.height(22.dp))

            SectionHeader(stringResource(R.string.dash_reminders_today), action = stringResource(R.string.action_edit)) { navController.navigate("reminders") }
            AppCard(Modifier.fillMaxWidth()) {
                if (uiState.reminders.isEmpty()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.NotificationsNone, HealthPalette.Amber)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.dash_no_reminders), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        uiState.reminders.take(4).forEach { reminder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Rounded.Alarm, HealthPalette.Amber, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(reminder.title, color = HealthPalette.Ink, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Pill(reminder.reminderTime, HealthPalette.Amber)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@StringRes
private fun greeting(): Int = when (LocalTime.now().hour) {
    in 5..10 -> R.string.greet_morning
    in 11..13 -> R.string.greet_noon
    in 14..17 -> R.string.greet_afternoon
    else -> R.string.greet_evening
}

@Composable
private fun ScoreHero(score: Int?, details: com.healthapp.domain.usecase.HealthScoreResult?, calculating: Boolean, onClick: () -> Unit) {
    // Count the number up and sweep the ring when the score arrives or changes.
    val shown by animateIntAsState(score ?: 0, tween(900, easing = FastOutSlowInEasing), label = "score")
    val ring by animateFloatAsState((score ?: 0) / 100f, tween(900, easing = FastOutSlowInEasing), label = "ring")
    val heroDescription = stringResource(R.string.score_hero_cd, score?.toString() ?: "—")
    GradientCard(HealthPalette.HeroGradient, Modifier.fillMaxWidth().semantics { contentDescription = heroDescription }, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.health_score), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (score != null) shown.toString() else "—", style = MaterialTheme.typography.displayLarge, color = Color.White.copy(alpha = if (score == null && calculating) .5f else 1f))
                    if (score != null) Text(" /100", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    when {
                        details == null && calculating -> stringResource(R.string.score_calculating)
                        details?.score != null -> stringResource(R.string.score_grade_areas, details.grade!!.title(), details.components.size)
                        details != null -> pluralStringResource(R.plurals.score_need_more, (HealthScore.MIN_AREAS - details.components.size).coerceAtLeast(1), (HealthScore.MIN_AREAS - details.components.size).coerceAtLeast(1))
                        else -> stringResource(R.string.score_not_enough)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Color.White
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                CircularProgressIndicator(
                    progress = { ring }, modifier = Modifier.fillMaxSize(),
                    color = Color.White, trackColor = Color.White.copy(alpha = .22f), strokeWidth = 10.dp, strokeCap = StrokeCap.Round
                )
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        details?.topTip?.tip?.let { tip ->
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Color(0xFFFFE066), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tip.text(), style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.see_details), style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, accent: Accent, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(accent.gradient), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Ink, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun HabitsCard(done: Int, total: Int, pending: List<Pair<String, String>>, onCheck: (String) -> Unit, onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CheckCircle, HealthPalette.Violet)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_progress), style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                    Text(stringResource(R.string.done_of_total, done, total), style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink)
                }
            }
            Spacer(Modifier.height(12.dp))
            AccentProgress(if (total > 0) done.toFloat() / total else 0f, HealthPalette.Violet)
            if (total == 0) {
                Text(stringResource(R.string.dash_no_habits), Modifier.padding(top = 12.dp), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            } else if (pending.isEmpty()) {
                Text(stringResource(R.string.dash_all_habits_done), Modifier.padding(top = 12.dp), color = HealthPalette.Violet.strong, style = MaterialTheme.typography.bodyMedium)
            } else {
                Spacer(Modifier.height(6.dp))
                pending.forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = false, role = Role.Checkbox, onValueChange = { if (it) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheck(id) } }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = false, onCheckedChange = null, colors = CheckboxDefaults.colors(uncheckedColor = HealthPalette.Subtle, checkedColor = HealthPalette.Violet.strong))
                        Text(name, color = HealthPalette.Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Calories + macros. Shared with the Nutrition screen. */
@Composable
fun NutritionSummaryCard(nutrition: NutritionSummary, compact: Boolean = false) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.LocalFireDepartment, HealthPalette.Orange)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (compact) R.string.energy_today else R.string.nutrition_today), style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${nutrition.totalCalories}", style = MaterialTheme.typography.headlineMedium, color = HealthPalette.Ink)
                        Text(if (nutrition.targetCalories > 0) stringResource(R.string.kcal_of_target, nutrition.targetCalories) else stringResource(R.string.kcal_unit), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AccentProgress(if (nutrition.targetCalories > 0) nutrition.totalCalories.toFloat() / nutrition.targetCalories else 0f, HealthPalette.Orange)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroPill(stringResource(R.string.macro_protein), nutrition.totalProtein, nutrition.targetProtein, HealthPalette.Coral, Modifier.weight(1f))
                MacroPill(stringResource(R.string.macro_carbs), nutrition.totalCarbs, nutrition.targetCarbs, HealthPalette.Amber, Modifier.weight(1f))
                MacroPill(stringResource(R.string.macro_fat), nutrition.totalFat, nutrition.targetFat, HealthPalette.Sky, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroPill(label: String, value: Double, target: Double, accent: Accent, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(accent.soft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent.strong)
        Text(stringResource(R.string.grams_value, value.toInt()), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
        if (target > 0) AccentProgress((value / target).toFloat(), accent, track = HealthPalette.Surface, height = 5.dp)
    }
}

/** Grey shapes in the dashboard layout, gently pulsing, while today's data loads. */
@Composable
private fun DashboardPlaceholder() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "placeholder")
    val alpha by pulse.animateFloat(
        0.45f, 0.9f,
        androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "alpha"
    )
    val block = HealthPalette.SurfaceAlt.copy(alpha = alpha)
    val loading = stringResource(R.string.loading_dashboard)
    Column(
        Modifier.fillMaxSize().background(HealthPalette.Background).padding(horizontal = 20.dp)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(.45f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(block))
        Box(Modifier.fillMaxWidth(.7f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(block))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)).background(block))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(4) { Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(block)) }
        }
        Spacer(Modifier.height(6.dp))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(104.dp).clip(RoundedCornerShape(22.dp)).background(block)) }
            }
        }
    }
}

/** The next medication dose, with a one-tap "Taken"; overdue doses turn coral. */
@Composable
private fun UpNextCard(dose: ScheduledDose?, done: Int, total: Int, onTaken: (ScheduledDose) -> Unit, onOpen: () -> Unit) {
    val now = java.time.LocalTime.now()
    val overdue = dose != null && !dose.time.isAfter(now)
    val accent = if (overdue) HealthPalette.Coral else HealthPalette.Indigo
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Medication, accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (dose == null) {
                    Text(stringResource(R.string.all_doses_done), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                } else {
                    val time = com.healthapp.domain.usecase.MedicationSchedule.formatTime(dose.time)
                    Text(
                        if (overdue) stringResource(R.string.up_next_overdue, time) else stringResource(R.string.up_next) + " · " + stringResource(R.string.up_next_at, time),
                        style = MaterialTheme.typography.labelMedium, color = accent.strong
                    )
                    Text(listOf(dose.medication.name, dose.medication.dosage).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(pluralStringResource(R.plurals.doses_progress, total, done, total), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            if (dose != null) {
                Spacer(Modifier.width(8.dp))
                SoftButton(stringResource(R.string.action_taken), { onTaken(dose) }, accent, icon = Icons.Rounded.Check)
            }
        }
    }
}
