package com.warehouse.kiosk.presentation.wms_install

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.domain.usecase.DownloadAndInstallApkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WmsInstallViewModel @Inject constructor(
    private val downloadAndInstallApkUseCase: DownloadAndInstallApkUseCase
) : ViewModel() {

    private val _installState = MutableStateFlow<DownloadAndInstallApkUseCase.InstallState?>(null)
    val installState: StateFlow<DownloadAndInstallApkUseCase.InstallState?> = _installState.asStateFlow()

    private val _apkUrl = MutableStateFlow("")
    val apkUrl: StateFlow<String> = _apkUrl.asStateFlow()

    fun updateApkUrl(url: String) {
        _apkUrl.value = url
    }

    fun installWmsApp() {
        if (_apkUrl.value.isBlank()) {
            _installState.value = DownloadAndInstallApkUseCase.InstallState.Error("Моля въведете APK URL")
            return
        }

        viewModelScope.launch {
            downloadAndInstallApkUseCase(_apkUrl.value).collect { state ->
                _installState.value = state
            }
        }
    }

    fun clearState() {
        _installState.value = null
    }
}