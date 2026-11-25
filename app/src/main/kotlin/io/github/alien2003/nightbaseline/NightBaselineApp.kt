package io.github.alien2003.nightbaseline

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import io.github.alien2003.nightbaseline.work.notify.Channels
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The application: the Hilt component root and WorkManager's configuration ([HiltWorkerFactory], so the night worker
 * gets its dependencies; the default initializer is removed in the manifest). On start it creates the notification
 * channels and, once onboarding is done, makes sure the daily chain is scheduled (KEEP: an existing check stays).
 */
@HiltAndroidApp
class NightBaselineApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var scheduler: NightScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Channels.ensure(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (settings.settings.first().onboardingDone) scheduler.ensureScheduled()
        }
    }
}
