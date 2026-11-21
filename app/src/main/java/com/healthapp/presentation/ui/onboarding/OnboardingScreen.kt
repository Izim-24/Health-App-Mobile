package com.healthapp.presentation.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthapp.domain.model.ActivityLevel
import com.healthapp.domain.model.Gender
import com.healthapp.domain.usecase.HealthCalculations
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.ArtworkKind
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.WellnessArtwork
import com.healthapp.presentation.viewmodel.OnboardingUiState
import com.healthapp.presentation.viewmodel.OnboardingViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class ActivityOption(val level: ActivityLevel, val title: String, val detail: String, val icon: ImageVector)

private val activityOptions = listOf(
    ActivityOption(ActivityLevel.SEDENTARY, "Ít vận động", "Làm việc văn phòng, ít tập", Icons.Rounded.Chair),
    ActivityOption(ActivityLevel.LIGHTLY_ACTIVE, "Nhẹ", "Tập 1–3 buổi/tuần", Icons.AutoMirrored.Rounded.DirectionsWalk),
    ActivityOption(ActivityLevel.MODERATELY_ACTIVE, "Vừa", "Tập 3–5 buổi/tuần", Icons.AutoMirrored.Rounded.DirectionsRun),
    ActivityOption(ActivityLevel.VERY_ACTIVE, "Nhiều", "Tập 6–7 buổi/tuần", Icons.Rounded.FitnessCenter),
    ActivityOption(ActivityLevel.EXTRA_ACTIVE, "Rất nhiều", "Lao động nặng / tập 2 lần/ngày", Icons.Rounded.LocalFireDepartment)
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(Modifier.fillMaxSize().background(HealthPalette.Background).padding(horizontal = 20.dp)) {
        // Top bar: back + progress + skip
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step > 0) IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại", tint = HealthPalette.Ink) }
            else Spacer(Modifier.size(48.dp))
            if (state.step > 0) {
                AccentProgress(state.step / OnboardingUiState.LAST_STEP.toFloat(), HealthPalette.Emerald, Modifier.weight(1f).padding(horizontal = 8.dp))
            } else Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::skip) { Text("Bỏ qua", color = HealthPalette.Muted) }
        }

        AnimatedContent(
            targetState = state.step, modifier = Modifier.weight(1f),
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "step"
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> AboutStep(state, viewModel)
                    2 -> BodyStep(state, viewModel)
                    else -> GoalsStep(state, viewModel)
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        PrimaryButton(
            text = when {
                state.saving -> "Đang lưu…"
                state.step == 0 -> "Bắt đầu"
                state.step == OnboardingUiState.LAST_STEP -> "Hoàn tất"
                else -> "Tiếp tục"
            },
            onClick = viewModel::next, enabled = !state.saving,
            icon = if (state.step == OnboardingUiState.LAST_STEP) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.padding(bottom = 20.dp, top = 8.dp)
        )
    }
}

