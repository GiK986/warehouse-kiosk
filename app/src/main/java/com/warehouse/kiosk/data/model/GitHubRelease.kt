package com.warehouse.kiosk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release API response model
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String, // e.g. "v1.0.9"

    @SerialName("name")
    val name: String, // e.g. "v1.0.9 - Device Info Management"

    @SerialName("body")
    val body: String?, // Release notes (markdown)

    @SerialName("published_at")
    val publishedAt: String, // ISO 8601 timestamp

    @SerialName("assets")
    val assets: List<GitHubAsset> = emptyList(),

    @SerialName("prerelease")
    val prerelease: Boolean = false,

    @SerialName("draft")
    val draft: Boolean = false
)

@Serializable
data class GitHubAsset(
    @SerialName("name")
    val name: String, // e.g. "warehouse-kiosk-release.apk"

    @SerialName("browser_download_url")
    val downloadUrl: String,

    @SerialName("size")
    val size: Long,

    @SerialName("content_type")
    val contentType: String
)

/**
 * App update information extracted from GitHub release
 */
data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseNotes: String?,
    val downloadUrl: String?,
    val apkSize: Long?
)