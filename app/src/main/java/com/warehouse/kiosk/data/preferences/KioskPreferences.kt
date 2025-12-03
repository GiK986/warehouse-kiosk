package com.warehouse.kiosk.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.warehouse.kiosk.domain.model.SavedApkUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KioskPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Define keys
    private object PrefKeys {
        val KIOSK_MODE_ACTIVE = booleanPreferencesKey("kiosk_mode_active")
        val INITIAL_SETUP_COMPLETED = booleanPreferencesKey("initial_setup_completed")
        val PASSWORD_HASH = stringPreferencesKey("password_hash")
        val AUTO_START_APP_PACKAGE = stringPreferencesKey("auto_start_app_package")
        val STAFF_NAME = stringPreferencesKey("staff_name")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val SAVED_APK_URLS = stringSetPreferencesKey("saved_apk_urls")
        val SAVE_URL_ENABLED = booleanPreferencesKey("save_url_enabled")
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

    // Staff Name
    val staffName: Flow<String> = dataStore.data.map {
        it[PrefKeys.STAFF_NAME] ?: ""
    }

    suspend fun setStaffName(name: String) {
        dataStore.edit {
            it[PrefKeys.STAFF_NAME] = name
        }
    }

    // Location Name
    val locationName: Flow<String> = dataStore.data.map {
        it[PrefKeys.LOCATION_NAME] ?: ""
    }

    suspend fun setLocationName(location: String) {
        dataStore.edit {
            it[PrefKeys.LOCATION_NAME] = location
        }
    }

    // Save URL Toggle
    val isSaveUrlEnabled: Flow<Boolean> = dataStore.data.map {
        it[PrefKeys.SAVE_URL_ENABLED] ?: false
    }

    suspend fun setSaveUrlEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PrefKeys.SAVE_URL_ENABLED] = enabled
        }
    }

    // Saved APK URLs
    val savedApkUrls: Flow<List<SavedApkUrl>> = dataStore.data.map { preferences ->
        val savedSet = preferences[PrefKeys.SAVED_APK_URLS] ?: emptySet()
        savedSet.mapNotNull { jsonString ->
            try {
                json.decodeFromString<SavedApkUrl>(jsonString)
            } catch (e: Exception) {
                null // Ignore invalid entries
            }
        }.sortedByDescending { it.timestamp }
    }

    suspend fun addSavedApkUrl(savedUrl: SavedApkUrl) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PrefKeys.SAVED_APK_URLS]?.toMutableSet() ?: mutableSetOf()

            // Remove existing entry with same package name if exists
            val existingEntry = currentSet.find { jsonString ->
                try {
                    json.decodeFromString<SavedApkUrl>(jsonString).packageName == savedUrl.packageName
                } catch (e: Exception) {
                    false
                }
            }
            existingEntry?.let { currentSet.remove(it) }

            // Add new entry
            currentSet.add(json.encodeToString(savedUrl))
            preferences[PrefKeys.SAVED_APK_URLS] = currentSet
        }
    }

    suspend fun removeSavedApkUrl(packageName: String) {
        dataStore.edit { preferences ->
            val currentSet = preferences[PrefKeys.SAVED_APK_URLS]?.toMutableSet() ?: return@edit

            val entryToRemove = currentSet.find { jsonString ->
                try {
                    json.decodeFromString<SavedApkUrl>(jsonString).packageName == packageName
                } catch (e: Exception) {
                    false
                }
            }

            entryToRemove?.let {
                currentSet.remove(it)
                preferences[PrefKeys.SAVED_APK_URLS] = currentSet
            }
        }
    }

    companion object {
        const val PREFERENCES_FILE_NAME = "kiosk_datastore"
    }
}