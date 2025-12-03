package com.warehouse.kiosk.presentation.wms_install

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.model.SavedApkUrl
import com.warehouse.kiosk.domain.usecase.DownloadAndInstallApkUseCase
import com.warehouse.kiosk.domain.usecase.RefreshInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WmsInstallViewModel @Inject constructor(
    private val downloadAndInstallApkUseCase: DownloadAndInstallApkUseCase,
    private val refreshInstalledAppsUseCase: RefreshInstalledAppsUseCase,
    private val repository: KioskRepository
) : ViewModel() {

    private val _installState = MutableStateFlow<DownloadAndInstallApkUseCase.InstallState?>(null)
    val installState: StateFlow<DownloadAndInstallApkUseCase.InstallState?> = _installState.asStateFlow()

    private val _apkUrl = MutableStateFlow("")
    val apkUrl: StateFlow<String> = _apkUrl.asStateFlow()

    val saveUrlEnabled: StateFlow<Boolean> = repository.isSaveUrlEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val savedApkUrls: StateFlow<List<SavedApkUrl>> = repository.savedApkUrls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateApkUrl(url: String) {
        _apkUrl.value = url
    }

    fun toggleSaveUrl(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSaveUrlEnabled(enabled)
        }
    }

    fun selectSavedUrl(savedUrl: SavedApkUrl) {
        _apkUrl.value = savedUrl.url
    }

    fun removeSavedUrl(packageName: String) {
        viewModelScope.launch {
            repository.removeSavedApkUrl(packageName)
        }
    }

    fun installWmsApp() {
        if (_apkUrl.value.isBlank()) {
            _installState.value = DownloadAndInstallApkUseCase.InstallState.Error("Моля въведете APK URL")
            return
        }

        viewModelScope.launch {
            downloadAndInstallApkUseCase(_apkUrl.value).collect { state ->
                _installState.value = state

                // При успешна инсталация
                if (state is DownloadAndInstallApkUseCase.InstallState.Success) {
                    // Опитай се да извлечеш package name от URL или от инсталираното приложение
                    val packageName = state.packageName

                    // Запази URL-а ако е включен toggle-а
                    if (saveUrlEnabled.value && packageName != null) {
                        val displayName = packageName.substringAfterLast('.')
                        repository.addSavedApkUrl(
                            SavedApkUrl(
                                url = _apkUrl.value,
                                packageName = packageName,
                                displayName = displayName
                            )
                        )
                    }

                    // Обнови списъка с приложения
                    refreshInstalledAppsUseCase()

                    // Изчисти URL полето
                    _apkUrl.value = ""
                }
            }
        }
    }

    fun clearState() {
        _installState.value = null
    }
}