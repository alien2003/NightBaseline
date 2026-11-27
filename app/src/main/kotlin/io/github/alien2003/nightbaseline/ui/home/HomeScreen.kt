package io.github.alien2003.nightbaseline.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.Sensitivity
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.HistoryStrip
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.LocalManualCheck
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TitleColumn
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.components.withEnd
import io.github.alien2003.nightbaseline.ui.history.CompactNightSummary
import io.github.alien2003.nightbaseline.ui.interaction.NbReveal
import io.github.alien2003.nightbaseline.ui.interaction.NbSwitcher
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailActions
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailPane
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.shell.CountComposition
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.shellToolbarHeight

/** Test tag of the top bar of Home and Night detail (the screenshot tests stitch scrolled captures below it). */
const val SCREEN_TOP_BAR_TAG: String = "screen-top-bar"

/**
 * Home ("Last night", `screens.md` 7.1, `layouts.md` 5.1): a stateless function of [state] and [actions]. Large-title
 * top bar with the night label, the banner slot, the summary / learning / state card (with "Last result" below
 * data-access and sync states), metric tiles in registry order, the "Not recorded by" row, the history strip and the
 * disclaimer. The layout follows [layout]: one column (Compact, CompactShort), a two-column grid (Medium and
 * single-pane wide windows), list-detail with the selected metric (Expanded, Book split at the fold) plus the recent
 * nights pane (Large), two side-by-side columns (WideShort), view-only top half and controls below the fold
 * (Tabletop) and the glance list (flip cover screens).
 *
 * @param scrollState scroll of the main column (the list pane in two-pane layouts); hoisted for state restoration
 *   and the screenshot tests.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: HomeActions = HomeActions(),
    onSelectTab: (TopLevel) -> Unit = actions.onSelectTab,
    debugAction: (@Composable () -> Unit)? = null,
    scrollState: ScrollState = rememberNbScrollState(),
) {
    CountComposition("HomeScreen")
    CompositionLocalProvider(LocalManualCheck provides state.check) {
        HomeContent(state, layout, modifier, actions, onSelectTab, debugAction, scrollState)
    }
}

@Suppress("LongParameterList") // HomeScreen's parameters, passed on below the check-state provider.
@Composable
private fun HomeContent(
    state: HomeState,
    layout: LayoutSpec,
    modifier: Modifier,
    actions: HomeActions,
    onSelectTab: (TopLevel) -> Unit,
    debugAction: (@Composable () -> Unit)?,
    scrollState: ScrollState,
) {
    val text = rememberUiText()
    // The summary card carries the night label itself; the bar repeats it only above learning and state cards.
    val noSubtitle = state.night.state.hasSummary || state.night.state == NightState.HealthConnectNone
    val subtitle = if (noSubtitle) null else text.nightLabel(state.night)
    ScreenFrame(
        layout = layout,
        title = stringResource(R.string.home_title),
        subtitle = subtitle,
        scroll = scrollState,
        selectedTab = TopLevel.Home,
        onSelectTab = onSelectTab,
        modifier = modifier,
        column = homeTitleColumn(layout),
        actions = { debugAction?.invoke() },
    ) { padding, contentTop ->
        val tabletopFold = layout.horizontalFold
        when {
            layout.mode == LayoutMode.Glance -> {
                GlanceHome(
                    state.night,
                    state.metricsNight,
                    state.source,
                    state.banner,
                    state.quietDays,
                    padding,
                    layout.widthDp,
                    actions,
                    scroll = scrollState,
                )
            }

            layout.mode == LayoutMode.Tabletop && tabletopFold != null -> {
                TabletopHome(state, layout, padding, contentTop, tabletopFold.positionDp.dp, actions, scrollState)
            }

            layout.twoPanes -> {
                TwoPaneHome(state, layout, padding, contentTop, actions, scrollState)
            }

            layout.mode == LayoutMode.WideShort -> {
                WideShortHome(state, layout, padding, contentTop, actions, scrollState)
            }

            else -> {
                SinglePaneHome(state, layout, padding, contentTop, actions, scrollState)
            }
        }
    }
}

/**
 * The shell shared by Home and Night detail: [AppScaffold] with the shell top bar (`coloros-conformance.md` C1–C7):
 * in Compact with [largeTitle] the large title sits below the 52 dp toolbar and scrolls away with the content, the
 * small title fading into the toolbar; 48 dp toolbar in short windows; none in Glance. Pushed pages ([onBack]) get the
 * round back button and hide the bottom bar in Compact. [content] receives the scaffold padding and the resting bar
 * height.
 */
