package io.github.alien2003.nightbaseline.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import io.github.alien2003.nightbaseline.ui.components.NbCard
import io.github.alien2003.nightbaseline.ui.components.NbIconButton
import io.github.alien2003.nightbaseline.ui.components.NbIcons
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.ShellRoundButton
import io.github.alien2003.nightbaseline.ui.components.TitleColumn
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.components.rememberPaneScrollState
import io.github.alien2003.nightbaseline.ui.interaction.ModalHost
import io.github.alien2003.nightbaseline.ui.interaction.ModalKind
import io.github.alien2003.nightbaseline.ui.interaction.ModalScope
import io.github.alien2003.nightbaseline.ui.interaction.NbStackHost
import io.github.alien2003.nightbaseline.ui.interaction.NbSwitcher
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.model.rememberUiText
import io.github.alien2003.nightbaseline.ui.shell.CountComposition
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
    scroll: ScrollState = rememberNbScrollState(),
    rootPage: SettingsPage? = null,
    setupAgain: SetupAgainConfirm = SetupAgainConfirm(),
) {
    CountComposition("SettingsScreen")
    val twoPanes = layout.twoPanes && layout.mode != LayoutMode.Tabletop && layout.mode != LayoutMode.Glance
    val singlePane = !twoPanes && layout.mode != LayoutMode.Glance && layout.mode != LayoutMode.Tabletop
    val page = if (twoPanes) SettingsPage.Main else state.page
    val scrolls = remember(scroll) { PageScrolls(scroll) }
    // Sub-pages are a stack of pages (ADR 0011, relation Depth) in every layout: the same push, pop and back gesture
    // as a pushed screen.
    val stack = pagePath(page, rootPage)
    val dialogs: @Composable () -> Unit = {
        SourceChangeDialog(state, layout, actions)
        SetupAgainDialog(setupAgain, layout)
    }
    if (singlePane) {
        // Every page is a whole shell page (top bar, content and, on the list, the bar): a pushed sub-page and the
        // back preview move its title and back button with it, as a pushed screen does.
        Box(modifier.fillMaxSize()) {
            SinglePanePages(state, layout, actions, page, stack, scrolls, onSelectTab)
            dialogs()
        }
        return
    }
    ShellPage(
        layout = layout,
        tab = TopLevel.Settings,
        title = stringResource(pageTitle(page)),
        scroll = scroll,
        modifier = modifier,
        onSelectTab = onSelectTab,
        onBack = if (page == SettingsPage.Main) null else actions.onBack,
        column = if (twoPanes) TitleColumn.atStart(layout.gutter) else titleColumn(layout),
        overlay = { dialogs() },
    ) { padding ->
        when {
            layout.mode == LayoutMode.Glance -> {
                GlanceSettings(state, actions, padding, stack, scrolls, onMore)
            }

            layout.mode == LayoutMode.Tabletop -> {
                TabletopSettings(state, layout, actions, padding, stack, scrolls)
            }

            else -> {
                TwoPaneSettings(state, layout, actions, padding, scroll)
            }
        }
    }
}

/** The title column of a single-pane page: at the start beside a vertical fold, centred over the content otherwise. */
private fun titleColumn(layout: LayoutSpec): TitleColumn =
    if (layout.verticalFold != null) TitleColumn.atStart(layout.gutter) else TitleColumn.centred(layout.gutter, NbDimens.textMax)

/**
 * `ST-SETUP-AGAIN`: the confirmation before "Set up again" runs onboarding again.
 *
 * @property open the dialog is shown.
 * @property onConfirm "Set up again".
 * @property onCancel "Cancel", Back, the scrim.
 */
@Stable
class SetupAgainConfirm(
    val open: Boolean = false,
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * The pages from the stack's root to [page]: the list (or a pushed route's [root]) first, then each sub-page's parent
 * chain (`About -> Licenses`).
 */
internal fun pagePath(
    page: SettingsPage,
    root: SettingsPage?,
): List<SettingsPage> {
    val path = generateSequence(page) { it.parent() }.toList().reversed()
    val start = root?.let { r -> path.indexOf(r).takeIf { it >= 0 } } ?: 0
    return path.drop(start)
}

