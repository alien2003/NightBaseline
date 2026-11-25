package io.github.alien2003.nightbaseline.nav

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.ui.components.GlassSheet
import io.github.alien2003.nightbaseline.ui.components.OverlayHost
import io.github.alien2003.nightbaseline.ui.components.TonalButton
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.history.HistoryAccess
import io.github.alien2003.nightbaseline.ui.history.HistoryActions
import io.github.alien2003.nightbaseline.ui.history.HistoryNightPreview
import io.github.alien2003.nightbaseline.ui.history.HistoryScreen
import io.github.alien2003.nightbaseline.ui.history.HistoryViewModel
import io.github.alien2003.nightbaseline.ui.home.HomeActions
import io.github.alien2003.nightbaseline.ui.home.HomeScreen
import io.github.alien2003.nightbaseline.ui.home.HomeUi
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.learning.LearningActions
import io.github.alien2003.nightbaseline.ui.learning.LearningScreen
import io.github.alien2003.nightbaseline.ui.metric.InfoOverlay
import io.github.alien2003.nightbaseline.ui.metric.InfoParagraphs
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailActions
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailScreen
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailViewModel
import io.github.alien2003.nightbaseline.ui.metric.RangeExplainerOverlay
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.night.NightDetailActions
import io.github.alien2003.nightbaseline.ui.night.NightDetailContent
import io.github.alien2003.nightbaseline.ui.night.NightDetailScreen
import io.github.alien2003.nightbaseline.ui.night.NightDetailState
import io.github.alien2003.nightbaseline.ui.night.NightDetailViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingActions
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingScreen
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingViewModel
import io.github.alien2003.nightbaseline.ui.settings.SettingsActions
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.settings.SettingsScreen
import io.github.alien2003.nightbaseline.ui.settings.SettingsViewModel
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import java.time.LocalDate

/**
 * A ViewModel factory that replaces Hilt (navigation tests and the integrated screenshots build the ViewModels from
 * the data-local / data-hc fakes). Null in the app: [appViewModel] then uses `hiltViewModel()`.
 */
val LocalViewModelFactory = staticCompositionLocalOf<ViewModelProvider.Factory?> { null }

/** The ViewModel of the current back stack entry: Hilt in the app, [LocalViewModelFactory] in tests. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val factory = LocalViewModelFactory.current
    return if (factory != null) viewModel(factory = factory) else hiltViewModel()
}

/** Sheets and dialogs opened from several screens; saved, so they survive fold / unfold and recreation. */
enum class AppSheet {
    /** "More" in Glance windows: History and Settings. */
    More,

    /** "How your usual range works". */
    Explainer,

    /** "Not recorded by {source}": the not-provided explanation. */
    NotRecorded,
}

