package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens of the cards, status and data-visualisation package (`docs/design/coloros-conformance.md` section 6, WP4)
 * with the values of conformance section 4. Values shared with the foundations come from [NbColors] (one source of
 * truth); the rest are data-only.
 *
 * ColorOS 17 canon: neutral R = G = B greys, one blue accent, no tonal containers; the usual range is a neutral solid
 * segment on a slider-like track, the chart is neutral with only the status shapes in colour.
 */
@Immutable
data class NbDataColors(
    /** `color.primaryText`: links, text actions, info status, the learning "Now" marker. */
    val primaryText: Color,
    /** `color.control.track`: range band track (slider idiom). */
    val bandTrack: Color,
    /** `color.band.fill` (new value): the usual range as a solid segment, 3:1 against the card. */
    val bandRange: Color,
    /** `color.marker.within`: the white slider-thumb dot of a value inside the range. */
    val markerWithin: Color,
    /** 1 dp rim of [markerWithin] (light theme only, where white on white needs an edge). */
    val markerRim: Color,
    /** Dotted outline of a band that is still learning. */
    val learningOutline: Color,
    /** `color.chart.grid`: 1 px horizontal grid lines. */
    val chartGrid: Color,
    /** `color.chart.bandArea`: the stepped usual-range area of the trend chart. */
    val chartBandArea: Color,
    /** `color.chart.point`: points inside the range. */
    val chartPoint: Color,
    /** `color.chart.guide`: 1 dp selection guide. */
    val chartGuide: Color,
    /** `color.solid.tooltip` / `surface.elevated`: the chart tooltip. */
    val tooltip: Color,
    /** Period selector track (solid content layer). */
    val segmentTrack: Color,
    /** Top of the solid convex period thumb. */
    val segmentThumbTop: Color,
    /** Bottom of the solid convex period thumb. */
    val segmentThumbBottom: Color,
    /** Top rim highlight of the period thumb. */
    val segmentThumbRim: Color,
    /** Learning progress: a night that has not come yet. */
    val progressPending: Color,
    /** `state.selected`: list-detail selection of a tile (neutral, no outline). */
    val selected: Color,
)

/** `#RRGGBB @ NN%`. */
private fun tint(
    rgb: Long,
    alphaPercent: Int,
): Color = Color(0xFF000000 or rgb).copy(alpha = alphaPercent / 100f)

/** Light values (light references `.data/design-refs/unfolded-light/`: white cards on light grey, grey controls). */
val LightNbDataColors: NbDataColors =
    NbDataColors(
        primaryText = LightNbColors.primaryText,
        bandTrack = LightNbColors.controlTrack,
        bandRange = LightNbColors.bandFill,
        markerWithin = LightNbColors.markerWithin,
        markerRim = LightNbColors.markerWithinRim,
        learningOutline = Color(0xFF8C8C8C),
        chartGrid = LightNbColors.chartGrid,
        chartBandArea = LightNbColors.chartBandArea,
        chartPoint = LightNbColors.chartPoint,
        chartGuide = tint(0x000000, 30),
        tooltip = LightNbColors.solidTooltip,
        segmentTrack = Color(0xFFE3E4E6),
        segmentThumbTop = Color(0xFFFFFFFF),
        segmentThumbBottom = Color(0xFFF7F7F9),
        segmentThumbRim = tint(0xFFFFFF, 80),
        progressPending = Color(0xFFD6D6D6),
        selected = LightNbColors.stateSelected,
    )

/** Dark values (measured ColorOS 17 dark references: true black window, #1A1A1A cards, slider track #333333). */
val DarkNbDataColors: NbDataColors =
    NbDataColors(
        primaryText = DarkNbColors.primaryText,
        bandTrack = DarkNbColors.controlTrack,
        bandRange = DarkNbColors.bandFill,
        markerWithin = DarkNbColors.markerWithin,
        markerRim = Color(0x00000000),
        learningOutline = Color(0xFF6B6B6B),
        chartGrid = DarkNbColors.chartGrid,
        chartBandArea = DarkNbColors.chartBandArea,
        chartPoint = DarkNbColors.chartPoint,
        chartGuide = tint(0xFFFFFF, 30),
        tooltip = DarkNbColors.solidTooltip,
        segmentTrack = Color(0xFF242424),
        segmentThumbTop = Color(0xFF3D3D3D),
        segmentThumbBottom = Color(0xFF333333),
        segmentThumbRim = tint(0xFFFFFF, 12),
        progressPending = Color(0xFF484848),
        selected = DarkNbColors.stateSelected,
    )

/** The data tokens of this theme. */
val NbColors.data: NbDataColors get() = if (isDark) DarkNbDataColors else LightNbDataColors

