package io.github.alien2003.nightbaseline.ui.learning

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.HistoryStrip
import io.github.alien2003.nightbaseline.ui.components.LearningCard
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.TonalButton
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.settings.ShellPage
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.dataType

/** Two tile columns need 2 x 260 dp plus the gap (`screens.md` 7.1 Medium). */
private val TwoColumnMin = 532.dp

/** Font scale from which the tabletop top half keeps only the title and the progress. */
private const val LARGE_FONT = 1.5f

/**
 * Home in the learning period (`screens.md` 7.6, states `L0` and `L1`..`L6`): the learning card ("n of 7 nights",
 * title, how many nights are left, "How it works"), "Already visible" (tiles with last night's values and a dotted
 * band), "What to expect", the history strip and the disclaimer. Stateless: [state] in, [actions] out.
 *
 * Per mode: Compact / CompactShort one column; Medium / WideShort one column with tiles in two columns; two-pane
 * layouts (Expanded and up when the rule passes, Book split at the fold) keep the card, expectations and strip at the
 * start and the tiles at the end; Tabletop shows the view-only summary in the top half and everything interactive in
 * the bottom half; Glance shows the progress, the title and one line per metric.
 */
@Composable
fun LearningScreen(
    state: LearningState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: LearningActions = LearningActions(),
    onSelectTab: (TopLevel) -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
) {
    val text = rememberUiText()
    ShellPage(
        layout = layout,
        tab = TopLevel.Home,
        title = stringResource(R.string.home_title),
        subtitle = text.nightLabel(state.night),
        scroll = scroll,
        modifier = modifier,
        onSelectTab = onSelectTab,
    ) { padding ->
        when {
            layout.mode == LayoutMode.Glance -> GlanceLearning(state, actions, padding, scroll)
            layout.mode == LayoutMode.Tabletop -> TabletopLearning(state, layout, actions, padding, scroll)
            layout.twoPanes -> TwoPaneLearning(state, layout, actions, padding, scroll)
            else -> SinglePaneLearning(state, layout, actions, padding, scroll)
        }
    }
}

@Composable
private fun SinglePaneLearning(
    state: LearningState,
    layout: LayoutSpec,
    actions: LearningActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val columns = if (layout.mode == LayoutMode.Medium || layout.mode == LayoutMode.WideShort) 2 else 1
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = layout.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) {
        LearningCard(state.night, state.source, onHowItWorks = actions.onHowItWorks, lastNightShort = state.lastNightShort)
        AlreadyVisible(state, columns, actions)
        WhatToExpect(Modifier.widthIn(max = NbDimens.textMax))
        Strip(state, actions)
        DisclaimerFooter(onAbout = actions.onAbout)
    }
}

@Composable
private fun Strip(
    state: LearningState,
    actions: LearningActions,
    individualTargets: Boolean = true,
) {
    HistoryStrip(
        state.recent,
        onNight = actions.onNight,
        onSeeAll = actions.onSeeAll,
        individualTargets = individualTargets,
        modifier = Modifier.widthIn(max = NbDimens.textMax),
    )
}

/** Start pane: card, expectations, strip, footer; end pane: the tiles (two columns when the pane is wide enough). */
@Composable
private fun TwoPaneLearning(
    state: LearningState,
    layout: LayoutSpec,
    actions: LearningActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(direction)
    val fold = layout.verticalFold
    val listWidth = fold?.let { it.positionDp.dp - start - layout.gutter - NbDimens.hingeClearance } ?: 400.dp
    val vertical = Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
    Row(Modifier.fillMaxSize().padding(start = start)) {
        Column(
            Modifier
                .width(listWidth + layout.gutter)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(start = layout.gutter)
                .then(vertical),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            LearningCard(state.night, state.source, onHowItWorks = actions.onHowItWorks, lastNightShort = state.lastNightShort)
            WhatToExpect()
            // In a narrow list pane the nights are not individually tappable (screens.md 4 HistoryStrip).
            Strip(state, actions, individualTargets = listWidth >= NbDimens.targetMin * 7 + NbDimens.cardPadding * 2)
            DisclaimerFooter(onAbout = actions.onAbout)
        }
        Spacer(Modifier.width(if (fold != null) NbDimens.hingeClearance * 2 else NbDimens.gutterMedium))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = layout.gutter + padding.calculateEndPadding(direction)),
        ) {
            val columns = if (maxWidth >= TwoColumnMin) 2 else 1
            Column(Modifier.fillMaxSize().verticalScroll(rememberPaneScrollState()).then(vertical)) {
                AlreadyVisible(state, columns, actions)
            }
        }
    }
}

/** Tabletop: the view-only summary fills the top half; "How it works", tiles, expectations and strip scroll below. */
@Composable
private fun TabletopLearning(
    state: LearningState,
    layout: LayoutSpec,
    actions: LearningActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val fold = layout.horizontalFold?.positionDp?.dp ?: (layout.heightDp / 2).dp
    val direction = LocalLayoutDirection.current
    // At large font the "n more nights" sentence moves to the bottom half so the view-only top half never clips.
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT
    val horizontal =
        Modifier.padding(
            start = padding.calculateStartPadding(direction) + layout.gutter,
            end = padding.calculateEndPadding(direction) + layout.gutter,
        )
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .height(fold - NbDimens.hingeClearance)
                .fillMaxWidth()
                .then(horizontal)
                .padding(top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                rememberUiText().nightLabel(state.night),
                style = NbTheme.dataType.segment,
                color = NbTheme.colors.textSecondary,
                modifier = Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
            )
            LearningSummary(state, Modifier.widthIn(max = NbDimens.textMax), body = !largeFont)
        }
        Spacer(Modifier.height(NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .then(horizontal)
                .padding(bottom = padding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (largeFont) {
                Text(
                    rememberUiText().nightBody(state.night, state.source),
                    style = NbTheme.typography.body,
                    color = NbTheme.colors.textSecondary,
                    modifier = Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
                )
            }
            TonalButton(stringResource(R.string.action_how_it_works), onClick = actions.onHowItWorks)
            AlreadyVisible(state, columns = 1, actions)
            WhatToExpect(Modifier.widthIn(max = NbDimens.textMax))
            Strip(state, actions)
        }
    }
}

/** Glance: night label with "More", progress, title, body and one line per metric. No strip, no footer. */
@Composable
private fun GlanceLearning(
    state: LearningState,
    actions: LearningActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val text = rememberUiText()
    val colors = NbTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = NbDimens.gutterGlance),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.nightLabel(state.night),
                style = NbTheme.dataType.statusCalm,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            NbIconButton(NbIcons.More, stringResource(R.string.action_more), onClick = actions.onMore)
        }
        Text(
            text.nightTitle(state.night, state.source),
            style = NbTheme.dataType.headline,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        LearningProgress(state.collected, skipped = if (state.lastNightShort) 1 else 0, modifier = Modifier.fillMaxWidth())
        Text(text.nightBody(state.night, state.source), style = NbTheme.typography.bodySmall, color = colors.textSecondary)
        GlanceMetricLines(state)
    }
}
