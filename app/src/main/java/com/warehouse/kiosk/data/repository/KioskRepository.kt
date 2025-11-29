package com.warehouse.kiosk.data.repository

import com.warehouse.kiosk.data.database.AppDao
import com.warehouse.kiosk.data.database.AppEntity
import com.warehouse.kiosk.data.preferences.KioskPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KioskRepository @Inject constructor(
    private val appDao: AppDao,
    private val preferences: KioskPreferences
) {

    // -- App Data --

    fun getAllApps(): Flow<List<AppEntity>> = appDao.getAllApps()

    fun getEnabledApps(): Flow<List<AppEntity>> = appDao.getEnabledApps()

    suspend fun updateApp(app: AppEntity) = appDao.updateApp(app)

    suspend fun insertAllApps(apps: List<AppEntity>) = appDao.insertAll(apps)

    // -- Preferences Data --

    val isKioskModeActive: Flow<Boolean> = preferences.isKioskModeActive

    suspend fun setKioskModeActive(isActive: Boolean) = preferences.setKioskModeActive(isActive)

    val isInitialSetupCompleted: Flow<Boolean> = preferences.isInitialSetupCompleted

    suspend fun setInitialSetupCompleted(completed: Boolean) = preferences.setInitialSetupCompleted(completed)

    val passwordHash: Flow<String?> = preferences.passwordHash

    suspend fun setPasswordHash(hash: String) = preferences.setPasswordHash(hash)

    val autoStartAppPackage: Flow<String?> = preferences.autoStartAppPackage

    suspend fun setAutoStartAppPackage(packageName: String?) = preferences.setAutoStartAppPackage(packageName)

    val staffName: Flow<String> = preferences.staffName

    suspend fun setStaffName(name: String) = preferences.setStaffName(name)

    val locationName: Flow<String> = preferences.locationName

    suspend fun setLocationName(location: String) = preferences.setLocationName(location)
}