package com.healthapp.presentation.ui.health

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.annotation.StringRes
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.VitalMeasurement
import com.healthapp.domain.model.VitalType
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.HealthTrackingViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class HealthEntryKind { HEART_RATE, BLOOD_PRESSURE, GLUCOSE, WEIGHT, SLEEP, ACTIVITY }
private enum class TrendMetric(@StringRes val label: Int) {
    WEIGHT(R.string.metric_weight), HEART_RATE(R.string.stat_heart_rate), GLUCOSE(R.string.stat_glucose), SLEEP(R.string.stat_sleep), STEPS(R.string.metric_steps)
}
private data class TrendPoint(val date: LocalDate, val value: Float)

private val TrendMetric.accent: Accent @Composable get() = when (this) {
    TrendMetric.WEIGHT -> HealthPalette.Emerald
    TrendMetric.HEART_RATE -> HealthPalette.Coral
    TrendMetric.GLUCOSE -> HealthPalette.Indigo
    TrendMetric.SLEEP -> HealthPalette.Violet
    TrendMetric.STEPS -> HealthPalette.Orange
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HealthTrackingScreen(navController: NavController, viewModel: HealthTrackingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var entry by remember { mutableStateOf<HealthEntryKind?>(null) }
    var trendMetric by remember { mutableStateOf(TrendMetric.WEIGHT) }
    var trendDays by remember { mutableIntStateOf(7) }
    val latest = state.vitals.groupBy { it.type }.mapValues { (_, values) -> values.maxByOrNull { it.recordedAt } }

    Column(Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(stringResource(R.string.tab_health), stringResource(R.string.health_header), subtitle = stringResource(R.string.health_header_sub), accent = HealthPalette.Coral)

        GradientCard(HealthPalette.Sky.gradient, Modifier.fillMaxWidth(), onClick = { navController.navigate("health_connect") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Sync, HealthPalette.Sky, onDark = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(stringResource(R.string.hc_card_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
            }
        }

        SectionHeader(stringResource(R.string.latest_readings), modifier = Modifier.padding(top = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val hr = latest[VitalType.HEART_RATE]
            HealthValueCard(Icons.Rounded.Favorite, HealthPalette.Coral, stringResource(R.string.stat_heart_rate), hr?.let { stringResource(R.string.bpm_value, it.value.toInt()) }, hr?.recordedAt, origin(hr?.id, hr?.source)) { entry = HealthEntryKind.HEART_RATE }
            val bp = latest[VitalType.BLOOD_PRESSURE]
            HealthValueCard(Icons.Rounded.MonitorHeart, HealthPalette.Pink, stringResource(R.string.stat_bp), bp?.let { stringResource(R.string.bp_value, it.value.toInt(), it.secondaryValue?.toInt()?.toString() ?: "—") }, bp?.recordedAt, origin(bp?.id, bp?.source)) { entry = HealthEntryKind.BLOOD_PRESSURE }
            val glucose = latest[VitalType.GLUCOSE]
            HealthValueCard(Icons.Rounded.Bloodtype, HealthPalette.Indigo, stringResource(R.string.stat_glucose), glucose?.let { stringResource(R.string.value_unit, oneDecimal(it.value), it.unit) }, glucose?.recordedAt, origin(glucose?.id, glucose?.source)) { entry = HealthEntryKind.GLUCOSE }
            val weight = state.weights.firstOrNull()
            HealthValueCard(Icons.Rounded.MonitorWeight, HealthPalette.Emerald, stringResource(R.string.metric_weight), weight?.let { stringResource(R.string.kg_value, oneDecimal(it.weight)) }, weight?.recordedAt, origin(weight?.id)) { entry = HealthEntryKind.WEIGHT }
            val sleep = state.sleeps.firstOrNull()
            HealthValueCard(Icons.Rounded.Bedtime, HealthPalette.Violet, stringResource(R.string.stat_sleep), sleep?.let { stringResource(R.string.sleep_duration, it.durationMinutes / 60, it.durationMinutes % 60) }, sleep?.wakeupTime, origin(sleep?.id)) { entry = HealthEntryKind.SLEEP }
            val stepActivities = state.activities.filter { it.date == LocalDate.now().toString() }
            val stepsToday = stepActivities.takeIf { it.isNotEmpty() }?.sumOf { it.steps }
            val lastStep = stepActivities.maxByOrNull { it.recordedAt }
            HealthValueCard(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Orange, stringResource(R.string.steps_today_title), stepsToday?.let { stringResource(R.string.steps_value, grouped(it)) }, lastStep?.recordedAt, origin(lastStep?.id)) { entry = HealthEntryKind.ACTIVITY }
        }

        SectionHeader(stringResource(R.string.section_trends), modifier = Modifier.padding(top = 18.dp))
        PeriodSelector(trendDays) { trendDays = it }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendMetric.entries.forEach { metric ->
                val selected = trendMetric == metric
                FilterChip(
                    selected = selected, onClick = { trendMetric = metric }, label = { Text(stringResource(metric.label)) },
                    shape = CircleShape,
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = HealthPalette.Line, selectedBorderColor = metric.accent.strong),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = metric.accent.strong, selectedLabelColor = Color.White, containerColor = HealthPalette.Surface, labelColor = HealthPalette.Ink)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val firstDate = LocalDate.now().minusDays((trendDays - 1).toLong())
        val trendPoints = when (trendMetric) {
            TrendMetric.WEIGHT -> state.weights.map { TrendPoint(LocalDate.parse(it.date), it.weight.toFloat()) }
            TrendMetric.HEART_RATE -> state.vitals.filter { it.type == VitalType.HEART_RATE }.map { TrendPoint(it.recordedAt.toLocalDate(), it.value.toFloat()) }
            TrendMetric.GLUCOSE -> state.vitals.filter { it.type == VitalType.GLUCOSE }.map { TrendPoint(it.recordedAt.toLocalDate(), it.value.toFloat()) }
            TrendMetric.SLEEP -> state.sleeps.map { TrendPoint(LocalDate.parse(it.date), it.durationMinutes / 60f) }
            TrendMetric.STEPS -> state.activities.groupBy { LocalDate.parse(it.date) }.map { (date, activities) -> TrendPoint(date, activities.sumOf { it.steps }.toFloat()) }
        }.filter { it.date >= firstDate && it.date <= LocalDate.now() }.sortedBy { it.date }
        TrendChart(trendMetric, trendPoints)

        SectionHeader(stringResource(R.string.section_history), modifier = Modifier.padding(top = 18.dp))
        val history = state.vitals.sortedByDescending { it.recordedAt }.take(20)
        if (history.isEmpty() && state.weights.isEmpty() && state.sleeps.isEmpty() && state.activities.isEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.no_readings), Modifier.padding(18.dp), color = HealthPalette.Muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { record -> VitalHistoryRow(record, onDelete = { viewModel.deleteVital(record.id) }) }
            state.weights.take(10).forEach { HealthHistoryLabel(Icons.Rounded.MonitorWeight, HealthPalette.Emerald, stringResource(R.string.metric_weight), stringResource(R.string.kg_value, oneDecimal(it.weight)), it.recordedAt, origin(it.id)) }
            state.sleeps.take(10).forEach { HealthHistoryLabel(Icons.Rounded.Bedtime, HealthPalette.Violet, stringResource(R.string.stat_sleep), stringResource(R.string.sleep_duration, it.durationMinutes / 60, it.durationMinutes % 60), it.wakeupTime, origin(it.id)) }
            state.activities.take(10).forEach {
                HealthHistoryLabel(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Orange, stringResource(R.string.metric_steps), if (it.durationMinutes > 0) stringResource(R.string.steps_with_minutes, grouped(it.steps), it.durationMinutes) else stringResource(R.string.steps_value, grouped(it.steps)), it.recordedAt, origin(it.id))
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    entry?.let { kind ->
        HealthEntryDialog(kind, onDismiss = { entry = null }, onSave = { first, second, extra ->
            when (kind) {
                HealthEntryKind.HEART_RATE -> viewModel.addVital(VitalType.HEART_RATE, first.toDouble(), unit = "bpm")
                HealthEntryKind.BLOOD_PRESSURE -> viewModel.addVital(VitalType.BLOOD_PRESSURE, first.toDouble(), second.toDouble(), "mmHg")
                HealthEntryKind.GLUCOSE -> viewModel.addVital(VitalType.GLUCOSE, first.toDouble(), unit = extra)
                HealthEntryKind.WEIGHT -> viewModel.addWeight(first.toDouble())
                HealthEntryKind.SLEEP -> viewModel.addSleep(first, second, extra.toIntOrNull())
                HealthEntryKind.ACTIVITY -> viewModel.addSteps(first.toInt(), second.toInt())
            }
            entry = null
        })
    }
    // Errors used to be cleared without being shown; show them briefly instead.
    state.error?.let { error ->
        val message = error.resolve()
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(error) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
}

/** Where an entry came from, from its id prefix / source field: imported, sample (demo) or typed by the user. */
@Composable
private fun origin(id: String?, source: String? = null): String = when {
    source == "HEALTH_CONNECT" || id?.startsWith("hc_") == true -> "Health Connect"
    source == "DEMO" || id?.startsWith("demo_") == true -> stringResource(R.string.origin_demo)
    else -> stringResource(R.string.origin_manual)
}

@Composable
private fun sourceLabel(origin: String, time: LocalDateTime) =
    stringResource(R.string.origin_time, origin, time.format(DateTimeFormatter.ofPattern("HH:mm dd/MM")))

/** Segmented control: Ngày / Tuần / Tháng. */
@Composable
private fun PeriodSelector(days: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp).selectableGroup()) {
        listOf(1 to R.string.period_day, 7 to R.string.period_week, 30 to R.string.period_month).forEach { (d, labelRes) ->
            val label = stringResource(labelRes)
            val selected = d == days
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (selected) HealthPalette.Surface else Color.Transparent)
                    .heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(d) }),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HealthPalette.Ink else HealthPalette.Muted)
            }
        }
    }
}

