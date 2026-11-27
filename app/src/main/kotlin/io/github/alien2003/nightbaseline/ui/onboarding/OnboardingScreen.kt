package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.github.alien2003.nightbaseline.ui.components.NbText
import io.github.alien2003.nightbaseline.ui.components.cardTag
import io.github.alien2003.nightbaseline.ui.interaction.nbVerticalScroll
import io.github.alien2003.nightbaseline.ui.interaction.rememberNbScrollState
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.controls

private const val ILLUSTRATION_MAX_FONT = 1.5f
private const val ILLUSTRATION_MIN_HEIGHT = 640f
private const val START_COLUMN_SHARE = 0.4f
private val TextColumnMax = 480.dp
private val CardMax = 840.dp

/**
 * The first run (`screens.md` 7.5, `states.md` 7.3, `layouts.md` 5.5): full screen, outside the shell (no navigation,
 * no glass), the plain neutral window background of every other screen (no tinted wash). Stateless: [state] in,
 * [actions] out. Per mode: Compact
 * with the primary button pinned at the bottom; CompactShort and Glance without illustration, everything scrolls;
 * Medium and WideShort two columns (start 40 %: illustration and step indicator, or the title in large type in
 * WideShort; end: text and actions, max 480 dp); Expanded and up the same two columns in a centred card (max 840 dp);
 * Tabletop illustration and title in the top half (view only), text and buttons in the bottom half; Book illustration
 * and indicator in the start half, text and buttons in the end half. The illustration is hidden at font scale >= 1.5.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    layout: LayoutSpec,
    modifier: Modifier = Modifier,
    actions: OnboardingActions = OnboardingActions(),
    scroll: ScrollState = rememberNbScrollState(),
) {
    val colors = NbTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val illustration = fontScale < ILLUSTRATION_MAX_FONT
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (layout.mode) {
            LayoutMode.Glance, LayoutMode.CompactShort -> {
                ScrollingStep(state, actions, layout, scroll)
            }

            LayoutMode.Compact -> {
                CompactStep(state, actions, layout, scroll, illustration && layout.heightDp >= ILLUSTRATION_MIN_HEIGHT)
            }

            LayoutMode.Medium, LayoutMode.WideShort -> {
                TwoColumnStep(state, actions, layout, scroll, illustration && layout.mode == LayoutMode.Medium)
            }

            LayoutMode.Expanded, LayoutMode.Large, LayoutMode.ExtraLarge -> {
                CardStep(state, actions, layout, scroll, illustration)
            }

            LayoutMode.Tabletop -> {
                TabletopStep(state, actions, layout, scroll, illustration)
            }

            LayoutMode.Book -> {
                BookStep(state, actions, layout, scroll, illustration)
            }
        }
    }
}

@Composable
private fun Title(
    state: OnboardingState,
    style: TextStyle = NbTheme.typography.largeTitle,
    modifier: Modifier = Modifier,
) {
    NbText(stepTitle(state), style = style, color = NbTheme.colors.textPrimary, modifier = modifier.semantics { heading() })
}

/** Title (unless the start column already shows it), body and the action row, for the end column of wide modes. */
@Composable
private fun ColumnScope.TextAndActions(
    state: OnboardingState,
    actions: OnboardingActions,
    showTitle: Boolean,
) {
    if (showTitle) Title(state, NbTheme.typography.headline)
    StepBody(state, actions)
    Spacer(Modifier.height(NbSpace.s3))
    StepActionRow(stepButtons(state, actions), actions.onBack)
}

/** CompactShort and Glance: indicator text, title, body and buttons in one scrolling column. */
@Composable
private fun ScrollingStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .nbVerticalScroll(scroll, id = "onboarding-1")
            .padding(horizontal = layout.gutter, vertical = NbSpace.s5),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        StepIndicator(state.step, dots = layout.mode != LayoutMode.Glance, onBack = backOf(state, actions))
        Title(state, NbTheme.typography.headline)
        StepBody(state, actions)
        StepActionRow(stepButtons(state, actions), actions.onBack)
    }
}

/** Compact: scrolling content, the buttons pinned at the bottom above the navigation inset. */
@Composable
private fun CompactStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
    illustration: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .nbVerticalScroll(scroll, id = "onboarding-2")
                .padding(horizontal = layout.gutter)
                .padding(top = NbSpace.s7, bottom = NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
        ) {
            StepIndicator(state.step, onBack = backOf(state, actions))
            if (illustration) StepIllustration(state.illustration())
            Title(state)
            StepBody(state, actions)
        }
        StepActionRow(
            stepButtons(state, actions),
            actions.onBack,
            Modifier.padding(horizontal = layout.gutter).padding(top = NbSpace.s3, bottom = NbSpace.s7),
        )
    }
}

/**
 * The start column of the wide modes: the step indicator on top (level with the title of the end column), then the
 * illustration at its wide size, or the title in large type without it.
 */
@Composable
private fun StartColumn(
    state: OnboardingState,
    actions: OnboardingActions,
    illustration: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NbSpace.s6)) {
        StepIndicator(state.step, onBack = backOf(state, actions))
        if (illustration) StepIllustration(state.illustration(), height = IllustrationHeightWide) else Title(state)
    }
}

/**
 * The two columns of Medium, WideShort and Book as one block (`layouts.md` 5.5): both columns start at the same top
 * edge (indicator level with the title) and the block is centred vertically in the window; it scrolls as one when it
 * is taller than the window, so the start column never floats on its own.
 */