/**
 * The single-pane pages as a framework stack (ADR 0011): each page is a whole, opaque shell page (top bar, content
 * and, on the list, the bar), so a push, a pop and a back gesture move its title and back button with it, exactly as
 * a pushed screen. Each page scrolls on its own state and lays out with its own shell padding.
 */
@Suppress("LongParameterList")
@Composable
private fun SinglePanePages(
    state: SettingsState,
    layout: LayoutSpec,
    actions: SettingsActions,
    page: SettingsPage,
    stack: List<SettingsPage>,
    scrolls: PageScrolls,
    onSelectTab: (TopLevel) -> Unit,
) {
    NbStackHost(stack = stack, key = { it }, onBack = actions.onBack, id = "settings") { shown ->
        val scroll = scrolls.of(shown)
        ShellPage(
            layout = layout,
            tab = TopLevel.Settings,
            title = stringResource(pageTitle(shown)),
            scroll = scroll,
            onSelectTab = onSelectTab,
            // The root of the stack shows the bar (the list) or the back button of a pushed route.
            onBack = if (shown == SettingsPage.Main) null else ({ if (shown == page) actions.onBack() }),
            column = titleColumn(layout),
        ) { padding ->
            SinglePaneColumn(layout, padding, scroll) {
                PageContent(state, actions, shown, inlineSensitivity(layout))
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

private fun pageTitle(page: SettingsPage): Int =
    when (page) {
        SettingsPage.Main -> R.string.settings_title
        SettingsPage.SourcePicker -> R.string.source_picker_title
        SettingsPage.Sensitivity -> R.string.settings_group_sensitivity
        SettingsPage.About -> R.string.settings_about
        SettingsPage.Licenses -> R.string.about_licenses
        SettingsPage.Privacy -> R.string.setup_privacy_policy
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
            .nbVerticalScroll(scroll, id = "settings-1")
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
                .nbVerticalScroll(rememberPaneScrollState(), id = "settings-2")
                .padding(start = layout.gutter, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s2),
        ) {
            SectionList(state, actions)
        }
        Spacer(Modifier.width(if (fold != null) NbDimens.hingeClearance * 2 else NbDimens.gutterMedium))
        // The detail pane: a section is an unordered peer of the others (Swap); a sub-page of a section (Licenses
        // under About) is a page deeper in the pane's own stack (Page), with its back gesture (ADR 0011).
        val section = state.page.section ?: state.section
        NbSwitcher(section, key = { it }, modifier = Modifier.weight(1f).fillMaxHeight(), id = "settings-section") { shown ->
            val stack = if (shown == section && state.page == SettingsPage.Licenses) DETAIL_LICENSES else DETAIL_SECTION
            NbStackHost(stack = stack, key = { it }, onBack = actions.onBack, id = "settings-detail") { detail ->
                val paneScroll = if (detail == DetailPage.Section) scroll else rememberNbScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .nbVerticalScroll(paneScroll, id = "settings-3")
                        .padding(
                            end = layout.gutter + padding.calculateEndPadding(direction),
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                ) {
                    SettingsContainer { DetailContent(state, actions, shown, licenses = detail == DetailPage.Licenses) }
                }
            }
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

/** The pages of the detail pane's stack. */
private enum class DetailPage { Section, Licenses }

private val DETAIL_SECTION = listOf(DetailPage.Section)

private val DETAIL_LICENSES = listOf(DetailPage.Section, DetailPage.Licenses)

/** Content of the detail pane: [section], or its sub-page (licenses) with a back row. */
@Composable
private fun DetailContent(
    state: SettingsState,
    actions: SettingsActions,
    section: SettingsSection,
    licenses: Boolean,
) {
    if (licenses) {
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
    stack: List<SettingsPage>,
    scrolls: PageScrolls,
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
        // The bottom half is the page stack (ADR 0011): a sub-page pushes over the groups like everywhere else.
        NbStackHost(
            stack = stack,
            key = { it },
            onBack = actions.onBack,
            modifier = Modifier.weight(1f),
            id = "settings-tabletop",
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .nbVerticalScroll(scrolls.of(shown), id = "settings-4")
                    .then(horizontal)
                    .padding(bottom = padding.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsContainer {
                    if (shown != SettingsPage.Main) BackRow(stringResource(pageTitle(shown)), actions.onBack)
                    PageContent(state, actions, shown, inlineSensitivity(layout))
                }
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
    stack: List<SettingsPage>,
    scrolls: PageScrolls,
    onMore: () -> Unit,
) {
    // Each page is a whole glance page in the framework's stack (ADR 0011): pushes, pops and the back gesture.
    NbStackHost(stack = stack, key = { it }, onBack = actions.onBack, id = "settings-glance") { shown ->
        GlancePage(state, actions, padding, shown, scrolls.of(shown), onMore)
    }
}

@Composable
private fun GlancePage(
    state: SettingsState,
    actions: SettingsActions,
    padding: PaddingValues,
    page: SettingsPage,
    scroll: ScrollState,
    onMore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .nbVerticalScroll(scroll, id = "settings-5")
            .padding(padding)
            .padding(horizontal = NbDimens.gutterGlance),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != SettingsPage.Main) {
                ShellRoundButton(NbIcons.ArrowBack, stringResource(R.string.a11y_back), onClick = actions.onBack)
            }
            NbText(
                stringResource(pageTitle(page)),
                style = NbTheme.typography.title,
                color = NbTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = NbSpace.s2).semantics { heading() },
            )
            ShellRoundButton(NbIcons.MoreVertical, stringResource(R.string.action_more), onClick = onMore)
        }
        when (page) {
            SettingsPage.Main -> {
                SourceGroup(state, actions)
                NotificationsGroup(state, actions)
                AboutGroup(state, actions)
            }

            else -> {
                PageContent(state, actions, page)
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
    ConfirmDialog(state.pendingSource, layout, onCancel = actions.onCancelSource) { source, modifier ->
        GlassDialog(
            title = stringResource(R.string.source_change_title, source.label),
            body = stringResource(R.string.source_change_body, source.label),
            confirm = stringResource(R.string.action_switch),
            onConfirm = actions.onConfirmSource,
            dismiss = stringResource(R.string.action_cancel),
            onDismiss = { dismiss() },
            modifier = modifier,
        )
    }
}

/** `ST-SETUP-AGAIN` (`coloros-conformance.md` H1), placed like [SourceChangeDialog]. */
@Composable
private fun SetupAgainDialog(
    confirm: SetupAgainConfirm,
    layout: LayoutSpec,
) {
    ConfirmDialog(if (confirm.open) Unit else null, layout, onCancel = confirm.onCancel) { _, modifier ->
        GlassDialog(
            title = stringResource(R.string.setup_again_title),
            body = stringResource(R.string.setup_again_body),
            confirm = stringResource(R.string.settings_setup_again),
            onConfirm = confirm.onConfirm,
            dismiss = stringResource(R.string.action_cancel),
            onDismiss = { dismiss() },
            modifier = modifier,
        )
    }
}

/**
 * A confirmation dialog of Settings while [value] is not null: docked at the bottom on phone-width windows (16 dp
 * margins above the navigation inset), centred on wider windows, in the end pane in Book (never across the fold) and
 * in the bottom half in Tabletop. [dialog] gets the dialog's placement modifier; `dismiss()` animates it out.
 */
@Composable
private fun <T : Any> ConfirmDialog(
    value: T?,
    layout: LayoutSpec,
    onCancel: () -> Unit,
    dialog: @Composable ModalScope.(value: T, modifier: Modifier) -> Unit,
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
        value,
        onDismiss = onCancel,
        kind = if (docked) ModalKind.DockedDialog else ModalKind.Dialog,
    ) { shown ->
        // Scrolls when the window is too short for the dialog (cover screen, tabletop half, 200 % font).
        BoxWithConstraints(Modifier.fillMaxSize().then(placement)) {
            Box(
                Modifier.fillMaxSize().nbVerticalScroll(rememberNbScrollState(), id = "settings-6").heightIn(min = maxHeight),
                contentAlignment = alignment,
            ) {
                dialog(
                    shown,
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
