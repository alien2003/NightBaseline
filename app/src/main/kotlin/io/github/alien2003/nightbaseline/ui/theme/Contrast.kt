package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Kind of a contrast pair and its lowest allowed ratio (WCAG 2.x, `tokens.md` 2.10). */
enum class ContrastKind(
    /** Lowest allowed minimum for this kind. */
    val floor: Double,
) {
    /** Normal text: 4.5. */
    Text(4.5),

    /** Large text (>= 24 sp, or >= 19 sp at 700): 3.0. */
    Large(3.0),

    /** Icons, glyphs, band edges, markers, essential boundaries: 3.0. */
    Graphic(3.0),

    /** Focus indicator: 3.0. */
    Focus(3.0),
}

/** Which themes a pair is checked in. */
enum class ContrastTheme {
    /** Light only. */
    Light,

    /** Dark only. */
    Dark,

    /** Both themes. */
    Both,
}

/** One row of the pairs table of `tokens.md` 2.10: foreground and background token names and the required ratio. */
data class ContrastPair(
    /** Pair name; its first dot-separated part is the [kind]. */
    val name: String,
    /** Foreground token (must be opaque). */
    val fg: String,
    /** Background token; translucent means glass (checked over pure white and pure black). */
    val bg: String,
    /** Themes. */
    val theme: ContrastTheme,
    /** Required ratio. */
    val min: Double,
) {
    /** Kind from the name. */
    val kind: ContrastKind get() = ContrastKind.valueOf(name.substringBefore('.').replaceFirstChar { it.uppercase() })
}

/**
 * WCAG contrast of the Kotlin tokens, mirroring `tools/design/contrast_check.py`: relative luminance in sRGB, and
 * for a translucent background the worse ratio of compositing it over pure white and over pure black.
 */
object NbContrast {
    /** Contrast ratio of opaque [fg] on [bg] (translucent [bg] = worst case over white and black). */
    fun ratio(
        fg: Color,
        bg: Color,
    ): Double {
        if (bg.alpha >= OPAQUE) return ratioOpaque(fg, bg)
        return min(ratioOpaque(fg, composite(bg, Color.White)), ratioOpaque(fg, composite(bg, Color.Black)))
    }

    /** The ratio of [pair] in [colors], or null when a token name is unknown. */
    fun ratio(
        pair: ContrastPair,
        colors: NbColors,
    ): Double? {
        val fg = colors.byTokenName(pair.fg)
        val bg = colors.byTokenName(pair.bg)
        return if (fg == null || bg == null) null else ratio(fg, bg)
    }

    private fun ratioOpaque(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + DELTA) / (min(la, lb) + DELTA)
    }

    /** Relative luminance (WCAG 2.x) of an opaque sRGB colour, from 8-bit channels as the Python check does. */
    fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = (c * MAX_8BIT).let { kotlin.math.round(it) } / MAX_8BIT
            return if (v <= LINEAR_LIMIT) v / LINEAR_SLOPE else ((v + GAMMA_OFFSET) / (1 + GAMMA_OFFSET)).pow(GAMMA)
        }
        return R_WEIGHT * channel(color.red) + G_WEIGHT * channel(color.green) + B_WEIGHT * channel(color.blue)
    }

    /** [top] (translucent) over the opaque [bottom]. */
    fun composite(
        top: Color,
        bottom: Color,
    ): Color {
        val a = top.alpha
        return Color(
            red = top.red * a + bottom.red * (1 - a),
            green = top.green * a + bottom.green * (1 - a),
            blue = top.blue * a + bottom.blue * (1 - a),
            alpha = 1f,
        )
    }

    private const val OPAQUE = 0.9999f
    private const val DELTA = 0.05
    private const val MAX_8BIT = 255.0
    private const val LINEAR_LIMIT = 0.04045
    private const val LINEAR_SLOPE = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA = 2.4
    private const val R_WEIGHT = 0.2126
    private const val G_WEIGHT = 0.7152
    private const val B_WEIGHT = 0.0722
}

