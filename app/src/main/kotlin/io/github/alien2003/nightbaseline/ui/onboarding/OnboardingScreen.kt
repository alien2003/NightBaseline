package io.github.alien2003.nightbaseline.ui.onboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.material.solidCard
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

private const val ILLUSTRATION_MAX_FONT = 1.5f
private const val ILLUSTRATION_MIN_HEIGHT = 640f
private const val START_COLUMN_SHARE = 0.4f
private const val GRADIENT_ALPHA = 0.45f
private val TextColumnMax = 480.dp
private val CardMax = 840.dp

/**
 * The first run (`screens.md` 7.5, `states.md` 7.3, `layouts.md` 5.5): full screen, outside the shell (no navigation,
 * no glass), solid background with a quiet static gradient. Stateless: [state] in, [actions] out. Per mode: Compact
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
    scroll: ScrollState = rememberScrollState(),
) {
    val colors = NbTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val illustration = fontScale < ILLUSTRATION_MAX_FONT
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .background(Brush.verticalGradient(listOf(colors.primaryContainer.copy(alpha = GRADIENT_ALPHA), colors.background)))
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
    Text(stepTitle(state), style = style, color = NbTheme.colors.textPrimary, modifier = modifier.semantics { heading() })
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
            .verticalScroll(scroll)
            .padding(horizontal = layout.gutter, vertical = NbSpace.s5),
        verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
    ) {
        StepIndicator(state.step, dots = layout.mode != LayoutMode.Glance)
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
                .verticalScroll(scroll)
                .padding(horizontal = layout.gutter)
                .padding(top = NbSpace.s7, bottom = NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
        ) {
            StepIndicator(state.step)
            if (illustration) StepIllustration(state.illustration())
            Title(state)
            StepBody(state, actions)
        }
        StepActionRow(
            stepButtons(state, actions),
            actions.onBack,
            Modifier.padding(horizontal = layout.gutter, vertical = NbSpace.s4),
        )
    }
}

/** The start column of the wide modes: indicator and illustration, or the title in large type without it. */
@Composable
private fun StartColumn(
    state: OnboardingState,
    illustration: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NbSpace.s6, Alignment.CenterVertically)) {
        StepIndicator(state.step)
        if (illustration) StepIllustration(state.illustration()) else Title(state)
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
    Row(
        Modifier.fillMaxSize().padding(horizontal = layout.gutter),
        horizontalArrangement = Arrangement.spacedBy(NbSpace.s8),
    ) {
        StartColumn(state, illustration, Modifier.weight(START_COLUMN_SHARE).fillMaxHeight().verticalScroll(rememberScrollState()))
        Column(
            Modifier
                .weight(1f - START_COLUMN_SHARE)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(vertical = NbSpace.s7),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s5, Alignment.CenterVertically),
        ) {
            Column(Modifier.widthIn(max = TextColumnMax), verticalArrangement = Arrangement.spacedBy(NbSpace.s5)) {
                TextAndActions(state, actions, showTitle = illustration)
            }
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
                .solidCard(NbShapes.xl)
                .padding(NbSpace.s8),
            horizontalArrangement = Arrangement.spacedBy(NbSpace.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartColumn(state, illustration, Modifier.weight(START_COLUMN_SHARE))
            Column(
                Modifier.weight(1f - START_COLUMN_SHARE).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(NbSpace.s5),
            ) {
                TextAndActions(state, actions, showTitle = illustration)
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
                .verticalScroll(rememberScrollState()),
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
                .verticalScroll(scroll)
                .padding(bottom = NbSpace.s5),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s4),
        ) {
            StepBody(state, actions)
            StepActionRow(stepButtons(state, actions), actions.onBack)
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
    val startWidth = (fold?.positionDp ?: (layout.widthDp / 2)).dp - NbDimens.hingeClearance - layout.gutter
    Row(Modifier.fillMaxSize().padding(horizontal = layout.gutter)) {
        StartColumn(
            state,
            illustration,
            Modifier
                .width(startWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = NbSpace.s7),
        )
        Spacer(Modifier.width(NbDimens.hingeClearance * 2))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(vertical = NbSpace.s7),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s5, Alignment.CenterVertically),
        ) {
            TextAndActions(state, actions, showTitle = illustration)
        }
    }
}
