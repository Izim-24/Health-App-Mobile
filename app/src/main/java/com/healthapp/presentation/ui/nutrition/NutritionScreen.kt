package com.healthapp.presentation.ui.nutrition

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.theme.ArtworkKind
import com.healthapp.presentation.ui.theme.WellnessArtwork
import com.healthapp.domain.model.Food
import com.healthapp.domain.model.Meal
import com.healthapp.domain.model.MealType
import com.healthapp.presentation.ui.dashboard.NutritionSummaryCard
import com.healthapp.presentation.viewmodel.NutritionViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NutritionScreen(navController: NavController, viewModel: NutritionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<String?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var preselectedFood by remember { mutableStateOf<Food?>(null) }
    Column(Modifier.fillMaxSize().background(HealthPalette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(stringResource(R.string.section_nutrition), stringResource(R.string.nutrition_header), subtitle = stringResource(R.string.nutrition_header_sub), accent = HealthPalette.Orange)

        GradientCard(HealthPalette.Orange.gradient, Modifier.fillMaxWidth(), onClick = { navController.navigate("food_scanner") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.scan_card_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(stringResource(R.string.scan_card_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, null, tint = HealthPalette.Orange.strong, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_food), style = MaterialTheme.typography.labelLarge, color = HealthPalette.Orange.strong)
                    }
                }
                WellnessArtwork(ArtworkKind.FOOD, Modifier.size(96.dp))
            }
        }

        SectionHeader(stringResource(R.string.today), modifier = Modifier.padding(top = 14.dp))
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HealthPalette.Orange.strong) }
            state.summary != null -> NutritionSummaryCard(state.summary!!)
            else -> EmptyCard(stringResource(R.string.dash_no_nutrition))
        }

        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.meals), style = MaterialTheme.typography.titleLarge, color = HealthPalette.Ink, modifier = Modifier.weight(1f))
            SoftButton(stringResource(R.string.add_food), { preselectedFood = null; showAddDialog = true }, HealthPalette.Orange, icon = Icons.Rounded.Add)
        }
        mealToDelete?.let { id ->
            ConfirmDeleteDialog(
                stringResource(R.string.confirm_delete_meal), stringResource(R.string.confirm_delete_meal_body),
                onConfirm = { viewModel.deleteMeal(id) }, onDismiss = { mealToDelete = null }
            )
        }
        if (state.meals.isEmpty()) {
            EmptyState(Illustration.MEALS, stringResource(R.string.meals_empty_title), stringResource(R.string.no_meals_today))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MealType.entries.forEach { type ->
                    state.meals.filter { it.mealType == type }.forEach { meal ->
                        MealCard(meal, onDelete = { mealToDelete = meal.id })
                    }
                }
            }
        }
        state.error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) }

        SectionHeader(stringResource(R.string.food_library), modifier = Modifier.padding(top = 18.dp), action = stringResource(R.string.enter_own)) { showCustomDialog = true }
        Text(stringResource(R.string.food_library_sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.foods.take(6).forEach { food ->
                AppCard(Modifier.fillMaxWidth(), onClick = { preselectedFood = food; showAddDialog = true }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.RestaurantMenu, HealthPalette.Amber, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                            Text(stringResource(R.string.food_line, food.calories, food.servingSize, food.sourceLabel()), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(34.dp).clip(CircleShape).background(HealthPalette.Orange.soft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.add_named, food.name), tint = HealthPalette.Orange.strong, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showAddDialog) {
        FoodAddDialog(
            foods = state.foods,
            isSearching = state.isSearchingFoods,
            initialFood = preselectedFood,
            onSearch = viewModel::searchFoods,
            onFilter = viewModel::filterFoods,
            onDismiss = { showAddDialog = false; viewModel.filterFoods("") },
            onAdd = { food, grams, type -> viewModel.addFood(food, grams, type); showAddDialog = false; preselectedFood = null; viewModel.filterFoods("") }
        )
    }
    if (showCustomDialog) CustomFoodDialog(onDismiss = { showCustomDialog = false }, onSave = { name, calories, protein, carbs, fat ->
        viewModel.addCustomFood(name, calories, protein, carbs, fat)
        showCustomDialog = false
    })
}

@Composable
private fun CustomFoodDialog(onDismiss: () -> Unit, onSave: (String, Int, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    fun number(value: String) = value.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (calories.toIntOrNull() ?: -1) in 0..2000 && listOf(protein, carbs, fat).all { (number(it) ?: -1.0) in 0.0..100.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_food_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.custom_food_sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.food_name)) }, singleLine = true)
            OutlinedTextField(calories, { calories = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.kcal_per_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(protein, { protein = it }, label = { Text(stringResource(R.string.protein_per_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(carbs, { carbs = it }, label = { Text(stringResource(R.string.carbs_per_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(fat, { fat = it }, label = { Text(stringResource(R.string.fat_per_100)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { onSave(name, calories.toInt(), number(protein)!!, number(carbs)!!, number(fat)!!) }, enabled = valid) { Text(stringResource(R.string.save_food)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun MealType.accent() = when (this) {
    MealType.BREAKFAST -> HealthPalette.Amber
    MealType.LUNCH -> HealthPalette.Orange
    MealType.DINNER -> HealthPalette.Indigo
    MealType.SNACK -> HealthPalette.Pink
}

private fun MealType.icon() = when (this) {
    MealType.BREAKFAST -> Icons.Rounded.WbSunny
    MealType.LUNCH -> Icons.Rounded.LunchDining
    MealType.DINNER -> Icons.Rounded.DinnerDining
    MealType.SNACK -> Icons.Rounded.Cookie
}

@Composable
private fun MealCard(meal: Meal, onDelete: () -> Unit) {
    val accent = meal.mealType.accent()
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(meal.mealType.icon(), accent, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(meal.mealType.longRes()), style = MaterialTheme.typography.labelMedium, color = accent.strong)
                Text(meal.items.joinToString { it.food.name }.ifBlank { stringResource(R.string.meal_fallback) }, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(stringResource(R.string.meal_macros, meal.items.sumOf { it.portionGrams }, meal.totalProtein.toInt(), meal.totalCarbs.toInt(), meal.totalFat.toInt()), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${meal.totalCalories}", style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete_meal), tint = HealthPalette.Subtle) }
        }
    }
}

@Composable
private fun FoodAddDialog(foods: List<Food>, isSearching: Boolean, initialFood: Food?, onSearch: (String) -> Unit, onFilter: (String) -> Unit, onDismiss: () -> Unit, onAdd: (Food, Int, MealType) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedFood by remember(initialFood?.id) { mutableStateOf(initialFood) }
    var grams by remember(initialFood?.id) { mutableStateOf(initialFood?.servingSize?.toString() ?: "100") }
    var mealType by remember { mutableStateOf(MealType.SNACK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (selectedFood == null) R.string.choose_food else R.string.add_portion)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (selectedFood == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; onFilter(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.food_or_product)) },
                        placeholder = { Text(stringResource(R.string.food_search_example)) },
                        trailingIcon = {
                            IconButton(onClick = { onSearch(query) }, enabled = query.trim().length >= 2) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_food))
                            }
                        },
                        singleLine = true
                    )
                    Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                    Spacer(Modifier.height(8.dp))
                    if (isSearching) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (foods.isEmpty()) {
                        Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(foods.take(25), key = { it.id }) { food ->
                                TextButton(onClick = { selectedFood = food; grams = food.servingSize.toString() }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(food.name, color = HealthPalette.Ink)
                                        Text(stringResource(R.string.food_line_per, food.calories, food.servingSize, food.sourceLabel()), style = MaterialTheme.typography.labelSmall, color = HealthPalette.Muted)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(selectedFood!!.name, style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.portion_step1, selectedFood!!.servingSize), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = grams, onValueChange = { value -> if (value.all(Char::isDigit)) grams = value }, label = { Text(stringResource(R.string.portion_grams)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MealType.entries.forEach { type -> FilterChip(selected = mealType == type, onClick = { mealType = type }, label = { Text(stringResource(type.shortRes())) }) }
                    }
                    Text(stringResource(R.string.portion_step2, selectedFood!!.sourceLabel()), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
            }
        },
        confirmButton = {
            if (selectedFood != null) TextButton(onClick = { onAdd(selectedFood!!, grams.toIntOrNull() ?: 0, mealType) }, enabled = (grams.toIntOrNull() ?: 0) > 0) { Text(stringResource(R.string.save_meal)) }
        },
        dismissButton = { TextButton(onClick = { if (selectedFood == null) onDismiss() else selectedFood = null }) { Text(stringResource(if (selectedFood == null) R.string.action_close else R.string.action_back)) } }
    )
}

@Composable
private fun EmptyCard(message: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(18.dp), color = HealthPalette.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Food.sourceLabel(): String = when (source) {
    "OPEN_FOOD_FACTS" -> stringResource(R.string.source_off)
    "USER_CREATED" -> stringResource(R.string.source_user)
    "VN_LIBRARY" -> stringResource(R.string.source_vn)
    else -> stringResource(R.string.source_builtin)
}