/** Sizes of the cards and data package (conformance 4.3). */
object NbDataDimens {
    /** Status dot (within). */
    val statusDot: Dp = 8.dp

    /** Status triangle side (higher / lower). */
    val statusTriangle: Dp = 10.dp

    /** Status ring or dash (learning, missing). */
    val statusRing: Dp = 12.dp

    /** Gap between a status glyph and its label. */
    val statusGap: Dp = 6.dp

    /** Night glyph on the summary card, directly on the card (no container). */
    val summaryGlyph: Dp = 28.dp

    /** Icon of a state card and of a banner (no container). */
    val stateIcon: Dp = 24.dp

    /** Within marker diameter on the band. */
    val markerDot: Dp = 10.dp

    /** Learning progress segment height. */
    val progressSegment: Dp = 8.dp

    /** Gap between learning progress segments. */
    val progressGap: Dp = 4.dp

    /** Period selector: visual height of the track inside its 48 dp target row. */
    val periodTrack: Dp = 40.dp

    /** Period selector: thumb inset inside the track. */
    val periodInset: Dp = 4.dp

    /** Period selector: minimum segment width. */
    val periodSegmentMin: Dp = 52.dp

    /** Chart point radius (inside the range). */
    val chartPointRadius: Dp = 3.dp

    /** Readout row minimum height (stats card). */
    val readoutRow: Dp = 48.dp

    /** Section header: text start inside the content column (32 dp from the screen edge with a 16 dp gutter). */
    val sectionHeaderStart: Dp = 16.dp

    /** Section header: space above (on top of the column's 12 dp gap, about 24 dp in all). */
    val sectionHeaderTop: Dp = 12.dp
}

/** Shapes of the package. */
object NbDataShapes {
    /** `shape.popup`: the chart tooltip. */
    val popup: Shape = SmoothCornerShape(16.dp)
}

/** Shadow of the chart tooltip (`elevation.raised`, conformance K7). */
val NbColors.tooltipShadow: NbShadow get() = if (isDark) NbShadow(4.dp, 16.dp, 0.50f) else NbShadow(4.dp, 16.dp, 0.12f)

/** Shadow of the light period thumb (a white convex chip, as the light reference's round buttons). */
val NbColors.thumbShadow: NbShadow get() = if (isDark) NbShadow(1.dp, 3.dp, 0.30f) else NbShadow(1.dp, 4.dp, 0.12f)

/**
 * Text styles of the package (conformance 4.2), derived from the type scale with explicit sizes and the 400 / 500 /
 * 700 weights only, so they do not depend on the old 600 weights.
 */
@Immutable
data class NbDataType(
    /** `type.sectionHeader` 12/16 500, `text.tertiary`. */
    val sectionHeader: TextStyle,
    /** `type.itemTitle` 16/22 500: tile names, card titles. */
    val itemTitle: TextStyle,
    /** `type.headline` 22/28 500: summary headline. */
    val headline: TextStyle,
    /** `type.title` 20/26 500: state card titles. */
    val title: TextStyle,
    /** `type.summary` 14/20 400. */
    val summary: TextStyle,
    /** `type.statusLabel` calm: 13/18 400 (within, learning, missing, night label, range line). */
    val statusCalm: TextStyle,
    /** `type.statusLabel` moved: 14/20 500. */
    val statusMoved: TextStyle,
    /** Segments of the period selector: 14/20 500. */
    val segment: TextStyle,
    /** Tooltip value: 16/22 500 tnum. */
    val tooltipValue: TextStyle,
    /** Readout value: 15/20 500 tnum. */
    val readoutValue: TextStyle,
    /** "Now" marker of the learning timeline: 12/16 500. */
    val marker: TextStyle,
)

/** The data text styles in effect. */
val NbTheme.dataType: NbDataType
    @Composable
    @ReadOnlyComposable
    get() {
        val t = typography
        val medium = FontWeight.Medium
        return NbDataType(
            sectionHeader = t.caption.copy(fontWeight = medium),
            itemTitle = t.titleSmall.copy(fontWeight = medium),
            headline = t.headline.copy(fontWeight = medium),
            title = t.title.copy(fontWeight = medium),
            summary = t.bodySmall.copy(fontWeight = FontWeight.Normal),
            statusCalm = t.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
            statusMoved = t.bodySmall.copy(fontWeight = medium),
            segment = t.bodySmall.copy(fontWeight = medium),
            tooltipValue = t.numericSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = medium),
            readoutValue = t.numericSmall.copy(fontWeight = medium),
            marker = t.caption.copy(fontWeight = medium),
        )
    }

/** One physical pixel, at least 0.33 dp (conformance S5 hairline). */
val hairline: Dp
    @Composable
    @ReadOnlyComposable
    get() = maxOf((1f / LocalDensity.current.density).dp, 0.33.dp)
