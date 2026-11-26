// Chart geometry uses fractions of the plot area; naming every fraction adds nothing.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.ChartPointUi
import io.github.alien2003.nightbaseline.ui.model.MetricDisplay
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.UiText
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbColors
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDataShapes
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType
import io.github.alien2003.nightbaseline.ui.theme.hairline
import io.github.alien2003.nightbaseline.ui.theme.nbShadow
import io.github.alien2003.nightbaseline.ui.theme.thumbShadow
import io.github.alien2003.nightbaseline.ui.theme.tooltipShadow
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** The leading edge of the period thumb runs this much stiffer than `spring.selection` (247): a stretch toward the target. */
private const val THUMB_LEAD_STIFFNESS = 600f

/**
 * The 7 / 30 / 90 nights segmented control (`screens.md` 4, conformance K3): a solid neutral pill track (40 dp visual
 * inside 48 dp targets) with a solid convex thumb (gradient, top rim, soft shadow) that slides with `spring.selection`;
 * labels 14 sp medium, the selected one in `text.primary` (selection is the thumb's shape and position, not colour
 * alone). No tonal container, no accent. Each segment is a 48 dp+ tab (`a11y_period`). With [fill] the control
 * spans its width and the segments share it equally (labels wrap inside their segment instead of squeezing the last
 * one at large font).
 */
