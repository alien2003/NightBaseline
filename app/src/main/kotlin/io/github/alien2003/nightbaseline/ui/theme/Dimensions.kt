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

    /** `row.paddingVertical` (ColorOS 10 dp, measured 10.2 dp). */
    val rowPaddingVertical: Dp = 10.dp

    /** Row minimum height (grows with text): 48 dp as measured, equal to `target.min`. */
    val rowMinHeight: Dp = 48.dp

    /** `row.paddingHorizontal`. */
    val rowPaddingHorizontal: Dp = 16.dp

    /** Gap between a row title and its summary. */
    val rowTitleSummaryGap: Dp = 2.dp

    /** Section header: space above its text (includes the 16 dp group gap). */
    val sectionHeaderTop: Dp = 24.dp

    /** Section header: space between its text and the card below. */
    val sectionHeaderBottom: Dp = 8.dp

    /** Section header text start, measured from the card's edge (32 dp from the screen edge with a 16 dp gutter). */
    val sectionHeaderStart: Dp = 16.dp

    /** Hairline divider thickness: `max(1 px, 0.33 dp)`; draw with `max(1f, toPx())`. */
    val divider: Dp = 0.33.dp

    /** Divider inset from the card edge, at the start and at the end. */
    val dividerInset: Dp = 16.dp

    /** `card.gap` between homogeneous items (tiles, history months). */
    val cardGap: Dp = 12.dp

    /** `group.gap` between distinct cards and groups. */
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

    /** Space the rail takes from the content (`railArea`). */
    val railArea: Dp = 96.dp

    /** Compact collapsed top bar height (measured 52.1 dp). */
    val topBarCollapsed: Dp = 52.dp

    /** Medium and up top bar height (same as collapsed in the canon). */
    val topBarMedium: Dp = 52.dp

    /** Short windows top bar height. */
    val topBarShort: Dp = 48.dp

    /** Scroll distance under the top bar after which its tint is full (`tokens.md` 7.1). */
    val topBarTintDistance: Dp = 8.dp

    /** `textMax`: text columns and single cards. */
    val textMax: Dp = 600.dp

    /** `chartMax`. */
    val chartMax: Dp = 840.dp

    /** Dialog max width for confirmations. */
    val dialogMaxWidth: Dp = 392.dp

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

    /** Radius of the touch light under the finger (`tokens.md` 9.4); capped by the element's own size. */
    val glowRadius: Dp = 140.dp

    /** Chart height in Compact (`layouts.md` 5.2). */
    val chartHeightCompact: Dp = 220.dp

    /** Drag handle visual size of a sheet. */
    val sheetHandleWidth: Dp = 36.dp

    /** Drag handle visual height. */
    val sheetHandleHeight: Dp = 4.dp

    /** Convex round shell button: visual diameter (48 dp target). */
    val roundButton: Dp = 40.dp

    /** Margin of a round shell button from the window edge. */
    val roundButtonMargin: Dp = 16.dp

    /** Glyph inside a round shell button. */
    val roundButtonGlyph: Dp = 20.dp

    /** Glass rim: top highlight stroke. */
    val rimTop: Dp = 0.5.dp

    /** Glass rim: bottom stroke. */
    val rimBottom: Dp = 1.dp

    /** Glass rim: faint bottom reflection band inside the shape. */
    val rimBottomBand: Dp = 2.dp

    /** Higher-contrast border on solid shell surfaces (`onGlass.primary` at 60 %). */
    val contrastBorder: Dp = 1.dp

    /** Smallest radius of a concentric inner shape (`tokens.md` 5). */
    val concentricMinRadius: Dp = 12.dp
}
