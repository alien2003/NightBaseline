// The screen's blocks belong together (one per item of screens.md 7.3); skeleton and outline proportions are drawing fractions.
@file:Suppress("TooManyFunctions", "MagicNumber")

package io.github.alien2003.nightbaseline.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.Sensitivity
import io.github.alien2003.nightbaseline.ui.components.DASH
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIcon
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.components.RangeBand
import io.github.alien2003.nightbaseline.ui.components.StateCard
import io.github.alien2003.nightbaseline.ui.components.StatsRow
import io.github.alien2003.nightbaseline.ui.components.StatusLabel
import io.github.alien2003.nightbaseline.ui.components.TrendChart
import io.github.alien2003.nightbaseline.ui.components.chartSummary
import io.github.alien2003.nightbaseline.ui.components.markerColor
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightGlyph
import io.github.alien2003.nightbaseline.ui.model.StatusTone
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType

/** The header sentence of the anchor night: `detail_*`, with the low-side variant of `M-SIDE-IGNORED`. */
internal fun UiText.headerSentence(
    metric: MetricUi,
    source: String,
): String =
    if (metric.state == MetricState.SideIgnoredLower) {
        string(R.string.metric_detail_side_ignored_lower, name(metric.metric))
    } else {
        detail(metric, source)
    }

/** "Usual range: 52–61 bpm" with the header's precision (a value just outside never reads like the edge). */
internal fun UiText.usualRange(metric: MetricUi): String? {
    val range = metric.range ?: return null
    val d = decimals(metric)
    return string(R.string.stats_range, bare(metric.metric, range.low, d), value(metric.metric, range.high, d).text)
}

/** The metric-specific "which side counts" line, or null when both sides count. */
internal fun UiText.sideLine(state: MetricDetailState): String? =
    when {
        state.countedSide == Sensitivity.LOW -> string(R.string.metric_side_low_only, name(state.metric.metric))
        state.countedSide == Sensitivity.HIGH -> string(R.string.metric_side_high_only, name(state.metric.metric))
        state.wideSleepRange -> string(R.string.detail_sleep_range_wide)
        else -> null
    }

/** The big value + unit of the anchor night (a dash when there is none). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeaderValue(
    metric: MetricUi,
    large: Boolean = true,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val value = text.value(metric)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(NbSpace.s2), verticalArrangement = Arrangement.Bottom) {
        NbText(
            value?.number ?: DASH,
            style = if (large) NbTheme.typography.numericDisplay else NbTheme.typography.numericValue,
            color = if (value == null) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.alignByBaseline(),
        )
        value?.unit?.let {
            NbText(it, style = NbTheme.typography.unit, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * The header card (`screens.md` 7.3 item 2, the morph target of the tile): value for the anchor night, status chip,
 * usual range and one `detail_*` sentence. Variants: learning (progress and "How it works"), not recorded by the
 * source, access off for this metric, source gone (state card in place of the sentence) and waiting for sync (inline
 * line). One merged TalkBack node for the value part (`a11y_tile*`); actions stay separate buttons.
 */