private fun both(
    name: String,
    fg: String,
    bg: String,
    min: Double,
) = ContrastPair(name, fg, bg, ContrastTheme.Both, min)

/** The pairs table of `tokens.md` 2.10 (kept identical to the Markdown by `ContrastTest`). */
val NbContrastPairs: List<ContrastPair> =
    listOf(
        both("text.primary.background", "color.text.primary", "color.background", 4.5),
        both("text.primary.surface1", "color.text.primary", "color.surface.1", 4.5),
        both("text.primary.surface2", "color.text.primary", "color.surface.2", 4.5),
        both("text.primary.surface3", "color.text.primary", "color.surface.3", 4.5),
        both("text.primary.elevated", "color.text.primary", "color.surface.elevated", 4.5),
        both("text.secondary.background", "color.text.secondary", "color.background", 4.5),
        both("text.secondary.surface1", "color.text.secondary", "color.surface.1", 4.5),
        both("text.secondary.surface2", "color.text.secondary", "color.surface.2", 4.5),
        both("text.secondary.surface3", "color.text.secondary", "color.surface.3", 4.5),
        both("text.secondary.elevated", "color.text.secondary", "color.surface.elevated", 4.5),
        both("text.tertiary.background", "color.text.tertiary", "color.background", 4.5),
        both("text.tertiary.surface1", "color.text.tertiary", "color.surface.1", 4.5),
        both("text.link.surface1", "color.primary", "color.surface.1", 4.5),
        both("text.link.background", "color.primary", "color.background", 4.5),
        both("text.primaryText.background", "color.primaryText", "color.background", 4.5),
        both("text.primaryText.surface1", "color.primaryText", "color.surface.1", 4.5),
        both("text.primaryText.surface3", "color.primaryText", "color.surface.3", 4.5),
        both("text.primaryText.elevated", "color.primaryText", "color.surface.elevated", 4.5),
        both("text.onPrimary", "color.onPrimary", "color.primaryFill", 4.5),
        both("text.status.within.surface1", "color.status.within", "color.surface.1", 4.5),
        both("text.status.within.surface2", "color.status.within", "color.surface.2", 4.5),
        both("text.status.moved.surface1", "color.status.moved", "color.surface.1", 4.5),
        both("text.status.moved.surface2", "color.status.moved", "color.surface.2", 4.5),
        both("text.status.neutral.surface1", "color.status.neutral", "color.surface.1", 4.5),
        both("text.status.neutral.surface2", "color.status.neutral", "color.surface.2", 4.5),
        both("text.status.info.surface1", "color.status.info", "color.surface.1", 4.5),
        both("text.status.info.surface2", "color.status.info", "color.surface.2", 4.5),
        both("text.status.within.surface3", "color.status.within", "color.surface.3", 4.5),
        both("text.status.moved.surface3", "color.status.moved", "color.surface.3", 4.5),
        both("text.status.neutral.surface3", "color.status.neutral", "color.surface.3", 4.5),
        both("text.status.info.surface3", "color.status.info", "color.surface.3", 4.5),
        both("text.status.within.background", "color.status.within", "color.background", 4.5),
        both("text.status.moved.background", "color.status.moved", "color.background", 4.5),
        both("text.onGlass.primary.bar", "color.onGlass.primary", "color.glass.bar", 4.5),
        both("text.onGlass.secondary.bar", "color.onGlass.secondary", "color.glass.bar", 4.5),
        both("text.onGlass.primary.topBar", "color.onGlass.primary", "color.glass.topBar", 4.5),
        both("text.onGlass.secondary.topBar", "color.onGlass.secondary", "color.glass.topBar", 4.5),
        both("text.onGlass.primary.sheet", "color.onGlass.primary", "color.glass.sheet", 4.5),
        both("text.onGlass.secondary.sheet", "color.onGlass.secondary", "color.glass.sheet", 4.5),
        both("text.onGlass.primary.dialog", "color.onGlass.primary", "color.glass.dialog", 4.5),
        both("text.onGlass.secondary.dialog", "color.onGlass.secondary", "color.glass.dialog", 4.5),
        both("text.onGlass.primary.solidBarTop", "color.onGlass.primary", "color.solid.barTop", 4.5),
        both("text.onGlass.primary.solidBarBottom", "color.onGlass.primary", "color.solid.barBottom", 4.5),
        both("text.onGlass.secondary.solidBarTop", "color.onGlass.secondary", "color.solid.barTop", 4.5),
        both("text.onGlass.secondary.solidBarBottom", "color.onGlass.secondary", "color.solid.barBottom", 4.5),
        both("text.onGlass.primary.solidTopBar", "color.onGlass.primary", "color.solid.topBar", 4.5),
        both("text.onGlass.secondary.solidTopBar", "color.onGlass.secondary", "color.solid.topBar", 4.5),
        both("text.onGlass.primary.solidSheet", "color.onGlass.primary", "color.solid.sheet", 4.5),
        both("text.onGlass.secondary.solidSheet", "color.onGlass.secondary", "color.solid.sheet", 4.5),
        both("text.onGlass.primary.solidDialog", "color.onGlass.primary", "color.solid.dialog", 4.5),
        both("text.onGlass.secondary.solidDialog", "color.onGlass.secondary", "color.solid.dialog", 4.5),
        both("text.primary.tooltip", "color.text.primary", "color.solid.tooltip", 4.5),
        both("text.secondary.tooltip", "color.text.secondary", "color.solid.tooltip", 4.5),
        both("text.status.within.tooltip", "color.status.within", "color.solid.tooltip", 4.5),
        both("text.status.moved.tooltip", "color.status.moved", "color.solid.tooltip", 4.5),
        both("text.status.neutral.tooltip", "color.status.neutral", "color.solid.tooltip", 4.5),
        both("large.value.surface1", "color.text.primary", "color.surface.1", 4.5),
        both("graphic.band.fill.surface1", "color.band.fill", "color.surface.1", 3.0),
        both("graphic.marker.within.fill", "color.marker.within", "color.band.fill", 3.0),
        both("graphic.marker.within.surface1", "color.status.within", "color.surface.1", 3.0),
        both("graphic.marker.moved.track", "color.status.moved", "color.control.track", 3.0),
        both("graphic.marker.moved.surface1", "color.status.moved", "color.surface.1", 3.0),
        both("graphic.marker.neutral.track", "color.status.neutral", "color.control.track", 3.0),
        both("graphic.chart.point.surface1", "color.chart.point", "color.surface.1", 3.0),
        both("graphic.learningOutline.surface1", "color.band.learningOutline", "color.surface.1", 3.0),
        both("graphic.outline.surface1", "color.outline", "color.surface.1", 3.0),
        both("graphic.outline.surface2", "color.outline", "color.surface.2", 3.0),
        both("graphic.outline.background", "color.outline", "color.background", 3.0),
        both("graphic.primary.surface1", "color.primary", "color.surface.1", 3.0),
        both("graphic.primary.background", "color.primary", "color.background", 3.0),
        both("graphic.navIcon.bar", "color.onGlass.secondary", "color.glass.bar", 3.0),
        both("graphic.roundButton.glyph", "color.onGlass.primary", "color.control.convexBottom", 3.0),
        both("graphic.onPrimary.primary", "color.onPrimary", "color.primary", 3.0),
        both("focus.ring.background", "color.focus.ring", "color.background", 3.0),
        both("focus.ring.surface1", "color.focus.ring", "color.surface.1", 3.0),
        both("focus.ring.surface3", "color.focus.ring", "color.surface.3", 3.0),
        both("focus.ring.glassBar", "color.focus.ring", "color.glass.bar", 3.0),
        both("focus.ring.glassSheet", "color.focus.ring", "color.glass.sheet", 3.0),
        both("focus.ring.glassDialog", "color.focus.ring", "color.glass.dialog", 3.0),
        both("focus.ring.solidBarBottom", "color.focus.ring", "color.solid.barBottom", 3.0),
    )
