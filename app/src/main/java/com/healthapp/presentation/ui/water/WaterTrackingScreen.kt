package com.healthapp.presentation.ui.water

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.WaterIntake
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.WaterTrackingViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackingScreen(
    navController: NavController,
    viewModel: WaterTrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGoalDialog by remember { mutableStateOf(false) }
    var waterToDelete by remember { mutableStateOf<WaterIntake?>(null) }
    var goalInput by remember { mutableStateOf("") }
    val sky = HealthPalette.Sky

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.link_water)) { navController.popBackStack() } }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = sky.strong) }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            GradientCard(sky.gradient, Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.today), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
                        Text("${uiState.totalWaterToday} ml", style = MaterialTheme.typography.displaySmall, color = Color.White)
                        Text(
                            if (uiState.waterGoal > 0) stringResource(R.string.water_goal_left, uiState.waterGoal, (uiState.waterGoal - uiState.totalWaterToday).coerceAtLeast(0)) else stringResource(R.string.no_goal),
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f)
                        )
                    }
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { uiState.progressPercentage }, modifier = Modifier.fillMaxSize(),
                            color = Color.White, trackColor = Color.White.copy(alpha = .25f), strokeWidth = 10.dp, strokeCap = StrokeCap.Round
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.WaterDrop, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text("${(uiState.progressPercentage * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .2f))
                        .clickable { goalInput = uiState.waterGoal.takeIf { it > 0 }?.toString().orEmpty(); showGoalDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (uiState.waterGoal > 0) R.string.edit_goal else R.string.set_goal), style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            uiState.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) }

            SectionHeader(stringResource(R.string.quick_add), modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple(1, "100 ml", R.string.water_sip), Triple(2, "250 ml", R.string.water_glass), Triple(3, "500 ml", R.string.water_bottle)).forEach { (option, amount, labelRes) ->
                    val label = stringResource(labelRes)
                    val addLabel = stringResource(R.string.add_water_cd, label, amount)
                    AppCard(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = addLabel }, onClick = { viewModel.addQuickWater(option) }, color = sky.soft) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.LocalDrink, null, tint = sky.strong, modifier = Modifier.size((18 + option * 5).dp))
                            Text(amount, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = sky.strong)
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.today_log), modifier = Modifier.padding(top = 18.dp))
            if (uiState.waterLogs.isEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.no_water_logs), Modifier.padding(18.dp), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.waterLogs.forEach { log -> WaterLogItem(log, onDelete = { waterToDelete = log }) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    waterToDelete?.let { log ->
        ConfirmDeleteDialog(
            stringResource(R.string.confirm_delete_water, log.amountMl), stringResource(R.string.cannot_undo),
            onConfirm = { viewModel.deleteWaterLog(log.id) }, onDismiss = { waterToDelete = null }
        )
    }
    if (showGoalDialog) AlertDialog(
        onDismissRequest = { showGoalDialog = false },
        title = { Text(stringResource(R.string.water_goal_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                goalInput, { goalInput = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ml_per_day)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium
            )
        },
        confirmButton = { Button(onClick = { viewModel.setGoal(goalInput.toIntOrNull() ?: 0); showGoalDialog = false }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun WaterLogItem(log: WaterIntake, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.WaterDrop, HealthPalette.Sky, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Text("${log.amountMl} ml", style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
            Text(log.recordedAt.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete_water_cd, log.amountMl, log.recordedAt.format(DateTimeFormatter.ofPattern("HH:mm"))), tint = HealthPalette.Subtle) }
        }
    }
}
