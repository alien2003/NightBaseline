package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.github.alien2003.nightbaseline.ui.theme.ShellDimens

/**
 * The card column of a page as the top bar sees it (the container rule of review round 4, applied to the page title):
 * the column sits [gutter] inside the bar's start edge and [end] inside its end edge, is at most [maxWidth] wide
 * ([wideMax] from [wideFrom] of space, for pages whose cap depends on the tile columns) and is centred in that space
 * when [centred]. [ShellTopBar] starts its leading element (title or back button) at the column's start
 * edge, so a single-pane page on a wide window does not have its title hanging at the window edge while the cards
 * start further in. Never closer to the edge than the 16 dp toolbar margin.
 */
@Immutable
data class TitleColumn(
    /** Distance of the column space from the bar's start edge. */
    val gutter: Dp,
    /** Distance of the column space from the bar's end edge. */
    val end: Dp = gutter,
    /** Centred in the space between the gutters (else at its start). */
    val centred: Boolean = true,
    /** The column's width cap. */
    val maxWidth: Dp = Dp.Infinity,
    /** A wider cap once the space between the gutters reaches [wideFrom] (Home's two tile columns). */
    val wideMax: Dp = maxWidth,
    /** See [wideMax]. */
    val wideFrom: Dp = Dp.Infinity,
) {
    /** Start of the column from the bar's start edge, for a bar [width] wide. */
    fun start(width: Dp): Dp {
        val available = (width - gutter - end).coerceAtLeast(0.dp)
        val cap = if (available >= wideFrom) wideMax else maxWidth
        val column = minOf(available, cap)
        return if (centred) gutter + (available - column) / 2 else gutter
    }

    /** Factories for the common page arrangements. */
    companion object {
        /** A column that starts at [gutter] (list panes of two-pane pages, fold-aligned columns). */
        fun atStart(gutter: Dp): TitleColumn = TitleColumn(gutter, gutter, centred = false)

        /** A centred column capped at [max] (single-pane pages). */
        fun centred(
            gutter: Dp,
            max: Dp,
            end: Dp = gutter,
        ): TitleColumn = TitleColumn(gutter, end, centred = true, maxWidth = max)
    }
}

/** The same column with [extra] more space at the end (the system navigation inset the content also keeps clear of). */
fun TitleColumn.withEnd(extra: Dp): TitleColumn = if (extra == 0.dp) this else copy(end = end + extra)

/**
 * Moves the content's start edge from the toolbar margin to [column]'s start edge (measured against the incoming
 * width, so it costs no extra pass); the end edge stays where it is.
 */
internal fun Modifier.alignStartToColumn(column: TitleColumn?): Modifier =
    if (column == null) {
        this
    } else {
        layout { measurable, constraints ->
            val width = constraints.maxWidth
            val inset =
                if (constraints.hasBoundedWidth) {
                    (column.start(width.toDp()) - ShellDimens.toolbarMargin).roundToPx().coerceIn(0, width)
                } else {
                    0
                }
            val placeable = measurable.measure(constraints.offset(horizontal = -inset))
            layout(placeable.width + inset, placeable.height) { placeable.placeRelative(inset, 0) }
        }
    }
