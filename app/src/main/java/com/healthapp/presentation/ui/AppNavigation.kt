package com.healthapp.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthapp.presentation.ui.theme.HealthAppTheme
import com.healthapp.presentation.ui.dashboard.DashboardScreen
import com.healthapp.presentation.ui.nutrition.NutritionScreen
import com.healthapp.presentation.ui.nutrition.FoodScannerScreen
import com.healthapp.presentation.ui.habits.HabitListScreen
import com.healthapp.presentation.ui.water.WaterTrackingScreen
import com.healthapp.presentation.ui.profile.ProfileScreen
import com.healthapp.presentation.ui.reports.ReportsScreen
import com.healthapp.presentation.ui.health.HealthTrackingScreen
import com.healthapp.presentation.ui.mental.MentalHealthScreen
import com.healthapp.presentation.ui.ai.AIChatScreen
import com.healthapp.presentation.ui.reminders.RemindersScreen
import com.healthapp.presentation.ui.settings.SettingsScreen
import com.healthapp.presentation.ui.medication.MedicationScreen
import com.healthapp.presentation.ui.healthconnect.HealthConnectScreen
import com.healthapp.presentation.ui.theme.HealthPalette

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: com.healthapp.presentation.ui.theme.Accent)

@Composable
fun AppNavigation(startDestination: String = "dashboard") {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab("dashboard", "Trang chủ", Icons.Rounded.Home, HealthPalette.Emerald),
        Tab("health", "Sức khỏe", Icons.Rounded.MonitorHeart, HealthPalette.Coral),
        Tab("nutrition", "Ăn uống", Icons.Rounded.Restaurant, HealthPalette.Orange),
        Tab("habits", "Thói quen", Icons.Rounded.CheckCircle, HealthPalette.Violet),
        Tab("mental", "Tinh thần", Icons.Rounded.Spa, HealthPalette.Pink)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showNavigation = currentDestination?.route !in setOf("food_scanner", "onboarding", null)

    Surface(modifier = Modifier.fillMaxSize(), color = HealthPalette.Background) {
        Scaffold(
            containerColor = HealthPalette.Background,
            bottomBar = {
                if (showNavigation) {
                    Column {
                        HorizontalDivider(color = HealthPalette.Line, thickness = 1.dp)
                        NavigationBar(containerColor = HealthPalette.Surface, tonalElevation = 0.dp) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = tab.route != "dashboard" }
                                            launchSingleTop = true
                                            restoreState = tab.route != "dashboard"
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = tab.accent.strong,
                                        selectedTextColor = tab.accent.strong,
                                        indicatorColor = tab.accent.soft,
                                        unselectedIconColor = HealthPalette.Subtle,
                                        unselectedTextColor = HealthPalette.Muted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("onboarding") {
                    com.healthapp.presentation.ui.onboarding.OnboardingScreen(onFinished = {
                        navController.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } }
                    })
                }
                composable("dashboard") { DashboardScreen(navController) }
                composable("nutrition") { NutritionScreen(navController) }
                composable("health") { HealthTrackingScreen(navController) }
                composable("food_scanner") { FoodScannerScreen(navController) }
                composable("habits") { HabitListScreen(navController) }
                composable("water") { WaterTrackingScreen(navController) }
                composable("reports") { ReportsScreen(navController) }
                composable("profile") { ProfileScreen(navController) }
                composable("mental") { MentalHealthScreen() }
                composable("ai_chat") { AIChatScreen(onBack = { navController.popBackStack() }) }
                composable("reminders") { RemindersScreen(navController) }
                composable("settings") { SettingsScreen(navController) }
                composable("medications") { MedicationScreen(navController) }
                composable("health_connect") { HealthConnectScreen(navController) }
            }
        }
    }
}

@Composable
fun HealthAppRoot(appViewModel: com.healthapp.presentation.viewmodel.AppViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val app by appViewModel.state.collectAsState()
    HealthAppTheme(themeMode = app.themeMode) {
        if (app.ready) AppNavigation(app.startDestination)
        else Surface(Modifier.fillMaxSize(), color = HealthPalette.Background) {}
    }
}
