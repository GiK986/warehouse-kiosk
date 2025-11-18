package com.warehouse.kiosk.presentation.auto_start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoStartViewModel @Inject constructor(
    private val repository: KioskRepository
) : ViewModel() {

    // Get only the enabled apps to show in the list
    val enabledApps = repository.getEnabledApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Get the currently selected auto-start app
    val selectedAutoStartApp = repository.autoStartAppPackage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun onAutoStartAppSelected(packageName: String?) {
        viewModelScope.launch {
            repository.setAutoStartAppPackage(packageName)
        }
    }
}