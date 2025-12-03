package com.warehouse.kiosk.domain.model

import kotlinx.serialization.Serializable

/**
 * Представлява запазен APK URL с име на пакета за повторно използване.
 */
@Serializable
data class SavedApkUrl(
    val url: String,
    val packageName: String,
    val displayName: String, // Удобно име за показване в UI
    val timestamp: Long = System.currentTimeMillis()
)