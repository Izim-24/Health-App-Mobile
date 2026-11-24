package com.healthapp.presentation.ui.settings

import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.healthapp.presentation.ui.text.UiText
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.FileUpload
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.healthapp.presentation.ui.text.*
import com.healthapp.core.AppLocale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import com.healthapp.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.healthapp.presentation.ui.theme.HealthPalette
import com.healthapp.presentation.ui.components.*
import com.healthapp.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    // Restore: pick a file (system picker, no storage permission), confirm, then read it off the main thread.
    var pickedBackup by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickedBackup = uri }
    val scope = rememberCoroutineScope()
    val tooLarge = UiText.Res(R.string.err_file_too_large)
    pickedBackup?.let { uri ->
        AlertDialog(
            onDismissRequest = { pickedBackup = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pickedBackup = null
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    val bytes = input.readBytes()
                                    if (bytes.size > MAX_BACKUP_BYTES) null else String(bytes, Charsets.UTF_8)
                                }
                            }.getOrNull()
                        }
                        if (text == null) viewModel.showError(tooLarge) else viewModel.importBackup(text)
                    }
                }) { Text(stringResource(R.string.import_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pickedBackup = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    LaunchedEffect(state.exportFile) {
        val file = state.exportFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_chooser)))
        viewModel.exportConsumed()
    }

    Scaffold(
        containerColor = HealthPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BackTopBar(stringResource(R.string.settings_title)) { navController.popBackStack() }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeSelector(state.themeMode, viewModel::setThemeMode)
            LanguageSelector()
            SettingRow(Icons.Default.Notifications, HealthPalette.Amber, stringResource(R.string.link_reminders), stringResource(R.string.set_reminders_sub)) { navController.navigate("reminders") }
            SettingRow(Icons.Default.Sync, HealthPalette.Sky, "Health Connect", stringResource(R.string.set_hc_sub)) { navController.navigate("health_connect") }
            SettingRow(Icons.Default.Medication, HealthPalette.Indigo, stringResource(R.string.link_meds), stringResource(R.string.set_meds_sub)) { navController.navigate("medications") }
            SettingRow(Icons.Default.FileDownload, HealthPalette.Emerald, stringResource(R.string.set_export), stringResource(R.string.set_export_sub)) { if (!state.busy) viewModel.export() }
            SettingRow(Icons.Default.FileUpload, HealthPalette.Sky, stringResource(R.string.set_import), stringResource(R.string.set_import_sub)) {
                if (!state.busy) pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            if (com.healthapp.BuildConfig.DEMO_TOOLS) {
                SettingRow(Icons.Default.Science, HealthPalette.Violet, stringResource(R.string.set_demo), stringResource(R.string.set_demo_sub)) { if (!state.busy) viewModel.seedDemoData() }
                if (state.hasDemoData) SettingRow(Icons.Default.LayersClear, HealthPalette.Amber, stringResource(R.string.set_demo_clear), stringResource(R.string.set_demo_clear_sub)) { if (!state.busy) viewModel.clearDemoData() }
            }
            SettingRow(Icons.Default.DeleteForever, HealthPalette.Coral, stringResource(R.string.set_delete_all), stringResource(R.string.set_delete_all_sub), danger = true) { confirmDelete = true }

            AppCard(Modifier.fillMaxWidth(), color = HealthPalette.Emerald.soft) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, null, tint = HealthPalette.Emerald.strong)
                    Text(
                        stringResource(R.string.set_privacy_note),
                        style = MaterialTheme.typography.bodySmall, color = HealthPalette.Ink
                    )
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it.resolve(), color = HealthPalette.Primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.set_delete_title)) },
            text = { Text(stringResource(R.string.set_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAll() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.set_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, accent: com.healthapp.presentation.ui.theme.Accent, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon, accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) accent.strong else HealthPalette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = HealthPalette.Subtle, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/** Giao diện: follow the system, or force light/dark. */
@Composable
private fun ThemeSelector(mode: com.healthapp.domain.model.ThemeMode, onSelect: (com.healthapp.domain.model.ThemeMode) -> Unit) {
    SegmentedSetting(
        Icons.Default.DarkMode, HealthPalette.Violet, stringResource(R.string.appearance), stringResource(R.string.appearance_sub),
        options = listOf(
            com.healthapp.domain.model.ThemeMode.SYSTEM to stringResource(R.string.follow_phone),
            com.healthapp.domain.model.ThemeMode.LIGHT to stringResource(R.string.theme_light),
            com.healthapp.domain.model.ThemeMode.DARK to stringResource(R.string.theme_dark)
        ),
        selected = mode, onSelect = onSelect
    )
}

/** In-app language (Android 13+ also shows it in system Settings → Apps → Language). */
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    // Language names are written in their own language, so they read the same whichever is active.
    SegmentedSetting(
        Icons.Default.Translate, HealthPalette.Sky, stringResource(R.string.language), stringResource(R.string.language_sub),
        options = listOf(AppLocale.SYSTEM to stringResource(R.string.follow_phone), "vi" to "Tiếng Việt", "en" to "English"),
        selected = AppLocale.current(context),
        onSelect = { tag -> context.findActivity()?.let { AppLocale.set(it, tag) } }
    )
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun <T> SegmentedSetting(
    icon: ImageVector, accent: com.healthapp.presentation.ui.theme.Accent, title: String, subtitle: String,
    options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, accent)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = HealthPalette.Ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HealthPalette.Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.CircleShape).background(HealthPalette.SurfaceAlt).padding(4.dp)
                    .selectableGroup()
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (isSelected) accent.strong else androidx.compose.ui.graphics.Color.Transparent)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else HealthPalette.Muted)
                    }
                }
            }
        }
    }
}

/** Exports are a few hundred KB; refuse anything far larger rather than loading it into memory. */
private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