@Composable
fun PeriodSelector(
    selected: ChartPeriod,
    onSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
    forced: Pair<ChartPeriod, ForcedInteraction>? = null,
    fill: Boolean = false,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val data = colors.data
    val motion = NbTheme.motion
    val bounds = remember { mutableStateMapOf<ChartPeriod, Pair<Float, Float>>() }
    // The track is 40 dp tall inside 48 dp targets; the thumb sits 4 dp inside the track.
    val trackInset = (NbDimens.targetMin - NbDataDimens.periodTrack) / 2
    val inset = NbDataDimens.periodInset
    val thumbShadow = colors.thumbShadow
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = trackInset)
                .background(data.segmentTrack, NbShapes.pill),
        )
        val current = bounds[selected]
        if (current != null) {
            // The edge toward the new choice runs on a stiffer spring than the trailing one, so the thumb stretches
            // toward its target while it moves and settles without overshoot (coloros17-glass.md 4.2).
            val start = current.first
            val end = current.first + current.second
            var forward by remember { mutableStateOf(true) }
            var lastStart by remember { mutableFloatStateOf(start) }
            if (start != lastStart) {
                forward = start > lastStart
                lastStart = start
            }
            val lead: FiniteAnimationSpec<Float> = if (motion.reduced) snap() else spring(stiffness = THUMB_LEAD_STIFFNESS)
            val trail = motion.selection<Float>()
            val left by animateFloatAsState(start, if (forward) trail else lead, label = "periodStart")
            val right by animateFloatAsState(end, if (forward) lead else trail, label = "periodEnd")
            val x = left
            val w = (right - left).coerceAtLeast(0f)
            Canvas(Modifier.matchParentSize()) {
                val top = (trackInset + inset).toPx()
                val h = size.height - 2 * top
                val left = x + inset.toPx()
                val radius = CornerRadius(h / 2)
                // A solid convex chip (conformance K3, glass research 4.2): soft shadow, vertical gradient, top rim.
                drawRoundRect(
                    Color.Black.copy(alpha = thumbShadow.alpha),
                    topLeft = Offset(left, top + thumbShadow.offsetY.toPx()),
                    size = Size(w, h),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(data.segmentThumbTop, data.segmentThumbBottom), startY = top, endY = top + h),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = radius,
                )
                val rim = 1.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(listOf(data.segmentThumbRim, Color.Transparent), startY = top, endY = top + h / 2),
                    topLeft = Offset(left + rim / 2, top + rim / 2),
                    size = Size(w - rim, h - rim),
                    cornerRadius = CornerRadius((h - rim) / 2),
                    style = Stroke(rim),
                )
            }
        }
        Row(
            (if (fill) Modifier.fillMaxWidth().height(IntrinsicSize.Min) else Modifier)
                .padding(horizontal = inset)
                .selectableGroup(),
        ) {
            ChartPeriod.entries.forEach { period ->
                val isSelected = period == selected
                val label = text.plural(R.plurals.period_nights, period.nights, text.int(period.nights))
                Box(
                    (if (fill) Modifier.weight(1f).fillMaxHeight() else Modifier)
                        .sizeIn(minWidth = NbDataDimens.periodSegmentMin, minHeight = NbDimens.targetMin)
                        .onPlaced { coordinates -> bounds[period] = coordinates.positionInParent().x to coordinates.size.width.toFloat() }
                        .nbClickable(
                            onClick = { onSelect(period) },
                            shape = NbShapes.pill,
                            role = Role.Tab,
                            pressScale = false,
                            forced = forced?.takeIf { it.first == period }?.second,
                        ).semantics {
                            this.selected = isSelected
                            contentDescription = text.plural(R.plurals.a11y_period, period.nights, text.int(period.nights))
                        }.padding(horizontal = if (fill) NbSpace.s3 else NbSpace.s5, vertical = NbSpace.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    NbText(
                        label,
                        textAlign = TextAlign.Center,
                        style = NbTheme.dataType.segment,
                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Builds the visible text summary of a chart that is also its content description (`screens.md` 8): header, range,
 * last night, outside count (or all inside), extremes, gaps (omitted when 0). Numbers and dates use the locale.
 */
fun UiText.chartSummary(
    series: MetricSeriesUi,
    source: String,
): String {
    val metric = series.metric
    val points = series.points
    val n = points.size
    val parts = mutableListOf(plural(R.plurals.chart_summary_header, n, name(metric), int(n)))
    val unit = unit(metric).orEmpty()

    fun clean(s: String) = s.replace(" .", ".").replace(" ,", ",")
    // Same precision as the tile, so a value just outside never reads like the range edge.
    val d = points.lastOrNull()?.let { decimals(MetricUi(metric, it.state, it.value, it.range)) }
    points.lastOrNull { it.range != null }?.range?.let { r ->
        parts += clean(string(R.string.chart_summary_range, bare(metric, r.low, d), bare(metric, r.high, d), unit))
    }
    points.lastOrNull()?.let { last ->
        if (last.value != null) {
            val status =
                status(
                    MetricUi(
                        metric,
                        last.state,
                        last.value,
                        last.range,
                        learnedNights =
                            points.count {
                                it.value != null
                            },
                    ),
                    source,
                ).lowercase(locale)
            parts += clean(string(R.string.chart_summary_last, bare(metric, last.value, d), unit, status))
        }
    }
    val outside = points.filter { it.state.moved }
    if (outside.isNotEmpty()) {
        parts += string(R.string.chart_summary_outside, int(outside.size), int(n), dateShort(outside.last().date))
    } else if (points.any { it.state.compared }) {
        parts += plural(R.plurals.chart_summary_all_inside, n, int(n))
    }
    val valued = points.filter { it.value != null }
    val min = valued.minByOrNull { it.value!! }
    val max = valued.maxByOrNull { it.value!! }
    if (min != null && max != null) {
        parts +=
            string(
                R.string.chart_summary_extremes,
                bare(metric, min.value!!),
                dateShort(min.date),
                bare(metric, max.value!!),
                dateShort(max.date),
            )
    }
    val gaps = points.count { it.value == null }
    if (gaps > 0) parts += string(R.string.chart_summary_gaps, int(gaps))
    return parts.joinToString(" ")
}

/** Announcement / tooltip text of one chart point (`chart_tooltip*`, `a11y_chart_point`). */
fun UiText.chartPoint(
    metric: MetricId,
    point: ChartPointUi,
    source: String,
): String {
    val date = dateShort(point.date)
    return when {
        point.value == null -> {
            string(R.string.chart_tooltip_no_value, date)
        }

        point.state == MetricState.NotCompared -> {
            string(R.string.chart_tooltip_short, date)
        }

        else -> {
            string(
                R.string.chart_tooltip,
                date,
                value(metric, point.value).text,
                status(MetricUi(metric, point.state, point.value, point.range), source),
            )
        }
    }
}

/**
 * The per-night trend chart (`screens.md` 4, 7.3) for 7 / 30 / 90 nights on a solid card: the usual range as it was
 * on each night (stepped band), one point per night (no line, so missing nights stay gaps), hollow points while
 * learning or not compared, triangles for values outside. Tap selects the nearest night, a horizontal drag scrubs,
 * Left / Right keys step; the selection shows a `chart.guide` line and the solid [ChartTooltip]. One TalkBack node
 * whose description is [chartSummary], with "Previous night" / "Next night" actions; band and axes are decorative.
 *
 * The value axis uses round ticks ([niceScale]); the plot keeps every marker (also the selected, enlarged one) and
 * the tick labels inside the canvas, and each contiguous run of the band is one shape, so no seams show between
 * nights. While nothing has a value yet the plot shows a dotted outline where the band will be.
 *
 * @param interactive false for a view-only chart (tabletop top half): no gestures, keys or focus.
 * @param showSummary false when the caller places the [chartSummary] text elsewhere (it stays the description).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // Drawing, gestures, keys and accessibility of one chart node belong together.
@Composable
fun TrendChart(
    series: MetricSeriesUi,
    source: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = NbDimens.chartHeightCompact,
    interactive: Boolean = true,
    showSummary: Boolean = true,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = NbTheme.typography.numericAxis.copy(color = colors.textSecondary)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val points = series.points
    val summary = remember(series, text) { text.chartSummary(series, source) }
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val view = LocalView.current
    val last = points.lastIndex
    val step = { delta: Int -> currentSelect(((selected ?: last) + delta).coerceIn(0, last)) }
    val scale = remember(series) { scaleOf(series) }
    val selectedText = selected?.let { points.getOrNull(it) }?.let { text.chartPoint(series.metric, it, source) }
    val prev = text.string(R.string.a11y_prev_night)
    val next = text.string(R.string.a11y_next_night)
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.chartMax)
            .solidCard(NbShapes.l)
            .padding(NbSpace.s4),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = summary
                    selectedText?.let { stateDescription = it }
                    if (interactive) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(prev) {
                                    step(-1)
                                    true
                                },
                                CustomAccessibilityAction(next) {
                                    step(1)
                                    true
                                },
                            )
                    }
                }.then(
                    if (interactive) {
                        Modifier
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> step(if (rtl) 1 else -1).let { true }
                                    Key.DirectionRight -> step(if (rtl) -1 else 1).let { true }
                                    Key.MoveHome -> currentSelect(0).let { true }
                                    Key.MoveEnd -> currentSelect(last).let { true }
                                    else -> false
                                }
                            }.focusable()
                    } else {
                        Modifier
                    },
                ),
        ) {
            val density = LocalDensity.current
            val markerExtent = with(density) { MARKER_EXTENT.toPx() }
            val tickTexts = scale.ticks.map { measurer.measure(tickLabel(series.metric, it, scale, text), axisStyle) }
            val axisWidth = if (scale.empty) 0f else (tickTexts.maxOfOrNull { it.size.width } ?: 0) + with(density) { 6.dp.toPx() }
            Canvas(
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
                    .then(
                        if (interactive) {
                            Modifier
                                .pointerInput(points.size, rtl) {
                                    detectTapGestures { offset ->
                                        currentSelect(indexAt(offset.x, size.width.toFloat(), points.size, rtl, markerExtent))
                                    }
                                }.pointerInput(points.size, rtl) {
                                    var last: Int? = null
                                    detectHorizontalDragGestures(onDragStart = { last = currentSelected }) { change, _ ->
                                        val index = indexAt(change.position.x, size.width.toFloat(), points.size, rtl, markerExtent)
                                        // A light tick for every night the finger crosses (conformance M4, public constant).
                                        if (index != last) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        last = index
                                        currentSelect(index)
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val dateHeight = measurer.measure(text.dateShort(points.firstOrNull()?.date ?: LocalDate.EPOCH), axisStyle).size.height
                val labelHalf = (tickTexts.maxOfOrNull { it.size.height } ?: 0) / 2f
                val plot =
                    PlotArea(
                        this,
                        points.size,
                        Domain(scale.min, scale.max),
                        rtl,
                        axisWidth = axisWidth,
                        axisHeight = dateHeight + 4.dp.toPx(),
                        inset = markerExtent,
                        verticalInset = maxOf(markerExtent, labelHalf),
                    )
                if (!scale.empty) drawGrid(plot, colors, scale.ticks, tickTexts)
                drawBand(plot, points, colors)
                selected?.let { drawGuide(plot, it, colors) }
                drawPoints(plot, points, colors, selected)
                drawDateAxis(plot, points, measurer, axisStyle, text)
            }
            val selectedPoint = selected?.let { points.getOrNull(it) }
            if (selectedPoint != null) {
                val index = points.indexOf(selectedPoint)
                TooltipPlacer(guideX = { width -> slotX(width.toFloat(), points.size, index, rtl, axisWidth, markerExtent) }) {
                    ChartTooltip(series.metric, selectedPoint, source)
                }
            }
        }
        if (showSummary) {
            NbText(
                summary,
                style = NbTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier =
                    Modifier
                        .padding(
                            top = NbSpace.s4,
                        ).clearAndSetSemantics {
                        },
            )
        }
    }
}

/** Half the largest marker (selected triangle) plus its halo: the plot keeps this much room on every side. */
private val MARKER_EXTENT: Dp = 11.dp

/**
 * The solid tooltip over the chart (never glass, conformance K7): `surface.elevated` fill, no outline, `shape.popup`
 * 16 dp, a soft shadow; date 12 sp grey, value 16 sp medium, the [StatusLabel]. Not its own node.
 */
@Composable
fun ChartTooltip(
    metric: MetricId,
    point: ChartPointUi,
    source: String,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val shape = NbDataShapes.popup
    Column(
        modifier
            .nbShadow(colors.tooltipShadow, shape)
            .background(colors.data.tooltip, shape)
            .padding(horizontal = NbSpace.s4, vertical = NbSpace.s3)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(NbSpace.s1),
    ) {
        NbText(text.dateShort(point.date), style = NbTheme.typography.caption, color = colors.textSecondary)
        val value = point.value?.let { text.value(metric, it).text }
        NbText(value ?: text.string(R.string.status_no_value), style = NbTheme.dataType.tooltipValue, color = colors.textPrimary)
        if (point.value != null) {
            StatusLabel(
                text.status(MetricUi(metric, point.state, point.value, point.range), source),
                point.state.glyph,
                point.state.tone,
            )
        }
    }
}

/**
 * Places its single child at the top, beside the selection guide at [guideX] (px for the given width, the same slot
 * geometry the chart draws with): on the side with more room, a marker's extent away from the line, so the tooltip
 * never covers the selected marker.
 */
@Composable
private fun TooltipPlacer(
    guideX: (Int) -> Float,
    content: @Composable () -> Unit,
) {
    val gap = with(LocalDensity.current) { MARKER_EXTENT.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        layout(width, placeable.height) {
            val guide = guideX(width)
            val raw =
                when {
                    guide > width / 2f -> guide - gap - placeable.width
                    else -> guide + gap
                }
            val x = raw.roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
            placeable.place(x, 0)
        }
    }
}

/**
 * The period readout under a chart (`screens.md` 7.3, conformance K10): one 20 dp card of 48 dp rows, the label in
 * grey at the start and the value (tabular, medium) at the end, split by inset hairlines: nights inside the range
 * (k of N), the usual range, the middle value. Each row is one TalkBack node with the full sentence. [decimals]
 * overrides the metric's fraction digits, so the range reads like the header above it.
 */
@Composable
fun StatsRow(
    series: MetricSeriesUi,
    modifier: Modifier = Modifier,
    decimals: Int? = null,
) {
    val text = rememberUiText()
    val compared = series.points.filter { it.state.compared }
    val inside = compared.count { !it.state.moved }
    val range = series.points.lastOrNull { it.range != null }?.range
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = NbDimens.textMax)
            .solidCard(NbShapes.l),
    ) {
        ReadoutRow(
            label = text.string(R.string.stats_label_inside),
            value = text.plural(R.plurals.stats_value_inside, compared.size, text.int(inside), text.int(compared.size)),
            description = text.plural(R.plurals.stats_inside, compared.size, text.int(inside), text.int(compared.size)),
        )
        range?.let {
            val low = text.bare(series.metric, it.low, decimals)
            val high = text.value(series.metric, it.high, decimals).text
            ReadoutDivider()
            ReadoutRow(
                label = text.string(R.string.stats_label_range),
                value = text.string(R.string.stats_value_range, low, high),
                description = text.string(R.string.stats_range, low, high),
            )
            val median = text.value(series.metric, it.median, decimals).text
            ReadoutDivider()
            ReadoutRow(
                label = text.string(R.string.stats_label_median),
                value = median,
                description = text.string(R.string.stats_median, median),
            )
        }
    }
}

/** One readout row: grey label at the start, value at the end; both wrap inside their share at large font. */
@Composable
private fun ReadoutRow(
    label: String,
    value: String,
    description: String,
) {
    val colors = NbTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = NbDataDimens.readoutRow)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = NbDimens.cardPadding, vertical = NbSpace.s4 - NbSpace.s1),
        // Both hug their text inside their share; the leftover space goes between them, so the value ends at the edge.
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NbText(
            label,
            style = NbTheme.dataType.summary,
            color = colors.textSecondary,
            modifier = Modifier.weight(LABEL_SHARE, fill = false).padding(end = NbSpace.s4),
        )
        NbText(
            value,
            style = NbTheme.dataType.readoutValue,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The label's share of a readout row against the value's 1 (labels are the longer text). */
private const val LABEL_SHARE = 1.5f

/** Hairline between readout rows, inset 16 dp on both sides. */
@Composable
private fun ReadoutDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = NbDimens.cardPadding)
            .height(hairline)
            .background(NbTheme.colors.separator),
    )
}

