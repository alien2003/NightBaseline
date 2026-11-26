package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour token of `docs/design/tokens.md` section 2, for one theme. Names follow the token names without dots
 * (`color.status.moved` -> [statusMoved]); aliases (`= other.token`) are resolved to the same value. Translucent
 * tokens (`#RRGGBB @ NN%`) carry their alpha. There is no dynamic colour and no error colour ([decision], tokens.md 0
 * and 2.3). Neutrals are hue-free (R = G = B) as measured on the ColorOS 17 system apps; there is one blue accent.
 * `ColorTokensTest` keeps these values identical to the Markdown tables and `ContrastTest` checks the pairs.
 *
 * Tokens marked deprecated are retired by the ColorOS conformance plan (`docs/design/coloros-conformance.md` 4.1);
 * they are aliases of their neutral replacements until the last caller is migrated, then they are deleted.
 */
@Immutable
data class NbColors(
    /** `color.background`: window background, page behind cards. */
    val background: Color,
    /** `color.surface.1`: cards, tiles, chart card, settings groups. */
    val surface1: Color,
    /** `color.surface.2`: nested areas inside a card. */
    val surface2: Color,
    /** `color.surface.3`: selected row, segmented track, pressed fill; the highest fill that may carry secondary text. */
    val surface3: Color,
    /** `color.surface.elevated`: cards inside sheets, tooltip. */
    val surfaceElevated: Color,
    /** `color.text.primary`. */
    val textPrimary: Color,
    /** `color.text.secondary`. */
    val textSecondary: Color,
    /** `color.text.tertiary`: only on [background] and [surface1]. */
    val textTertiary: Color,
    /** `color.separator`: decorative 1 px hairline. */
    val separator: Color,
    /** `color.outline`: essential boundaries, unchecked radio ring. */
    val outline: Color,
    /** `color.chevron`: decorative disclosure chevron. */
    val chevron: Color,
    /** `color.control.track`: slider, range band and switch-off base track. */
    val controlTrack: Color,
    /** `color.control.switchOff`: switch track when off. */
    val controlSwitchOff: Color,
    /** `color.control.convexTop`: convex round shell button, gradient top. */
    val controlConvexTop: Color,
    /** `color.control.convexBottom`: convex round shell button, gradient bottom. */
    val controlConvexBottom: Color,
    /** `color.control.convexRimTop`. */
    val controlConvexRimTop: Color,
    /** `color.control.convexRimBottom`. */
    val controlConvexRimBottom: Color,
    /** `color.primary`: controls and graphics (switch on, radio, slider, selection); links. */
    val primary: Color,
    /** `color.primaryText`: links, text buttons, info status. */
    val primaryText: Color,
    /** `color.primaryFill`: filled buttons that carry a text label ([onPrimary] on it). */
    val primaryFill: Color,
    /** `color.onPrimary`: label and icon on [primaryFill]. */
    val onPrimary: Color,
    /** `color.accentGlow`: glow under the accent call-to-action (decorative). */
    val accentGlow: Color,
    /** `color.glow.neutral`: finger-following light on glass and shell items (decorative). */
    val glowNeutral: Color,
    /** `color.status.within`. */
    val statusWithin: Color,
    /** `color.status.moved` (also `status.above` and `status.below`). */
    val statusMoved: Color,
    /** `color.status.neutral` (also `status.learning` and `status.missing`). */
    val statusNeutral: Color,
    /** `color.band.fill`: solid usual-range segment on the track. */
    val bandFill: Color,
    /** `color.marker.within`: white value marker inside the range. */
    val markerWithin: Color,
    /** `color.marker.withinRim`: 1 px rim of [markerWithin] (light only; dark = the marker). */
    val markerWithinRim: Color,
    /** `color.chart.point`. */
    val chartPoint: Color,
    /** `color.chart.grid` (decorative). */
    val chartGrid: Color,
    /** `color.chart.bandArea`: the chart's stepped usual-range area. */
    val chartBandArea: Color,
    /** `color.glass.bar`: bottom bar and rail tint. */
    val glassBar: Color,
    /** `color.glass.topBar`: top bar tint under the small title (the row that carries text). */
    val glassTopBar: Color,
    /** `color.glass.topBarDim`: top stop of the progressive darkening gradient where no text sits. */
    val glassTopBarDim: Color,
    /** `color.glass.sheet`. */
    val glassSheet: Color,
    /** `color.glass.dialog`. */
    val glassDialog: Color,
    /** `color.glass.rimTop`: 0.5 dp top highlight. */
    val glassRimTop: Color,
    /** `color.glass.rimBottom`: 1 dp stroke and the 2 dp bottom reflection band. */
    val glassRimBottom: Color,
    /** `color.onGlass.primary` (also the selected navigation item). */
    val onGlassPrimary: Color,
    /** `color.onGlass.secondary` (also the unselected navigation item). */
    val onGlassSecondary: Color,
    /** `color.solid.barTop`. */
    val solidBarTop: Color,
    /** `color.solid.barBottom`. */
    val solidBarBottom: Color,
    /** `color.solid.sheet`. */
    val solidSheet: Color,
    /** `color.solid.dialog`. */
    val solidDialog: Color,
    /** `color.scrim`. */
    val scrim: Color,
    /** `color.sheetHandle`. */
    val sheetHandle: Color,
    /** `color.focus.ring`. */
    val focusRing: Color,
    /** `color.state.pressed`: highlight drawn under the content of a pressed element, clipped to its shape. */
    val statePressed: Color,
    /** `color.state.selected`: selected rows and tiles. */
    val stateSelected: Color,
    /** True for the dark theme. */
    val isDark: Boolean,
) {
    /** `color.status.above` = [statusMoved]. */
    val statusAbove: Color get() = statusMoved

    /** `color.status.below` = [statusMoved]. */
    val statusBelow: Color get() = statusMoved

    /** `color.status.learning` = [statusNeutral]. */
    val statusLearning: Color get() = statusNeutral

    /** `color.status.missing` = [statusNeutral]. */
    val statusMissing: Color get() = statusNeutral

    /** `color.status.info` = [primaryText]: data-access problems. */
    val statusInfo: Color get() = primaryText

    /** `color.band.learningOutline` = [outline]. */
    val bandLearningOutline: Color get() = outline

    /** `color.marker.halo` = [surface1]. */
    val markerHalo: Color get() = surface1

    /** `color.chart.guide` = [textSecondary]. */
    val chartGuide: Color get() = textSecondary

    /** `color.solid.topBar` = [background]. */
    val solidTopBar: Color get() = background

    /** `color.solid.tooltip` = [surfaceElevated]. */
    val solidTooltip: Color get() = surfaceElevated

    /** `color.primaryContainer` (retired: no tonal containers) = [surface3]. */
    @Deprecated("No tonal containers in ColorOS: use surface3 / stateSelected", ReplaceWith("surface3"))
    val primaryContainer: Color get() = surface3

    /** `color.onPrimaryContainer` (retired) = [textPrimary]. */
    @Deprecated("No tonal containers in ColorOS: use textPrimary", ReplaceWith("textPrimary"))
    val onPrimaryContainer: Color get() = textPrimary

    /** `color.accent` (retired: single blue accent) = [primary]. */
    @Deprecated("Single accent: use primary", ReplaceWith("primary"))
    val accent: Color get() = primary

    /** `color.status.within.container` (retired: no status containers) = [surface1]. */
    @Deprecated("No status containers: draw the glyph on the card", ReplaceWith("surface1"))
    val statusWithinContainer: Color get() = surface1

    /** `color.status.within.onContainer` (retired) = [statusWithin]. */
    @Deprecated("No status containers", ReplaceWith("statusWithin"))
    val statusWithinOnContainer: Color get() = statusWithin

    /** `color.status.moved.container` (retired) = [surface1]. */
    @Deprecated("No status containers: draw the glyph on the card", ReplaceWith("surface1"))
    val statusMovedContainer: Color get() = surface1

    /** `color.status.moved.onContainer` (retired) = [statusMoved]. */
    @Deprecated("No status containers", ReplaceWith("statusMoved"))
    val statusMovedOnContainer: Color get() = statusMoved

    /** `color.status.neutral.container` (retired) = [surface1]. */
    @Deprecated("No status containers: draw the glyph on the card", ReplaceWith("surface1"))
    val statusNeutralContainer: Color get() = surface1

    /** `color.status.neutral.onContainer` (retired) = [statusNeutral]. */
    @Deprecated("No status containers", ReplaceWith("statusNeutral"))
    val statusNeutralOnContainer: Color get() = statusNeutral

    /** `color.status.info.container` (retired) = [surface1]. */
    @Deprecated("No status containers: draw the glyph on the card", ReplaceWith("surface1"))
    val statusInfoContainer: Color get() = surface1

    /** `color.status.info.onContainer` (retired) = [statusInfo]. */
    @Deprecated("No status containers", ReplaceWith("statusInfo"))
    val statusInfoOnContainer: Color get() = statusInfo

    /** `color.band.edge` (retired: no band edge lines or median tick) = [bandFill], so the edges disappear. */
    @Deprecated("The usual range is a solid segment without edges", ReplaceWith("bandFill"))
    val bandEdge: Color get() = bandFill

    /** `color.glass.rimMid` (retired: two-stop rim) = [glassRimTop]. */
    @Deprecated("Rim is top highlight + bottom band only", ReplaceWith("glassRimTop"))
    val glassRimMid: Color get() = glassRimTop

    /** `color.glass.rimOuter` (retired: no outer hairline) = transparent. */
    @Deprecated("No outer hairline on shell surfaces", ReplaceWith("Color.Transparent"))
    val glassRimOuter: Color get() = Color.Transparent
}

