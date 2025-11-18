package com.warehouse.kiosk.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    @Query("SELECT * FROM kiosk_apps ORDER BY app_name ASC")
    fun getAllApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM kiosk_apps WHERE is_enabled = 1 ORDER BY app_name ASC")
    fun getEnabledApps(): Flow<List<AppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppEntity>)

    @Update
    suspend fun updateApp(app: AppEntity)

    @Query("SELECT * FROM kiosk_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun getAppByPackageName(packageName: String): AppEntity?

    @Query("DELETE FROM kiosk_apps")
    suspend fun clearAll()
}