@Composable
internal fun ScreenFrame(
    layout: LayoutSpec,
    title: String,
    subtitle: String?,
    scroll: ScrollState,
    selectedTab: TopLevel,
    onSelectTab: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    onBack: (() -> Unit)? = null,
    column: TitleColumn? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, Dp) -> Unit,
) {
    val density = LocalDensity.current
    val large = largeTitle && layout.mode == LayoutMode.Compact
    val short = layout.mode == LayoutMode.CompactShort || layout.mode == LayoutMode.WideShort
    // Read when the bar reports its size, never in composition: on a cold start the insets arrive after the first
    // composition, and a status bar of 0 subtracted from a measured bar that already includes it is one frame wrong.
    val statusBars = WindowInsets.statusBars
    val barHeight = shellToolbarHeight(short, large)
    // The bar grows with the font scale (title and subtitle never clip), so the content starts below its measured
    // height at rest; while scrolling the measured value is kept so the content does not jump.
    var restingBarHeight by remember { mutableStateOf(0.dp) }
    val contentTop = if (restingBarHeight > 0.dp) restingBarHeight else barHeight
    AppScaffold(
        layout = layout,
        selected = selectedTab,
        onSelect = onSelectTab,
        modifier = modifier,
        showBar = onBack == null,
        topBar = { contentPadding ->
            if (layout.mode != LayoutMode.Glance) {
                Box(
                    Modifier
                        .testTag(SCREEN_TOP_BAR_TAG)
                        .padding(start = contentPadding.calculateStartPadding(LocalLayoutDirection.current))
                        .onSizeChanged { size ->
                            if (scroll.value == 0) {
                                val statusBarPx = statusBars.getTop(density)
                                restingBarHeight = with(density) { (size.height - statusBarPx).coerceAtLeast(0).toDp() }
                            }
                        },
                ) {
                    ShellTopBar(
                        title = title,
                        subtitle = subtitle,
                        large = large,
                        height = barHeight,
                        scrollOffset = { with(density) { scroll.value.toDp() } },
                        onBack = onBack,
                        column = column?.withEnd(contentPadding.calculateEndPadding(LocalLayoutDirection.current)),
                        actions = actions,
                    )
                }
            }
        },
    ) { padding -> content(padding, contentTop) }
}

/**
 * The card column of Home for the title (review round 4 container rule): the list pane of two-pane layouts, the start
 * column of WideShort, otherwise the centred single column whose cap widens with the two tile columns.
 */
internal fun homeTitleColumn(layout: LayoutSpec): TitleColumn =
    if (layout.twoPanes || layout.mode == LayoutMode.WideShort || layout.mode == LayoutMode.Book) {
        TitleColumn.atStart(layout.gutter)
    } else {
        tileTitleColumn(layout.gutter)
    }

/** A centred column capped at `textMax`, or at `chartMax` once two tile columns fit ([tileColumns]). */
internal fun tileTitleColumn(gutter: Dp): TitleColumn =
    TitleColumn(
        gutter = gutter,
        maxWidth = NbDimens.textMax,
        wideMax = NbDimens.chartMax,
        wideFrom = TileMinWidth * 2 + NbDimens.cardGap,
    )

/**
 * Space above the first block: status bar + top bar + 8 dp, as a value read in composition. Prefer [belowTopBar],
 * which applies the status bar when the content is laid out (no jump when the insets arrive after the first frame).
 */
@Composable
internal fun topInset(barHeight: Dp): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + barHeight + NbSpace.s3

