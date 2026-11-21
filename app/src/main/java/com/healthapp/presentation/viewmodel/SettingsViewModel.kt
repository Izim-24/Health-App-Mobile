package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.domain.repository.DataManagementRepository
import com.healthapp.domain.repository.DemoDataRepository
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
    val message: String? = null,
    val hasDemoData: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DataManagementRepository,
    private val demo: DemoDataRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsUiState())
    val state = mutable.asStateFlow()

    init { viewModelScope.launch { mutable.update { it.copy(hasDemoData = demo.hasDemoData()) } } }

    /** Debug builds only (the button is hidden in release): fills 14 days of clearly labelled sample data. */
    fun seedDemoData() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            demo.seed()
                .onSuccess { days -> mutable.update { it.copy(busy = false, hasDemoData = true, message = "Đã nạp dữ liệu mẫu cho $days ngày gần nhất.") } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = "Không nạp được dữ liệu mẫu: ${e.message}") } }
        }
    }

    fun clearDemoData() {
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            demo.clear()
                .onSuccess { mutable.update { it.copy(busy = false, hasDemoData = false, message = "Đã xóa dữ liệu mẫu. Dữ liệu của bạn được giữ nguyên.") } }
                .onFailure { e -> mutable.update { it.copy(busy = false, message = "Không xóa được dữ liệu mẫu: ${e.message}") } }
        }
    }

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
