package com.warehouse.kiosk.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.usecase.SetWallpaperUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: KioskRepository,
    private val setWallpaperUseCase: SetWallpaperUseCase
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

    private val _wallpaperState = MutableStateFlow<WallpaperState>(WallpaperState.Idle)
    val wallpaperState: StateFlow<WallpaperState> = _wallpaperState.asStateFlow()

    fun setWallpaper() {
        viewModelScope.launch {
            _wallpaperState.value = WallpaperState.Loading
            setWallpaperUseCase().fold(
                onSuccess = {
                    _wallpaperState.value = WallpaperState.Success
                },
                onFailure = { error ->
                    _wallpaperState.value = WallpaperState.Error(error.message ?: "Неизвестна грешка")
                }
            )
        }
    }

    fun resetWallpaperState() {
        _wallpaperState.value = WallpaperState.Idle
    }

//    fun exitKioskMode() {
//        viewModelScope.launch {
//            repository.setKioskModeActive(false)
//        }
//    }
}

sealed class WallpaperState {
    data object Idle : WallpaperState()
    data object Loading : WallpaperState()
    data object Success : WallpaperState()
    data class Error(val message: String) : WallpaperState()
}