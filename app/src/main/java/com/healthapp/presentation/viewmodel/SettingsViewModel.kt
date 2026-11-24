package com.healthapp.presentation.viewmodel

import com.healthapp.presentation.ui.text.UiText
import com.healthapp.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.DataManagementRepository
import com.healthapp.domain.repository.DemoDataRepository
import com.healthapp.domain.repository.AppSettingsRepository
import com.healthapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val busy: Boolean = false,
    val exportFile: File? = null,
    val message: UiText? = null,
    val hasDemoData: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DataManagementRepository,
    private val demo: DemoDataRepository,
    private val appSettings: AppSettingsRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            mutable.update { it.copy(hasDemoData = demo.hasDemoData(), themeMode = appSettings.settings.first().themeMode) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        mutable.update { it.copy(themeMode = mode) }
        viewModelScope.launch { appSettings.setThemeMode(mode) }
    }

    /** Debug builds only (the button is hidden in release): fills 14 days of clearly labelled sample data. */
    fun seedDemoData() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            demo.seed()
                .onSuccess { days -> mutable.update { it.copy(busy = false, hasDemoData = true, message = UiText.Res(R.string.msg_demo_loaded, listOf(days))) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_demo_load_failed, listOf(e.message.orEmpty()))) } }
        }
    }

    fun clearDemoData() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            demo.clear()
                .onSuccess { mutable.update { it.copy(busy = false, hasDemoData = false, message = UiText.Res(R.string.msg_demo_cleared)) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_demo_clear_failed, listOf(e.message.orEmpty()))) } }
        }
    }

    /** Restores a backup the user picked; [json] is the file's text (read by the screen, which has the Uri). */
    fun importBackup(json: String) {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.importFromJson(json)
                .onSuccess { n -> mutable.update { it.copy(busy = false, message = UiText.Plural(R.plurals.msg_imported, n, listOf(n))) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_import_failed, listOf(e.message.orEmpty()))) } }
        }
    }

    fun showError(text: UiText) = mutable.update { it.copy(busy = false, message = text) }

    fun export() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.exportToFile()
                .onSuccess { file -> mutable.update { it.copy(busy = false, exportFile = file) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_export_failed, listOf(e.message.orEmpty()))) } }
        }
    }

    fun exportConsumed() = mutable.update { it.copy(exportFile = null) }

    fun deleteAll() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.deleteAllData()
                .onSuccess { mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_deleted_all)) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = UiText.Res(R.string.msg_delete_failed, listOf(e.message.orEmpty()))) } }
        }
    }
}
