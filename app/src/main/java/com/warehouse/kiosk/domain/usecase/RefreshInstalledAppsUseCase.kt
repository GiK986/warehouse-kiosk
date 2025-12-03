package com.warehouse.kiosk.domain.usecase

import com.warehouse.kiosk.data.repository.KioskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case за обновяване на списъка с инсталирани приложения.
 * Извиква GetInstalledAppsUseCase и актуализира базата данни.
 */
class RefreshInstalledAppsUseCase @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val repository: KioskRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val installedApps = getInstalledAppsUseCase()
        repository.insertAllApps(installedApps)
    }
}