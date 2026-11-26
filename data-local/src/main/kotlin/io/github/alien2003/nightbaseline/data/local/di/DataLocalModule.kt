package io.github.alien2003.nightbaseline.data.local.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RoomNightRepository
import io.github.alien2003.nightbaseline.data.local.RoomRunLog
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.db.NightBaselineDatabase
import io.github.alien2003.nightbaseline.data.local.db.NightDao
import io.github.alien2003.nightbaseline.data.local.db.RunLogDao
import io.github.alien2003.nightbaseline.data.local.settings.DataStoreSettingsRepository
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the local data layer: the Room database and its DAOs, the settings DataStore, the wall [Clock] (UTC) and
 * the [ZoneSource]. Other modules inject these and must not provide their own [Clock].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataLocalModule {
    /** The app database (no destructive fallback: a schema change needs a migration). */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): NightBaselineDatabase =
        Room
            .databaseBuilder(context, NightBaselineDatabase::class.java, NightBaselineDatabase.FILE_NAME)
            .addMigrations(NightBaselineDatabase.MIGRATION_1_2)
            .build()

    /** DAO of `nights`. */
    @Provides
    fun provideNightDao(database: NightBaselineDatabase): NightDao = database.nightDao()

    /** DAO of `run_log`. */
    @Provides
    fun provideRunLogDao(database: NightBaselineDatabase): RunLogDao = database.runLogDao()

    /** The settings DataStore, writing on the IO dispatcher for the lifetime of the process. */
    @Suppress("InjectDispatcher") // This module is the injection point; DataStore needs its own long-lived scope.
    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            context.preferencesDataStoreFile(DataStoreSettingsRepository.FILE_NAME)
        }

    /** Wall clock in UTC; everything that needs "now" injects it so tests can fix time. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    /** The device zone, read on every use. */
    @Provides
    fun provideZoneSource(): ZoneSource = ZoneSource.SYSTEM
}

/** Interface bindings of the local data layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataLocalBindingsModule {
    /** Room-backed nights. */
    @Binds
    abstract fun bindNightRepository(implementation: RoomNightRepository): NightRepository

    /** Room-backed run log. */
    @Binds
    abstract fun bindRunLog(implementation: RoomRunLog): RunLog

    /** DataStore-backed settings. */
    @Binds
    abstract fun bindSettingsRepository(implementation: DataStoreSettingsRepository): SettingsRepository
}
