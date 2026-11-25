package io.github.alien2003.nightbaseline.ui.metric

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.DASH
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.GlassSheet
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.OverlayHost
import io.github.alien2003.nightbaseline.ui.components.PeriodSelector
import io.github.alien2003.nightbaseline.ui.components.RangeBand
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TonalButton
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.chartPoint
import io.github.alien2003.nightbaseline.ui.components.chartSummary
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/** Test tag of the top bar (screenshot stitching keeps it once at the top). */
const val METRIC_TOP_BAR_TAG: String = "metric_top_bar"

/**
 * Metric detail (`screens.md` 7.3, `layouts.md` 5.2): how one measurement behaves over time, where its usual range
 * comes from and what moving outside it means. Stateless: [state] in, intents out through [actions].
 *
 * Layout per [layout] mode: Compact and CompactShort a single column; Medium, Expanded, Large and ExtraLarge (when
 * the screen stands alone) the header beside the stats, the chart across the content width (max 840 dp) and the
 * summary beside the explainer cards; WideShort the chart at the start and a scrolling column at the end; Tabletop
 * the chart (view only) above the fold and every control and explanation below it; Book the header, period and chart
 * in the start pane and the explanations in the end pane; Glance a header line, the 7-night glyph row and the text
 * summary. The range explainer opens as a glass sheet (Compact, CompactShort, Tabletop lower half, Glance) or a glass
 * dialog (Medium and up; Book: in the end pane).
 *
 * @param scroll the main scrolling column (the end pane in Book, the lower half in Tabletop).
 */
@Composable
fun MetricDetailScreen(
    state: MetricDetailState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: MetricDetailActions = MetricDetailActions(),
    scroll: ScrollState = rememberScrollState(),
    onSelectTab: (TopLevel) -> Unit = {},
) {
    val text = rememberUiText()
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(topBarHeight(layout.mode)) }
    val showTopBar = layout.mode != LayoutMode.Glance && layout.mode != LayoutMode.Tabletop
    AppScaffold(
        layout = layout,
        selected = TopLevel.Home,
        onSelect = onSelectTab,
        modifier = modifier,
        topBar = { padding ->
            if (showTopBar) {
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Box(
                    Modifier
                        .padding(start = padding.calculateStartPadding(LocalLayoutDirection.current))
                        .testTag(METRIC_TOP_BAR_TAG)
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } - statusBar },
                ) {
                    ShellTopBar(
                        title = text.name(state.metric.metric),
                        height = topBarHeight(layout.mode),
                        scrolledUnder = with(density) { scroll.value.toDp() },
                        onBack = actions.onBack,
                        actions = {
                            NbIconButton(
                                NbIcons.Info,
                                text.string(R.string.a11y_range_info),
                                onClick = actions.onOpenExplainer,
                                tint = NbTheme.colors.onGlassPrimary,
                                onGlass = true,
                            )
                        },
                    )
                }
            }
        },
        overlay = { if (state.explainerOpen) ExplainerOverlay(state, layout, actions) },
    ) { padding ->
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + barHeight + NbSpace.s3
        when (layout.mode) {
            LayoutMode.Glance -> GlanceDetail(state, actions, padding, scroll)
            LayoutMode.Tabletop -> TabletopDetail(state, layout, actions, padding, scroll)
            LayoutMode.Book -> BookDetail(state, layout, actions, padding, top, scroll)
            LayoutMode.WideShort -> WideShortDetail(state, layout, actions, padding, top, scroll)
            LayoutMode.Compact, LayoutMode.CompactShort -> SingleColumn(state, layout, actions, padding, top, scroll)
            else -> WideColumn(state, layout, actions, padding, top, scroll)
        }
    }
}

/** Top bar height per mode (`layouts.md` 4.3): 56 dp Compact, 48 dp short windows, 64 dp otherwise. */
private fun topBarHeight(mode: LayoutMode): Dp =
    when (mode) {
        LayoutMode.Compact -> NbDimens.topBarCollapsed
        LayoutMode.CompactShort, LayoutMode.WideShort -> NbDimens.topBarShort
        else -> NbDimens.topBarMedium
    }

