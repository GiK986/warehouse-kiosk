package com.warehouse.kiosk.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kiosk_apps",
    indices = [Index(value = ["package_name"], unique = true)]
)
data class AppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)