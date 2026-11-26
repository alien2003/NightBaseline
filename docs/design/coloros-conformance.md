# ColorOS 17 conformance plan

Status: plan, not yet implemented (2026-11-26). Consolidates five read-only audits (shell, cards and data, lists
and controls, foundations, motion and feedback) against the ColorOS 17 references. Nothing in `tokens.md` or the
code has been changed by this document; section 4 is a diff against `tokens.md` for the implementer to apply.

Sources and notation:

- **[ref]** measured on the owner's ColorOS 17 dark-theme captures in `<project>/.data/design-refs/folded/`
  (git-ignored; outer screen 1140 x 2616 px, 1 dp = 2.3625 px, system font scale 1.35). File names are given so
  the numbers can be re-measured; the images are never copied into the repository.
- **RA §n**: `docs/design/reference-analysis.md` section n (measured numbers).
- **[Iris]** / **[COUI]**: numbers and behaviour read from the third-party Iris and COUI-Kit research sources
  (`<project>/.tooling/research/`, see `docs/research/design-coloros17.md`). Only numbers and patterns are taken;
  no code, resource or asset is copied (CLAUDE.md, ADR 0003).
- **[estimate]**: derived by eye or by conversion, to be re-measured. **[needs light references]**: no light
  capture exists; the value is a proposal from the kits. **[needs reference capture]**: no capture of that
  screen type exists (dialog, calendar, setup flow, slow back gesture, chart).
- **[decision]**: a deliberate deviation from the canon, with the reason (section 1.2).
- Priority: **P1** visible on almost every screen, **P2** noticeable, **P3** polish.
- Paths in tables are relative to `app/src/main/kotlin/io/github/alien2003/nightbaseline/`.

## 1. Principle

### 1.1 Statement

The whole UI follows ColorOS 17 as seen in OPPO's system apps (Settings, Clock, Compass, Files): **neutral,
hue-free greys on a true black window, one saturated blue accent, no tonal containers, convex round shell
buttons, a floating pill bar without a selection indicator, progressive blur instead of bar backgrounds, and
critically damped springs.** It is not Material You and not generic flat Material: no `primaryContainer` roles,
no tonal chips or avatars, no capsule indicators, no outlined segments or filter chips, no ripple, no M3 switch
or radio, no M3 type scale (20/600 titles, 600-weight labels).

The audits agree on the root causes of today's "Material" look:

1. a cool blue-violet tint on every neutral (B channel +3..+10);
2. an indigo `primary` / `primaryContainer` system plus a second violet `accent`;
3. tonal containers (status chips, glyph badges, nav capsule, period capsule, tonal button);
4. Material metrics (56 dp rows, 64 dp bar, 56 dp toolbar, 20/600 titles, 600-weight labels);
5. stock M3 `Switch` / `RadioButton`, platform stretch overscroll, and the Navigation-Compose default fade.

The structure is already right and stays: true black window, 20 dp cards, 16 dp gutters, floating glass shell.

### 1.2 Where the canon yields to our constraints

Calm-health tone (`principles.md`) and accessibility (4.5:1 text, 3:1 graphics, 48 dp targets, colour never
alone, 200 % font scale) win over the canon. Resolutions:

