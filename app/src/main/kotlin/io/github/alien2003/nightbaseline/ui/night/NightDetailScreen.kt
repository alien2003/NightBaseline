package io.github.alien2003.nightbaseline.ui.night

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.DisclaimerFooter
import io.github.alien2003.nightbaseline.ui.components.InlineBanner
import io.github.alien2003.nightbaseline.ui.components.LearningProgress
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NightSummaryCard
import io.github.alien2003.nightbaseline.ui.components.SecondaryButton
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.history.CompactNightSummary
import io.github.alien2003.nightbaseline.ui.home.BlockHeading
import io.github.alien2003.nightbaseline.ui.home.FitColumn
import io.github.alien2003.nightbaseline.ui.home.GlanceHome
import io.github.alien2003.nightbaseline.ui.home.HomeActions
import io.github.alien2003.nightbaseline.ui.home.LoadingCard
import io.github.alien2003.nightbaseline.ui.home.MetricGrid
import io.github.alien2003.nightbaseline.ui.home.NightActionsRow
import io.github.alien2003.nightbaseline.ui.home.NightBlock
import io.github.alien2003.nightbaseline.ui.home.NotRecordedRow
import io.github.alien2003.nightbaseline.ui.home.ScreenFrame
import io.github.alien2003.nightbaseline.ui.home.TABLETOP_COMPACT_FONT
import io.github.alien2003.nightbaseline.ui.home.contentWidth
import io.github.alien2003.nightbaseline.ui.home.tileColumns
import io.github.alien2003.nightbaseline.ui.home.topInset
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.dataType

/**
 * Night detail (`screens.md` 7.2): the Home view of one night. Top bar with back, the night label as title and
 * previous / next chevrons (also Left / Right keys and a horizontal swipe on the summary); then the banner, the
 * summary or state card, the "Measurements" heading with how many nights the ranges use, every metric tile with its
 * band and the sentence that explains its state, the "Not recorded by" row and the disclaimer. No history strip.
 * Layouts: one column (Compact), a two-column grid (Medium and wider), the summary and the tiles on either side of a
 * book fold, the summary above and the controls below a tabletop fold, and the glance list. Stateless.
 *
 * @param scrollState scroll of the main column; hoisted for state restoration and the screenshot tests.
 */
@Composable
fun NightDetailScreen(
    state: NightDetailState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: NightDetailActions = NightDetailActions(),
    scrollState: ScrollState = rememberScrollState(),
) {
    val text = rememberUiText()
    val keys = Modifier.nightKeys(state, actions)
    ScreenFrame(
        layout = layout,
        title = text.nightLabel(state.night),
        subtitle = null,
        scroll = scrollState,
        selectedTab = state.section,
        onSelectTab = actions.onSelectTab,
        modifier = modifier.then(keys),
        largeTitle = false,
        onBack = actions.onBack,
        actions = { if (layout.mode != LayoutMode.Tabletop) PrevNextIcons(state, actions) },
    ) { padding, contentTop ->
        val fold = layout.horizontalFold
        when {
            layout.mode == LayoutMode.Glance -> {
                GlanceNight(state, layout, padding, actions, scrollState)
            }

            layout.mode == LayoutMode.Tabletop && fold != null -> {
                TabletopNight(
                    state,
                    layout,
                    padding,
                    contentTop,
                    fold.positionDp.dp,
                    actions,
                    scrollState,
                )
            }

            layout.mode == LayoutMode.Book && layout.verticalFold != null -> {
                BookNight(
                    state,
                    layout,
                    padding,
                    contentTop,
                    actions,
                    scrollState,
                )
            }

            else -> {
                SinglePaneNight(state, layout, padding, contentTop, actions, scrollState)
            }
        }
    }
}

/**
 * The content of Night detail without the shell, for the detail pane of History (`screens.md` 7.2 Expanded): one
 * column of blocks in [columns] columns of tiles.
 */
@Composable
fun ColumnScope.NightDetailContent(
    state: NightDetailState,
    actions: NightDetailActions,
    columns: Int = 1,
) {
    NightBlocks(state, actions, columns, if (columns > 1) NbDimens.chartMax else NbDimens.textMax)
}

