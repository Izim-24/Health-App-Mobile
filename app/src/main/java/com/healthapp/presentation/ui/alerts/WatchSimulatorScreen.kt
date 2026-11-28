package com.healthapp.presentation.ui.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.R
import com.healthapp.domain.usecase.ActivityState
import com.healthapp.domain.usecase.WatchScenario
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.text.titleRes
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.WatchSimulatorViewModel

private data class ScenarioUi(val scenario: WatchScenario, @StringRes val title: Int, @StringRes val sub: Int)

private val scenarios = listOf(
    ScenarioUi(WatchScenario.REST, R.string.sim_scenario_rest, R.string.sim_scenario_rest_sub),
    ScenarioUi(WatchScenario.RUN, R.string.sim_scenario_run, R.string.sim_scenario_run_sub),
    ScenarioUi(WatchScenario.OVER_EXERTION, R.string.sim_scenario_over, R.string.sim_scenario_over_sub),
    ScenarioUi(WatchScenario.REST_TACHY, R.string.sim_scenario_tachy, R.string.sim_scenario_tachy_sub),
    ScenarioUi(WatchScenario.BP_CRISIS, R.string.sim_scenario_bp, R.string.sim_scenario_bp_sub)
)

/** Demo/debug builds only: a pretend watch that streams readings so the alerts can be shown live. */
@Composable
fun WatchSimulatorScreen(navController: NavController, viewModel: WatchSimulatorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    fun allowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var notificationsOn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(allowed()) }
    var asked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = it }
    // Never keep simulating (and notifying) in the background.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.stop()
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationsOn = allowed()   // back from Settings
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.stop() }
    }
    fun start(s: WatchScenario) {
        // Asked once per visit: after a "no", the notice below explains it instead of a prompt on every scenario.
        if (!allowed() && !asked) { asked = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        viewModel.start(s)
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.sim_title)) { navController.popBackStack() } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.sim_sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            WatchFace(state.bpm, state.stepsPerMin, state.activity, state.simMinute, state.lastBp, state.running)
            if (!notificationsOn && asked) AppCard(Modifier.fillMaxWidth(), color = HealthPalette.Amber.soft, onClick = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) {
                Text(stringResource(R.string.sim_notifications_off), Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
            }

            val alert = state.lastAlert
            AppCard(Modifier.fillMaxWidth(), onClick = if (alert != null) ({ navController.navigate("health_alerts") }) else null) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (alert != null) {
                        IconBadge(alertIcon(alert), alertAccent(alert), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.sim_last_alert, stringResource(alert.kind.titleRes())),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                        LevelChip(alert.level)
                    } else {
                        Text(stringResource(R.string.sim_no_alert), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
                    }
                }
            }

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scenarios.forEach { ui ->
                    val selected = state.running && state.scenario == ui.scenario
                    AppCard(
                        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { start(ui.scenario) }),
                        color = if (selected) HealthPalette.Coral.soft else HealthPalette.Surface
                    ) {
                        Row(Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(stringResource(ui.title), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                                Text(stringResource(ui.sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                            }
                        }
                    }
                }
            }
            if (state.running) {
                OutlinedButton(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.sim_stop)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WatchFace(bpm: Int, stepsPerMin: Int, activity: ActivityState, minute: Int, bp: Pair<Int, Int>?, running: Boolean) {
    val ring = HealthPalette.Coral.strong
    val track = HealthPalette.Line
    val stateText = stringResource(
        when (activity) {
            ActivityState.RESTING -> R.string.sim_state_resting
            ActivityState.ACTIVE -> R.string.sim_state_active
            ActivityState.RECOVERING -> R.string.sim_state_recovering
        }
    )
    val description = stringResource(R.string.bpm_value, bpm) + ", " + stateText
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF111827))
                drawCircle(track, style = Stroke(10.dp.toPx()), radius = size.minDimension / 2 - 10.dp.toPx())
                drawArc(ring, -90f, 360f * (bpm.coerceIn(40, 200) - 40) / 160f, false,
                    topLeft = androidx.compose.ui.geometry.Offset(10.dp.toPx(), 10.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                    style = Stroke(10.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Favorite, null, tint = ring, modifier = Modifier.size(22.dp))
                Text("$bpm", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("bpm", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .7f))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.sim_steps, stepsPerMin, stepsPerMin), style = MaterialTheme.typography.labelMedium, color = Color.White)
                Text(stateText, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Emerald.soft)
                bp?.let { Text(stringResource(R.string.sim_bp, it.first, it.second), style = MaterialTheme.typography.labelMedium, color = Color.White) }
                if (running) Text(stringResource(R.string.sim_clock, minute), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
            }
        }
    }
}
