package io.github.alien2003.nightbaseline.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.HistoryStrip
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.LearningCard
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.MetricRangeTile
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NightGlyphBadge
import io.github.alien2003.nightbaseline.ui.components.NightStateCard
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.PeriodSelector
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.StatsRow
import io.github.alien2003.nightbaseline.ui.components.StatusChip
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.TrendChart
import io.github.alien2003.nightbaseline.ui.components.tone
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * Placeholder Home ("Last night", `screens.md` 7.1) on the synthetic sample data: large-title top bar with the night
 * label, banner slot, summary / learning / state card, metric tiles in registry order (two columns in Medium), the
 * "Not recorded by" row, the history strip and the disclaimer footer. Two-pane layouts add the Metric detail of the
 * first metric that moved (or the first metric) in the detail pane. The data layer is wired in a later step.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    onSelectTab: (TopLevel) -> Unit = {},
    debugAction: (@Composable () -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val scrollDp = with(density) { scroll.value.toDp() }
    val large = layout.mode == LayoutMode.Compact
    val collapseRange = NbDimens.topBarLarge - NbDimens.topBarCollapsed
    val collapse = if (large) (scrollDp / collapseRange).coerceIn(0f, 1f) else 1f
    val scrolledUnder = if (large) (scrollDp - collapseRange).coerceAtLeast(0.dp) else scrollDp
    val text = rememberUiText()
    val topPaddingPx =
        with(density) {
            WindowInsets.statusBars
                .asPaddingValues()
                .calculateTopPadding()
                .roundToPx()
        }
    val barHeight = topBarHeight(layout.mode)
    // The bar grows with the font scale (title and subtitle never clip), so the content starts below its measured
    // height at rest; while scrolling the measured value is kept so the content does not jump.
    var restingBarHeight by remember { mutableStateOf(0.dp) }
    val contentTop = maxOf(barHeight, restingBarHeight)
    val defaultMetric = (state.night.movedMetrics.firstOrNull() ?: state.night.metrics.firstOrNull())?.metric
    var selectedMetric by rememberSaveable { mutableStateOf(defaultMetric?.key) }
    AppScaffold(
        layout = layout,
        selected = TopLevel.Home,
        onSelect = onSelectTab,
        modifier = modifier,
        topBar = { contentPadding ->
            if (layout.mode != LayoutMode.Glance) {
                Box(
                    Modifier
                        .padding(start = contentPadding.calculateStartPadding(LocalLayoutDirection.current))
                        .onSizeChanged { size ->
                            if (scroll.value == 0) {
                                val statusBar = topPaddingPx
                                restingBarHeight = with(density) { (size.height - statusBar).coerceAtLeast(0).toDp() }
                            }
                        },
                ) {
                    ShellTopBar(
                        title = stringResource(R.string.home_title),
                        subtitle = text.nightLabel(state.night),
                        large = large,
                        collapse = collapse,
                        height = barHeight,
                        scrolledUnder = scrolledUnder,
                        actions = { debugAction?.invoke() },
                    )
                }
            }
        },
    ) { padding ->
        when {
            layout.mode == LayoutMode.Glance -> {
                GlanceHome(state, padding)
            }

            layout.twoPanes -> {
                TwoPaneHome(
                    state,
                    layout,
                    padding,
                    contentTop,
                    selectedMetric?.let(::MetricId) ?: defaultMetric,
                    onSelectMetric = { selectedMetric = it.key },
                )
            }

            else -> {
                SinglePaneHome(state, layout, padding, contentTop, scroll)
            }
        }
    }
}

/** Top bar height per mode (`layouts.md` 4.3): large title in Compact, 48 dp in short windows, 64 dp otherwise. */
private fun topBarHeight(mode: LayoutMode): Dp =
    when (mode) {
        LayoutMode.Compact -> NbDimens.topBarLarge
        LayoutMode.CompactShort, LayoutMode.WideShort -> NbDimens.topBarShort
        else -> NbDimens.topBarMedium
    }

@Composable
private fun topInset(barHeight: Dp): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + barHeight + NbSpace.s3

@Composable
private fun SinglePaneHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    scroll: ScrollState,
) {
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
        HomeBlocks(state, columns = if (layout.mode == LayoutMode.Medium || layout.mode == LayoutMode.WideShort) 2 else 1)
    }
}

