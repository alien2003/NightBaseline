package io.github.alien2003.nightbaseline.work.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.alien2003.nightbaseline.work.notify.AndroidNightNotifier
import io.github.alien2003.nightbaseline.work.notify.AndroidProblemNotifier
import io.github.alien2003.nightbaseline.work.notify.NightNotifier
import io.github.alien2003.nightbaseline.work.notify.ProblemNotifier
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import io.github.alien2003.nightbaseline.work.schedule.WorkManagerNightScheduler

/**
 * Bindings of the work layer. The app's `Application` implements `androidx.work.Configuration.Provider` with the
 * injected `HiltWorkerFactory` (and removes WorkManager's default initializer) so that [NightWorker] can be built.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    /** Android notifications. */
    @Binds
    abstract fun bindNotifier(implementation: AndroidNightNotifier): NightNotifier

    /** Android data-access problem notifications. */
    @Binds
    abstract fun bindProblemNotifier(implementation: AndroidProblemNotifier): ProblemNotifier

    /** WorkManager scheduling. */
    @Binds
    abstract fun bindScheduler(implementation: WorkManagerNightScheduler): NightScheduler
}
