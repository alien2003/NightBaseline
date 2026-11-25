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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.HistoryStrip
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace

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
    scrollState: ScrollState = rememberScrollState(),
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
 * The shell shared by Home and Night detail: [AppScaffold] with the glass top bar (large title collapsing on scroll in
 * Compact, 64 / 48 dp elsewhere, none in Glance). [content] receives the scaffold padding and the resting bar height.
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
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, Dp) -> Unit,
) {
    val density = LocalDensity.current
    val scrollDp = with(density) { scroll.value.toDp() }
    val large = largeTitle && layout.mode == LayoutMode.Compact
    val collapseRange = NbDimens.topBarLarge - NbDimens.topBarCollapsed
    val collapse = if (large) (scrollDp / collapseRange).coerceIn(0f, 1f) else 1f
    val scrolledUnder = if (large) (scrollDp - collapseRange).coerceAtLeast(0.dp) else scrollDp
    val statusBarPx =
        with(density) {
            WindowInsets.statusBars
                .asPaddingValues()
                .calculateTopPadding()
                .roundToPx()
        }
    val barHeight = topBarHeight(layout.mode, large)
    // The bar grows with the font scale (title and subtitle never clip), so the content starts below its measured
    // height at rest; while scrolling the measured value is kept so the content does not jump.
    var restingBarHeight by remember { mutableStateOf(0.dp) }
    val contentTop = maxOf(barHeight, restingBarHeight)
    AppScaffold(
        layout = layout,
        selected = selectedTab,
        onSelect = onSelectTab,
        modifier = modifier,
        topBar = { contentPadding ->
            if (layout.mode != LayoutMode.Glance) {
                Box(
                    Modifier
                        .testTag(SCREEN_TOP_BAR_TAG)
                        .padding(start = contentPadding.calculateStartPadding(LocalLayoutDirection.current))
                        .onSizeChanged { size ->
                            if (scroll.value == 0) {
                                restingBarHeight = with(density) { (size.height - statusBarPx).coerceAtLeast(0).toDp() }
                            }
                        },
                ) {
                    ShellTopBar(
                        title = title,
                        subtitle = subtitle,
                        large = large,
                        collapse = collapse,
                        height = barHeight,
                        scrolledUnder = scrolledUnder,
                        onBack = onBack,
                        actions = actions,
                    )
                }
            }
        },
    ) { padding -> content(padding, contentTop) }
}

/** Top bar height per mode (`layouts.md` 4.3): large title in Compact, 48 dp in short windows, 64 dp otherwise. */
private fun topBarHeight(
    mode: LayoutMode,
    large: Boolean,
): Dp =
    when {
        large -> NbDimens.topBarLarge
        mode == LayoutMode.Compact -> NbDimens.topBarCollapsed
        mode == LayoutMode.CompactShort || mode == LayoutMode.WideShort -> NbDimens.topBarShort
        else -> NbDimens.topBarMedium
    }

