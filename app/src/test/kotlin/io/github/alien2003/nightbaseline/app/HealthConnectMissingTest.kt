package io.github.alien2003.nightbaseline.app

import androidx.lifecycle.SavedStateHandle
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.home.HomeUi
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.model.ManualCheckUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.onboarding.HealthConnectStatus
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.SourceRecheck
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Health Connect missing or needing an update, and a source list that stays empty: Settings says so instead of "Not
 * read yet" or "No sleep data", and every "Check again" that changes nothing gives visible feedback (onboarding source
 * step, Home's update card, Settings' source page).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectMissingTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun <T> StateFlow<T>.ready(until: (T) -> Boolean): T = withTimeout(TIMEOUT_MS) { first(until) }

    @Test
    fun `settings know Health Connect needs an update or is missing`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(healthConnect = HcAvailability.UPDATE_REQUIRED))
            val vm = SettingsViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            assertEquals(HealthConnectStatus.NeedsUpdate, vm.ui.ready { it != null }!!.healthConnect)
            app.access.set(TestApp.allowed(healthConnect = HcAvailability.NOT_SUPPORTED))
            assertEquals(
                HealthConnectStatus.Unavailable,
                vm.ui.ready { it?.healthConnect == HealthConnectStatus.Unavailable }!!.healthConnect,
            )
        }

    @Test
    fun `check again on an empty source page re-reads the sources and says nothing was found`() =
        runTest {
            val app = TestApp(nights = 0)
            // No app wrote sleep and the default source is not installed: nothing to list.
            app.platform.labels = emptyMap()
            val vm = SettingsViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            vm.openPage(SettingsPage.SourcePicker)
            val refreshes = app.access.refreshes
            vm.checkSources()
            val state = vm.ui.ready { it?.sourcesRechecked == true }!!
            assertTrue(state.sources.isEmpty())
            assertTrue(!state.checkingSources)
            assertTrue(app.access.refreshes > refreshes, "Health Connect is read again")
        }

    @Test
    fun `onboarding check again with Health Connect still missing says so`() =
        runTest {
            val app = TestApp(nights = 0, settings = AppSettings())
            app.gateway.status = HcAvailability.UPDATE_REQUIRED
            val vm = OnboardingViewModel(SavedStateHandle(), app.data)
            vm.next()
            vm.next()
            assertEquals(OnboardingStep.Source, vm.ui.value.step)
            // The first check of the step is not a "Check again": no note yet.
            vm.checkAgain()
            assertEquals(HealthConnectStatus.NeedsUpdate, vm.ui.value.healthConnect)
            vm.checkAgain()
            assertEquals(SourceRecheck.HealthConnectStillMissing, vm.ui.value.recheck)
            // Once it works the note goes.
            app.gateway.status = HcAvailability.AVAILABLE
            vm.checkAgain()
            assertNull(vm.ui.value.recheck)
        }

    @Test
    fun `home check again on the update card says Health Connect is still missing`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(healthConnect = HcAvailability.UPDATE_REQUIRED))
            val vm = HomeViewModel(app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val before = vm.ui.ready { it is HomeUi.Ready } as HomeUi.Ready
            assertEquals(NightState.HealthConnectUpdate, before.state.night.state)
            assertEquals(ManualCheckUi.None, before.state.check)
            vm.recheckHealthConnect()
            val after = vm.ui.ready { (it as? HomeUi.Ready)?.state?.check == ManualCheckUi.HealthConnectStillMissing }
            assertTrue(after is HomeUi.Ready)
            // Installed or updated: the line goes away.
            app.access.set(TestApp.allowed())
            vm.ui.ready { (it as? HomeUi.Ready)?.state?.check == ManualCheckUi.None }
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