private class Domain(
    val min: Double,
    val max: Double,
)

/** A value axis with round ticks: the domain runs from the first to the last tick. */
internal data class NiceScale(
    /** Lowest tick (bottom of the plot). */
    val min: Double,
    /** Highest tick (top of the plot). */
    val max: Double,
    /** Distance between ticks. */
    val step: Double,
    /** Fraction digits the tick labels need (of hours for durations). */
    val decimals: Int,
    /** True when there is no value to scale (nothing is labelled). */
    val empty: Boolean = false,
) {
    /** Tick values, bottom to top. */
    val ticks: List<Double>
        get() = if (empty) emptyList() else List(((max - min) / step).roundToInt() + 1) { min + it * step }
}

private const val MAX_TICKS = 5
private const val MINUTES_PER_HOUR = 60.0
private const val EPSILON = 1e-9
private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0)

/** Minute steps for durations: quarter, half, one, two, three, four, six hours. */
private val DURATION_STEPS = listOf(15.0, 30.0, 60.0, 120.0, 180.0, 240.0, 360.0)

/**
 * Round ticks covering [lo]..[hi] with at most [maxTicks] labels: steps of 1, 2, 2.5 or 5 times a power of ten (or
 * whole parts of hours for [duration] in minutes). A flat series gets one step of room on each side.
 */
