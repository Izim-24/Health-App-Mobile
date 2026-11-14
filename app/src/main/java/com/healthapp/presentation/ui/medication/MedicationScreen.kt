package com.healthapp.presentation.ui.medication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.DoseStatus
import com.healthapp.domain.model.Medication
import com.healthapp.domain.model.ScheduledDose
import com.healthapp.domain.usecase.Adherence
import com.healthapp.domain.usecase.MedicationSchedule
import com.healthapp.presentation.ui.reminders.dayLabels
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.MedicationDraft
import com.healthapp.presentation.viewmodel.MedicationViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(navController: NavController, viewModel: MedicationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf<MedicationDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<Medication?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar("Lịch uống thuốc") { navController.popBackStack() }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = MedicationDraft(); requestNotificationsIfNeeded() },
                containerColor = HealthPalette.Indigo.strong,
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Thêm thuốc") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    LaunchedEffect(err) { kotlinx.coroutines.delay(4000); viewModel.clearError() }
                }
            }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }
            if (state.medications.isEmpty()) {
                item { EmptyState() }
                return@LazyColumn
            }

            item { AdherenceCard(state.todayDoses, state.todayAdherence, state.weekAdherence) }

            item { SectionTitle("Hôm nay") }
            if (state.todayDoses.isEmpty()) {
                item { Text("Không có liều nào theo lịch hôm nay.", color = HealthPalette.Muted) }
            } else {
                items(state.todayDoses, key = { "${it.medication.id}|${it.time}" }) { dose ->
                    DoseRow(dose, onTaken = { viewModel.mark(dose, DoseStatus.TAKEN) }, onSkipped = { viewModel.mark(dose, DoseStatus.SKIPPED) })
                }
            }

            item { SectionTitle("Thuốc của bạn") }
            items(state.medications, key = { it.id }) { med ->
                MedicationRow(
                    med,
                    onEdit = { draft = viewModel.draftFor(med) },
                    onToggle = { viewModel.setActive(med, it) },
                    onDelete = { pendingDelete = med }
                )
            }
            item {
                Text(
                    "Ứng dụng chỉ nhắc theo lịch bạn nhập, không thay thế chỉ định của bác sĩ hoặc dược sĩ. Không tự ý đổi liều.",
                    style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                )
            }
        }
    }

    draft?.let { current ->
        MedicationEditorDialog(
            initial = current,
            onDismiss = { draft = null },
            onSave = { d -> viewModel.save(d).also { if (it == null) draft = null } }
        )
    }

    pendingDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xóa ${med.name}?") },
            text = { Text("Lịch nhắc và lịch sử uống thuốc này sẽ bị xóa. Nếu chỉ tạm ngừng, hãy tắt công tắc thay vì xóa.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(med); pendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HealthPalette.Ink, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun EmptyState() {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(Icons.Default.Medication, HealthPalette.Indigo, size = 64.dp)
            Text("Chưa có thuốc nào", fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
            Text("Thêm thuốc với giờ uống để nhận thông báo và đánh dấu từng liều đã uống.", color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdherenceCard(doses: List<ScheduledDose>, today: Adherence, week: Adherence) {
    val takenToday = doses.count { it.status == DoseStatus.TAKEN }
    GradientCard(HealthPalette.Indigo.gradient, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TIẾN ĐỘ HÔM NAY", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("$takenToday / ${doses.size} liều", color = HealthPalette.Surface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { if (doses.isEmpty()) 0f else takenToday.toFloat() / doses.size },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            val now = LocalDateTime.now()
            val missed = doses.count { it.status == null && !it.date.atTime(it.time).isAfter(now) && !it.date.atTime(it.time).isBefore(it.medication.createdAt) }
            Text(
                buildString {
                    append("7 ngày qua: ")
                    append(week.rate?.let { "${(it * 100).toInt()}% liều đã uống (${week.taken}/${week.scheduled})" } ?: "chưa có liều đến hạn")
                    if (missed > 0) append(" · $missed liều hôm nay chưa đánh dấu")
                },
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DoseRow(dose: ScheduledDose, onTaken: () -> Unit, onSkipped: () -> Unit) {
    val due = !dose.date.atTime(dose.time).isAfter(LocalDateTime.now())
    val overdue = due && dose.status == null
    AppCard(Modifier.fillMaxWidth(), color = if (overdue) HealthPalette.Orange.soft else HealthPalette.Surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(MedicationSchedule.formatTime(dose.time), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = HealthPalette.Indigo.strong, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dose.medication.name, fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink,
                    textDecoration = if (dose.status == DoseStatus.SKIPPED) TextDecoration.LineThrough else null
                )
                val sub = listOfNotNull(dose.medication.dosage.ifBlank { null }, dose.medication.instructions,
                    when (dose.status) { DoseStatus.TAKEN -> "đã uống"; DoseStatus.SKIPPED -> "đã bỏ qua"; null -> if (overdue) "chưa đánh dấu" else null })
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            StatusButton(Icons.Default.Close, "Bỏ qua", selected = dose.status == DoseStatus.SKIPPED, selectedColor = HealthPalette.Muted, onClick = onSkipped)
            Spacer(Modifier.width(6.dp))
            StatusButton(Icons.Default.Check, "Đã uống", selected = dose.status == DoseStatus.TAKEN, selectedColor = HealthPalette.Primary, onClick = onTaken)
        }
    }
}

@Composable
private fun StatusButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = HealthPalette.Line, contentColor = HealthPalette.Ink,
            checkedContainerColor = selectedColor, checkedContentColor = HealthPalette.Surface
        )
    ) { Icon(icon, label) }
}

@Composable
private fun MedicationRow(med: Medication, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM")
    AppCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOf(med.name, med.dosage).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.SemiBold, color = if (med.isActive) HealthPalette.Ink else HealthPalette.Muted)
                Text(med.times.joinToString("  ") { MedicationSchedule.formatTime(it) }, color = HealthPalette.Indigo.strong, style = MaterialTheme.typography.bodyMedium)
                val days = med.daysOfWeek?.joinToString(" ") { dayLabels[it].orEmpty() } ?: "Hằng ngày"
                val course = med.endDate?.let { "đến ${it.format(fmt)}" } ?: "dài hạn"
                Text("$days · $course${if (!med.isActive) " · tạm ngừng" else ""}", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Xóa ${med.name}", tint = HealthPalette.Muted) }
            Switch(checked = med.isActive, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = HealthPalette.Indigo.strong))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationEditorDialog(initial: MedicationDraft, onDismiss: () -> Unit, onSave: (MedicationDraft) -> String?) {
    var d by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "Thêm thuốc" else "Sửa thuốc") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text("Tên thuốc") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.dosage, { d = d.copy(dosage = it) }, label = { Text("Liều (vd: 500 mg, 1 viên)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.times, { d = d.copy(times = it) }, label = { Text("Giờ uống (vd: 08:00, 20:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1 lần" to "08:00", "2 lần" to "08:00, 20:00", "3 lần" to "07:00, 12:00, 19:00").forEach { (label, preset) ->
                        AssistChip(onClick = { d = d.copy(times = preset) }, label = { Text(label) })
                    }
                }
                OutlinedTextField(d.instructions, { d = d.copy(instructions = it) }, label = { Text("Ghi chú (vd: sau ăn)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Ngày dùng", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in d.days, onClick = { d = d.copy(days = if (day in d.days) d.days - day else d.days + day) }, label = { Text(dayLabels[day].orEmpty()) })
                    }
                }
                OutlinedTextField(
                    d.durationDays, { d = d.copy(durationDays = it.filter(Char::isDigit).take(4)) },
                    label = { Text("Số ngày dùng (trống = dài hạn)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(d) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