| Conflict | ColorOS canon | Resolution [decision] |
|---|---|---|
| Accent button label | #428AF7 fill + white label, 3.38:1 | Filled buttons with a text label use `primaryFill` #2A6BE0 (white 4.91:1). #428AF7 stays for controls, graphics and links (5.15:1 on card). |
| Light accent | #0080FF (3.8:1 on white) | #0068E0 (5.18 on white, 4.58 on #F0F1F2) for text, fills and graphics. |
| Disabled content | whole item #5F5F5F (2.73:1) | Disabled labels stay at `text.tertiary` (>= 4.5:1); only disabled control graphics (switch, radio) may use #5F5F5F. |
| Secondary text on raised fills | #969696 | Canon #969696 kept (5.88 on card, 4.59 on `surface.3` #2E2E2E). Rule: secondary text never on fills lighter than #2E2E2E (fails 4.4 on #313131, 4.27 on #333333). |
| Light secondary text | black @ 54 % = #757575 (4.07 on #F0F1F2) | #666666 (5.08 on #F0F1F2, 5.74 on white). |
| Nav label size | 10 sp, scaling capped | 11/14 sp (below our 12 sp minimum: recorded exception, the label is doubled by the icon and the content description). Font scaling is **not** capped; labels wrap to 2 lines and the bar grows. |
| Large title scaling | rendered ~33 dp at scale 1.35 (capped) | 32 sp, normal scaling; Android 14+ non-linear scaling already compresses it. |
| Switch off track | #505050, 2.16:1 on card | Kept; state is carried by the white knob position (non-colour carrier). Recorded as a canon exception. |
| Unchecked radio ring | #5F5F5F (2.73:1) | #767676 (3.83:1 on card) so the control boundary reaches 3:1. |
| Focus ring | none in ColorOS | Keep the 2 dp keyboard-only focus ring (`tokens.md` 10). |
| Touch targets | 40 dp round buttons, 36-40 dp chips and segments | Visual size follows the canon; every target is extended to 48 x 48 dp invisibly. |
| Nav selection without capsule | filled icon + bold label, both near-white | Accepted: selection is carried by outline -> filled icon shape and 400/500 -> 700 weight, not by colour. Reverses `tokens.md` 11 "Selection in bar". |
| Destructive dialog confirm | red fill (#FF6C61 / #DB382C) | No red in the app (`principles.md` 1): destructive confirm uses `primaryFill`; the wording carries the consequence. |
| Status colours | COUI GREEN #24B232 / YELLOW #E5A100 | Proposed (owner question Q1/Q2); every status keeps its glyph and label, no container. |
| Row height | 48 dp | Accepted: 48 dp equals `target.min`. |

## 2. Deviation table

Columns: **Area / component**, **Now**, **ColorOS target (value)**, **Evidence**, **P**, **Files**. Paths are
relative to the Kotlin package root unless they start with `docs/`, `app/` or `tools/`.

### 2.1 Foundations: colour

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| F1 | Dark neutral ramp | surface1 #1A1B1E, surface2 #232428, surface3 #2D2E33, separator #34353A, text #ECEDF0 / #A3A6AE / #8E919A, outline #8A8E98 (cool tint) | R = G = B: surface1 #1A1A1A, surface2 #242424, surface3 #2E2E2E, separator #484848 (1 px), text #E9E9E9 / #969696 / #8A8A8A, outline #767676, chevron #5F5F5F (decorative) | RA §6, §1.1; `settings-display.png`; Iris dark = white @ 10/90/54 % over black, divider white @ 20 % | P1 | `ui/theme/NbColors.kt`, `docs/design/tokens.md` 2.1, 11 |
| F2 | Accent / primary | indigo #8FB0FF with dark label #0B1C45 (light #2C5BD6); focus #A9C2FF | `primary` #428AF7 (controls, graphics); `primaryText` #5C9DFF (links, text buttons, 6.4:1); `primaryFill` #2A6BE0 + white label; focus #5C9DFF. Light #0068E0 for all [needs light references] | RA §5, §6 (Settings switch, radio, slider, selected label #428AF7); Iris `accentText`; COUI night blue | P1 | `ui/theme/NbColors.kt`, `ui/theme/Theme.kt`, `tokens.md` 2.2, 2.7 |
| F3 | Tonal containers | `primaryContainer` #25314F / #DCE5FB and `onPrimaryContainer` (nav capsule, period selector, tonal button, avatars) | none exist; selection = icon fill + weight, underline, neutral indicator | RA §4.1 (`alarmclock.png`: no capsule); Iris `navigation.md`; COUI has no container role | P1 | `NbColors.kt`, `Navigation.kt`, `Buttons.kt`, `Chart.kt`, `SettingsParts.kt`, `HistoryControls.kt` |
| F4 | Second accent | violet `accent` #B6A8FF / #6650C8, `accentGlow` | single blue accent; glow neutral white @ 13.5 % (bar) or #428AF7 @ 22 % (accent CTA) | Iris / COUI single `accent` | P2 | `NbColors.kt`, `Feedback.kt`, `onboarding/*` |
| F5 | Status containers | eight `*.container` / `*.onContainer` tokens, tonal pills | no tonal chips in any reference; status = glyph + coloured or grey text | `settings-display.png`, `alarmclock.png` (disabled card dims content) | P1 | `NbColors.kt`, `StatusGlyphs.kt`, `tokens.md` 2.3, 2.9 |
| F6 | Status hues | within #74CDB9, moved #E8B56E, neutral #A4A9B4, info #A9BDE6 | within #24B232 (6.21), moved #E5A100 (7.81), neutral #969696, info = `primaryText` #5C9DFF (owner Q1/Q2). Light: #1E7F2E / #9C6200 / #6B6B6B / #0068E0 [needs light references] | COUI functional GREEN / YELLOW | P2 | `NbColors.kt`, `tokens.md` 2.3 |
| F7 | Shell tints | bar #1C1D21 @ 78 %, solid bar #1C1D21 -> #24252A, sheet #1E1F23, tooltip #2A2B30, onGlass #F2F3F5 / #CDD0D6 | bar #1B1B1B @ 80 %, solid #1B1B1B -> #232323, sheet #171717, dialog #232323, tooltip #2B2B2B, onGlass #E9E9E9 / #C0C0C0 | RA §4.1 bar gradient; Iris `glass` 0xFF1B1B1B, `dialog` #232323, sheet #171717 | P1 | `NbColors.kt`, `ui/material/ShellMaterial.kt` |
| F8 | Light palette | bg #F1F2F5, surface2 #F6F7F9, surface3 #E9EBF0, separator #DCDEE3, text #16171A / #50535B / #62656D (cool) | bg #F0F1F2, card #FFFFFF, surface2 #F5F5F5, surface3 #E8E8EC, separator #E0E0E0, text #1A1A1A / #666666 / #6E6E6E [needs light references] | Iris / COUI light schemes | P1 once verified | `NbColors.kt`, `tokens.md` 2.1 |
| F9 | Range band | indigo fill #2B3550 + 1.5 dp edges #8794B8 + median tick, track `surface.2` | track 16 dp pill #333333; usual range a solid rounded segment #6B6B6B (3.27:1 vs card); no edges, no tick; light track #EBEBEB, fill #8C8C8C (3.36 on white) [needs light references] | RA §5 slider: track #333333, 20.3 dp | P1 | `RangeBand.kt`, `NbColors.kt`, `tokens.md` 2.4 |
| F10 | Scrim | light #0E1014 @ 32 % | dark #000000 @ 60 % (kept); light #000000 @ 40 % [needs light references] | Iris scrim #66000000; COUI dim 0.6 dark | P3 | `NbColors.kt` |

### 2.2 Foundations: type, spacing, shape, elevation

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| T1 | Weights | 600 on headline, titleSmall, label, labelSmall | only 400 / 500 / 700 (600 renders near-bold on the system sans) | RA §3 (medium row titles); COUI ItemTitle 16/500 | P1 | `ui/theme/NbTypography.kt` |
| T2 | Top-bar title | `type.title` 20/26/600 | new `type.topBarTitle` 18/24/700, 1 line, ellipsis | RA §2 em 18.7 dp #E3E3E3; Iris `topBarTitle` 18 sp / 708 | P1 | `NbTypography.kt`, `Shell.kt` |
| T3 | Large title | 28/34/700, lerped into the bar | 32/40/700 (800 where the font has it), in scrolling content | RA §3 Clock em 32.7 dp bold #E6E6E6; Iris `largeTitle` 32 | P1 | `NbTypography.kt`, `Shell.kt` |
| T4 | Row title / summary / value | 16/22/600; 14/20 #A3A6AE; 14 sp | `type.itemTitle` 16/22/500; `type.summary` 14/19/400 #969696 (13 sp canon, kept 14 for legibility [decision]); value 14/20/400 #969696 | RA §3 pitch 24.5 dp; COUI ItemTitle 16/500, ItemSummary 13/400, ItemValue 14/400 | P1 | `NbTypography.kt`, `Lists.kt` |
| T5 | Section header | `labelSmall` 12/16/600 `text.tertiary` | `type.sectionHeader` 12/16/500 #8A8A8A | RA §1, §3; COUI GroupTitle 12/500 | P1 | `NbTypography.kt`, `Lists.kt`, `home/HomeBlocks.kt` |
| T6 | Nav label | 12/16/600-700 | `type.navLabel` 11/14, 500 unselected / 700 selected (section 1.2) | RA §4.1 cap 8.0 dp; Iris `tabLabel` 10/500 | P1 | `NbTypography.kt`, `Navigation.kt` |
| T7 | Dialog / sheet title | `type.title` 20/600 left | `type.dialogTitle` 18/24/700, centred | COUI dialog title 18 sp, centred; Iris dialog 18/700 | P2 | `NbTypography.kt`, `Shell.kt` |
| T8 | Buttons, chips, segments | `label` 14/20/600 | buttons 16/22/500; chips and segments 14/20/500 | COUI dialog button 16/500; ChipTab 14/500; Iris 17/500 large button | P2 | `NbTypography.kt`, `Buttons.kt` |
| T9 | Subtitle under large title | `bodySmall` 14 sp in onGlass secondary | `type.subtitleSmall` 14/19/400 `text.tertiary` #8A8A8A | RA §3 Clock subtitle #8A8A8A, box 18.6 dp | P2 | `NbTypography.kt`, `Shell.kt` |
| S1 | Row metrics | min height 56 dp, vertical padding 12 dp | min 48 dp, vertical 10 dp, horizontal 16 dp, title->summary 2 dp | RA §1.1 48.3 dp rows, 10.2 dp pad; `settings-accessibility.xml` 48.3 / 74.1 dp rows; COUI 48 / 16 / 10 | P1 | `ui/theme/Dimensions.kt`, `Lists.kt` |
| S2 | Section header spacing | top 16 / bottom 8; card-to-card ~40 dp | top 24 (16 gap + 8), bottom 8, text start 32 dp from the screen edge; card-to-card 54 dp | RA §1 (54.2 dp, header row 38.1 dp, text x 32.6 dp) | P1 | `Lists.kt`, `Dimensions.kt` |
| S3 | Settings master list | every entry a separate card, ~6 dp gaps (HC segmented tiles) | one card per group, 1 px hairlines inset 16 dp both sides | RA §1.1, §1.2 contrast note; `settings-display.png` | P1 | `settings/SettingsSections.kt`, `SettingsParts.kt` |
| S4 | Card gaps | 12 dp everywhere | 12 dp between homogeneous items (tiles, history months); 16 dp between distinct cards and groups | RA §1 16.1 dp groups; RA §1.3 Clock 11.9 dp | P2 | `Dimensions.kt`, `home/HomeGrid.kt` |
| S5 | Divider | 1 dp (~2.6 px), start-inset only | `max(1 px, 0.33 dp)`, #484848, inset 16 dp start and end (start 16 + icon + 16 with a leading icon) | RA §1.1 1 px #484848 inset 16.5 dp; COUI divider | P1 | `Lists.kt`, `SettingsParts.kt` |
| R1 | Hero card radius | `shape.xl` 28 dp | one content radius 20 dp (`shape.l`); `shape.xl` only for the onboarding illustration | RA §1.2 19.3-20.4 dp; Iris card 20 | P2 | `ui/theme/Shapes.kt`, `Cards.kt` |
| R2 | Nested and small radii | banner / nested box / tooltip `shape.m` 12 dp | banner 20 dp (`shape.l`); nested boxes removed (hairline split); tooltip `shape.popup` 16 dp | Iris field 16, popup 23 | P2 | `Shapes.kt`, `Feedback.kt`, `Chart.kt` |
| R3 | Smoothing | 0.6 on all radii >= 16 dp; pills exact | cards 0.55; add smooth pill (r = h/2/1.08, smoothing 0.08) for bar, buttons | Iris shapes; RA §4.1 "fuller than a circle" | P3 | `Shapes.kt` |
| E1 | Light card shadow | y 1 / blur 3 / 6 % | none: white cards on #F0F1F2, separated by the background step | Iris / COUI light [needs light references] | P2 | `ui/theme/Elevation.kt`, `ShellMaterial.kt` |
| E2 | Shell shadow | dark y 4 / blur 16 / 50 % | y 4 / blur 10 / 35 %; round buttons y 3 / blur 8 / 20 %; accent CTA glow #428AF7 @ 35 %, y 4, blur 12 | RA §2, §4.1, §4.3 (~4-5 dp below); COUI button shadow 68/255 x 0.75 | P3 | `Elevation.kt` |

### 2.3 Shell

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| A1 | Bar selection | `SelectionCapsule` (`primaryContainer` pill) moving on `spring.selection` | no indicator; filled icon + 700 label (selected), outline icon + 500 label (unselected); 100 ms crossfade (`duration.micro`, `easing.standard`), snap under reduced motion | RA §4.1, `alarmclock.png` ("Alarm" selected, no capsule); Iris `navigation.md` | P1 | `ui/components/Navigation.kt` |
| A2 | Bar colours | selected `onPrimaryContainer` #D7E2FF, unselected #CDD0D6 | `nav.selected` #E9E9E9, `nav.unselected` #C0C0C0 (9.47:1 on bar); light #E6000000 / #B8000000 [needs light references] | RA §4.1, §6 | P1 | `Navigation.kt`, `ui/theme/ShellTokens.kt` (new) |
| A3 | Bar geometry | min height 64, inner padding 8, item min width 80, item padding 8 / 4, icon->label 2 | height 56 (minimum; grows with wrapping), items 70 x 48 dp, 8 dp gap, padding 12 dp horizontal / 4 dp vertical, item content: 4 dp top, 24 dp icon, 2 dp gap, label; no item horizontal padding | RA §4.1 bar 327.2 x 55.9, item 69.8 x 47.8, pitch 77.9, side 11.9, icon 24.1 at 3.8; Iris pill 56 | P1 | `Dimensions.kt`, `Navigation.kt` |
| A4 | Bar bottom offset | nav-bar inset + 12 dp (~36 dp above the edge on gesture nav) | gesture nav: bottom = max(16 dp, inset + 4 dp); 3-button: inset + 8 dp; `BarClearance` = 88 dp (+ inset when > 16 dp) | RA §4.1 bar edge 16.1 dp above the screen edge; Iris `bottomArea` 56 + 16 + 16 | P1 | `ui/shell/AppScaffold.kt`, `Dimensions.kt` |
| A5 | Bar fill, rim, shadow | rim 1 dp white 16 / 5 / 11 % + 0.5 dp black 45 % outer hairline; shadow 4 / 16 / 0.50 | fill #1B1B1B -> #232323; top rim white 13 % at 0.5 dp, bottom rim white 16 % at 1 dp (bottom brighter and thicker), no outer hairline; shadow 4 / 10 / 0.35. Light: #FFFFFF -> #F7F7F9, rim 0xCCFFFFFF / 0x14000000, shadow 4 / 12 / 0.10 [needs light references] | RA §4.1 (top rim #363636 1 px, bottom #3B3B3B-#3D3D3D 2-3 px); Iris `rimHighlight` / `rimShade` | P1 | `ui/material/ShellMaterial.kt`, `NbColors.kt` |
| A6 | Bar press | per-item state layer + accent glow | whole pill scales 0.96 (`spring.pressIn`/`pressOut`), neutral glow white @ 13.5 %, radius ~140 dp clipped to the pill; slide-to-select optional; focus ring kept | Iris `BottomNavPill` (content @ 13.5 %, 140 dp) | P3 | `Navigation.kt`, `ui/material/Interaction.kt` |
| B1 | Rail | 72 dp pill with capsule; `Large` 180 dp labelled panel (Material drawer) | 56 dp pill, same colours, icons and rim as the bar; items 48 x ~56 dp, 8 dp apart, 12 dp end padding, 4 dp side padding; offset 16 dp; `railArea` 88 dp; labels under icons in every size class [needs inner-screen references] | extrapolated from RA §4.1 | P1 | `Navigation.kt`, `Dimensions.kt` |
| C1 | Back / action buttons | bare 48 dp `NbIconButton`, chevron glyph | `ShellRoundButton`: 40 dp convex circle in a 48 dp target, margin 16 dp; fill #1F1F1F -> #2A2A2A, rim 0.5 dp #333333 top / 1 dp #3E3E3E bottom, shadow 3 / 8 / 0.20; glyph 20 dp, 1.5 dp stroke, #E9E9E9; back is an arrow, not a chevron. Light #FFFFFF -> #F2F2F4, rim 0xCCFFFFFF / 0x14000000, shadow 0.10 [needs light references] | RA §2, `settings-display.png` (40.2 dp, left 15.7 dp), `alarmclock.png` "more" (right 15.6 dp) | P1 | `ui/components/ShellRoundButton.kt` (new), `Shell.kt`, `NbIcons.kt` |
| C2 | Toolbar height | collapsed 56, medium 64, short 48 | 52 dp (collapsed = medium); short 48 kept; first content at status bar + 52 + 8 | RA §2 52.1 dp; Iris 52 | P1 | `Dimensions.kt`, `Shell.kt` |
| C3 | Collapsed title position | start 60 dp (with back) / 20 dp | 66 dp with back (16 + 40 + 10); 16 dp without | RA §2 title start 65.6 dp | P1 | `Shell.kt` |
| C4 | Large title mechanics | title inside the bar row (`topBarLarge` 112 dp), `TextStyle` lerp 28 -> 20 sp | toolbar row always 52 dp with only round buttons; large title is the first scrolling item at x 16 dp, subtitle below, 8 dp to the first card; small title fades in over scroll 40 -> 72 dp with (0.33, 0, 0.67, 1); no text size animation; delete `topBarLarge` | RA §1.3, §3 (`alarmclock.png`: cap top 99.9 dp, subtitle 137.1-155.8 dp, first card 175.7 dp); Iris `navigation.md`, `Scaffold.kt` | P1 | `Shell.kt`, `home/HomeScreen.kt`, `settings/ShellPage.kt` |
| C5 | Top-bar backing | opaque `solidTopBar` row behind the title once scrolled, 0.5 dp hairline | no background, no divider; progressive blur radius 12 dp (intensity 1 at top -> 0 at the toolbar bottom) + darkening gradient `background` @ 0.35 -> 0; fallback (API < 31) gradient `background` @ 0.85 -> 0 over status bar + toolbar + 16 dp; tint distance 10 dp | RA §2, §7 (sigma 0.8 -> 4.5 dp, darkening x0.65-0.75, `blur_overlay` 0-175.7 dp); COUI pre-33 fallback gradient | P1 | `Shell.kt`, `ShellMaterial.kt` |
| C6 | Bar subtitle colour | `onGlassSecondary` #CDD0D6 | `text.tertiary` #8A8A8A, 14 sp | RA §3 | P2 | `Shell.kt` |
| C7 | Bottom bar on sub-pages | visible on Settings > About etc. | top-level destinations only; detail routes pass `NavigationKind.None` on Compact; bar slides out (translateY 88 dp + alpha 0, 200 ms `easing.exit`), back with `spring.page`; rail stays on wide windows | `settings-display.png` (no bar); `alarmclock.png` | P2 | `nav/AppNavHost.kt`, `ui/shell/AppScaffold.kt`, `settings/ShellPage.kt` |
| D1 | Status-bar icons | `enableEdgeToEdge()` follows the system, not the in-app theme | `SystemBarStyle.dark/light(TRANSPARENT)` from the app's `isDark`; transparent 3-button nav bar with our own bottom gradient | `alarmclock.png` (icons always contrast) | P2 | `MainActivity.kt`, `ui/theme/Theme.kt` |
| G1 | Sheet motion | composed and removed instantly; handle not draggable | enter `spring(1.0, 250)` slide from below; programmatic hide `spring(1.0, 440)`; drag follows the finger, upward rubber band to 72 dp; release dismisses at > 50 % off screen or a downward fling, else returns `spring(1.0, 247)`; scrim alpha follows the offset | COUI `CouiSheetMotion` (show 632 -> 195, hide 1218 -> 288); Iris `BottomSheet.kt` | P1 | `Shell.kt`, `ui/motion/Motion.kt` |
| G2 | Sheet header | 32 x 4 dp handle in a 48 dp box; left 20 sp title + trailing X; padding 20 dp | handle 36 x 4 dp, 10 dp top / 6 dp bottom, white @ 30 % dark (light black @ 26 % [needs light references]), 48 dp drag target; 56 dp header row, title centred 18/24/700; close = `ShellRoundButton` at 16 dp from the trailing edge [estimate]; horizontal padding 16 dp | Iris `BottomSheet.kt`, `popups.md` | P1 | `Shell.kt` |
| G3 | Sheet material | blur 40, tint #1E1F23 @ 88 %, rim + black hairline, overlay shadow 12 / 32 / 0.60 | fill #171717 @ 92 % (solid #171717); cards on it `surface.elevated` #2B2B2B (light #FFFFFF on #F5F5F8); 32 dp top corners; 0.5 dp top highlight white 10 % only; shadow 0 / 24 / 0.30; max height = window - status bar - 24 dp | Iris sheet #171717, surfaceElevated #2B2B2B, sigma ~22 dp | P2 | `Shell.kt`, `ShellMaterial.kt`, `NbColors.kt` |
| H1 | Dialog layout | centred, 32 dp radius, left 20 sp title, end-aligned text button + filled pill | Compact: bottom-docked, 16 dp side margins, bottom 16 dp + nav inset; Medium+: centred; max width 392 dp; padding 24 dp; title 18/24/700 centred; message 14/20 `text.secondary` centred; title->message 6 dp, message->buttons 22 dp; two equal `weight(1f)` 44 dp pills (48 dp target), 8 dp gap, 16 dp inset and bottom; dismiss = neutral `SecondaryButton`, confirm = `primaryFill` (also when destructive, section 1.2) [needs reference capture] | COUI `CouiDialogDefaults` (16 / 32 margins, 392, 24, 44 dp buttons); Iris `Dialog.kt` | P1 | `Shell.kt`, `settings/SettingsScreen.kt` |
| H2 | Dialog shape and motion | 32 dp, glass 32 dp blur + rim; no animation | radius 30 dp smoothing 0.6 (COUI 45 dp alternative, settle with a capture); fill #232323 @ 94 % (solid #232323), no outer hairline; bottom-docked enter: slide from +100 % own height + fade, `tween(250, flow)`; centred enter: scale 0.8 -> 1 + fade, `tween(250, flow)`; exit fade (+ 24 dp slide when docked) `tween(150, exit)`; scrim fades with it | Iris dialog 30 dp, #232323; COUI 250 ms (0.3, 0, 0.1, 1) / 150 ms (0.3, 0, 1, 1) | P2 | `Shell.kt`, `Motion.kt` |
| P1 | Page transitions | none declared: Navigation-Compose default fade (700 ms tween) everywhere | push: incoming from +100 % width, outgoing to -1/3 width with a black dim 0 -> 0.3; pop mirrored (incoming from -1/3, dim 0.3 -> 0, outgoing to +100 %); all `spring(1.0, 220)`; no scale, no fade; RTL mirrored; top-level tab switch crossfade `tween(150, standard)`; reduced motion `tween(120)` crossfade | RA §8.1, `frames-back/f000057-f000092` (exit ~200 ms, entry -124 px @ 100 ms, -24 px @ 215 ms, settle ~470 ms, no overshoot); dim seen by eye on `f000062` vs `f000092` [estimate, to be measured] | P1 | `nav/AppNavHost.kt`, `Motion.kt` |
| P2 | In-page sub-navigation | Settings sub-pages and onboarding steps swap instantly | same push / pop spec via `AnimatedContent`, direction from page depth (Main 0, sub-page 1, Licenses 2) | `settings-back.mp4` (this exact path) | P1 | `settings/SettingsScreen.kt`, `onboarding/OnboardingScreen.kt` |
| P3 | Predictive back | scale 0.92 + corners to `shape.xl`, nav seekable fade | drive the push / pop above from back progress; release `spring(1.0, 220)`, cancel `spring(1.0, 1500)`; commit threshold 45 dp; keep scale 0.92 until a slow-gesture capture exists [needs reference capture] | Iris `navigation.md`; COUI back motion | P3 | `nav/AppNavHost.kt` |

### 2.4 Lists and controls

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| L1 | Switch | M3 `Switch` 52 x 32, thumb 16-28 dp, outline when off, dark knob when on | `NbSwitch`: 44 x 24 dp pill, 18 dp white knob in every state, 3 dp inset, no outline; on `primary` #428AF7 (light #0068E0), off #505050 (light #D6D6D6 [needs light references]); disabled track alpha 0.3; knob `spring(0.7, 247)` with stretch to x1.3 at 34.7 % of travel; draggable, snaps to nearer end; whole row is the target | RA §5, `settings-display.png`, `settings-accessibility.xml` (44.0 x 24.1 dp); COUI switch | P1 | `ui/components/Controls.kt` (new), `Lists.kt` |
| L2 | Radio | M3 `RadioButton`, leading | `NbRadio`: 24 dp box; unchecked 20 dp ring 1.3 dp #767676 (section 1.2; light #8F8F8F); checked filled `primary` disc 20.6 dp with an 11.8 dp white dot; 300 ms fill scale with `easing.flow` + 1.08 pulse; always **trailing**, vertically centred | RA §5, `settings-display.png`; COUI single-choice list (radio trailing) | P1 | `Controls.kt`, `Lists.kt`, `settings/SettingsParts.kt` |
| L3 | Chevron | 20 dp, 2 dp stroke, `text.secondary` | 12 x 24 dp box, ~5 x 10 dp glyph, 1.3 dp stroke, #5F5F5F (light #BDBDBD), decorative | RA §1.1 (11.9 x 24.1 dp, #5F5F5F) | P3 | `NbIcons.kt`, `Lists.kt` |
| L4 | Primary button | pastel #8FB0FF + navy label (dark), 48 dp, padding 20 / 12, 14/600; disabled `surface.3` | 50 dp min height, padding 28 / 12, label 16/22/500 white on `primaryFill` #2A6BE0 (light #0068E0); optional +6 % lightness bottom gradient and accent glow (dark); disabled = same fill and label at alpha 0.3 | Iris large button (50 dp, 28 / 12, 17 sp); RA §4.3 FAB gradient + glow | P1 | `ui/components/Buttons.kt` |
| L5 | Tonal / text buttons | `TonalButton` on `primaryContainer`; text button 14/600 #8FB0FF | `SecondaryButton`: neutral fill white @ 10 % (light black @ 6 %), label `text.primary` 16/500, 50 dp; text button 16/22/500 `primaryText`, padding 12 / 8, 48 dp target | COUI dialog secondary pill; Iris text button | P2 | `Buttons.kt` |
| L6 | History List/Calendar toggle | segmented pill with `primaryContainer` capsule | `NbTabRow` under the top bar: 48 dp row, centred group, tabs = label + 12 dp each side; labels 14/20, selected 500 `text.primary`, unselected 400 #8A8A8A; indicator 2 dp x label width, square ends, `text.primary` (not accent), animated `spring.selection` | `settings-accessibility.png` + `.xml` measured: row 47.8 dp, tab pad 12.7 dp, underline 2.1 dp x 55.0 dp #E6E6E6, unselected #8A8A8A | P2 | `Controls.kt`, `history/HistoryControls.kt` |
| L7 | Filter chips | M3 FilterChip look: 36 dp, 1 dp outline, check icon, `primaryContainer` | COUI ChipTab: 36 dp visual (48 dp target), padding 16 dp, no outline, no check; unselected #262626 (light #E6E7E8), selected #505050 + 1 dp white @ 15 % rim, label 700 (light #FFFFFF) [needs light references]; label 14/20/500 | COUI `CouiChipTabDefaults` | P2 | `history/HistoryControls.kt` |
| L8 | Sensitivity presets | three cards, selected has a 2 dp accent outline, leading radio, weight flips 700/400 | one `SettingsGroup` with three rows: 16/500 title, summary, trailing `NbRadio`, inset hairlines; no outline, no weight change | RA §1 pattern | P2 | `settings/SettingsParts.kt` |
| L9 | Theme choice | label + leading-radio rows | `NbChoiceRow`: three columns, 24 dp own line icon, 14 sp label, `NbRadio` below, 1 px vertical dividers; selected label `primary` 500; falls back to trailing-radio rows when labels do not fit at 200 % | RA §1.1 Display row 129.1 dp, selected label #428AF7, `settings-display.png` | P2 | `Controls.kt`, `settings/SettingsSections.kt` |
| L10 | Source picker | 40 dp letter avatar on `primaryContainer`, weight flips | 40 dp app icon from PackageManager at runtime (nothing bundled); fallback letter on neutral #333333 (light #EDEDED); title 500 always; trailing `NbRadio`; divider start inset 72 dp | COUI single choice | P2 | `settings/SettingsParts.kt` |
| L11 | History feed | one card per night (~8 dp gaps), month header 16 sp | one 20 dp card per month under a section header (T5); rows 48 dp min, 10 dp vertical, 24 dp leading glyph + 16 dp; dividers start 56 dp / end 16 dp; selected row neutral fill `surface.3` (light black @ 6 %) | RA §1 Settings pattern | P2 | `Lists.kt`, `history/HistoryScreen.kt` |
| L12 | History strip on Home | title + "See all" inside the card; seven 20 dp filled check circles | header outside the card (T5) with "See all" `primaryText` 13/500 at the end (48 dp target); weekday letters 12/400 #969696; within 8 dp dot, moved 12 dp rounded-square-with-triangle, missing 10 dp dash; padding 16 / 10 | COUI `Group` header actions | P2 | `Lists.kt` |
| L13 | Calendar cells | filled `surface.1` tiles, 8 dp, today 1.5 dp outline | grid inside one 20 dp card; transparent 48 dp cells; day 16/20/400; selected 36 dp `primary` circle with white 500 number; today number `primary` 500; month arrows in 40 dp round buttons [needs reference capture] | hypothesis from ColorOS Calendar | P2 | `Lists.kt`, `history/HistoryCalendar.kt` |
| L14 | Onboarding | purple step dots; Back text button + tonal + primary pill bottom row (Material wizard); 28 sp left title | back = `ShellRoundButton` in the top bar; bottom column: full-width `PrimaryButton` (max 360 dp, 50 dp), 8 dp, centred `NbTextButton`, 24 dp above the nav inset; dots 6 dp, current 16 x 6 dp `primary`, inactive #505050 (light #D6D6D6) [needs reference capture] | COUI large-button pattern (hypothesis) | P3 | `onboarding/OnboardingContent.kt`, `onboarding/OnboardingScreen.kt` |

### 2.5 Cards and data

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| K1 | Status chip | 28 dp tonal pill (#143029 / #3A2A14), 16 dp glyph, 14/600 | `StatusLabel`: no background, 6 dp gap; within 8 dp dot + "Usual" 13/400 #969696; higher/lower 10 dp triangle + label in `status.moved` 14/500; learning/missing 12 dp dotted ring or dash + 13/400 #969696 | no tonal chips in any reference; Clock disabled card dims content (RA §1.3) | P1 | `StatusGlyphs.kt`, `Cards.kt`, `metric/MetricBlocks.kt` |
| K2 | Section headings on Home | `BlockHeading` = `type.title` 20/600 white ("Already visible", "This period", ...) | `SectionHeader` style (T5, S2): 12/16/500 #8A8A8A, start 32 dp; intro sentence moves into the card or a footer (12 sp `text.tertiary`, 32 / 8 dp) | RA §1, §3; COUI GroupTitle, Footer | P1 | `home/HomeBlocks.kt` |
| K3 | Period selector 7/30/90 | `surface.3` track, 2 dp inset, `primaryContainer` capsule, 14/600-700 | COUI segmented: 40 dp visual (48 dp target), 4 dp inset, segments >= 52 dp, label padding 12 dp; track white @ 12 % (#1F1F1F on black, light black @ 6 %), indicator white @ 20 % (#333333; light #FFFFFF @ 70 % [needs light references]); labels 14/500, selected #E9E9E9, unselected #969696; `spring.selection` 1.0/247, optional height squash 0.94 -> 1 `spring(0.5, 110)`, segment tick haptic on drag | COUI `CouiSegmentedControlDefaults`, `CouiSegmentMotion` | P1 | `Chart.kt` |
| K4 | Night summary card | `shape.xl` 28 dp, 20 dp padding, 40 dp glyph in a 56 dp tonal circle | `shape.l` 20 dp, 16 dp padding; 28 dp glyph in the status colour directly on the card; night label 13/400 #969696; headline 22/28/500; body 14/20/400 #969696; action link `primaryText` | RA §1.2 one card radius, RA line 63 padding 16.5 | P2 | `Cards.kt`, `StatusGlyphs.kt` |
| K5 | Metric tile | name 16/600; selected (list-detail) 2 dp primary outline | name 16/500; range line 13/400 #969696; selected = white @ 10 % overlay (#313131; then no secondary text on it, use `text.primary`) or neutral fill, no outline | Iris `anchorHighlight` white @ 15 % | P2 | `Cards.kt` |
| K6 | Value marker | within marker in `status.within` | within: 10 dp white dot with a 2 dp halo in the card colour (slider-thumb idiom); moved: 10 dp triangle `status.moved` (5.67:1 on the track); learning: dotted #6B6B6B outline (owner Q1) | RA §5 slider thumb | P1 | `RangeBand.kt` |
| K7 | Chart | indigo band with edges, grid #2C2D32 1 dp, points #B9BDC6, tooltip #2A2B30 + outline, 12 dp | grid 1 px white @ 8 % (#2E2E2E), horizontal only; axis 12/400 tnum #969696; band white @ 12 % (#363636), no edges; points within #E9E9E9 r 3 dp, moved triangles, learning hollow #969696; selected 1.4x + 1 dp guide white @ 30 %; tooltip #2B2B2B, no outline, `shape.popup` 16 dp, shadow 4 / 16 / 0.50, date 12 #969696, value 16/500, `StatusLabel`. Light grid black @ 6 %, tooltip #FFFFFF shadow 12 % [needs light references]; axis side [needs reference capture] | no chart in the references; COUI / Iris scheme | P2 | `Chart.kt` |
| K8 | Learning progress | 7 x 10 dp segments in violet, pending outlined | 8 dp segments, 4 dp gap; counted `primary`; pending filled white @ 20 % (#484848 on card); skipped half fill; label 13/400 #969696; "Now" marker plain `primary` text 12/500, no chip | Iris `IrisProgressBar` (8 dp, trackEmpty white @ 20 %) | P2 | `Feedback.kt`, `learning/LearningBlocks.kt` |
| K9 | Banner / state card | banner `shape.m` 12 dp; state card 48 dp tonal info circle, headline 22/600 | banner `shape.l` 20 dp, padding 16 / 12, 24 dp outline icon `primaryText`, text 14/400 `text.primary`, action `primaryText` 14/500; state card 24 dp icon without container, title 20/26/500, body 14/400 #969696 | single content radius; no tonal icon containers | P2 | `Feedback.kt` |
| K10 | Stats row | `StatBox` chips on `surface.2`, `shape.m` | one 20 dp card with 48 dp rows: label 14/400 #969696 left, value 15/500 tnum #E9E9E9 end-aligned, 1 px #484848 dividers inset 16 dp | `compass2.png` + `.xml` readout: label #6E6E6E, value #E7E7E7, row pitch 74.9 dp at 1.35 | P3 | `Chart.kt` |
| K11 | Nested boxes, explainers | `NestedBox` on `surface.2` + 12 dp; explainer titles 20-22/600 | no tinted boxes inside cards: hairline split + 12 dp spacing; titles 16/500, body 14/20/400 #969696 | COUI has no inner boxes | P3 | `Feedback.kt`, `metric/*`, `night/*` |
| K12 | Disclaimer footer | centred 12 sp + link | start-aligned COUI Footer: 32 / 8 dp padding, 12 sp #8A8A8A, link `primaryText` | COUI Footer | P3 | `Cards.kt` |
| K13 | Text links | #8FB0FF / #2C5BD6 | `primaryText` #5C9DFF (dark, 6.4:1) / #0068E0 (light) | Iris `accentText` | P2 | via `NbColors.kt` |

### 2.6 Motion and feedback

| # | Component | Now | ColorOS target | Evidence | P | Files |
|---|---|---|---|---|---|---|
| M1 | Overscroll | platform stretch (no `LocalOverscrollFactory`) | `NbOverscroll`: translate, no distortion; follow 24 % (slow) to 34 % (fast) of the pull, holds while the finger rests; return 700 ms (0.2, 0.62, 0.17, 1.05); fling throw = leftover velocity x 0.047 s, max 107 dp, rise 60 ms FastOutSlowIn; a finger stops a running return; reduced motion: none; sheets never translate upward past their top | RA §8.2, `settings-display-scroll.mp4`, `frames-scroll`, `crop-scroll-top*` (>= 37 dp travel, ~0.7 s return); Iris `Overscroll.kt` | P1 | `ui/motion/NbOverscroll.kt` (new), `ui/theme/Theme.kt` |
| M2 | Press feedback | fixed scale 0.97, one spring 0.6/1200, `text.primary` @ 12 % layer drawn **over** content (tints labels), `tween(100)` | size-dependent scale `0.85 + 0.12 f(area 20^2..150^2 dp^2)` (aspect > 2 averaged with `0.85 + 0.15 f((aspect-2)/13)`): 40 dp 0.889, 56 dp 0.910, tile 0.946, full-width pill 0.965; press-in spring 1.0/632 (compact) or 1.0/439 (wide); release 0.4/158 (compact) or 0.5/158 (wide); highlight **under** content, clipped to the shape: dark white @ 12.5 %, on accent fills white @ 20 % [estimate], light black @ 6 % [needs light references]; rows in 1.0/439, out 1.0/895; buttons in 1.0/1755, out 1.0/187 | COUI `CouiPressMotion`; Iris `TouchMotion` | P2 | `ui/material/Interaction.kt`, `Motion.kt` |
| M3 | Rows shrink on press | history row, calendar cell, home pane rows, history controls scale | rows inside a grouped card only light up (`pressScale = false`); standalone tiles and cards keep the M2 scale | Iris touch-and-drag; COUI rows | P2 | `Lists.kt`, `home/HomePanes.kt`, `history/HistoryControls.kt` |
| M4 | Haptics | none implemented | public constants only (ADR 0003, no vendor codes): switch `TOGGLE_ON`/`TOGGLE_OFF` (API 34+, else `CLOCK_TICK`); segment drag crossing `SEGMENT_TICK` (else `CLOCK_TICK`); chart scrub point `CLOCK_TICK` (`SEGMENT_FREQUENT_TICK` on 34+); long press `LONG_PRESS`; nothing for taps, tabs, rows, nav, overscroll edge; via the View so the system setting applies | Iris docs; COUI `SegmentedControl` | P2 | `Controls.kt`, `Chart.kt`, `Motion.kt` |
| M5 | Default indication | `MaterialTheme` provides `ripple()`; every call site opts out manually | `LocalIndication provides NbPressIndication` (M2 highlight), `LocalRippleConfiguration provides null`; grep / detekt rule banning `ripple(` | COUI / Iris press overlay, no ripple | P2 | `ui/theme/Theme.kt`, `Interaction.kt`, `tools/` |
| M6 | Touch glow | `accentGlow` violet, 48 dp, 110 / 480 ms | white @ 13.5 % (dark), radius ~140 dp capped by the element, same 110 / 480 ms; light white @ 30 % over light glass [needs light references]; rubber-band follow on glass icon buttons only (max 28 dp, ratio 0.05, release `spring(0.35, 210)`) | Iris `BottomNavPill`, touch-and-drag | P3 | `Interaction.kt` |
| M7 | Fling | Compose spline decay | unchanged; revisit with a side-by-side recording after M1 [hypothesis] | COUI SpringOverScroller | P3 | none |

## 3. Material3 components to replace or restyle

### 3.1 M3 classes in use

| File | M3 API | Action | P | Package |
|---|---|---|---|---|
| `ui/components/Lists.kt` (imports, switch row) | `Switch`, `SwitchDefaults` | replace with `NbSwitch` (L1) | P1 | WP3 |
| `ui/components/Lists.kt` (radio row) | `RadioButton` | replace with trailing `NbRadio` (L2) | P1 | WP3 |
| `ui/settings/SettingsParts.kt` (source picker, presets) | `RadioButton`, `RadioButtonDefaults` | replace with `NbRadio` (L2, L8, L10) | P1 | WP3 |
| `ui/theme/Theme.kt` | `MaterialTheme`, `darkColorScheme`, `lightColorScheme`, implicit `ripple()` and `MotionScheme` | provide `LocalContentColor`, `LocalTextStyle`, `LocalIndication` ourselves; reduce to a neutral minimal scheme now, remove once no M3 widget remains | P2 | WP1 (final removal in the sweep) |
| ~26-28 files (components, home, metric, night, history, learning, onboarding, settings, `nav/AppNavHost.kt`, `PermissionsRationaleActivity.kt`) | `material3.Text` | `NbText` over `BasicText` (no visual change once `Theme.kt` provides locals) | P3 | WP1 creates, sweep swaps |
| ~10 files (`Buttons`, `Feedback`, `Lists`, `Navigation`, `StatusGlyphs`, `HistoryControls`, `LearningBlocks`, `MetricBlocks`, `OnboardingContent`, `SettingsParts`) | `material3.Icon` | `NbIcon` (`Image` + `ColorFilter`) | P3 | WP1 creates, sweep swaps |

### 3.2 Material patterns built by hand

| Pattern | Where | Replace with | Ref |
|---|---|---|---|
| Moving capsule nav indicator | `Navigation.kt` `SelectionCapsule` (bar and rail) | icon fill + weight | A1, B1 |
| `primaryContainer` / `onPrimaryContainer` roles | bar, rail, `Chart.kt` period selector, `Buttons.kt` `TonalButton`, `SettingsParts.kt` avatar, `HistoryControls.kt` | neutral fills, `text.primary`, accent only on controls | F3 |
| Tonal status chip, tonal glyph badge, tonal info circle | `StatusGlyphs.kt` `StatusChip`, `NightGlyphBadge`; `Feedback.kt` `StateCard` | `StatusLabel`, bare glyphs | K1, K4, K9 |
| Segmented button with tonal thumb | `Chart.kt` `PeriodSelector`, `HistoryControls.kt` `HistoryViewToggle` | COUI segmented control; `NbTabRow` | K3, L6 |
| FilterChip (outline + check) | `HistoryControls.kt` filter chips | ChipTab | L7 |
| Outlined progress segments | `Feedback.kt` learning progress | filled track segments | K8 |
| Assist-chip stat boxes, nested tinted boxes | `Chart.kt` `StatBox`, `Feedback.kt` `NestedBox` | readout rows, hairline split | K10, K11 |
| Segmented settings tiles, outlined selected cards | `settings/*`, `PresetCard` | one grouped card, trailing radio | S3, L8 |
| AlertDialog (left title, end-aligned text + filled buttons) | `Shell.kt` `GlassDialog` | bottom-docked COUI dialog | H1 |
| Left title + X sheet header | `Shell.kt` `GlassSheet` | centred title, round close | G2 |
| Bare 48 dp icon back button, chevron glyph | `Shell.kt`, `NbIcons.kt` | `ShellRoundButton`, arrow | C1 |
| M3 type scale (20/600 titles, 600 labels) | `NbTypography.kt` | section 4.3 | T1-T8 |
| Disabled button on grey `surface.3` | `Buttons.kt` | fill at alpha 0.3 | L4 |
| Platform stretch overscroll, ripple default, Navigation default fade | `Theme.kt`, `AppNavHost.kt` | M1, M5, P1 | |

## 4. Token changes (diff against `docs/design/tokens.md`)

`-` = current value, `+` = proposed. Light values are all [needs light references] unless noted. `ContrastTest`,
`tools/design/contrast_check.py` pairs (`tokens.md` 2.9) and `ColorTokensTest` (referenced in `NbColors.kt`; create it if missing) change together with these.

### 4.1 Colours (`tokens.md` 2.1-2.8)

| token | light (- / +) | dark (- / +) | note |
|---|---|---|---|
| `color.background` | #F1F2F5 / **#F0F1F2** | #000000 / #000000 | |
| `color.surface.1` | #FFFFFF / #FFFFFF | #1A1B1E / **#1A1A1A** | |
| `color.surface.2` | #F6F7F9 / **#F5F5F5** | #232428 / **#242424** | |
| `color.surface.3` | #E9EBF0 / **#E8E8EC** | #2D2E33 / **#2E2E2E** | highest fill that may carry `text.secondary` |
| + `color.surface.elevated` | — / **#FFFFFF** | — / **#2B2B2B** | cards inside sheets, tooltip |
| `color.text.primary` | #16171A / **#1A1A1A** | #ECEDF0 / **#E9E9E9** | 14.3 on card |
| `color.text.secondary` | #50535B / **#666666** | #A3A6AE / **#969696** | 5.88 / 4.59 on `surface.3` |
| `color.text.tertiary` | #62656D / **#6E6E6E** | #8E919A / **#8A8A8A** | only on `background` / `surface.1` (light 4.51) |
| `color.separator` | #DCDEE3 / **#E0E0E0** | #34353A / **#484848** | drawn 1 px |
| `color.outline` | #747883 / **#767676** | #8A8E98 / **#767676** | essential boundaries, unchecked radio |
| + `color.chevron` | — / **#BDBDBD** | — / **#5F5F5F** | decorative only |
| + `color.control.track` | — / **#EBEBEB** | — / **#333333** | slider / range band / switch-off base |
| + `color.control.switchOff` | — / **#D6D6D6** | — / **#505050** | |
| `color.primary` | #2C5BD6 / **#0068E0** | #8FB0FF / **#428AF7** | controls, graphics; dark also links |
| + `color.primaryText` | — / **#0068E0** | — / **#5C9DFF** | links, text buttons, info status |
| + `color.primaryFill` | — / **#0068E0** | — / **#2A6BE0** | filled buttons with a text label (white 5.18 / 4.91) |
| `color.onPrimary` | #FFFFFF / #FFFFFF | #0B1C45 / **#FFFFFF** | |
| `color.primaryContainer` | #DCE5FB / **removed** | #25314F / **removed** | |
| `color.onPrimaryContainer` | #12306F / **removed** | #D7E2FF / **removed** | |
| `color.accent` | #6650C8 / **removed (= primary)** | #B6A8FF / **removed** | |
| `color.accentGlow` | #6650C8 @ 18 % / **#0068E0 @ 16 %** | #B6A8FF @ 22 % / **#428AF7 @ 22 %** | accent CTA only; shell glow below |
| + `color.glow.neutral` | — / **#FFFFFF @ 30 %** | — / **#FFFFFF @ 13.5 %** | touch light on glass |
| `color.status.within` | #1E7566 / **#1E7F2E** | #74CDB9 / **#24B232** | owner Q1 |
| `color.status.moved` | #95560F / **#9C6200** | #E8B56E / **#E5A100** | owner Q2 |
| `color.status.neutral` | #5B606B / **#6B6B6B** | #A4A9B4 / **#969696** | |
| `color.status.info` | #3E5C96 / **= primaryText** | #A9BDE6 / **= primaryText** | |
| `color.status.*.container`, `*.onContainer` (8 tokens) | **removed** | **removed** | and their 2.9 pairs |
| `color.band.fill` | #DCE4F5 / **#8C8C8C** | #2B3550 / **#6B6B6B** | solid usual-range segment; boundary = fill vs card (3.36 / 3.27) |
| `color.band.edge` | #6F7FA6 / **removed** | #8794B8 / **removed** | no edge lines, no median tick |
| `color.marker.within` (new) | — / **#FFFFFF + #CCCCCC 1 px rim** | — / **#FFFFFF** | with 2 dp `marker.halo` |
| `color.chart.point` | #4A4F5A / **#4A4A4A** | #B9BDC6 / **#E9E9E9** | |
| `color.chart.grid` | #E6E8EC / **#000000 @ 6 %** | #2C2D32 / **#FFFFFF @ 8 %** | 1 px |
| + `color.chart.bandArea` | — / **#000000 @ 8 %** | — / **#FFFFFF @ 12 %** | chart stepped area |
| `color.glass.bar` | #F7F8FA @ 80 % / **#FFFFFF @ 80 %** | #1C1D21 @ 78 % / **#1B1B1B @ 80 %** | |
| `color.glass.topBar` | #F1F2F5 @ 82 % / **`background` gradient 0.35 -> 0** | #0E0E10 @ 74 % / **`background` gradient 0.35 -> 0** | darkening, not a backing |
| `color.glass.sheet` | #F7F8FA @ 90 % / **#F5F5F8 @ 92 %** | #1E1F23 @ 88 % / **#171717 @ 92 %** | |
| `color.glass.dialog` | = sheet / **#FFFFFF @ 94 %** | = sheet / **#232323 @ 94 %** | |
| `color.glass.rimTop` | #FFFFFF @ 90 % / **#FFFFFF @ 80 %** | #FFFFFF @ 16 % / **#FFFFFF @ 13 %, 0.5 dp** | |
| `color.glass.rimMid` | #FFFFFF @ 40 % / **removed** | #FFFFFF @ 5 % / **removed** | |
| `color.glass.rimBottom` | #FFFFFF @ 60 % / **#000000 @ 8 %** | #FFFFFF @ 11 % / **#FFFFFF @ 16 %, 1 dp** | bottom brighter and thicker (dark) |
| `color.glass.rimOuter` | #000000 @ 7 % / **removed on bar, rail, sheet, dialog** | #000000 @ 45 % / **removed** | |
| `color.onGlass.primary` | #111215 / **#1A1A1A** | #F2F3F5 / **#E9E9E9** | also `nav.selected` |
| `color.onGlass.secondary` | #3E4047 / **#474747** | #CDD0D6 / **#C0C0C0** | also `nav.unselected` (9.47 on bar) |
| + `color.control.convexTop` / `convexBottom` | — / **#FFFFFF / #F2F2F4** | — / **#1F1F1F / #2A2A2A** | `ShellRoundButton` |
| + `color.control.convexRimTop` / `RimBottom` | — / **#FFFFFF @ 80 % / #000000 @ 8 %** | — / **#333333 / #3E3E3E** | |
| `color.solid.barTop` / `barBottom` | #FCFCFD / #F2F3F6 -> **#FFFFFF / #F7F7F9** | #1C1D21 / #24252A -> **#1B1B1B / #232323** | |
| `color.solid.topBar` | = background (+ hairline) / **gradient `background` 0.85 -> 0, no hairline** | same | |
| `color.solid.sheet` | #FFFFFF / **#F5F5F8** | #1E1F23 / **#171717** | |
| `color.solid.dialog` | = sheet / **#FFFFFF** | = sheet / **#232323** | |
| `color.solid.tooltip` | #FFFFFF / #FFFFFF | #2A2B30 / **#2B2B2B** | = `surface.elevated` |
| `color.scrim` | #0E1014 @ 32 % / **#000000 @ 40 %** | #000000 @ 60 % / unchanged | |
| + `color.sheetHandle` | — / **#000000 @ 26 %** | — / **#FFFFFF @ 30 %** | |
| `color.focus.ring` | #1F4FD1 / **#0068E0** | #A9C2FF / **#5C9DFF** | |
| `state.pressed` | `text.primary` @ 12 % over content / **#000000 @ 6 % under content** | same / **#FFFFFF @ 12.5 % under content** | M2 |
| `state.selected` (new) | — / **#000000 @ 6 %** | — / **`surface.3`** | selected rows, tiles |

Remove from 2.1 the sentence "with a faint cool tint of our own"; replace with "neutral, R = G = B, as measured".

### 4.2 Typography (`tokens.md` 3)

| name | - | + |
|---|---|---|
| `type.largeTitle` | 28 / 34 / 700, collapses into the bar | **32 / 40 / 700 (800 if available), scrolls with content** |
| `type.headline` | 22 / 28 / 600 | **22 / 28 / 500** |
| `type.title` | 20 / 26 / 600 | **20 / 26 / 500** (pane and state-card titles only) |
| + `type.topBarTitle` | — | **18 / 24 / 700, 1 line** |
| + `type.dialogTitle` | — | **18 / 24 / 700, centred** (sheets and dialogs) |
| `type.titleSmall` -> `type.itemTitle` | 16 / 22 / 600 | **16 / 22 / 500** |
| `type.bodySmall` -> `type.summary` | 14 / 20 / 400 | **14 / 19 / 400** (13 sp canon, [decision] 14) |
| `type.label` | 14 / 20 / 600 | **14 / 20 / 500** (chips, segments, status) |
| + `type.button` | — | **16 / 22 / 500** |
| `type.labelSmall` | 12 / 16 / 600 | **12 / 16 / 500** (weekday letters) |
| + `type.sectionHeader` | — | **12 / 16 / 500, `text.tertiary`** |
| + `type.navLabel` | (was `labelSmall`) | **11 / 14, 500 unselected / 700 selected; exception to the 12 sp minimum** |
| + `type.subtitleSmall` | — | **14 / 19 / 400, `text.tertiary`** |
| + `type.statusLabel` | — | **13 / 18 / 400 (within, learning) / 14 / 20 / 500 (moved)** |
| `type.numeric.*`, `type.body`, `type.caption`, `type.unit` | | unchanged |

Rule added to section 3: weights 400 / 500 / 700 only.

### 4.3 Spacing and sizes (`tokens.md` 4, `Dimensions.kt`)

| name | - | + |
|---|---|---|
| `row.paddingVertical` / min height | 12 dp / 56 dp | **10 dp / 48 dp** |
| `row.paddingHorizontal` | (16) | **16 dp**, title -> summary 2 dp |
| `card.padding` | 16 / 16 | 16 / 16 for content cards; **0 vertical for list cards** (rows carry it) |
| `card.gap` | 12 dp everywhere | **12 dp between homogeneous items; 16 dp between distinct cards** (`group.gap`) |
| section header row | top 16 / bottom 8 | **top 24 (incl. 16 gap) / bottom 8, text start 32 dp from the screen edge** |
| divider | 1 dp, start inset | **max(1 px, 0.33 dp), inset 16 dp start and end (+ icon + 16 with a leading icon)** |
| `barHeight` / item / gap / padding | 64 / 80 min / — / 8 | **56 (min) / 70 x 48 / 8 / 12 h, 4 v** |
| `barOffset` | nav inset + 12 dp | **gesture: max(16, inset + 4); 3-button: inset + 8** |
| `content.bottomClearance` | bar + offset + 16 | **88 dp (+ inset when > 16 dp)** |
| `railWidth` / `railOffset` / `railArea` | 72 / 12 / — ; labelled 180 | **56 / 16 / 88; labelled rail removed** |
| `topBarCollapsed` / `Medium` / `Short` / `Large` | 56 / 64 / 48 / 112 | **52 / 52 / 48 / removed** |
| title start | 60 / 20 dp | **66 dp with back, 16 dp without** |
| `topBarTintDistance` | 8 dp | **10 dp**; small title alpha over scroll 40 -> 72 dp |
| round button | — | **40 dp visual, 48 dp target, 16 dp margin, glyph 20 dp / 1.5 dp stroke** |
| `icon.size` | ... 16 dp chip glyph, 40 dp glyph in 56 dp container | **status dot 8, triangle 10, ring 12; summary glyph 28 dp, no container** |
| switch / radio / chevron | M3 | **44 x 24, knob 18, inset 3 / 24 box, ring 20 x 1.3 / 12 x 24** |
| segmented control | — | **40 dp visual, 4 dp inset, segments >= 52 dp, 12 dp label padding** |
| tab row | — | **48 dp row, 12 dp tab padding, 2 dp indicator** |
| chip | 36 dp | **36 dp visual, 16 dp padding, no outline** |
| buttons | 48 dp, 20 / 12 | **primary 50 dp, 28 / 12; dialog 44 dp** |
| sheet handle | 32 x 4 | **36 x 4, 10 top / 6 bottom, 48 dp drag target**; header 56 dp; max height window - status bar - 24 dp |
| dialog | max width 480, centred | **392 dp; Compact bottom-docked 16 / 16 + inset margins; padding 24; title->message 6; message->buttons 22; button gap 8** |
| progress segment | 10 dp | **8 dp, gap 4** |

### 4.4 Shapes (`tokens.md` 5)

| name | - | + |
|---|---|---|
| `shape.m` | 12 dp (banner, nested box, tooltip) | **12 dp, small elements only** |
| + `shape.popup` | — | **16 dp** (tooltip) |
| `shape.l` | 20 dp, smoothing 0.6 | **20 dp, smoothing 0.55** (also banners, hero) |
| `shape.xl` | 28 dp (hero, onboarding) | **onboarding illustration only** |
| `shape.dialog` | 32 dp | **30 dp, smoothing 0.6** (COUI 45 dp alternative, [needs reference capture]) |
| `shape.pill` | exact semicircle | **smooth pill (r = h / 2 / 1.08, smoothing 0.08) for bar, rail, buttons; exact elsewhere** |
| `shape.pill` uses | ... selection capsule ... | **remove "selection capsule"** |

### 4.5 Elevation (`tokens.md` 6)

| name | light (- / +) | dark (- / +) |
|---|---|---|
| `elevation.card` | y1 / b3 / 6 % / **none** | none / none |
| `elevation.shell` | y6 / b20 / 10 % / **y4 / b12 / 10 %** | y4 / b16 / 50 % / **y4 / b10 / 35 %** |
| + `elevation.control` | — / **y3 / b8 / 10 %** | — / **y3 / b8 / 20 %** |
| `elevation.overlay` (sheet) | y12 / b32 / 14 % / **y0 / b24 / 12 %** | y12 / b32 / 60 % / **y0 / b24 / 30 %** |
| + `elevation.accentGlow` | — / **#0068E0 @ 25 %, y4 / b12** | — / **#428AF7 @ 35 %, y4 / b12** |

Materials (`tokens.md` 7.1): bar blur 24 dp kept; top bar progressive blur **20 -> 12 dp**, no solid backing,
no hairline; sheet blur 40 kept (tint near opaque); dialog: rim and outer hairline removed.

### 4.6 Motion (`tokens.md` 9, `Motion.kt`)

| name | - | + |
|---|---|---|
| `spring.page` | 1.0 / 220 | unchanged (matches measured settle); push / pop add a black dim **0 -> 0.3** on the covered page [estimate] |
| `spring.selection` | 0.72 / 600 (nav + period capsule) | **1.0 / 247** (period indicator, tab underline; nav capsule removed) |
| `spring.press` | 0.6 / 1200 | **split: `pressIn` 1.0 / 632 compact, 1.0 / 439 wide; `pressOut` 0.4 / 158 compact, 0.5 / 158 wide** |
| press scale | 0.97 everywhere | **size-dependent 0.85-0.97 (M2); bar pill 0.96; grouped rows none** |
| `spring.sheet` | 0.92 / 420 | **1.0 / 250 (show)**; + `spring.sheetHide` **1.0 / 440** (replaces the 200 ms exit tween); + `spring.sheetReturn` **1.0 / 247** |
| + `spring.switch` | — | **0.7 / 247**, knob stretch x1.3 at 34.7 % |
| + `spring.squash` | — | **0.5 / 110** (segment height squash, optional) |
| dialog | scale 0.94 -> 1 + fade 250 / fade out 150 | **docked: slide +100 % + fade `tween(250, flow)`; centred: scale 0.8 -> 1 + fade `tween(250, flow)`; exit fade `tween(150, exit)`** |
| state layer timing | `tween(100)` | **rows in 1.0 / 439, out 1.0 / 895; buttons in 1.0 / 1755, out 1.0 / 187** |
| glow | `accentGlow`, 48 dp | **`glow.neutral`, ~140 dp clipped; 110 / 480 ms unchanged** |
| + `motion.overscroll` | — | **follow 24-34 %; return 700 ms (0.2, 0.62, 0.17, 1.05); throw 0.047 s x v, max 107 dp, rise 60 ms** |
| + `easing.title` | — | **(0.33, 0, 0.67, 1)** small-title fade |
| nav selection | capsule `spring.selection` | **icon / weight crossfade `duration.micro` + `easing.standard`** |
| 9.4 Push | "no dimming (as measured)" | **"dim 0 -> 0.3 on the covered page [estimate, re-measure `frames-back/f000060-f000075`]"** (also correct RA §8.1) |
| 9.4 Top-level switch | crossfade + capsule move | **crossfade 150 ms only** |
| 9.5 Haptics | `CLOCK_TICK`, `CONFIRM` on snap | **as M4; no `CONFIRM` on snap** |
| 9.6 Reduced motion | | **+ overscroll none; switch snaps; sheet / dialog fade 120 ms** |

### 4.7 Calibration table (`tokens.md` 11)

Rows to rewrite: "Window / card (dark)" -> ours #000000 / #1A1A1A, "match"; "Secondary text" -> #969696 with the
`surface.3` rule; "Accent" -> #428AF7 controls, #2A6BE0 label fills, #5C9DFF text; "Bottom bar" -> 56 dp,
16 dp above the edge; "Selection in bar" -> "filled icon + bold label, no capsule; not colour alone because of
shape and weight". Add rows: switch off track (2.16:1 exception), unchecked radio #767676 vs #5F5F5F, nav label
11 sp vs 10 sp, disabled labels.

## 5. Items needing light-theme or other references

All light values in section 4 are proposals from the Iris and COUI light schemes. One light capture of
Settings > Display, Settings > Accessibility, Clock and a sheet would settle most of them; dialog, Calendar,
setup-flow, chart (OHealth) and slow back-gesture captures settle the rest. Capturing needs the owner's explicit
permission for each phone session (hard rule 10).

| Item | Proposed value | Source |
|---|---|---|
| Light background / card | #F0F1F2 / #FFFFFF, no card shadow | Iris, COUI light |
| Light text ramp | #1A1A1A / #666666 / #6E6E6E | kits (black @ 90 / 54 %), adjusted for 4.5:1 |
| Light separator, chevron | #E0E0E0 / #BDBDBD | black @ 12 % / 26 % |
| Light accent | #0068E0 (kits #0080FF fails 3.8:1) | COUI `Day.BLUE`, adjusted |
| Light switch off / radio ring | #D6D6D6 / #8F8F8F | black @ 16 % / 26 % (+ 3:1 adjustment) |
| Light bar, rail, round button | #FFFFFF -> #F7F7F9 / #F2F2F4, rim #FFFFFF @ 80 % + #000000 @ 8 %, shadow 4 / 12 / 10 % | Iris `rimHighlight` / `rimShade`, COUI 34 / 255 x 0.75 |
| Light sheet, dialog, scrim | #F5F5F8, #FFFFFF, #000000 @ 40 % (dialog dim 20 % per COUI) | Iris, COUI |
| Light nav colours | #E6000000 / #B8000000 (~#474747) | Iris `content` / `contentVariant` |
| Light segmented, chips | track black @ 6 %, indicator #FFFFFF @ 70 %; chip #E6E7E8 / #FFFFFF | COUI |
| Light status | within #1E7F2E, moved #9C6200, neutral #6B6B6B | COUI hues, darkened to 5:1 |
| Light glow / pressed | white @ 30 % / black @ 6 % | Iris `pressOverlay` |
| Light band | track #EBEBEB, fill #8C8C8C, marker white + #CCCCCC rim | derived |
| Whether bar and sheets are translucent in light | assume yes (as dark) | — |
| Dialog shape, title weight, position | 30 dp, 700, bottom-docked | [needs reference capture] |
| Calendar grid | transparent cells, accent circle | [needs reference capture] |
| Onboarding / setup layout | full-width primary + text button | [needs reference capture] |
| Chart axis side, health chart look | right side, neutral grid | [needs reference capture] (OHealth) |
| Predictive back preview | scale 0.92 kept | [needs reference capture] (slow back swipe) |
| Page dim during push / pop | black 0.3 | [estimate] re-measure existing `frames-back` |
| Inner-screen rail | 56 dp vertical pill | [needs inner-screen references] |

## 6. Implementation order and work packages

Order: P1 first inside every package; the packages run in parallel. Ownership is exclusive: a package edits only
the files it owns. Contract that keeps them independent:

- **Existing token names keep their names**; WP1 changes their values. Components that read `colors.surface1`,
  `type.titleSmall` etc. pick up the new look without edits.
- **New tokens a package needs for its own components live in a new file the package owns** (`ShellTokens.kt`,
  `ControlTokens.kt`, `DataTokens.kt`), with the values from section 4. After all packages merge, WP1's
  follow-up folds them into `NbColors` / `Dimensions` (rename only).
- **Removals happen last**: `primaryContainer`, `onPrimaryContainer`, `accent`, `*.container`, `band.edge`,
  `topBarLarge` are first marked `@Deprecated` by WP1 and deleted in the sweep once no package references them.
- Screenshot baselines (`app/build/screenshots/`) are regenerated per package; `ContrastTest` is owned by WP1
  only, and packages add pairs in their own test file if needed.

### WP1 Foundations and motion system (P1: F1, F2, F5 values, F7 values, T1-T5, S1, S5 values, M1; then P2/P3)

Owns:
- `ui/theme/NbColors.kt`, `NbTypography.kt`, `Dimensions.kt`, `Shapes.kt`, `Elevation.kt`, `Theme.kt`,
  `Contrast.kt`
- `ui/motion/Motion.kt`, new `ui/motion/NbOverscroll.kt`
- `ui/material/Interaction.kt` (M2, M5, M6: press scale, highlight under content, `NbPressIndication`, glow)
- `MainActivity.kt` (D1 status-bar style)
- new `ui/components/NbText.kt` (`NbText`, `NbIcon`)
- `docs/design/tokens.md`, `docs/design/reference-analysis.md` (§8.1 dim correction), `docs/adr/` entry for
  section 1.2
- `tools/design/contrast_check.py`, `app/src/test/.../ui/theme/ContrastTest.kt`, `ColorTokensTest`, a
  ripple-ban check in `tools/`

Steps: neutral ramp and accent values (4.1); type weights and new styles (4.2); row, divider, section-header
values (4.3); shapes and elevation (4.4, 4.5); springs, overscroll, indication (4.6); `Theme.kt` provides
`LocalContentColor`, `LocalTextStyle`, `LocalIndication`, `LocalOverscrollFactory`, minimal neutral M3 scheme;
tokens.md and tests in the same commits. Follow-up sweep after all packages: fold package token files, delete
deprecated tokens, swap `material3.Text` / `Icon` to `NbText` / `NbIcon` everywhere, drop `MaterialTheme`.

### WP2 Shell, overlays and navigation (P1: A1-A5, B1, C1-C5, G1, G2, H1, P1, P2)

Owns:
- `ui/components/Navigation.kt`, `ui/components/Shell.kt`, `ui/components/TopLevel.kt`, `ui/components/NbIcons.kt`
  (arrow back glyph, filled / outline tab icons, small chevron for WP3)
- new `ui/components/ShellRoundButton.kt`, new `ui/theme/ShellTokens.kt`
- `ui/shell/AppScaffold.kt`, `ui/material/ShellMaterial.kt`, `ui/material/Effects.kt`,
  `ui/material/PlatformEffects.kt`
- `nav/AppNavHost.kt` (transitions, `NavigationKind.None` on detail routes, predictive back), `nav/Routes.kt`
- `ui/settings/ShellPage.kt`, `ui/settings/SettingsScreen.kt` (dialog placement, sub-page `AnimatedContent`)
- `ui/home/HomeScreen.kt` (top bar / large title only)

Keeps the public signatures of `GlassSheet`, `GlassDialog`, `ShellTopBar` stable (adding parameters with defaults
only) so screens owned by WP3 / WP4 compile unchanged. Dialog buttons use local pill composables until WP3's
`SecondaryButton` lands; the sweep switches them.

### WP3 Controls, lists, settings, history, onboarding (P1: L1, L2, L4, S1-S3 layouts, M4 switch haptic; then L3, L5-L14, M3)

Owns:
- `ui/components/Lists.kt`, `ui/components/Buttons.kt`, new `ui/components/Controls.kt` (`NbSwitch`, `NbRadio`,
  `NbTabRow`, `NbChoiceRow`, `NbChipTab`), new `ui/theme/ControlTokens.kt`
- `ui/settings/SettingsParts.kt`, `SettingsSections.kt`, `SettingsPages.kt`
- `ui/history/HistoryControls.kt`, `HistoryCalendar.kt`, `HistoryScreen.kt`, `HistoryNightPreview.kt`
- `ui/onboarding/OnboardingContent.kt`, `OnboardingScreen.kt` (layout and step `AnimatedContent`),
  `StepIllustration.kt`
- `PermissionsRationaleActivity.kt`
- `docs/design/screens.md` (list, settings, history, onboarding sections)

Removes every M3 `Switch` / `RadioButton` import (unblocks the WP1 `MaterialTheme` removal). Uses
`NbIcons.ChevronSmall` once WP2 adds it; until then draws the chevron with the current icon at the new size.

### WP4 Cards, status and data visualisation (P1: K1, K2, K3, K6, F9; then K4, K5, K7-K13, M4 segment and scrub haptics)

Owns:
- `ui/components/Cards.kt`, `StatusGlyphs.kt`, `RangeBand.kt`, `Chart.kt` (period selector, stats row, chart
  colours, tooltip), `Feedback.kt`, new `ui/theme/DataTokens.kt`
- `ui/home/HomeBlocks.kt`, `HomeGrid.kt`, `HomePanes.kt`
- `ui/metric/*` screens and blocks, `ui/night/*`, `ui/learning/*`
- `docs/design/screens.md` is edited only by WP3; WP4 records its wording changes ("chip" -> "label") in its
  handback for WP3 / the sweep [decision: one owner per doc file]

### Size and sequencing

| Package | P1 items | Rough size | Blocks |
|---|---|---|---|
| WP1 | palette, type, rows, overscroll | large (touches tests and docs) | the sweep only |
| WP2 | bar, rail, top bar, round button, sheet, dialog, page transitions | large | none |
| WP3 | switch, radio, rows, buttons, grouped settings | medium | none (chevron glyph is optional) |
| WP4 | status label, headers, period selector, range band | medium | none |
| Sweep | fold tokens, delete deprecated, NbText/NbIcon, drop `MaterialTheme`, regenerate screenshots | small | after WP1-WP4 |

## 7. Owner questions

1. Within state: white value marker and a grey "Usual" label with a small green dot (#24B232), so "within"
   carries almost no colour (the calm reading)?
2. Moved colour: COUI yellow #E5A100 or the current desaturated ochre #E8B56E (calmer, less canonical)?
3. Retire the violet accent entirely in favour of the single ColorOS blue (onboarding illustrations included)?
4. Dialog radius: Iris 30 dp or COUI 45 dp, before a dialog capture exists?
5. Permission for one reference session (light theme Settings, Clock, a sheet and a dialog; Calendar; a slow
   back swipe), to replace the [needs light references] values.
