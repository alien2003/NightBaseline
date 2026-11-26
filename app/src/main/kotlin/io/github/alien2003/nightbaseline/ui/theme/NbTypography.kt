package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale of `tokens.md` section 3, after the ColorOS 17 system apps: system font only, weights 400 / 500 / 700
 * only (600 renders near-bold on the system sans), sizes and line heights in sp (they scale with the system font
 * scale up to 200 %, never capped), letter spacing 0, no all-caps, `includeFontPadding = false`, centred line height
 * without trimming. Numeric styles use tabular lining figures (`tnum, lnum`) so digits do not jitter.
 */
@Immutable
data class NbTypography(
    /** `type.largeTitle` 32/40 700: top-level screen title, first item of the scrolling content. */
    val largeTitle: TextStyle,
    /** `type.headline` 22/28 500: night summary headline. */
    val headline: TextStyle,
    /** `type.title` 20/26 500: pane and state-card titles. */
    val title: TextStyle,
    /** `type.titleSmall` = `type.itemTitle` 16/22 500: card titles, metric name, row titles. */
    val titleSmall: TextStyle,
    /** `type.body` 16/24 400. */
    val body: TextStyle,
    /** `type.bodySmall` 14/20 400: chart summary, banner text. */
    val bodySmall: TextStyle,
    /** `type.label` 14/20 500: chips, segments, status. */
    val label: TextStyle,
    /** `type.labelSmall` 12/16 500: weekday letters. */
    val labelSmall: TextStyle,
    /** `type.caption` 12/16 400: footnotes, disclaimer. */
    val caption: TextStyle,
    /** `type.numeric.display` 48/56 500 tnum: Metric detail header value. */
    val numericDisplay: TextStyle,
    /** `type.numeric.value` 32/38 500 tnum: last-night value on a tile. */
    val numericValue: TextStyle,
    /** `type.numeric.value` in the `Glance` layout: 28/34. */
    val numericValueGlance: TextStyle,
    /** `type.numeric.small` 15/20 500 tnum: stats, tooltip value, row values. */
    val numericSmall: TextStyle,
    /** `type.numeric.axis` 12/16 400 tnum: chart axis labels. */
    val numericAxis: TextStyle,
    /** `type.unit` 16/22 400: unit next to [numericValue]. */
    val unit: TextStyle,
    /** `type.unit` next to [numericSmall]: 13/18. */
    val unitSmall: TextStyle,
    /** `type.topBarTitle` 18/24 700: collapsed top-bar title, one line with ellipsis. */
    val topBarTitle: TextStyle,
    /** `type.dialogTitle` 18/24 700: sheet and dialog titles, centred. */
    val dialogTitle: TextStyle,
    /** `type.summary` 14/19 400: summaries under row titles (canon 13 sp, 14 for legibility). */
    val summary: TextStyle,
    /** `type.value` 14/20 400: trailing row values. */
    val value: TextStyle,
    /** `type.button` 16/22 500: button labels, text buttons. */
    val button: TextStyle,
    /** `type.sectionHeader` 12/16 500: grey section header outside the cards (`text.tertiary`). */
    val sectionHeader: TextStyle,
    /** `type.navLabel` 11/14 500: unselected navigation label (recorded exception to the 12 sp minimum). */
    val navLabel: TextStyle,
    /** `type.navLabel` 11/14 700: selected navigation label. */
    val navLabelSelected: TextStyle,
    /** `type.subtitleSmall` 14/19 400: subtitle under the large title (`text.tertiary`). */
    val subtitleSmall: TextStyle,
    /** `type.statusLabel` 13/18 400: "Usual", learning and missing labels. */
    val statusLabel: TextStyle,
    /** `type.statusLabel` for moved values: 14/20 500. */
    val statusLabelMoved: TextStyle,
) {
    /** `type.itemTitle` = [titleSmall]. */
    val itemTitle: TextStyle get() = titleSmall
}

private const val TABULAR_FIGURES = "tnum, lnum"

private fun style(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: Int,
    numeric: Boolean = false,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = FontWeight(weight),
        letterSpacing = 0.em,
        fontFeatureSettings = if (numeric) TABULAR_FIGURES else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None),
        // A word wider than a narrow pane at 200 % font (a book half next to the rail) breaks with a hyphen at a
        // syllable instead of at an arbitrary letter (review round 1, integration).
        hyphens = Hyphens.Auto,
    )

/** The one type scale (colour is applied by the components, not baked into the styles). */
val DefaultNbTypography: NbTypography =
    NbTypography(
        largeTitle = style(32.sp, 40.sp, 700),
        headline = style(22.sp, 28.sp, 500),
        title = style(20.sp, 26.sp, 500),
        titleSmall = style(16.sp, 22.sp, 500),
        body = style(16.sp, 24.sp, 400),
        bodySmall = style(14.sp, 20.sp, 400),
        label = style(14.sp, 20.sp, 500),
        labelSmall = style(12.sp, 16.sp, 500),
        caption = style(12.sp, 16.sp, 400),
        numericDisplay = style(48.sp, 56.sp, 500, numeric = true),
        numericValue = style(32.sp, 38.sp, 500, numeric = true),
        numericValueGlance = style(28.sp, 34.sp, 500, numeric = true),
        numericSmall = style(15.sp, 20.sp, 500, numeric = true),
        numericAxis = style(12.sp, 16.sp, 400, numeric = true),
        unit = style(16.sp, 22.sp, 400),
        unitSmall = style(13.sp, 18.sp, 400),
        topBarTitle = style(18.sp, 24.sp, 700),
        dialogTitle = style(18.sp, 24.sp, 700),
        summary = style(14.sp, 19.sp, 400),
        value = style(14.sp, 20.sp, 400),
        button = style(16.sp, 22.sp, 500),
        sectionHeader = style(12.sp, 16.sp, 500),
        navLabel = style(11.sp, 14.sp, 500),
        navLabelSelected = style(11.sp, 14.sp, 700),
        subtitleSmall = style(14.sp, 19.sp, 400),
        statusLabel = style(13.sp, 18.sp, 400),
        statusLabelMoved = style(14.sp, 20.sp, 500),
    )
