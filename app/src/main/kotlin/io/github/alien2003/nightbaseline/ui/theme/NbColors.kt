package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour token of `docs/design/tokens.md` section 2, for one theme. Names follow the token names without dots
 * (`color.status.moved` -> [statusMoved]); aliases (`= other.token`) are resolved to the same value. Translucent
 * tokens (`#RRGGBB @ NN%`) carry their alpha. There is no dynamic colour and no error colour ([decision], tokens.md 0
 * and 2.3). `ColorTokensTest` keeps these values identical to the Markdown tables and `ContrastTest` checks the pairs.
 */
@Immutable
data class NbColors(
    /** `color.background`: window background, page behind cards. */
    val background: Color,
    /** `color.surface.1`: cards, tiles, chart card, settings groups. */
    val surface1: Color,
    /** `color.surface.2`: nested areas inside a card. */
    val surface2: Color,
    /** `color.surface.3`: selected row, segmented track, pressed fill. */
    val surface3: Color,
    /** `color.text.primary`. */
    val textPrimary: Color,
    /** `color.text.secondary`. */
    val textSecondary: Color,
    /** `color.text.tertiary`: only on [background] and [surface1]. */
    val textTertiary: Color,
    /** `color.separator`: decorative hairline. */
    val separator: Color,
    /** `color.outline`: essential boundaries. */
    val outline: Color,
    /** `color.primary`. */
    val primary: Color,
    /** `color.onPrimary`. */
    val onPrimary: Color,
    /** `color.primaryContainer`: selected navigation capsule, selected period. */
    val primaryContainer: Color,
    /** `color.onPrimaryContainer`. */
    val onPrimaryContainer: Color,
    /** `color.accent`: learning progress. */
    val accent: Color,
    /** `color.accentGlow`: finger-following light on glass items (decorative). */
    val accentGlow: Color,
    /** `color.status.within`. */
    val statusWithin: Color,
    /** `color.status.moved` (also `status.above` and `status.below`). */
    val statusMoved: Color,
    /** `color.status.neutral` (also `status.learning` and `status.missing`). */
    val statusNeutral: Color,
    /** `color.status.info`: data-access problems. */
    val statusInfo: Color,
    /** `color.status.within.container`. */
    val statusWithinContainer: Color,
    /** `color.status.within.onContainer`. */
    val statusWithinOnContainer: Color,
    /** `color.status.moved.container`. */
    val statusMovedContainer: Color,
    /** `color.status.moved.onContainer`. */
    val statusMovedOnContainer: Color,
    /** `color.status.neutral.container`. */
    val statusNeutralContainer: Color,
    /** `color.status.neutral.onContainer`. */
    val statusNeutralOnContainer: Color,
    /** `color.status.info.container`. */
    val statusInfoContainer: Color,
    /** `color.status.info.onContainer`. */
    val statusInfoOnContainer: Color,
    /** `color.band.fill`. */
    val bandFill: Color,
    /** `color.band.edge`: 1.5 dp edges and the median tick. */
    val bandEdge: Color,
    /** `color.chart.point`. */
    val chartPoint: Color,
    /** `color.chart.grid` (decorative). */
    val chartGrid: Color,
    /** `color.glass.bar`: bottom bar and rail tint. */
    val glassBar: Color,
    /** `color.glass.topBar`. */
    val glassTopBar: Color,
    /** `color.glass.sheet` (also `glass.dialog`). */
    val glassSheet: Color,
    /** `color.glass.rimTop`. */
    val glassRimTop: Color,
    /** `color.glass.rimMid`. */
    val glassRimMid: Color,
    /** `color.glass.rimBottom`. */
    val glassRimBottom: Color,
    /** `color.glass.rimOuter`: 0.5 dp outer hairline. */
    val glassRimOuter: Color,
    /** `color.onGlass.primary`. */
    val onGlassPrimary: Color,
    /** `color.onGlass.secondary`. */
    val onGlassSecondary: Color,
    /** `color.solid.barTop`. */
    val solidBarTop: Color,
    /** `color.solid.barBottom`. */
    val solidBarBottom: Color,
    /** `color.solid.sheet` (also `solid.dialog`). */
    val solidSheet: Color,
    /** `color.solid.tooltip`. */
    val solidTooltip: Color,
    /** `color.scrim`. */
    val scrim: Color,
    /** `color.focus.ring`. */
    val focusRing: Color,
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

    /** `color.band.learningOutline` = [outline]. */
    val bandLearningOutline: Color get() = outline

    /** `color.marker.halo` = [surface1]. */
    val markerHalo: Color get() = surface1

    /** `color.chart.guide` = [textSecondary]. */
    val chartGuide: Color get() = textSecondary

    /** `color.glass.dialog` = [glassSheet]. */
    val glassDialog: Color get() = glassSheet

    /** `color.solid.topBar` = [background]. */
    val solidTopBar: Color get() = background

    /** `color.solid.dialog` = [solidSheet]. */
    val solidDialog: Color get() = solidSheet
}

