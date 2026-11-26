package io.github.alien2003.nightbaseline.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.components.GlassDialog
import io.github.alien2003.nightbaseline.ui.components.GroupCard
import io.github.alien2003.nightbaseline.ui.components.ModalHost
import io.github.alien2003.nightbaseline.ui.components.ModalKind
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.TitleColumn
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.CountComposition
import io.github.alien2003.nightbaseline.ui.shell.rememberPageTransitions
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens

private const val LIST_PANE_MIN = 320f
private const val LIST_PANE_MAX = 400f
private const val MAX_PANE_FONT_SCALE = 1.25f

/**
 * Settings (`screens.md` 7.7, `layouts.md` 5.4): stateless, everything comes from [state] and goes out through
 * [actions]. Per mode: Compact grouped single column with sub-screens; Medium / WideShort one centred column (max
 * 600 dp) with Sensitivity inline; two-pane modes (Expanded and up when the two-pane rule passes, and Book split at
 * the fold) list the sections at the start and show the selected section's content at the end; Tabletop shows the
 * current source and last read in the top half (view only) and the groups in the bottom half; Glance keeps only
 * Source, Notifications and About.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: SettingsActions = SettingsActions(),
    onSelectTab: (TopLevel) -> Unit = {},
    onMore: () -> Unit = {},
    scroll: ScrollState = rememberScrollState(),
) {
    CountComposition("SettingsScreen")
    val twoPanes = layout.twoPanes && layout.mode != LayoutMode.Tabletop && layout.mode != LayoutMode.Glance
    val page = if (twoPanes) SettingsPage.Main else state.page
    val back = if (page == SettingsPage.Main) null else actions.onBack
    val scrolls = remember(scroll) { PageScrolls(scroll) }
    ShellPage(
        layout = layout,
        tab = TopLevel.Settings,
        title = stringResource(pageTitle(page)),
        scroll = if (twoPanes) scroll else scrolls.of(page),
        modifier = modifier,
        onSelectTab = onSelectTab,
        onBack = back,
        column =
            when {
                twoPanes || layout.verticalFold != null -> TitleColumn.atStart(layout.gutter)
                else -> TitleColumn.centred(layout.gutter, NbDimens.textMax)
            },
        overlay = { SourceChangeDialog(state, layout, actions) },
    ) { padding ->
        when {
            layout.mode == LayoutMode.Glance -> {
                GlanceSettings(state, actions, padding, scroll, onMore)
            }

            layout.mode == LayoutMode.Tabletop -> {
                TabletopSettings(state, layout, actions, padding, scroll)
            }

            twoPanes -> {
                TwoPaneSettings(state, layout, actions, padding, scroll)
            }

            else -> {
                // Sub-pages push and pop like screens (coloros-conformance.md P2): depth decides the direction. Each
                // page is opaque (the covered page never shows through the incoming one), scrolls on its own state
                // and keeps the padding it was laid out with while it leaves (review round 5).
                val transitions = rememberPageTransitions()
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { transitions.content(forward = depth(targetState) > depth(initialState)) },
                    label = "settingsPage",
                ) { shown ->
                    val own = remember { PaddingHolder(padding) }
                    if (shown == page) own.value = padding
                    SinglePaneColumn(layout, own.value, scrolls.of(shown)) {
                        PageContent(state, actions, shown, inlineSensitivity(layout))
                    }
                }
            }
        }
    }
}

/** The scroll state of each single-pane page: the main list uses the caller's, sub-pages their own. */
private class PageScrolls(
    private val main: ScrollState,
) {
    private val pages = mutableMapOf<SettingsPage, ScrollState>()

    fun of(page: SettingsPage): ScrollState = if (page == SettingsPage.Main) main else pages.getOrPut(page) { ScrollState(0) }
}

/** The padding a page was last laid out with while shown; plain (not state), read only by that page. */
private class PaddingHolder(
    var value: PaddingValues,
)