internal fun niceScale(
    lo: Double,
    hi: Double,
    duration: Boolean = false,
    maxTicks: Int = MAX_TICKS,
): NiceScale {
    val low = minOf(lo, hi)
    val high = maxOf(lo, hi)
    val candidates =
        if (duration) {
            DURATION_STEPS
        } else {
            val span = (high - low).takeIf { it > 0 } ?: maxOf(abs(high) * 0.02, 0.1)
            val base = 10.0.pow(floor(log10(span / maxTicks)) - 1)
            (0..3).flatMap { e -> NICE_STEPS.map { it * base * 10.0.pow(e) } }
        }
    var fallback: NiceScale? = null
    for (step in candidates) {
        var min = floor(low / step + EPSILON) * step
        var max = ceil(high / step - EPSILON) * step
        if (max - min < step / 2) {
            min -= step
            max += step
        }
        val scale = NiceScale(min, max, step, decimalsOf(step, duration))
        if (scale.ticks.size <= maxTicks) return scale
        fallback = scale
    }
    return checkNotNull(fallback)
}

private fun decimalsOf(
    step: Double,
    duration: Boolean,
): Int {
    val unit = if (duration) step / MINUTES_PER_HOUR else step
    return (0..3).firstOrNull { d ->
        val shifted = unit * 10.0.pow(d)
        abs(shifted - shifted.roundToInt()) < 1e-6
    } ?: 3
}

