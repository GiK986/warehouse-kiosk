package com.warehouse.kiosk.presentation.app_selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.database.AppEntity
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val repository: KioskRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _appListState = MutableStateFlow<List<AppEntity>>(emptyList())
    val appListState = _appListState.asStateFlow()

    private var originalAppList: List<AppEntity> = emptyList()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges = _hasUnsavedChanges.asStateFlow()

    init {
        repository.getAllApps()
            .onEach { apps ->
                if (originalAppList.isEmpty()) {
                    originalAppList = apps
                }
                _appListState.value = apps
                _hasUnsavedChanges.value = false // Reset on initial load
            }
            .launchIn(viewModelScope)
    }

    fun onAppEnableChanged(app: AppEntity, isEnabled: Boolean) {
        _appListState.update { currentList ->
            currentList.map {
                if (it.id == app.id) {
                    it.copy(isEnabled = isEnabled)
                } else {
                    it
                }
            }
        }
        _hasUnsavedChanges.value = _appListState.value != originalAppList
    }

    fun saveChanges() {
        viewModelScope.launch {
            repository.insertAllApps(_appListState.value)
            originalAppList = _appListState.value // Update the original list
            _hasUnsavedChanges.value = false
        }
    }

    suspend fun refreshAppList() {
        val systemApps = getInstalledAppsUseCase()
        val dbApps = repository.getAllApps().first() // Get the freshest list from DB

        val newApps = systemApps.filter { sysApp ->
            dbApps.none { dbApp -> dbApp.packageName == sysApp.packageName }
        }

        if (newApps.isNotEmpty()) {
            repository.insertAllApps(newApps)
        }
        // After refresh, the list in the DB is the new original state
        originalAppList = repository.getAllApps().first()
        _appListState.value = originalAppList
        _hasUnsavedChanges.value = false
    }
}