private fun pageTitle(page: SettingsPage): Int =
    when (page) {
        SettingsPage.Main -> R.string.settings_title
        SettingsPage.SourcePicker -> R.string.source_picker_title
        SettingsPage.Sensitivity -> R.string.settings_group_sensitivity
        SettingsPage.About -> R.string.settings_about
        SettingsPage.Licenses -> R.string.about_licenses
        SettingsPage.Privacy -> R.string.setup_privacy_policy
    }

/** Navigation depth of a page: the list 0, its sub-pages 1, licenses 2. */
private fun depth(page: SettingsPage): Int =
    when (page) {
        SettingsPage.Main -> 0
        SettingsPage.Licenses -> 2
        else -> 1
    }

private fun inlineSensitivity(layout: LayoutSpec): Boolean = layout.mode == LayoutMode.Medium || layout.mode == LayoutMode.WideShort

/** One page in single-pane form. */
@Composable
private fun PageContent(
    state: SettingsState,
    actions: SettingsActions,
    page: SettingsPage,
    inlineSensitivity: Boolean = false,
) {
    when (page) {
        SettingsPage.Main -> MainGroups(state, actions, inlineSensitivity)
        SettingsPage.SourcePicker -> SourcePickerContent(state, actions)
        SettingsPage.Sensitivity -> SensitivityContent(state, actions)
        SettingsPage.About -> AboutContent(state, actions)
        SettingsPage.Licenses -> LicensesContent(state)
        SettingsPage.Privacy -> PrivacyContent()
    }
}

/** Every group in order; Medium shows the sensitivity presets inline instead of a row that opens a sub-screen. */
@Composable
private fun MainGroups(
    state: SettingsState,
    actions: SettingsActions,
    inlineSensitivity: Boolean,
) {
    SourceGroup(state, actions)
    NotificationsGroup(state, actions)
    if (inlineSensitivity) {
        SensitivityContent(state, actions, title = true)
    } else {
        SensitivityRowGroup(state, actions)
    }
    AppearanceGroup(state, actions)
    PrivacyGroup(actions)
    AboutGroup(state, actions)
}

/** A scrolling column of cards, centred, max 600 dp, clear of the rail, bar and top bar. */
@Composable
private fun SinglePaneColumn(
    layout: LayoutSpec,
    padding: PaddingValues,
    scroll: ScrollState,
    content: @Composable () -> Unit,
) {
    // With a vertical (non-separating) fold the column is start-aligned so controls keep off the crease.
    val alignment = if (layout.verticalFold != null) Alignment.Start else Alignment.CenterHorizontally
    Column(
        Modifier
            .fillMaxSize()
            .background(NbTheme.colors.background)
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = layout.gutter),
        horizontalAlignment = alignment,
    ) { SettingsContainer { content() } }
}

/**
 * The one container rule of Settings (review round 4): every card, section header and footer of a page sits in this
 * column, max [NbDimens.textMax] wide, so they share one width and one start edge in every layout; the caller centres
 * the container (single pane) or starts it at the pane edge (detail pane, fold).
 */
@Composable
private fun SettingsContainer(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NbDimens.cardGap),
    ) { content() }
}

/** List-detail: sections at the start (split at the fold in Book), the selected section's content at the end. */
@Composable
private fun TwoPaneSettings(
    state: SettingsState,
    layout: LayoutSpec,
    actions: SettingsActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val direction = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(direction)
    val fold = layout.verticalFold
    val listWidth = fold?.let { it.positionDp.dp - start - layout.gutter - NbDimens.hingeClearance } ?: listPaneWidth()
    Row(Modifier.fillMaxSize().padding(start = start)) {
        Column(
            Modifier
                .width(listWidth + layout.gutter)
                .fillMaxHeight()
                .verticalScroll(rememberPaneScrollState())
                .padding(start = layout.gutter, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
        ) {
            SectionList(state, actions)
        }
        Spacer(Modifier.width(if (fold != null) NbDimens.hingeClearance * 2 else NbDimens.gutterMedium))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(
                    end = layout.gutter + padding.calculateEndPadding(direction),
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            SettingsContainer { DetailContent(state, actions) }
        }
    }
}

@Composable
private fun listPaneWidth(): Dp {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, MAX_PANE_FONT_SCALE)
    return (LIST_PANE_MIN * scale).coerceAtMost(LIST_PANE_MAX).dp
}

