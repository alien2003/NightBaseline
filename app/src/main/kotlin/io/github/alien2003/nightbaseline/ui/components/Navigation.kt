package io.github.alien2003.nightbaseline.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.material.ForcedInteraction
import io.github.alien2003.nightbaseline.ui.material.ShellMaterial
import io.github.alien2003.nightbaseline.ui.material.nbClickable
import io.github.alien2003.nightbaseline.ui.material.shellSurface
import io.github.alien2003.nightbaseline.ui.theme.NbDimens
import io.github.alien2003.nightbaseline.ui.theme.NbShapes
import io.github.alien2003.nightbaseline.ui.theme.NbSpace
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

private class ItemBounds(
    val offset: DpOffset,
    val size: DpSize,
)

/**
 * The floating glass bottom bar (`layouts.md` 4.1): a `material.glass.bar` pill (solid gradient in the fallback) with
 * a 1 dp rim and `elevation.shell`, hugging its items, at least 64 dp tall, growing with the labels (two lines max at
 * large font, never cut). Selection is a solid `primaryContainer` capsule plus filled icon plus bold label, and the
 * capsule moves between items with `spring.selection` (snaps under reduced motion). The caller centres it with its
 * bottom at the navigation-bar inset + 12 dp.
 *
 * @param forced catalog only: shows hover / pressed / focus on one destination.
 */
@Composable
fun FloatingNavBar(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    forced: Pair<TopLevel, ForcedInteraction>? = null,
) {
    val bounds = remember { mutableStateMapOf<TopLevel, ItemBounds>() }
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = NbDimens.barHeight)
                .shellSurface(ShellMaterial.Bar, NbShapes.pill)
                .padding(NbDimens.barInnerPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        SelectionCapsule(bounds[selected])
        HuggingRow(Modifier.selectableGroup()) {
            TopLevel.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    vertical = true,
                    showLabel = true,
                    forced = forced?.takeIf { it.first == destination }?.second,
                    modifier = Modifier.widthIn(min = NbDimens.barItemMinWidth),
                    onBounds = { bounds[destination] = it },
                )
            }
        }
    }
}

/**
 * The floating glass navigation rail (`layouts.md` 4.2): a vertical `material.glass.rail` pill 72 dp wide (about
 * 180 dp labelled pill in `Large`), icon + label items (icon only in `WideShort`, with the label as content
 * description), the same capsule selection as the bar. The caller places it at the start edge + 12 dp, centred
 * vertically.
 */
@Composable
fun FloatingNavRail(
    selected: TopLevel,
    onSelect: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    wide: Boolean = false,
    forced: Pair<TopLevel, ForcedInteraction>? = null,
) {
    val bounds = remember { mutableStateMapOf<TopLevel, ItemBounds>() }
    val width = if (wide) NbDimens.railWidthLabelled else NbDimens.railWidth
    Box(
        modifier =
            modifier
                // Grows with the labels at large font (they wrap, never cut); the scaffold reads the actual width.
                .width(IntrinsicSize.Max)
                .widthIn(min = width, max = NbDimens.railWidthLabelled)
                // The labelled wide rail keeps the narrow rail's corner radius; a full pill would turn it into a blob.
                .shellSurface(ShellMaterial.Rail, if (wide) RoundedCornerShape(NbDimens.railWidth / 2) else NbShapes.pill)
                .padding(vertical = NbSpace.s5, horizontal = NbSpace.s2),
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionCapsule(bounds[selected])
        Column(
            Modifier.selectableGroup().width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(NbSpace.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopLevel.entries.forEach { destination ->
                NavItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    vertical = !wide,
                    showLabel = showLabels,
                    forced = forced?.takeIf { it.first == destination }?.second,
                    onBounds = { bounds[destination] = it },
                    modifier = Modifier.fillMaxWidth().widthIn(min = if (wide) width - NbSpace.s5 else NbDimens.railWidth - NbSpace.s3),
                )
            }
        }
    }
}

@Composable
private fun SelectionCapsule(bounds: ItemBounds?) {
    if (bounds == null) return
    val motion = NbTheme.motion
    val x by animateDpAsState(bounds.offset.x, motion.selection(), label = "capsuleX")
    val y by animateDpAsState(bounds.offset.y, motion.selection(), label = "capsuleY")
    val w by animateDpAsState(bounds.size.width, motion.selection(), label = "capsuleW")
    val h by animateDpAsState(bounds.size.height, motion.selection(), label = "capsuleH")
    Box(
        Modifier
            .offset(x, y)
            .size(w, h)
            .background(NbTheme.colors.primaryContainer, NbShapes.pill),
    )
}

@Composable
private fun NavItem(
    destination: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    vertical: Boolean,
    showLabel: Boolean,
    forced: ForcedInteraction?,
    onBounds: (ItemBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NbTheme.colors
    val density = LocalDensity.current
    val label = stringResource(destination.label)
    val color = if (selected) colors.onPrimaryContainer else colors.onGlassSecondary
    val style = NbTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    val content: @Composable () -> Unit = {
        Icon(
            if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(NbDimens.icon),
        )
        if (showLabel) {
            // No line limit: at large font the label wraps and the bar or rail grows (layouts.md 4.1, never cut).
            Text(label, style = style, color = color, textAlign = TextAlign.Center)
        }
    }
    val itemModifier =
        modifier
            .sizeIn(minWidth = NbDimens.targetMin, minHeight = NbDimens.targetMin)
            .onPlaced { coordinates ->
                val position = coordinates.positionInParent()
                with(density) {
                    onBounds(
                        ItemBounds(
                            DpOffset(position.x.toDp(), position.y.toDp()),
                            DpSize(coordinates.size.width.toDp(), coordinates.size.height.toDp()),
                        ),
                    )
                }
            }.nbClickable(onClick = onClick, shape = NbShapes.pill, role = Role.Tab, onGlass = true, forced = forced)
            .semantics {
                this.selected = selected
                if (!showLabel) contentDescription = label
            }.padding(horizontal = NbSpace.s3, vertical = NbSpace.s2)
    if (vertical) {
        Column(itemModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NbSpace.s1)) {
            content()
        }
    } else {
        Row(itemModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NbSpace.s3)) {
            content()
        }
    }
}

/**
 * A row that hugs its items while they fit and otherwise gives every item an equal share of the width (each still at
 * least 48 dp), so labels wrap instead of being cut (`layouts.md` 4.1). Items are centred vertically.
 */
@Composable
private fun HuggingRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val share =
            if (constraints.hasBoundedWidth && natural.sum() > constraints.maxWidth) {
                (constraints.maxWidth / measurables.size.coerceAtLeast(1))
            } else {
                null
            }
        val placeables =
            measurables.mapIndexed { i, m ->
                val w = share ?: natural[i]
                m.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = constraints.maxHeight))
            }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(placeables.sumOf { it.width }, height.coerceAtLeast(constraints.minHeight)) {
            var x = 0
            placeables.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width
            }
        }
    }
}

/** Bottom clearance of scrolling content above the floating bar (`content.bottomClearance`, `tokens.md` 4). */
val BarClearance: Dp = NbDimens.barHeight + NbDimens.barOffset + NbSpace.s5