@Composable
internal fun HeaderCard(
    state: MetricDetailState,
    actions: MetricDetailActions,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val metric = state.metric
    val description = text.tileDescription(metric, state.source)
    NbCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            NbText(text.nightLabelOf(state), style = NbTheme.dataType.statusCalm, color = colors.textSecondary)
            if (metric.state != MetricState.NotProvided && metric.state != MetricState.AccessOff) HeaderValue(metric)
            StatusLabel(text.status(metric, state.source), metric.state.glyph, metric.state.tone)
            if (metric.range != null && metric.value != null) {
                RangeBand(metric.range, metric.value, metric.state)
            }
            text.usualRange(metric)?.takeIf { !state.learning }?.let {
                NbText(it, style = NbTheme.typography.body, color = colors.textPrimary)
            }
        }
        when (metric.state) {
            MetricState.Learning -> {
                LearningProgress(metric.learnedNights)
                NbText(text.headerSentence(metric, state.source), style = NbTheme.typography.body, color = colors.textSecondary)
                NbTextButton(text.string(R.string.action_how_it_works), onClick = actions.onOpenExplainer)
            }

            MetricState.NotProvided -> {
                if (state.series != null) {
                    NbText(
                        text.string(R.string.detail_not_provided_title, state.source, text.name(metric.metric)),
                        style = NbTheme.dataType.itemTitle,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    NbText(text.headerSentence(metric, state.source), style = NbTheme.typography.body, color = colors.textSecondary)
                    state.notProvidedSince?.let {
                        NbText(
                            text.string(R.string.detail_not_provided_since, text.dateShort(it)),
                            style = NbTheme.typography.body,
                            color = colors.textSecondary,
                        )
                    }
                    NbTextButton(text.string(R.string.action_choose_source), onClick = actions.onChooseSource)
                }
            }

            MetricState.AccessOff -> {
                NbText(text.headerSentence(metric, state.source), style = NbTheme.typography.body, color = colors.textSecondary)
                PrimaryButton(text.string(R.string.action_open_hc_permissions), onClick = actions.onOpenPermissions)
            }

            else -> {
                if (state.notice != MetricNotice.SourceGone) {
                    NbText(text.headerSentence(metric, state.source), style = NbTheme.typography.body, color = colors.textSecondary)
                }
            }
        }
        if (state.notice == MetricNotice.WaitingSync) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
                NbIcon(
                    NbIcons.CloudClock,
                    contentDescription = null,
                    tint = colors.statusInfo,
                    modifier = Modifier.sizeIn(maxWidth = 20.dp),
                )
                NbText(
                    text.string(R.string.detail_waiting_sync, state.source),
                    style = NbTheme.typography.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The anchor night's label ("Night of Nov 24–25"). */
internal fun UiText.nightLabelOf(state: MetricDetailState): String = nightLabel(state.anchorDate)

/** The screen-level problem card (`E-SOURCE-GONE`, `E-PERM`, `E-HC-UPDATE`); null for the inline ones. */
@Composable
internal fun NoticeCard(
    state: MetricDetailState,
    actions: MetricDetailActions,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    when (state.notice) {
        MetricNotice.SourceGone -> {
            StateCard(
                NightGlyph.CloudClock,
                StatusTone.Info,
                text.string(R.string.state_source_gone_title, state.source),
                text.string(R.string.state_source_gone_body),
                modifier,
                primaryAction = text.string(R.string.action_choose_source),
                onPrimary = actions.onChooseSource,
            )
        }

        MetricNotice.AccessOff -> {
            StateCard(
                NightGlyph.Key,
                StatusTone.Info,
                text.string(R.string.state_perm_title),
                text.string(R.string.state_perm_body),
                modifier,
                primaryAction = text.string(R.string.action_open_hc_permissions),
                onPrimary = actions.onOpenPermissions,
            )
        }

        MetricNotice.HealthConnectUpdate -> {
            StateCard(
                NightGlyph.Download,
                StatusTone.Info,
                text.string(R.string.state_hc_update_title),
                text.string(R.string.state_hc_update_body),
                modifier,
                primaryAction = text.string(R.string.action_open_store),
                onPrimary = actions.onOpenStore,
            )
        }

        MetricNotice.WaitingSync, null -> {}
    }
}

/**
 * The chart area: the [TrendChart] of the selected period with its notes (`D-PERIOD-SHORT`, `D-BAND-CHANGED`), an
 * empty frame with a dotted band while nothing is stored (`L0`), or the "not recorded" state card when the source
 * never wrote this metric (`states.md` 4.1).
 */
@Composable
internal fun ChartBlock(
    state: MetricDetailState,
    actions: MetricDetailActions,
    height: Dp,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showSummary: Boolean = true,
    showNotes: Boolean = true,
) {
    val text = rememberUiText()
    val series = state.shownSeries
    Column(modifier.fillMaxWidth().widthIn(max = NbDimens.chartMax), verticalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
        when {
            state.metric.state == MetricState.NotProvided && state.series == null -> {
                StateCard(
                    MetricState.NotProvided.toNightGlyph(),
                    StatusTone.Neutral,
                    text.string(R.string.detail_not_provided_title, state.source, text.name(state.metric.metric)),
                    text.string(R.string.detail_not_provided_body, state.source),
                    secondaryAction = text.string(R.string.action_choose_source),
                    onSecondary = actions.onChooseSource,
                )
            }

            series == null || series.points.isEmpty() -> {
                EmptyChart(text.string(R.string.metric_chart_empty, state.source), height)
            }

            else -> {
                TrendChart(
                    series,
                    state.source,
                    selected = state.selected,
                    onSelect = actions.onSelect,
                    height = height,
                    interactive = interactive,
                    showSummary = showSummary,
                )
                if (showNotes) ChartNotes(state)
            }
        }
    }
}

/** Notes under the chart: fewer nights than the period, and a shifted range. */
@Composable
internal fun ChartNotes(state: MetricDetailState) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    if (state.periodShort) {
        NbText(
            text.plural(R.plurals.detail_period_short, state.storedNights, text.int(state.storedNights)),
            style = NbTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
    state.bandShiftDate?.let {
        NbText(
            text.string(R.string.detail_band_changed, text.dateShort(it)),
            style = NbTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

private fun MetricState.toNightGlyph(): NightGlyph =
    if (this ==
        MetricState.NotProvided
    ) {
        NightGlyph.SlashedCircle
    } else {
        NightGlyph.DashCircle
    }

/** `L0`: a solid chart frame with a dotted outline where the band will be, and one line of text. */
@Composable
internal fun EmptyChart(
    message: String,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.chartMax)
            .solidCard(NbShapes.l)
            .padding(NbSpace.s4),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height * EMPTY_CHART_SHARE)
                .clearAndSetSemantics {},
        ) {
            val stroke = NbDimens.bandEdgeStroke.toPx()
            drawRoundRect(
                colors.data.learningOutline,
                topLeft = Offset(stroke, size.height * 0.3f),
                size = Size(size.width - 2 * stroke, size.height * 0.4f),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
            )
        }
        NbText(message, style = NbTheme.typography.body, color = colors.textSecondary)
    }
}

private const val EMPTY_CHART_SHARE = 0.6f

/** `LOADING`: a skeleton header and an empty chart frame (no shimmer: idle screens never animate). */
@Composable
internal fun LoadingBlocks(
    chartHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val text = rememberUiText()
    val loading = text.string(R.string.state_loading_body)
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        NbCard(Modifier.fillMaxWidth()) {
            SkeletonBar(0.4f, 14.dp)
            SkeletonBar(0.55f, 44.dp)
            SkeletonBar(0.35f, 28.dp)
            SkeletonBar(1f, NbDimens.bandHeight)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = NbDimens.chartMax)
                .height(chartHeight)
                .solidCard(NbShapes.l),
        )
        NbText(loading, style = NbTheme.typography.body, color = colors.textSecondary)
    }
}