/**
 * Pads the first block below the top bar: the status bar inset (applied at layout time, like the bar's own spacer, so
 * a cold start never draws the content one status bar too high), the bar's resting height [barHeight] and 8 dp, plus
 * [bottom] below the last block.
 */
@Composable
internal fun Modifier.belowTopBar(
    barHeight: Dp,
    bottom: Dp,
): Modifier =
    windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        .padding(top = barHeight + NbSpace.s3, bottom = bottom)

/** Width left for content inside [padding] and the gutters. */
@Composable
internal fun contentWidth(
    total: Dp,
    padding: PaddingValues,
    gutter: Dp,
): Dp {
    val direction = LocalLayoutDirection.current
    return total - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction) - gutter * 2
}

/** Which optional blocks a column shows. */
private data class Blocks(
    val night: Boolean = true,
    val tiles: Boolean = true,
    val strip: Boolean = true,
    val footer: Boolean = true,
)

/**
 * The blocks of Home in their fixed order (`screens.md` 7.1, 7.6): banner, night block, "Last result" heading and
 * card, "Already visible" heading (learning), tiles, "Not recorded by" row, "What to expect" (learning), strip,
 * footer.
 */
@Composable
private fun ColumnScope.HomeBlocks(
    state: HomeState,
    actions: HomeActions,
    columns: Int,
    maxWidth: Dp,
    blocks: Blocks = Blocks(),
    selected: MetricId? = null,
    stripTargets: Boolean = true,
    stripBelowSummary: Int? = null,
) {
    val strip: @Composable (Int) -> Unit = { count ->
        HistoryStrip(
            state.recent.takeLast(count),
            onNight = { actions.onNight(it.wakeDate) },
            onSeeAll = actions.onSeeAll,
            individualTargets = stripTargets,
            modifier = Modifier.widthIn(max = maxWidth),
        )
    }
    if (blocks.night) {
        NbReveal(state.banner) {
            InlineBanner(
                it,
                state.source,
                Modifier.widthIn(max = maxWidth),
                onAction = { actions.onBannerAction(it) },
                onDismiss = { actions.onBannerDismiss(it) },
                quietDays = state.quietDays,
                maxWidth = maxWidth,
            )
        }
        NightBlockOf(state, actions, Modifier.widthIn(max = maxWidth), maxWidth)
        // Multi-column Home: the strip is a full-width card right below the summary (review round 4).
        if (stripBelowSummary != null && state.recent.isNotEmpty()) strip(stripBelowSummary)
        LastResultBlock(state, maxWidth)
    }
    if (blocks.tiles) TileBlocks(state, actions, columns, maxWidth, selected)
    if (blocks.strip && stripBelowSummary == null && state.recent.isNotEmpty()) strip(STRIP_NIGHTS)
    if (blocks.footer) DisclaimerFooter(onAbout = actions.onAbout)
}

/** "Last result: {night label}" and the stored night's summary below a data-access or sync state card. */
@Composable
private fun LastResultBlock(
    state: HomeState,
    maxWidth: Dp,
) {
    val metricsNight = state.metricsNight
    if (state.night.state !in HomeState.LAST_RESULT_STATES || metricsNight == null) return
    val text = rememberUiText()
    BlockHeading(text.string(R.string.home_last_result_heading, text.nightLabel(metricsNight)), Modifier.widthIn(max = maxWidth))
    // The heading above already names the night.
    NightSummaryCard(metricsNight, state.source, Modifier.widthIn(max = maxWidth), showLabel = false, maxWidth = maxWidth)
}

/** Skeleton tiles (loading), "Already visible" (learning), the tiles, the "Not recorded by" row, "What to expect". */
@Composable
private fun TileBlocks(
    state: HomeState,
    actions: HomeActions,
    columns: Int,
    maxWidth: Dp,
    selected: MetricId?,
) {
    val night = state.night
    val metricsNight = state.metricsNight
    if (night.state == NightState.Loading) SkeletonTiles(LOADING_TILES, columns, maxWidth)
    if (metricsNight != null) {
        if (night.state ==
            NightState.Learning
        ) {
            BlockHeading(stringResource(R.string.home_already_visible), Modifier.widthIn(max = maxWidth))
        }
        MetricGrid(metricsNight.metrics, state.source, columns, maxWidth, actions.onMetric, selected)
        NotRecordedRow(metricsNight.notRecorded, state.source, actions.onNotRecordedInfo, Modifier.widthIn(max = maxWidth))
    }
    if (night.state == NightState.Learning || night.state == NightState.LearningStart) ExpectCard(Modifier.widthIn(max = maxWidth))
}

