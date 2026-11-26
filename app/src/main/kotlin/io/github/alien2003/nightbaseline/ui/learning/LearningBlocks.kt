package io.github.alien2003.nightbaseline.ui.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.RangeBand
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.data
import io.github.alien2003.nightbaseline.ui.theme.dataType
import io.github.alien2003.nightbaseline.ui.theme.hairline

/**
 * The view-only learning summary (tabletop top half, `layouts.md` 6.4): title, progress and the "n more nights" body,
 * nothing interactive; without [body] when the half is too short for it (large font). Elsewhere the shared
 * `LearningCard` (with its "How it works" link) is used.
 */
@Composable
internal fun LearningSummary(
    state: LearningState,
    modifier: Modifier = Modifier,
    body: Boolean = true,
) {
    val text = rememberUiText()
    NbCard(modifier.fillMaxWidth()) {
        Text(
            text.nightTitle(state.night, state.source),
            style = NbTheme.dataType.headline,
            color = NbTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        LearningProgress(state.collected, skipped = if (state.lastNightShort) 1 else 0)
        if (body) {
            Text(text.nightBody(state.night, state.source), style = NbTheme.dataType.summary, color = NbTheme.colors.textSecondary)
        }
    }
}

/**
 * "Already visible" (`screens.md` 7.6 item 3): heading, one line on the dotted outline, then every metric with last
 * night's value and a dotted band outline (no status yet) as compact rows, split over [columns] cards. `L0` shows a
 * note instead (no values yet).
 */
@Composable
internal fun AlreadyVisible(
    state: LearningState,
    columns: Int,
    actions: LearningActions,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val maxWidth = if (columns > 1) NbDimens.chartMax else NbDimens.textMax
    Column(
        modifier.widthIn(max = maxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        // A ColorOS section header outside the card, the intro as grey text under it (conformance K2).
        Text(
            stringResource(R.string.home_already_visible),
            style = NbTheme.dataType.sectionHeader,
            color = colors.textTertiary,
            modifier =
                Modifier
                    .padding(
                        top = NbDataDimens.sectionHeaderTop,
                        start = NbDataDimens.sectionHeaderStart,
                        end = NbDataDimens.sectionHeaderStart,
                    ).semantics { heading() },
        )
        val intro = if (state.nothingYet) R.string.setup_learning_visible_empty else R.string.setup_learning_visible_body
        Text(
            stringResource(intro),
            style = NbTheme.dataType.summary,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = NbDataDimens.sectionHeaderStart),
        )
        if (state.nothingYet) return@Column
        val rows = state.night.metrics.chunked((state.night.metrics.size + columns - 1) / columns)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NbDimens.cardGap)) {
            rows.forEach { part ->
                Column(Modifier.weight(1f).solidCard(NbShapes.l)) {
                    part.forEachIndexed { i, metric -> VisibleRow(state, metric, actions, divider = i > 0) }
                }
            }
        }
    }
}

/**
 * One metric of "Already visible": name, last night's value and a small dotted band outline (the range is not known
 * yet, so there is no status chip on every row; the card heading says the period is learning). The whole row opens
 * Metric detail; one TalkBack node (`a11y_tile_learning`).
 */
@Composable
private fun VisibleRow(
    state: LearningState,
    metric: MetricUi,
    actions: LearningActions,
    divider: Boolean,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    val description = text.tileDescription(metric, state.source)
    Column(Modifier.fillMaxWidth()) {
        if (divider) {
            Spacer(
                Modifier
                    .padding(horizontal = NbSpace.s5)
                    .fillMaxWidth()
                    .height(hairline)
                    .background(colors.separator),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = NbDimens.rowMinHeight)
                .nbClickable(onClick = { actions.onMetric(metric.metric) }, shape = NbShapes.l, pressScale = false)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }.padding(horizontal = NbSpace.s5, vertical = NbDimens.rowPaddingVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s2)) {
                Text(text.name(metric.metric), style = NbTheme.dataType.itemTitle, color = colors.textPrimary)
                RangeBand(null, metric.value, metric.state, Modifier.widthIn(max = 160.dp))
            }
            Text(text.value(metric)?.text ?: "–", style = NbTheme.typography.numericSmall, color = colors.textPrimary)
            Icon(NbIcons.Chevron, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(NbDimens.iconInline))
        }
    }
}

