// Shell tokens are literal values from docs/design/coloros-conformance.md 4 (like the colour tables in NbColors).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours of the shell (bar, rail, top bar, round buttons, sheets, dialogs) that `NbColors` does not have yet
 * (`coloros-conformance.md` 4.1, WP2). Dark values are measured on the owner's phone (`reference-analysis.md` 2, 4.1);
 * light values come from the light captures of Clock and Settings (bar #FAFBFB with a white top rim and a white
 * bottom band, round button #F6F7F8 with a soft grey shadow on #F0F1F2). After all work packages merge, the sweep
 * folds these into `NbColors` under the same names.
 */
@Immutable
data class ShellColors(
    /** `nav.selected`: filled icon and bold label of the selected destination. */
    val navSelected: Color,
    /** `nav.unselected`: outline icon and regular label. */
    val navUnselected: Color,
    /** `glass.bar` tint over the blur (bar and rail). */
    val barTint: Color,
    /** Light added towards the bottom of the bar (vertical gradient transparent -> this). */
    val barSheen: Color,
    /** `solid.barTop`. */
    val barSolidTop: Color,
    /** `solid.barBottom`. */
    val barSolidBottom: Color,
    /** `glass.rimTop`: thin highlight along the top edge. */
    val rimTop: Color,
    /** Width of [rimTop] inside the shape. */
    val rimTopWidth: Dp,
    /** `glass.rimBottom`: the faint bottom reflection band. */
    val rimBottom: Color,
    /** Width of the bottom reflection band inside the shape. */
    val rimBottomWidth: Dp,
    /** `control.convexTop`: round shell button, top of its vertical gradient. */
    val convexTop: Color,
    /** `control.convexBottom`. */
    val convexBottom: Color,
    /** `control.convexRimTop`. */
    val convexRimTop: Color,
    /** `control.convexRimBottom`. */
    val convexRimBottom: Color,
    /** Glyph on a round shell button. */
    val convexGlyph: Color,
    /** `glow.neutral`: the touch light on shell surfaces (dark: light; light: a soft shade, white is invisible). */
    val glow: Color,
    /** `glass.sheet` tint. */
    val sheetTint: Color,
    /** `solid.sheet`. */
    val sheetSolid: Color,
    /** Top highlight of a sheet. */
    val sheetHighlight: Color,
    /** `glass.dialog` tint. */
    val dialogTint: Color,
    /** `solid.dialog`. */
    val dialogSolid: Color,
    /** `sheetHandle`. */
    val sheetHandle: Color,
    /** Neutral pill of a dialog's dismiss button. */
    val secondaryFill: Color,
    /** `primaryFill`, top of the accent gradient of filled buttons with a white label (>= 4.5:1). */
    val primaryFillTop: Color,
    /** `primaryFill`, bottom of the accent gradient. */
    val primaryFillBottom: Color,
    /** Top gloss band of a filled accent button. */
    val primaryGloss: Color,
    /** `elevation.accentGlow` colour under a filled accent button. */
    val primaryGlow: Color,
    /** Strength of the top-bar darkening (`background` at this alpha under the status bar, fading to 0). */
    val topBarDim: Float,
    /** Shadow of the bar and rail. */
    val shellShadow: NbShadow,
    /** Shadow of a round shell button (`elevation.control`). */
    val controlShadow: NbShadow,
    /** Shadow of sheets and dialogs. */
    val overlayShadow: NbShadow,
)

/** Dark shell colours (measured: bar #1B1B1B -> #232323, rims #363636 / #3B3B3B, button #1F1F1F -> #2A2A2A). */
val DarkShellColors: ShellColors =
    ShellColors(
        navSelected = Color(0xFFE9E9E9),
        navUnselected = Color(0xFFC0C0C0),
        barTint = Color(0xFF1B1B1B).copy(alpha = 0.80f),
        barSheen = Color.White.copy(alpha = 0.035f),
        barSolidTop = Color(0xFF1B1B1B),
        barSolidBottom = Color(0xFF232323),
        rimTop = Color.White.copy(alpha = 0.13f),
        rimTopWidth = 0.5.dp,
        rimBottom = Color.White.copy(alpha = 0.16f),
        rimBottomWidth = 2.dp,
        convexTop = Color(0xFF1F1F1F),
        convexBottom = Color(0xFF2A2A2A),
        convexRimTop = Color(0xFF333333),
        convexRimBottom = Color(0xFF3E3E3E),
        convexGlyph = Color(0xFFE9E9E9),
        glow = Color.White.copy(alpha = 0.135f),
        sheetTint = Color(0xFF171717).copy(alpha = 0.92f),
        sheetSolid = Color(0xFF171717),
        sheetHighlight = Color.White.copy(alpha = 0.10f),
        dialogTint = Color(0xFF232323).copy(alpha = 0.96f),
        dialogSolid = Color(0xFF232323),
        sheetHandle = Color.White.copy(alpha = 0.30f),
        secondaryFill = Color.White.copy(alpha = 0.10f),
        primaryFillTop = Color(0xFF2E70E4),
        primaryFillBottom = Color(0xFF2563D6),
        primaryGloss = Color.White.copy(alpha = 0.16f),
        primaryGlow = Color(0xFF428AF7).copy(alpha = 0.35f),
        topBarDim = 0.35f,
        shellShadow = NbShadow(4.dp, 10.dp, 0.35f),
        controlShadow = NbShadow(3.dp, 8.dp, 0.20f),
        overlayShadow = NbShadow(0.dp, 24.dp, 0.30f),
    )

/** Light shell colours (light captures: bar #FAFBFB with white rims, button #F6F7F8, soft grey shadows). */
val LightShellColors: ShellColors =
    ShellColors(
        navSelected = Color(0xFF1A1A1A),
        navUnselected = Color(0xFF474747),
        barTint = Color(0xFFFAFBFB).copy(alpha = 0.86f),
        barSheen = Color.Transparent,
        barSolidTop = Color(0xFFFBFBFC),
        barSolidBottom = Color(0xFFF8F9F9),
        rimTop = Color.White,
        rimTopWidth = 1.dp,
        rimBottom = Color.White,
        rimBottomWidth = 2.dp,
        convexTop = Color(0xFFFAFAFB),
        convexBottom = Color(0xFFF3F4F5),
        convexRimTop = Color.White,
        convexRimBottom = Color(0xFFFCFCFC),
        convexGlyph = Color(0xFF181818),
        glow = Color.Black.copy(alpha = 0.06f),
        sheetTint = Color(0xFFF5F5F8).copy(alpha = 0.92f),
        sheetSolid = Color(0xFFF5F5F8),
        sheetHighlight = Color.White,
        dialogTint = Color.White.copy(alpha = 0.96f),
        dialogSolid = Color.White,
        sheetHandle = Color.Black.copy(alpha = 0.26f),
        secondaryFill = Color.Black.copy(alpha = 0.06f),
        primaryFillTop = Color(0xFF0A72E8),
        primaryFillBottom = Color(0xFF0060D0),
        primaryGloss = Color.White.copy(alpha = 0.18f),
        primaryGlow = Color(0xFF0068E0).copy(alpha = 0.25f),
        topBarDim = 0.55f,
        shellShadow = NbShadow(4.dp, 12.dp, 0.10f),
        controlShadow = NbShadow(2.dp, 10.dp, 0.10f),
        overlayShadow = NbShadow(0.dp, 24.dp, 0.12f),
    )

/** Shell geometry (`coloros-conformance.md` 4.3, measured in `reference-analysis.md` 2 and 4.1). */
object ShellDimens {
    /** Floating bar minimum height (grows when labels wrap at large font). */
    val barHeight: Dp = 56.dp

    /** Bar item minimum size. */
    val barItemWidth: Dp = 70.dp

    /** Bar item minimum height (= `target.min`). */
    val barItemHeight: Dp = 48.dp

    /** Gap between bar items. */
    val barItemGap: Dp = 8.dp

    /** Bar padding at the start and end. */
    val barPaddingHorizontal: Dp = 12.dp

    /** Bar padding at the top and bottom. */
    val barPaddingVertical: Dp = 4.dp

    /** Space above the icon inside a bar item. */
    val barItemTop: Dp = 4.dp

    /** Icon -> label gap in bar and rail items. */
    val navLabelGap: Dp = 2.dp

    /** Minimum distance of the bar from the screen's bottom edge (gesture navigation). */
    val barBottomMin: Dp = 16.dp

    /** Space above the gesture-navigation inset. */
    val barAboveGestureInset: Dp = 4.dp

    /** Space above a 3-button navigation bar. */
    val barAboveButtonsInset: Dp = 8.dp

    /** Insets taller than this are a 3-button navigation bar. */
    val gestureInsetMax: Dp = 32.dp

    /** Space between the bar's top and the end of scrolling content. */
    val barContentGap: Dp = 16.dp

    /** Rail minimum width. */
    val railWidth: Dp = 56.dp

    /** Rail item minimum height. */
    val railItemHeight: Dp = 56.dp

    /** Gap between rail items. */
    val railItemGap: Dp = 8.dp

    /** Rail padding at the top and bottom. */
    val railPaddingVertical: Dp = 12.dp

    /** Rail padding at the sides. */
    val railPaddingHorizontal: Dp = 4.dp

    /** Rail offset from the start edge. */
    val railOffset: Dp = 16.dp

    /** Toolbar row below the status bar. */
    val toolbar: Dp = 52.dp

    /** Toolbar row in short windows. */
    val toolbarShort: Dp = 48.dp

    /** Fade zone of the top-bar blur and darkening below the toolbar. */
    val topBarFade: Dp = 16.dp

    /** Scroll under the top bar after which its material is at full strength. */
    val topBarTintDistance: Dp = 10.dp

    /** Scroll at which the small toolbar title starts to fade in (large-title pages). */
    val smallTitleFadeStart: Dp = 40.dp

    /** Scroll at which the small toolbar title is fully visible. */
    val smallTitleFadeEnd: Dp = 72.dp

    /** Visual edge of round buttons and the title from the screen edge. */
    val toolbarMargin: Dp = 16.dp

    /** Gap between the back button and the toolbar title (title starts at 66 dp). */
    val toolbarTitleGap: Dp = 10.dp

    /** Space below the large title block before the first card (the page adds 8 dp more). */
    val largeTitleBottom: Dp = 12.dp

    /** Height of the large title block before it is measured (title, subtitle, bottom space). */
    val largeTitleEstimate: Dp = 60.dp

    /** Round shell button: visual circle. */
    val roundButton: Dp = 40.dp

    /** Glyph inside a round shell button. */
    val roundButtonGlyph: Dp = 20.dp

    /** Touch light radius (capped by the element). */
    val glowRadius: Dp = 140.dp

    /** Sheet corner radius (top corners). */
    val sheetRadius: Dp = 28.dp

    /** Sheet handle width. */
    val handleWidth: Dp = 36.dp

    /** Sheet handle height. */
    val handleHeight: Dp = 4.dp

    /** Space above the handle. */
    val handleTop: Dp = 10.dp

    /** Space below the handle. */
    val handleBottom: Dp = 6.dp

    /** Sheet header row (centred title, round close button). */
    val sheetHeader: Dp = 56.dp

    /** Gap between a sheet's top and the status bar. */
    val sheetTopGap: Dp = 24.dp

    /** Upward rubber band of a dragged sheet. */
    val sheetRubberBand: Dp = 72.dp

    /** Dialog corner radius (owner decision: 30 dp). */
    val dialogRadius: Dp = 30.dp

    /** Dialog maximum width. */
    val dialogMaxWidth: Dp = 392.dp

    /** Dialog margin from the window edges when docked at the bottom. */
    val dialogMargin: Dp = 16.dp

    /** Dialog inner padding around the text. */
    val dialogPadding: Dp = 24.dp

    /** Dialog padding around the buttons. */
    val dialogButtonInset: Dp = 16.dp

    /** Title -> message. */
    val dialogTitleGap: Dp = 6.dp

    /** Message -> buttons. */
    val dialogButtonsGap: Dp = 22.dp

    /** Visual height of a dialog button (in a 48 dp target). */
    val dialogButton: Dp = 44.dp

    /** Windows at least this wide centre dialogs instead of docking them. */
    const val DIALOG_DOCK_MAX_WIDTH: Float = 600f
}

/**
 * Toolbar height per layout mode: 48 dp in short windows, 52 dp otherwise; on large-title pages the resting bar adds
 * the large title block ([ShellDimens.largeTitleEstimate] until measured).
 */
fun shellToolbarHeight(
    short: Boolean,
    large: Boolean,
): Dp =
    when {
        large -> ShellDimens.toolbar + ShellDimens.largeTitleEstimate
        short -> ShellDimens.toolbarShort
        else -> ShellDimens.toolbar
    }

/** Shell shapes. */
object ShellShapes {
    /** Bottom sheet: top corners [ShellDimens.sheetRadius]. */
    val sheet: Shape =
        SmoothCornerShape(topStart = ShellDimens.sheetRadius, topEnd = ShellDimens.sheetRadius, bottomEnd = 0.dp, bottomStart = 0.dp)

    /** Dialog: [ShellDimens.dialogRadius]. */
    val dialog: Shape = SmoothCornerShape(ShellDimens.dialogRadius)
}

/** Shell text styles (`coloros-conformance.md` 4.2: `type.topBarTitle`, `dialogTitle`, `navLabel`, `subtitleSmall`). */
@Immutable
data class ShellType(
    /** 18 / 24 / 700. */
    val topBarTitle: TextStyle,
    /** 18 / 24 / 700, centred. */
    val dialogTitle: TextStyle,
    /** 14 / 20 / 400, centred. */
    val dialogMessage: TextStyle,
    /** 11 / 14 / 500 (unselected; selected is bold). */
    val navLabel: TextStyle,
    /** 14 / 19 / 400. */
    val subtitleSmall: TextStyle,
    /** 16 / 22 / 500. */
    val button: TextStyle,
)

/** Access to the shell tokens of the current theme. */
object ShellTokens {
    /** Shell colours of the current theme. */
    val colors: ShellColors
        @Composable @ReadOnlyComposable
        get() = if (NbTheme.colors.isDark) DarkShellColors else LightShellColors

    /** Shell text styles, derived from the current type scale. */
    val type: ShellType
        @Composable @ReadOnlyComposable
        get() {
            val base = NbTheme.typography.body
            return ShellType(
                topBarTitle = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                dialogTitle = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                dialogMessage =
                    base.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                navLabel =
                    base.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                subtitleSmall = base.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
                button = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
}
