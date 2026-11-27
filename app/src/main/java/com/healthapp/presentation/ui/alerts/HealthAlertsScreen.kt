package com.healthapp.presentation.ui.alerts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.R
import com.healthapp.domain.usecase.AlertLevel
import com.healthapp.domain.usecase.VitalAlert
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.text.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.viewmodel.HealthAlertsViewModel
import java.time.format.DateTimeFormatter

@Composable
fun HealthAlertsScreen(navController: NavController, viewModel: HealthAlertsViewModel = hiltViewModel()) {
    val alerts by viewModel.alerts.collectAsState()
    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.channel_health_alerts)) { navController.popBackStack() } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (alerts.isEmpty()) {
                EmptyState(Illustration.READINGS, stringResource(R.string.alerts_empty_title), stringResource(R.string.alerts_empty_msg))
            } else {
                AlertDetail(alerts.first())
                alerts.drop(1).forEach { AlertRow(it) }
                TextButton(onClick = viewModel::clear, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.alerts_clear)) }
            }
            Text(stringResource(R.string.alerts_disclaimer), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun alertIcon(alert: VitalAlert) =
    if (alert.kind.isBloodPressure()) Icons.Rounded.MonitorHeart else Icons.Rounded.Favorite

@Composable
fun alertAccent(alert: VitalAlert): Accent = if (alert.kind.isBloodPressure()) HealthPalette.Pink else HealthPalette.Coral

/** "12:30 28/11 · while resting · Simulated" */
@Composable
fun alertMeta(alert: VitalAlert): String = listOfNotNull(
    alert.at.format(DateTimeFormatter.ofPattern("HH:mm dd/MM")),
    alert.state?.let { stringResource(it.labelRes()) },
    if (alert.simulated) stringResource(R.string.alert_simulated) else null
).joinToString(" · ")

@Composable
private fun AlertDetail(alert: VitalAlert) {
    val context = LocalContext.current
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(alertIcon(alert), alertAccent(alert))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(alert.kind.titleRes()), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                    Text(alertMeta(alert), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
                LevelChip(alert.level)
            }
            Text(stringResource(alert.kind.bodyRes(), alert.value, alert.secondary ?: 0), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Ink)
            if (alert.level == AlertLevel.URGENT) {
                FastCard()
                PrimaryButton(stringResource(R.string.call_115), {
                    // Opens the dialer with 115 filled in; the user decides whether to call.
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:115")))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Call, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FastCard() {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(HealthPalette.Coral.soft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.fast_title), fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
        listOf(R.string.fast_face, R.string.fast_arm, R.string.fast_speech, R.string.fast_time).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
        }
    }
}

@Composable
private fun AlertRow(alert: VitalAlert) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(alertIcon(alert), alertAccent(alert), size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(alert.kind.titleRes()), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                Text("${alertValue(alert)} · ${alertMeta(alert)}", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
        }
    }
}

@Composable
fun LevelChip(level: AlertLevel) {
    val (color, text) = when (level) {
        AlertLevel.URGENT -> MaterialTheme.colorScheme.error to stringResource(R.string.alert_level_urgent)
        AlertLevel.WARNING -> HealthPalette.Orange.strong to stringResource(R.string.alert_level_warning)
        AlertLevel.TREND -> HealthPalette.Indigo.strong to stringResource(R.string.alert_level_trend)
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Surface,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 10.dp, vertical = 4.dp))
}