private fun scaleOf(series: MetricSeriesUi): NiceScale {
    val values = series.points.flatMap { listOfNotNull(it.value, it.range?.low, it.range?.high) }
    if (values.isEmpty()) return NiceScale(0.0, 1.0, 1.0, 0, empty = true)
    return niceScale(values.min(), values.max(), MetricDisplay.of(series.metric).duration)
}

/** A tick label: the bare number, or hours for durations ("7.5 h"). */
private fun tickLabel(
    metric: MetricId,
    value: Double,
    scale: NiceScale,
    text: UiText,
): String =
    if (MetricDisplay.of(metric).duration) {
        text.string(R.string.chart_axis_hours, text.number(value / MINUTES_PER_HOUR, scale.decimals))
    } else {
        text.number(value, scale.decimals)
    }

/**
 * Plot geometry: one slot per night between the value axis and the far edge, kept [inset] from both horizontal ends
 * and [verticalInset] from the top and the date axis, so markers and tick labels never leave the canvas.
 */
@Suppress("LongParameterList") // The plot geometry needs every measured inset.
private class PlotArea(
    scope: DrawScope,
    val count: Int,
    val domain: Domain,
    val rtl: Boolean,
    axisWidth: Float,
    axisHeight: Float,
    inset: Float,
    verticalInset: Float,
) {
    /** Edges of the night slots. */
    val left = (if (rtl) 0f else axisWidth) + inset
    val right = scope.size.width - (if (rtl) axisWidth else 0f) - inset
    val top = verticalInset
    val bottom = scope.size.height - axisHeight - verticalInset

    /** Grid lines span the whole plot next to the value labels. */
    val gridLeft = if (rtl) 0f else axisWidth
    val gridRight = scope.size.width - if (rtl) axisWidth else 0f
    val step = (right - left) / count.coerceAtLeast(1)

    fun x(index: Int): Float {
        val i = if (rtl) count - 1 - index else index
        return left + step * (i + 0.5f)
    }

    fun y(value: Double): Float = (bottom - (value - domain.min) / (domain.max - domain.min) * (bottom - top)).toFloat()
}

