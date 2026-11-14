package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthapp.data.healthconnect.HealthConnectManager
import com.healthapp.data.healthconnect.HcState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val manager: HealthConnectManager
) : ViewModel() {
    val state: StateFlow<HcState> = manager.state
    val permissions = HealthConnectManager.PERMISSIONS
    val permissionContract = manager.permissionContract()

    init { refresh() }

    fun refresh() = viewModelScope.launch { manager.refresh() }
    fun onPermissionsResult(granted: Set<String>) = viewModelScope.launch { manager.onPermissionsResult(granted) }
    fun syncNow() = viewModelScope.launch { manager.sync(force = false) }
    fun disconnect(removeImported: Boolean) = viewModelScope.launch { manager.disconnect(removeImported) }
    fun clearError() = manager.clearError()
}
