package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.interaction.FollowPresses
import io.github.alien2003.nightbaseline.ui.interaction.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.interaction.NbEasing
import io.github.alien2003.nightbaseline.ui.interaction.ShellPressState
import io.github.alien2003.nightbaseline.ui.interaction.rememberShellPressState
import io.github.alien2003.nightbaseline.ui.interaction.shellClickable
import io.github.alien2003.nightbaseline.ui.interaction.shellPressGlow
import io.github.alien2003.nightbaseline.ui.interaction.shellPressInput
import io.github.alien2003.nightbaseline.ui.interaction.shellPressScale
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens

/**
 * The floating bottom bar (`coloros-conformance.md` A1–A6, measured on ColorOS Clock): a capsule of the bar material
 * (acrylic blur + tint, thin top rim, faint bottom reflection band, soft shadow; solid gradient in the fallback),
 * 56 dp tall at font 1.0 and growing when labels wrap, hugging its 70 x 48 dp items 8 dp apart with 12 dp side
 * padding. **No selection indicator**: the selected destination has the filled icon and a bold label
 * (`nav.selected`), the others the outline icon and a regular label (`nav.unselected`); selection is carried by shape
 * and weight, not colour. A press shrinks the whole pill (0.98) with the touch-following glow clipped to it. The
 * caller centres it 16 dp above the screen's bottom edge (gesture navigation).
 *
 * @param sideMargin space the bar keeps free on each side while its labels fit without breaking a word (the layout
 *   gutter); when the longest words need it the bar uses that space too, so a label never breaks inside a word.
 * @param forced catalog only: shows pressed / focus on one destination.
 */
@Composable
fun FloatingNavBar(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    sideMargin: Dp = 0.dp,
    forced: Pair<TopLevel, ForcedInteraction>? = null,
) {
    val press = rememberShellPressState()
    Box(
        modifier =
            modifier
                .shellPressInput(press)
                .shellPressScale(press)
                .sizeIn(minHeight = ShellDimens.barHeight)
                .shellSurface(ShellMaterial.Bar, NbShapes.pill)
                .shellPressGlow(press, NbShapes.pill, forcedPressed = forced?.second == ForcedInteraction.Pressed)
                .padding(horizontal = ShellDimens.barPaddingHorizontal, vertical = ShellDimens.barPaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        HuggingRow(Modifier.selectableGroup(), gap = ShellDimens.barItemGap, slack = sideMargin * 2) {
            TopLevel.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    press = press,
                    showLabel = true,
                    focused = forced?.takeIf { it.first == destination }?.second == ForcedInteraction.Focus,
                    modifier = Modifier.sizeIn(minWidth = ShellDimens.barItemWidth, minHeight = ShellDimens.barItemHeight),
                )
            }
        }
    }
}

/**
 * The floating navigation rail (`coloros-conformance.md` B1): the bar's language turned vertical, a 56 dp capsule
 * of the same material with the same icons and selection (filled icon + bold label, no indicator), items 48 x 56 dp
 * 8 dp apart, labels under the icons (icons only in `WideShort`, the label then being the content description). It
 * grows with the labels at large font. The caller places it 16 dp from the start edge, centred vertically.
 * The labelled wide rail of earlier designs is gone: labels sit under the icons in every size class.
 */
@Composable
fun FloatingNavRail(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    forced: Pair<TopLevel, ForcedInteraction>? = null,
) {
    val press = rememberShellPressState()
    Column(
        modifier
            .shellPressInput(press)
            .shellPressScale(press)
            .width(IntrinsicSize.Max)
            .widthIn(min = ShellDimens.railWidth)
            .shellSurface(ShellMaterial.Rail, NbShapes.pill)
            .shellPressGlow(press, NbShapes.pill, forcedPressed = forced?.second == ForcedInteraction.Pressed)
            .padding(vertical = ShellDimens.railPaddingVertical, horizontal = ShellDimens.railPaddingHorizontal)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ShellDimens.railItemGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopLevel.entries.forEach { destination ->
            NavItem(
                destination = destination,
                selected = destination == selected,
                onClick = { onSelect(destination) },
                press = press,
                showLabel = showLabels,
                focused = forced?.takeIf { it.first == destination }?.second == ForcedInteraction.Focus,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sizeIn(
                            minWidth = NbDimens.targetMin,
                            minHeight = if (showLabels) ShellDimens.railItemHeight else NbDimens.targetMin,
                        ),
            )
        }
    }
}

