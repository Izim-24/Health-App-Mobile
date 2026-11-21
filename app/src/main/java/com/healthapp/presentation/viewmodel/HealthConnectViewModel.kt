package com.healthapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.PermissionController
import com.healthapp.domain.repository.HealthSyncRepository
import com.healthapp.domain.model.HcState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val manager: HealthSyncRepository
) : ViewModel() {
    val state: StateFlow<HcState> = manager.state
    val permissions = manager.permissions
    // Android permission UI contract: platform code, so it lives in presentation.
    val permissionContract = PermissionController.createRequestPermissionResultContract()

    init { refresh() }

    fun refresh() = viewModelScope.launch { manager.refresh() }
    fun onPermissionsResult(granted: Set<String>) = viewModelScope.launch { manager.onPermissionsResult(granted) }
    fun syncNow() = viewModelScope.launch { manager.sync(force = false) }
    fun disconnect(removeImported: Boolean) = viewModelScope.launch { manager.disconnect(removeImported) }
    fun clearError() = manager.clearError()
}