/** `#RRGGBB @ NN%` from tokens.md. */
private fun tint(
    rgb: Long,
    alphaPercent: Float,
): Color = Color(0xFF000000 or rgb).copy(alpha = alphaPercent / 100f)

/** Light theme, calibrated to the light ColorOS 17 captures (#F0F1F2 window, white cards, #E0E0E0 hairlines). */
val LightNbColors: NbColors =
    NbColors(
        background = Color(0xFFF0F1F2),
        surface1 = Color(0xFFFFFFFF),
        surface2 = Color(0xFFF5F5F5),
        surface3 = Color(0xFFEBEBEB),
        surfaceElevated = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF666666),
        textTertiary = Color(0xFF6E6E6E),
        separator = Color(0xFFE0E0E0),
        outline = Color(0xFF767676),
        chevron = Color(0xFFBDBDBD),
        controlTrack = Color(0xFFEBEBEB),
        controlSwitchOff = Color(0xFFD6D6D6),
        controlConvexTop = Color(0xFFFFFFFF),
        controlConvexBottom = Color(0xFFF2F2F4),
        controlConvexRimTop = tint(0xFFFFFF, 80f),
        controlConvexRimBottom = tint(0x000000, 8f),
        primary = Color(0xFF0068E0),
        primaryText = Color(0xFF0062D6),
        primaryFill = Color(0xFF0068E0),
        onPrimary = Color(0xFFFFFFFF),
        accentGlow = tint(0x0068E0, 25f),
        glowNeutral = tint(0xFFFFFF, 30f),
        statusWithin = Color(0xFF19762A),
        statusMoved = Color(0xFF8F5A00),
        statusNeutral = Color(0xFF666666),
        bandFill = Color(0xFF8C8C8C),
        markerWithin = Color(0xFFFFFFFF),
        markerWithinRim = Color(0xFFCCCCCC),
        chartPoint = Color(0xFF4A4A4A),
        chartGrid = tint(0x000000, 6f),
        chartBandArea = tint(0x000000, 8f),
        glassBar = tint(0xFFFFFF, 80f),
        glassTopBar = tint(0xF0F1F2, 82f),
        glassTopBarDim = tint(0xF0F1F2, 35f),
        glassSheet = tint(0xF5F5F8, 92f),
        glassDialog = tint(0xFFFFFF, 94f),
        glassRimTop = tint(0xFFFFFF, 80f),
        glassRimBottom = tint(0x000000, 8f),
        onGlassPrimary = Color(0xFF1A1A1A),
        onGlassSecondary = Color(0xFF474747),
        solidBarTop = Color(0xFFFFFFFF),
        solidBarBottom = Color(0xFFF7F7F9),
        solidSheet = Color(0xFFF5F5F8),
        solidDialog = Color(0xFFFFFFFF),
        scrim = tint(0x000000, 40f),
        sheetHandle = tint(0x000000, 26f),
        focusRing = Color(0xFF0068E0),
        statePressed = tint(0x000000, 6f),
        stateSelected = tint(0x000000, 6f),
        isDark = false,
    )

