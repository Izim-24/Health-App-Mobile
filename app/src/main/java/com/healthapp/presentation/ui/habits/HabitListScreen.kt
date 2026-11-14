package com.healthapp.presentation.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.healthapp.presentation.ui.components.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.healthapp.domain.model.Habit
import com.healthapp.domain.model.HabitFrequency
import com.healthapp.presentation.viewmodel.HabitListViewModel
import com.healthapp.presentation.ui.theme.HealthPalette
import java.time.LocalDate

@Composable
fun HabitListScreen(navController: NavController, viewModel: HabitListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var showHabitDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadHabits()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(HealthPalette.Background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                ScreenHeader("Thói quen", "Tiến bộ mỗi ngày", "Từng bước nhỏ, đều đặn mỗi ngày.", HealthPalette.Violet)
                if (uiState.habits.isNotEmpty()) {
                    val done = uiState.habits.count { it.isCompletedToday }
                    val total = uiState.habits.count { it.isEnabled }.coerceAtLeast(1)
                    val progress = done.toFloat() / total
                    GradientCard(HealthPalette.Violet.gradient, Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Hôm nay", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                                Text("$done / $total thói quen", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(if (done == total) "Hoàn thành tất cả — tuyệt vời! 🎉" else "Còn ${total - done} việc nữa", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                            }
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = Color.White, trackColor = Color.White.copy(alpha = .25f), strokeWidth = 8.dp, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading && uiState.habits.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HealthPalette.Violet.strong) }
                uiState.error != null && uiState.habits.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(uiState.error ?: "Không tải được thói quen", color = HealthPalette.Muted) }
                uiState.habits.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IconBadge(Icons.Rounded.EmojiEvents, HealthPalette.Violet, size = 72.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Bắt đầu từ một mục tiêu nhỏ", style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("Tạo thói quen của riêng bạn và đánh dấu khi hoàn thành.", color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("Tạo thói quen", { editingHabit = null; showHabitDialog = true }, Modifier.widthIn(max = 260.dp), icon = Icons.Rounded.Add, color = HealthPalette.Violet.strong)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.habits.sortedBy { it.isCompletedToday }, key = { it.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            date = uiState.date,
                            onToggle = { checked -> viewModel.setHabitCompleted(habit.id, uiState.date, checked) },
                            onEdit = { editingHabit = habit; showHabitDialog = true },
                            onDelete = { viewModel.deleteHabit(habit.id) }
                        )
                    }
                }
            }
        }

        if (uiState.habits.isNotEmpty()) ExtendedFloatingActionButton(
            onClick = { editingHabit = null; showHabitDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = HealthPalette.Violet.strong,
            contentColor = Color.White,
            shape = CircleShape,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Thói quen mới") }
        )
    }

    if (showHabitDialog) {
        HabitEditorDialog(
            habit = editingHabit,
            onDismiss = { showHabitDialog = false; editingHabit = null },
            onSave = { name, amount, unit ->
                val existing = editingHabit
                if (existing == null) viewModel.createHabit(name, HabitFrequency.DAILY, goalValue = amount, goalUnit = unit)
                else viewModel.updateHabit(existing, name, amount, unit)
                showHabitDialog = false
                editingHabit = null
            }
        )
    }
}

@Composable
private fun HabitCard(habit: Habit, date: String, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val done = habit.isCompletedToday
    AppCard(Modifier.fillMaxWidth(), color = if (done) HealthPalette.Violet.soft else HealthPalette.Surface, border = !done) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Large round check target
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (done) HealthPalette.Violet.strong else HealthPalette.SurfaceAlt)
                    .clickable { onToggle(!done) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = if (done) "Bỏ đánh dấu" else "Đánh dấu hoàn thành", tint = if (done) Color.White else HealthPalette.Subtle)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    habit.name, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                val goal = if (habit.goalValue != null) "${formatAmount(habit.goalValue)} ${habit.goalUnit.orEmpty()} mỗi ngày" else "Hằng ngày"
                Text(if (done) "Đã xong hôm nay · $goal" else goal, style = MaterialTheme.typography.bodySmall, color = if (done) HealthPalette.Violet.strong else HealthPalette.Muted)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Chỉnh sửa", tint = HealthPalette.Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Xóa", tint = HealthPalette.Subtle) }
        }
    }
}

@Composable
private fun HabitEditorDialog(habit: Habit?, onDismiss: () -> Unit, onSave: (String, Double?, String?) -> Unit) {
    var name by remember(habit?.id) { mutableStateOf(habit?.name.orEmpty()) }
    var amount by remember(habit?.id) { mutableStateOf(habit?.goalValue?.let(::formatAmount).orEmpty()) }
    var unit by remember(habit?.id) { mutableStateOf(habit?.goalUnit.orEmpty()) }
    val parsedAmount = amount.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "Tạo thói quen" else "Chỉnh sửa thói quen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên thói quen") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> if (value.all { it.isDigit() || it == '.' || it == ',' }) amount = value },
                        modifier = Modifier.weight(1f),
                        label = { Text("Mục tiêu (tùy chọn)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, modifier = Modifier.weight(1f), label = { Text("Đơn vị") }, singleLine = true)
                }
                Text("Ví dụ: Uống nước · 2 · lít", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validAmount = parsedAmount?.takeIf { it > 0 }
                val validUnit = unit.trim().takeIf { it.isNotEmpty() }
                onSave(name.trim(), validAmount, validUnit)
            }, enabled = name.isNotBlank() && (amount.isBlank() || (parsedAmount != null && parsedAmount > 0 && unit.isNotBlank()))) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
