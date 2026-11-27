package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.GroupPosition
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.NightRow
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.ShellTopBar
import io.github.alien2003.nightbaseline.ui.components.TitleColumn
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.interaction.NbLazyColumn
import io.github.alien2003.nightbaseline.ui.interaction.NbSwitcher
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbLazyListState
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.AppScaffold
import io.github.alien2003.nightbaseline.ui.shell.CountComposition
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.shellToolbarHeight
import java.time.LocalDate
import java.time.YearMonth

/** The summary of a selected night shown in place; `interactive` is false in the tabletop top half (view only). */
typealias NightPreviewSlot = @Composable (night: NightUi, interactive: Boolean) -> Unit

/**
 * History (`screens.md` 7.4, `layouts.md` 5.3): "which nights were unusual?". Stateless: everything comes from
 * [state], every interaction goes to [actions]. One `resolveLayout` decision ([layout], fold info included) picks the
 * arrangement:
 * - `Compact` / `CompactShort` / single-pane `Medium` and `Expanded`: one column (max 600 dp). List: filter chips and
 *   the feed with sticky month headers; a row opens Night detail. Calendar: month grid and legend; in Compact a day
 *   opens Night detail, from 600 dp the selected night's summary reflows below the grid.
 * - Two panes (`Expanded` / `Large` when the two-pane rule passes, `WideShort`, `Book` split exactly at the fold):
 *   list or calendar in the list pane (360 / 400 dp, or the start half), [nightPreview] of the selected night (default
 *   the newest) in the detail pane; a row or day selects.
 * - `Tabletop`: the selected night's summary in the top half (view only), list or calendar and all controls in the
 *   bottom half.
 * - `Glance`: list only, compact rows, the "More" button.
 *
 * One tab level only (review round 4): the filters are the centred ColorOS tab row of the list; the List / Calendar
 * switch is a round action in the top bar ([HistoryViewButton]), or at the end of the controls row where there is no
 * top bar (Tabletop).
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: HistoryActions = HistoryActions(),
    onSelectTab: (TopLevel) -> Unit = {},
    listState: LazyListState = rememberNbLazyListState(),
    nightPreview: NightPreviewSlot = { night, interactive ->
        HistoryNightPreview(night, state.source, interactive, onOpen = { actions.onOpenNight(night.wakeDate) })
    },
) {
    CountComposition("HistoryScreen")
    val density = LocalDensity.current
    val arrangement = HistoryArrangement.of(layout)
    var barHeight by remember { mutableStateOf(0.dp) }
    var toolbarHeight by remember { mutableStateOf(0.dp) }
    AppScaffold(
        layout = layout,
        selected = TopLevel.History,
        onSelect = onSelectTab,
        modifier = modifier,
        topBar = { padding ->
            if (arrangement.topBar) {
                HistoryTopBar(
                    state,
                    layout,
                    arrangement,
                    listState,
                    Modifier
                        .padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr))
                        .then(if (arrangement.twoPanes) Modifier.width(arrangement.listPaneOuter) else Modifier)
                        .onSizeChanged { size ->
                            // Measured at rest only: the large title scrolls with the content (conformance C4), so
                            // the content inset must not shrink while it collapses.
                            val atRest = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            if (atRest || barHeight == 0.dp) barHeight = with(density) { size.height.toDp() }
                        },
                    actions.onView,
                    onToolbarHeight = { toolbarHeight = it },
                    column =
                        if (arrangement.twoPanes) {
                            TitleColumn.atStart(arrangement.listGutter)
                        } else {
                            TitleColumn.centred(
                                layout.gutter,
                                NbDimens.textMax,
                                end =
                                    layout.gutter + padding.calculateRightPadding(LayoutDirection.Ltr),
                            )
                        },
                )
            }
        },
    ) { padding ->
        val content = HistoryContent(state, actions, arrangement, nightPreview)
        when (layout.mode) {
            LayoutMode.Glance -> {
                GlanceHistory(state, actions, padding, listState)
            }

            LayoutMode.Tabletop -> {
                TabletopHistory(content, layout, padding, listState)
            }

            else -> {
                if (arrangement.twoPanes) {
                    TwoPaneHistory(
                        content,
                        layout,
                        padding,
                        barHeight,
                        listState,
                    )
                } else {
                    SinglePaneHistory(content, layout, padding, barHeight, toolbarHeight, listState)
                }
            }
        }
    }
}

/** Where things go for one layout (derived once from [LayoutSpec]). */
private data class HistoryArrangement(
    val mode: LayoutMode,
    val twoPanes: Boolean,
    /** The view switch is a top-bar action (else it ends the first content row). */
    val toggleInBar: Boolean,
    /** A shell top bar exists (not in Glance and Tabletop). */
    val topBar: Boolean,
    /** Gutter of the list pane. */
    val listGutter: Dp,
    /** Width of the list pane content (two panes only). */
    val listWidth: Dp,
    /** A tap selects in place (two panes, tabletop, calendar reflow) instead of opening Night detail. */
    val selectsInPlace: Boolean,
    /** The calendar view shows the selected night below the grid (single pane from 600 dp). */
    val previewBelowCalendar: Boolean,
) {
    /** List pane including its start gutter (the top bar spans it in two-pane layouts). */
    val listPaneOuter: Dp get() = listGutter + listWidth

    companion object {
        fun of(layout: LayoutSpec): HistoryArrangement {
            val mode = layout.mode
            val twoPanes = layout.twoPanes || mode == LayoutMode.WideShort
            val rail = if (layout.navigation in RAILS) NbDimens.railArea.value else 0f
            val fold = layout.verticalFold
            // A book half narrower than the calendar pane (346 dp at 520 dpi) gets the compact gutter (layouts.md 3.3).
            val bookPane = fold?.let { it.positionDp - it.occludedDp / 2 - rail - NbDimens.hingeClearance.value }
            val listGutter = if (bookPane != null && bookPane < CALENDAR_PANE) NbDimens.gutterCompact else layout.gutter
            val listWidth = listWidth(mode, bookPane, listGutter)
            return HistoryArrangement(
                mode = mode,
                twoPanes = twoPanes,
                // The view switch is a round top-bar action wherever there is a top bar (review round 4).
                toggleInBar = mode != LayoutMode.Glance && mode != LayoutMode.Tabletop,
                topBar = mode != LayoutMode.Glance && mode != LayoutMode.Tabletop,
                listGutter = listGutter,
                listWidth = listWidth,
                selectsInPlace = twoPanes || mode == LayoutMode.Tabletop,
                previewBelowCalendar = !twoPanes && mode != LayoutMode.Tabletop && layout.widthDp >= MEDIUM_W,
            )
        }

        private fun listWidth(
            mode: LayoutMode,
            bookPane: Float?,
            gutter: Dp,
        ): Dp =
            when {
                bookPane != null -> (bookPane - gutter.value).dp
                mode == LayoutMode.Large || mode == LayoutMode.ExtraLarge -> LIST_PANE_MAX.dp
                else -> CALENDAR_PANE.dp
            }

        private val RAILS = setOf(NavigationKind.Rail, NavigationKind.RailIcons, NavigationKind.RailLabelled)
        private const val CALENDAR_PANE = 360f
        private const val LIST_PANE_MAX = 400f
        private const val MEDIUM_W = 600f
    }
}

