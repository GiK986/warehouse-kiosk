package com.warehouse.kiosk.domain.usecase

import com.warehouse.kiosk.data.repository.KioskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case за обновяване на списъка с инсталирани приложения.
 * Извиква GetInstalledAppsUseCase и добавя само НОВИ приложения в базата данни.
 * НЕ презаписва съществуващите приложения, за да запази техните isEnabled настройки.
 */
class RefreshInstalledAppsUseCase @Inject constructor(
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val repository: KioskRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        // Вземаме всички системни приложения
        val systemApps = getInstalledAppsUseCase()

        // Вземаме текущите приложения от базата данни
        val dbApps = repository.getAllApps().first()

        // Филтрираме само НОВИТЕ приложения (които не са в DB)
        val newApps = systemApps.filter { sysApp ->
            dbApps.none { dbApp -> dbApp.packageName == sysApp.packageName }
        }

        // Добавяме само новите приложения
        if (newApps.isNotEmpty()) {
            repository.insertAllApps(newApps)
        }
    }
}