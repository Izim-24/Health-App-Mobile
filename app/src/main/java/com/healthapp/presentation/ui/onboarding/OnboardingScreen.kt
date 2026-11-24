package com.healthapp.presentation.ui.onboarding

import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import com.healthapp.presentation.ui.text.*
import java.time.format.FormatStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.annotation.StringRes
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.ArtworkKind
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.WellnessArtwork
import com.healthapp.presentation.viewmodel.OnboardingUiState
import com.healthapp.presentation.viewmodel.OnboardingViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class ActivityOption(val level: ActivityLevel, @StringRes val title: Int, @StringRes val detail: Int, val icon: ImageVector)

private val activityOptions = listOf(
    ActivityOption(ActivityLevel.SEDENTARY, R.string.activity_sedentary, R.string.activity_sedentary_detail, Icons.Rounded.Chair),
    ActivityOption(ActivityLevel.LIGHTLY_ACTIVE, R.string.activity_light, R.string.activity_light_detail, Icons.AutoMirrored.Rounded.DirectionsWalk),
    ActivityOption(ActivityLevel.MODERATELY_ACTIVE, R.string.activity_moderate, R.string.activity_moderate_detail, Icons.AutoMirrored.Rounded.DirectionsRun),
    ActivityOption(ActivityLevel.VERY_ACTIVE, R.string.activity_very, R.string.activity_very_detail, Icons.Rounded.FitnessCenter),
    ActivityOption(ActivityLevel.EXTRA_ACTIVE, R.string.activity_extra, R.string.activity_extra_detail, Icons.Rounded.LocalFireDepartment)
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(Modifier.fillMaxSize().background(HealthPalette.Background).padding(horizontal = 20.dp)) {
        // Top bar: back + progress + skip
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step > 0) IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = HealthPalette.Ink) }
            else Spacer(Modifier.size(48.dp))
            if (state.step > 0) {
                AccentProgress(state.step / OnboardingUiState.LAST_STEP.toFloat(), HealthPalette.Emerald, Modifier.weight(1f).padding(horizontal = 8.dp))
            } else Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.action_skip), color = HealthPalette.Muted) }
        }

        AnimatedContent(
            targetState = state.step, modifier = Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "step"
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> AboutStep(state, viewModel)
                    2 -> BodyStep(state, viewModel)
                    else -> GoalsStep(state, viewModel)
                }
            }
        }

        state.error?.let {
            // Announced by TalkBack as soon as it appears.
            Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        PrimaryButton(
            text = when {
                state.saving -> stringResource(R.string.saving)
                state.step == 0 -> stringResource(R.string.onb_start)
                state.step == OnboardingUiState.LAST_STEP -> stringResource(R.string.onb_finish)
                else -> stringResource(R.string.action_continue)
            },
            onClick = viewModel::next, enabled = !state.saving,
            icon = if (state.step == OnboardingUiState.LAST_STEP) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.padding(bottom = 20.dp, top = 8.dp)
        )
    }
}

