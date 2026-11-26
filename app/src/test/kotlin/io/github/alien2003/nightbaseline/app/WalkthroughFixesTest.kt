package io.github.alien2003.nightbaseline.app

import androidx.lifecycle.SavedStateHandle
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricEvaluation
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.MetricValue
import io.github.alien2003.nightbaseline.algo.MissingReason
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HcSession
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.home.HomeUi
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.model.ManualCheckUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUiMapper
import io.github.alien2003.nightbaseline.ui.night.NightDetailViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.PermissionStatus
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The logic fixes of the emulator walkthrough: learning progress that counts the shown night on every surface,
 * read-path banners never stacked on a data-access state card, no "doesn't send" for a night read without access,
 * the visible state of "Check now", "Last read" after a time zone change, and the onboarding source and permission
 * steps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalkthroughFixesTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun <T> StateFlow<T>.ready(until: (T) -> Boolean): T = withTimeout(TIMEOUT_MS) { first(until) }

    private suspend fun TestScope.home(app: TestApp): Pair<HomeViewModel, HomeUi> {
        val vm = HomeViewModel(app.data)
        backgroundScope.launch { vm.ui.collect {} }
        return vm to vm.ui.ready { it !is HomeUi.Pending }
    }

    @Test
    fun `learning progress counts every stored valid night, the newest included, on Home and Night detail`() =
        runTest {
            val records = TestApp.records(3)
            val valid = records.count { it.summary.valid }
            val app = TestApp(nights = 3)
            val learning = (home(app).second as HomeUi.Learning).state
            assertEquals(valid, learning.collected)
            assertEquals(valid, learning.recent.count { it.counted })
            val newest = records.last()
            val vm = NightDetailViewModel(SavedStateHandle(mapOf("date" to newest.date, "section" to "Home")), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val night = vm.ui.ready { it != null }!!.night
            assertEquals(valid, night.collectedNights)
            // Per metric: the values before this night plus its own, the same count as the night's.
            val hr = night.metrics.first { it.metric == MetricId.SLEEPING_HR }
            assertEquals(MetricState.Learning, hr.state)
            assertEquals(records.count { it.summary.valid && it.summary.values[MetricId.SLEEPING_HR]?.value != null }, hr.learnedNights)
        }

    @Test
    fun `a stored valid night is never L0`() {
        val summary = TestApp.records(1).single().summary
        val evaluation = Evaluator.evaluate(summary, emptyList(), AlgoParams())
        assertEquals(0, evaluation.historyNights)
        val night = NightUiMapper.night(summary, evaluation)
        assertEquals(NightState.Learning, night.state)
        assertEquals(1, night.collectedNights)
    }

    @Test
    fun `data-access states drop the read-path banners`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(granted = HcPermissions.ALL - HcPermissions.READ_SLEEP))
            app.at(SampleData.LAST_WAKE.plusDays(1).atTime(9, 0))
            val state = (home(app).second as HomeUi.Ready).state
            assertEquals(NightState.Permission, state.night.state)
            assertNull(state.banner)
            val failed = TestApp(access = TestApp.allowed(granted = HcPermissions.ALL - HcPermissions.READ_HEART_RATE))
            failed.runLog.log(RunLogKind.ERROR, "night check failed: test")
            failed.data.refresh()
            assertNull((home(failed).second as HomeUi.Ready).state.banner)
        }

    @Test
    fun `a night read without access shows access off then, never not recorded by the source`() {
        val record = TestApp.records().last()
        val values =
            record.summary.values + (MetricId.SPO2 to MetricValue(MetricId.SPO2, null, 0, 0.0, MissingReason.NO_ACCESS))
        val summary = record.summary.copy(values = values)
        val history = TestApp.records().map { it.summary }
        val evaluation = Evaluator.evaluate(summary, history, AlgoParams())
        assertTrue(evaluation.metrics.none { it.metric == MetricId.SPO2 })
        val night = NightUiMapper.night(summary, evaluation, history = history)
        assertEquals(MetricState.AccessOffThen, night.metrics.first { it.metric == MetricId.SPO2 }.state)
        assertTrue(night.metrics.none { it.state == MetricState.NotProvided })
        val missing = MetricEvaluation(MetricId.SPO2, null, null, null, MetricStatus.MISSING, false, MissingReason.NO_ACCESS)
        assertEquals(MetricState.AccessOffThen, NightUiMapper.metricState(missing, short = false))
    }

    @Test
    fun `check now shows a busy state, keeps the placeholder card and then says nothing new came`() =
        runTest {
            val app = TestApp()
            app.at(SampleData.LAST_WAKE.plusDays(1).atTime(19, 0))
            val (vm, ui) = home(app)
            assertEquals(NightState.NoData, (ui as HomeUi.Ready).state.night.state)
            assertEquals(ManualCheckUi.None, ui.state.check)
            app.data.checkNow()
            assertEquals(1, app.scheduler.runNow)
            assertEquals(
                ManualCheckUi.Checking,
                (
                    vm.ui.ready {
                        (it as? HomeUi.Ready)?.state?.check == ManualCheckUi.Checking
                    } as HomeUi.Ready
                ).state.check,
            )
            app.running.value = true
            // While the check runs the card stays where it is (no flash of the previous night).
            assertEquals(NightState.NoData, (vm.ui.value as HomeUi.Ready).state.night.state)
            app.running.value = false
            val done = (vm.ui.ready { (it as? HomeUi.Ready)?.state?.check is ManualCheckUi.NothingNew } as HomeUi.Ready).state
            assertTrue((done.check as ManualCheckUi.NothingNew).time.isNotBlank())
        }

    @Test
    fun `the manual check state machine ends without RUNNING and drops its result later`() {
        var t = ManualCheckTrack().next(1_000L, running = false, now = 1_000L)
        assertEquals(ManualCheck.Checking, t.state(1_000L))
        t = t.next(1_000L, running = false, now = 1_000L + AppData.CHECK_START_MS)
        assertEquals(ManualCheck.Done(1_000L, 1_000L + AppData.CHECK_START_MS), t.state(2_000L + AppData.CHECK_START_MS))
        assertEquals(ManualCheck.None, t.state(1_000L + AppData.CHECK_START_MS + AppData.CHECK_RESULT_MS))
        val seen = ManualCheckTrack().next(5L, running = true, now = 6L).next(5L, running = false, now = 9L)
        assertEquals(ManualCheck.Done(5L, 9L), seen.state(10L))
        assertEquals(ManualCheck.None, seen.next(null, running = false, now = 11L).state(11L))
    }

    @Test
    fun `last read follows a time zone change while the app runs`() =
        runTest {
            val app = TestApp()
            app.runLog.log(RunLogKind.EVALUATED, "night evaluated")
            val vm = SettingsViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val before = vm.ui.ready { it?.lastRead != null }!!.lastRead
            app.zoneId = ZoneId.of("America/Anchorage")
            app.platform.timeChange.tryEmit(Unit)
            val after = vm.ui.ready { it?.lastRead != before }!!.lastRead
            assertNotEquals(before, after)
        }

    @Test
    fun `onboarding asks for access before listing sources and preselects the only source after the grant`() =
        runTest {
            val app = TestApp(nights = 0, settings = AppSettings())
            app.platform.labels = mapOf(RING to "Ring")
            app.gateway.granted.clear()
            val end = app.clock.millis() - HOUR_MS
            app.gateway.sessions += HcSession("s1", RING, end - 8 * HOUR_MS, end, 0, end)
            val vm = OnboardingViewModel(SavedStateHandle(), app.data)
            vm.next()
            vm.next()
            assertEquals(OnboardingStep.Source, vm.ui.value.step)
            assertTrue(vm.ui.value.sourcesNeedAccess)
            assertTrue(vm.needsPermissionsToList())
            app.gateway.granted += HcPermissions.ALL
            vm.onPermissionsResult(HcPermissions.ALL)
            assertEquals(RING, vm.ui.value.selectedSource)
            assertNotNull(vm.ui.value.selected)
            assertTrue(!vm.ui.value.sourcesNeedAccess)
        }

    @Test
    fun `the permission step knows which optional permissions are off`() =
        runTest {
            val app = TestApp(nights = 0, settings = AppSettings())
            val vm = OnboardingViewModel(SavedStateHandle(), app.data)
            val partial = HcPermissions.ALL - HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND - HcPermissions.READ_HEALTH_DATA_HISTORY
            app.gateway.granted.clear()
            app.gateway.granted += partial
            vm.onPermissionsResult(partial)
            assertEquals(PermissionStatus.Partial, vm.ui.value.permission)
            assertEquals(
                setOf(HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND, HcPermissions.READ_HEALTH_DATA_HISTORY),
                vm.ui.value.missingOptional,
            )
        }

    @Test
    fun `the ready step counts the newest night the first check will show, as Home does`() =
        runTest {
            val gen =
                SyntheticNights(SyntheticNights.Config(seed = 11, nights = 3, missingNightP = 0.0, shortNightP = 0.0)).generate()
            val app = TestApp(nights = 0, settings = AppSettings())
            SyntheticSource.load(app.gateway, gen)
            // Two hours after the third night: the backfill stores two, the newest is left to the first check.
            app.clock.now = Instant.ofEpochMilli(SyntheticSource.nightEndMs(gen.last()) + 2 * HOUR_MS)
            val vm = OnboardingViewModel(SavedStateHandle(), app.data)
            vm.skipNotifications()
            val ready = vm.ui.ready { it.pastNights > 0 }
            assertEquals(OnboardingStep.Ready, ready.step)
            assertEquals(2, app.nights.all.size)
            // Home says "3 of 7" for these three nights (NightUi.collectedNights counts the shown night).
            assertEquals(3, ready.pastNights)
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val HOUR_MS = 3_600_000L
        const val RING = "org.example.ring"
    }
}
