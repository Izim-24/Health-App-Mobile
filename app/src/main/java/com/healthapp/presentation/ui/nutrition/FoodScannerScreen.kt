package com.healthapp.presentation.ui.nutrition

import kotlin.math.abs
import com.healthapp.domain.usecase.FoodScan
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.healthapp.presentation.ui.theme.Illustration
import com.healthapp.presentation.ui.theme.IllustrationImage
import com.healthapp.presentation.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.healthapp.domain.model.MealType
import com.healthapp.domain.usecase.ScannedFood
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.viewmodel.AIFoodScannerViewModel
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScannerScreen(navController: NavController, viewModel: AIFoodScannerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val orange = HealthPalette.Orange
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // System photo picker: no storage permission, and the user shares only the one photo they pick.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { viewModel.analyzeImage(it.toString()) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) viewModel.analyzeImage(uri.toString())
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest capture
        val file = File(dir, "meal-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }
    // The app declares CAMERA, so Android requires it to be granted before using the camera intent.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val onCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BackTopBar(stringResource(R.string.scan_food)) { navController.popBackStack() } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Photo
            Box(
                Modifier.fillMaxWidth()
                    // Fixed height for the photo; the empty state may grow (illustration + tips, large fonts).
                    .then(if (state.imageUri != null) Modifier.height(230.dp) else Modifier.heightIn(min = 230.dp))
                    .clip(RoundedCornerShape(26.dp)).background(orange.soft),
                contentAlignment = Alignment.Center
            ) {
                if (state.imageUri != null) {
                    AsyncImage(model = state.imageUri, contentDescription = stringResource(R.string.food_photo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (state.isAnalyzing) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(color = Color.White)
                                Text(stringResource(R.string.analyzing_photo), color = Color.White, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                        IllustrationImage(Illustration.SCAN, Modifier.height(120.dp).aspectRatio(1.25f))
                        Text(stringResource(R.string.take_or_pick), style = MaterialTheme.typography.titleMedium, color = HealthPalette.Ink)
                        Text(stringResource(R.string.photo_tips), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(stringResource(R.string.photo_tip_reference), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Orange.strong, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.take_photo), onCamera, Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera, enabled = !state.isAnalyzing, color = orange.strong)
                SoftButton(stringResource(R.string.gallery), { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, orange, Modifier.weight(1f).height(52.dp), icon = Icons.Rounded.PhotoLibrary, enabled = !state.isAnalyzing)
            }
            Text(
                stringResource(R.string.photo_privacy),
                style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 10.dp)
            )

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                AppCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, color = HealthPalette.Coral.soft) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = HealthPalette.Coral.strong)
                        Spacer(Modifier.width(10.dp))
                        Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = HealthPalette.Ink)
                    }
                }
            }

            if (state.hasResult) ResultSection(
                items = state.items, notes = state.notes, isMock = state.isMock, mealType = state.mealType, saving = state.isSaving,
                onGrams = viewModel::setGrams, onPortion = viewModel::setPortion, onScaleAll = viewModel::scaleAll, onRemove = viewModel::removeItem, onMealType = viewModel::setMealType, onSave = viewModel::save
            )

            if (!state.hasResult) {
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_manually)) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultSection(
    items: List<ScannedFood>, notes: String?, isMock: Boolean, mealType: MealType, saving: Boolean,
    onGrams: (Int, Int) -> Unit, onPortion: (Int, Double) -> Unit, onScaleAll: (Double) -> Unit,
    onRemove: (Int) -> Unit, onMealType: (MealType) -> Unit, onSave: () -> Unit
) {
    val totals = com.healthapp.domain.usecase.FoodScan.totals(items)
    Spacer(Modifier.height(18.dp))
    if (isMock) {
        AppCard(Modifier.fillMaxWidth(), color = HealthPalette.Amber.soft) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Science, null, tint = HealthPalette.Amber.strong)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.mock_banner), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    GradientCard(HealthPalette.Orange.gradient, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.estimate_for_meal), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${totals.calories}", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(stringResource(R.string.kcal_unit), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = .85f), modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.macro_protein) to totals.protein, stringResource(R.string.macro_carbs) to totals.carbs, stringResource(R.string.macro_fat) to totals.fat).forEach { (label, g) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .2f)).padding(10.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .9f))
                    Text("${g.toInt()} g", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
    notes?.let {
        Text(stringResource(R.string.tip_prefix, it), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(top = 10.dp))
    }

    SectionHeader(stringResource(R.string.check_each_item), modifier = Modifier.padding(top = 14.dp))
    Text(stringResource(R.string.check_each_sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted, modifier = Modifier.padding(bottom = 10.dp))
    WholeMealPortion(items, onScaleAll)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, item ->
            ScannedFoodRow(item, onGrams = { onGrams(index, it) }, onPortion = { onPortion(index, it) }, onRemove = { onRemove(index) })
        }
    }

    SectionHeader(stringResource(R.string.meal), modifier = Modifier.padding(top = 14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { t ->
            FilterChip(
                selected = mealType == t, onClick = { onMealType(t) }, label = { Text(stringResource(t.shortRes())) }, shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HealthPalette.Orange.strong, selectedLabelColor = Color.White)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryButton(if (saving) stringResource(R.string.saving) else stringResource(R.string.save_to_diary, totals.calories), onSave, enabled = !saving && items.isNotEmpty(), icon = Icons.Rounded.Check, color = HealthPalette.Orange.strong)
}

@Composable
private fun ScannedFoodRow(item: ScannedFood, onGrams: (Int) -> Unit, onPortion: (Double) -> Unit, onRemove: () -> Unit) {
    var gramsText by remember(item.name) { mutableStateOf(item.grams.toString()) }
    // Keep the text field in step when a portion chip changes the grams.
    LaunchedEffect(item.grams) { if (gramsText.toIntOrNull() != item.grams) gramsText = item.grams.toString() }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                    Text(stringResource(R.string.item_macros, oneDecimal(item.protein), oneDecimal(item.carbs), oneDecimal(item.fat)), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
                Text("${item.calories} kcal", style = MaterialTheme.typography.titleSmall, color = HealthPalette.Orange.strong)
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_named, item.name), tint = HealthPalette.Subtle) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { v ->
                        val digits = v.filter(Char::isDigit).take(4)
                        gramsText = digits
                        digits.toIntOrNull()?.takeIf { it > 0 }?.let(onGrams)
                    },
                    label = { Text(stringResource(R.string.portion_g)) }, singleLine = true, modifier = Modifier.width(140.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(stringResource(R.string.confidence_pct, (item.confidence * 100).toInt()), if (item.lowConfidence) HealthPalette.Amber else HealthPalette.Emerald)
                    Pill(stringResource(if (item.fromTable) R.string.kcal_from_table else R.string.kcal_from_ai), if (item.fromTable) HealthPalette.Indigo else HealthPalette.Orange)
                    if (item.energyMismatch) Pill(stringResource(R.string.kcal_mismatch), HealthPalette.Coral)
                }
            }
            Text(stringResource(R.string.portion_quick), style = MaterialTheme.typography.labelMedium, color = HealthPalette.Muted)
            PortionChips(item.portionFactor, onPortion)
        }
    }
}

