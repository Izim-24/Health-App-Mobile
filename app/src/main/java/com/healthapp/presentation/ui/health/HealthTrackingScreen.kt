package com.healthapp.presentation.ui.health

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
private enum class TrendMetric(val label: String) { WEIGHT("Cân nặng"), HEART_RATE("Nhịp tim"), GLUCOSE("Đường huyết"), SLEEP("Giấc ngủ"), STEPS("Bước chân") }
private data class TrendPoint(val date: LocalDate, val value: Float)

private val TrendMetric.accent: Accent get() = when (this) {
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
        ScreenHeader("Sức khỏe", "Theo dõi chỉ số", "Nhập số đo từ máy đo, đồng hồ hoặc đồng bộ tự động.", HealthPalette.Coral)

        GradientCard(HealthPalette.Sky.gradient, Modifier.fillMaxWidth(), onClick = { navController.navigate("health_connect") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Sync, HealthPalette.Sky, onDark = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Tự nhập bước chân, giấc ngủ, nhịp tim từ đồng hồ và ứng dụng khác", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
            }
        }

        SectionHeader("Chỉ số gần nhất", modifier = Modifier.padding(top = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val hr = latest[VitalType.HEART_RATE]
            HealthValueCard(Icons.Rounded.Favorite, HealthPalette.Coral, "Nhịp tim", hr?.let { "${it.value.toInt()} bpm" }, hr?.recordedAt, hr?.source == "HEALTH_CONNECT") { entry = HealthEntryKind.HEART_RATE }
            val bp = latest[VitalType.BLOOD_PRESSURE]
            HealthValueCard(Icons.Rounded.MonitorHeart, HealthPalette.Pink, "Huyết áp", bp?.let { "${it.value.toInt()}/${it.secondaryValue?.toInt() ?: "—"} mmHg" }, bp?.recordedAt, bp?.source == "HEALTH_CONNECT") { entry = HealthEntryKind.BLOOD_PRESSURE }
            val glucose = latest[VitalType.GLUCOSE]
            HealthValueCard(Icons.Rounded.Bloodtype, HealthPalette.Indigo, "Đường huyết", glucose?.let { "${"%.1f".format(it.value)} ${it.unit}" }, glucose?.recordedAt, glucose?.source == "HEALTH_CONNECT") { entry = HealthEntryKind.GLUCOSE }
            val weight = state.weights.firstOrNull()
            HealthValueCard(Icons.Rounded.MonitorWeight, HealthPalette.Emerald, "Cân nặng", weight?.let { "${"%.1f".format(it.weight)} kg" }, weight?.recordedAt, weight?.id?.startsWith("hc_") == true) { entry = HealthEntryKind.WEIGHT }
            val sleep = state.sleeps.firstOrNull()
            HealthValueCard(Icons.Rounded.Bedtime, HealthPalette.Violet, "Giấc ngủ", sleep?.let { "${it.durationMinutes / 60} giờ ${it.durationMinutes % 60} phút" }, sleep?.wakeupTime, sleep?.id?.startsWith("hc_") == true) { entry = HealthEntryKind.SLEEP }
            val stepActivities = state.activities.filter { it.date == LocalDate.now().toString() }
            val stepsToday = stepActivities.takeIf { it.isNotEmpty() }?.sumOf { it.steps }
            val lastStep = stepActivities.maxByOrNull { it.recordedAt }
            HealthValueCard(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Orange, "Số bước hôm nay", stepsToday?.let { "$it bước" }, lastStep?.recordedAt, lastStep?.id?.startsWith("hc_") == true) { entry = HealthEntryKind.ACTIVITY }
        }

        SectionHeader("Xu hướng", modifier = Modifier.padding(top = 18.dp))
        PeriodSelector(trendDays) { trendDays = it }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendMetric.entries.forEach { metric ->
                val selected = trendMetric == metric
                FilterChip(
                    selected = selected, onClick = { trendMetric = metric }, label = { Text(metric.label) },
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

        SectionHeader("Lịch sử đo", modifier = Modifier.padding(top = 18.dp))
        val history = state.vitals.sortedByDescending { it.recordedAt }.take(20)
        if (history.isEmpty() && state.weights.isEmpty() && state.sleeps.isEmpty() && state.activities.isEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Chưa có lần đo nào. Chạm “Ghi” ở một chỉ số để bắt đầu.", Modifier.padding(18.dp), color = HealthPalette.Muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { record -> VitalHistoryRow(record, onDelete = { viewModel.deleteVital(record.id) }) }
            state.weights.take(10).forEach { HealthHistoryLabel(Icons.Rounded.MonitorWeight, HealthPalette.Emerald, "Cân nặng", "${"%.1f".format(it.weight)} kg", it.recordedAt, it.id.startsWith("hc_")) }
            state.sleeps.take(10).forEach { HealthHistoryLabel(Icons.Rounded.Bedtime, HealthPalette.Violet, "Giấc ngủ", "${it.durationMinutes / 60} giờ ${it.durationMinutes % 60} phút", it.wakeupTime, it.id.startsWith("hc_")) }
            state.activities.take(10).forEach {
                HealthHistoryLabel(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Orange, "Bước chân", "${it.steps} bước" + if (it.durationMinutes > 0) " · ${it.durationMinutes} phút" else "", it.recordedAt, it.id.startsWith("hc_"))
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
    state.error?.let { error ->
        LaunchedEffect(error) { viewModel.clearError() }
    }
}

private fun sourceLabel(fromHealthConnect: Boolean, time: LocalDateTime) =
    "${if (fromHealthConnect) "Health Connect" else "Nhập tay"} · ${time.format(DateTimeFormatter.ofPattern("HH:mm dd/MM"))}"

/** Segmented control: Ngày / Tuần / Tháng. */
@Composable
private fun PeriodSelector(days: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp)) {
        listOf(1 to "Ngày", 7 to "Tuần", 30 to "Tháng").forEach { (d, label) ->
            val selected = d == days
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (selected) HealthPalette.Surface else Color.Transparent)
                    .clickable { onSelect(d) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) HealthPalette.Ink else HealthPalette.Muted)
            }
        }
    }
}