@Composable
private fun HistoryTopBar(
    state: HistoryUiState,
    layout: LayoutSpec,
    arrangement: HistoryArrangement,
    listState: LazyListState,
    modifier: Modifier,
    onView: (HistoryView) -> Unit,
    onToolbarHeight: (Dp) -> Unit,
    column: TitleColumn,
) {
    val density = LocalDensity.current
    val large = layout.mode == LayoutMode.Compact
    val short = layout.mode == LayoutMode.CompactShort || layout.mode == LayoutMode.WideShort
    // Past the first item the exact offset is unknown; anything beyond the large title block reads as fully scrolled.
    // Read lazily by the bar (layer, layout, derived state), so scrolling the feed never recomposes it.
    val scrolled = {
        if (listState.firstVisibleItemIndex > 0) SCROLLED_PAST else with(density) { listState.firstVisibleItemScrollOffset.toDp() }
    }
    Box(modifier) {
        ShellTopBar(
            title = stringResource(R.string.history_title),
            large = large,
            height = shellToolbarHeight(short, large),
            scrollOffset = scrolled,
            column = column,
            onToolbarHeight = onToolbarHeight,
            actions = {
                if (arrangement.toggleInBar && !state.empty && !state.loading) {
                    HistoryViewButton(state.view, onView)
                }
            },
        )
    }
}

