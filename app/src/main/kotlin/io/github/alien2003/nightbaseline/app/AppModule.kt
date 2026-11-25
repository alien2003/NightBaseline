package io.github.alien2003.nightbaseline.app

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App-level bindings: the platform status the screens observe. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    /** Package manager and notifications. */
    @Binds
    abstract fun bindPlatform(implementation: AndroidAppPlatform): AppPlatform

    /** Health Connect, source and notification status. */
    @Binds
    abstract fun bindAccessMonitor(implementation: DefaultAccessMonitor): AccessMonitor

    /** Whether a night check runs. */
    @Binds
    abstract fun bindCheckState(implementation: WorkCheckState): CheckState
}
