package com.warehouse.kiosk.presentation.kiosk_settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KioskSettingsViewModel @Inject constructor(
    private val repository: KioskRepository
) : ViewModel() {

    val isKioskModeActive = repository.isKioskModeActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun onKioskModeChanged(isActive: Boolean) {
        viewModelScope.launch {
            repository.setKioskModeActive(isActive)
        }
    }
}