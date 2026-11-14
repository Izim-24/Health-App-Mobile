package com.healthapp.presentation.ui.reminders

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

internal val dayLabels = mapOf(
    DayOfWeek.MONDAY to "T2", DayOfWeek.TUESDAY to "T3", DayOfWeek.WEDNESDAY to "T4",
    DayOfWeek.THURSDAY to "T5", DayOfWeek.FRIDAY to "T6", DayOfWeek.SATURDAY to "T7", DayOfWeek.SUNDAY to "CN"
)

private val typeLabels = mapOf(
    ReminderType.WATER to "Uống nước", ReminderType.EXERCISE to "Vận động", ReminderType.SLEEP to "Giấc ngủ",
    ReminderType.MEAL to "Bữa ăn", ReminderType.HABIT to "Thói quen"
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
            BackTopBar("Nhắc nhở") { navController.popBackStack() }
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
                text = { Text("Thêm nhắc nhở") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Text("Nhắc bạn uống nước, vận động, ăn uống và đi ngủ đúng giờ. Thông báo được lên lịch trên thiết bị, không cần mạng.", color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (!permissionGranted) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HealthPalette.Orange.soft)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Thông báo đang tắt", fontWeight = FontWeight.Bold, color = HealthPalette.Ink)
                            Text("Cho phép thông báo để nhận nhắc nhở đúng giờ.", color = HealthPalette.Ink, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Cho phép thông báo") }
                        }
                    }
                }
            }
            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                            Text("Chưa có nhắc nhở nào", fontWeight = FontWeight.SemiBold, color = HealthPalette.Ink)
                            Text("Thử thêm nhắc uống nước lúc 09:00 hoặc nhắc đi ngủ lúc 22:30.", color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
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
                val days = reminder.daysOfWeek?.joinToString(" ") { dayLabels[it].orEmpty() } ?: "Hằng ngày"
                Text("${typeLabels[reminder.reminderType]} · $days", color = HealthPalette.Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Xóa nhắc nhở", tint = HealthPalette.Muted) }
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
        title = { Text(if (initial == null) "Nhắc nhở mới" else "Sửa nhắc nhở") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                TimeInput(state = timeState)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeLabels[t].orEmpty()) })
                    }
                }
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Tiêu đề (tùy chọn)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Lặp lại", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(dayLabels[d].orEmpty()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = {
                onSave(title, type, "%02d:%02d".format(timeState.hour, timeState.minute), DayOfWeek.entries.filter { it in days })
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