/** The steps of "What to expect", in order; the first is "now" while learning. */
private val ExpectSteps =
    listOf(
        R.string.setup_expect_learning,
        R.string.setup_expect_range,
        R.string.setup_expect_adjust,
        R.string.setup_expect_notes,
    )

/**
 * "What to expect" (`screens.md` 7.6 item 4) as a short vertical timeline: the current step has a filled marker and a
 * "Now" label (shape and text, not colour alone), the later ones an outline marker. Each step is one TalkBack node.
 */
@Composable
internal fun WhatToExpect(modifier: Modifier = Modifier) {
    val colors = NbTheme.colors
    NbCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_expect_title),
            style = NbTheme.dataType.title,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Column {
            ExpectSteps.forEachIndexed { i, res ->
                val now = i == 0
                val line = stringResource(res)
                val description = if (now) stringResource(R.string.setup_expect_step_now, line) else line
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalArrangement = Arrangement.spacedBy(NbSpace.s4),
                ) {
                    TimelineMarker(now = now, last = i == ExpectSteps.lastIndex)
                    Column(
                        Modifier.weight(1f).padding(bottom = if (i == ExpectSteps.lastIndex) 0.dp else NbSpace.s5),
                        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
                    ) {
                        if (now) NowLabel()
                        Text(
                            line,
                            style = NbTheme.dataType.summary,
                            color = if (now) colors.textPrimary else colors.textSecondary,
                            fontWeight = if (now) FontWeight.Medium else null,
                        )
                    }
                }
            }
        }
    }
}

private val MarkerSize: Dp = 14.dp
private val MarkerTop: Dp = 4.dp

/** Marker and the line down to the next step (decorative). */
@Composable
private fun TimelineMarker(
    now: Boolean,
    last: Boolean,
) {
    val colors = NbTheme.colors
    Canvas(Modifier.width(MarkerSize).fillMaxHeight()) {
        val radius = MarkerSize.toPx() / 2
        val center = Offset(size.width / 2, radius + MarkerTop.toPx())
        val stroke = 2.dp.toPx()
        if (!last) drawLine(colors.separator, center, Offset(center.x, size.height), stroke)
        if (now) {
            drawCircle(colors.primary, radius, center)
        } else {
            drawCircle(colors.surface1, radius, center)
            drawCircle(colors.outline, radius - stroke / 2, center, style = Stroke(stroke))
        }
    }
}

/** The "Now" marker of the current timeline step: plain accent text, no chip (conformance K8). */
@Composable
private fun NowLabel() {
    Text(
        stringResource(R.string.setup_expect_now),
        style = NbTheme.dataType.marker,
        color = NbTheme.colors.data.primaryText,
    )
}

/** Glance learning (`layouts.md` 7): progress segments, title, body and one line per metric with its value. */
@Composable
internal fun GlanceMetricLines(state: LearningState) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    if (state.nothingYet) {
        Text(stringResource(R.string.setup_learning_visible_empty), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        return
    }
    state.night.metrics.forEach { metric ->
        val value = text.value(metric)?.text ?: "–"
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = NbSpace.s2)
                .clearAndSetSemantics { contentDescription = text.tileDescription(metric, state.source) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            MetricGlyphIcon(metric.state.glyph, colors.statusNeutral, Modifier.size(NbDimens.iconChip))
            Column(Modifier.weight(1f)) {
                Text(text.shortName(metric.metric), style = NbTheme.dataType.itemTitle, color = colors.textPrimary)
                Text(text.status(metric, state.source), style = NbTheme.typography.caption, color = colors.textSecondary)
            }
            Text(value, style = NbTheme.typography.numericSmall, color = colors.textPrimary)
        }
    }
}