/** `#RRGGBB @ NN%` from tokens.md. */
private fun tint(
    rgb: Long,
    alphaPercent: Int,
): Color = Color(0xFF000000 or rgb).copy(alpha = alphaPercent / 100f)

/** Light theme (our own; no light references were captured). */
val LightNbColors: NbColors =
    NbColors(
        background = Color(0xFFF1F2F5),
        surface1 = Color(0xFFFFFFFF),
        surface2 = Color(0xFFF6F7F9),
        surface3 = Color(0xFFE9EBF0),
        textPrimary = Color(0xFF16171A),
        textSecondary = Color(0xFF50535B),
        textTertiary = Color(0xFF62656D),
        separator = Color(0xFFDCDEE3),
        outline = Color(0xFF747883),
        primary = Color(0xFF2C5BD6),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE5FB),
        onPrimaryContainer = Color(0xFF12306F),
        accent = Color(0xFF6650C8),
        accentGlow = tint(0x6650C8, 18),
        statusWithin = Color(0xFF1E7566),
        statusMoved = Color(0xFF95560F),
        statusNeutral = Color(0xFF5B606B),
        statusInfo = Color(0xFF3E5C96),
        statusWithinContainer = Color(0xFFE1F2ED),
        statusWithinOnContainer = Color(0xFF0F4A3F),
        statusMovedContainer = Color(0xFFF8EAD6),
        statusMovedOnContainer = Color(0xFF5C3306),
        statusNeutralContainer = Color(0xFFECEDF1),
        statusNeutralOnContainer = Color(0xFF3A3E47),
        statusInfoContainer = Color(0xFFE3E9F6),
        statusInfoOnContainer = Color(0xFF233C6B),
        bandFill = Color(0xFFDCE4F5),
        bandEdge = Color(0xFF6F7FA6),
        chartPoint = Color(0xFF4A4F5A),
        chartGrid = Color(0xFFE6E8EC),
        glassBar = tint(0xF7F8FA, 80),
        glassTopBar = tint(0xF1F2F5, 82),
        glassSheet = tint(0xF7F8FA, 90),
        glassRimTop = tint(0xFFFFFF, 90),
        glassRimMid = tint(0xFFFFFF, 40),
        glassRimBottom = tint(0xFFFFFF, 60),
        glassRimOuter = tint(0x000000, 7),
        onGlassPrimary = Color(0xFF111215),
        onGlassSecondary = Color(0xFF3E4047),
        solidBarTop = Color(0xFFFCFCFD),
        solidBarBottom = Color(0xFFF2F3F6),
        solidSheet = Color(0xFFFFFFFF),
        solidTooltip = Color(0xFFFFFFFF),
        scrim = tint(0x0E1014, 32),
        focusRing = Color(0xFF1F4FD1),
        isDark = false,
    )

/** Dark theme, calibrated to the measured system apps (true black window, #1A1A1A cards) with a faint cool tint. */
val DarkNbColors: NbColors =
    NbColors(
        background = Color(0xFF000000),
        surface1 = Color(0xFF1A1B1E),
        surface2 = Color(0xFF232428),
        surface3 = Color(0xFF2D2E33),
        textPrimary = Color(0xFFECEDF0),
        textSecondary = Color(0xFFA3A6AE),
        textTertiary = Color(0xFF8E919A),
        separator = Color(0xFF34353A),
        outline = Color(0xFF8A8E98),
        primary = Color(0xFF8FB0FF),
        onPrimary = Color(0xFF0B1C45),
        primaryContainer = Color(0xFF25314F),
        onPrimaryContainer = Color(0xFFD7E2FF),
        accent = Color(0xFFB6A8FF),
        accentGlow = tint(0xB6A8FF, 22),
        statusWithin = Color(0xFF74CDB9),
        statusMoved = Color(0xFFE8B56E),
        statusNeutral = Color(0xFFA4A9B4),
        statusInfo = Color(0xFFA9BDE6),
        statusWithinContainer = Color(0xFF143029),
        statusWithinOnContainer = Color(0xFFB1E8DA),
        statusMovedContainer = Color(0xFF3A2A14),
        statusMovedOnContainer = Color(0xFFF5D3A2),
        statusNeutralContainer = Color(0xFF2A2C31),
        statusNeutralOnContainer = Color(0xFFD2D5DC),
        statusInfoContainer = Color(0xFF1F2A42),
        statusInfoOnContainer = Color(0xFFCFDBF5),
        bandFill = Color(0xFF2B3550),
        bandEdge = Color(0xFF8794B8),
        chartPoint = Color(0xFFB9BDC6),
        chartGrid = Color(0xFF2C2D32),
        glassBar = tint(0x1C1D21, 78),
        glassTopBar = tint(0x0E0E10, 74),
        glassSheet = tint(0x1E1F23, 88),
        glassRimTop = tint(0xFFFFFF, 16),
        glassRimMid = tint(0xFFFFFF, 5),
        glassRimBottom = tint(0xFFFFFF, 11),
        glassRimOuter = tint(0x000000, 45),
        onGlassPrimary = Color(0xFFF2F3F5),
        onGlassSecondary = Color(0xFFCDD0D6),
        solidBarTop = Color(0xFF1C1D21),
        solidBarBottom = Color(0xFF24252A),
        solidSheet = Color(0xFF1E1F23),
        solidTooltip = Color(0xFF2A2B30),
        scrim = tint(0x000000, 60),
        focusRing = Color(0xFFA9C2FF),
        isDark = true,
    )