@Composable
private fun NightBlockOf(
    state: HomeState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    maxWidth: Dp = NbDimens.textMax,
) {
    NightBlock(
        state.night,
        state.source,
        modifier,
        maxWidth = maxWidth,
        lastNightShort = state.lastNightShort,
        onMetric = actions.onMetric,
        onStateAction = actions.onStateAction,
        onStateSecondary = actions.onStateSecondary,
        onHowItWorks = actions.onHowItWorks,
    )
}

@Composable
private fun SinglePaneHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    actions: HomeActions,
    scroll: ScrollState,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = contentWidth(maxWidth, padding, layout.gutter)
        val columns = tileColumns(width)
        val cap = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
        // Multi-column: the strip follows the summary across the full width, 14 nights when 14 targets fit.
        val stripNights = if (minOf(width, cap) >= STRIP_WIDE_MIN) STRIP_WIDE else STRIP_NIGHTS
        val below = stripNights.takeIf { columns > 1 && state.night.state != NightState.Loading }
        Column(
            Modifier
                .fillMaxSize()
                .nbVerticalScroll(scroll, id = "home-1")
                .padding(padding)
                .padding(horizontal = layout.gutter)
                .belowTopBar(barHeight, bottom = NbSpace.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // One container for every block (review round 4): same width cap, same start edge, centred as a whole.
            Column(
                Modifier.widthIn(max = cap).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
            ) {
                HomeBlocks(state, actions, columns, cap, stripBelowSummary = below)
            }
        }
    }
}

@Composable
private fun TwoPaneHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    actions: HomeActions,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    val fold = layout.verticalFold
    val start = padding.calculateStartPadding(direction)
    val end = padding.calculateEndPadding(direction)
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    val available = layout.widthDp.dp - start - end - layout.gutter * 2
    val supporting =
        fold == null &&
            (layout.mode == LayoutMode.Large || layout.mode == LayoutMode.ExtraLarge) &&
            available - LIST_PANE_LARGE - SUPPORTING_PANE - PANE_SPACER * 2 >= DETAIL_PANE_MIN
    // Book: split exactly at the fold, 12 dp clear of the hinge on each side; otherwise a 320-400 dp list pane.
    val listWidth =
        when {
            fold != null -> (fold.positionDp - fold.occludedDp / 2).dp - start - layout.gutter - NbDimens.hingeClearance
            supporting -> LIST_PANE_LARGE
            else -> LIST_PANE_MAX
        }
    val spacer = if (fold != null) fold.occludedDp.dp + NbDimens.hingeClearance * 2 else PANE_SPACER
    val endGutter = if (fold != null) NbSpace.s5 else layout.gutter
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = start, end = end),
    ) {
        Column(
            Modifier
                .padding(start = layout.gutter)
                .width(listWidth)
                .fillMaxHeight()
                .nbVerticalScroll(scroll, id = "home-2")
                .belowTopBar(barHeight, bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, actions, 1, NbDimens.textMax, selected = state.detailMetric, stripTargets = false)
        }
        Spacer(Modifier.width(spacer))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .nbVerticalScroll(rememberPaneScrollState(), id = "home-3")
                .padding(end = if (supporting) 0.dp else endGutter)
                .belowTopBar(barHeight, bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            // Another metric in the detail pane is an unordered peer of the shown one: the Swap kind (ADR 0011).
            NbSwitcher(state, key = { it.detailMetric ?: Unit }, id = "home-detail") { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) { DetailPaneContent(shown, actions, layout) }
            }
        }
        if (supporting) {
            Spacer(Modifier.width(PANE_SPACER))
            Column(
                Modifier
                    .width(SUPPORTING_PANE)
                    .fillMaxHeight()
                    .nbVerticalScroll(rememberPaneScrollState(), id = "home-4")
                    .padding(end = layout.gutter)
                    .belowTopBar(barHeight, bottom),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
            ) {
                RecentNightsPane(state.recent.takeLast(STRIP_WIDE), actions.onNight)
            }
        }
    }
}

