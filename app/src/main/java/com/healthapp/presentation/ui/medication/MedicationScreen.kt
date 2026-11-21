package com.healthapp.presentation.ui.medication

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    // Android 12+: exact alarms need the user's OK ("Alarms & reminders"). Re-check when returning from Settings.
    var exactAllowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) exactAllowed = canScheduleExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(stringResource(R.string.link_meds)) { navController.popBackStack() }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = MedicationDraft(); requestNotificationsIfNeeded() },
                containerColor = HealthPalette.Indigo.strong,
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_med)) }
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
                    Text(err.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LaunchedEffect(err) { kotlinx.coroutines.delay(4000); viewModel.clearError() }
                }
            }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }
            if (!exactAllowed && state.medications.any { it.isActive }) {
                item { ExactAlarmBanner(onAllow = { openExactAlarmSettings(context) }) }
            }
            if (state.medications.isEmpty()) {
                item { EmptyState() }
                return@LazyColumn
            }

            item { AdherenceCard(state.todayDoses, state.todayAdherence, state.weekAdherence) }

            item { SectionTitle(stringResource(R.string.today)) }
            if (state.todayDoses.isEmpty()) {
                item { Text(stringResource(R.string.no_doses_today), color = HealthPalette.Muted) }
            } else {
                items(state.todayDoses, key = { "${it.medication.id}|${it.time}" }) { dose ->
                    DoseRow(dose, onTaken = { viewModel.mark(dose, DoseStatus.TAKEN) }, onSkipped = { viewModel.mark(dose, DoseStatus.SKIPPED) })
                }
            }

            item { SectionTitle(stringResource(R.string.your_meds)) }
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
                    stringResource(R.string.med_disclaimer),
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
            title = { Text(stringResource(R.string.delete_named_q, med.name)) },
            text = { Text(stringResource(R.string.med_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(med); pendingDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
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
            Text(stringResource(R.string.no_meds), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
            Text(stringResource(R.string.no_meds_sub), color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdherenceCard(doses: List<ScheduledDose>, today: Adherence, week: Adherence) {
    val takenToday = doses.count { it.status == DoseStatus.TAKEN }
    GradientCard(HealthPalette.Indigo.gradient, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.today_progress_caps), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(stringResource(R.string.doses_of_total, takenToday, doses.size), color = HealthPalette.Surface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(
                progress = { if (doses.isEmpty()) 0f else takenToday.toFloat() / doses.size },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            val now = LocalDateTime.now()
            val missed = doses.count { it.status == null && !it.date.atTime(it.time).isAfter(now) && !it.date.atTime(it.time).isBefore(it.medication.createdAt) }
            val last7 = stringResource(R.string.last_7_days)
            val noneDue = stringResource(R.string.no_doses_due)
            val takenRateFmt = stringResource(R.string.doses_taken_rate)
            val unmarkedFmt = stringResource(R.string.doses_unmarked_today)
            Text(
                buildString {
                    append(last7)
                    append(week.rate?.let { String.format(takenRateFmt, (it * 100).toInt(), week.taken, week.scheduled) } ?: noneDue)
                    if (missed > 0) append(String.format(unmarkedFmt, missed))
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
                    when (dose.status) {
                        DoseStatus.TAKEN -> stringResource(R.string.dose_taken)
                        DoseStatus.SKIPPED -> stringResource(R.string.dose_skipped)
                        null -> if (overdue) stringResource(R.string.dose_unmarked) else null
                    })
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            val timeText = MedicationSchedule.formatTime(dose.time)
            StatusButton(Icons.Default.Close, stringResource(R.string.dose_action_cd, stringResource(R.string.action_skip_dose), dose.medication.name, timeText), selected = dose.status == DoseStatus.SKIPPED, selectedColor = HealthPalette.Muted, onClick = onSkipped)
            Spacer(Modifier.width(6.dp))
            StatusButton(Icons.Default.Check, stringResource(R.string.dose_action_cd, stringResource(R.string.action_taken), dose.medication.name, timeText), selected = dose.status == DoseStatus.TAKEN, selectedColor = HealthPalette.Primary, onClick = onTaken)
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
                val names = dayNames()
                val days = med.daysOfWeek?.joinToString(" ") { names[it].orEmpty() } ?: stringResource(R.string.every_day)
                val course = med.endDate?.let { stringResource(R.string.until_date, it.format(fmt)) } ?: stringResource(R.string.long_term)
                Text("$days · $course${if (!med.isActive) stringResource(R.string.paused_suffix) else ""}", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete_item, med.name), tint = HealthPalette.Muted) }
            Switch(checked = med.isActive, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = HealthPalette.Indigo.strong))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationEditorDialog(initial: MedicationDraft, onDismiss: () -> Unit, onSave: (MedicationDraft) -> UiText?) {
    var d by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<UiText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == null) R.string.add_med else R.string.edit_med)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(d.name, { d = d.copy(name = it) }, label = { Text(stringResource(R.string.med_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.dosage, { d = d.copy(dosage = it) }, label = { Text(stringResource(R.string.med_dosage)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.times, { d = d.copy(times = it) }, label = { Text(stringResource(R.string.med_times)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.times_1 to "08:00", R.string.times_2 to "08:00, 20:00", R.string.times_3 to "07:00, 12:00, 19:00").forEach { (label, preset) ->
                        AssistChip(onClick = { d = d.copy(times = preset) }, label = { Text(stringResource(label)) })
                    }
                }
                OutlinedTextField(d.instructions, { d = d.copy(instructions = it) }, label = { Text(stringResource(R.string.med_notes)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.days_of_use), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val names = dayNames()
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(selected = day in d.days, onClick = { d = d.copy(days = if (day in d.days) d.days - day else d.days + day) }, label = { Text(names[day].orEmpty()) })
                    }
                }
                OutlinedTextField(
                    d.durationDays, { d = d.copy(durationDays = it.filter(Char::isDigit).take(4)) },
                    label = { Text(stringResource(R.string.duration_days)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(d) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ExactAlarmBanner(onAllow: () -> Unit) {
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.exact_title), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
                Text(
                    stringResource(R.string.exact_body),
                    style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                )
            }
            SoftButton(stringResource(R.string.action_allow), onClick = onAllow, accent = HealthPalette.Amber)
        }
    }
}
