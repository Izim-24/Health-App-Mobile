package com.healthapp.presentation.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.Accent
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.ReportPeriod
import com.healthapp.presentation.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar("Báo cáo sức khỏe") { navController.popBackStack() } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Tổng hợp từ dữ liệu bạn đã ghi trên thiết bị.", style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp)) {
                ReportPeriod.entries.forEach { period ->
                    val selected = state.period == period
                    Box(
                        Modifier.weight(1f).clip(CircleShape).background(if (selected) HealthPalette.Indigo.strong else Color.Transparent)
                            .clickable { viewModel.select(period) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(period.label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else HealthPalette.Muted)
                    }
                }
            }
            Text(state.range, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Spacer(Modifier.height(14.dp))
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthPalette.Indigo.strong)
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportCard(Icons.Rounded.LocalFireDepartment, HealthPalette.Orange, "Dinh dưỡng",
                        if (state.nutritionDays == 0) "Chưa ghi bữa ăn trong khoảng này." else "Trung bình ${state.nutritionDays} ngày có ghi") {
                        if (state.nutritionDays > 0) {
                            ReportItem("Năng lượng", "${state.avgCalories} kcal/ngày")
                            ReportItem("Đạm", "${state.avgProtein} g/ngày")
                            ReportItem("Tinh bột", "${state.avgCarbs} g/ngày")
                            ReportItem("Chất béo", "${state.avgFat} g/ngày")
                        }
                    }
                    ReportCard(Icons.AutoMirrored.Rounded.DirectionsWalk, HealthPalette.Coral, "Vận động",
                        if (state.activityDays == 0) "Chưa ghi vận động trong khoảng này." else "Trung bình ${state.activityDays} ngày có ghi") {
                        if (state.activityDays > 0) {
                            ReportItem("Số bước", "${state.avgSteps} bước/ngày")
                            ReportItem("Tổng thời gian", "${state.totalExerciseMinutes} phút")
                            ReportItem("Năng lượng ghi nhận", "${state.totalCaloriesBurned} kcal")
                        }
                    }
                    ReportCard(Icons.Rounded.WaterDrop, HealthPalette.Sky, "Nước uống",
                        if (state.waterDays == 0) "Chưa ghi nước uống trong khoảng này." else "Trung bình ${state.waterDays} ngày có ghi") {
                        if (state.waterDays > 0) ReportItem("Lượng nước", "${state.avgWaterMl} ml/ngày")
                    }
                    ReportCard(Icons.Rounded.CheckCircle, HealthPalette.Violet, "Thói quen",
                        if (state.activeHabits == 0) "Chưa tạo thói quen." else "Đang theo dõi ${state.activeHabits} thói quen") {
                        if (state.activeHabits > 0) ReportItem("Lượt hoàn thành", "${state.completedHabits}")
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ReportCard(icon: ImageVector, accent: Accent, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun ReportItem(label: String, value: String) {
    HorizontalDivider(color = HealthPalette.Line, modifier = Modifier.padding(vertical = 4.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
    }
}