@Composable
private fun WelcomeStep() {
    GradientCard(HealthPalette.HeroGradient, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        WellnessArtwork(ArtworkKind.GARDEN, Modifier.size(120.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text("Chào mừng bạn!", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Theo dõi sức khỏe, thói quen và bữa ăn — dữ liệu lưu trên máy của bạn.",
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(Modifier.height(18.dp))
    Feature(Icons.Rounded.Favorite, HealthPalette.Coral, "Điểm sức khỏe dễ hiểu", "Tổng hợp giấc ngủ, vận động, huyết áp, BMI, thuốc và thói quen.")
    Feature(Icons.Rounded.PhotoCamera, HealthPalette.Orange, "Chụp ảnh món ăn", "AI ước tính calo, bạn kiểm tra trước khi lưu.")
    Feature(Icons.Rounded.Medication, HealthPalette.Indigo, "Nhắc uống thuốc", "Đánh dấu “Đã uống” ngay trên thông báo.")
    Text("Mất khoảng 1 phút để thiết lập.", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Feature(icon: ImageVector, accent: Accent, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AboutStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var showPicker by remember { mutableStateOf(false) }
    StepTitle("Về bạn", "Dùng để ước tính nhu cầu năng lượng.")
    OutlinedTextField(state.name, vm::setName, label = { Text("Tên của bạn") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
    Spacer(Modifier.height(12.dp))
    AppCard(Modifier.fillMaxWidth(), onClick = { showPicker = true }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Cake, HealthPalette.Pink, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ngày sinh", style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
                Text(
                    state.birthDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))?.let { d -> "$d · ${HealthCalculations.ageOn(state.birthDate)} tuổi" } ?: "Chọn ngày",
                    style = MaterialTheme.typography.titleMedium, color = if (state.birthDate == null) HealthPalette.Subtle else HealthPalette.Ink
                )
            }
            Icon(Icons.Rounded.CalendarMonth, null, tint = HealthPalette.Muted)
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Giới tính", style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Gender.MALE to "Nam", Gender.FEMALE to "Nữ", Gender.OTHER to "Khác").forEach { (g, label) ->
            FilterChip(
                selected = state.gender == g, onClick = { vm.setGender(g) }, label = { Text(label) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HealthPalette.Primary, selectedLabelColor = Color.White)
            )
        }
    }

    if (showPicker) {
        val initial = state.birthDate ?: LocalDate.of(2000, 1, 1)
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = (LocalDate.now().year - 110)..(LocalDate.now().year - 15)
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.setBirthDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Hủy") } }
        ) { DatePicker(picker) }
    }
}

@Composable
private fun BodyStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle("Cơ thể & vận động", "Chiều cao, cân nặng và mức vận động thường ngày.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(state.heightCm, vm::setHeight, label = { Text("Chiều cao (cm)") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
        OutlinedTextField(state.weightKg, vm::setWeight, label = { Text("Cân nặng (kg)") }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium)
    }
    Spacer(Modifier.height(16.dp))
    Text("Mức vận động", style = MaterialTheme.typography.labelLarge, color = HealthPalette.Ink)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        activityOptions.forEach { o ->
            val selected = state.activity == o.level
            Card(
                onClick = { vm.setActivity(o.level) }, shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = if (selected) HealthPalette.Emerald.soft else HealthPalette.Surface),
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HealthPalette.Primary else HealthPalette.Line)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(o.icon, HealthPalette.Emerald, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                        Text(o.detail, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                    }
                    if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = HealthPalette.Primary)
                }
            }
        }
    }
}

@Composable
private fun GoalsStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle("Mục tiêu mỗi ngày", "Ước tính theo công thức Mifflin–St Jeor. Bạn có thể sửa.")
    val t = state.targets
    GradientCard(HealthPalette.Orange.gradient, Modifier.fillMaxWidth()) {
        Text("Năng lượng duy trì", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.calorieGoal.ifBlank { "—" }, style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(" kcal/ngày", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(bottom = 6.dp))
        }
        if (t != null) Text("BMR ${t.bmr} kcal × hệ số vận động", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(state.calorieGoal, vm::setCalorieGoal, label = { Text("Mục tiêu năng lượng (kcal)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium)
    Spacer(Modifier.height(12.dp))
    if (t != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Macro("Đạm", t.proteinGrams, HealthPalette.Coral, Modifier.weight(1f))
        Macro("Tinh bột", t.carbsGrams, HealthPalette.Amber, Modifier.weight(1f))
        Macro("Chất béo", t.fatGrams, HealthPalette.Sky, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.bmi?.let { b ->
            val cat = HealthCalculations.bmiCategory(b)
            StatTile(Icons.Rounded.MonitorWeight, "BMI (chuẩn châu Á)", "%.1f".format(b), cat.label, HealthPalette.Emerald, Modifier.weight(1f))
        }
        state.waterGoalMl?.let { w -> StatTile(Icons.Rounded.WaterDrop, "Nước mỗi ngày", "$w ml", "≈35 ml/kg", HealthPalette.Sky, Modifier.weight(1f)) }
    }
    Text("Đây là ước tính tham khảo, không thay thế tư vấn của chuyên gia dinh dưỡng.", style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Macro(label: String, grams: Double, accent: Accent, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(accent.soft).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent.strong)
        Text("${grams.toInt()} g", style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = HealthPalette.Ink)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
    }
}