/** Centre of night [index]'s slot in a plot [width] px wide (the geometry of [PlotArea], usable outside drawing). */
@Suppress("LongParameterList") // Mirrors PlotArea's inputs.
private fun slotX(
    width: Float,
    count: Int,
    index: Int,
    rtl: Boolean,
    axisWidth: Float,
    inset: Float,
): Float {
    val left = (if (rtl) 0f else axisWidth) + inset
    val right = width - (if (rtl) axisWidth else 0f) - inset
    val i = if (rtl) count - 1 - index else index
    return left + (right - left) / count.coerceAtLeast(1) * (i + 0.5f)
}

private fun indexAt(
    x: Float,
    width: Float,
    count: Int,
    rtl: Boolean,
    inset: Float = 0f,
): Int {
    val usable = (width - 2 * inset).coerceAtLeast(1f)
    val raw = (((x - inset) / usable) * count).toInt().coerceIn(0, count - 1)
    return if (rtl) count - 1 - raw else raw
}

private fun DrawScope.drawGrid(
    plot: PlotArea,
    colors: NbColors,
    ticks: List<Double>,
    labels: List<TextLayoutResult>,
) {
    ticks.forEachIndexed { i, value ->
        val y = plot.y(value)
        // 1 px neutral hairlines, horizontal only (conformance K7).
        drawLine(colors.data.chartGrid, Offset(plot.gridLeft, y), Offset(plot.gridRight, y), strokeWidth = 1f)
        val text = labels[i]
        val x = if (plot.rtl) plot.gridRight + 6f * density else plot.gridLeft - text.size.width - 6f * density
        drawText(text, topLeft = Offset(x.coerceAtLeast(0f), y - text.size.height / 2f))
    }
}

/**
 * The band as it was on each night: every contiguous run of nights with a range is one filled shape whose top and
 * bottom edges step between nights (no per-night rectangles, so no anti-aliased seams). Runs break at nights without
 * a range. With no range and no value at all, a dotted outline marks where the band will be.
 */
