package com.warehouse.kiosk.presentation.device_info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.manager.DeviceOwnerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val repository: KioskRepository,
    private val deviceOwnerManager: DeviceOwnerManager
) : ViewModel() {

    val staffName = repository.staffName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val locationName = repository.locationName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    fun updateStaffName(newName: String) {
        viewModelScope.launch {
            repository.setStaffName(newName)
            try {
                deviceOwnerManager.setLockScreenInfo(newName)
            } catch (e: SecurityException) {
                // Log if not Device Owner, but still save to preferences
                android.util.Log.w("DeviceInfoViewModel", "Cannot set lock screen info: ${e.message}")
            }
        }
    }

    fun updateLocationName(newLocation: String) {
        viewModelScope.launch {
            repository.setLocationName(newLocation)
        }
    }
}