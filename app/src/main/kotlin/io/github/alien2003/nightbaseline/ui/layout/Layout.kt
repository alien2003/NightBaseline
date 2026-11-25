package io.github.alien2003.nightbaseline.ui.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.theme.NbDimens

/** A fold or hinge in the window, in dp from the start (vertical) or top (horizontal) edge. */
@Immutable
data class FoldInfo(
    /** Position of the fold's centre. */
    val positionDp: Float,
    /** Whether it separates the window into two logical areas (book, tabletop). */
    val separating: Boolean,
    /** Width of the occluded hinge area (0 for a seamless fold). */
    val occludedDp: Float = 0f,
)

/** Everything `resolveLayout` looks at (`layouts.md` 3.1); screenshot tests construct it directly. */
@Immutable
data class LayoutInputs(
    /** Window width. */
    val widthDp: Float,
    /** Window height. */
    val heightDp: Float,
    /** System font scale. */
    val fontScale: Float = 1f,
    /** A vertical fold (book posture when separating). */
    val verticalFold: FoldInfo? = null,
    /** A horizontal fold (tabletop when separating). */
    val horizontalFold: FoldInfo? = null,
    /** A mouse or trackpad was used. */
    val precisePointer: Boolean = false,
)

/** Layout modes of `layouts.md` 3.2, checked in this order. */
enum class LayoutMode {
    /** `min(w, h) < 320` or (`w < 400` and `h < 480`): flip cover screens, tiny windows. */
    Glance,

    /** Separating horizontal fold and `h >= 480`. */
    Tabletop,

    /** Separating vertical fold and `w >= 600`. */
    Book,

    /** `w < 600` and `h < 480`. */
    CompactShort,

    /** `w < 600`. */
    Compact,

    /** `w >= 600` and `h < 480`. */
    WideShort,

    /** `600 <= w < 840`. */
    Medium,

    /** `840 <= w < 1200`. */
    Expanded,

    /** `1200 <= w < 1600`. */
    Large,

    /** `w >= 1600`. */
    ExtraLarge,
}

/** The navigation shell of a layout (`layouts.md` 4). */
enum class NavigationKind {
    /** No bar; a "More" button opens a sheet (Glance). */
    None,

    /** Floating bottom bar. */
    Bar,

    /** Floating rail, icon + label. */
    Rail,

    /** Floating rail, icons only (WideShort). */
    RailIcons,

    /** Labelled wide rail (Large, ExtraLarge). */
    RailLabelled,
}

/** The one layout decision passed down the UI tree. */
@Immutable
data class LayoutSpec(
    /** Mode. */
    val mode: LayoutMode,
    /** Navigation shell. */
    val navigation: NavigationKind,
    /** Side gutter. */
    val gutter: Dp,
    /** Whether list-detail shows two panes (two-pane rule of `layouts.md` 3.3, or forced by a book fold). */
    val twoPanes: Boolean,
    /** Window width. */
    val widthDp: Float,
    /** Window height. */
    val heightDp: Float,
    /** The separating vertical fold, if any (panes split exactly there). */
    val verticalFold: FoldInfo? = null,
    /** The separating horizontal fold, if any. */
    val horizontalFold: FoldInfo? = null,
)

private const val GLANCE_MIN = 320f
private const val GLANCE_W = 400f
private const val SHORT_H = 480f
private const val MEDIUM_W = 600f
private const val EXPANDED_W = 840f
private const val LARGE_W = 1200f
private const val XL_W = 1600f
private const val BOOK_START_PANE_MIN = 360f
private const val LIST_PANE_MIN = 320f
private const val DETAIL_PANE_MIN = 360f
private const val MAX_PANE_FONT_SCALE = 1.5f
private const val PANE_SPACER = 24f
private const val PANE_SPACER_MEDIUM = 16f
private const val DEFAULT_WIDTH_DP = 412f
private const val DEFAULT_HEIGHT_DP = 915f