private fun factorLabel(f: Double) = when (f) { 0.5 -> "½"; 0.75 -> "¾"; 1.25 -> "1¼"; 1.5 -> "1½"; else -> "1" }

/** ½ · ¾ · AI · 1¼ · 1½ — one tap scales the item relative to the AI's estimate. */
@Composable
private fun PortionChips(current: Double, onPick: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FoodScan.PORTION_STEPS.forEach { f ->
            val selected = abs(current - f) < 0.02
            val cd = stringResource(R.string.portion_factor_cd, factorLabel(f))
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape)
                    .background(if (selected) HealthPalette.Orange.strong else HealthPalette.Orange.soft)
                    .selectable(selected, role = Role.RadioButton, onClick = { onPick(f) })
                    .semantics { contentDescription = cd },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (f == 1.0) stringResource(R.string.portion_ai) else "×" + factorLabel(f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else HealthPalette.Orange.strong
                )
            }
        }
    }
}

/** Scale the whole meal at once, with the measured reason for doing so. */
@Composable
private fun WholeMealPortion(items: List<ScannedFood>, onScaleAll: (Double) -> Unit) {
    val common = items.map { it.portionFactor }.distinct().singleOrNull()
    AppCard(Modifier.fillMaxWidth(), color = HealthPalette.Amber.soft) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.whole_meal), style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
            Text(stringResource(R.string.whole_meal_sub), style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85 to R.string.portion_less, 1.0 to R.string.portion_as_photo, 1.15 to R.string.portion_more).forEach { (f, label) ->
                    val selected = common != null && abs(common - f) < 0.02
                    Box(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                            .background(if (selected) HealthPalette.Amber.strong else HealthPalette.Surface)
                            .selectable(selected, role = Role.RadioButton, onClick = { onScaleAll(f) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else HealthPalette.Ink)
                    }
                }
            }
        }
    }
}