/**
 * Chart height (`layouts.md` 5.2): 220 dp Compact, 240 dp Medium, 280 dp Expanded and up, +40 dp when the window is
 * at least 900 dp tall, never taller than 45 % of the window.
 */
internal fun chartHeight(layout: LayoutSpec): Dp {
    val base =
        when (layout.mode) {
            LayoutMode.Glance, LayoutMode.Compact, LayoutMode.CompactShort, LayoutMode.Book, LayoutMode.Tabletop -> 220.dp
            LayoutMode.Medium, LayoutMode.WideShort -> 240.dp
            LayoutMode.Expanded, LayoutMode.Large, LayoutMode.ExtraLarge -> 280.dp
        }
    val tall = if (layout.heightDp >= TALL_WINDOW) base + 40.dp else base
    return minOf(tall, (layout.heightDp * MAX_CHART_SHARE).dp)
}

private const val TALL_WINDOW = 900f
private const val MAX_CHART_SHARE = 0.45f

/** Chart period selector, hidden while nothing can be charted (L0, loading, not recorded and nothing stored). */
@Composable
private fun Period(
    state: MetricDetailState,
    actions: MetricDetailActions,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    if (state.loading || state.series == null || state.storedNights == 0) return
    PeriodSelector(state.period, onSelect = actions.onPeriod, modifier = modifier, fill = fill)
}

@Composable
private fun SingleColumn(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
    padding: PaddingValues,
    top: Dp,
    scroll: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = layout.gutter)
            .padding(top = top, bottom = NbSpace.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        SingleColumnBlocks(state, actions, chartHeight(layout))
    }
}

@Composable
private fun ColumnScope.SingleColumnBlocks(
    state: MetricDetailState,
    actions: MetricDetailActions,
    chartHeight: Dp,
    footer: Boolean = true,
) {
    if (state.loading) {
        LoadingBlocks(chartHeight)
        return
    }
    HeaderCard(state, actions)
    NoticeCard(state, actions)
    Period(state, actions, Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax), fill = true)
    ChartBlock(state, actions, chartHeight)
    Column(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth()) { StatsBlock(state) }
    ComputedCard(state)
    MeaningCard()
    SourceLine(state)
    if (footer) DisclaimerFooter(onAbout = actions.onAbout, modifier = Modifier.widthIn(max = NbDimens.textMax))
}

/**
 * Metric detail as the detail pane of another screen (two-pane Home, `layouts.md` 5.1 and 5.2): the same blocks as
 * the standalone screen without its scaffold, top bar or scroll container (the caller's pane scrolls). The pane uses
 * the Medium arrangement from [WIDE_PANE] wide, else the single column. The explainer is the caller's overlay.
 */
@Composable
fun MetricDetailPane(
    state: MetricDetailState,
    layout: LayoutSpec,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
    actions: MetricDetailActions = MetricDetailActions(),
) {
    val text = rememberUiText()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
        Row(Modifier.widthIn(max = NbDimens.chartMax).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.name(state.metric.metric),
                style = NbTheme.typography.title,
                color = NbTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            NbIconButton(NbIcons.Info, text.string(R.string.a11y_range_info), onClick = actions.onOpenExplainer)
        }
        if (paneWidth >= WIDE_PANE) {
            Column(
                Modifier.widthIn(max = NbDimens.chartMax).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(NbDimens.groupGap),
            ) {
                WideColumnBlocks(state, actions, chartHeight(layout), footer = false)
            }
        } else {
            SingleColumnBlocks(state, actions, chartHeight(layout), footer = false)
        }
    }
}

/** From this pane width Metric detail uses the Medium arrangement (`layouts.md` 5.2). */
val WIDE_PANE: Dp = 640.dp

/** Medium and up (standalone): header beside stats, full-width chart, summary beside the explainer cards. */
@Composable
private fun WideColumn(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
    padding: PaddingValues,
    top: Dp,
    scroll: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = layout.gutter)
            .padding(top = top, bottom = NbSpace.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = NbDimens.chartMax).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NbDimens.groupGap),
        ) {
            WideColumnBlocks(state, actions, chartHeight(layout))
        }
    }
}