@Composable
private fun BalancedColumns(
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .focusRingRoom()
                .nbVerticalScroll(scroll, id = "onboarding-3")
                .padding(FocusRingRoom)
                .heightIn(min = minHeight)
                .padding(vertical = NbSpace.s7),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, content = content)
        }
    }
}

/** Medium and WideShort: start 40 % (indicator + illustration or large title), end text + actions (max 480 dp). */
@Composable
private fun TwoColumnStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
    illustration: Boolean,
) {
    BalancedColumns(scroll, Modifier.padding(horizontal = layout.gutter)) {
        StartColumn(state, actions, illustration, Modifier.weight(START_COLUMN_SHARE))
        Spacer(Modifier.width(NbSpace.s8))
        Column(
            Modifier.weight(1f - START_COLUMN_SHARE).widthIn(max = TextColumnMax),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
        ) {
            TextAndActions(state, actions, showTitle = illustration)
        }
    }
}

/** Expanded and up: the two columns in a centred solid card, max 840 dp; the outside is background. */
@Composable
private fun CardStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
    illustration: Boolean,
) {
    Box(Modifier.fillMaxSize().padding(layout.gutter), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = CardMax)
                .fillMaxWidth()
                .cardTag()
                .solidCard(NbShapes.l)
                .padding(NbSpace.s8),
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s8),
            verticalAlignment = Alignment.Top,
        ) {
            StartColumn(state, actions, illustration, Modifier.weight(START_COLUMN_SHARE))
            // The body scrolls inside the card and the action row stays pinned at its bottom, so a tall step (the
            // partial-access note above the permission list) never pushes "Next" below the card's edge.
            Column(Modifier.weight(1f - START_COLUMN_SHARE)) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .focusRingRoom()
                        .nbVerticalScroll(scroll, id = "onboarding-4")
                        .padding(FocusRingRoom),
                    verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
                ) {
                    if (illustration) Title(state, NbTheme.typography.headline)
                    StepBody(state, actions)
                }
                // The same 8 dp + 2 x 16 dp gap as in TextAndActions (a spacer between two spaced items).
                StepActionRow(stepButtons(state, actions), actions.onBack, Modifier.padding(top = NbSpace.s5 * 2 + NbSpace.s3))
            }
        }
    }
}

/** Tabletop: indicator, illustration and title in the top half (view only); text and buttons in the bottom half. */
@Composable
private fun TabletopStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
    illustration: Boolean,
) {
    val fold = layout.horizontalFold?.positionDp?.dp ?: (layout.heightDp / 2).dp
    Column(Modifier.fillMaxSize().padding(horizontal = layout.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .height(fold - NbDimens.hingeClearance)
                .widthIn(max = TextColumnMax)
                .fillMaxWidth()
                .nbVerticalScroll(rememberNbScrollState(), id = "onboarding-5"),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s4, Alignment.CenterVertically),
        ) {
            StepIndicator(state.step)
            if (illustration) StepIllustration(state.illustration())
            Title(state, NbTheme.typography.headline)
        }
        Spacer(Modifier.height(NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = TextColumnMax)
                .fillMaxWidth()
                .focusRingRoom()
                .nbVerticalScroll(scroll, id = "onboarding-6")
                .padding(FocusRingRoom)
                .padding(bottom = NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
        ) {
            StepBody(state, actions)
            // Nothing interactive in the top half: Back stays with the other buttons here.
            StepActionRow(stepButtons(state, actions), actions.onBack, backHere = true)
        }
    }
}

/** Book: start half illustration + indicator, end half text + buttons; split exactly at the fold with clearance. */
@Composable
private fun BookStep(
    state: OnboardingState,
    actions: OnboardingActions,
    layout: LayoutSpec,
    scroll: ScrollState,
    illustration: Boolean,
) {
    val fold = layout.verticalFold
    val startWidth =
        (fold?.positionDp ?: (layout.widthDp / 2)).dp - (fold?.occludedDp ?: 0f).dp / 2 - NbDimens.hingeClearance - layout.gutter
    BalancedColumns(scroll, Modifier.padding(horizontal = layout.gutter)) {
        StartColumn(state, actions, illustration, Modifier.width(startWidth))
        Spacer(Modifier.width(NbDimens.hingeClearance * 2 + (fold?.occludedDp ?: 0f).dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NbSpace.s5)) {
            TextAndActions(state, actions, showTitle = illustration)
        }
    }
}

/** Room for the focus ring (2 dp outside the element, 2 dp wide) at the edges of a scroll container. */
private val FocusRingRoom: Dp = NbDimens.focusRingOffset + NbDimens.focusRingWidth

/**
 * Lays a scroll container out [FocusRingRoom] larger on every side than it reports, so it clips that much further
 * out and the focus ring of a button at its edge stays whole. Paired with `.padding(FocusRingRoom)` inside the
 * scroll, the content stays exactly where it was.
 */
private fun Modifier.focusRingRoom(): Modifier =
    layout { measurable, constraints ->
        val room = FocusRingRoom.roundToPx()
        val placeable = measurable.measure(constraints.offset(room * 2, room * 2))
        val width = (placeable.width - room * 2).coerceAtLeast(0)
        val height = (placeable.height - room * 2).coerceAtLeast(0)
        layout(width, height) { placeable.place(-room, -room) }
    }

/** The back action for the round button next to the step indicator, or null on steps without "Back". */
@Composable
private fun backOf(
    state: OnboardingState,
    actions: OnboardingActions,
): (() -> Unit)? = actions.onBack.takeIf { stepButtons(state, actions).back }
