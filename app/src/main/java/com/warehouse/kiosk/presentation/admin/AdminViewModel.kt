package com.warehouse.kiosk.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: KioskRepository
) : ViewModel() {

    val isKioskModeActive = repository.isKioskModeActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val autoStartAppName = repository.autoStartAppPackage
        .combine(repository.getAllApps()) { selectedPackage, allApps ->
            if (selectedPackage == null) {
                "None"
            } else {
                allApps.find { it.packageName == selectedPackage }?.appName ?: "None"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "None"
        )

//    fun exitKioskMode() {
//        viewModelScope.launch {
//            repository.setKioskModeActive(false)
//        }
//    }
}