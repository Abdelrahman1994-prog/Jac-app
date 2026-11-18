package com.jacdrive.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.jacdrive.core.database.ALL_MIGRATIONS
import com.jacdrive.core.database.DiagnosticDao
import com.jacdrive.core.database.JacDriveDatabase
import com.jacdrive.core.database.VehicleProfileDao
import com.jacdrive.core.settings.DataStoreSettingsRepository
import com.jacdrive.core.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Application-lifetime scope for work that must outlive a screen (polling, recording). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("jacdrive_settings")

@Module
@InstallIn(SingletonComponent::class)
object CoreProvidesModule {
    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): JacDriveDatabase =
        Room.databaseBuilder(context, JacDriveDatabase::class.java, JacDriveDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun vehicleProfileDao(db: JacDriveDatabase): VehicleProfileDao = db.vehicleProfileDao()

    @Provides
    fun diagnosticDao(db: JacDriveDatabase): DiagnosticDao = db.diagnosticDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreBindsModule {
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
