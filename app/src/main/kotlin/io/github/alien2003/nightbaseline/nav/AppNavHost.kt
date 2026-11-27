package io.github.alien2003.nightbaseline.nav

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.ui.components.GlassSheet
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.SecondaryButton
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
import io.github.alien2003.nightbaseline.ui.interaction.LocalNbBackRouter
import io.github.alien2003.nightbaseline.ui.interaction.ModalHost
import io.github.alien2003.nightbaseline.ui.interaction.ModalKind
import io.github.alien2003.nightbaseline.ui.interaction.ModalScope
import io.github.alien2003.nightbaseline.ui.interaction.NbNavStack
import io.github.alien2003.nightbaseline.ui.interaction.NbPageNavigator
import io.github.alien2003.nightbaseline.ui.interaction.NbSectionHost
import io.github.alien2003.nightbaseline.ui.interaction.NbStackHost
import io.github.alien2003.nightbaseline.ui.interaction.nbKeys
import io.github.alien2003.nightbaseline.ui.interaction.nbReproject
import io.github.alien2003.nightbaseline.ui.interaction.page
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbLazyListState
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbNavController
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.learning.LearningActions
import io.github.alien2003.nightbaseline.ui.learning.LearningScreen
import io.github.alien2003.nightbaseline.ui.metric.InfoPanel
import io.github.alien2003.nightbaseline.ui.metric.InfoParagraphs
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailActions
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailScreen
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailViewModel
import io.github.alien2003.nightbaseline.ui.metric.RangeExplainerPanel
import io.github.alien2003.nightbaseline.ui.metric.infoModalKind
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
import io.github.alien2003.nightbaseline.ui.settings.SetupAgainConfirm
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.flow.first
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
    navController: NavHostController = rememberNbNavController(),
    debugAction: (@Composable () -> Unit)? = null,
) {
    var sheet by rememberSaveable { mutableStateOf<AppSheet?>(null) }
    var sheetSource by rememberSaveable { mutableStateOf("") }
    var sheetTitle by rememberSaveable { mutableStateOf("") }
    val router = LocalNbBackRouter.current
    val openTab: (TopLevel) -> Unit = { tab -> navController.toTop(tab) }
    // Asks for every permission; when nothing of [wanted] comes back and no dialog can have been shown (Health
    // Connect stops showing it after two denials), the Health Connect app opens where it can be turned on.
    val requestFor: (Set<String>) -> Unit = { wanted ->
        val launched = SystemClock.elapsedRealtime()
        system.requestHealthPermissions(HcPermissions.ALL) { granted ->
            onRefresh()
            val elapsed = SystemClock.elapsedRealtime() - launched
            if (PermissionFallback.mustOpenHealthConnect(wanted, granted, elapsed)) system.openHealthConnect()
        }
    }
    val requestPermissions: () -> Unit = { requestFor(HcPermissions.REQUIRED) }
    val onboardingDoneNow by rememberUpdatedState(onboardingDone)
    // A window change during a back gesture (fold, rotation, resize) drops it (ADR 0011 R6), so no page is left half
    // moved in a layout it was not laid out for.
    LaunchedEffect(layout) { router?.abort() }

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
    // The flag cleared from outside the graph (not "Set up again", which pushes a rerun of onboarding over Settings
    // and keeps the flag): onboarding at the source step, history kept.
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
                navController.push(SettingsRoute(SettingsPage.SourcePicker.name))
            }

            NightState.HealthConnectNone -> {
                navController.push(SettingsRoute(SettingsPage.About.name))
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
            BannerKind.Background -> requestFor(setOf(HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND))
            BannerKind.ReadFailed, BannerKind.Wait -> onCheckNow()
            BannerKind.SourceQuiet -> navController.push(SettingsRoute(SettingsPage.SourcePicker.name))
            BannerKind.NotificationsOff -> system.openNotificationSettings()
            BannerKind.RangeReady -> Unit
        }
    }
    val about: () -> Unit = { navController.push(SettingsRoute(SettingsPage.About.name)) }
    val more: () -> Unit = { sheet = AppSheet.More }

    // Ctrl+1/2/3, Ctrl+R, Esc and Alt+Left (layouts.md 10.2).
    val shortcuts: (KeyEvent) -> Boolean = shortcuts@{ event ->
        if (event.type != KeyEventType.KeyDown) return@shortcuts false
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

            // The same Back as the system's: the router's topmost target (sheet, sub-page, page, section).
            event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft) -> {
                router?.back() == true
            }

            else -> {
                false
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            // Every frame the app draws (scroll, fades, page and tab transitions, sheets, the blur under the bar)
            // votes for the panel's high rate on API 35+; an idle screen draws nothing and votes nothing (ADR 0008).
            // Draw-only: no pixel changes.
            .preferredFrameRate(FrameRateCategory.High)
            // With keyboard focus somewhere in the content; without focus the activity hands the keys that nothing
            // consumed to the same handler (RegisterKeyShortcuts).
            .nbKeys(shortcuts),
    ) {
        RegisterKeyShortcuts(shortcuts)
        val startDestination: Any = remember { if (onboardingDone) TopLevelRoute else OnboardingRoute() }
        val onSheet: (AppSheet, String, String) -> Unit = { s, source, title ->
            sheet = s
            sheetSource = source
            sheetTitle = title
        }
        // Every push and pop of every route is the framework's Page kind (ADR 0011); a back gesture drives the same
        // motion under the finger and its release continues from the finger's velocity.
        NbNavStack(
            navController = navController,
            // Fixed for the life of the graph: a change of the flag must not rebuild the graph (that would clear the
            // back stack and drop the step the redirect above asks for).
            startDestination = startDestination,
        ) {
            graph(
                layout = layout,
                navController = navController,
                openTab = openTab,
                nightActions = nightActions,
                nightSecondary = nightSecondary,
                bannerAction = bannerAction,
                onSheet = onSheet,
                about = about,
                more = more,
                requestPermissions = requestPermissions,
                system = system,
                onRefresh = onRefresh,
                debugAction = debugAction,
                onExplainer = { sheet = AppSheet.Explainer },
            )
        }
        // One host for the shared sheets: it animates every close (scrim, Back, drag, option) on the Nav spring and
        // leaves nothing composed once hidden. The Glance "More" sheet is always a sheet; the info panels follow the layout.
        ModalHost(
            sheet,
            onDismiss = { sheet = null },
            kind = if (sheet == AppSheet.More) ModalKind.Sheet else infoModalKind(layout),
        ) { open ->
            when (open) {
                AppSheet.Explainer -> {
                    RangeExplainerPanel(layout, sideLine = null)
                }

                AppSheet.NotRecorded -> {
                    InfoPanel(layout, sheetTitle) {
                        InfoParagraphs(listOf(stringResource(R.string.detail_not_provided_body, sheetSource)), it)
                    }
                }

                AppSheet.More -> {
                    MoreSheet(
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

/** The destinations of [AppNavHost]; every one is a page of the framework's stack (ADR 0011). */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod") // The graph: one composable block per destination.
private fun NavGraphBuilder.graph(
    layout: LayoutSpec,
    navController: NavHostController,
    openTab: (TopLevel) -> Unit,
    nightActions: (NightState, String) -> Unit,
    nightSecondary: (NightState) -> Unit,
    bannerAction: (BannerKind) -> Unit,
    onSheet: (AppSheet, String, String) -> Unit,
    about: () -> Unit,
    more: () -> Unit,
    requestPermissions: () -> Unit,
    system: SystemActions,
    onRefresh: () -> Unit,
    debugAction: (@Composable () -> Unit)?,
    onExplainer: () -> Unit,
) {
    page<TopLevelRoute> { entry ->
        val tabName by entry.savedStateHandle
            .getStateFlow(TOP_LEVEL_TAB, TopLevel.Home.name)
            .collectAsStateWithLifecycle()
        val tab = TopLevel.entries.firstOrNull { it.name == tabName } ?: TopLevel.Home
        // The sections in bar order (ADR 0006, ADR 0011 kind Tab): a bar or rail tap, Ctrl+1/2/3, "See all", "See
        // history" and a back gesture to Home are all the same change of the selected section. Back from History or
        // Settings returns to Home; on Home nothing takes Back (the system's back-to-home).
        NbSectionHost(selected = tab, order = TopLevel.entries, onBackToFirst = { openTab(TopLevel.Home) }) { shown, active ->
            when (shown) {
                TopLevel.Home -> {
                    HomeDestination(
                        entry = entry,
                        active = active,
                        layout = layout,
                        navController = navController,
                        onSelectTab = openTab,
                        onStateAction = nightActions,
                        onStateSecondary = nightSecondary,
                        onBannerAction = bannerAction,
                        onSheet = onSheet,
                        onAbout = about,
                        onMore = more,
                        debugAction = debugAction,
                    )
                }

                TopLevel.History -> {
                    HistoryDestination(entry, active, layout, navController, openTab, more, requestPermissions, system)
                }

                TopLevel.Settings -> {
                    SettingsDestination(layout, navController, openTab, more, system, rootPage = null)
                }
            }
        }
    }
    page<SettingsRoute> { entry ->
        val route = entry.toRoute<SettingsRoute>()
        SettingsDestination(
            layout,
            navController,
            openTab,
            more,
            system,
            rootPage =
                SettingsPage.entries.firstOrNull {
                    it.name ==
                        route.page
                },
        )
    }
    page<NightRoute> { entry ->
        run {
            val route = entry.toRoute<NightRoute>()
            val vm: NightDetailViewModel = appViewModel()
            val text = rememberUiText()
            val state by vm.ui.collectAsStateWithLifecycle()
            val scroll = rememberNbScrollState()
            // Unfolded into two panes while this night was opened from History: back to History's detail pane.
            FoldBack(layout.twoPanes && route.section == TopLevel.History.name) {
                navController.previousBackStackEntry?.savedStateHandle?.set(SELECT_NIGHT, vm.shownDate.value.toString())
                navController.popBackStack()
            }
            state?.let { s ->
                NightDetailScreen(
                    s,
                    layout.forDetail(),
                    actions =
                        NightDetailActions(
                            onBack = { navController.popBackStack() },
                            onPrevious = vm::show,
                            onNext = vm::show,
                            onMetric = { m ->
                                navController.push(MetricRoute(m.key, vm.shownDate.value.toString(), route.section))
                            },
                            onStateAction = { nightActions(it, s.source) },
                            onStateSecondary = nightSecondary,
                            onHowItWorks = onExplainer,
                            onBannerAction = bannerAction,
                            onBannerDismiss = vm::dismissBanner,
                            onNotRecordedInfo = {
                                onSheet(
                                    AppSheet.NotRecorded,
                                    s.source,
                                    text.string(
                                        R.string.home_not_recorded_row,
                                        s.source,
                                        text.list(s.night.notRecorded.map(text::name)),
                                    ),
                                )
                            },
                            onAbout = about,
                            onMore = more,
                            onSelectTab = openTab,
                        ),
                    scrollState = scroll,
                )
            }
        }
    }
    page<MetricRoute> { entry ->
        run {
            val route = entry.toRoute<MetricRoute>()
            val vm: MetricDetailViewModel = appViewModel()
            val state by vm.ui.collectAsStateWithLifecycle()
            var selection by rememberSaveable { mutableStateOf(route.point.takeIf { it >= 0 }) }
            var explainer by rememberSaveable { mutableStateOf(false) }
            val scroll = rememberNbScrollState()
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
                    layout.forDetail(),
                    actions =
                        MetricDetailActions(
                            onBack = { navController.popBackStack() },
                            onPeriod = {
                                selection = null
                                vm.setPeriod(it)
                            },
                            onSelect = { selection = it },
                            onOpenNight = { navController.push(NightRoute(it.toString(), route.section)) },
                            onOpenExplainer = { explainer = true },
                            onCloseExplainer = { explainer = false },
                            onChooseSource = { navController.push(SettingsRoute(SettingsPage.SourcePicker.name)) },
                            onOpenPermissions = requestPermissions,
                            onOpenStore = system::openStore,
                            onAbout = about,
                        ),
                    scroll = scroll,
                    onSelectTab = openTab,
                )
            }
        }
    }
    page<OnboardingRoute> { entry ->
        val rerun = entry.toRoute<OnboardingRoute>().rerun
        OnboardingDestination(layout, navController, system, onRefresh, about, rerun)
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

/**
 * Runs [action] once when [condition] becomes true (a layout change, never a reload); in a hidden top-level section
 * it waits until the section is shown again ([active]).
 */
@Composable
private fun FoldBack(
    condition: Boolean,
    active: () -> Boolean = ALWAYS,
    action: () -> Unit,
) {
    val current by rememberUpdatedState(action)
    val router = LocalNbBackRouter.current
    LaunchedEffect(condition) {
        if (condition) {
            snapshotFlow(active).first { it }
            // A layout change re-projects the pages with a snap, never a push or pop (ADR 0011 R6).
            nbReproject(router) { current() }
        }
    }
}

/** [FoldBack] outside the top-level host: always shown. */
private val ALWAYS: () -> Boolean = { true }

/**
 * Pushed detail pages carry no bottom bar on phones (`coloros-conformance.md` C7): the bar belongs to the top-level
 * destinations. The rail stays on wide windows.
 */
private fun LayoutSpec.forDetail(): LayoutSpec = if (navigation == NavigationKind.Bar) copy(navigation = NavigationKind.None) else this

/** Saved-state key on the [TopLevelRoute] entry: the selected section (`TopLevel` name). */
const val TOP_LEVEL_TAB: String = "top_level_tab"

/**
 * Top-level navigation: back to the [TopLevelRoute] entry at the root (pages pushed above it are popped) and select
 * [tab] there. The sections stay composed in `NbSectionHost`, so this rebuilds nothing.
 */
fun NavHostController.toTop(tab: TopLevel) {
    val root = currentBackStack.value.lastOrNull { it.destination.hasRoute<TopLevelRoute>() }
    if (root == null) {
        navigate(TopLevelRoute) { popUpTo(0) { inclusive = true } }
    } else if (currentBackStackEntry != root) {
        popBackStack<TopLevelRoute>(inclusive = false)
    }
    getBackStackEntry<TopLevelRoute>().savedStateHandle[TOP_LEVEL_TAB] = tab.name
}

/**
 * A push from a tap: only while the top entry is resumed. During a push or pop transition the leaving page stays
 * composed and keeps taking touches, but its entry is no longer resumed, so a second tap (a fast double tap, or a tap
 * on another row) does not push a second page over the first.
 */
fun <T : Any> NavHostController.push(route: T) {
    val top = currentBackStackEntry ?: return
    if (top.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navigate(route)
}

/** The selected section when [this] is the [TopLevelRoute] entry, else null. */
fun NavBackStackEntry.topLevelTab(): TopLevel? =
    if (destination.hasRoute<TopLevelRoute>()) {
        val name = savedStateHandle.get<String>(TOP_LEVEL_TAB) ?: TopLevel.Home.name
        TopLevel.entries.firstOrNull { it.name == name } ?: TopLevel.Home
    } else {
        null
    }

@Suppress("LongParameterList")
@Composable
private fun HomeDestination(
    entry: NavBackStackEntry,
    active: () -> Boolean,
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
    val scroll = rememberNbScrollState()
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
    FoldBack(!layout.twoPanes && explicit && selected != null, active) {
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
                        onMetric = { navController.push(MetricRoute(it.key)) },
                        onNight = { navController.push(NightRoute(it.wakeDate.toString(), TopLevel.Home.name)) },
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
                                navController.push(MetricRoute(m.key))
                            }
                        },
                        onNight = { navController.push(NightRoute(it.toString(), TopLevel.Home.name)) },
                        onSeeAll = { onSelectTab(TopLevel.History) },
                        onStateAction = { onStateAction(it, state.source) },
                        // "Check again" on the Health Connect card says so when it is still missing.
                        onStateSecondary = {
                            if (it ==
                                NightState.HealthConnectUpdate
                            ) {
                                vm.recheckHealthConnect()
                            } else {
                                onStateSecondary(it)
                            }
                        },
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
    active: () -> Boolean,
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
    val listState = rememberNbLazyListState()
    val pendingSelect by entry.savedStateHandle.getStateFlow<String?>(SELECT_NIGHT, null).collectAsStateWithLifecycle()
    LaunchedEffect(pendingSelect) {
        pendingSelect?.let {
            vm.select(LocalDate.parse(it))
            explicit = true
            entry.savedStateHandle[SELECT_NIGHT] = null
        }
    }
    FoldBack(!layout.twoPanes && explicit && state.selected != null, active) {
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
                onOpenNight = { navController.push(NightRoute(it.toString(), TopLevel.History.name)) },
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
                    NbText(
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
                                navController.push(MetricRoute(m.key, night.wakeDate.toString(), TopLevel.History.name))
                            },
                        ),
                    )
                }
            } else {
                HistoryNightPreview(night, state.source, interactive, onOpen = {
                    navController.push(NightRoute(night.wakeDate.toString(), TopLevel.History.name))
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
    rootPage: SettingsPage?,
) {
    val vm: SettingsViewModel = appViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val setupAgain by vm.setupAgainPending.collectAsStateWithLifecycle()
    val scroll = rememberNbScrollState()
    val reason = NbTheme.effects.reason
    // Remembered, so a recomposition of the destination (a tab switch) does not recompose the screen.
    val confirm =
        remember(setupAgain, vm, navController) {
            SetupAgainConfirm(
                open = setupAgain,
                onConfirm = {
                    vm.cancelSetupAgain()
                    navController.push(OnboardingRoute(OnboardingStep.Source.name, rerun = true))
                },
                onCancel = vm::cancelSetupAgain,
            )
        }
    // A pushed Settings route starts its own stack at the page it opened (B11): one Back on that page pops the
    // route, so a route never shows the list with the bar.
    // In two panes the detail pane has its own structural stack (About -> Licenses), so the route's root is the list.
    val paned = layout.twoPanes && layout.mode != LayoutMode.Tabletop && layout.mode != LayoutMode.Glance
    val root = rootPage?.takeIf { it != SettingsPage.Main && !paned }
    val back: () -> Unit = { if (page == root || !vm.back()) navController.popBackStack() }
    state?.let { s ->
        // The page comes straight from the ViewModel: what is shown and what Back closes are the same page. The
        // screen draws its sub-pages as a framework stack (ADR 0011).
        SettingsScreen(
            s.copy(glassReason = reason, page = page),
            layout,
            actions =
                SettingsActions(
                    onOpenPage = vm::openPage,
                    onSelectSection = vm::selectSection,
                    onBack = back,
                    onPickSource = vm::pickSource,
                    onConfirmSource = vm::confirmSource,
                    onCancelSource = vm::cancelSource,
                    onOpenHealthConnect = system::openHealthConnect,
                    onInstallHealthConnect = system::openStore,
                    onCheckSources = vm::checkSources,
                    onSetupAgain = vm::askSetupAgain,
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
            rootPage = root,
            setupAgain = confirm,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun OnboardingDestination(
    layout: LayoutSpec,
    navController: NavHostController,
    system: SystemActions,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
    rerun: Boolean,
) {
    val vm: OnboardingViewModel = appViewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    // "Set up again" pushed this rerun over Settings at the source step: Back there returns to Settings (the page
    // stack's pop), and nothing is reset until the rerun is done.
    val leaves = rerun && state.step == OnboardingStep.Source
    val request: () -> Unit = {
        if (vm.mustOpenHealthConnect()) {
            system.openHealthConnect()
        } else {
            system.requestHealthPermissions(vm.permissionsToRequest()) { vm.onPermissionsResult(it) }
        }
    }
    val actions =
        OnboardingActions(
            onNext = vm::next,
            onBack = { if (leaves) navController.popBackStack() else vm.back() },
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
                    navController.navigate(TopLevelRoute) { popUpTo(0) { inclusive = true } }
                }
            },
        )
    // The steps are a stack of pages (ADR 0011, relation Depth): Next pushes, Back (button, gesture, Esc) pops. A
    // rerun's first step leaves the route instead (its Back target is the page stack's).
    val first = if (rerun) OnboardingStep.Source.ordinal else 0
    val steps = OnboardingStep.entries.subList(minOf(first, state.step.ordinal), state.step.ordinal + 1).map { state.copy(step = it) }
    NbStackHost(
        stack = steps,
        key = { it.step },
        onBack = { vm.back() },
        id = "onboarding",
        backEnabled = state.step != OnboardingStep.Welcome && state.step != OnboardingStep.Ready && !leaves,
    ) { shown ->
        OnboardingScreen(shown, layout, actions = actions, scroll = rememberNbScrollState())
    }
}

/** Glance "More" (`screens.md` 2): a glass sheet with labelled History and Settings entries. */
@Composable
private fun ModalScope.MoreSheet(onOpen: (TopLevel) -> Unit) {
    GlassSheet(stringResource(R.string.action_more)) {
        listOf(TopLevel.Home, TopLevel.History, TopLevel.Settings).forEach { tab ->
            SecondaryButton(stringResource(tab.label), onClick = { onOpen(tab) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