/** The list-pane content shared by the single-pane, two-pane and tabletop arrangements. */
private class HistoryContent(
    val state: HistoryUiState,
    val actions: HistoryActions,
    val arrangement: HistoryArrangement,
    val nightPreview: NightPreviewSlot,
) {
    private fun tap(date: LocalDate) = if (arrangement.selectsInPlace) actions.onSelect(date) else actions.onOpenNight(date)

    private fun dayTap(date: LocalDate) =
        if (arrangement.selectsInPlace || arrangement.previewBelowCalendar) actions.onSelect(date) else actions.onOpenNight(date)

    /** The selection is drawn where the selected night is also shown in place. */
    private val showSelection: Boolean get() = arrangement.selectsInPlace

    fun LazyListScope.items(calendarMinHeight: Dp = 0.dp) {
        state.access?.let { access ->
            item(key = "banner") { HistoryAccessBanner(access, onAction = { actions.onFixAccess(access) }, Modifier.padding(top = GAP)) }
        }
        when {
            state.loading -> {
                item(key = "loading") {
                    HistorySkeleton(rows = SKELETON_ROWS, Modifier.widthIn(max = NbDimens.textMax).padding(top = GAP))
                }
            }

            state.empty -> {
                item(key = "empty") { EmptyHistory(Modifier.widthIn(max = NbDimens.textMax).padding(top = GAP)) }
            }

            state.view == HistoryView.List -> {
                listItems()
            }

            else -> {
                calendarItems(calendarMinHeight)
            }
        }
    }

    /**
     * The one control row: the centred filter tab row (list view) and, without a top bar, the view switch at its end.
     * The calendar view has no filters; it keeps the row only for the view switch.
     */
    private fun LazyListScope.controlRow(filters: Boolean) {
        val viewButton = !arrangement.toggleInBar
        if (!filters && !viewButton) return
        item(key = "controls") {
            Row(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (filters) HistoryFilterTabs(state.filter, actions.onFilter)
                }
                if (viewButton) HistoryViewButton(state.view, actions.onView)
            }
        }
    }

    private fun LazyListScope.listItems() {
        controlRow(filters = true)
        if (state.visibleNights.isEmpty()) {
            item(key = "filter-empty") {
                HistoryEmptyCard(
                    modifier = Modifier.widthIn(max = NbDimens.textMax).padding(top = GAP),
                    title = null,
                    body =
                        stringResource(
                            if (state.filter == HistoryFilter.Gaps) R.string.history_filter_empty_gaps else R.string.history_filter_empty,
                        ),
                    action = stringResource(R.string.action_show_all),
                    onAction = { actions.onFilter(HistoryFilter.All) },
                )
            }
            return
        }
        val selected = state.selectedNight?.wakeDate
        state.visibleByMonth.forEach { (month, nights) ->
            stickyHeader(key = "month:$month") {
                val text = rememberUiText()
                Box(Modifier.fillMaxWidth().background(NbTheme.colors.background), contentAlignment = Alignment.TopCenter) {
                    HistoryMonthHeader(text.month(month.atDay(1)), Modifier.widthIn(max = NbDimens.textMax))
                }
            }
            itemsIndexed(nights, key = { _, night -> "night:${night.wakeDate.toEpochDay()}" }) { i, night ->
                NightRow(
                    night,
                    onClick = { tap(night.wakeDate) },
                    selected = showSelection && night.wakeDate == selected,
                    position = GroupPosition.of(i, nights.size),
                )
            }
        }
    }

    /**
     * The calendar, its legend and the preview as one block of at least [minHeight]: a month grid is short, and the
     * list must still scroll far enough for the large title to collapse fully into the toolbar title.
     */
    private fun LazyListScope.calendarItems(minHeight: Dp) {
        controlRow(filters = false)
        item(key = "calendar") {
            Column(Modifier.heightIn(min = minHeight), horizontalAlignment = Alignment.CenterHorizontally) {
                HistoryCalendar(
                    month = state.month,
                    nights = state.byDate,
                    today = state.today,
                    onDay = ::dayTap,
                    onMonth = actions.onMonth,
                    selected = state.selectedNight?.wakeDate?.takeIf { showSelection || arrangement.previewBelowCalendar },
                    firstMonth = state.firstMonth,
                    modifier = Modifier.padding(top = GAP),
                )
                HistoryLegend(
                    threshold = state.nights.firstOrNull()?.threshold ?: 2,
                    Modifier.widthIn(max = NbDimens.textMax).padding(top = GAP),
                )
                val night = state.selectedNight
                if (arrangement.previewBelowCalendar && night != null) {
                    Box(Modifier.padding(top = GAP)) { nightPreview(night, true) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    HistoryEmptyCard(
        title = stringResource(R.string.history_empty_title),
        body = stringResource(R.string.history_empty_body),
        modifier = modifier,
    )
}

@Composable
private fun HistoryList(
    content: HistoryContent,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    calendarMinHeight: Dp = 0.dp,
) {
    NbLazyColumn(
        id = "history-3",
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No list spacing: night rows join into one card per month; other items carry their own top gap.
        with(content) { items(calendarMinHeight) }
    }
}

@Composable
private fun SinglePaneHistory(
    content: HistoryContent,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    toolbarHeight: Dp,
    listState: LazyListState,
) {
    // The list starts below the collapsed bar (status bar + toolbar, the edge where month headers stick); the large
    // title block below the toolbar is content padding, so the list scrolls up 1:1 with the collapsing title
    // (`coloros-conformance.md` C4) instead of keeping the resting bar height as an empty band with a hard cut. The
    // toolbar is its measured height: it grows with the font scale.
    val short = layout.mode == LayoutMode.CompactShort || layout.mode == LayoutMode.WideShort
    val toolbar = maxOf(toolbarHeight, if (short) ShellDimens.toolbarShort else ShellDimens.toolbar)
    val statusBars = WindowInsets.statusBars
    val density = LocalDensity.current
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    val contentPadding =
        remember(layout.gutter, bottom, barHeight, toolbar, statusBars, density) {
            BelowToolbarPadding(layout.gutter, bottom, barHeight - toolbar + NbSpace.s3, statusBars, density)
        }
    // With the large title (Compact) the calendar block is tall enough for the list to scroll by the whole title
    // block: scroll range = top + content + bottom - viewport >= top - 8 dp when content >= viewport - bottom - 8 dp.
    var viewport by remember { mutableStateOf(0.dp) }
    val calendarMinHeight = if (layout.mode == LayoutMode.Compact) (viewport - bottom - NbSpace.s3).coerceAtLeast(0.dp) else 0.dp
    HistoryList(
        content,
        listState,
        contentPadding,
        Modifier
            .fillMaxSize()
            .padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr), end = padding.calculateRightPadding(LayoutDirection.Ltr))
            .windowInsetsPadding(statusBars.only(WindowInsetsSides.Top))
            .padding(top = toolbar)
            .onSizeChanged { viewport = with(density) { it.height.toDp() } },
        calendarMinHeight,
    )
}

/**
 * Content padding of the single-pane list: [gutter] at the sides, [bottom], and on top [belowToolbar] (the resting
 * bar below its toolbar, plus the gap) minus the status bar, which the list's own position already clears. The status
 * bar is read when the list measures, never in composition, so the first frame after the insets arrive is right.
 */
private class BelowToolbarPadding(
    private val gutter: Dp,
    private val bottom: Dp,
    private val belowToolbar: Dp,
    private val statusBars: WindowInsets,
    private val density: Density,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = gutter

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = gutter

    override fun calculateTopPadding(): Dp = (belowToolbar - with(density) { statusBars.getTop(this).toDp() }).coerceAtLeast(NbSpace.s3)

    override fun calculateBottomPadding(): Dp = bottom
}

@Composable
private fun TwoPaneHistory(
    content: HistoryContent,
    layout: LayoutSpec,
    padding: PaddingValues,
    barHeight: Dp,
    listState: LazyListState,
) {
    val arrangement = content.arrangement
    val start = padding.calculateLeftPadding(LayoutDirection.Ltr)
    val bottom = padding.calculateBottomPadding() + NbSpace.s5
    val fold = layout.verticalFold
    // Two panes have no large title: the bar keeps one height, so both panes simply start below it.
    Row(Modifier.fillMaxSize().padding(start = start, top = barHeight)) {
        HistoryList(
            content,
            listState,
            PaddingValues(start = arrangement.listGutter, top = NbSpace.s3, bottom = bottom),
            Modifier.width(arrangement.listWidth + arrangement.listGutter).fillMaxHeight(),
        )
        // On a separating fold the gap is the hinge plus 12 dp on each side; otherwise the pane spacer.
        Spacer(Modifier.width(if (fold != null) NbDimens.hingeClearance * 2 + fold.occludedDp.dp else PANE_SPACER))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .nbVerticalScroll(rememberPaneScrollState(), id = "history-1")
                .padding(end = layout.gutter + padding.calculateRightPadding(LayoutDirection.Ltr), top = NbSpace.s3, bottom = bottom),
        ) {
            val night = content.state.selectedNight
            if (night != null && !content.state.loading) {
                // Selecting another night (row, day, previous / next in the pane) is one change of the shown night: the
                // Pager kind, newer nights from the end side (ADR 0011).
                NbSwitcher(night, key = { it.wakeDate }, id = "history-detail", order = compareBy { it.wakeDate }) {
                    content.nightPreview(it, true)
                }
            }
        }
    }
}

@Composable
private fun TabletopHistory(
    content: HistoryContent,
    layout: LayoutSpec,
    padding: PaddingValues,
    listState: LazyListState,
) {
    val fold = layout.horizontalFold ?: return
    val topHalf = (fold.positionDp - fold.occludedDp / 2).dp - NbDimens.hingeClearance
    val colors = NbTheme.colors
    Column(Modifier.fillMaxSize().padding(start = padding.calculateLeftPadding(LayoutDirection.Ltr))) {
        // Top half: view only. It scrolls only as a last resort at very large font, so nothing is ever cut off.
        Column(
            Modifier
                .height(topHalf)
                .fillMaxWidth()
                .nbVerticalScroll(rememberNbScrollState(), id = "history-2")
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = layout.gutter, vertical = NbSpace.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
        ) {
            NbText(
                stringResource(R.string.history_title),
                style = NbTheme.typography.title,
                color = colors.textPrimary,
                modifier = Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth().semantics { heading() },
            )
            content.state.selectedNight?.let { content.nightPreview(it, false) }
        }
        Spacer(Modifier.height(NbDimens.hingeClearance * 2 + fold.occludedDp.dp))
        HistoryList(
            content,
            listState,
            PaddingValues(start = layout.gutter, end = layout.gutter, bottom = padding.calculateBottomPadding() + NbSpace.s5),
            Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun GlanceHistory(
    state: HistoryUiState,
    actions: HistoryActions,
    padding: PaddingValues,
    listState: LazyListState,
) {
    val colors = NbTheme.colors
    NbLazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(WindowInsets.statusBars.asPaddingValues()),
        id = "history-4",
        state = listState,
        contentPadding = PaddingValues(horizontal = NbDimens.gutterGlance, vertical = NbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NbText(
                    stringResource(R.string.history_title),
                    style = NbTheme.typography.title,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                ShellRoundButton(NbIcons.MoreVertical, stringResource(R.string.action_more), onClick = actions.onMore)
            }
        }
        state.access?.let { access -> item(key = "banner") { HistoryAccessBanner(access, onAction = { actions.onFixAccess(access) }) } }
        when {
            state.loading -> {
                item(key = "loading") { HistorySkeleton(rows = GLANCE_SKELETON_ROWS) }
            }

            state.empty -> {
                item(key = "empty") { EmptyHistory() }
            }

            else -> {
                items(state.nights, key = { "night:${it.wakeDate.toEpochDay()}" }) { night ->
                    GlanceNightRow(night, onClick = { actions.onOpenNight(night.wakeDate) })
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 6
private const val GLANCE_SKELETON_ROWS = 3
private val PANE_SPACER = 24.dp

/** Gap above an item of the History list (rows of one month have none). */
private val GAP = 12.dp

/** A scroll offset beyond the large title block and the small-title fade (the first item has scrolled away). */
private val SCROLLED_PAST = 400.dp