/** Space above the first block: status bar + top bar + 8 dp. */
@Composable
internal fun topInset(barHeight: Dp): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + barHeight + NbSpace.s3

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
    stripBesideSummary: Boolean = false,
) {
    if (blocks.night) {
        state.banner?.let {
            InlineBanner(
                it,
                state.source,
                Modifier.widthIn(max = maxWidth),
                onAction = { actions.onBannerAction(it) },
                onDismiss = { actions.onBannerDismiss(it) },
                quietDays = state.quietDays,
            )
        }
        if (stripBesideSummary && state.recent.isNotEmpty()) {
            Row(Modifier.widthIn(max = maxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
                NightBlockOf(state, actions, Modifier.weight(1f))
                HistoryStrip(
                    state.recent.takeLast(STRIP_WIDE),
                    onNight = { actions.onNight(it.wakeDate) },
                    onSeeAll = actions.onSeeAll,
                    individualTargets = stripTargets,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            NightBlockOf(state, actions, Modifier.widthIn(max = maxWidth))
        }
        LastResultBlock(state, maxWidth)
    }
    if (blocks.tiles) TileBlocks(state, actions, columns, maxWidth, selected)
    if (blocks.strip && !stripBesideSummary && state.recent.isNotEmpty()) {
        HistoryStrip(
            state.recent.takeLast(STRIP_NIGHTS),
            onNight = { actions.onNight(it.wakeDate) },
            onSeeAll = actions.onSeeAll,
            individualTargets = stripTargets,
            modifier = Modifier.widthIn(max = maxWidth),
        )
    }
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
    NightSummaryCard(metricsNight, state.source, Modifier.widthIn(max = maxWidth), showLabel = false)
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
) {
    NightBlock(
        state.night,
        state.source,
        modifier,
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(padding)
                .padding(horizontal = layout.gutter)
                .padding(top = topInset(barHeight), bottom = NbSpace.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            val maxWidth = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
            // 14 nights beside the summary when the content is at least 720 dp wide (screens.md 7.1, Medium).
            val beside = columns > 1 && width >= STRIP_BESIDE_MIN && state.night.state != NightState.Loading
            HomeBlocks(state, actions, columns, maxWidth, stripBesideSummary = beside)
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
    val top = topInset(barHeight)
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
                .verticalScroll(scroll)
                .padding(top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, actions, 1, NbDimens.textMax, selected = state.detailMetric, stripTargets = false)
        }
        Spacer(Modifier.width(spacer))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = if (supporting) 0.dp else endGutter, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            DetailPaneContent(state, actions)
        }
        if (supporting) {
            Spacer(Modifier.width(PANE_SPACER))
            Column(
                Modifier
                    .width(SUPPORTING_PANE)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = layout.gutter, top = top, bottom = bottom),
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
) {
    val metric = state.metricsNight?.metrics?.firstOrNull { it.metric == state.detailMetric }
    if (metric != null) {
        MetricDetailPane(
            metric,
            state.series[metric.metric],
            state.source,
            state.period,
            state.chartSelection,
            actions.onPeriod,
            actions.onChartSelect,
        )
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
    val top = topInset(barHeight)
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
                .verticalScroll(scroll)
                .padding(top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, actions, 1, NbDimens.textMax, Blocks(tiles = false, footer = false), stripTargets = false)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = top, bottom = bottom),
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
    Box(Modifier.fillMaxSize().padding(horizontal = layout.gutter)) {
        // Top half: a column that only scrolls when 200 % text does not fit; it holds no control.
        Column(
            Modifier
                .fillMaxWidth()
                .height(foldY - NbDimens.hingeClearance)
                .verticalScroll(rememberScrollState())
                .padding(top = topInset(barHeight), bottom = NbSpace.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (night.state == NightState.Loading) {
                LoadingCard()
            } else {
                NightSummaryCard(night, state.source, Modifier.widthIn(max = NbDimens.textMax))
            }
            if (night.state == NightState.Learning || night.state == NightState.LearningStart) {
                LearningProgress(
                    night.historyNights,
                    Modifier.widthIn(max = NbDimens.textMax),
                    skipped = if (state.lastNightShort) 1 else 0,
                )
            }
            if (state.recent.isNotEmpty()) {
                HistoryStrip(
                    state.recent.takeLast(STRIP_NIGHTS),
                    onNight = {},
                    onSeeAll = {},
                    interactive = false,
                    modifier = Modifier.widthIn(max = NbDimens.textMax),
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .offset(y = foldY + NbDimens.hingeClearance)
                .padding(bottom = foldY + NbDimens.hingeClearance)
                .verticalScroll(scroll)
                .padding(bottom = padding.calculateBottomPadding() + NbSpace.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            // Everything below the fold aligns with the tile grid.
            val columns = tileColumns(contentWidth(layout.widthDp.dp, padding, layout.gutter))
            val maxWidth = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
            state.banner?.let {
                InlineBanner(
                    it,
                    state.source,
                    Modifier.widthIn(max = maxWidth),
                    onAction = { actions.onBannerAction(it) },
                    onDismiss = { actions.onBannerDismiss(it) },
                    quietDays = state.quietDays,
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
                NbTextButton(stringResource(R.string.action_see_all), onClick = actions.onSeeAll)
            }
            DisclaimerFooter(onAbout = actions.onAbout)
        }
    }
}

/** Nights in the compact strip. */
internal const val STRIP_NIGHTS = 7

/** Nights in the wide strip and the supporting pane. */
internal const val STRIP_WIDE = 14

private const val LOADING_TILES = 4
private val STRIP_BESIDE_MIN = 720.dp
private val LIST_PANE_MAX = 400.dp
private val LIST_PANE_LARGE = 360.dp
private val DETAIL_PANE_MIN = 360.dp
private val SUPPORTING_PANE = 320.dp
private val PANE_SPACER = 24.dp
private val WIDE_SHORT_START = 360.dp