@Composable
private fun HomeBlocks(
    state: HomeState,
    columns: Int,
    selected: MetricId? = null,
    onSelect: (MetricId) -> Unit = {},
    stripTargets: Boolean = true,
) {
    val night = state.night
    val maxWidth = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
    state.banner?.let { InlineBanner(it, state.source, onDismiss = {}) }
    when {
        night.state.hasSummary -> {
            NightSummaryCard(night, state.source)
        }

        night.state == NightState.Learning || night.state == NightState.LearningStart -> {
            LearningCard(
                night,
                state.source,
                onHowItWorks = {},
            )
        }

        else -> {
            NightStateCard(night, state.source)
        }
    }
    night.metrics.chunked(columns).forEach { row ->
        Row(Modifier.widthIn(max = maxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            row.forEach { metric ->
                MetricRangeTile(
                    metric,
                    state.source,
                    onClick = { onSelect(metric.metric) },
                    selected = metric.metric == selected,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (night.notRecorded.isNotEmpty()) NotRecordedRow(night.notRecorded, state.source)
    HistoryStrip(state.recent, onNight = {}, onSeeAll = {}, individualTargets = stripTargets, modifier = Modifier.widthIn(max = maxWidth))
    DisclaimerFooter(onAbout = {})
}

@Composable
private fun NotRecordedRow(
    metrics: List<MetricId>,
    source: String,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Row(Modifier.fillMaxWidth().widthIn(max = NbDimens.textMax), verticalAlignment = Alignment.CenterVertically) {
        MetricGlyphIcon(MetricState.NotProvided.glyph, colors.statusNeutral, Modifier.padding(horizontal = NbSpace.s3))
        Text(
            text.string(R.string.home_not_recorded_row, source, text.list(metrics.map { text.name(it) })),
            style = NbTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NbIconButton(NbIcons.Info, stringResource(R.string.action_learn_more), onClick = {}, tint = colors.textSecondary)
    }
}

@Composable
private fun TwoPaneHome(
    state: HomeState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    selected: MetricId?,
    onSelectMetric: (MetricId) -> Unit,
) {
    val fold = layout.verticalFold
    val start = padding.calculateLeftPadding(LayoutDirection.Ltr)
    // Book: split exactly at the fold with 12 dp clearance on each side; otherwise a 320-400 dp list pane.
    val listWidth = fold?.let { it.positionDp.dp - start - layout.gutter - NbDimens.hingeClearance } ?: 400.dp
    val top = topInset(barHeight)
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    Row(Modifier.fillMaxSize().padding(start = start)) {
        Column(
            Modifier
                .width(listWidth + layout.gutter)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = layout.gutter, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            HomeBlocks(state, columns = 1, selected = selected, onSelect = onSelectMetric, stripTargets = false)
        }
        Spacer(Modifier.width(if (fold != null) NbDimens.hingeClearance * 2 else 24.dp))
        val metric = state.night.metrics.firstOrNull { it.metric == selected } ?: state.night.metrics.firstOrNull()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = layout.gutter + padding.calculateRightPadding(LayoutDirection.Ltr), top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (metric != null) MetricDetailPane(metric, state.series[metric.metric], state.source)
        }
    }
}

/** The Metric detail of the two-pane Home (`screens.md` 7.3): header card, period selector, chart, stats. */
@Composable
private fun MetricDetailPane(
    metric: MetricUi,
    series: MetricSeriesUi?,
    source: String,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    var period by rememberSaveable { mutableStateOf(ChartPeriod.Month) }
    var selection by rememberSaveable(metric.metric.key) { mutableStateOf<Int?>(null) }
    NbCard(Modifier.fillMaxWidth()) {
        Text(
            text.name(metric.metric),
            style = NbTheme.typography.title,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        val value = text.value(metric)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
            Text(value?.number ?: "–", style = NbTheme.typography.numericDisplay, color = colors.textPrimary)
            value?.unit?.let {
                Text(
                    it,
                    style = NbTheme.typography.unit,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        StatusChip(text.status(metric, source), metric.state.glyph, metric.state.tone)
        Text(text.detail(metric, source), style = NbTheme.typography.body, color = colors.textSecondary)
    }
    PeriodSelector(period, onSelect = { period = it })
    if (series != null) {
        val shown = series.copy(points = series.points.takeLast(period.nights))
        TrendChart(shown, source, selected = selection, onSelect = { selection = it })
        StatsRow(shown)
    }
}

@Composable
private fun GlanceHome(
    state: HomeState,
    padding: PaddingValues,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val night = state.night
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = NbDimens.gutterGlance, vertical = NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.nightLabel(night),
                style = NbTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            NbIconButton(NbIcons.More, stringResource(R.string.action_more), onClick = {})
        }
        val title = text.nightTitle(night, state.source)
        Row(
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            NightGlyphBadge(night.state.glyph, night.state.tone, container = 40.dp, glyphSize = 28.dp)
            Text(title, style = NbTheme.typography.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
        }
        night.metrics.forEach { metric ->
            val value = text.value(metric)?.text ?: "–"
            val status = text.status(metric, state.source)
            Row(
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = NbDimens.targetMin)
                    .clearAndSetSemantics { contentDescription = text.tileDescription(metric, state.source) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
            ) {
                MetricGlyphIcon(metric.state.glyph, colors.tone(metric.state.tone))
                Column(Modifier.weight(1f)) {
                    Text(text.shortName(metric.metric), style = NbTheme.typography.titleSmall, color = colors.textPrimary)
                    Text(status, style = NbTheme.typography.caption, color = colors.textSecondary)
                }
                Text(value, style = NbTheme.typography.numericSmall, color = colors.textPrimary)
            }
        }
    }
}