@Composable
private fun SkeletonBar(
    fraction: Float,
    height: Dp,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(NbTheme.colors.surface2, NbShapes.m),
    )
}

/** The visible chart summary as its own text (when the chart card does not show it), plus the notes. */
@Composable
internal fun SummaryText(state: MetricDetailState) {
    val text = rememberUiText()
    val series = state.shownSeries ?: return
    if (series.points.isEmpty()) return
    NbText(
        text.chartSummary(series, state.source),
        style = NbTheme.typography.body,
        color = NbTheme.colors.textSecondary,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** `StatsRow` of the period with the header's precision; nothing when no night of the period was compared. */
@Composable
internal fun StatsBlock(state: MetricDetailState) {
    val text = rememberUiText()
    val series = state.shownSeries ?: return
    if (series.points.none { it.state.compared }) return
    Column(verticalArrangement = Arrangement.spacedBy(NbSpace.s3), modifier = Modifier.widthIn(max = NbDimens.chartMax)) {
        // A ColorOS section header outside the readout card (conformance K2).
        NbText(
            text.string(R.string.metric_stats_title),
            style = NbTheme.dataType.sectionHeader,
            color = NbTheme.colors.textTertiary,
            modifier =
                Modifier
                    .padding(top = NbDataDimens.sectionHeaderTop, start = NbDataDimens.sectionHeaderStart)
                    .semantics { heading() },
        )
        StatsRow(series, decimals = text.decimals(state.metric))
    }
}

/**
 * `ExplainerCard` "How your usual range is computed" (`screens.md` 7.3 item 7): the range, the learning rule, what
 * counts as outside, and the metric's side line (SpO2: only lower values count).
 */
@Composable
internal fun ComputedCard(
    state: MetricDetailState,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    ExplainerCard(
        title = text.string(R.string.detail_computed_title),
        paragraphs =
            listOfNotNull(
                text.string(R.string.explainer_range),
                text.string(R.string.explainer_learning),
                text.string(R.string.explainer_outside),
            ),
        emphasis = text.sideLine(state),
        modifier = modifier,
    )
}

/** `ExplainerCard` "What 'outside your usual range' means" (item 8), ending with the care sentence. */
@Composable
internal fun MeaningCard(modifier: Modifier = Modifier) {
    val text = rememberUiText()
    ExplainerCard(
        title = text.string(R.string.explainer_meaning_title),
        paragraphs = listOf(text.string(R.string.explainer_meaning)),
        emphasis = text.string(R.string.explainer_care),
        modifier = modifier,
    )
}

@Composable
private fun ExplainerCard(
    title: String,
    paragraphs: List<String>,
    emphasis: String?,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    NbCard(modifier.fillMaxWidth()) {
        NbText(title, style = NbTheme.dataType.itemTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        paragraphs.forEach { NbText(it, style = NbTheme.dataType.summary, color = colors.textSecondary) }
        emphasis?.let {
            NbText(it, style = NbTheme.dataType.summary.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary)
        }
    }
}

/** "Measured by {source}. Last sync {time}." */
@Composable
internal fun SourceLine(
    state: MetricDetailState,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val line =
        state.lastSync?.let { text.string(R.string.detail_source_line, state.source, it) }
            ?: text.string(R.string.metric_source_line_no_sync, state.source)
    NbText(
        line,
        style = NbTheme.typography.bodySmall,
        color = NbTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().widthIn(max = NbDimens.textMax),
    )
}

/**
 * Glance: the last 7 nights as a glyph row (weekday letter above each night's status shape), one node per night is
 * not needed here: the row is decorative and the chart summary below carries the same facts.
 */
@Composable
internal fun GlanceNights(state: MetricDetailState) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val points =
        state.series
            ?.points
            ?.takeLast(GLANCE_NIGHTS)
            .orEmpty()
    if (points.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        points.forEach { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                NbText(text.weekdayLetter(p.date), style = NbTheme.typography.caption, color = colors.textSecondary)
                Box(Modifier.width(20.dp).height(20.dp), contentAlignment = Alignment.Center) {
                    MetricGlyphIcon(p.state.glyph, colors.markerColor(p.state))
                }
            }
        }
    }
}

/** Nights in the Glance glyph row. */
internal const val GLANCE_NIGHTS = 7