private fun DrawScope.drawBand(
    plot: PlotArea,
    points: List<ChartPointUi>,
    colors: NbColors,
) {
    if (points.none { it.range != null }) {
        if (points.none { it.value != null }) drawBandPlaceholder(plot, colors, NbDimens.bandEdgeStroke.toPx())
        return
    }
    val half = plot.step / 2

    // Slot edges in drawing order (start to end of the run, mirrored in RTL).
    fun from(i: Int) = if (plot.rtl) plot.x(i) + half else plot.x(i) - half

    fun to(i: Int) = if (plot.rtl) plot.x(i) - half else plot.x(i) + half
    bandRuns(points).forEach { run ->
        val fill = Path()
        run.forEachIndexed { k, i ->
            val r = checkNotNull(points[i].range)
            if (k == 0) fill.moveTo(from(i), plot.y(r.high)) else fill.lineTo(from(i), plot.y(r.high))
            fill.lineTo(to(i), plot.y(r.high))
        }
        run.asReversed().forEach { i ->
            val yLow = plot.y(checkNotNull(points[i].range).low)
            fill.lineTo(to(i), yLow)
            fill.lineTo(from(i), yLow)
        }
        fill.close()
        // A quiet neutral area, no edge lines (conformance K7): the points carry the status.
        drawPath(fill, colors.data.chartBandArea)
    }
}

/** Index runs of consecutive nights that have a range. */
private fun bandRuns(points: List<ChartPointUi>): List<List<Int>> {
    val runs = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    points.forEachIndexed { i, p ->
        if (p.range != null) {
            current += i
        } else if (current.isNotEmpty()) {
            runs += current
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) runs += current
    return runs
}

private fun DrawScope.drawBandPlaceholder(
    plot: PlotArea,
    colors: NbColors,
    edge: Float,
) {
    val h = plot.bottom - plot.top
    drawRoundRect(
        colors.data.learningOutline,
        topLeft = Offset(plot.left, plot.top + h * 0.3f),
        size = Size(plot.right - plot.left, h * 0.4f),
        cornerRadius = CornerRadius(8f * density),
        style = Stroke(edge, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density))),
    )
}

private fun DrawScope.drawGuide(
    plot: PlotArea,
    index: Int,
    colors: NbColors,
) {
    val x = plot.x(index)
    drawLine(colors.data.chartGuide, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f * density)
}

private fun DrawScope.drawPoints(
    plot: PlotArea,
    points: List<ChartPointUi>,
    colors: NbColors,
    selected: Int?,
) {
    // Points are 3 dp dots; they shrink with the period so 90 nights stay separate.
    val radius = (plot.step * 0.4f).coerceIn(2f * density, NbDataDimens.chartPointRadius.toPx())
    val triangleSide = (radius * 3.4f).coerceAtMost(NbDataDimens.statusTriangle.toPx())
    val halo = NbDimens.markerHalo.toPx() * 0.5f
    points.forEachIndexed { i, p ->
        val v = p.value ?: return@forEachIndexed
        val center = Offset(plot.x(i), plot.y(v))
        val scale = if (i == selected) 1.4f else 1f
        when (p.state) {
            MetricState.Higher, MetricState.Lower -> {
                val side = triangleSide * scale
                drawCircle(colors.markerHalo, radius = side * 0.5f + halo, center = center)
                drawPath(triangle(center, side, up = p.state == MetricState.Higher), colors.statusMoved)
            }

            MetricState.Within, MetricState.SideIgnoredHigher, MetricState.SideIgnoredLower -> {
                drawCircle(colors.markerHalo, radius = radius * scale + halo, center = center)
                drawCircle(colors.data.chartPoint, radius = radius * scale, center = center)
            }

            else -> {
                // Learning or not compared: hollow and grey.
                drawCircle(colors.markerHalo, radius = radius * scale + halo, center = center)
                drawCircle(colors.textSecondary, radius = radius * scale - 0.5f * density, center = center, style = Stroke(1f * density))
            }
        }
    }
}

private fun DrawScope.drawDateAxis(
    plot: PlotArea,
    points: List<ChartPointUi>,
    measurer: TextMeasurer,
    style: TextStyle,
    text: UiText,
) {
    if (points.isEmpty()) return
    listOf(0, points.lastIndex).distinct().forEach { i ->
        val label = measurer.measure(text.dateShort(points[i].date), style)
        val x = (plot.x(i) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(x, size.height - label.size.height))
    }
}
