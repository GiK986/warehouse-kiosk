package com.warehouse.kiosk.presentation.app_updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.model.AppUpdateInfo
import com.warehouse.kiosk.data.repository.ApkRepository
import com.warehouse.kiosk.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdatesViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val apkRepository: ApkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /**
     * Check for available updates from GitHub
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking

            val result = updateRepository.checkForUpdates()

            _uiState.value = result.fold(
                onSuccess = { updateInfo ->
                    if (updateInfo.isUpdateAvailable) {
                        UpdateUiState.UpdateAvailable(updateInfo)
                    } else {
                        UpdateUiState.UpToDate(updateInfo.currentVersion)
                    }
                },
                onFailure = { error ->
                    UpdateUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Download and install the update
     */
    fun downloadAndInstall(downloadUrl: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UpdateUiState.Downloading(0)

                // Download APK
                val apkFile = apkRepository.downloadApk(downloadUrl)

                _uiState.value = UpdateUiState.Installing

                // Install APK
                val success = apkRepository.installApk(apkFile)

                // Cleanup
                apkRepository.cleanupApkFile(apkFile)

                _uiState.value = if (success) {
                    UpdateUiState.InstallSuccess
                } else {
                    UpdateUiState.Error("Installation failed")
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Download/Install failed")
            }
        }
    }

    /**
     * Reset state to idle
     */
    fun resetState() {
        _uiState.value = UpdateUiState.Idle
    }
}

/**
 * UI State for App Updates screen
 */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data object Installing : UpdateUiState()
    data object InstallSuccess : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}