/** `layouts.md` 3.2 and 3.3: picks exactly one mode, its navigation, gutter and whether two panes fit. Pure. */
fun resolveLayout(inputs: LayoutInputs): LayoutSpec {
    val vFold = inputs.verticalFold?.takeIf { it.separating }
    val hFold = inputs.horizontalFold?.takeIf { it.separating }
    val mode = modeOf(inputs.widthDp, inputs.heightDp, vFold, hFold)
    val navigation = navigationOf(mode, vFold)
    val gutter = gutterOf(mode)
    val twoPanes = twoPanes(inputs, mode, navigation, gutter)
    return LayoutSpec(mode, navigation, gutter, twoPanes, inputs.widthDp, inputs.heightDp, vFold, hFold)
}

@Suppress("CyclomaticComplexMethod") // The ordered rule table of layouts.md 3.2; each line is one mode.
private fun modeOf(
    w: Float,
    h: Float,
    vFold: FoldInfo?,
    hFold: FoldInfo?,
): LayoutMode =
    when {
        minOf(w, h) < GLANCE_MIN || (w < GLANCE_W && h < SHORT_H) -> LayoutMode.Glance
        hFold != null && h >= SHORT_H -> LayoutMode.Tabletop
        vFold != null && w >= MEDIUM_W -> LayoutMode.Book
        w < MEDIUM_W && h < SHORT_H -> LayoutMode.CompactShort
        w < MEDIUM_W -> LayoutMode.Compact
        h < SHORT_H -> LayoutMode.WideShort
        w < EXPANDED_W -> LayoutMode.Medium
        w < LARGE_W -> LayoutMode.Expanded
        w < XL_W -> LayoutMode.Large
        else -> LayoutMode.ExtraLarge
    }

private fun navigationOf(
    mode: LayoutMode,
    vFold: FoldInfo?,
): NavigationKind =
    when (mode) {
        LayoutMode.Glance -> NavigationKind.None

        LayoutMode.Tabletop, LayoutMode.Compact, LayoutMode.CompactShort -> NavigationKind.Bar

        // A rail only when the start pane keeps >= 360 dp next to it (layouts.md 3.2).
        LayoutMode.Book -> if (bookStartPane(vFold) >= BOOK_START_PANE_MIN) NavigationKind.Rail else NavigationKind.Bar

        LayoutMode.WideShort -> NavigationKind.RailIcons

        LayoutMode.Medium, LayoutMode.Expanded -> NavigationKind.Rail

        LayoutMode.Large, LayoutMode.ExtraLarge -> NavigationKind.RailLabelled
    }

private fun bookStartPane(vFold: FoldInfo?): Float = (vFold?.positionDp ?: 0f) - NbDimens.railArea.value

private fun gutterOf(mode: LayoutMode): Dp =
    when (mode) {
        LayoutMode.Glance -> NbDimens.gutterGlance
        LayoutMode.Compact, LayoutMode.CompactShort, LayoutMode.Tabletop -> NbDimens.gutterCompact
        LayoutMode.Large, LayoutMode.ExtraLarge -> NbDimens.gutterLarge
        else -> NbDimens.gutterMedium
    }

/** The two-pane rule: `w - railArea - 2 * gutter - paneSpacer >= (listPaneMin + detailPaneMin) * s`; always in Book. */
private fun twoPanes(
    inputs: LayoutInputs,
    mode: LayoutMode,
    navigation: NavigationKind,
    gutter: Dp,
): Boolean {
    val spacer = if (mode == LayoutMode.Medium) PANE_SPACER_MEDIUM else PANE_SPACER
    val scale = inputs.fontScale.coerceIn(1f, MAX_PANE_FONT_SCALE)
    val rail = if (navigation == NavigationKind.None || navigation == NavigationKind.Bar) 0f else NbDimens.railArea.value
    val fits = inputs.widthDp - rail - 2 * gutter.value - spacer >= (LIST_PANE_MIN + DETAIL_PANE_MIN) * scale
    return when (mode) {
        LayoutMode.Book -> true
        LayoutMode.Medium, LayoutMode.Expanded, LayoutMode.Large, LayoutMode.ExtraLarge -> fits
        else -> false
    }
}

/** The layout in effect (provided by the app scaffold; screenshot tests provide their own). */
val LocalLayoutSpec =
    staticCompositionLocalOf { resolveLayout(LayoutInputs(widthDp = DEFAULT_WIDTH_DP, heightDp = DEFAULT_HEIGHT_DP)) }

/** Gap between panes. */
val PaneSpacer: Dp = 24.dp
