package io.github.alien2003.nightbaseline.app

import androidx.lifecycle.SavedStateHandle
import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.SensitivityLevel
import io.github.alien2003.nightbaseline.nav.AppRootViewModel
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.history.HistoryAccess
import io.github.alien2003.nightbaseline.ui.history.HistoryView
import io.github.alien2003.nightbaseline.ui.history.HistoryViewModel
import io.github.alien2003.nightbaseline.ui.home.HomeUi
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailViewModel
import io.github.alien2003.nightbaseline.ui.metric.MetricNotice
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.night.NightDetailViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.PermissionStatus
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SensitivityPreset
import io.github.alien2003.nightbaseline.ui.settings.SettingsViewModel
import io.github.alien2003.nightbaseline.ui.settings.SourceOption
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The ViewModels over the data-local / data-hc fakes filled with synthetic nights ([TestApp]): the key paths of
 * `states.md` 1 and 3 (results, learning, permission revoked, Health Connect unavailable or outdated, source gone,
 * banners), Night and Metric detail, History, Settings and onboarding.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun <T> StateFlow<T>.ready(until: (T) -> Boolean): T = withTimeout(TIMEOUT_MS) { first(until) }

    private suspend fun TestScope.home(app: TestApp): HomeUi {
        val vm = HomeViewModel(app.data)
        backgroundScope.launch { vm.ui.collect {} }
        return vm.ui.ready { it !is HomeUi.Pending }
    }

    @Test
    fun `newest stored night with deviations is the summary, with the strip and the series`() =
        runTest {
            val ui = home(TestApp())
            assertTrue(ui is HomeUi.Ready, "$ui")
            val state = (ui as HomeUi.Ready).state
            assertEquals(SampleData.LAST_WAKE, state.night.wakeDate)
            assertTrue(state.night.state.hasSummary, "${state.night.state}")
            assertTrue(state.night.deviating >= 1)
            assertEquals(7, state.recent.size)
            assertEquals(SampleData.LAST_WAKE, state.recent.last().wakeDate)
            val series = state.series.getValue(MetricId.SLEEPING_HR)
            assertEquals(90, series.points.size)
            assertTrue(series.points.count { it.range != null } > 30)
            assertNull(state.banner)
        }

    @Test
    fun `the first nights show the learning presentation`() =
        runTest {
            val ui = home(TestApp(nights = 4))
            assertTrue(ui is HomeUi.Learning, "$ui")
            val learning = (ui as HomeUi.Learning).state
            assertTrue(learning.night.state == NightState.Learning || learning.night.state == NightState.LearningStart)
            assertTrue(learning.collected in 1..6, "collected ${learning.collected}")
            assertEquals(4, learning.recent.size)
        }

    @Test
    fun `no stored night and no running check is L0, a running check is LOADING`() =
        runTest {
            val idle = home(TestApp(nights = 0)) as HomeUi.Learning
            assertEquals(NightState.LearningStart, idle.state.night.state)
            val app = TestApp(nights = 0)
            app.running.value = true
            val loading = home(app) as HomeUi.Ready
            assertEquals(NightState.Loading, loading.state.night.state)
        }

    @Test
    fun `permission revoked shows E-PERM above the last result`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(granted = HcPermissions.ALL - HcPermissions.READ_HEART_RATE))
            val state = (home(app) as HomeUi.Ready).state
            assertEquals(NightState.Permission, state.night.state)
            assertEquals(SampleData.LAST_WAKE, state.lastResult?.wakeDate)
            assertNotNull(state.metricsNight)
        }

    @Test
    fun `Health Connect missing or outdated wins over everything`() =
        runTest {
            val none = TestApp(access = TestApp.allowed(healthConnect = HcAvailability.NOT_SUPPORTED, granted = emptySet()))
            assertEquals(NightState.HealthConnectNone, (home(none) as HomeUi.Ready).state.night.state)
            val update = TestApp(access = TestApp.allowed(healthConnect = HcAvailability.UPDATE_REQUIRED, granted = emptySet()))
            val state = (home(update) as HomeUi.Ready).state
            assertEquals(NightState.HealthConnectUpdate, state.night.state)
            assertNotNull(state.lastResult)
        }

    @Test
    fun `an uninstalled source is E-SOURCE-GONE`() =
        runTest {
            val state = (home(TestApp(access = TestApp.allowed(sourceLabel = null))) as HomeUi.Ready).state
            assertEquals(NightState.SourceGone, state.night.state)
        }

    @Test
    fun `an optional permission off marks only that metric`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(granted = HcPermissions.ALL - HcPermissions.READ_OXYGEN_SATURATION))
            val state = (home(app) as HomeUi.Ready).state
            assertEquals(
                MetricState.AccessOff,
                state.night.metrics
                    .first { it.metric == MetricId.SPO2 }
                    .state,
            )
            assertTrue(state.night.state.hasSummary)
        }

    @Test
    fun `banners follow the priority of states md 3`() =
        runTest {
            val bg = TestApp(access = TestApp.allowed(granted = HcPermissions.ALL - HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND))
            assertEquals(BannerKind.Background, (home(bg) as HomeUi.Ready).state.banner)
            val failed = TestApp()
            failed.runLog.log(RunLogKind.ERROR, "night check failed: test")
            failed.data.refresh()
            assertEquals(BannerKind.ReadFailed, (home(failed) as HomeUi.Ready).state.banner)
            val notif = TestApp(access = TestApp.allowed(notifications = NotificationAccess.Blocked))
            assertEquals(BannerKind.NotificationsOff, (home(notif) as HomeUi.Ready).state.banner)
            val snoozed =
                TestApp(
                    settings = AppSettings(onboardingDone = true, notificationBannerDismissedAtMs = TestApp().clock.millis()),
                    access = TestApp.allowed(notifications = NotificationAccess.Blocked),
                )
            assertNull((home(snoozed) as HomeUi.Ready).state.banner)
        }

    @Test
    fun `next morning before 15 00 keeps last night with B-WAIT, after 18 00 it is E-SYNC or E-NODATA`() =
        runTest {
            val morning = TestApp()
            morning.at(SampleData.LAST_WAKE.plusDays(1).atTime(9, 0))
            val wait = (home(morning) as HomeUi.Ready).state
            assertEquals(SampleData.LAST_WAKE, wait.night.wakeDate)
            assertEquals(BannerKind.Wait, wait.banner)
            val evening = TestApp()
            evening.at(SampleData.LAST_WAKE.plusDays(1).atTime(19, 0))
            assertEquals(NightState.NoData, (home(evening) as HomeUi.Ready).state.night.state)
            val silent = TestApp()
            silent.at(SampleData.LAST_WAKE.plusDays(2).atTime(19, 0))
            val sync = (home(silent) as HomeUi.Ready).state
            assertEquals(NightState.NotSynced, sync.night.state)
            assertEquals(SampleData.LAST_WAKE, sync.lastResult?.wakeDate)
        }

    @Test
    fun `dismissing B-RANGE-READY and B-NOTIF-OFF is stored`() =
        runTest {
            val app = TestApp()
            val vm = HomeViewModel(app.data)
            vm.dismissBanner(BannerKind.RangeReady)
            vm.dismissBanner(BannerKind.NotificationsOff)
            val s = app.settings.current.value
            assertTrue(s.rangeReadySeen)
            assertEquals(app.clock.millis(), s.notificationBannerDismissedAtMs)
            vm.checkNow()
            assertEquals(1, app.scheduler.runNow)
        }

    @Test
    fun `night detail opens the date of the route and steps to its neighbours`() =
        runTest {
            val app = TestApp()
            val date = SampleData.LAST_WAKE.minusDays(5)
            val vm = NightDetailViewModel(SavedStateHandle(mapOf("date" to date.toString(), "section" to "Home")), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val state = vm.ui.ready { it != null }!!
            assertEquals(date, state.night.wakeDate)
            assertNotNull(state.previous)
            assertNotNull(state.next)
            vm.show(state.next!!)
            assertEquals(
                state.next,
                vm.ui
                    .ready { it?.night?.wakeDate == state.next }!!
                    .night.wakeDate,
            )
        }

    @Test
    fun `metric detail has 90 nights with the range of each night and a saved period`() =
        runTest {
            val app = TestApp()
            val handle = SavedStateHandle(mapOf("metric" to MetricId.SLEEPING_HR.key))
            val vm = MetricDetailViewModel(handle, app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val state = vm.ui.ready { it != null }!!
            assertEquals(SampleData.LAST_WAKE, state.anchorDate)
            assertEquals(ChartPeriod.Month, state.period)
            assertEquals(90, state.series?.points?.size)
            assertTrue(state.shownPoints.count { it.range != null } > 20)
            vm.setPeriod(ChartPeriod.Quarter)
            assertEquals("Quarter", handle.get<String>("period"))
            assertEquals(ChartPeriod.Quarter, vm.ui.ready { it?.period == ChartPeriod.Quarter }!!.period)
        }

    @Test
    fun `metric detail shows the access notice when permissions were revoked`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(granted = setOf(HcPermissions.READ_SLEEP)))
            val vm = MetricDetailViewModel(SavedStateHandle(mapOf("metric" to MetricId.SPO2.key)), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            assertEquals(MetricNotice.AccessOff, vm.ui.ready { it != null }!!.notice)
        }

    @Test
    fun `history lists every night newest first and keeps the view in saved state`() =
        runTest {
            val app = TestApp(access = TestApp.allowed(granted = setOf(HcPermissions.READ_SLEEP)))
            val handle = SavedStateHandle()
            val vm = HistoryViewModel(handle, app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val state = vm.ui.ready { !it.loading }
            assertEquals(SampleData.evaluated.size, state.nights.size)
            assertEquals(SampleData.LAST_WAKE, state.nights.first().wakeDate)
            assertEquals(HistoryAccess.Permission, state.access)
            vm.setView(HistoryView.Calendar)
            assertEquals("Calendar", handle.get<String>("history_view"))
        }

    @Test
    fun `a source change keeps the stored nights in history and restarts learning`() =
        runTest {
            val app = TestApp()
            app.platform.labels = app.platform.labels + ("org.example.ring" to "Ring")
            val settings = SettingsViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { settings.ui.collect {} }
            settings.pickSource(SourceOption("org.example.ring", "Ring", LocalDate.now(), emptyList()))
            assertEquals(
                "org.example.ring",
                settings.ui
                    .ready { it?.pendingSource != null }!!
                    .pendingSource
                    ?.packageName,
            )
            settings.confirmSource()
            assertEquals("org.example.ring", app.settings.current.value.sourcePackage)
            assertEquals(1, app.scheduler.runNow)
            assertEquals(SampleData.evaluated.size, app.nights.all.size)
            val history = HistoryViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { history.ui.collect {} }
            assertEquals(
                SampleData.evaluated.size,
                history.ui
                    .ready { !it.loading }
                    .nights.size,
            )
            assertTrue(home(app) is HomeUi.Learning)
        }

    @Test
    fun `sensitivity presets map onto the stored levels and the preview counts notes`() =
        runTest {
            val app = TestApp()
            val vm = SettingsViewModel(SavedStateHandle(), app.data)
            backgroundScope.launch { vm.ui.collect {} }
            val state = vm.ui.ready { it != null }!!
            assertEquals(SensitivityPreset.Balanced, state.sensitivity)
            assertNotNull(state.previewNotes)
            vm.setSensitivity(SensitivityPreset.More)
            assertEquals(SensitivityLevel.HIGH, app.settings.current.value.sensitivity)
            val more = vm.ui.ready { it?.sensitivity == SensitivityPreset.More }!!
            assertTrue(more.previewNotes!! >= state.previewNotes!!)
        }

    @Test
    fun `the root re-evaluates stale stored nights at start and after a sensitivity change, silently`() =
        runTest {
            val app = TestApp()
            val newest = app.nights.all.last()
            val stale = Evaluator.evaluate(newest.summary, app.nights.all.map { it.summary }, AlgoParams(zThreshold = 4.5))
            app.nights.upsert(newest.copy(evaluation = stale, algoFingerprint = null))

            AppRootViewModel(app.data)
            val balanced = AppSettings(onboardingDone = true).algoParams()
            assertTrue(app.nights.all.all { it.algoFingerprint == AlgoFingerprint.of(balanced) })
            assertEquals(
                newest.evaluation,
                app.nights.all
                    .last()
                    .evaluation,
            )
            assertEquals(1, app.runLog.lines.count { it.kind == RunLogKind.RECOMPUTED })

            SettingsViewModel(SavedStateHandle(), app.data).setSensitivity(SensitivityPreset.More)
            val more = AppSettings(sensitivity = SensitivityLevel.HIGH).algoParams()
            assertTrue(app.nights.all.all { it.algoFingerprint == AlgoFingerprint.of(more) })
            val history = app.nights.all.map { it.summary }
            assertEquals(app.nights.all.map { Evaluator.evaluate(it.summary, history, more) }, app.nights.all.map { it.evaluation })
            assertEquals(2, app.runLog.lines.count { it.kind == RunLogKind.RECOMPUTED })
            assertTrue(app.runLog.lines.none { it.kind == RunLogKind.NOTIFIED })
        }

    @Test
    fun `onboarding goes through the permission request, denial twice opens Health Connect`() =
        runTest {
            val app = TestApp(nights = 0, settings = AppSettings())
            app.gateway.granted.clear()
            val handle = SavedStateHandle()
            val vm = OnboardingViewModel(handle, app.data)
            vm.next()
            vm.next()
            assertEquals(OnboardingStep.Source, vm.ui.value.step)
            assertEquals(AppSettings.DEFAULT_SOURCE_PACKAGE, vm.ui.value.selectedSource)
            vm.useSource()
            assertEquals(OnboardingStep.Permissions, vm.ui.value.step)
            assertTrue(HcPermissions.REQUIRED.all { it in vm.permissionsToRequest() })
            vm.onPermissionsResult(emptySet())
            assertEquals(PermissionStatus.Denied, vm.ui.value.permission)
            vm.onPermissionsResult(emptySet())
            assertEquals(PermissionStatus.DeniedAgain, vm.ui.value.permission)
            assertTrue(vm.mustOpenHealthConnect())
            app.gateway.granted += HcPermissions.ALL
            vm.onPermissionsResult(HcPermissions.ALL)
            assertEquals(PermissionStatus.Granted, vm.ui.value.permission)
            vm.next()
            assertEquals(OnboardingStep.Notifications, vm.ui.value.step)
            vm.onNotificationsResult(false)
            assertEquals(OnboardingStep.Ready, vm.ui.value.step)
            assertEquals(NotificationAccess.Blocked, vm.ui.value.notifications)
            assertEquals("Ready", handle.get<String>("step"))
            var finished = false
            vm.done { finished = true }
            assertTrue(finished)
            assertTrue(app.settings.current.value.onboardingDone)
            assertEquals(1, app.scheduler.runNow)
            assertTrue(app.platform.asked)
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
