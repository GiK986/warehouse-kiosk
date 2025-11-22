package com.warehouse.kiosk.presentation.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    val enabledApps = repository.getEnabledApps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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

    // 1. Създайте частен, променлив StateFlow, който ще държи състоянието.
    private val _showPasswordDialog = MutableStateFlow(false)

    // 2. Изложете го като публичен, неизменим StateFlow, който UI-ът ще наблюдава.
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    // 3. Функция, която показва диалога (променя стойността на true).
    fun showPasswordDialog() {
        _showPasswordDialog.value = true
    }

    // 4. Функция, която скрива диалога (променя стойността на false).
    fun hidePasswordDialog() {
        _showPasswordDialog.value = false
    }

}