@Composable
private fun HealthHistoryLabel(icon: ImageVector, accent: Accent, title: String, value: String, time: LocalDateTime, fromHealthConnect: Boolean) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                Text(sourceLabel(fromHealthConnect, time), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
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
                Text(metric.label, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
                Pill("${points.size} lần ghi", accent)
            }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có dữ liệu trong khoảng này", color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("%.1f".format(points.last().value), style = MaterialTheme.typography.headlineMedium, color = accent.strong, modifier = Modifier.padding(top = 6.dp))
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
                        drawLine(HealthPalette.Line, Offset(left, y), Offset(right, y), strokeWidth = 2f)
                    }
                    if (coords.size > 1) {
                        val line = Path().apply { coords.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                        val fill = Path().apply { addPath(line); lineTo(coords.last().x, bottom); lineTo(coords.first().x, bottom); close() }
                        drawPath(fill, Brush.verticalGradient(listOf(accent.strong.copy(alpha = .28f), accent.strong.copy(alpha = 0f)), startY = top, endY = bottom))
                        drawPath(line, accent.strong, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    coords.forEach { o ->
                        drawCircle(Color.White, radius = 9f, center = o)
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
private fun HealthValueCard(icon: ImageVector, accent: Accent, title: String, value: String?, recordedAt: LocalDateTime?, fromHealthConnect: Boolean, onAdd: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                Text(value ?: "Chưa có", style = MaterialTheme.typography.titleMedium, color = if (value == null) HealthPalette.Subtle else HealthPalette.Ink)
                recordedAt?.let { Text(sourceLabel(fromHealthConnect, it), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted) }
            }
            SoftButton("Ghi", onAdd, accent, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun VitalHistoryRow(measurement: VitalMeasurement, onDelete: () -> Unit) {
    val (label, value) = when (measurement.type) {
        VitalType.HEART_RATE -> "Nhịp tim" to "${measurement.value.toInt()} ${measurement.unit}"
        VitalType.BLOOD_PRESSURE -> "Huyết áp" to "${measurement.value.toInt()}/${measurement.secondaryValue?.toInt()} ${measurement.unit}"
        VitalType.GLUCOSE -> "Đường huyết" to "${"%.1f".format(measurement.value)} ${measurement.unit}"
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
                Text(sourceLabel(measurement.source == "HEALTH_CONNECT", measurement.recordedAt), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = accent.strong)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Xóa $label", tint = HealthPalette.Subtle) }
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
        HealthEntryKind.HEART_RATE -> "Ghi nhịp tim"
        HealthEntryKind.BLOOD_PRESSURE -> "Ghi huyết áp"
        HealthEntryKind.GLUCOSE -> "Ghi đường huyết"
        HealthEntryKind.WEIGHT -> "Ghi cân nặng"
        HealthEntryKind.SLEEP -> "Ghi giấc ngủ"
        HealthEntryKind.ACTIVITY -> "Ghi bước chân"
    }
    val firstLabel = when (kind) {
        HealthEntryKind.HEART_RATE -> "Nhịp tim (bpm)"
        HealthEntryKind.BLOOD_PRESSURE -> "Tâm thu (mmHg)"
        HealthEntryKind.GLUCOSE -> "Đường huyết"
        HealthEntryKind.WEIGHT -> "Cân nặng (kg)"
        HealthEntryKind.SLEEP -> "Giờ đi ngủ (HH:mm)"
        HealthEntryKind.ACTIVITY -> "Số bước"
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
                OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text(firstLabel) }, placeholder = { Text(if (kind == HealthEntryKind.SLEEP) "22:30" else "Nhập giá trị") }, keyboardOptions = KeyboardOptions(keyboardType = if (kind == HealthEntryKind.SLEEP) KeyboardType.Text else KeyboardType.Decimal), singleLine = true, shape = MaterialTheme.shapes.medium)
                if (kind == HealthEntryKind.BLOOD_PRESSURE || kind == HealthEntryKind.ACTIVITY || kind == HealthEntryKind.SLEEP) {
                    val secondLabel = when (kind) {
                        HealthEntryKind.BLOOD_PRESSURE -> "Tâm trương (mmHg)"
                        HealthEntryKind.ACTIVITY -> "Thời lượng đi bộ (phút)"
                        else -> "Giờ thức dậy (HH:mm)"
                    }
                    OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text(secondLabel) }, placeholder = { Text(if (kind == HealthEntryKind.SLEEP) "06:30" else "Nhập giá trị") }, keyboardOptions = KeyboardOptions(keyboardType = if (kind == HealthEntryKind.SLEEP) KeyboardType.Text else KeyboardType.Number), singleLine = true, shape = MaterialTheme.shapes.medium)
                }
                if (kind == HealthEntryKind.GLUCOSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = unit == "mmol/L", onClick = { unit = "mmol/L" }, label = { Text("mmol/L") }, shape = CircleShape)
                        FilterChip(selected = unit == "mg/dL", onClick = { unit = "mg/dL" }, label = { Text("mg/dL") }, shape = CircleShape)
                    }
                }
                if (kind == HealthEntryKind.SLEEP) OutlinedTextField(value = quality, onValueChange = { value -> if (value.length <= 2 && value.all(Char::isDigit)) quality = value }, label = { Text("Chất lượng ngủ (1–10, tùy chọn)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = MaterialTheme.shapes.medium)
                val helpText = when (kind) {
                    HealthEntryKind.HEART_RATE -> "Nhập nhịp tim hiển thị trên đồng hồ hoặc máy đo. Ví dụ: 72 bpm."
                    HealthEntryKind.BLOOD_PRESSURE -> "Nhập hai số SYS/DIA từ máy đo huyết áp. Ví dụ: 120 và 80 mmHg."
                    HealthEntryKind.GLUCOSE -> "Nhập kết quả từ máy đo đường huyết; chọn đúng đơn vị trên thiết bị."
                    HealthEntryKind.WEIGHT -> "Nhập số cân nặng hiển thị trên cân."
                    HealthEntryKind.SLEEP -> "Nhập giờ đi ngủ và giờ thức dậy đêm vừa qua. Ví dụ: 22:30 và 06:30."
                    HealthEntryKind.ACTIVITY -> "Nhập số bước và thời lượng đi bộ từ thiết bị hoặc tự ghi."
                }
                Text(helpText, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(first, second, if (kind == HealthEntryKind.GLUCOSE) unit else quality) }, enabled = valid) { Text("Lưu số đo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