/** The section rows of the two-pane list, in one grouped card. */
@Composable
private fun SectionList(
    state: SettingsState,
    actions: SettingsActions,
) {
    val text = rememberUiText()
    val selected = state.page.section ?: state.section
    // One grouped card (conformance S3), like the single-pane list.
    GroupCard {
        SettingsSection.entries.forEachIndexed { i, section ->
            SectionRow(
                title = stringResource(section.title),
                summary = text.sectionSummary(section, state),
                selected = section == selected,
                onClick = { actions.onSelectSection(section) },
                showDivider = i > 0,
            )
        }
    }
}

/** Content of the detail pane: the selected section, or its sub-page (licenses, privacy policy) with a back row. */
@Composable
private fun DetailContent(
    state: SettingsState,
    actions: SettingsActions,
) {
    val section = state.page.section ?: state.section
    if (state.page == SettingsPage.Licenses) {
        BackRow(stringResource(pageTitle(SettingsPage.Licenses)), actions.onBack)
        LicensesContent(state)
        return
    }
    PaneHeading(stringResource(section.title), Modifier.padding(top = NbSpace.s5))
    when (section) {
        SettingsSection.Source -> {
            SourceGroup(state, actions, title = false)
            PaneHeading(stringResource(R.string.source_picker_title), Modifier.padding(top = NbSpace.s5))
            SourcePickerContent(state, actions)
        }

        SettingsSection.Notifications -> {
            NotificationsGroup(state, actions, title = false)
        }

        SettingsSection.Sensitivity -> {
            SensitivityContent(state, actions)
        }

        SettingsSection.Appearance -> {
            AppearanceGroup(state, actions, title = false)
        }

        SettingsSection.Privacy -> {
            PrivacyGroup(actions, title = false, policyRow = false)
            PaneHeading(stringResource(R.string.setup_privacy_policy), Modifier.padding(top = NbSpace.s5))
            PrivacyContent()
        }

        SettingsSection.About -> {
            AboutContent(state, actions)
        }
    }
}

/** A heading row with a back button inside a pane (sub-pages in a detail pane or the tabletop bottom half). */
@Composable
private fun BackRow(
    title: String,
    onBack: () -> Unit,
) {
    Row(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShellRoundButton(NbIcons.ArrowBack, stringResource(R.string.a11y_back), onClick = onBack)
        PaneHeading(title, Modifier.weight(1f).padding(start = NbSpace.s2))
    }
}

/**
 * Tabletop (`layouts.md` 5.4): the top half shows the title, the current source and its last read (view only, nothing
 * interactive above the fold); the bottom half scrolls the groups, or the open sub-page under a back row.
 */
@Composable
private fun TabletopSettings(
    state: SettingsState,
    layout: LayoutSpec,
    actions: SettingsActions,
    padding: PaddingValues,
    scroll: ScrollState,
) {
    val fold = layout.horizontalFold?.positionDp?.dp ?: (layout.heightDp / 2).dp
    val direction = LocalLayoutDirection.current
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
            verticalArrangement = Arrangement.spacedBy(NbSpace.s4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NbText(
                stringResource(R.string.settings_title),
                style = NbTheme.typography.largeTitle,
                color = NbTheme.colors.textPrimary,
                modifier = Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth().semantics { heading() },
            )
            NbCard(Modifier.widthIn(max = NbDimens.textMax).fillMaxWidth()) {
                NbText(
                    stringResource(R.string.settings_source),
                    style = NbTheme.typography.labelSmall,
                    color = NbTheme.colors.textSecondary,
                )
                NbText(state.sourceLabel, style = NbTheme.typography.headline, color = NbTheme.colors.textPrimary)
                NbText(lastReadSummary(state), style = NbTheme.typography.body, color = NbTheme.colors.textSecondary)
            }
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
        ) {
            SettingsContainer {
                if (state.page != SettingsPage.Main) BackRow(stringResource(pageTitle(state.page)), actions.onBack)
                PageContent(state, actions, state.page, inlineSensitivity(layout))
            }
        }
    }
}