/** The chevrons in the top bar: round buttons in 48 dp targets; a missing neighbour keeps its slot so the other does not move. */
@Composable
private fun PrevNextIcons(
    state: NightDetailState,
    actions: NightDetailActions,
) {
    val previous = state.previous
    val next = state.next
    // The chevron points forward; mirrored it points back (and both follow the layout direction).
    if (previous != null) {
        ShellRoundButton(
            NbIcons.Chevron,
            stringResource(R.string.a11y_prev_night),
            onClick = { actions.onPrevious(previous) },
            modifier = Modifier.graphicsLayer { scaleX = -1f },
        )
    } else {
        Spacer(Modifier.size(NbDimens.targetMin))
    }
    if (next != null) {
        ShellRoundButton(NbIcons.Chevron, stringResource(R.string.a11y_next_night), onClick = { actions.onNext(next) })
    } else {
        Spacer(Modifier.size(NbDimens.targetMin))
    }
}

/** Previous / next as labelled buttons, for the tabletop bottom half and the glance list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrevNextButtons(
    state: NightDetailState,
    actions: NightDetailActions,
    modifier: Modifier = Modifier,
) {
    val previous = state.previous
    val next = state.next
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        previous?.let { SecondaryButton(stringResource(R.string.a11y_prev_night), onClick = { actions.onPrevious(it) }) }
        next?.let { SecondaryButton(stringResource(R.string.a11y_next_night), onClick = { actions.onNext(it) }) }
    }
}

/** Left / Right step to the neighbouring night, Esc goes back (`layouts.md` 10.2). */
@Composable
private fun Modifier.nightKeys(
    state: NightDetailState,
    actions: NightDetailActions,
): Modifier {
    val onPrevious by rememberUpdatedState(state.previous?.let { date -> { actions.onPrevious(date) } })
    val onNext by rememberUpdatedState(state.next?.let { date -> { actions.onNext(date) } })
    val onBack by rememberUpdatedState(actions.onBack)
    return onPreviewKeyEvent { event ->
        val handler =
            when {
                event.type != KeyEventType.KeyDown -> null
                event.key == Key.DirectionLeft -> onPrevious
                event.key == Key.DirectionRight -> onNext
                event.key == Key.Escape -> onBack
                else -> null
            }
        handler?.invoke()
        handler != null
    }
}

/** A horizontal swipe on the night block steps to the neighbouring night (start-to-end = previous). */
@Composable
private fun Modifier.swipeNights(
    state: NightDetailState,
    actions: NightDetailActions,
): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previous by rememberUpdatedState(state.previous)
    val next by rememberUpdatedState(state.next)
    val current by rememberUpdatedState(actions)
    return pointerInput(rtl) {
        val threshold = SWIPE_THRESHOLD.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, delta -> total += if (rtl) -delta else delta },
            onDragEnd = {
                when {
                    total > threshold -> previous?.let(current.onPrevious)
                    total < -threshold -> next?.let(current.onNext)
                }
            },
        )
    }
}

/** Banner, night block, measurements heading and window note, tiles, "Not recorded by" row, footer. */
@Composable
private fun ColumnScope.NightBlocks(
    state: NightDetailState,
    actions: NightDetailActions,
    columns: Int,
    maxWidth: Dp,
    night: Boolean = true,
    tiles: Boolean = true,
    footer: Boolean = true,
) {
    if (night) {
        state.shownBanner?.let {
            InlineBanner(
                it,
                state.source,
                Modifier.widthIn(max = maxWidth),
                onAction = { actions.onBannerAction(it) },
                onDismiss = { actions.onBannerDismiss(it) },
                maxWidth = maxWidth,
            )
        }
        NightBlock(
            state.night,
            state.source,
            Modifier.widthIn(max = maxWidth).swipeNights(state, actions),
            lastNightShort = state.lastNightShort,
            onMetric = actions.onMetric,
            onStateAction = actions.onStateAction,
            onStateSecondary = actions.onStateSecondary,
            onHowItWorks = actions.onHowItWorks,
            // The top bar title is the night label.
            showLabel = false,
            maxWidth = maxWidth,
        )
    }
    if (tiles) MeasurementBlocks(state, actions, columns, maxWidth)
    if (footer) DisclaimerFooter(onAbout = actions.onAbout)
}

@Composable
private fun MeasurementBlocks(
    state: NightDetailState,
    actions: NightDetailActions,
    columns: Int,
    maxWidth: Dp,
) {
    if (!state.hasTiles) return
    val text = rememberUiText()
    val night = state.night
    BlockHeading(stringResource(R.string.night_measurements), Modifier.widthIn(max = maxWidth))
    if (night.windowNights > 0 && night.state != NightState.Learning) {
        NbText(
            text.plural(R.plurals.night_range_window, night.windowNights, text.int(night.windowNights)),
            style = NbTheme.typography.bodySmall,
            color = NbTheme.colors.textSecondary,
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(start = NbSpace.s1),
        )
    }
    MetricGrid(night.metrics, state.source, columns, maxWidth, actions.onMetric, explain = true)
    NotRecordedRow(night.notRecorded, state.source, actions.onNotRecordedInfo)
}

