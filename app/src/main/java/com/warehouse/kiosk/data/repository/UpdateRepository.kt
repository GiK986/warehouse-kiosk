package com.warehouse.kiosk.data.repository

import android.util.Log
import com.warehouse.kiosk.BuildConfig
import com.warehouse.kiosk.data.model.AppUpdateInfo
import com.warehouse.kiosk.data.model.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for checking app updates from GitHub Releases
 */
@Singleton
class UpdateRepository @Inject constructor() {

    companion object {
        private const val TAG = "UpdateRepository"
        private const val GITHUB_API_URL = "https://api.github.com/repos/GiK986/warehouse-kiosk/releases/latest"
        private const val CONNECT_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 10000 // 10 seconds
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Check for available app updates from GitHub Releases
     */
    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates from GitHub...")
            val release = fetchLatestRelease()

            val currentVersion = BuildConfig.VERSION_NAME
            val latestVersion = release.tagName.removePrefix("v")

            // Find APK asset
            val apkAsset = release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            }

            val isUpdateAvailable = compareVersions(currentVersion, latestVersion) < 0

            Log.i(TAG, "Current: $currentVersion, Latest: $latestVersion, Update available: $isUpdateAvailable")

            val updateInfo = AppUpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                isUpdateAvailable = isUpdateAvailable,
                releaseNotes = release.body,
                downloadUrl = apkAsset?.downloadUrl,
                apkSize = apkAsset?.size
            )

            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch latest release from GitHub API
     */
    private suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("GitHub API returned $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<GitHubRelease>(response)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Compare two version strings
     * @return negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0

            if (part1 != part2) {
                return part1.compareTo(part2)
            }
        }

        return 0
    }
}