/**
 * The detail pane of two-pane Home: Metric detail of the selected metric, or (when the night has no metric to show)
 * the longer explanation of the state (`layouts.md` 5.1 "States on Home in every mode").
 */
@Composable
private fun DetailPaneContent(
    state: HomeState,
    actions: HomeActions,
    layout: LayoutSpec,
) {
    val night = state.metricsNight
    val metric = night?.metrics?.firstOrNull { it.metric == state.detailMetric }
    if (metric != null) {
        val series = state.series[metric.metric]?.takeIf { s -> s.points.any { it.value != null } }
        val detail =
            MetricDetailState(
                metric = metric,
                anchorDate = night.wakeDate,
                series = series,
                source = state.source,
                period = state.period,
                selected = state.chartSelection,
                countedSide = AlgoParams().metrics.firstOrNull { it.id == metric.metric }?.sensitivity ?: Sensitivity.BOTH,
                storedNights = series?.let { s -> s.points.size - s.points.indexOfFirst { it.value != null } } ?: 0,
            )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            MetricDetailPane(
                detail,
                layout,
                paneWidth = maxWidth,
                actions =
                    MetricDetailActions(
                        onPeriod = actions.onPeriod,
                        onSelect = actions.onChartSelect,
                        onOpenNight = actions.onNight,
                        onOpenExplainer = actions.onHowItWorks,
                        onAbout = actions.onAbout,
                    ),
            )
        }
    } else {
        ExplainerCard(meaning = state.night.state.hasSummary)
    }
}

@Composable
private fun WideShortHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    actions: HomeActions,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    Row(
        Modifier
            .fillMaxSize()
            .padding(
                start = padding.calculateStartPadding(direction) + layout.gutter,
                end =
                    padding.calculateEndPadding(direction) + layout.gutter,
            ),
        horizontalArrangement = Arrangement.spacedBy(PANE_SPACER),
    ) {
        Column(
            Modifier
                .width(WIDE_SHORT_START)
                .fillMaxHeight()
                .nbVerticalScroll(scroll, id = "home-5")
                .belowTopBar(barHeight, bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, actions, 1, NbDimens.textMax, Blocks(tiles = false, footer = false), stripTargets = false)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .nbVerticalScroll(rememberPaneScrollState(), id = "home-6")
                .belowTopBar(barHeight, bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, actions, 1, NbDimens.textMax, Blocks(night = false, strip = false))
        }
    }
}

/**
 * Tabletop (`layouts.md` 5.1, 6.4): the top half is view only (night summary without its action, learning progress,
 * the last nights as a glyph row); the fold plus 12 dp on each side stays empty; the bottom half scrolls and holds the
 * banner, the state's actions, the tiles, "See all" and the footer above the bar.
 */
