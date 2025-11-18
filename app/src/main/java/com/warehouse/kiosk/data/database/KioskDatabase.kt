package com.warehouse.kiosk.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KioskDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        const val DATABASE_NAME = "kiosk_database"
    }
}