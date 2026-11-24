package com.healthapp.presentation.ui.healthconnect

import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.selection.toggleable
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.healthapp.domain.model.HcAvailability
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.HealthConnectViewModel
import java.time.format.DateTimeFormatter

private val dataTypes = listOf(
    HealthPermission.getReadPermission(StepsRecord::class) to R.string.stat_steps,
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) to R.string.hc_active_energy,
    HealthPermission.getReadPermission(SleepSessionRecord::class) to R.string.stat_sleep,
    HealthPermission.getReadPermission(HeartRateRecord::class) to R.string.stat_heart_rate,
    HealthPermission.getReadPermission(WeightRecord::class) to R.string.metric_weight,
    HealthPermission.getReadPermission(BloodPressureRecord::class) to R.string.stat_bp,
    HealthPermission.getReadPermission(BloodGlucoseRecord::class) to R.string.stat_glucose
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(navController: NavController, viewModel: HealthConnectViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(viewModel.permissionContract) { viewModel.onPermissionsResult(it) }

    // Permissions can change in Health Connect while we're in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar("Health Connect") { navController.popBackStack() }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IllustrationImage(Illustration.SYNC, Modifier.fillMaxWidth().height(150.dp))
            Text(
                stringResource(R.string.hc_intro),
                color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium
            )

            when (state.availability) {
                HcAvailability.NOT_SUPPORTED -> InfoCard(stringResource(R.string.hc_unsupported), stringResource(R.string.hc_unsupported_sub))
                HcAvailability.NEEDS_UPDATE -> {
                    InfoCard(stringResource(R.string.hc_install), stringResource(R.string.hc_install_sub))
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_play)) }
                }
                HcAvailability.AVAILABLE -> {
                    StatusCard(state.connected, state.syncing, state.lastSync?.format(DateTimeFormatter.ofPattern("HH:mm dd/MM")), state.lastSummary?.let {
                        stringResource(R.string.hc_last_summary, it.days, it.sleep, it.heartRate, it.weight, it.bloodPressure, it.glucose)
                    })

                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.data_types), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
                            val grantedText = stringResource(R.string.access_granted)
                            val notGrantedText = stringResource(R.string.access_not_granted)
                            dataTypes.forEach { (perm, label) ->
                                val ok = perm in state.granted
                                Row(Modifier.semantics(mergeDescendants = true) { stateDescription = if (ok) grantedText else notGrantedText },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ok) HealthPalette.Sky.strong else HealthPalette.Subtle, modifier = Modifier.size(20.dp))
                                    Text(stringResource(label), color = if (ok) HealthPalette.Ink else HealthPalette.Muted)
                                }
                            }
                        }
                    }

                    if (!state.connected || state.granted.size < viewModel.permissions.size) {
                        PrimaryButton(stringResource(if (state.connected) R.string.hc_grant_more else R.string.hc_connect), { launcher.launch(viewModel.permissions) }, color = HealthPalette.Sky.strong)
                    }
                    if (state.connected) {
                        OutlinedButton(onClick = viewModel::syncNow, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sync_now))
                        }
                        TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.disconnect))
                        }
                    }
                    Text(
                        stringResource(R.string.hc_sync_note),
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                    )
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            TextButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.hc_how_used)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDisconnect) {
        var removeImported by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.hc_disconnect_q)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.hc_disconnect_body))
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = removeImported, role = Role.Checkbox, onValueChange = { removeImported = it }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = removeImported, onCheckedChange = null)
                        Text(stringResource(R.string.hc_remove_imported))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.disconnect(removeImported); confirmDisconnect = false }) { Text(stringResource(R.string.disconnect)) } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            text = { Box(Modifier.heightIn(max = 480.dp)) { HealthDataPrivacy() } },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.action_close)) } }
        )
    }
}

@Composable
private fun StatusCard(connected: Boolean, syncing: Boolean, lastSync: String?, summary: String?) {
    AppCard(Modifier.fillMaxWidth(), color = if (connected) HealthPalette.Sky.strong else HealthPalette.Surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val fg = if (connected) HealthPalette.Surface else HealthPalette.Ink
            Text(stringResource(if (connected) R.string.connected_caps else R.string.not_connected_caps), color = if (connected) HealthPalette.Emerald.soft else HealthPalette.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(
                when {
                    syncing -> stringResource(R.string.syncing)
                    lastSync != null -> stringResource(R.string.synced_at, lastSync)
                    connected -> stringResource(R.string.not_synced)
                    else -> stringResource(R.string.connect_to_import)
                },
                color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthPalette.Emerald.soft)
            summary?.let { Text(it, color = fg.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Orange.soft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
        }
    }
}