@Composable
private fun HealthHistoryLabel(icon: ImageVector, accent: Accent, title: String, value: String, time: LocalDateTime, origin: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                Text(sourceLabel(origin, time), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = accent.strong)
        }
    }
}

@Composable
private fun TrendChart(metric: TrendMetric, points: List<TrendPoint>) {
    val accent = metric.accent
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(metric.label), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                Pill(stringResource(R.string.entries_count, points.size), accent)
            }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_data_in_range), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("%.1f".format(points.last().value), style = MaterialTheme.typography.headlineMedium, color = accent.strong, modifier = Modifier.padding(top = 6.dp))
                val gridColor = HealthPalette.Line
                val dotCenter = HealthPalette.Surface
                Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp, bottom = 8.dp)) {
                    val left = 8f
                    val right = size.width - 8f
                    val top = 10f
                    val bottom = size.height - 6f
                    val minValue = points.minOf { it.value }
                    val maxValue = points.maxOf { it.value }
                    val spread = (maxValue - minValue).takeIf { it > 0f } ?: 1f
                    val coords = points.mapIndexed { index, point ->
                        val x = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)
                        Offset(x, bottom - (point.value - minValue) / spread * (bottom - top))
                    }
                    for (i in 0..3) {
                        val y = top + (bottom - top) * i / 3
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 2f)
                    }
                    if (coords.size > 1) {
                        val line = Path().apply { coords.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                        val fill = Path().apply { addPath(line); lineTo(coords.last().x, bottom); lineTo(coords.first().x, bottom); close() }
                        drawPath(fill, Brush.verticalGradient(listOf(accent.strong.copy(alpha = .28f), accent.strong.copy(alpha = 0f)), startY = top, endY = bottom))
                        drawPath(line, accent.strong, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    coords.forEach { o ->
                        drawCircle(dotCenter, radius = 9f, center = o)
                        drawCircle(accent.strong, radius = 6f, center = o)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val fmt = DateTimeFormatter.ofPattern("dd/MM")
                    Text(points.first().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
                    Text(points.last().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
                }
            }
        }
    }
}

@Composable
private fun HealthValueCard(icon: ImageVector, accent: Accent, title: String, value: String?, recordedAt: LocalDateTime?, origin: String, onAdd: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                Text(value ?: stringResource(R.string.not_set), style = MaterialTheme.typography.titleMedium, color = if (value == null) HealthPalette.Subtle else HealthPalette.Ink)
                recordedAt?.let { Text(sourceLabel(origin, it), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted) }
            }
            SoftButton(stringResource(R.string.action_log), onAdd, accent, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun VitalHistoryRow(measurement: VitalMeasurement, onDelete: () -> Unit) {
    val (label, value) = when (measurement.type) {
        VitalType.HEART_RATE -> stringResource(R.string.stat_heart_rate) to "${measurement.value.toInt()} ${measurement.unit}"
        VitalType.BLOOD_PRESSURE -> stringResource(R.string.stat_bp) to "${measurement.value.toInt()}/${measurement.secondaryValue?.toInt()} ${measurement.unit}"
        VitalType.GLUCOSE -> stringResource(R.string.stat_glucose) to stringResource(R.string.value_unit, oneDecimal(measurement.value), measurement.unit)
    }
    val (icon, accent) = when (measurement.type) {
        VitalType.HEART_RATE -> Icons.Rounded.Favorite to HealthPalette.Coral
        VitalType.BLOOD_PRESSURE -> Icons.Rounded.MonitorHeart to HealthPalette.Pink
        VitalType.GLUCOSE -> Icons.Rounded.Bloodtype to HealthPalette.Indigo
    }
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                Text(sourceLabel(origin(measurement.id, measurement.source), measurement.recordedAt), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = accent.strong)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete_item, label), tint = HealthPalette.Subtle) }
        }
    }
}