@Composable
private fun ColumnScope.WideColumnBlocks(
    state: MetricDetailState,
    actions: MetricDetailActions,
    chartHeight: Dp,
    footer: Boolean = true,
) {
    if (state.loading) {
        LoadingBlocks(chartHeight)
        return
    }
    val hasStats = state.shownPoints.any { it.state.compared }
    val hasCard = state.notice != null && state.notice != MetricNotice.WaitingSync
    if (hasStats || hasCard) {
        Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.groupGap)) {
            HeaderCard(state, actions, Modifier.weight(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
                NoticeCard(state, actions)
                StatsBlock(state)
            }
        }
    } else {
        // Nothing to put beside the header (learning, nothing compared): it takes the text column alone.
        HeaderCard(state, actions)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Period(state, actions) }
    ChartBlock(state, actions, chartHeight, showSummary = false)
    Row(horizontalArrangement = Arrangement.spacedBy(NbDimens.groupGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            SummaryText(state)
            SourceLine(state)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            ComputedCard(state)
            MeaningCard()
        }
    }
    if (footer) DisclaimerFooter(onAbout = actions.onAbout)
}

/** Phone landscape: the chart at the start (60 %, full height), a scrolling column at the end. */
@Composable
private fun WideShortDetail(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
    padding: PaddingValues,
    top: Dp,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction))
            .padding(horizontal = layout.gutter),
        horizontalArrangement = Arrangement.spacedBy(NbDimens.groupGap),
    ) {
        BoxWithConstraints(
            Modifier
                .weight(WIDE_SHORT_CHART_SHARE)
                .fillMaxHeight()
                .padding(top = top, bottom = NbSpace.s5),
        ) {
            ChartBlock(state, actions, (maxHeight - CHART_CARD_CHROME).coerceAtLeast(MIN_CHART), showSummary = false)
        }
        Column(
            Modifier
                .weight(1f - WIDE_SHORT_CHART_SHARE)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(top = top, bottom = NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HeaderCard(state, actions)
            NoticeCard(state, actions)
            Period(state, actions)
            SummaryText(state)
            StatsBlock(state)
            ComputedCard(state)
            MeaningCard()
            SourceLine(state)
            DisclaimerFooter(onAbout = actions.onAbout)
        }
    }
}

private const val WIDE_SHORT_CHART_SHARE = 0.6f

/** Card padding plus the date axis allowance around the plot. */
private val CHART_CARD_CHROME: Dp = 24.dp
private val MIN_CHART: Dp = 120.dp