@Composable
private fun WelcomeStep() {
    GradientCard(HealthPalette.HeroGradient, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        WellnessArtwork(ArtworkKind.GARDEN, Modifier.size(120.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onb_welcome), style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(18.dp))
    Feature(Icons.Rounded.Favorite, HealthPalette.Coral, stringResource(R.string.onb_feature_score), stringResource(R.string.onb_feature_score_detail))
    Feature(Icons.Rounded.PhotoCamera, HealthPalette.Orange, stringResource(R.string.onb_feature_food), stringResource(R.string.onb_feature_food_detail))
    Feature(Icons.Rounded.Medication, HealthPalette.Indigo, stringResource(R.string.onb_feature_meds), stringResource(R.string.onb_feature_meds_detail))
    Text(stringResource(R.string.onb_one_minute), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Feature(icon: ImageVector, accent: Accent, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AboutStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    StepTitle(stringResource(R.string.onb_about), stringResource(R.string.onb_about_sub), Illustration.ABOUT)
    OutlinedTextField(state.name, vm::setName, label = { Text(stringResource(R.string.onb_your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
    Spacer(Modifier.height(12.dp))
    AppCard(Modifier.fillMaxWidth(), onClick = { showPicker = true }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Cake, HealthPalette.Pink, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.birth_date), style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                Text(
                    state.birthDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))?.let { d -> stringResource(R.string.date_with_age, d, HealthCalculations.ageOn(state.birthDate) ?: 0) } ?: stringResource(R.string.pick_date),
                    style = MaterialTheme.typography.titleMedium, color = if (state.birthDate == null) HealthPalette.Subtle else HealthPalette.Ink
                )
            }
            Icon(Icons.Rounded.CalendarMonth, null, tint = HealthPalette.Muted)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.gender), style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Gender.MALE to R.string.gender_male, Gender.FEMALE to R.string.gender_female, Gender.OTHER to R.string.gender_other).forEach { (g, label) ->
            FilterChip(
                selected = state.gender == g, onClick = { vm.setGender(g) }, label = { Text(stringResource(label)) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HealthPalette.Primary, selectedLabelColor = Color.White)
            )
        }
    }

    if (showPicker) {
        val initial = state.birthDate ?: LocalDate.of(2000, 1, 1)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = (LocalDate.now().year - 110)..(LocalDate.now().year - 15)
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.setBirthDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text(stringResource(R.string.action_choose)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(picker) }
    }
}

@Composable
private fun BodyStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.onb_body), stringResource(R.string.onb_body_sub), Illustration.BODY)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(state.heightCm, vm::setHeight, label = { Text(stringResource(R.string.height_cm)) }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
        OutlinedTextField(state.weightKg, vm::setWeight, label = { Text(stringResource(R.string.weight_kg)) }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium)
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.activity_level), style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        activityOptions.forEach { o ->
            val selected = state.activity == o.level
            Card(
                onClick = { vm.setActivity(o.level) }, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.semantics { this.selected = selected; role = Role.RadioButton },
                colors = CardDefaults.cardColors(containerColor = if (selected) HealthPalette.Emerald.soft else HealthPalette.Surface),
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HealthPalette.Primary else HealthPalette.Line)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(o.icon, HealthPalette.Emerald, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(o.title), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                        Text(stringResource(o.detail), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                    }
                    if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = HealthPalette.Primary)
                }
            }
        }
    }
}

@Composable
private fun GoalsStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.onb_goals), stringResource(R.string.onb_goals_sub), Illustration.GOALS)
    val t = state.targets
    GradientCard(HealthPalette.Orange.gradient, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.maintenance_energy), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.calorieGoal.ifBlank { "—" }, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(stringResource(R.string.kcal_per_day), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(bottom = 6.dp))
        }
        if (t != null) Text(stringResource(R.string.bmr_times_activity, t.bmr), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(state.calorieGoal, vm::setCalorieGoal, label = { Text(stringResource(R.string.energy_goal_kcal)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
    Spacer(Modifier.height(12.dp))
    if (t != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Macro(stringResource(R.string.macro_protein), t.proteinGrams, HealthPalette.Coral, Modifier.weight(1f))
        Macro(stringResource(R.string.macro_carbs), t.carbsGrams, HealthPalette.Amber, Modifier.weight(1f))
        Macro(stringResource(R.string.macro_fat), t.fatGrams, HealthPalette.Sky, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.bmi?.let { b ->
            val cat = HealthCalculations.bmiCategory(b)
            StatTile(Icons.Rounded.MonitorWeight, stringResource(R.string.bmi_asian), oneDecimal(b), cat.title(), HealthPalette.Emerald, Modifier.weight(1f))
        }
        state.waterGoalMl?.let { w -> StatTile(Icons.Rounded.WaterDrop, stringResource(R.string.water_per_day), stringResource(R.string.ml_value, w), stringResource(R.string.water_rule), HealthPalette.Sky, Modifier.weight(1f)) }
    }
    Text(stringResource(R.string.estimate_disclaimer), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Macro(label: String, grams: Double, accent: Accent, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(accent.soft).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent.strong)
        Text(stringResource(R.string.grams_value, grams.toInt()), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String, illustration: Illustration? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        illustration?.let { IllustrationImage(it, Modifier.height(120.dp).aspectRatio(1.25f).align(Alignment.CenterHorizontally)) }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = HealthPalette.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
    }
}