/**
 * The navigation graph (`screens.md` 3) inside the adaptive shell: typed routes for Home, History, Settings, Night
 * detail, Metric detail and onboarding; list-detail on wide windows (Home metrics -> Metric detail pane, History ->
 * Night detail pane) by the pane rule of `resolveLayout`, with the selection carried across fold / unfold; deep links
 * from notifications; Ctrl+1/2/3, Esc and Alt+Left. Every screen draws its own scaffold with the floating bar or rail
 * of [layout].
 *
 * @param onboardingDone null while unknown (renders nothing); false starts onboarding.
 * @param newestNight the newest stored night (a night link to it opens Home).
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // The graph: one composable block per destination.
@Composable
fun AppNavHost(
    layout: LayoutSpec,
    onboardingDone: Boolean,
    system: SystemActions,
    modifier: Modifier = Modifier,
    newestNight: String? = null,
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onCheckNow: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    debugAction: (@Composable () -> Unit)? = null,
) {
    var sheet by rememberSaveable { mutableStateOf<AppSheet?>(null) }
    var sheetSource by rememberSaveable { mutableStateOf("") }
    var sheetTitle by rememberSaveable { mutableStateOf("") }
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val openTab: (TopLevel) -> Unit = { tab -> navController.toTop(tab) }
    val requestPermissions: () -> Unit = {
        system.requestHealthPermissions(HcPermissions.ALL) { granted ->
            onRefresh()
            if (HcPermissions.missingRequired(granted).isNotEmpty() && granted.isEmpty()) system.openHealthConnect()
        }
    }
    val onboardingDoneNow by rememberUpdatedState(onboardingDone)

    // Deep links wait for onboarding (screens.md 3: kept and opened after "ready").
    LaunchedEffect(deepLink, onboardingDone, newestNight) {
        val link = deepLink ?: return@LaunchedEffect
        if (!onboardingDoneNow) return@LaunchedEffect
        when (link) {
            is DeepLink.Night -> {
                navController.toTop(TopLevel.Home)
                if (link.date != newestNight) navController.navigate(NightRoute(link.date, TopLevel.Home.name))
            }

            is DeepLink.Fix -> {
                when (link.problem) {
                    FixProblem.Permission -> {
                        navController.toTop(TopLevel.Home)
                        requestPermissions()
                    }

                    FixProblem.HealthConnect -> {
                        navController.toTop(TopLevel.Home)
                    }

                    FixProblem.SourceSync -> {
                        navController.toTop(TopLevel.Settings)
                        navController.navigate(SettingsRoute(SettingsPage.SourcePicker.name))
                    }
                }
            }
        }
        onDeepLinkHandled()
    }
    // "Set up again" in Settings clears the flag: onboarding at the source step, history kept.
    LaunchedEffect(onboardingDone) {
        val onOnboarding =
            navController.currentBackStackEntry
                ?.destination
                ?.route
                ?.contains("OnboardingRoute") == true
        if (!onboardingDone && !onOnboarding && navController.currentBackStackEntry != null) {
            navController.navigate(OnboardingRoute(OnboardingStep.Source.name)) { popUpTo(0) { inclusive = true } }
        }
    }

    val nightActions: (NightState, String) -> Unit = { state, source ->
        when (state) {
            NightState.Learning, NightState.LearningStart, NightState.Moved, NightState.SomeMoved -> {
                sheet = AppSheet.Explainer
            }

            NightState.NotSynced -> {
                system.openApp(source)
            }

            NightState.NoData, NightState.Short, NightState.Ambiguous, NightState.NotEvaluated -> {
                navController.toTop(TopLevel.History)
            }

            NightState.Permission -> {
                requestPermissions()
            }

            NightState.HealthConnectUpdate -> {
                system.openStore()
            }

            NightState.SourceGone -> {
                navController.navigate(SettingsRoute(SettingsPage.SourcePicker.name))
            }

            NightState.HealthConnectNone -> {
                navController.navigate(SettingsRoute(SettingsPage.About.name))
            }

            NightState.Loading, NightState.AllWithin -> {
                Unit
            }
        }
    }
    val nightSecondary: (NightState) -> Unit = { state ->
        if (state == NightState.HealthConnectUpdate) onRefresh() else onCheckNow()
    }
    val bannerAction: (BannerKind) -> Unit = { kind ->
        when (kind) {
            BannerKind.Background -> requestPermissions()
            BannerKind.ReadFailed, BannerKind.Wait -> onCheckNow()
            BannerKind.SourceQuiet -> navController.navigate(SettingsRoute(SettingsPage.SourcePicker.name))
            BannerKind.NotificationsOff -> system.openNotificationSettings()
            BannerKind.RangeReady -> Unit
        }
    }
    val about: () -> Unit = { navController.navigate(SettingsRoute(SettingsPage.About.name)) }
    val more: () -> Unit = { sheet = AppSheet.More }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val tab =
                    when (event.key) {
                        Key.One -> TopLevel.Home
                        Key.Two -> TopLevel.History
                        Key.Three -> TopLevel.Settings
                        else -> null
                    }
                when {
                    event.isCtrlPressed && tab != null && onboardingDoneNow -> {
                        openTab(tab)
                        true
                    }

                    event.isCtrlPressed && event.key == Key.R && onboardingDoneNow -> {
                        onCheckNow()
                        true
                    }

                    event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft) -> {
                        when {
                            sheet != null -> sheet = null
                            back?.hasEnabledCallbacks() == true -> back.onBackPressed()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        NavHost(
            navController = navController,
            startDestination = if (onboardingDone) HomeRoute else OnboardingRoute(),
        ) {
            composable<HomeRoute> { entry ->
                HomeDestination(
                    entry = entry,
                    layout = layout,
                    navController = navController,
                    onSelectTab = openTab,
                    onStateAction = nightActions,
                    onStateSecondary = nightSecondary,
                    onBannerAction = bannerAction,
                    onSheet = { s, source, title ->
                        sheet = s
                        sheetSource = source
                        sheetTitle = title
                    },
                    onAbout = about,
                    onMore = more,
                    debugAction = debugAction,
                )
            }
            composable<HistoryRoute> { entry ->
                HistoryDestination(entry, layout, navController, openTab, more, requestPermissions, system)
            }
            composable<SettingsRoute> {
                SettingsDestination(layout, navController, openTab, more, system)
            }
            composable<NightRoute> { entry ->
                val route = entry.toRoute<NightRoute>()
                val vm: NightDetailViewModel = appViewModel()
                val text = rememberUiText()
                val state by vm.ui.collectAsStateWithLifecycle()
                val scroll = rememberScrollState()
                // Unfolded into two panes while this night was opened from History: back to History's detail pane.
                FoldBack(layout.twoPanes && route.section == TopLevel.History.name) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(SELECT_NIGHT, vm.shownDate.value.toString())
                    navController.popBackStack()
                }
                state?.let { s ->
                    NightDetailScreen(
                        s,
                        layout,
                        actions =
                            NightDetailActions(
                                onBack = { navController.popBackStack() },
                                onPrevious = vm::show,
                                onNext = vm::show,
                                onMetric = { m ->
                                    navController.navigate(MetricRoute(m.key, vm.shownDate.value.toString(), route.section))
                                },
                                onStateAction = { nightActions(it, s.source) },
                                onStateSecondary = nightSecondary,
                                onHowItWorks = { sheet = AppSheet.Explainer },
                                onBannerAction = bannerAction,
                                onBannerDismiss = vm::dismissBanner,
                                onNotRecordedInfo = {
                                    sheetSource = s.source
                                    sheetTitle =
                                        text.string(
                                            R.string.home_not_recorded_row,
                                            s.source,
                                            text.list(s.night.notRecorded.map(text::name)),
                                        )
                                    sheet = AppSheet.NotRecorded
                                },
                                onAbout = about,
                                onMore = more,
                                onSelectTab = openTab,
                            ),
                        scrollState = scroll,
                    )
                }
            }
            composable<MetricRoute> { entry ->
                val route = entry.toRoute<MetricRoute>()
                val vm: MetricDetailViewModel = appViewModel()
                val state by vm.ui.collectAsStateWithLifecycle()
                var selection by rememberSaveable { mutableStateOf(route.point.takeIf { it >= 0 }) }
                var explainer by rememberSaveable { mutableStateOf(false) }
                val scroll = rememberScrollState()
                // Unfolded into two panes while Home's metric was open: Home's detail pane takes over (layouts.md 8.2)
                // with the same period and selected night.
                FoldBack(layout.twoPanes && route.section == TopLevel.Home.name && route.date == null) {
                    navController.previousBackStackEntry?.savedStateHandle?.let { home ->
                        state?.period?.let { home[SELECT_PERIOD] = it.name }
                        home[SELECT_POINT] = selection ?: -1
                        home[SELECT_METRIC] = route.metric
                    }
                    navController.popBackStack()
                }
                state?.let { s ->
                    MetricDetailScreen(
                        s.copy(selected = selection, explainerOpen = explainer),
                        layout,
                        actions =
                            MetricDetailActions(
                                onBack = { navController.popBackStack() },
                                onPeriod = {
                                    selection = null
                                    vm.setPeriod(it)
                                },
                                onSelect = { selection = it },
                                onOpenNight = { navController.navigate(NightRoute(it.toString(), route.section)) },
                                onOpenExplainer = { explainer = true },
                                onCloseExplainer = { explainer = false },
                                onChooseSource = { navController.navigate(SettingsRoute(SettingsPage.SourcePicker.name)) },
                                onOpenPermissions = requestPermissions,
                                onOpenStore = system::openStore,
                                onAbout = about,
                            ),
                        scroll = scroll,
                        onSelectTab = openTab,
                    )
                    if (explainer) BackHandler { explainer = false }
                }
            }
            composable<OnboardingRoute> {
                OnboardingDestination(layout, navController, system, onRefresh, about)
            }
        }
        sheet?.let { open ->
            BackHandler { sheet = null }
            when (open) {
                AppSheet.Explainer -> {
                    RangeExplainerOverlay(layout, sideLine = null, onClose = { sheet = null })
                }

                AppSheet.NotRecorded -> {
                    InfoOverlay(layout, sheetTitle, onClose = { sheet = null }) {
                        InfoParagraphs(listOf(stringResource(R.string.detail_not_provided_body, sheetSource)), it)
                    }
                }

                AppSheet.More -> {
                    MoreSheet(
                        onClose = { sheet = null },
                        onOpen = { tab ->
                            sheet = null
                            openTab(tab)
                        },
                    )
                }
            }
        }
    }
}

/** Saved-state key on Home's entry: a metric to select in the detail pane after an unfold. */
const val SELECT_METRIC: String = "select_metric"

