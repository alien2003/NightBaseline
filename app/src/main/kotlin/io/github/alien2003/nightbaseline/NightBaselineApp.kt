package io.github.alien2003.nightbaseline

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Lazy
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
 * Both run on a background thread, and the injected objects are built only when first used, so the main thread goes
 * straight to the first frame (docs/development.md, "Performance"). Every notifier creates the channels itself
 * before it posts, so nothing depends on the start-up call having finished.
 */
@HiltAndroidApp
class NightBaselineApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var lazyWorkerFactory: Lazy<HiltWorkerFactory>

    @Inject lateinit var lazySettings: Lazy<SettingsRepository>

    @Inject lateinit var lazyScheduler: Lazy<NightScheduler>

    /** The app's settings repository (the on-device suite reads it). */
    val settings: SettingsRepository get() = lazySettings.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(lazyWorkerFactory.get()).build()

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            Channels.ensure(this@NightBaselineApp)
            if (settings.settings.first().onboardingDone) lazyScheduler.get().ensureScheduled()
        }
    }
}
