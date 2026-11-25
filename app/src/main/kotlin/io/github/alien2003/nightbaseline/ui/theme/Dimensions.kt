package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale of `tokens.md` section 4 (4 dp grid with a 2 dp half step). */
object NbSpace {
    /** `space.0`. */
    val s0: Dp = 0.dp

    /** `space.1`. */
    val s1: Dp = 2.dp

    /** `space.2`. */
    val s2: Dp = 4.dp

    /** `space.3`. */
    val s3: Dp = 8.dp

    /** `space.4`. */
    val s4: Dp = 12.dp

    /** `space.5`. */
    val s5: Dp = 16.dp

    /** `space.6`. */
    val s6: Dp = 20.dp

    /** `space.7`. */
    val s7: Dp = 24.dp

    /** `space.8`. */
    val s8: Dp = 32.dp

    /** `space.9`. */
    val s9: Dp = 40.dp

    /** `space.10`. */
    val s10: Dp = 48.dp

    /** `space.11`. */
    val s11: Dp = 64.dp
}

/** Semantic spacing and sizes of `tokens.md` sections 4 and 10, and the shell geometry of `layouts.md` 3.3 and 4. */
object NbDimens {
    /** `gutter.compact`. */
    val gutterCompact: Dp = 16.dp

    /** `gutter.medium`. */
    val gutterMedium: Dp = 24.dp

    /** `gutter.large`. */
    val gutterLarge: Dp = 32.dp

    /** `gutter.glance`. */
    val gutterGlance: Dp = 12.dp

    /** `card.padding` (horizontal and vertical). */
    val cardPadding: Dp = 16.dp

    /** `row.paddingVertical`. */
    val rowPaddingVertical: Dp = 12.dp

    /** Row minimum height (grows with text). */
    val rowMinHeight: Dp = 56.dp

    /** `card.gap` between tiles and list cards. */
    val cardGap: Dp = 12.dp

    /** `group.gap` between groups. */
    val groupGap: Dp = 16.dp

    /** `icon.size` standard. */
    val icon: Dp = 24.dp

    /** `icon.size` inline with text. */
    val iconInline: Dp = 20.dp

    /** `icon.size` status glyph in chips. */
    val iconChip: Dp = 16.dp

    /** `icon.size` summary card glyph. */
    val iconSummary: Dp = 40.dp

    /** Container of the summary card glyph. */
    val iconSummaryContainer: Dp = 56.dp

    /** `hinge.clearance` on each side of a separating fold. */
    val hingeClearance: Dp = 12.dp

    /** `target.min` for every interactive element. */
    val targetMin: Dp = 48.dp

    /** `target.spacing` between the visible edges of adjacent small targets. */
    val targetSpacing: Dp = 8.dp

    /** `focus.ring` stroke width. */
    val focusRingWidth: Dp = 2.dp

    /** `focus.ring` offset outside the element's shape. */
    val focusRingOffset: Dp = 2.dp

    /** Floating bottom bar minimum height at font 1.0 (`layouts.md` 4.1). */
    val barHeight: Dp = 64.dp

    /** Bar offset above the navigation bar inset. */
    val barOffset: Dp = 12.dp

    /** Minimum width of one bar item. */
    val barItemMinWidth: Dp = 80.dp

    /** Bar inner padding. */
    val barInnerPadding: Dp = 8.dp

    /** Floating rail width (`layouts.md` 4.2). */
    val railWidth: Dp = 72.dp

    /** Labelled rail width in `Large` / `ExtraLarge`. */
    val railWidthLabelled: Dp = 180.dp

    /** Rail offset from the start edge. */
    val railOffset: Dp = 12.dp

    /** Space the rail takes from the content (`railArea`). */
    val railArea: Dp = 96.dp

    /** Compact large title top bar height at rest (collapses to [topBarCollapsed]). */
    val topBarLarge: Dp = 112.dp

    /** Compact collapsed top bar height. */
    val topBarCollapsed: Dp = 56.dp

    /** Medium and up top bar height. */
    val topBarMedium: Dp = 64.dp

    /** Short windows top bar height. */
    val topBarShort: Dp = 48.dp

    /** Scroll distance under the top bar after which its tint is full (`tokens.md` 7.1). */
    val topBarTintDistance: Dp = 8.dp

    /** `textMax`: text columns and single cards. */
    val textMax: Dp = 600.dp

    /** `chartMax`. */
    val chartMax: Dp = 840.dp

    /** Dialog max width for confirmations. */
    val dialogMaxWidth: Dp = 480.dp

    /** Dialog max width when it hosts sheet content. */
    val dialogMaxWidthWide: Dp = 560.dp

    /** Buttons never wider than this on wide windows. */
    val buttonMaxWidth: Dp = 360.dp

    /** Height of one [io.github.alien2003.nightbaseline.ui.components.LearningProgress] segment. */
    val learningSegmentHeight: Dp = 10.dp

    /** Range band track height on a tile. */
    val bandHeight: Dp = 16.dp

    /** Band edge and median tick stroke. */
    val bandEdgeStroke: Dp = 1.5.dp

    /** Value marker halo. */
    val markerHalo: Dp = 2.dp

    /** Status triangle side in the band. */
    val markerTriangle: Dp = 10.dp

    /** Radius of the touch light under the finger on glass (`tokens.md` 9.5). */
    val glowRadius: Dp = 48.dp

    /** Chart height in Compact (`layouts.md` 5.2). */
    val chartHeightCompact: Dp = 220.dp

    /** Drag handle visual size of a sheet. */
    val sheetHandleWidth: Dp = 32.dp

    /** Drag handle visual height. */
    val sheetHandleHeight: Dp = 4.dp
}