/** Saved-state key on Home's entry: the chart period (`ChartPeriod` name) that comes with [SELECT_METRIC]. */
const val SELECT_PERIOD: String = "select_period"

/** Saved-state key on Home's entry: the selected chart night (index, -1 for none) that comes with [SELECT_METRIC]. */
const val SELECT_POINT: String = "select_point"

/** Saved-state key on History's entry: a night to select in the detail pane after an unfold. */
const val SELECT_NIGHT: String = "select_night"

/** Runs [action] once when [condition] becomes true (a layout change, never a reload). */
@Composable
private fun FoldBack(
    condition: Boolean,
    action: () -> Unit,
) {
    val current by rememberUpdatedState(action)
    LaunchedEffect(condition) { if (condition) current() }
}

/** Top-level navigation: one entry per section, state saved and restored, Home at the root. */
fun NavHostController.toTop(tab: TopLevel) {
    val route: Any =
        when (tab) {
            TopLevel.Home -> HomeRoute
            TopLevel.History -> HistoryRoute
            TopLevel.Settings -> SettingsRoute()
        }
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Suppress("LongParameterList")
@Composable
private fun HomeDestination(
    entry: NavBackStackEntry,
    layout: LayoutSpec,
    navController: NavHostController,
    onSelectTab: (TopLevel) -> Unit,
    onStateAction: (NightState, String) -> Unit,
    onStateSecondary: (NightState) -> Unit,
    onBannerAction: (BannerKind) -> Unit,
    onSheet: (AppSheet, String, String) -> Unit,
    onAbout: () -> Unit,
    onMore: () -> Unit,
    debugAction: (@Composable () -> Unit)?,
) {
    val vm: HomeViewModel = appViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val text = rememberUiText()
    // Saved UI state of the route, above the layout switch (layouts.md 8.2 rule 1).
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var explicit by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(ChartPeriod.Month) }
    var selection by rememberSaveable { mutableStateOf<Int?>(null) }
    val scroll = rememberScrollState()
    val pendingSelect by entry.savedStateHandle.getStateFlow<String?>(SELECT_METRIC, null).collectAsStateWithLifecycle()
    LaunchedEffect(pendingSelect) {
        pendingSelect?.let {
            selected = it
            explicit = true
            val handle = entry.savedStateHandle
            handle.get<String>(SELECT_PERIOD)?.let { p -> ChartPeriod.entries.firstOrNull { e -> e.name == p } }?.let { period = it }
            selection = handle.get<Int>(SELECT_POINT)?.takeIf { p -> p >= 0 }
            handle[SELECT_PERIOD] = null
            handle[SELECT_POINT] = null
            handle[SELECT_METRIC] = null
        }
    }
    // Folded from two panes with a metric the person chose: the detail screen is on top (layouts.md 8.2 rule 4), with
    // the pane's period and selected night.
    FoldBack(!layout.twoPanes && explicit && selected != null) {
        explicit = false
        selected?.let { navController.navigate(MetricRoute(it, period = period.name, point = selection ?: -1)) }
    }
    when (val u = ui) {
        HomeUi.Pending -> {
            Box(Modifier.fillMaxSize())
        }

        is HomeUi.Learning -> {
            LearningScreen(
                u.state,
                layout,
                actions =
                    LearningActions(
                        onHowItWorks = { onSheet(AppSheet.Explainer, u.state.source, "") },
                        onMetric = { navController.navigate(MetricRoute(it.key)) },
                        onNight = { navController.navigate(NightRoute(it.wakeDate.toString(), TopLevel.Home.name)) },
                        onSeeAll = { onSelectTab(TopLevel.History) },
                        onAbout = onAbout,
                        onMore = onMore,
                    ),
                onSelectTab = onSelectTab,
                scroll = scroll,
            )
        }

        is HomeUi.Ready -> {
            val state = u.state.copy(selectedMetric = selected?.let(::MetricId), period = period, chartSelection = selection)
            HomeScreen(
                state,
                layout,
                actions =
                    HomeActions(
                        onMetric = { m ->
                            if (layout.twoPanes) {
                                selected = m.key
                                explicit = true
                                selection = null
                            } else {
                                navController.navigate(MetricRoute(m.key))
                            }
                        },
                        onNight = { navController.navigate(NightRoute(it.toString(), TopLevel.Home.name)) },
                        onSeeAll = { onSelectTab(TopLevel.History) },
                        onStateAction = { onStateAction(it, state.source) },
                        onStateSecondary = onStateSecondary,
                        onHowItWorks = { onSheet(AppSheet.Explainer, state.source, "") },
                        onBannerAction = onBannerAction,
                        onBannerDismiss = vm::dismissBanner,
                        onNotRecordedInfo = {
                            val names =
                                state.metricsNight
                                    ?.notRecorded
                                    .orEmpty()
                                    .map(text::name)
                            onSheet(
                                AppSheet.NotRecorded,
                                state.source,
                                text.string(R.string.home_not_recorded_row, state.source, text.list(names)),
                            )
                        },
                        onAbout = onAbout,
                        onMore = onMore,
                        onPeriod = {
                            period = it
                            selection = null
                        },
                        onChartSelect = { selection = it },
                        onSelectTab = onSelectTab,
                    ),
                debugAction = debugAction,
                scrollState = scroll,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun HistoryDestination(
    entry: NavBackStackEntry,
    layout: LayoutSpec,
    navController: NavHostController,
    onSelectTab: (TopLevel) -> Unit,
    onMore: () -> Unit,
    requestPermissions: () -> Unit,
    system: SystemActions,
) {
    val vm: HistoryViewModel = appViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val nights by vm.list.collectAsStateWithLifecycle()
    var explicit by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pendingSelect by entry.savedStateHandle.getStateFlow<String?>(SELECT_NIGHT, null).collectAsStateWithLifecycle()
    LaunchedEffect(pendingSelect) {
        pendingSelect?.let {
            vm.select(LocalDate.parse(it))
            explicit = true
            entry.savedStateHandle[SELECT_NIGHT] = null
        }
    }
    FoldBack(!layout.twoPanes && explicit && state.selected != null) {
        explicit = false
        state.selected?.let { navController.navigate(NightRoute(it.toString(), TopLevel.History.name)) }
    }
    HistoryScreen(
        state,
        layout,
        actions =
            HistoryActions(
                onView = vm::setView,
                onFilter = vm::setFilter,
                onMonth = vm::setMonth,
                onSelect = {
                    vm.select(it)
                    explicit = layout.twoPanes
                },
                onOpenNight = { navController.navigate(NightRoute(it.toString(), TopLevel.History.name)) },
                onFixAccess = { access ->
                    if (access == HistoryAccess.Permission) requestPermissions() else system.openStore()
                },
                onMore = onMore,
            ),
        onSelectTab = onSelectTab,
        listState = listState,
        nightPreview = { night, interactive ->
            if (interactive && layout.twoPanes) {
                // The real Night detail in the detail pane (layouts.md 5.3 Expanded, Book).
                val index = nights.indexOfFirst { it.wakeDate == night.wakeDate }
                val detail =
                    NightDetailState(
                        night = night,
                        source = state.source,
                        previous = nights.getOrNull(index + 1)?.wakeDate,
                        next = if (index > 0) nights[index - 1].wakeDate else null,
                    )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
                    // The pane's own title row (layouts.md 4.3): the night label, as the Night detail top bar shows it.
                    Text(
                        rememberUiText().nightLabel(night),
                        style = NbTheme.typography.title,
                        color = NbTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    NightDetailContent(
                        detail,
                        NightDetailActions(
                            onPrevious = vm::select,
                            onNext = vm::select,
                            onMetric = { m ->
                                navController.navigate(MetricRoute(m.key, night.wakeDate.toString(), TopLevel.History.name))
                            },
                        ),
                    )
                }
            } else {
                HistoryNightPreview(night, state.source, interactive, onOpen = {
                    navController.navigate(NightRoute(night.wakeDate.toString(), TopLevel.History.name))
                })
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun SettingsDestination(
    layout: LayoutSpec,
    navController: NavHostController,
    onSelectTab: (TopLevel) -> Unit,
    onMore: () -> Unit,
    system: SystemActions,
) {
    val vm: SettingsViewModel = appViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val reason = NbTheme.effects.reason
    state?.let { s ->
        BackHandler(enabled = s.page != SettingsPage.Main && !layout.twoPanes) { vm.back() }
        SettingsScreen(
            s.copy(glassReason = reason),
            layout,
            actions =
                SettingsActions(
                    onOpenPage = vm::openPage,
                    onSelectSection = vm::selectSection,
                    onBack = { if (!vm.back()) navController.popBackStack() },
                    onPickSource = vm::pickSource,
                    onConfirmSource = vm::confirmSource,
                    onCancelSource = vm::cancelSource,
                    onOpenHealthConnect = system::openHealthConnect,
                    onSetupAgain = vm::setupAgain,
                    onRequestNotifications = { system.requestNotifications { vm.refresh() } },
                    onOpenNotificationSettings = system::openNotificationSettings,
                    onNotifyResults = vm::setNotifyResults,
                    onNotifyProblems = vm::setNotifyProblems,
                    onSensitivity = vm::setSensitivity,
                    onTheme = vm::setTheme,
                    onGlass = vm::setGlass,
                ),
            onSelectTab = onSelectTab,
            onMore = onMore,
            scroll = scroll,
        )
    }
}

@Composable
private fun OnboardingDestination(
    layout: LayoutSpec,
    navController: NavHostController,
    system: SystemActions,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
) {
    val vm: OnboardingViewModel = appViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val request: () -> Unit = {
        if (vm.mustOpenHealthConnect()) {
            system.openHealthConnect()
        } else {
            system.requestHealthPermissions(vm.permissionsToRequest()) { vm.onPermissionsResult(it) }
        }
    }
    BackHandler(enabled = state.step != OnboardingStep.Welcome && state.step != OnboardingStep.Ready) { vm.back() }
    OnboardingScreen(
        state,
        layout,
        actions =
            OnboardingActions(
                onNext = vm::next,
                onBack = { vm.back() },
                onSelectSource = vm::select,
                onUseSource = vm::useSource,
                onCheckAgain = {
                    onRefresh()
                    if (vm.needsPermissionsToList()) request() else vm.checkAgain()
                },
                onOpenHealthConnect = system::openHealthConnect,
                onInstallHealthConnect = system::openStore,
                onAbout = onAbout,
                onRequestPermissions = request,
                onAllowNotifications = { system.requestNotifications(vm::onNotificationsResult) },
                onSkipNotifications = vm::skipNotifications,
                onDone = {
                    vm.done {
                        navController.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                    }
                },
            ),
        scroll = scroll,
    )
}

/** Glance "More" (`screens.md` 2): a glass sheet with labelled History and Settings entries. */
@Composable
private fun MoreSheet(
    onClose: () -> Unit,
    onOpen: (TopLevel) -> Unit,
) {
    OverlayHost(onDismiss = onClose, alignment = Alignment.BottomCenter) {
        GlassSheet(stringResource(R.string.action_more), onClose = onClose) {
            listOf(TopLevel.Home, TopLevel.History, TopLevel.Settings).forEach { tab ->
                TonalButton(stringResource(tab.label), onClick = { onOpen(tab) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
