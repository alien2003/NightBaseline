package io.github.alien2003.nightbaseline.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.LearningCard
import io.github.alien2003.nightbaseline.ui.components.MetricGlyphIcon
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NbTextButton
import io.github.alien2003.nightbaseline.ui.components.NightStateCard
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.PrimaryButton
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDataDimens
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.dataType

/** Minimum width of one tile in a multi-column grid (`screens.md` 7.1). */
internal val TileMinWidth: Dp = 260.dp

/** Columns of the metric grid for a content width: 2 when two tiles of [TileMinWidth] fit, else 1. */
internal fun tileColumns(contentWidth: Dp): Int = if (contentWidth >= TileMinWidth * 2 + NbDimens.cardGap) 2 else 1

/**
 * The night block of Home and Night detail (`states.md` 2): the summary card with its action (`S-ALL`, `S-SOME`,
 * `S-DEV`), the learning card (`L0`, `L1`..`L6`), the loading skeleton or the state card of a problem.
 */
@Composable
internal fun NightBlock(
    night: NightUi,
    source: String,
    modifier: Modifier = Modifier,
    lastNightShort: Boolean = false,
    onMetric: (MetricId) -> Unit = {},
    onStateAction: (NightState) -> Unit = {},
    onStateSecondary: (NightState) -> Unit = {},
    onHowItWorks: () -> Unit = {},
    showLabel: Boolean = true,
    maxWidth: Dp = NbDimens.textMax,
) {
    val text = rememberUiText()
    when {
        night.state == NightState.Loading -> {
            LoadingCard(modifier)
        }

        night.state.hasSummary -> {
            val single = night.movedMetrics.singleOrNull()
            NightSummaryCard(
                night,
                source,
                modifier,
                action = text.nightAction(night, source),
                onAction = {
                    if (night.state == NightState.SomeMoved &&
                        single != null
                    ) {
                        onMetric(single.metric)
                    } else {
                        onStateAction(night.state)
                    }
                },
                showLabel = showLabel,
                maxWidth = maxWidth,
            )
        }

        night.state == NightState.Learning || night.state == NightState.LearningStart -> {
            LearningCard(night, source, onHowItWorks = onHowItWorks, modifier = modifier, lastNightShort = lastNightShort)
        }

        else -> {
            NightStateCard(
                night,
                source,
                modifier,
                onPrimary = { onStateAction(night.state) },
                onSecondary = { onStateSecondary(night.state) },
            )
        }
    }
}

/**
 * The "Not recorded by {source}: HRV, Skin temperature" row (`states.md` 4.1) for optional metrics the source never
 * writes, with an info button that opens the not-provided sheet. Hidden when the list is empty.
 */
@Composable
internal fun NotRecordedRow(
    metrics: List<MetricId>,
    source: String,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (metrics.isEmpty()) return
    val text = rememberUiText()
    val colors = NbTheme.colors
    Row(
        modifier.widthIn(max = NbDimens.textMax).fillMaxWidth().padding(start = NbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        MetricGlyphIcon(MetricState.NotProvided.glyph, colors.statusNeutral)
        NbText(
            text.string(R.string.home_not_recorded_row, source, text.list(metrics.map { text.name(it) })),
            style = NbTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NbIconButton(NbIcons.Info, stringResource(R.string.action_learn_more), onClick = onInfo, tint = colors.textSecondary)
    }
}

/**
 * A ColorOS section header above a group of cards (conformance K2, T5, S2): 12 sp medium in `text.tertiary`, outside
 * the cards, its text 32 dp from the screen edge (16 dp inside the content column), about 24 dp below the previous
 * card and 8 dp above the next; heading semantics.
 */
@Composable
internal fun BlockHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    NbText(
        title,
        style = NbTheme.dataType.sectionHeader,
        color = NbTheme.colors.textTertiary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    top = NbDataDimens.sectionHeaderTop,
                    start = NbDataDimens.sectionHeaderStart,
                    end = NbDataDimens.sectionHeaderStart,
                ).semantics { heading() },
    )
}

/** "What to expect" while learning (`screens.md` 7.6). */
@Composable
internal fun ExpectCard(modifier: Modifier = Modifier) {
    NbCard(modifier.fillMaxWidth()) {
        NbText(
            stringResource(R.string.home_expect_title),
            style = NbTheme.dataType.itemTitle,
            color = NbTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        NbText(stringResource(R.string.home_expect_body), style = NbTheme.dataType.summary, color = NbTheme.colors.textSecondary)
    }
}

/**
 * "How your usual range works" as a card (`states.md` 8): the detail pane of two-pane layouts when the night has no
 * metric to show (learning start, no data, Health Connect missing), and the longer explanation next to state cards.
 */
@Composable
internal fun ExplainerCard(
    modifier: Modifier = Modifier,
    meaning: Boolean = false,
) {
    val colors = NbTheme.colors
    NbCard(modifier.fillMaxWidth()) {
        NbText(
            stringResource(R.string.explainer_title),
            style = NbTheme.dataType.itemTitle,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        listOf(R.string.explainer_range, R.string.explainer_learning, R.string.explainer_outside).forEach {
            NbText(stringResource(it), style = NbTheme.dataType.summary, color = colors.textSecondary)
        }
        if (meaning) {
            NbText(
                stringResource(R.string.explainer_meaning_title),
                style = NbTheme.dataType.itemTitle,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            NbText(stringResource(R.string.explainer_meaning), style = NbTheme.dataType.summary, color = colors.textSecondary)
            NbText(stringResource(R.string.explainer_care), style = NbTheme.dataType.summary, color = colors.textSecondary)
        }
    }
}

/**
 * The primary and secondary actions of a night state as a button row, for layouts that show the card text where
 * nothing may be interactive (the tabletop top half) and put the controls in the bottom half.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NightActionsRow(
    night: NightUi,
    source: String,
    onMetric: (MetricId) -> Unit,
    onStateAction: (NightState) -> Unit,
    onStateSecondary: (NightState) -> Unit,
    onHowItWorks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberUiText()
    val primary = text.nightAction(night, source)
    val secondary = text.nightSecondaryAction(night)
    if (primary == null && secondary == null) return
    val single = night.movedMetrics.singleOrNull()
    val learning = night.state == NightState.Learning || night.state == NightState.LearningStart
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        primary?.let { label ->
            val onClick = {
                when {
                    learning -> onHowItWorks()
                    night.state == NightState.SomeMoved && single != null -> onMetric(single.metric)
                    else -> onStateAction(night.state)
                }
            }
            if (night.state.hasSummary || learning) NbTextButton(label, onClick) else PrimaryButton(label, onClick)
        }
        secondary?.let { NbTextButton(it, onClick = { onStateSecondary(night.state) }) }
    }
}
