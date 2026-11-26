package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.SHELL_PRESS_SCALE_LARGE
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.rememberShellPressState
import io.github.alien2003.nightbaseline.ui.material.shellClickable
import io.github.alien2003.nightbaseline.ui.material.shellPressGlow
import io.github.alien2003.nightbaseline.ui.material.shellPressInput
import io.github.alien2003.nightbaseline.ui.material.shellPressScale
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
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
 * @param forced catalog only: shows pressed / focus on one destination.
 */
@Composable
fun FloatingNavBar(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    forced: Pair<TopLevel, ForcedInteraction>? = null,
) {
    val press = rememberShellPressState()
    Box(
        modifier =
            modifier
                .shellPressInput(press, SHELL_PRESS_SCALE_LARGE)
                .shellPressScale(press)
                .sizeIn(minHeight = ShellDimens.barHeight)
                .shellSurface(ShellMaterial.Bar, NbShapes.pill)
                .shellPressGlow(press, NbShapes.pill, forcedPressed = forced?.second == ForcedInteraction.Pressed)
                .padding(horizontal = ShellDimens.barPaddingHorizontal, vertical = ShellDimens.barPaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        HuggingRow(Modifier.selectableGroup(), gap = ShellDimens.barItemGap) {
            TopLevel.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
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
            .shellPressInput(press, SHELL_PRESS_SCALE_LARGE)
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
    showLabel: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shell = ShellTokens.colors
    val motion = NbTheme.motion
    val label = stringResource(destination.label)
    val color = if (selected) shell.navSelected else shell.navUnselected
    val style = ShellTokens.type.navLabel.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    val source = remember { MutableInteractionSource() }
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
        verticalArrangement = Arrangement.Center,
    ) {
        if (showLabel) Spacer(Modifier.height(ShellDimens.barItemTop))
        // Outline -> filled icon: a 100 ms crossfade (snap under reduced motion).
        Crossfade(
            targetState = selected,
            animationSpec = if (motion.reduced) snap() else tween(NbDurations.MICRO, easing = NbEasing.Standard),
            label = "navIcon",
        ) { filled ->
            NbIcon(
                if (filled) destination.selectedIcon else destination.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(NbDimens.icon),
            )
        }
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
 * A row that hugs its items ([gap] apart) while they fit and otherwise gives every item an equal share of the width
 * (each still at least 48 dp), so labels wrap instead of being cut (`layouts.md` 4.1). Items are centred vertically.
 */
@Composable
private fun HuggingRow(
    modifier: Modifier = Modifier,
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val gaps = gapPx * (measurables.size - 1).coerceAtLeast(0)
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val share =
            if (constraints.hasBoundedWidth && natural.sum() + gaps > constraints.maxWidth) {
                ((constraints.maxWidth - gaps) / measurables.size.coerceAtLeast(1)).coerceAtLeast(0)
            } else {
                null
            }
        val placeables =
            measurables.mapIndexed { i, m ->
                val w = share ?: natural[i]
                m.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = constraints.maxHeight))
            }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = placeables.sumOf { it.width } + gaps
        layout(width, height.coerceAtLeast(constraints.minHeight)) {
            var x = 0
            placeables.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
        }
    }
}

/**
 * Bottom clearance of scrolling content above the floating bar at its minimum height: bar + its 16 dp offset + 16 dp
 * (`content.bottomClearance` 88 dp). [AppScaffold][io.github.alien2003.nightbaseline.ui.shell.AppScaffold] adds the
 * measured extra height of a grown bar and a taller navigation inset.
 */
val BarClearance: Dp = ShellDimens.barHeight + ShellDimens.barBottomMin + ShellDimens.barContentGap
