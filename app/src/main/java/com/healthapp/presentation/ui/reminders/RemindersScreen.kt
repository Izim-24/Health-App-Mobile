package com.healthapp.presentation.ui.reminders

import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.DayOfWeek
import com.healthapp.domain.model.Reminder
import com.healthapp.domain.model.ReminderType
import com.healthapp.domain.usecase.ReminderSchedule
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.RemindersViewModel

private val typeLabels = mapOf(
    ReminderType.WATER to R.string.rtype_water, ReminderType.EXERCISE to R.string.rtype_exercise, ReminderType.SLEEP to R.string.rtype_sleep,
    ReminderType.MEAL to R.string.rtype_meal, ReminderType.HABIT to R.string.rtype_habit
)

/** Title saved with the reminder (and shown in its notification) when the user leaves it blank. */
private val defaultTitles = mapOf(
    ReminderType.WATER to R.string.rtitle_water, ReminderType.EXERCISE to R.string.rtitle_exercise, ReminderType.SLEEP to R.string.rtitle_sleep,
    ReminderType.MEAL to R.string.rtitle_meal, ReminderType.HABIT to R.string.rtitle_habit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(navController: NavController, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var permissionGranted by remember { mutableStateOf(hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(stringResource(R.string.link_reminders)) { navController.popBackStack() }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null; showEditor = true
                    if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                containerColor = HealthPalette.Amber.strong,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_reminder)) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Text(stringResource(R.string.reminders_intro), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (!permissionGranted) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Orange.soft)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.notifications_off), fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
                            Text(stringResource(R.string.notifications_off_sub), color = HealthPalette.Ink, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow_notifications)) }
                        }
                    }
                }
            }
            state.error?.let { err ->
                item {
                    Text(err.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    LaunchedEffect(err) { kotlinx.coroutines.delay(4000); viewModel.clearError() }
                }
            }
            if (state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.reminders.isEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconBadge(Icons.Default.NotificationsActive, HealthPalette.Amber, size = 64.dp)
                            Text(stringResource(R.string.no_reminders), fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
                            Text(stringResource(R.string.no_reminders_sub), color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(state.reminders, key = { it.id }) { reminder ->
                    ReminderRow(reminder,
                        onToggle = { viewModel.toggle(reminder, it) },
                        onEdit = { editing = reminder; showEditor = true },
                        onDelete = { viewModel.delete(reminder.id) })
                }
            }
        }
    }

    if (showEditor) {
        ReminderEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { title, type, time, days ->
                viewModel.save(editing?.id, title, type, time, days)
                showEditor = false
            }
        )
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = when (reminder.reminderType) {
        ReminderType.WATER -> HealthPalette.Sky
        ReminderType.EXERCISE -> HealthPalette.Orange
        ReminderType.SLEEP -> HealthPalette.Violet
        ReminderType.MEAL -> HealthPalette.Amber
        ReminderType.HABIT -> HealthPalette.Emerald
    }
    AppCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(reminder.reminderTime, style = MaterialTheme.typography.displaySmall, color = if (reminder.isEnabled) accent.strong else HealthPalette.Subtle)
                Text(reminder.title, color = HealthPalette.Ink, fontWeight = FontWeight.SemiBold)
                val names = dayNames()
                val days = reminder.daysOfWeek?.joinToString(" ") { names[it].orEmpty() } ?: stringResource(R.string.every_day)
                Text("${typeLabels[reminder.reminderType]?.let { stringResource(it) }.orEmpty()} · $days", color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete_item, reminder.title), tint = HealthPalette.Muted) }
            Switch(checked = reminder.isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = accent.strong))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditorDialog(
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (String, ReminderType, String, List<DayOfWeek>) -> Unit
) {
    val initialTime = initial?.let { ReminderSchedule.parseTime(it.reminderTime) }
    val timeState = rememberTimePickerState(initialHour = initialTime?.hour ?: 9, initialMinute = initialTime?.minute ?: 0, is24Hour = true)
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var type by remember { mutableStateOf(initial?.reminderType ?: ReminderType.WATER) }
    var days by remember { mutableStateOf(initial?.daysOfWeek?.toSet() ?: DayOfWeek.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_reminder else R.string.edit_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                TimeInput(state = timeState)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeLabels[t]?.let { stringResource(it) }.orEmpty()) })
                    }
                }
                OutlinedTextField(title, { title = it.take(60) }, label = { Text(stringResource(R.string.title_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.repeat), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(dayNames()[d].orEmpty()) })
                    }
                }
            }
        },
        confirmButton = {
            val defaultTitle = stringResource(defaultTitles.getValue(type))
            TextButton(enabled = days.isNotEmpty(), onClick = {
                onSave(title.ifBlank { defaultTitle }, type, "%02d:%02d".format(java.util.Locale.ROOT, timeState.hour, timeState.minute), DayOfWeek.entries.filter { it in days })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
