// Control tokens are literal values from docs/design/coloros-conformance.md section 4 (like the colour tables in NbColors).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours of the ColorOS-style controls, lists and buttons (work package WP3 of `coloros-conformance.md`, section 6).
 * Values shared with the foundations come from [NbColors] (one source of truth); the rest are control-only. Dark values are measured
 * on the folded dark references, light values on the unfolded light references (`settings-display`: background
 * #F0F1F2, card #FFFFFF, hairline #E0E0E0, switch off #D6D6D6, chevron #BDBDBD, header #6E6E6E, tab underline
 * #181818), adjusted where accessibility requires (`coloros-conformance.md` 1.2). `ControlContrastTest` checks the
 * pairs.
 */
@Immutable
data class NbControlColors(
    /** `color.primary`: switch on track, checked radio, selected calendar day, step dot (graphics, 3:1). */
    val accent: Color,
    /** `color.primaryText`: text buttons and links (4.5:1 on cards and the window). */
    val accentText: Color,
    /** `color.primaryFill` top of the primary button gradient (white label 4.5:1). */
    val fillTop: Color,
    /** `color.primaryFill` bottom of the primary button gradient (darker, so the label only gains contrast). */
    val fillBottom: Color,
    /** Label on [fillTop] / [fillBottom] and the switch / radio knob. */
    val onFill: Color,
    /** `elevation.accentGlow`: coloured light under the primary button. */
    val accentGlow: Color,
    /** Top gloss band of the primary button (white, fading to nothing by mid-height). */
    val gloss: Color,
    /** `color.control.switchOff`: switch track when off (state also carried by the knob position). */
    val switchOff: Color,
    /** Unchecked radio ring (3:1 against the card, section 1.2). */
    val radioRing: Color,
    /** `color.chevron`: decorative disclosure chevron. */
    val chevron: Color,
    /** `color.separator`: 1 px inset hairline between rows. */
    val hairline: Color,
    /** `type.sectionHeader` colour (= `text.tertiary`, on the window background). */
    val sectionHeader: Color,
    /** Item title (= `text.primary`). */
    val title: Color,
    /** Summary and value text (= `text.secondary`). */
    val summary: Color,
    /** Selected tab label and its underline. */
    val tabSelected: Color,
    /** Unselected tab label. */
    val tabUnselected: Color,
    /** `SecondaryButton` neutral fill. */
    val secondaryFill: Color,
    /** Unselected chip fill. */
    val chip: Color,
    /** Selected chip fill. */
    val chipSelected: Color,
    /** Selected chip 1 dp rim. */
    val chipSelectedRim: Color,
    /** Selected chip label. */
    val chipSelectedLabel: Color,
    /** `state.selected`: selected row in a grouped card. */
    val selectedRow: Color,
    /** Letter avatar fill when the app icon is not available. */
    val avatar: Color,
    /** Inactive onboarding step dot. */
    val dotInactive: Color,
)

/** Light control colours (measured on the light references where they exist). */
val LightNbControlColors: NbControlColors =
    NbControlColors(
        accent = LightNbColors.primary,
        accentText = LightNbColors.primaryText,
        fillTop = Color(0xFF0A6EE6),
        fillBottom = Color(0xFF0060D2),
        onFill = Color.White,
        accentGlow = LightNbColors.accentGlow,
        gloss = Color.White.copy(alpha = 0.22f),
        switchOff = LightNbColors.controlSwitchOff,
        radioRing = Color(0xFF858585),
        chevron = LightNbColors.chevron,
        hairline = LightNbColors.separator,
        sectionHeader = LightNbColors.textTertiary,
        title = LightNbColors.textPrimary,
        summary = LightNbColors.textSecondary,
        tabSelected = LightNbColors.textPrimary,
        tabUnselected = LightNbColors.textTertiary,
        secondaryFill = Color.Black.copy(alpha = 0.06f),
        chip = Color(0xFFE6E7E8),
        chipSelected = Color(0xFFFFFFFF),
        chipSelectedRim = Color.Black.copy(alpha = 0.10f),
        chipSelectedLabel = Color(0xFF1A1A1A),
        selectedRow = LightNbColors.stateSelected,
        avatar = Color(0xFFEDEDED),
        dotInactive = Color(0xFFD6D6D6),
    )

/** Dark control colours (measured on the folded dark references). */
val DarkNbControlColors: NbControlColors =
    NbControlColors(
        accent = DarkNbColors.primary,
        accentText = DarkNbColors.primaryText,
        fillTop = Color(0xFF2A6BE0),
        fillBottom = Color(0xFF2160D2),
        onFill = Color.White,
        accentGlow = DarkNbColors.accentGlow,
        gloss = Color.White.copy(alpha = 0.16f),
        switchOff = DarkNbColors.controlSwitchOff,
        radioRing = Color(0xFF767676),
        chevron = DarkNbColors.chevron,
        hairline = DarkNbColors.separator,
        sectionHeader = DarkNbColors.textTertiary,
        title = DarkNbColors.textPrimary,
        summary = DarkNbColors.textSecondary,
        tabSelected = DarkNbColors.textPrimary,
        tabUnselected = DarkNbColors.textTertiary,
        secondaryFill = Color.White.copy(alpha = 0.10f),
        chip = Color(0xFF262626),
        chipSelected = Color(0xFF505050),
        chipSelectedRim = Color.White.copy(alpha = 0.15f),
        chipSelectedLabel = Color(0xFFFFFFFF),
        selectedRow = DarkNbColors.stateSelected,
        avatar = Color(0xFF333333),
        dotInactive = Color(0xFF505050),
    )

/** Sizes of the ColorOS-style controls and list rows (`coloros-conformance.md` 4.3). */
object NbControlDimens {
    /** Row minimum height (= `target.min`). */
    val rowMinHeight: Dp = 48.dp

    /** Row vertical padding. */
    val rowPaddingVertical: Dp = 10.dp

    /** Row horizontal padding (and the hairline inset). */
    val rowPaddingHorizontal: Dp = 16.dp

    /** Title -> summary gap. */
    val rowTitleGap: Dp = 2.dp

    /** Gap between a row's text column and its trailing control. */
    val rowTrailingGap: Dp = 12.dp

    /** Space above a section header (with the 12 dp list spacing: the measured ~52 dp card-to-card step). */
    val sectionHeaderTop: Dp = 16.dp

    /** Section header: space between the header text and its card. */
    val sectionHeaderBottom: Dp = 8.dp

    /** Section header text start inside the gutter (32 dp from the screen edge with the 16 dp gutter). */
    val sectionHeaderStart: Dp = 16.dp

    /** Switch track width. */
    val switchWidth: Dp = 44.dp

    /** Switch track height. */
    val switchHeight: Dp = 24.dp

    /** Switch knob diameter. */
    val switchKnob: Dp = 18.dp

    /** Radio box. */
    val radioBox: Dp = 24.dp

    /** Unchecked ring / checked disc diameter. */
    val radioDisc: Dp = 20.dp

    /** Unchecked ring stroke. */
    val radioStroke: Dp = 1.3.dp

    /** Checked white dot diameter. */
    val radioDot: Dp = 9.dp

    /** Chevron box width (the glyph is about 5 x 10 dp). */
    val chevronWidth: Dp = 12.dp

    /** Chevron box height. */
    val chevronHeight: Dp = 24.dp

    /** Primary button minimum height. */
    val buttonHeight: Dp = 50.dp

    /** Primary button horizontal padding. */
    val buttonPaddingHorizontal: Dp = 28.dp

    /** Tab row height. */
    val tabRowHeight: Dp = 48.dp

    /** Tab label horizontal padding. */
    val tabPadding: Dp = 12.dp

    /** Tab underline thickness. */
    val tabIndicator: Dp = 2.dp

    /** Chip visual height (inside a 48 dp target). */
    val chipHeight: Dp = 36.dp

    /** Chip horizontal padding. */
    val chipPadding: Dp = 16.dp

    /** Leading icon / avatar of a source row. */
    val avatar: Dp = 40.dp
}

/** Text styles of the controls (`coloros-conformance.md` 4.2), derived from the theme scale (weights 400/500/700). */
object NbControlType {
    /** `type.itemTitle` 16/22/500. */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = NbTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)

    /** `type.summary` 14/19/400. */
    val summary: TextStyle
        @Composable @ReadOnlyComposable
        get() = NbTheme.typography.summary

    /** `type.sectionHeader` 12/16/500. */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = NbTheme.typography.sectionHeader

    /** `type.button` 16/22/500. */
    val button: TextStyle
        @Composable @ReadOnlyComposable
        get() = NbTheme.typography.button

    /** Chips, segments and tabs 14/20 (weight set per state). */
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = NbTheme.typography.label.copy(fontWeight = FontWeight.Medium)
}

/** The control colours of the current theme. */
val NbTheme.controls: NbControlColors
    @Composable @ReadOnlyComposable
    get() = if (colors.isDark) DarkNbControlColors else LightNbControlColors
