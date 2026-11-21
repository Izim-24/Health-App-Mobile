package com.healthapp.presentation.ui.profile

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import com.healthapp.presentation.viewmodel.ProfileViewModel

private val genderLabels = mapOf(Gender.MALE to R.string.gender_male, Gender.FEMALE to R.string.gender_female, Gender.OTHER to R.string.gender_other)
private val activityLabels = mapOf(
    ActivityLevel.SEDENTARY to R.string.activity_sedentary,
    ActivityLevel.LIGHTLY_ACTIVE to R.string.activity_light_long,
    ActivityLevel.MODERATELY_ACTIVE to R.string.activity_moderate_long,
    ActivityLevel.VERY_ACTIVE to R.string.activity_very_long,
    ActivityLevel.EXTRA_ACTIVE to R.string.activity_extra_long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(navController: NavController, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var activityMenu by remember { mutableStateOf(false) }

    val fieldShape = MaterialTheme.shapes.medium
    val height = state.heightCm.toIntOrNull()
    val weight = state.weightKg.replace(',', '.').toDoubleOrNull()
    val bmi = if (height != null && weight != null) HealthCalculations.bmi(height, weight) else null

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.nav_profile)) { navController.popBackStack() } }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        GradientCard(HealthPalette.HeroGradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                    Text(state.name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name.ifBlank { stringResource(R.string.profile_yours) }, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    Text(stringResource(R.string.stored_on_device), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
                }
            }
            if (bmi != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat("BMI", oneDecimal(bmi), HealthCalculations.bmiCategory(bmi).title())
                    weight?.let { HealthCalculations.suggestedWaterMl(it) }?.let { HeroStat(stringResource(R.string.water_suggested), stringResource(R.string.ml_value, it), stringResource(R.string.per_day)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.loading) CircularProgressIndicator(color = HealthPalette.Primary) else {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.personal_info), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                    OutlinedTextField(state.name, { viewModel.update(it, state.birthDate, state.heightCm, state.weightKg, state.calorieGoal) }, label = { Text(stringResource(R.string.full_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape)
                    OutlinedTextField(state.birthDate, { viewModel.update(state.name, it, state.heightCm, state.weightKg, state.calorieGoal) }, label = { Text(stringResource(R.string.birth_date_iso)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape)
                    Text(stringResource(R.string.gender), style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gender.entries.forEach { g ->
                            FilterChip(
                                selected = state.gender == g, onClick = { viewModel.setGender(g) }, label = { Text(genderLabels[g]?.let { stringResource(it) }.orEmpty()) }, shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HealthPalette.Primary, selectedLabelColor = Color.White)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(state.heightCm, { viewModel.update(state.name, state.birthDate, it, state.weightKg, state.calorieGoal) }, label = { Text(stringResource(R.string.height_short)) }, modifier = Modifier.weight(1f), singleLine = true, shape = fieldShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(state.weightKg, { viewModel.update(state.name, state.birthDate, state.heightCm, it, state.calorieGoal) }, label = { Text(stringResource(R.string.weight_short)) }, modifier = Modifier.weight(1f), singleLine = true, shape = fieldShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    ExposedDropdownMenuBox(expanded = activityMenu, onExpandedChange = { activityMenu = it }) {
                        OutlinedTextField(
                            value = state.activityLevel?.let { activityLabels[it] }?.let { stringResource(it) }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.activity_level)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activityMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = fieldShape
                        )
                        ExposedDropdownMenu(expanded = activityMenu, onDismissRequest = { activityMenu = false }) {
                            ActivityLevel.entries.forEach { level ->
                                DropdownMenuItem(text = { Text(activityLabels[level]?.let { stringResource(it) }.orEmpty()) }, onClick = { viewModel.setActivityLevel(level); activityMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(state.calorieGoal, { viewModel.update(state.name, state.birthDate, state.heightCm, state.weightKg, it) }, label = { Text(stringResource(R.string.energy_goal_day)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    SoftButton(stringResource(R.string.calc_targets), viewModel::calculateTargets, HealthPalette.Orange, Modifier.fillMaxWidth(), icon = Icons.Default.Calculate)
                    state.suggestion?.let {
                        Text(it.resolve(), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HealthPalette.Orange.soft).padding(12.dp))
                    }
                    Text(
                        if (bmi != null) stringResource(R.string.bmi_line, oneDecimal(bmi), HealthCalculations.bmiCategory(bmi).title(), bmiNormalRange()) else stringResource(R.string.bmi_needs_data),
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                    )
                    state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (state.saved) Pill(stringResource(R.string.profile_saved), HealthPalette.Emerald)
                    PrimaryButton(stringResource(R.string.save_profile), viewModel::save)
                }
            }
        }
        SectionHeader(stringResource(R.string.section_tools), modifier = Modifier.padding(top = 18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkRow(Icons.Default.Assessment, HealthPalette.Indigo, stringResource(R.string.link_reports)) { navController.navigate("reports") }
            LinkRow(Icons.Default.LocalDrink, HealthPalette.Sky, stringResource(R.string.link_water)) { navController.navigate("water") }
            LinkRow(Icons.Default.Medication, HealthPalette.Indigo, stringResource(R.string.link_meds)) { navController.navigate("medications") }
            LinkRow(Icons.Default.Notifications, HealthPalette.Amber, stringResource(R.string.link_reminders)) { navController.navigate("reminders") }
            LinkRow(Icons.Default.ChatBubbleOutline, HealthPalette.Violet, stringResource(R.string.link_ai)) { navController.navigate("ai_chat") }
            LinkRow(Icons.Default.Settings, HealthPalette.Emerald, stringResource(R.string.link_settings)) { navController.navigate("settings") }
        }
        Spacer(Modifier.height(28.dp))
    }
    }
}

@Composable
private fun RowScope.HeroStat(label: String, value: String, detail: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
    }
}


@Composable
private fun LinkRow(icon: ImageVector, accent: com.healthapp.presentation.ui.theme.Accent, label: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 40.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, color = HealthPalette.Subtle, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}
