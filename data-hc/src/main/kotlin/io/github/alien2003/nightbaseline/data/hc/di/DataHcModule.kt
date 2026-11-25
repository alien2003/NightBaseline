package io.github.alien2003.nightbaseline.data.hc.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.alien2003.nightbaseline.data.hc.HealthConnectClientGateway
import io.github.alien2003.nightbaseline.data.hc.HealthConnectGateway
import io.github.alien2003.nightbaseline.data.hc.HealthConnectReader
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.hc.ProcessImportanceForegroundState
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import javax.inject.Singleton

/** Provides the Health Connect reader (real client, process-importance foreground check). */
@Module
@InstallIn(SingletonComponent::class)
object DataHcModule {
    /** The reader the app uses. */
    @Provides
    @Singleton
    fun provideHealthDataReader(
        gateway: HealthConnectGateway,
        zone: ZoneSource,
        @ApplicationContext context: Context,
    ): HealthDataReader = HealthConnectReader(gateway, ProcessImportanceForegroundState, zone, ownPackage = context.packageName)
}

/** Interface bindings of the Health Connect layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataHcBindingsModule {
    /** The real gateway. */
    @Binds
    abstract fun bindGateway(implementation: HealthConnectClientGateway): HealthConnectGateway
}
