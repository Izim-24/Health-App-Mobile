package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.repository.DataManagementRepository
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
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DataManagementRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsUiState())
    val state = mutable.asStateFlow()

    fun export() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.exportToFile()
                .onSuccess { file -> mutable.update { it.copy(busy = false, exportFile = file) } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = "Xuất dữ liệu thất bại: ${e.message}") } }
        }
    }

    fun exportConsumed() = mutable.update { it.copy(exportFile = null) }

    fun deleteAll() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.deleteAllData()
                .onSuccess { mutable.update { it.copy(busy = false, message = "Đã xóa toàn bộ dữ liệu trên thiết bị.") } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = "Không thể xóa dữ liệu: ${e.message}") } }
        }
    }
}