@Composable
private fun HealthEntryDialog(kind: HealthEntryKind, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var first by remember(kind) { mutableStateOf("") }
    var second by remember(kind) { mutableStateOf("") }
    var unit by remember(kind) { mutableStateOf(if (kind == HealthEntryKind.GLUCOSE) "mmol/L" else "") }
    var quality by remember(kind) { mutableStateOf("") }
    val title = when (kind) {
        HealthEntryKind.HEART_RATE -> stringResource(R.string.log_heart_rate)
        HealthEntryKind.BLOOD_PRESSURE -> stringResource(R.string.log_bp)
        HealthEntryKind.GLUCOSE -> stringResource(R.string.log_glucose)
        HealthEntryKind.WEIGHT -> stringResource(R.string.log_weight)
        HealthEntryKind.SLEEP -> stringResource(R.string.log_sleep)
        HealthEntryKind.ACTIVITY -> stringResource(R.string.log_steps)
    }
    val firstLabel = when (kind) {
        HealthEntryKind.HEART_RATE -> stringResource(R.string.field_heart_rate)
        HealthEntryKind.BLOOD_PRESSURE -> stringResource(R.string.field_systolic)
        HealthEntryKind.GLUCOSE -> stringResource(R.string.stat_glucose)
        HealthEntryKind.WEIGHT -> stringResource(R.string.weight_kg)
        HealthEntryKind.SLEEP -> stringResource(R.string.field_bedtime)
        HealthEntryKind.ACTIVITY -> stringResource(R.string.field_steps)
    }
    val firstValue = first.replace(',', '.').toDoubleOrNull()
    val secondValue = second.replace(',', '.').toDoubleOrNull()
    val valid = when (kind) {
        HealthEntryKind.SLEEP -> runCatching { java.time.LocalTime.parse(first); java.time.LocalTime.parse(second) }.isSuccess && first != second && (quality.isBlank() || quality.toIntOrNull()?.let { it in 1..10 } == true)
        HealthEntryKind.ACTIVITY -> first.toIntOrNull()?.let { it > 0 } == true && second.toIntOrNull()?.let { it > 0 } == true
        HealthEntryKind.BLOOD_PRESSURE -> firstValue?.let { it > 0 } == true && secondValue?.let { it > 0 } == true
        else -> firstValue?.let { it > 0 } == true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HealthPalette.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text(firstLabel) }, placeholder = { Text(if (kind == HealthEntryKind.SLEEP) "22:30" else stringResource(R.string.enter_value)) }, keyboardOptions = KeyboardOptions(keyboardType = if (kind == HealthEntryKind.SLEEP) KeyboardType.Text else KeyboardType.Decimal), singleLine = true, shape = MaterialTheme.shapes.medium)
                if (kind == HealthEntryKind.BLOOD_PRESSURE || kind == HealthEntryKind.ACTIVITY || kind == HealthEntryKind.SLEEP) {
                    val secondLabel = when (kind) {
                        HealthEntryKind.BLOOD_PRESSURE -> stringResource(R.string.field_diastolic)
                        HealthEntryKind.ACTIVITY -> stringResource(R.string.field_walk_minutes)
                        else -> stringResource(R.string.field_wake_time)
                    }
                    OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text(secondLabel) }, placeholder = { Text(if (kind == HealthEntryKind.SLEEP) "06:30" else stringResource(R.string.enter_value)) }, keyboardOptions = KeyboardOptions(keyboardType = if (kind == HealthEntryKind.SLEEP) KeyboardType.Text else KeyboardType.Number), singleLine = true, shape = MaterialTheme.shapes.medium)
                }
                if (kind == HealthEntryKind.GLUCOSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = unit == "mmol/L", onClick = { unit = "mmol/L" }, label = { Text("mmol/L") }, shape = CircleShape)
                        FilterChip(selected = unit == "mg/dL", onClick = { unit = "mg/dL" }, label = { Text("mg/dL") }, shape = CircleShape)
                    }
                }
                if (kind == HealthEntryKind.SLEEP) OutlinedTextField(value = quality, onValueChange = { value -> if (value.length <= 2 && value.all(Char::isDigit)) quality = value }, label = { Text(stringResource(R.string.field_sleep_quality)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = MaterialTheme.shapes.medium)
                val helpText = when (kind) {
                    HealthEntryKind.HEART_RATE -> stringResource(R.string.hint_heart_rate)
                    HealthEntryKind.BLOOD_PRESSURE -> stringResource(R.string.hint_bp)
                    HealthEntryKind.GLUCOSE -> stringResource(R.string.hint_glucose)
                    HealthEntryKind.WEIGHT -> stringResource(R.string.hint_weight)
                    HealthEntryKind.SLEEP -> stringResource(R.string.hint_sleep)
                    HealthEntryKind.ACTIVITY -> stringResource(R.string.hint_steps)
                }
                Text(helpText, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(first, second, if (kind == HealthEntryKind.GLUCOSE) unit else quality) }, enabled = valid) { Text(stringResource(R.string.save_reading)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