/**
 * Tabletop (`layouts.md` 5.2): above the fold the metric name, last night's value and status as a large label and the
 * chart (view only, nothing interactive); below it back, the period selector, Previous / Next night step buttons,
 * the summary, stats, explainers and the bar. Only the lower half scrolls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletopDetail(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val text = rememberUiText()
    val fold = layout.horizontalFold?.positionDp?.dp ?: (layout.heightDp / 2).dp
    val hinge = (layout.horizontalFold?.occludedDp ?: 0f).dp
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val direction = LocalLayoutDirection.current
    val sides =
        Modifier
            .padding(start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction))
            .padding(horizontal = layout.gutter)
    Column(Modifier.fillMaxSize()) {
        Column(
            sides
                .fillMaxWidth()
                .height(fold - hinge / 2 - NbDimens.hingeClearance)
                .padding(top = statusBar + NbSpace.s3),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            TabletopLabel(state)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (state.loading) {
                    LoadingBlocks(MIN_CHART)
                } else if (state.notProvidedNone) {
                    EmptyChart(
                        text.string(R.string.detail_not_provided_title, state.source, text.name(state.metric.metric)),
                        (maxHeight - CHART_CARD_CHROME).coerceAtLeast(MIN_CHART),
                    )
                } else {
                    ChartBlock(
                        state,
                        actions,
                        (maxHeight - CHART_CARD_CHROME).coerceAtLeast(MIN_CHART),
                        interactive = false,
                        showSummary = false,
                        showNotes = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(hinge + NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .then(sides)
                .padding(bottom = padding.calculateBottomPadding() + NbSpace.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = NbDimens.chartMax), verticalAlignment = Alignment.CenterVertically) {
                NbIconButton(NbIcons.Back, text.string(R.string.a11y_back), onClick = actions.onBack)
                Box(Modifier.weight(1f).padding(horizontal = NbSpace.s3)) { Period(state, actions) }
                NbIconButton(NbIcons.Info, text.string(R.string.a11y_range_info), onClick = actions.onOpenExplainer)
            }
            if (state.hasChart && !state.loading) {
                val last = state.shownPoints.lastIndex
                val current = state.selected ?: last
                FlowRow(
                    Modifier.fillMaxWidth().widthIn(max = NbDimens.chartMax),
                    horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    TonalButton(text.string(R.string.a11y_prev_night), onClick = { actions.onSelect((current - 1).coerceIn(0, last)) })
                    TonalButton(text.string(R.string.a11y_next_night), onClick = { actions.onSelect((current + 1).coerceIn(0, last)) })
                }
            }
            if (state.loading) return@Column
            NoticeCard(state, actions)
            if (state.notProvidedNone) ChartBlock(state, actions, MIN_CHART)
            SelectedNightLine(state)
            Column(Modifier.fillMaxWidth().widthIn(max = NbDimens.chartMax), verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                SummaryText(state)
                ChartNotes(state)
            }
            if (state.metric.state.let { it == MetricState.Learning || it == MetricState.NotProvided || it == MetricState.AccessOff }) {
                HeaderCard(state, actions)
            }
            Column(Modifier.widthIn(max = NbDimens.chartMax).fillMaxWidth()) { StatsBlock(state) }
            ComputedCard(state)
            MeaningCard()
            SourceLine(state)
            DisclaimerFooter(onAbout = actions.onAbout)
        }
    }
}

/** Tabletop top half: name, last night's value and status as one large, view-only label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabletopLabel(state: MetricDetailState) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val metric = state.metric
    val description = text.tileDescription(metric, state.source)
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.name(metric.metric), style = NbTheme.typography.title, color = colors.textPrimary)
        Text(text.value(metric)?.text ?: DASH, style = NbTheme.typography.title, color = colors.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone), size = NbDimens.iconInline)
            Text(text.status(metric, state.source), style = NbTheme.typography.title, color = colors.textPrimary)
        }
    }
}

/** Tabletop lower half: the night the step buttons selected, as text (the tooltip lives above the fold). */
@Composable
private fun SelectedNightLine(state: MetricDetailState) {
    val text = rememberUiText()
    val point = state.selected?.let { state.shownPoints.getOrNull(it) } ?: return
    Text(
        text.chartPoint(state.metric.metric, point, state.source),
        style = NbTheme.typography.titleSmall,
        color = NbTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().widthIn(max = NbDimens.chartMax),
    )
}

/**
 * Book (`layouts.md` 5.2, opened on its own): header, period and chart in the start pane, everything that explains in
 * the end pane; the panes split exactly at the fold with 12 dp clearance on each side.
 */