@Composable
private fun NavItem(
    destination: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    press: ShellPressState,
    showLabel: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shell = ShellTokens.colors
    val label = stringResource(destination.label)
    val color = if (selected) shell.navSelected else shell.navUnselected
    // Labels wrap only between words (no hyphenation): the bar gives every item at least its longest word.
    val style = ShellTokens.type.navLabel.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, hyphens = Hyphens.None)
    val source = remember { MutableInteractionSource() }
    // A press on an item is a press on the whole bar or rail (one press node, ADR 0011).
    FollowPresses(press, source)
    Column(
        modifier
            .shellClickable(
                onClick = onClick,
                shape = NbShapes.pill,
                role = Role.Tab,
                forced = if (focused) ForcedInteraction.Focus else null,
                interactionSource = source,
            ).semantics {
                this.selected = selected
                if (!showLabel) contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        // With labels the icons share one top line and a wrapped label only grows downwards (the bar's items are
        // all as tall as the tallest one); the icon-only rail centres its icons.
        verticalArrangement = if (showLabel) Arrangement.Top else Arrangement.Center,
    ) {
        if (showLabel) Spacer(Modifier.height(ShellDimens.barItemTop))
        // Outline -> filled icon, label colour and weight: a step at the selection's change (ADR 0011: the bar shows the
        // target section from the first frame of the change; R1-8, a crossfade left the old item lit for 2-4 frames).
        NbIcon(
            if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(NbDimens.icon),
        )
        if (showLabel) {
            Spacer(Modifier.height(ShellDimens.navLabelGap))
            // No line limit: at large font the label wraps and the bar or rail grows (layouts.md 4.1, never cut).
            NbText(label, style = style, color = color, modifier = Modifier.padding(horizontal = NbSpaceHalf))
            Spacer(Modifier.height(ShellDimens.barItemTop))
        }
    }
}

/** Keeps a wrapped label off the item's edges. */
private val NbSpaceHalf: Dp = ShellDimens.navLabelGap

/**
 * A row that hugs its items ([gap] apart) while they fit and otherwise shares the width out (`layouts.md` 4.1): every
 * item gets at least its minimum intrinsic width (its longest word, so a label wraps between words and never inside
 * one) and the rest is shared equally. It first tries to stay [slack] narrower than the space it is given (the side
 * margins); only when the longest words do not fit there does it use that space, then the gaps. Every item gets the
 * height of the tallest one, so an item whose label wraps keeps its icon level with the others.
 */
@Composable
private fun HuggingRow(
    modifier: Modifier = Modifier,
    gap: Dp,
    slack: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val count = measurables.size.coerceAtLeast(1)
        var gapPx = gap.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val words = measurables.map { it.minIntrinsicWidth(Constraints.Infinity) }

        fun gaps() = gapPx * (measurables.size - 1).coerceAtLeast(0)
        val widths =
            if (!constraints.hasBoundedWidth || natural.sum() + gaps() <= constraints.maxWidth - slack.roundToPx()) {
                natural
            } else {
                val preferred = constraints.maxWidth - slack.roundToPx()
                val limit =
                    when {
                        words.sum() + gaps() <= preferred -> preferred
                        words.sum() + gaps() <= constraints.maxWidth -> constraints.maxWidth
                        else -> constraints.maxWidth.also { gapPx = 0 }
                    }
                share(words, (limit - gaps()).coerceAtLeast(0), count)
            }
        val tallest = measurables.mapIndexed { i, m -> m.minIntrinsicHeight(widths[i]) }.maxOrNull() ?: 0
        val height = tallest.coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeables =
            measurables.mapIndexed { i, m ->
                m.measure(Constraints(minWidth = widths[i], maxWidth = widths[i], minHeight = height, maxHeight = height))
            }
        val width = placeables.sumOf { it.width } + gaps()
        layout(width.coerceAtMost(constraints.maxWidth), height) {
            var x = 0
            placeables.forEach {
                it.place(x, 0)
                x += it.width + gapPx
            }
        }
    }
}

/**
 * [total] shared out over [count] items, each at least its [minimum]: items whose minimum is above the equal share
 * keep it, the others share what is left equally.
 */
internal fun share(
    minimum: List<Int>,
    total: Int,
    count: Int,
): List<Int> {
    val fixed = BooleanArray(minimum.size)
    var left = total
    var open = count
    var changed = true
    while (changed && open > 0) {
        changed = false
        val equal = left / open
        minimum.forEachIndexed { i, m ->
            if (!fixed[i] && m > equal) {
                fixed[i] = true
                left -= m
                open--
                changed = true
            }
        }
    }
    val equal = if (open > 0) (left / open).coerceAtLeast(0) else 0
    return minimum.mapIndexed { i, m -> if (fixed[i]) m else maxOf(equal, m) }
}

/**
 * Bottom clearance of scrolling content above the floating bar at its minimum height: bar + its 16 dp offset + 16 dp
 * (`content.bottomClearance` 88 dp). [AppScaffold][io.github.alien2003.nightbaseline.ui.shell.AppScaffold] adds the
 * measured extra height of a grown bar and a taller navigation inset.
 */
val BarClearance: Dp = ShellDimens.barHeight + ShellDimens.barBottomMin + ShellDimens.barContentGap
