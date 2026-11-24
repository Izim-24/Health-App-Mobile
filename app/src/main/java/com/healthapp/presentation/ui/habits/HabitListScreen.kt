package com.healthapp.presentation.ui.habits

import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.healthapp.R
import androidx.compose.ui.res.stringResource
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

    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { !listState.canScrollBackward } }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }
    habitToDelete?.let { h ->
        ConfirmDeleteDialog(
            stringResource(R.string.delete_named_q, h.name), stringResource(R.string.confirm_delete_habit_body),
            onConfirm = { viewModel.deleteHabit(h.id) }, onDismiss = { habitToDelete = null }
        )
    }
    Box(Modifier.fillMaxSize().background(HealthPalette.Background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                ScreenHeader(stringResource(R.string.section_habits), stringResource(R.string.habits_header), subtitle = stringResource(R.string.habits_header_sub), accent = HealthPalette.Violet)
                if (uiState.habits.isNotEmpty()) {
                    val done = uiState.habits.count { it.isCompletedToday }
                    val total = uiState.habits.count { it.isEnabled }.coerceAtLeast(1)
                    val progress by animateFloatAsState(done.toFloat() / total, tween(600, easing = FastOutSlowInEasing), label = "habits")
                    val allDone = done == total
                    // A stronger buzz once when the last habit of the day is checked off.
                    var wasAllDone by remember { mutableStateOf(allDone) }
                    LaunchedEffect(allDone) {
                        if (allDone && !wasAllDone) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        wasAllDone = allDone
                    }
                    GradientCard(HealthPalette.Violet.gradient, Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.today), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                                Text(stringResource(R.string.habits_of_total, done, total), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(if (done == total) stringResource(R.string.habits_all_done) else stringResource(R.string.habits_left, total - done), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                            }
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = Color.White, trackColor = Color.White.copy(alpha = .25f), strokeWidth = 8.dp, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                // Percentage, replaced by a bouncing trophy when everything is done.
                                AnimatedContent(allDone, transitionSpec = { (scaleIn(spring(dampingRatio = .45f)) + fadeIn()) togetherWith fadeOut() }, label = "done") { finished ->
                                    if (finished) Text("🏆", fontSize = 26.sp)
                                    else Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading && uiState.habits.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HealthPalette.Violet.strong) }
                uiState.error != null && uiState.habits.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(uiState.error ?: stringResource(R.string.err_load_habits), color = HealthPalette.Muted) }
                uiState.habits.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    IllustrationImage(Illustration.HABITS, Modifier.height(170.dp).aspectRatio(1.25f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.habits_empty_title), style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.habits_empty_sub), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(stringResource(R.string.create_habit), { editingHabit = null; showHabitDialog = true }, Modifier.widthIn(max = 260.dp), icon = Icons.Rounded.Add, color = HealthPalette.Violet.strong)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.habits.sortedBy { it.isCompletedToday }, key = { it.id }) { habit ->
                        HabitCard(
                            habit = habit,
                            date = uiState.date,
                            onToggle = { checked ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setHabitCompleted(habit.id, uiState.date, checked)
                            },
                            onEdit = { editingHabit = habit; showHabitDialog = true },
                            onDelete = { habitToDelete = habit }
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
            icon = { Icon(Icons.Rounded.Add, contentDescription = if (fabExpanded) null else stringResource(R.string.new_habit)) },
            text = { Text(stringResource(R.string.new_habit)) },
            expanded = fabExpanded
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
            // Large round check target; TalkBack reads "Done: <habit>, checked/not checked".
            val doneLabel = stringResource(R.string.habit_done_cd, habit.name)
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (done) HealthPalette.Violet.strong else HealthPalette.SurfaceAlt)
                    .toggleable(value = done, role = Role.Checkbox, onValueChange = onToggle)
                    .semantics { contentDescription = doneLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = if (done) Color.White else HealthPalette.Subtle)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    habit.name, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                val goal = if (habit.goalValue != null) stringResource(R.string.habit_goal_daily, formatAmount(habit.goalValue), habit.goalUnit.orEmpty()) else stringResource(R.string.every_day)
                Text(if (done) stringResource(R.string.habit_done_today, goal) else goal, style = MaterialTheme.typography.bodySmall, color = if (done) HealthPalette.Violet.strong else HealthPalette.Muted)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_named, habit.name), tint = HealthPalette.Muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete_item, habit.name), tint = HealthPalette.Subtle) }
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
        title = { Text(stringResource(if (habit == null) R.string.create_habit else R.string.edit_habit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.habit_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> if (value.all { it.isDigit() || it == '.' || it == ',' }) amount = value },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.goal_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.unit)) }, singleLine = true)
                }
                Text(stringResource(R.string.habit_example), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validAmount = parsedAmount?.takeIf { it > 0 }
                val validUnit = unit.trim().takeIf { it.isNotEmpty() }
                onSave(name.trim(), validAmount, validUnit)
            }, enabled = name.isNotBlank() && (amount.isBlank() || (parsedAmount != null && parsedAmount > 0 && unit.isNotBlank()))) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