@Composable
private fun TabletopHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    foldY: Dp,
    actions: HomeActions,
    scroll: ScrollState,
) {
    val night = state.night
    val text = rememberUiText()
    // Both halves share one container: the width of the tile grid below the fold (review round 4).
    val columns = tileColumns(contentWidth(layout.widthDp.dp, padding, layout.gutter))
    val maxWidth = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
    Box(Modifier.fillMaxSize().padding(horizontal = layout.gutter)) {
        // Top half: view only and never scrolls (layouts.md 6.4); at large font it keeps the compact summary only.
        val large = LocalDensity.current.fontScale >= TABLETOP_COMPACT_FONT
        Box(
            Modifier
                .fillMaxWidth()
                .height(foldY - NbDimens.hingeClearance)
                .clipToBounds()
                .belowTopBar(barHeight, bottom = NbSpace.s3),
        ) {
            FitColumn(NbDimens.cardGap) {
                when {
                    night.state == NightState.Loading -> LoadingCard()
                    large -> CompactNightSummary(night, state.source, maxWidth = maxWidth)
                    else -> NightSummaryCard(night, state.source, maxWidth = maxWidth)
                }
                if (night.state == NightState.Learning || night.state == NightState.LearningStart) {
                    LearningProgress(
                        night.collectedNights,
                        Modifier.widthIn(max = maxWidth),
                        skipped = if (state.lastNightShort) 1 else 0,
                    )
                }
                if (state.recent.isNotEmpty() && !large) {
                    HistoryStrip(
                        state.recent.takeLast(STRIP_NIGHTS),
                        onNight = {},
                        onSeeAll = {},
                        interactive = false,
                        modifier = Modifier.widthIn(max = maxWidth),
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .offset(y = foldY + NbDimens.hingeClearance)
                .padding(bottom = foldY + NbDimens.hingeClearance)
                .nbVerticalScroll(scroll, id = "home-7")
                .padding(bottom = padding.calculateBottomPadding() + NbSpace.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = maxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
            ) { TabletopControls(state, actions, columns, maxWidth) }
        }
    }
}

/** The bottom half of tabletop Home: everything below the fold aligns with the tile grid. */
@Composable
private fun ColumnScope.TabletopControls(
    state: HomeState,
    actions: HomeActions,
    columns: Int,
    maxWidth: Dp,
) {
    val night = state.night
    NbReveal(state.banner) {
        InlineBanner(
            it,
            state.source,
            Modifier.widthIn(max = maxWidth),
            onAction = { actions.onBannerAction(it) },
            onDismiss = { actions.onBannerDismiss(it) },
            quietDays = state.quietDays,
            maxWidth = maxWidth,
        )
    }
    NightActionsRow(
        night,
        state.source,
        actions.onMetric,
        actions.onStateAction,
        actions.onStateSecondary,
        actions.onHowItWorks,
        Modifier.widthIn(max = maxWidth),
    )
    LastResultBlock(state, maxWidth)
    HomeBlocks(state, actions, columns, maxWidth, Blocks(night = false, strip = false, footer = false))
    if (state.recent.isNotEmpty()) {
        NbTextButton(
            stringResource(R.string.action_see_all),
            onClick = actions.onSeeAll,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    DisclaimerFooter(onAbout = actions.onAbout)
}

/**
 * A view-only column for the tabletop top half (`layouts.md` 6.4): children in order, centred, each placed only if it
 * fits completely in the height left (later ones are dropped, never cut), so the half never scrolls or clips text.
 */
@Composable
internal fun FitColumn(
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val placed = mutableListOf<Placeable>()
        var used = 0
        for (m in measurables) {
            val p = m.measure(loose)
            val need = used + (if (placed.isEmpty()) 0 else gap) + p.height
            if (need > constraints.maxHeight && placed.isNotEmpty()) break
            placed += p
            used = need
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = 0
            placed.forEach {
                it.placeRelative((constraints.maxWidth - it.width) / 2, y)
                y += it.height + gap
            }
        }
    }
}

/** From this font scale the tabletop top half shows the compact summary only (it never scrolls). */
internal const val TABLETOP_COMPACT_FONT = 1.5f

/** Nights in the compact strip. */
internal const val STRIP_NIGHTS = 7

/** Nights in the wide strip and the supporting pane. */
internal const val STRIP_WIDE = 14

private const val LOADING_TILES = 4

/** 14 night targets of 48 dp plus the strip card's 12 dp side padding. */
private val STRIP_WIDE_MIN = NbDimens.targetMin * STRIP_WIDE + NbSpace.s3 * 2
private val LIST_PANE_MAX = 400.dp
private val LIST_PANE_LARGE = 360.dp
private val DETAIL_PANE_MIN = 360.dp
private val SUPPORTING_PANE = 320.dp
private val PANE_SPACER = 24.dp
private val WIDE_SHORT_START = 360.dp
