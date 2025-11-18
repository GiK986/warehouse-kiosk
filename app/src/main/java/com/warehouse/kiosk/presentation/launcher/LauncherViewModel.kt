package com.warehouse.kiosk.presentation.launcher

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import com.warehouse.kiosk.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
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
}