@Composable
private fun BookDetail(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
    padding: PaddingValues,
    top: Dp,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(direction)
    val fold = layout.verticalFold
    val foldX = (fold?.positionDp ?: (layout.widthDp / 2)).dp
    val hinge = (fold?.occludedDp ?: 0f).dp
    val startWidth = foldX - hinge / 2 - NbDimens.hingeClearance - start
    Row(Modifier.fillMaxSize().padding(start = start)) {
        Column(
            Modifier
                .width(startWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = layout.gutter, top = top, bottom = padding.calculateBottomPadding() + NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (state.loading) {
                LoadingBlocks(chartHeight(layout))
                return@Column
            }
            HeaderCard(state, actions)
            NoticeCard(state, actions)
            Period(state, actions, Modifier.fillMaxWidth(), fill = true)
            ChartBlock(state, actions, chartHeight(layout), showSummary = false)
        }
        Spacer(Modifier.width(hinge + NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(end = layout.gutter + padding.calculateEndPadding(direction), top = top)
                .padding(bottom = padding.calculateBottomPadding() + NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (state.loading) return@Column
            SummaryText(state)
            StatsBlock(state)
            ComputedCard(state)
            MeaningCard()
            SourceLine(state)
            DisclaimerFooter(onAbout = actions.onAbout)
        }
    }
}

/**
 * Glance (flip cover, tiny windows): back and info row, value with glyph and label, a thin band, the usual range in
 * words, the 7-night glyph row and the text summary of those nights; no period selector, no footer. Scrolls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceDetail(
    state: MetricDetailState,
    actions: MetricDetailActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val metric = state.metric
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = NbDimens.gutterGlance, vertical = NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbIconButton(NbIcons.Back, text.string(R.string.a11y_back), onClick = actions.onBack)
            Text(
                text.name(metric.metric),
                style = NbTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            NbIconButton(NbIcons.Info, text.string(R.string.a11y_range_info), onClick = actions.onOpenExplainer)
        }
        if (state.loading) {
            Text(text.string(R.string.state_loading_body), style = NbTheme.typography.body, color = colors.textSecondary)
            return@Column
        }
        val description = text.tileDescription(metric, state.source)
        Column(
            Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (metric.state != MetricState.NotProvided && metric.state != MetricState.AccessOff) HeaderValue(metric, large = false)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                    MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone))
                    Text(text.status(metric, state.source), style = NbTheme.typography.label, color = colors.textPrimary)
                }
            }
            if (metric.range != null && metric.value != null) RangeBand(metric.range, metric.value, metric.state, height = 10.dp)
            text.usualRange(metric)?.let { Text(it, style = NbTheme.typography.bodySmall, color = colors.textSecondary) }
            if (!metric.state.compared) {
                Text(text.headerSentence(metric, state.source), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        NoticeLine(state)
        GlanceNights(state)
        state.series
            ?.let { it.copy(points = it.points.takeLast(GLANCE_NIGHTS)) }
            ?.takeIf { it.points.isNotEmpty() }
            ?.let { week ->
                val summary = text.chartSummary(week, state.source)
                Text(
                    summary,
                    style = NbTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.semantics { contentDescription = summary },
                )
            }
    }
}

/** Glance: a screen-level problem as one line (the full state card does not fit a cover screen). */
@Composable
private fun NoticeLine(state: MetricDetailState) {
    val text = rememberUiText()
    val line =
        when (state.notice) {
            MetricNotice.WaitingSync -> text.string(R.string.detail_waiting_sync, state.source)
            MetricNotice.SourceGone -> text.string(R.string.state_source_gone_title, state.source)
            MetricNotice.AccessOff -> text.string(R.string.state_perm_title)
            MetricNotice.HealthConnectUpdate -> text.string(R.string.state_hc_update_title)
            null -> null
        } ?: return
    Text(line, style = NbTheme.typography.body, color = NbTheme.colors.textPrimary)
}

/**
 * The range explainer (`states.md` 8): a glass sheet in Compact, CompactShort, Tabletop (lower half only) and Glance;
 * a glass dialog from Medium up, placed in the end pane in Book. The text sits on a solid card inside the glass.
 */
@Composable
private fun ExplainerOverlay(
    state: MetricDetailState,
    layout: LayoutSpec,
    actions: MetricDetailActions,
) {
    val text = rememberUiText()
    RangeExplainerOverlay(layout, sideLine = text.sideLine(state), onClose = actions.onCloseExplainer)
}

/**
 * "How your usual range works" (`states.md` 8) for any screen: a glass sheet in Compact, CompactShort, Tabletop
 * (lower half only) and Glance, a glass dialog from Medium up (in the end pane in Book). [sideLine] is the
 * metric-specific line ("Only lower values of SpO2 count"), null outside Metric detail.
 */
@Composable
fun RangeExplainerOverlay(
    layout: LayoutSpec,
    sideLine: String?,
    onClose: () -> Unit,
) {
    val text = rememberUiText()
    InfoOverlay(layout, text.string(R.string.explainer_title), onClose) { ExplainerBody(sideLine, it) }
}

/**
 * A titled sheet or dialog (`layouts.md` 4.4): a glass sheet in Compact, CompactShort, Tabletop (lower half only)
 * and Glance, a centred glass dialog from Medium up, placed in the end pane in Book. [body] gets the modifier that
 * lets it scroll inside the available height.
 */
@Composable
fun InfoOverlay(
    layout: LayoutSpec,
    title: String,
    onClose: () -> Unit,
    body: @Composable ColumnScope.(Modifier) -> Unit,
) {
    val sheet = layout.mode in setOf(LayoutMode.Compact, LayoutMode.CompactShort, LayoutMode.Tabletop, LayoutMode.Glance)
    val maxHeight =
        when (layout.mode) {
            LayoutMode.Tabletop -> {
                val fold = layout.horizontalFold?.positionDp ?: (layout.heightDp / 2)
                (layout.heightDp - fold).dp - NbDimens.hingeClearance * 2
            }

            LayoutMode.Glance -> {
                layout.heightDp.dp
            }

            else -> {
                (layout.heightDp * SHEET_SHARE).dp
            }
        }
    if (sheet) {
        OverlayHost(onDismiss = onClose, alignment = Alignment.BottomCenter) {
            GlassSheet(title, onClose = onClose, modifier = Modifier.heightIn(max = maxHeight)) {
                body(Modifier.weight(1f, fill = false))
            }
        }
    } else {
        val fold = layout.verticalFold
        val startPad = fold?.let { (it.positionDp + it.occludedDp / 2).dp + NbDimens.hingeClearance } ?: 0.dp
        OverlayHost(onDismiss = onClose, alignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().padding(start = startPad).padding(NbSpace.s7), contentAlignment = Alignment.Center) {
                InfoDialog(title, onClose, maxHeight, body)
            }
        }
    }
}

private const val SHEET_SHARE = 0.85f

@Composable
private fun InfoDialog(
    title: String,
    onClose: () -> Unit,
    maxHeight: Dp,
    body: @Composable ColumnScope.(Modifier) -> Unit,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(
        Modifier
            .widthIn(max = NbDimens.dialogMaxWidthWide)
            .heightIn(max = maxHeight)
            .shellSurface(ShellMaterial.Dialog, NbShapes.dialog)
            .semantics { paneTitle = title }
            .padding(NbSpace.s7),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = NbTheme.typography.title,
                color = colors.onGlassPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            NbIconButton(
                NbIcons.Close,
                text.string(R.string.a11y_close),
                onClick = onClose,
                tint = colors.onGlassPrimary,
                onGlass = true,
            )
        }
        body(Modifier.weight(1f, fill = false))
    }
}

/** Paragraphs on a solid card inside an [InfoOverlay]. */
@Composable
fun InfoParagraphs(
    paragraphs: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Column(modifier.verticalScroll(rememberScrollState())) {
        NbCard(Modifier.fillMaxWidth()) {
            paragraphs.forEach { Text(it, style = NbTheme.typography.body, color = colors.textPrimary) }
        }
    }
}

/** The explainer copy on a solid card: range, learning, outside, side line, meaning, care, full disclaimer. */
@Composable
private fun ExplainerBody(
    sideLine: String?,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(modifier.verticalScroll(rememberScrollState())) {
        NbCard(Modifier.fillMaxWidth()) {
            listOfNotNull(
                text.string(R.string.explainer_range),
                text.string(R.string.explainer_learning),
                text.string(R.string.explainer_outside),
                sideLine,
            ).forEach { Text(it, style = NbTheme.typography.body, color = colors.textPrimary) }
            Text(
                text.string(R.string.explainer_meaning_title),
                style = NbTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text.string(R.string.explainer_meaning), style = NbTheme.typography.body, color = colors.textPrimary)
            Text(text.string(R.string.explainer_care), style = NbTheme.typography.body, color = colors.textPrimary)
            Text(text.string(R.string.disclaimer_full), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}
