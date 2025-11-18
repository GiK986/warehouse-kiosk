package com.warehouse.kiosk.di

import android.content.Context
import androidx.room.Room
import com.warehouse.kiosk.data.database.AppDao
import com.warehouse.kiosk.data.database.KioskDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKioskDatabase(@ApplicationContext context: Context): KioskDatabase {
        return Room.databaseBuilder(
            context,
            KioskDatabase::class.java,
            KioskDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideAppDao(database: KioskDatabase): AppDao {
        return database.appDao()
    }
}