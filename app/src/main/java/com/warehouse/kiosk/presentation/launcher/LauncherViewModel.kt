package com.warehouse.kiosk.presentation.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.manager.DeviceOwnerManager
import com.warehouse.kiosk.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: KioskRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val deviceOwnerManager: DeviceOwnerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val enabledApps = repository.getEnabledApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    init {
        loadAppsIntoDatabase()
    }

    private fun loadAppsIntoDatabase() {
        viewModelScope.launch {
            val appCount = repository.getAllApps().first().size
            if (appCount == 0) {
                val installedApps = getInstalledAppsUseCase()
                repository.insertAllApps(installedApps)
            }
        }
    }

    fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    // Password Dialog State
    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    fun showPasswordDialog() {
        _showPasswordDialog.value = true
    }

    fun hidePasswordDialog() {
        _showPasswordDialog.value = false
    }

    // Edit Staff Name Dialog State
    private val _showEditStaffDialog = MutableStateFlow(false)
    val showEditStaffDialog: StateFlow<Boolean> = _showEditStaffDialog.asStateFlow()

    fun showEditStaffDialog() {
        _showEditStaffDialog.value = true
    }

    fun hideEditStaffDialog() {
        _showEditStaffDialog.value = false
    }

    // Update Staff Name and Lock Screen Info
    fun updateStaffName(newName: String) {
        viewModelScope.launch {
            repository.setStaffName(newName)
            try {
                deviceOwnerManager.setLockScreenInfo(newName)
            } catch (e: SecurityException) {
                // Log if not Device Owner, but still save to preferences
                android.util.Log.w("LauncherViewModel", "Cannot set lock screen info: ${e.message}")
            }
        }
    }

}