/**
 * Colour of the token named [name] in `docs/design/tokens.md` (for example `color.status.moved`), or null when the
 * name is not a colour token. Used by the token parity and contrast tests and by the catalog's contrast overlay.
 */
fun NbColors.byTokenName(name: String): Color? = tokenAccessors[name]?.invoke(this)

/** Every colour token name of tokens.md section 2 mapped to its value. */
val NbColorTokenNames: Set<String> get() = tokenAccessors.keys

private val tokenAccessors: Map<String, (NbColors) -> Color> =
    mapOf(
        "color.background" to { it.background },
        "color.surface.1" to { it.surface1 },
        "color.surface.2" to { it.surface2 },
        "color.surface.3" to { it.surface3 },
        "color.text.primary" to { it.textPrimary },
        "color.text.secondary" to { it.textSecondary },
        "color.text.tertiary" to { it.textTertiary },
        "color.separator" to { it.separator },
        "color.outline" to { it.outline },
        "color.primary" to { it.primary },
        "color.onPrimary" to { it.onPrimary },
        "color.primaryContainer" to { it.primaryContainer },
        "color.onPrimaryContainer" to { it.onPrimaryContainer },
        "color.accent" to { it.accent },
        "color.accentGlow" to { it.accentGlow },
        "color.status.within" to { it.statusWithin },
        "color.status.moved" to { it.statusMoved },
        "color.status.above" to { it.statusAbove },
        "color.status.below" to { it.statusBelow },
        "color.status.neutral" to { it.statusNeutral },
        "color.status.learning" to { it.statusLearning },
        "color.status.missing" to { it.statusMissing },
        "color.status.info" to { it.statusInfo },
        "color.status.within.container" to { it.statusWithinContainer },
        "color.status.within.onContainer" to { it.statusWithinOnContainer },
        "color.status.moved.container" to { it.statusMovedContainer },
        "color.status.moved.onContainer" to { it.statusMovedOnContainer },
        "color.status.neutral.container" to { it.statusNeutralContainer },
        "color.status.neutral.onContainer" to { it.statusNeutralOnContainer },
        "color.status.info.container" to { it.statusInfoContainer },
        "color.status.info.onContainer" to { it.statusInfoOnContainer },
        "color.band.fill" to { it.bandFill },
        "color.band.edge" to { it.bandEdge },
        "color.band.learningOutline" to { it.bandLearningOutline },
        "color.marker.halo" to { it.markerHalo },
        "color.chart.point" to { it.chartPoint },
        "color.chart.grid" to { it.chartGrid },
        "color.chart.guide" to { it.chartGuide },
        "color.glass.bar" to { it.glassBar },
        "color.glass.topBar" to { it.glassTopBar },
        "color.glass.sheet" to { it.glassSheet },
        "color.glass.dialog" to { it.glassDialog },
        "color.glass.rimTop" to { it.glassRimTop },
        "color.glass.rimMid" to { it.glassRimMid },
        "color.glass.rimBottom" to { it.glassRimBottom },
        "color.glass.rimOuter" to { it.glassRimOuter },
        "color.onGlass.primary" to { it.onGlassPrimary },
        "color.onGlass.secondary" to { it.onGlassSecondary },
        "color.solid.barTop" to { it.solidBarTop },
        "color.solid.barBottom" to { it.solidBarBottom },
        "color.solid.topBar" to { it.solidTopBar },
        "color.solid.sheet" to { it.solidSheet },
        "color.solid.dialog" to { it.solidDialog },
        "color.solid.tooltip" to { it.solidTooltip },
        "color.scrim" to { it.scrim },
        "color.focus.ring" to { it.focusRing },
    )