/** Glance (`layouts.md` 5.4): title row with "More", then Source, Notifications and About only. */
@Composable
private fun GlanceSettings(
    state: SettingsState,
    actions: SettingsActions,
    padding: PaddingValues,
    scroll: ScrollState,
    onMore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(padding)
            .padding(horizontal = NbDimens.gutterGlance),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.page !=
                SettingsPage.Main
            ) {
                ShellRoundButton(NbIcons.ArrowBack, stringResource(R.string.a11y_back), onClick = actions.onBack)
            }
            NbText(
                stringResource(pageTitle(state.page)),
                style = NbTheme.typography.title,
                color = NbTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = NbSpace.s2).semantics { heading() },
            )
            ShellRoundButton(NbIcons.MoreVertical, stringResource(R.string.action_more), onClick = onMore)
        }
        when (state.page) {
            SettingsPage.Main -> {
                SourceGroup(state, actions)
                NotificationsGroup(state, actions)
                AboutGroup(state, actions)
            }

            else -> {
                PageContent(state, actions, state.page)
            }
        }
    }
}

/**
 * `ST-SOURCE-CHANGE`: the confirmation dialog (`coloros-conformance.md` H1), docked at the bottom on phone-width
 * windows (16 dp margins above the navigation inset), centred on wider windows, in the end pane in Book (never across
 * the fold) and in the bottom half in Tabletop.
 */
@Composable
private fun SourceChangeDialog(
    state: SettingsState,
    layout: LayoutSpec,
    actions: SettingsActions,
) {
    val bookFold = layout.verticalFold?.takeIf { layout.mode == LayoutMode.Book }
    val tabletopFold = layout.horizontalFold?.takeIf { layout.mode == LayoutMode.Tabletop }
    val docked = bookFold == null && tabletopFold == null && layout.widthDp < ShellDimens.DIALOG_DOCK_MAX_WIDTH
    val placement =
        when {
            bookFold != null -> Modifier.padding(start = bookFold.positionDp.dp + NbDimens.hingeClearance)
            tabletopFold != null -> Modifier.padding(top = tabletopFold.positionDp.dp + NbDimens.hingeClearance)
            else -> Modifier
        }
    val alignment = if (docked) Alignment.BottomCenter else Alignment.Center
    ModalHost(
        state.pendingSource,
        onDismiss = actions.onCancelSource,
        kind = if (docked) ModalKind.DockedDialog else ModalKind.Dialog,
    ) { source ->
        // Scrolls when the window is too short for the dialog (cover screen, tabletop half, 200 % font).
        BoxWithConstraints(Modifier.fillMaxSize().then(placement)) {
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                contentAlignment = alignment,
            ) {
                GlassDialog(
                    title = stringResource(R.string.source_change_title, source.label),
                    body = stringResource(R.string.source_change_body, source.label),
                    confirm = stringResource(R.string.action_switch),
                    onConfirm = actions.onConfirmSource,
                    dismiss = stringResource(R.string.action_cancel),
                    onDismiss = { dismiss() },
                    modifier =
                        if (docked) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(ShellDimens.dialogMargin)
                        } else {
                            Modifier.padding(layout.gutter)
                        },
                )
            }
        }
    }
}
