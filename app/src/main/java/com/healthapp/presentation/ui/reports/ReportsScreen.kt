package com.healthapp.presentation.ui.reports

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.ReportPeriod
import com.healthapp.presentation.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.link_reports)) { navController.popBackStack() } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.reports_sub), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp).selectableGroup()) {
                ReportPeriod.entries.forEach { period ->
                    val selected = state.period == period
                    Box(
                        Modifier.weight(1f).clip(CircleShape).background(if (selected) HealthPalette.Indigo.strong else Color.Transparent)
                            .heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.select(period) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(period.label), style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else HealthPalette.Muted)
                    }
                }
            }
            Text(state.range, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Spacer(Modifier.height(14.dp))
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthPalette.Indigo.strong)
                state.error != null -> Text(state.error!!.resolve(), color = MaterialTheme.colorScheme.error)
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportCard(Icons.Rounded.LocalFireDepartment, HealthPalette.Orange, stringResource(R.string.section_nutrition),
                        if (state.nutritionDays == 0) stringResource(R.string.no_meals_period) else stringResource(R.string.avg_over_days, state.nutritionDays)) {
                        if (state.nutritionDays > 0) {
                            ReportItem(stringResource(R.string.energy), stringResource(R.string.kcal_per_day_v, state.avgCalories))
                            ReportItem(stringResource(R.string.macro_protein), stringResource(R.string.g_per_day_v, state.avgProtein.toString()))
                            ReportItem(stringResource(R.string.macro_carbs), stringResource(R.string.g_per_day_v, state.avgCarbs.toString()))
                            ReportItem(stringResource(R.string.macro_fat), stringResource(R.string.g_per_day_v, state.avgFat.toString()))
                        }
                    }
                    ReportCard(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Coral, stringResource(R.string.area_activity),
                        if (state.activityDays == 0) stringResource(R.string.no_activity_period) else stringResource(R.string.avg_over_days, state.activityDays)) {
                        if (state.activityDays > 0) {
                            ReportItem(stringResource(R.string.stat_steps), stringResource(R.string.steps_per_day_v, grouped(state.avgSteps)))
                            ReportItem(stringResource(R.string.total_time), stringResource(R.string.minutes_v, state.totalExerciseMinutes))
                            ReportItem(stringResource(R.string.energy_recorded), stringResource(R.string.kcal_v, state.totalCaloriesBurned))
                        }
                    }
                    ReportCard(Icons.Rounded.WaterDrop, HealthPalette.Sky, stringResource(R.string.stat_water),
                        if (state.waterDays == 0) stringResource(R.string.no_water_period) else stringResource(R.string.avg_over_days, state.waterDays)) {
                        if (state.waterDays > 0) ReportItem(stringResource(R.string.water_amount), stringResource(R.string.ml_per_day_v, state.avgWaterMl))
                    }
                    ReportCard(Icons.Rounded.CheckCircle, HealthPalette.Violet, stringResource(R.string.section_habits),
                        if (state.activeHabits == 0) stringResource(R.string.no_habits_created) else stringResource(R.string.tracking_habits, state.activeHabits)) {
                        if (state.activeHabits > 0) ReportItem(stringResource(R.string.completions), "${state.completedHabits}")
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ReportCard(icon: ImageVector, accent: Accent, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ReportItem(label: String, value: String) {
    HorizontalDivider(color = HealthPalette.Line, modifier = Modifier.padding(vertical = 4.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
    }
}
