package com.healthapp.presentation.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import com.healthapp.presentation.viewmodel.ProfileViewModel

private val genderLabels = mapOf(Gender.MALE to "Nam", Gender.FEMALE to "Nữ", Gender.OTHER to "Khác")
private val activityLabels = mapOf(
    ActivityLevel.SEDENTARY to "Ít vận động",
    ActivityLevel.LIGHTLY_ACTIVE to "Nhẹ (1–3 buổi/tuần)",
    ActivityLevel.MODERATELY_ACTIVE to "Vừa (3–5 buổi/tuần)",
    ActivityLevel.VERY_ACTIVE to "Nhiều (6–7 buổi/tuần)",
    ActivityLevel.EXTRA_ACTIVE to "Rất nhiều / lao động nặng"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(navController: NavController, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var activityMenu by remember { mutableStateOf(false) }

    val fieldShape = MaterialTheme.shapes.medium
    val height = state.heightCm.toIntOrNull()
    val weight = state.weightKg.replace(',', '.').toDoubleOrNull()
    val bmi = if (height != null && weight != null) HealthCalculations.bmi(height, weight) else null

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Hồ sơ") { navController.popBackStack() } }
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        GradientCard(HealthPalette.HeroGradient, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                    Text(state.name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.name.ifBlank { "Hồ sơ của bạn" }, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    Text("Thông tin lưu trên thiết bị", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
                }
            }
            if (bmi != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStat("BMI", "%.1f".format(bmi), bmiLabel(bmi))
                    weight?.let { HealthCalculations.suggestedWaterMl(it) }?.let { HeroStat("Nước gợi ý", "${it} ml", "mỗi ngày") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.loading) CircularProgressIndicator(color = HealthPalette.Primary) else {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Thông tin cá nhân", style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                    OutlinedTextField(state.name, { viewModel.update(it, state.birthDate, state.heightCm, state.weightKg, state.calorieGoal) }, label = { Text("Họ tên") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape)
                    OutlinedTextField(state.birthDate, { viewModel.update(state.name, it, state.heightCm, state.weightKg, state.calorieGoal) }, label = { Text("Ngày sinh (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape)
                    Text("Giới tính", style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Gender.entries.forEach { g ->
                            FilterChip(
                                selected = state.gender == g, onClick = { viewModel.setGender(g) }, label = { Text(genderLabels[g].orEmpty()) }, shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HealthPalette.Primary, selectedLabelColor = Color.White)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(state.heightCm, { viewModel.update(state.name, state.birthDate, it, state.weightKg, state.calorieGoal) }, label = { Text("Cao (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = fieldShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(state.weightKg, { viewModel.update(state.name, state.birthDate, state.heightCm, it, state.calorieGoal) }, label = { Text("Nặng (kg)") }, modifier = Modifier.weight(1f), singleLine = true, shape = fieldShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    ExposedDropdownMenuBox(expanded = activityMenu, onExpandedChange = { activityMenu = it }) {
                        OutlinedTextField(
                            value = state.activityLevel?.let { activityLabels[it] }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Mức vận động") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activityMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = fieldShape
                        )
                        ExposedDropdownMenu(expanded = activityMenu, onDismissRequest = { activityMenu = false }) {
                            ActivityLevel.entries.forEach { level ->
                                DropdownMenuItem(text = { Text(activityLabels[level].orEmpty()) }, onClick = { viewModel.setActivityLevel(level); activityMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(state.calorieGoal, { viewModel.update(state.name, state.birthDate, state.heightCm, state.weightKg, it) }, label = { Text("Mục tiêu năng lượng (kcal/ngày)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    SoftButton("Tính mục tiêu calo & macro", viewModel::calculateTargets, HealthPalette.Orange, Modifier.fillMaxWidth(), icon = Icons.Default.Calculate)
                    state.suggestion?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(HealthPalette.Orange.soft).padding(12.dp))
                    }
                    Text(
                        if (bmi != null) "BMI ${"%.1f".format(bmi)} · ${bmiLabel(bmi)} · chuẩn châu Á: bình thường ${HealthCalculations.BMI_NORMAL_RANGE}" else "BMI sẽ hiện khi có chiều cao và cân nặng",
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (state.saved) Pill("Đã lưu hồ sơ ✓", HealthPalette.Emerald)
                    PrimaryButton("Lưu hồ sơ", viewModel::save)
                }
            }
        }
        SectionHeader("Tiện ích", modifier = Modifier.padding(top = 18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkRow(Icons.Default.Assessment, HealthPalette.Indigo, "Báo cáo sức khỏe") { navController.navigate("reports") }
            LinkRow(Icons.Default.LocalDrink, HealthPalette.Sky, "Nước uống") { navController.navigate("water") }
            LinkRow(Icons.Default.Medication, HealthPalette.Indigo, "Lịch uống thuốc") { navController.navigate("medications") }
            LinkRow(Icons.Default.Notifications, HealthPalette.Amber, "Nhắc nhở") { navController.navigate("reminders") }
            LinkRow(Icons.Default.ChatBubbleOutline, HealthPalette.Violet, "Hỏi trợ lý AI") { navController.navigate("ai_chat") }
            LinkRow(Icons.Default.Settings, HealthPalette.Emerald, "Cài đặt & dữ liệu") { navController.navigate("settings") }
        }
        Spacer(Modifier.height(28.dp))
    }
    }
}

@Composable
private fun RowScope.HeroStat(label: String, value: String, detail: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
    }
}

private fun bmiLabel(bmi: Double) = HealthCalculations.bmiCategory(bmi).label

@Composable
private fun LinkRow(icon: ImageVector, accent: com.healthapp.presentation.ui.theme.Accent, label: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, accent, size = 40.dp)
            Text(label, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, color = HealthPalette.Subtle)
        }
    }
}
