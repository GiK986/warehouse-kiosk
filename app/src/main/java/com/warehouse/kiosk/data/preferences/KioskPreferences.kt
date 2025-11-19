package com.warehouse.kiosk.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KioskPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    // Define keys
    private object PrefKeys {
        val KIOSK_MODE_ACTIVE = booleanPreferencesKey("kiosk_mode_active")
        val INITIAL_SETUP_COMPLETED = booleanPreferencesKey("initial_setup_completed")
        val PASSWORD_HASH = stringPreferencesKey("password_hash")
        val AUTO_START_APP_PACKAGE = stringPreferencesKey("auto_start_app_package")
    }

    // Kiosk Mode Status
    val isKioskModeActive: Flow<Boolean> = dataStore.data.map {
        it[PrefKeys.KIOSK_MODE_ACTIVE] ?: false
    }

    suspend fun setKioskModeActive(isActive: Boolean) {
        dataStore.edit {
            it[PrefKeys.KIOSK_MODE_ACTIVE] = isActive
        }
    }

    // Initial Setup Status
    val isInitialSetupCompleted: Flow<Boolean> = dataStore.data.map {
        it[PrefKeys.INITIAL_SETUP_COMPLETED] ?: false
    }

    suspend fun setInitialSetupCompleted(completed: Boolean) {
        dataStore.edit {
            it[PrefKeys.INITIAL_SETUP_COMPLETED] = completed
        }
    }

    // Password Hash
    val passwordHash: Flow<String?> = dataStore.data.map {
        it[PrefKeys.PASSWORD_HASH]
    }

    suspend fun setPasswordHash(hash: String) {
        dataStore.edit {
            it[PrefKeys.PASSWORD_HASH] = hash
        }
    }

    // Auto-start App
    val autoStartAppPackage: Flow<String?> = dataStore.data.map {
        it[PrefKeys.AUTO_START_APP_PACKAGE]
    }

    suspend fun setAutoStartAppPackage(packageName: String?) {
        dataStore.edit {
            if (packageName == null) {
                it.remove(PrefKeys.AUTO_START_APP_PACKAGE)
            } else {
                it[PrefKeys.AUTO_START_APP_PACKAGE] = packageName
            }
        }
    }

    companion object {
        const val PREFERENCES_FILE_NAME = "kiosk_datastore"
    }
}