/** Dark theme, calibrated to the measured system apps: true black window, #1A1A1A cards, hue-free greys. */
val DarkNbColors: NbColors =
    NbColors(
        background = Color(0xFF000000),
        surface1 = Color(0xFF1A1A1A),
        surface2 = Color(0xFF242424),
        surface3 = Color(0xFF2E2E2E),
        surfaceElevated = Color(0xFF2B2B2B),
        textPrimary = Color(0xFFE9E9E9),
        textSecondary = Color(0xFF969696),
        textTertiary = Color(0xFF8A8A8A),
        separator = Color(0xFF484848),
        outline = Color(0xFF767676),
        chevron = Color(0xFF5F5F5F),
        controlTrack = Color(0xFF333333),
        controlSwitchOff = Color(0xFF505050),
        controlConvexTop = Color(0xFF1F1F1F),
        controlConvexBottom = Color(0xFF2A2A2A),
        controlConvexRimTop = Color(0xFF333333),
        controlConvexRimBottom = Color(0xFF3E3E3E),
        primary = Color(0xFF428AF7),
        primaryText = Color(0xFF5C9DFF),
        primaryFill = Color(0xFF2A6BE0),
        onPrimary = Color(0xFFFFFFFF),
        accentGlow = tint(0x428AF7, 35f),
        glowNeutral = tint(0xFFFFFF, 13.5f),
        statusWithin = Color(0xFF24B232),
        statusMoved = Color(0xFFE5A100),
        statusNeutral = Color(0xFF969696),
        bandFill = Color(0xFF6B6B6B),
        markerWithin = Color(0xFFFFFFFF),
        markerWithinRim = Color(0xFFFFFFFF),
        chartPoint = Color(0xFFE9E9E9),
        chartGrid = tint(0xFFFFFF, 8f),
        chartBandArea = tint(0xFFFFFF, 12f),
        glassBar = tint(0x1B1B1B, 80f),
        glassTopBar = tint(0x000000, 72f),
        glassTopBarDim = tint(0x000000, 35f),
        glassSheet = tint(0x171717, 92f),
        glassDialog = tint(0x232323, 94f),
        glassRimTop = tint(0xFFFFFF, 13f),
        glassRimBottom = tint(0xFFFFFF, 16f),
        onGlassPrimary = Color(0xFFE9E9E9),
        onGlassSecondary = Color(0xFFC0C0C0),
        solidBarTop = Color(0xFF1B1B1B),
        solidBarBottom = Color(0xFF232323),
        solidSheet = Color(0xFF171717),
        solidDialog = Color(0xFF232323),
        scrim = tint(0x000000, 60f),
        sheetHandle = tint(0xFFFFFF, 30f),
        focusRing = Color(0xFF5C9DFF),
        statePressed = tint(0xFFFFFF, 12.5f),
        stateSelected = Color(0xFF2E2E2E),
        isDark = true,
    )