@Composable
private fun SinglePaneNight(
    state: NightDetailState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    actions: NightDetailActions,
    scroll: ScrollState,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = tileColumns(contentWidth(maxWidth, padding, layout.gutter))
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
            NightBlocks(state, actions, columns, if (columns > 1) NbDimens.chartMax else NbDimens.textMax)
        }
    }
}

/** Book: the night block on the start side of the fold, the measurements on the end side, 12 dp clear of the hinge. */
@Composable
private fun BookNight(
    state: NightDetailState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    actions: NightDetailActions,
    scroll: ScrollState,
) {
    val fold = layout.verticalFold ?: return
    val direction = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(direction)
    val top = topInset(barHeight)
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    val startWidth = (fold.positionDp - fold.occludedDp / 2).dp - start - layout.gutter - NbDimens.hingeClearance
    Row(Modifier.fillMaxSize().padding(start = start, end = padding.calculateEndPadding(direction))) {
        Column(
            Modifier
                .padding(start = layout.gutter)
                .width(startWidth)
                .fillMaxHeight()
                .verticalScroll(rememberPaneScrollState())
                .padding(top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            NightBlocks(state, actions, 1, NbDimens.textMax, tiles = !state.hasTiles)
        }
        Spacer(Modifier.width(fold.occludedDp.dp + NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(end = NbSpace.s5, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
        ) {
            if (state.hasTiles) NightBlocks(state, actions, 1, NbDimens.textMax, night = false)
        }
    }
}

/**
 * Tabletop: the night summary (view only) above the fold; previous / next, the card's actions, the measurements and
 * the footer below it.
 */
@Composable
private fun TabletopNight(
    state: NightDetailState,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    foldY: Dp,
    actions: NightDetailActions,
    scroll: ScrollState,
) {
    val night = state.night
    Box(Modifier.fillMaxSize().padding(horizontal = layout.gutter)) {
        // Top half: view only and never scrolls (layouts.md 6.4); at large font it keeps the compact summary only.
        val large = LocalDensity.current.fontScale >= TABLETOP_COMPACT_FONT
        Box(
            Modifier
                .fillMaxWidth()
                .height(foldY - NbDimens.hingeClearance)
                .clipToBounds()
                .padding(top = topInset(barHeight), bottom = NbSpace.s3),
        ) {
            FitColumn(NbDimens.cardGap) {
                when {
                    night.state == NightState.Loading -> LoadingCard()
                    large -> CompactNightSummary(night, state.source, Modifier.widthIn(max = NbDimens.textMax))
                    else -> NightSummaryCard(night, state.source, Modifier.widthIn(max = NbDimens.textMax), showLabel = false)
                }
                if (night.state == NightState.Learning && !large) {
                    LearningProgress(
                        night.historyNights,
                        Modifier.widthIn(max = NbDimens.textMax),
                        skipped = if (state.lastNightShort) 1 else 0,
                    )
                }
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
            PrevNextButtons(state, actions, Modifier.widthIn(max = maxWidth))
            state.shownBanner?.let {
                InlineBanner(
                    it,
                    state.source,
                    Modifier.widthIn(max = maxWidth),
                    onAction = { actions.onBannerAction(it) },
                    onDismiss = { actions.onBannerDismiss(it) },
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
            NightBlocks(state, actions, columns, maxWidth, night = false)
        }
    }
}

/** Glance: as glance Home for this night, with back at the start of the label row and previous / next at the end. */
@Composable
private fun GlanceNight(
    state: NightDetailState,
    layout: LayoutSpec,
    padding: PaddingValues,
    actions: NightDetailActions,
    scroll: ScrollState,
) {
    val homeActions =
        HomeActions(
            onMetric = actions.onMetric,
            onStateAction = actions.onStateAction,
            onHowItWorks = actions.onHowItWorks,
            onBannerAction = actions.onBannerAction,
            onBannerDismiss = actions.onBannerDismiss,
            onMore = actions.onMore,
        )
    GlanceHome(
        night = state.night,
        metricsNight = state.night.takeIf { state.hasTiles },
        source = state.source,
        banner = state.shownBanner,
        quietDays = 0,
        padding = padding,
        widthDp = layout.widthDp,
        actions = homeActions,
        leading = { ShellRoundButton(NbIcons.ArrowBack, stringResource(R.string.a11y_back), onClick = actions.onBack) },
        trailing = { PrevNextButtons(state, actions, Modifier.padding(top = NbSpace.s3)) },
        scroll = scroll,
    )
}

private val SWIPE_THRESHOLD = 56.dp