/**
 * Colour of the token named [name] in `docs/design/tokens.md` (for example `color.status.moved`), or null when the
 * name is not a colour token. Used by the token parity and contrast tests and by the catalog's contrast overlay.
 */
fun NbColors.byTokenName(name: String): Color? = tokenAccessors[name]?.invoke(this)

/** Every colour token name of tokens.md section 2 mapped to its value. */
val NbColorTokenNames: Set<String> get() = tokenAccessors.keys

@Suppress("DEPRECATION") // The retired tokens stay in tokens.md (and checked) until they are deleted.
private val tokenAccessors: Map<String, (NbColors) -> Color> =
    mapOf(
        "color.background" to { it.background },
        "color.surface.1" to { it.surface1 },
        "color.surface.2" to { it.surface2 },
        "color.surface.3" to { it.surface3 },
        "color.surface.elevated" to { it.surfaceElevated },
        "color.text.primary" to { it.textPrimary },
        "color.text.secondary" to { it.textSecondary },
        "color.text.tertiary" to { it.textTertiary },
        "color.separator" to { it.separator },
        "color.outline" to { it.outline },
        "color.chevron" to { it.chevron },
        "color.control.track" to { it.controlTrack },
        "color.control.switchOff" to { it.controlSwitchOff },
        "color.control.convexTop" to { it.controlConvexTop },
        "color.control.convexBottom" to { it.controlConvexBottom },
        "color.control.convexRimTop" to { it.controlConvexRimTop },
        "color.control.convexRimBottom" to { it.controlConvexRimBottom },
        "color.primary" to { it.primary },
        "color.primaryText" to { it.primaryText },
        "color.primaryFill" to { it.primaryFill },
        "color.onPrimary" to { it.onPrimary },
        "color.accentGlow" to { it.accentGlow },
        "color.glow.neutral" to { it.glowNeutral },
        "color.status.within" to { it.statusWithin },
        "color.status.moved" to { it.statusMoved },
        "color.status.above" to { it.statusAbove },
        "color.status.below" to { it.statusBelow },
        "color.status.neutral" to { it.statusNeutral },
        "color.status.learning" to { it.statusLearning },
        "color.status.missing" to { it.statusMissing },
        "color.status.info" to { it.statusInfo },
        "color.band.fill" to { it.bandFill },
        "color.band.learningOutline" to { it.bandLearningOutline },
        "color.marker.within" to { it.markerWithin },
        "color.marker.withinRim" to { it.markerWithinRim },
        "color.marker.halo" to { it.markerHalo },
        "color.chart.point" to { it.chartPoint },
        "color.chart.grid" to { it.chartGrid },
        "color.chart.bandArea" to { it.chartBandArea },
        "color.chart.guide" to { it.chartGuide },
        "color.glass.bar" to { it.glassBar },
        "color.glass.topBar" to { it.glassTopBar },
        "color.glass.topBarDim" to { it.glassTopBarDim },
        "color.glass.sheet" to { it.glassSheet },
        "color.glass.dialog" to { it.glassDialog },
        "color.glass.rimTop" to { it.glassRimTop },
        "color.glass.rimBottom" to { it.glassRimBottom },
        "color.onGlass.primary" to { it.onGlassPrimary },
        "color.onGlass.secondary" to { it.onGlassSecondary },
        "color.solid.barTop" to { it.solidBarTop },
        "color.solid.barBottom" to { it.solidBarBottom },
        "color.solid.topBar" to { it.solidTopBar },
        "color.solid.sheet" to { it.solidSheet },
        "color.solid.dialog" to { it.solidDialog },
        "color.solid.tooltip" to { it.solidTooltip },
        "color.scrim" to { it.scrim },
        "color.sheetHandle" to { it.sheetHandle },
        "color.focus.ring" to { it.focusRing },
        "color.state.pressed" to { it.statePressed },
        "color.state.selected" to { it.stateSelected },
        // Retired (coloros-conformance.md 4.1): aliases of their replacements until the sweep deletes them.
        "color.primaryContainer" to { it.primaryContainer },
        "color.onPrimaryContainer" to { it.onPrimaryContainer },
        "color.accent" to { it.accent },
        "color.status.within.container" to { it.statusWithinContainer },
        "color.status.within.onContainer" to { it.statusWithinOnContainer },
        "color.status.moved.container" to { it.statusMovedContainer },
        "color.status.moved.onContainer" to { it.statusMovedOnContainer },
        "color.status.neutral.container" to { it.statusNeutralContainer },
        "color.status.neutral.onContainer" to { it.statusNeutralOnContainer },
        "color.status.info.container" to { it.statusInfoContainer },
        "color.status.info.onContainer" to { it.statusInfoOnContainer },
        "color.band.edge" to { it.bandEdge },
        "color.glass.rimMid" to { it.glassRimMid },
    )
