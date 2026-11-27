# Design tokens

Status: implemented (stage 4), ColorOS 17 conformance applied 2026-11-26 (`coloros-conformance.md` WP1). Date: 2026-11-26.
Inputs: `principles.md`, `reference-analysis.md` (measurements of the owner's system apps, outer screen, dark theme),
`docs/research/design-coloros17.md` (Iris numbers, Haze recommendation), `docs/research/coloros17-glass.md`,
`coloros-conformance.md` (the conformance plan this file implements), ADR 0003, ADR 0005, `screens.md`, `states.md`,
`layouts.md`. This file is the single source of truth for values; the other design documents only name tokens.

Our own token set in the spirit of ColorOS 17 (Living Flow Design, Luminous Effect). Nothing here is a vendor
resource: no vendor fonts, icons, images or colour files; reference measurements are calibration targets only.

Tags: **[measured]** (from `reference-analysis.md`), **[Iris]** (third-party reverse-engineered numbers, see the
research file), **[decision]** (ours, with the reason), **[hypothesis]** (to verify on device or emulator).

Automatic check: `python3 -I tools/design/contrast_check.py` parses the colour tables (header
`| token | light | dark | ... |`) and the pairs table (header `| pair | fg | bg | theme | min |`) in this file
and fails on any pair below its threshold. Keep both formats exact; see section 2.10. `ContrastTest` and
`ColorTokensTest` run the same checks against the Kotlin theme in `./gradlew test`. It runs locally today; wiring
it into the pre-commit hook (when `tokens.md` is staged) and into CI is an open item for stage 4/5 (`review.md`).

---

## 0. Conventions

- Names are `group.role[.variant]`; Kotlin names drop the dots (`color.status.moved` -> `NbColors.statusMoved`).
- Colour values: `#RRGGBB` (opaque), `#RRGGBB @ NN%` (translucent, alpha NN %), `= other.token` (alias, resolved
  per theme).
- All sizes in dp, text in sp. Every text size scales with the system font scale up to 200 % (no capping).
- Two themes, light and dark, follow the system by default (Settings > Appearance can force one). **No dynamic
  colour** [decision]: wallpaper-derived colours cannot be contrast-checked ahead of time and would recolour the
  status hues that carry meaning.
- Two effect modes, `EffectsMode.Glass` and `EffectsMode.Solid` (section 7). Every glass token has a solid
  counterpart; geometry is identical in both modes.

---

## 1. Colour roles at a glance

| Role group | Purpose | Rule |
|---|---|---|
| `background`, `surface.*` | window and solid content surfaces | data always sits here |
| `text.*` | text on `background` / `surface.*` | never on glass |
| `onGlass.*` | text and icons on glass and on its solid fallback | the only text tokens allowed on glass |
| `primary`, `primaryText`, `primaryFill` | the one blue accent: controls and graphics, links and text buttons, filled buttons | never carry a measurement status |
| `status.*` | within / moved / neutral / info, plus aliases | always with a shape glyph and a label; no tonal containers |
| `band.*`, `marker.*`, `chart.*` | range band, value marker, chart | marks must reach 3:1 |
| `control.*`, `chevron` | switch, slider track, convex round buttons, disclosure chevron | |
| `glass.*`, `solid.*`, `scrim` | shell materials (glass only on bar, rail, top bar, sheet, dialog) | section 7 |
| `focus.ring` | keyboard focus | 3:1 on every surface incl. glass worst case |
| `state.*` | pressed and selected highlights | drawn under the content |

## 2. Colours

All values follow the ColorOS 17 conformance plan (`coloros-conformance.md` section 4.1). Where the canon fails
our accessibility rules, the rule wins and the deviation is recorded in `coloros-conformance.md` 1.2 and ADR 0005.

### 2.1 Neutrals and text

Neutral, R = G = B, as measured: dark on the folded captures (true black window, #1A1A1A cards, #E9E9E9 /
#969696 / #8A8A8A text, #484848 hairline), light on the inner-screen light captures (window #F0F1F2, white cards,
#E0E0E0 hairline, #BDBDBD chevron, #D6D6D6 switch off, #6E6E6E section header) [measured]. Light secondary text is
#666666 instead of the measured #757575 (4.07:1 on the window) [decision]. Secondary text never sits on a fill
lighter (dark) or darker (light) than `surface.3`.

| token | light | dark | use |
|---|---|---|---|
| `color.background` | `#F0F1F2` | `#000000` | window background, page behind cards |
| `color.surface.1` | `#FFFFFF` | `#1A1A1A` | cards, tiles, chart card, settings groups (level 1) |
| `color.surface.2` | `#F5F5F5` | `#242424` | nested areas (retiring: hairline splits replace nested boxes) |
| `color.surface.3` | `#EBEBEB` | `#2E2E2E` | selected row, segmented track, pressed fill; highest fill that may carry `text.secondary` |
| `color.surface.elevated` | `#FFFFFF` | `#2B2B2B` | cards inside sheets, chart tooltip |
| `color.text.primary` | `#1A1A1A` | `#E9E9E9` | titles, values, body |
| `color.text.secondary` | `#666666` | `#969696` | summaries, units, axis labels, row values |
| `color.text.tertiary` | `#6E6E6E` | `#8A8A8A` | section headers, captions, subtitles, disabled labels; only on `background` and `surface.1` |
| `color.separator` | `#E0E0E0` | `#484848` | 1 px hairline inside cards, inset 16 dp at both ends; decorative, never the only boundary |
| `color.outline` | `#767676` | `#767676` | essential boundaries: unchecked radio ring, text field, dotted learning band |
| `color.chevron` | `#BDBDBD` | `#5F5F5F` | disclosure chevron (decorative: the row title carries the meaning) |
| `color.control.track` | `#EBEBEB` | `#333333` | slider and range band track |
| `color.control.switchOff` | `#D6D6D6` | `#505050` | switch track when off (2.16:1, recorded canon exception: the white knob position carries the state) |
| `color.control.convexTop` | `#FAFAFB` | `#1F1F1F` | convex round shell button, gradient top |
| `color.control.convexBottom` | `#F3F4F5` | `#2A2A2A` | convex round shell button, gradient bottom |
| `color.control.convexRimTop` | `#FFFFFF` | `#333333` | convex button rim, 0.5 dp top |
| `color.control.convexRimBottom` | `#FCFCFC` | `#3E3E3E` | convex button rim, 1 dp bottom |

### 2.2 Accent

One saturated blue, as in the system apps; the violet second accent is retired [owner decision 2026-11-26]. The
canon accent fails 4.5:1 under a white label (#428AF7: 3.38) and as light text (#0080FF / measured #0078EF: 3.8 /
4.26 on white), so the accent is split by role [decision]:

| token | light | dark | use |
|---|---|---|---|
| `color.primary` | `#0068E0` | `#428AF7` | controls and graphics: switch on, radio, slider, progress, selected label; links (5.18 / 5.15 on the card) |
| `color.primaryText` | `#0062D6` | `#5C9DFF` | links, text buttons, info status; passes 4.5 on `surface.3` too |
| `color.primaryFill` | `#0068E0` | `#2A6BE0` | filled buttons that carry a text label |
| `color.onPrimary` | `#FFFFFF` | `#FFFFFF` | label and icon on `primaryFill` (5.18 / 4.91); glyphs on `primary` (3:1) |
| `color.accentGlow` | `#0068E0 @ 25%` | `#428AF7 @ 35%` | coloured glow under the accent call-to-action only (decorative) |
| `color.glow.neutral` | `#FFFFFF @ 30%` | `#FFFFFF @ 13.5%` | touch-following light on shell items and the primary button (decorative, never behind text that must be read) |

### 2.3 Status

Calm by construction: there is no red and no error colour in the app (`principles.md` 1). Higher and lower share
one hue, the direction is carried by the triangle (`principles.md` 2). Every status has a glyph and a label
(`screens.md` section 6); there are no tonal status containers (none exist in the references). Hues follow the
ColorOS functional green and yellow [owner decision 2026-11-26: amber moved colour, calm green within], darkened in
light so they pass 4.5:1 on every surface up to `surface.3`.

| token | light | dark | glyph that always accompanies it | use |
|---|---|---|---|---|
| `color.status.within` | `#19762A` | `#24B232` | small filled dot (metric), circle with check (night) | inside the usual range; used sparingly, the label is grey "Usual" |
| `color.status.moved` | `#8F5A00` | `#E5A100` | triangle(s) in the direction of change; rounded square with 1 or 2 triangles (night) | moved outside the usual range (ColorOS amber) |
| `color.status.movedGlyph` | `#B07000` | `= color.status.moved` | the moved shapes only: triangles, the night glyph, band and chart markers (graphics, 3:1) | the brighter ColorOS amber in light, where the 4.5:1 text colour reads brown |
| `color.status.above` | `= color.status.moved` | `= color.status.moved` | upward triangle, marker right of the band | higher than usual |
| `color.status.below` | `= color.status.moved` | `= color.status.moved` | downward triangle, marker left of the band | lower than usual |
| `color.status.neutral` | `#666666` | `#969696` | dotted ring, dash, half circle, slashed circle | learning, missing, short night, not provided |
| `color.status.learning` | `= color.status.neutral` | `= color.status.neutral` | dotted ring, dotted band outline | learning period, per-metric learning |
| `color.status.missing` | `= color.status.neutral` | `= color.status.neutral` | short dash (no value), slashed circle (not provided) | no value, too few readings, not recorded |
| `color.status.info` | `= color.primaryText` | `= color.primaryText` | outline icons: key, cloud with clock, download arrow | data-access problems (permission, sync, Health Connect update) |

Notification accent colour (`states.md` 6.1): the light value of `color.status.moved`.

### 2.4 Range band, value marker, chart

The usual range is a solid neutral segment on the track (like the measured slider): its boundary is the fill
against the card (3.36 / 3.27). No edge lines, no median tick. Markers get a 2 dp halo in `marker.halo`.

| token | light | dark | use |
|---|---|---|---|
| `color.band.fill` | `#8C8C8C` | `#6B6B6B` | usual-range segment on the 16 dp `control.track` pill |
| `color.band.learningOutline` | `= color.outline` | `= color.outline` | dotted outline where the band will be (learning) |
| `color.marker.within` | `#FFFFFF` | `#FFFFFF` | value marker inside the range (slider-thumb idiom) |
| `color.marker.withinRim` | `#8C8C8C` | `#FFFFFF` | 1 px rim of the white marker (light only: the dark marker draws no rim) |
| `color.marker.halo` | `= color.surface.1` | `= color.surface.1` | 2 dp ring around every value marker |
| `color.chart.point` | `#4A4A4A` | `#E9E9E9` | chart points inside the range; learning points are hollow `status.neutral` |
| `color.chart.grid` | `#000000 @ 6%` | `#FFFFFF @ 8%` | 1 px horizontal grid lines (decorative; values are in labels and the text summary) |
| `color.chart.bandArea` | `#000000 @ 8%` | `#FFFFFF @ 12%` | the chart's stepped usual-range area |
| `color.chart.guide` | `= color.text.secondary` | `= color.text.secondary` | vertical selection guide under the finger / pointer |

Marker colours: `marker.within` (inside), `status.movedGlyph` (triangle, 3:1 on the track and the card),
`status.neutral` (learning, on the track), `chart.point` (chart points inside the range).

### 2.5 Glass tints, rim, text on glass

Tints are composited over the blurred backdrop. Their alpha is chosen so that `onGlass.*` text passes 4.5:1 even
if the backdrop were pure white or pure black (section 2.10 checks both). The rim is drawn inside the shape: a
0.5 dp top highlight and a brighter, thicker bottom: a 1 dp stroke plus a faint 2 dp reflection band (bottom
brighter than top, as measured on the bar: top #363636 1 px, bottom #3B3B3B-#3D3D3D 2-3 px [measured]). No outer
hairline, no refraction, no specular motion (`docs/research/coloros17-glass.md` 4.1).

| token | light | dark | use |
|---|---|---|---|
| `color.glass.bar` | `#FFFFFF @ 80%` | `#1B1B1B @ 80%` | floating bottom bar and navigation rail tint |
| `color.glass.topBar` | `#F0F1F2 @ 82%` | `#000000 @ 72%` | top bar tint under the small title once it is visible (the row that carries text) |
| `color.glass.topBarDim` | `#F0F1F2 @ 35%` | `#000000 @ 35%` | top stop of the progressive darkening (0.35 -> 0) where no text sits [measured x0.65-0.75] |
| `color.glass.sheet` | `#F5F5F8 @ 92%` | `#171717 @ 92%` | bottom sheet tint |
| `color.glass.dialog` | `#FFFFFF @ 96%` | `#232323 @ 96%` | dialog tint (96 % keeps grey message text >= 4.5:1 over any backdrop) |
| `color.glass.rimTop` | `#FFFFFF @ 80%` | `#FFFFFF @ 13%` | rim, 0.5 dp top highlight |
| `color.glass.rimBottom` | `#000000 @ 8%` | `#FFFFFF @ 16%` | rim, 1 dp bottom stroke and 2 dp bottom reflection band (band at half this alpha) |
| `color.onGlass.primary` | `#1A1A1A` | `#E9E9E9` | titles, labels, selected icons and labels on glass |
| `color.onGlass.secondary` | `#474747` | `#C0C0C0` | unselected nav icons and labels (measured light #454545), secondary text in sheets and dialogs |

The top bar title rule [decision, accessibility over canon]: the ColorOS top bar has no backing, only progressive
blur and a 0.35 -> 0 darkening. We keep that look where no text sits (`glass.topBarDim`); once the small title has
faded in, its row gets `glass.topBar`, which passes the worst case.

The chart tooltip is **not** glass: it floats over data, so it is always `material.solid.tooltip` (section 7.2)
and uses `text.*` [decision, review round 0: glass is reserved for the shell, ADR 0003].

The touch light (`glow.neutral`, section 9.4) is composited *under* the labels, as part of the backdrop, so the
worst-case contrast model (backdrop pure white or pure black) still bounds every label on a pressed glass item.

Inside a glass sheet or dialog, content cards (for example a settings group) are solid `surface.elevated` /
`surface.1` and use `text.*` again; only text placed directly on the tint uses `onGlass.*`. No glass on glass.

### 2.6 Solid fallback for glass, and scrim

Used in `EffectsMode.Solid` (section 7.3), for the home-screen widget and, in both modes, for the chart tooltip and
the convex round buttons. The bar keeps a quiet vertical gradient (the "lit" look of the measured shell controls:
#1B1B1B -> #232323 [measured]) and the same rim tokens as glass, so the fallback looks finished rather than switched
off. Under higher contrast the solid shell also gets a 1 dp border in `onGlass.primary` at 60 % (section 7.3).

| token | light | dark | use |
|---|---|---|---|
| `color.solid.barTop` | `#FFFFFF` | `#1B1B1B` | bar and rail fill, gradient top |
| `color.solid.barBottom` | `#F7F7F9` | `#232323` | bar and rail fill, gradient bottom |
| `color.solid.topBar` | `= color.background` | `= color.background` | top bar fill: `background` opaque over status bar + toolbar, fading to 0 over the 16 dp below; no hairline |
| `color.solid.sheet` | `#F5F5F8` | `#171717` | sheet fill |
| `color.solid.dialog` | `#FFFFFF` | `#232323` | dialog fill |
| `color.solid.tooltip` | `= color.surface.elevated` | `= color.surface.elevated` | chart tooltip fill in both effect modes (with `elevation.raised`) |
| `color.scrim` | `#000000 @ 40%` | `#000000 @ 60%` | dims content behind sheets and dialogs, both modes |
| `color.sheetHandle` | `#000000 @ 26%` | `#FFFFFF @ 30%` | 36 x 4 dp sheet handle (decorative; the sheet has a close button) |

### 2.7 Focus

| token | light | dark | use |
|---|---|---|---|
| `color.focus.ring` | `#0068E0` | `#5C9DFF` | 2 dp keyboard focus ring (section 10); ColorOS has none, ours is kept [decision] |

### 2.8 State layers

ColorOS has no ripple. The pressed highlight is drawn **under** the content, clipped to the element's shape, so
labels keep their colour; elements also shrink (section 9.4). Material's ripple is removed app-wide
(`LocalIndication` = `NbPressIndication` from the interaction framework, `LocalRippleConfiguration` = null; `tools/check-ripple.sh` rejects
`ripple(`).

| token | light | dark | use |
|---|---|---|---|
| `color.state.pressed` | `#000000 @ 6%` | `#FFFFFF @ 12.5%` | pressed highlight under the content (hover: two thirds of it) |
| `color.state.selected` | `#000000 @ 6%` | `= color.surface.3` | selected rows and tiles (then `text.primary`, no secondary text on it) |

| name | value | where |
|---|---|---|
| `state.glassHover` | rim alphas x 1.5 | glass items (no grey layer on glass) |
| `state.glassPressed` | `glow.neutral` light under the finger (section 9.4) | glass items, round shell buttons, primary button |
| `state.dragged` | `text.primary` at 16 % | chart scrub handle, sheet handle |
| `state.disabled` | labels stay `text.tertiary` (>= 4.5:1); only control graphics (switch, radio) may use the canon #5F5F5F; filled buttons at alpha 0.3 with a reason text | disabled controls |

### 2.9 Retired tokens (deleted)

The integration sweep deleted the tokens the conformance plan retired; no code or pair refers to them any more:
`color.primaryContainer` / `onPrimaryContainer` (no tonal containers: neutral selection, icon fill and weight),
`color.accent` (single blue accent, `primary`), the `color.status.*.container` / `onContainer` pairs (glyph and label
sit on the card), `color.band.edge` (no band edges or median tick), `color.glass.rimMid` / `rimOuter` (two-part rim,
no outer hairline).

### 2.10 Contrast pairs (machine-checked)

Format (parsed by `tools/design/contrast_check.py`):

- `pair`: name; the first part is the kind: `text` (normal text, >= 4.5), `large` (text >= 24 sp, or >= 19 sp at
  weight 700; >= 3.0), `graphic` (icons, glyphs, markers, essential boundaries; >= 3.0), `focus`
  (focus indicator; >= 3.0). The script rejects a `min` below the kind's rule.
- `fg`, `bg`: token names (or literal colours); `fg` must be opaque. A translucent `bg` is glass: it is
  composited over a pure white and a pure black backdrop and the worse ratio counts.
- `theme`: `light`, `dark` or `both`. `min`: required ratio.
- Every text or on-colour token must appear as `fg` at least once (coverage check).

| pair | fg | bg | theme | min |
|---|---|---|---|---|
| text.primary.background | color.text.primary | color.background | both | 4.5 |
| text.primary.surface1 | color.text.primary | color.surface.1 | both | 4.5 |
| text.primary.surface2 | color.text.primary | color.surface.2 | both | 4.5 |
| text.primary.surface3 | color.text.primary | color.surface.3 | both | 4.5 |
| text.primary.elevated | color.text.primary | color.surface.elevated | both | 4.5 |
| text.secondary.background | color.text.secondary | color.background | both | 4.5 |
| text.secondary.surface1 | color.text.secondary | color.surface.1 | both | 4.5 |
| text.secondary.surface2 | color.text.secondary | color.surface.2 | both | 4.5 |
| text.secondary.surface3 | color.text.secondary | color.surface.3 | both | 4.5 |
| text.secondary.elevated | color.text.secondary | color.surface.elevated | both | 4.5 |
| text.tertiary.background | color.text.tertiary | color.background | both | 4.5 |
| text.tertiary.surface1 | color.text.tertiary | color.surface.1 | both | 4.5 |
| text.link.surface1 | color.primary | color.surface.1 | both | 4.5 |
| text.link.background | color.primary | color.background | both | 4.5 |
| text.primaryText.background | color.primaryText | color.background | both | 4.5 |
| text.primaryText.surface1 | color.primaryText | color.surface.1 | both | 4.5 |
| text.primaryText.surface3 | color.primaryText | color.surface.3 | both | 4.5 |
| text.primaryText.elevated | color.primaryText | color.surface.elevated | both | 4.5 |
| text.onPrimary | color.onPrimary | color.primaryFill | both | 4.5 |
| text.status.within.surface1 | color.status.within | color.surface.1 | both | 4.5 |
| text.status.within.surface2 | color.status.within | color.surface.2 | both | 4.5 |
| text.status.moved.surface1 | color.status.moved | color.surface.1 | both | 4.5 |
| text.status.moved.surface2 | color.status.moved | color.surface.2 | both | 4.5 |
| text.status.neutral.surface1 | color.status.neutral | color.surface.1 | both | 4.5 |
| text.status.neutral.surface2 | color.status.neutral | color.surface.2 | both | 4.5 |
| text.status.info.surface1 | color.status.info | color.surface.1 | both | 4.5 |
| text.status.info.surface2 | color.status.info | color.surface.2 | both | 4.5 |
| text.status.within.surface3 | color.status.within | color.surface.3 | both | 4.5 |
| text.status.moved.surface3 | color.status.moved | color.surface.3 | both | 4.5 |
| text.status.neutral.surface3 | color.status.neutral | color.surface.3 | both | 4.5 |
| text.status.info.surface3 | color.status.info | color.surface.3 | both | 4.5 |
| text.status.within.background | color.status.within | color.background | both | 4.5 |
| text.status.moved.background | color.status.moved | color.background | both | 4.5 |
| text.onGlass.primary.bar | color.onGlass.primary | color.glass.bar | both | 4.5 |
| text.onGlass.secondary.bar | color.onGlass.secondary | color.glass.bar | both | 4.5 |
| text.onGlass.primary.topBar | color.onGlass.primary | color.glass.topBar | both | 4.5 |
| text.onGlass.secondary.topBar | color.onGlass.secondary | color.glass.topBar | both | 4.5 |
| text.onGlass.primary.sheet | color.onGlass.primary | color.glass.sheet | both | 4.5 |
| text.onGlass.secondary.sheet | color.onGlass.secondary | color.glass.sheet | both | 4.5 |
| text.onGlass.primary.dialog | color.onGlass.primary | color.glass.dialog | both | 4.5 |
| text.onGlass.secondary.dialog | color.onGlass.secondary | color.glass.dialog | both | 4.5 |
| text.onGlass.primary.solidBarTop | color.onGlass.primary | color.solid.barTop | both | 4.5 |
| text.onGlass.primary.solidBarBottom | color.onGlass.primary | color.solid.barBottom | both | 4.5 |
| text.onGlass.secondary.solidBarTop | color.onGlass.secondary | color.solid.barTop | both | 4.5 |
| text.onGlass.secondary.solidBarBottom | color.onGlass.secondary | color.solid.barBottom | both | 4.5 |
| text.onGlass.primary.solidTopBar | color.onGlass.primary | color.solid.topBar | both | 4.5 |
| text.onGlass.secondary.solidTopBar | color.onGlass.secondary | color.solid.topBar | both | 4.5 |
| text.onGlass.primary.solidSheet | color.onGlass.primary | color.solid.sheet | both | 4.5 |
| text.onGlass.secondary.solidSheet | color.onGlass.secondary | color.solid.sheet | both | 4.5 |
| text.onGlass.primary.solidDialog | color.onGlass.primary | color.solid.dialog | both | 4.5 |
| text.onGlass.secondary.solidDialog | color.onGlass.secondary | color.solid.dialog | both | 4.5 |
| text.primary.tooltip | color.text.primary | color.solid.tooltip | both | 4.5 |
| text.secondary.tooltip | color.text.secondary | color.solid.tooltip | both | 4.5 |
| text.status.within.tooltip | color.status.within | color.solid.tooltip | both | 4.5 |
| text.status.moved.tooltip | color.status.moved | color.solid.tooltip | both | 4.5 |
| text.status.neutral.tooltip | color.status.neutral | color.solid.tooltip | both | 4.5 |
| large.value.surface1 | color.text.primary | color.surface.1 | both | 4.5 |
| graphic.band.fill.surface1 | color.band.fill | color.surface.1 | both | 3.0 |
| graphic.marker.within.fill | color.marker.within | color.band.fill | both | 3.0 |
| graphic.marker.within.surface1 | color.status.within | color.surface.1 | both | 3.0 |
| graphic.marker.moved.track | color.status.movedGlyph | color.control.track | both | 3.0 |
| graphic.marker.moved.surface1 | color.status.movedGlyph | color.surface.1 | both | 3.0 |
| graphic.marker.moved.surface3 | color.status.movedGlyph | color.surface.3 | both | 3.0 |
| graphic.marker.moved.background | color.status.movedGlyph | color.background | both | 3.0 |
| graphic.marker.neutral.track | color.status.neutral | color.control.track | both | 3.0 |
| graphic.chart.point.surface1 | color.chart.point | color.surface.1 | both | 3.0 |
| graphic.learningOutline.surface1 | color.band.learningOutline | color.surface.1 | both | 3.0 |
| graphic.outline.surface1 | color.outline | color.surface.1 | both | 3.0 |
| graphic.outline.surface2 | color.outline | color.surface.2 | both | 3.0 |
| graphic.outline.background | color.outline | color.background | both | 3.0 |
| graphic.primary.surface1 | color.primary | color.surface.1 | both | 3.0 |
| graphic.primary.background | color.primary | color.background | both | 3.0 |
| graphic.navIcon.bar | color.onGlass.secondary | color.glass.bar | both | 3.0 |
| graphic.roundButton.glyph | color.onGlass.primary | color.control.convexBottom | both | 3.0 |
| graphic.onPrimary.primary | color.onPrimary | color.primary | both | 3.0 |
| focus.ring.background | color.focus.ring | color.background | both | 3.0 |
| focus.ring.surface1 | color.focus.ring | color.surface.1 | both | 3.0 |
| focus.ring.surface3 | color.focus.ring | color.surface.3 | both | 3.0 |
| focus.ring.glassBar | color.focus.ring | color.glass.bar | both | 3.0 |
| focus.ring.glassSheet | color.focus.ring | color.glass.sheet | both | 3.0 |
| focus.ring.glassDialog | color.focus.ring | color.glass.dialog | both | 3.0 |
| focus.ring.solidBarBottom | color.focus.ring | color.solid.barBottom | both | 3.0 |

Not in the table on purpose: `separator`, `chevron`, `chart.grid`, `chart.bandArea`, `glass.rim*`,
`control.switchOff` (canon exception, section 2.1), `control.convex*`, `accentGlow`, `glow.neutral`, `state.*`,
`sheetHandle` and `scrim` are decorative or are backed by a checked boundary (band fill vs card, labels, text
summary). `glass.topBarDim` carries no text. The large value pair is held to 4.5 although large text would allow
3.0 [decision: values are the most important text]. `graphic.onPrimary.primary` covers white glyphs and the
switch knob on the canon accent (3.38 dark); white *text* only sits on `primaryFill`.

The model's limits: the worst case composites the tint over pure white and pure black in sRGB, which is stricter
than any real blurred backdrop; Haze may add extra blend layers that the script does not model. The component
catalog still renders every glass component over a busy synthetic backdrop for a visual check, and the screenshot
tests sample rendered text and backing pixels on device where blur is available [hypothesis: sampling approach to
be designed with the screenshot tests].

---

## 3. Typography

System font only (`FontFamily.Default`: Roboto or the device's system sans, which also covers Cyrillic); no
bundled or vendor fonts. Hierarchy comes from size, weight and grey level, as in the references [measured].
**Weights 400 / 500 / 700 only** (600 renders near-bold on the system sans and is the Material signature).
Sizes and line heights are in sp, so both scale; Android 14+ non-linear font scaling compresses the largest sizes.
Letter spacing 0 everywhere; no all-caps (Russian labels get long). `includeFontPadding = false`,
`LineHeightStyle(alignment = Center, trim = None)`. Minimum size 12 sp, with one recorded exception (`navLabel`).

Numeric styles use `fontFeatureSettings = "tnum, lnum"` (tabular, lining figures) so values, axis labels and
stats do not jitter when digits change; numbers are formatted per locale (`NumberFormat`), units come from string
resources and use the matching non-numeric style at a smaller size.

| name | size sp | line height sp | weight | figures | use | reference |
|---|---|---|---|---|---|---|
| `type.largeTitle` | 32 | 40 | 700 | | top-level screen title, first item of the scrolling content (no size animation) | Clock large title 32.7 dp bold [measured] |
| `type.headline` | 22 | 28 | 500 | | night summary headline | |
| `type.title` | 20 | 26 | 500 | | pane and state-card titles | |
| `type.topBarTitle` | 18 | 24 | 700 | | collapsed top-bar title, 1 line, ellipsis | top bar em 18.7 dp [measured] |
| `type.dialogTitle` | 18 | 24 | 700 | | sheet and dialog titles, centred | |
| `type.titleSmall` (= `type.itemTitle`) | 16 | 22 | 500 | | row titles, card titles, metric name | row title 16 sp medium [measured] |
| `type.body` | 16 | 24 | 400 | | explanations, state card body, onboarding text | |
| `type.bodySmall` | 14 | 20 | 400 | | chart summary, banner text | |
| `type.summary` | 14 | 19 | 400 | | summaries under row titles (canon 13 sp; 14 for legibility [decision]) | row summary 13-14 sp [measured] |
| `type.value` | 14 | 20 | 400 | | trailing row values (`text.secondary`) | |
| `type.button` | 16 | 22 | 500 | | button labels, text buttons | |
| `type.label` | 14 | 20 | 500 | | chips, segments, tabs | |
| `type.statusLabel` | 13 / 14 | 18 / 20 | 400 / 500 | | status label: within, learning, missing 13/400 grey; moved 14/500 in `status.moved` | |
| `type.sectionHeader` | 12 | 16 | 500 | | grey section header outside the cards (`text.tertiary`) | 12 sp, #8A8A8A dark / #6E6E6E light [measured] |
| `type.subtitleSmall` | 14 | 19 | 400 | | subtitle under the large title (`text.tertiary`) | Clock subtitle #8A8A8A [measured] |
| `type.labelSmall` | 12 | 16 | 500 | | weekday letters | |
| `type.navLabel` | 11 | 14 | 500 / 700 | | navigation labels: 500 unselected, 700 selected | 10 sp canon; 11 sp, below our 12 sp minimum, recorded exception (the icon and the content description double it); never capped, wraps to 2 lines |
| `type.caption` | 12 | 16 | 400 | | footnotes, disclaimer, "updated" times | |
| `type.numeric.display` | 48 | 56 | 500 | tnum | Metric detail header value | |
| `type.numeric.value` | 32 | 38 | 500 | tnum | last-night value on a tile and in the glance layout (28/34 in `Glance`) | |
| `type.numeric.small` | 15 | 20 | 500 | tnum | stats row figures, tooltip value, history row values, range text numbers | |
| `type.numeric.axis` | 12 | 16 | 400 | tnum | chart axis labels | |
| `type.unit` | 16 | 22 | 400 | | unit next to `numeric.value` (13/18 next to `numeric.small`) | |

WCAG "large text" (>= 24 sp regular or >= 19 sp at 700): only `largeTitle`, `numeric.display` and `numeric.value`
qualify; everything else is checked at 4.5:1 (and the values too, section 2.10).

At 200 % font scale (`principles.md` 8): rows and tiles grow, labels wrap (max 2 lines for nav labels, the bar
grows), values never ellipsize; in tiles narrower than value + unit, the unit wraps below the value.

## 4. Spacing

Base grid 4 dp with a 2 dp half step for hairline-adjacent details.

| name | dp | name | dp |
|---|---|---|---|
| `space.0` | 0 | `space.6` | 20 |
| `space.1` | 2 | `space.7` | 24 |
| `space.2` | 4 | `space.8` | 32 |
| `space.3` | 8 | `space.9` | 40 |
| `space.4` | 12 | `space.10` | 48 |
| `space.5` | 16 | `space.11` | 64 |

Semantic spacing (window-level gutters and pane widths are owned by `layouts.md` 3.3 and only repeated here):

| name | value | reference |
|---|---|---|
| `gutter.compact` / `.medium` / `.large` / `.glance` | 16 / 24 / 32 / 12 dp | 16 dp side margin [measured] |
| `card.padding` | 16 dp horizontal, 16 dp vertical for content cards; 0 vertical for list cards (the rows carry it) | 16.5 dp inner padding [measured] |
| `row.paddingVertical` / min height | 10 dp / 48 dp (grows with text; 48 dp = `target.min`) | 10.2 dp, 48.3 dp rows [measured] |
| `row.paddingHorizontal` | 16 dp; title -> summary 2 dp | [measured] |
| `card.gap` | 12 dp between homogeneous items (tiles, history months) | 11.9 dp (Clock) [measured] |
| `group.gap` | 16 dp between distinct cards and groups | 16.1 dp [measured] |
| section header row | 24 dp above its text (incl. the 16 dp group gap), 8 dp below; text starts 32 dp from the screen edge (16 dp inside the gutter); card-to-card across a header 54 dp | 54.2 dp, text x 32.6 dp [measured] |
| divider | `max(1 px, 0.33 dp)`, inset 16 dp at start and end (start 16 + icon + 16 with a leading icon) | 1 px #484848 inset 16.5 dp [measured] |
| `content.bottomClearance` | 88 dp (+ the navigation inset when it exceeds 16 dp) | `layouts.md` 4.1 |
| round shell button | 40 dp visual, 48 dp target, 16 dp from the window edge, 20 dp glyph with a 1.5 dp stroke | 40.2 dp, left 15.7 dp [measured] |
| glass rim | 0.5 dp top, 1 dp bottom stroke + 2 dp bottom reflection band | [measured] |
| `icon.size` | 24 dp (standard), 20 dp (inline with text), status dot 8 / triangle 10 / ring 12 dp, summary glyph 28 dp without a container | 24 dp nav icon [measured] |
| `hinge.clearance` | 12 dp on each side of a separating fold | `layouts.md` |

Shell geometry (bar 56 dp, items 70 x 48 dp, offset 16 dp, rail 56 dp, toolbar 52 dp, title start 66 / 16 dp,
sheet header 56 dp, dialog 392 dp / 24 dp padding) is specified in `coloros-conformance.md` 4.3 and applied with
the shell components (`ShellTokens.kt`).

Component token files [decision, integration sweep]: `NbColors`, `NbTypography`, `NbShapes` and `NbDimens` are the
single source for every value listed in this document. The component files `ShellTokens.kt` (shell),
`ControlTokens.kt` (controls, lists, buttons) and `DataTokens.kt` (cards, band, chart, period selector) keep only
component-specific values (for example the nav label colours, the radio ring, the period thumb gradient); every value
they share with this document is read from the foundations, never repeated as a literal. The dialog confirm pill and
`PrimaryButton` use the same accent gradient (`primaryFill` -> a darker bottom, white label >= 4.5:1). The app has no
Material dependency: text and icons are `NbText` / `NbIcon` on Compose foundation, and the theme provides
`LocalIndication` (no ripple) and `LocalOverscrollFactory` itself.

## 5. Shapes

Radii are calibrated to the references: one content radius ≈ 20 dp for every card, bars and controls capsules
[measured]. **Concentric rule**: an inner shape's radius = outer radius - inset between the edges, never below
12 dp for controls (`NbShapes.concentric`), e.g. 44 dp buttons 16 dp inside a 30 dp dialog get a full pill since
their own height wins; a 20 dp card inside a 32 dp sheet at 12 dp inset keeps 20 dp.

| name | radius | smoothing | use |
|---|---|---|---|
| `shape.xs` | 4 dp | none | chart tooltip pointer, smallest details |
| `shape.s` | 8 dp | none | small icon containers |
| `shape.m` | 12 dp | none | small elements only |
| `shape.popup` | 16 dp | 0.6 | chart tooltip, popups |
| `shape.l` | 20 dp | 0.55 | cards, tiles, chart card, settings groups, banners, state cards, night summary |
| `shape.xl` | 28 dp | 0.6 | onboarding illustration only |
| `shape.sheet` | 32 dp (top corners only) | 0.6 | bottom sheets |
| `shape.dialog` | 30 dp | 0.6 | dialogs [owner decision 2026-11-26] |
| `shape.smoothPill` | h / 2 / 1.08 | 0.08 | bottom bar, rail, buttons ("fuller than a circle" near the straight edge [measured]) |
| `shape.pill` | 50 % of the short side | n/a | chips, switch, segmented track and thumb, range band track |
| `shape.circle` | 50 % | n/a | value markers, round shell buttons, radio |

**Squircle decision** [decision]: radii >= 16 dp use continuous ("smoothed") corners, drawn by our own
`SmoothCornerShape` (cubic curves per corner, path cached per size, convex so platform shadows still work on API
28-29). Smoothing below 16 dp is invisible at phone densities, so small radii stay circular (cheaper). If profiling
shows path cost in scrolling lists, tiles fall back to `RoundedCornerShape(20.dp)` without changing size (visual
difference under 1 px) [hypothesis: cost not measured yet].

Content inside cards is padded, not clipped, so smoothed shapes are drawn as backgrounds and borders; only the
chart canvas clips, with a plain rounded rectangle.

## 6. Elevation and shadows

The references use no shadows on cards (dark on black, light white on #F0F1F2) and soft shadows under shell
controls and the round buttons [measured]. Shadows are drawn with Compose `Modifier.dropShadow` (offset y, blur
radius, colour), identical in glass and solid modes.

| name | light | dark | use |
|---|---|---|---|
| `elevation.flat` | none | none | everything inside cards |
| `elevation.card` | none | none | cards and tiles: separated by the background step |
| `elevation.shell` | y 4 dp, blur 12 dp, black 10 % | y 4 dp, blur 10 dp, black 35 % | floating bar, rail |
| `elevation.control` | y 3 dp, blur 8 dp, black 10 % | y 3 dp, blur 8 dp, black 20 % | convex round shell buttons |
| `elevation.raised` | y 4 dp, blur 16 dp, black 12 % | y 4 dp, blur 16 dp, black 50 % | tooltip, dragged handle |
| `elevation.overlay` | y 0, blur 24 dp, black 12 % | y 0, blur 24 dp, black 30 % | sheets, dialogs (above `scrim`) |
| `elevation.accentGlow` | `#0068E0` 25 %, y 4 dp, blur 12 dp | `#428AF7` 35 %, y 4 dp, blur 12 dp | glow under the accent call-to-action (measured FAB) |

Dark relies on the rim, not the shadow, to separate the shell (shadows barely show on black).

## 7. Materials

In the spirit of ColorOS 17's Luminous Effect (`docs/research/coloros17-glass.md`): blur + static per-theme tint +
noise + rim + soft shadow on the shell only. No refraction, no reflection, no motion specular, no backdrop
luminance sampling, no `haze-glass` in 0.1.0; cards, charts, tooltip and the period selector stay solid.

### 7.1 Glass (EffectsMode.Glass)

Implemented with Haze (`dev.chrisbanes.haze:haze` + `haze-blur` 2.1.0, ADR 0003): the content root is a
`hazeSource`; each shell component is a `hazeEffect` with a `HazeStyle(tint, blurRadius, noiseFactor)` built from
these tokens, plus our own rim and shadow. Text never sits on the blur without the tint (section 2.5).

| name | component | blur radius dp | tint | noise | rim | shadow | reference |
|---|---|---|---|---|---|---|---|
| `material.glass.bar` | floating bottom bar | 24 | `glass.bar` | 3 % | 0.5 dp `rimTop`, 1 dp `rimBottom` + 2 dp bottom band | `elevation.shell` | Iris bar sigma 25 px @ 480 dpi ≈ 8.3 dp [Iris] |
| `material.glass.rail` | floating navigation rail | 24 | `glass.bar` | 3 % | as bar | `elevation.shell` | same as bar |
| `material.glass.topBar` | top bar, only once content scrolls under it | progressive: 0 at the toolbar's bottom edge -> 12 under the status bar | darkening `glass.topBarDim` 0.35 -> 0; `glass.topBar` under the small title once it is visible (section 2.5); tint distance 10 dp | 0 | none, no divider | none | progressive blur and darkening to ≈ x0.7 under the status bar, sigma ≈ 0.8 -> 4.5 dp [measured] |
| `material.glass.sheet` | bottom sheet | 40 | `glass.sheet` | 3 % | 0.5 dp `rimTop` on the top edge only | `elevation.overlay` | Iris sheet ≈ 22 dp sigma [Iris] |
| `material.glass.dialog` | dialog | 32 | `glass.dialog` | 3 % | none | `elevation.overlay` | Iris dialog ≈ 11 dp sigma [Iris] |

Blur radius unit [hypothesis]: the values are Haze `blurRadius` in dp. Android's blur radius maps to a Gaussian
sigma of about `0.58 x radius` (Skia's radius-to-sigma conversion), so 24 dp ≈ 14 dp sigma. Calibrate once on the
owner's phone against the system bar look and adjust these numbers only. Noise is generated by Haze at runtime (no
texture file); it matches the fine grain seen on the system's translucent surfaces [measured].

### 7.2 Solid (always for data; fallback for the shell)

| name | fill | rim | shadow | used for |
|---|---|---|---|---|
| `material.solid.card` | `surface.1` | none | `elevation.card` | cards, tiles, chart card, settings groups, state cards |
| `material.solid.bar` | vertical gradient `solid.barTop` -> `solid.barBottom` | same rim tokens as glass | `elevation.shell` | bar and rail in fallback |
| `material.solid.convex` | vertical gradient `control.convexTop` -> `control.convexBottom` | `control.convexRimTop` 0.5 dp / `control.convexRimBottom` 1 dp | `elevation.control` | round back / action buttons in both modes (never blurred) |
| `material.solid.topBar` | `solid.topBar` opaque over status bar + toolbar, fading out over the 16 dp below, no hairline | none | none | top bar in fallback |
| `material.solid.sheet` | `solid.sheet` | same rim as glass sheet | `elevation.overlay` | sheet in fallback |
| `material.solid.dialog` | `solid.dialog` | none | `elevation.overlay` | dialog in fallback |
| `material.solid.tooltip` | `solid.tooltip` | none | `elevation.raised` | chart tooltip, both modes (it sits over data, so never glass) |
| `material.solid.widget` | `surface.1` with `shape.l` | none | system widget | home-screen widget (RemoteViews cannot blur) |

### 7.3 When the solid fallback activates

One resolver, `resolveEffectsMode()`, runs at the root, observes the inputs below and exposes `EffectsMode` plus the
reason. The first matching rule wins; the reason becomes the Settings summary (`states.md`, "Glass effects").

| # | Condition | API used | Effects | Motion | Settings summary key |
|---|---|---|---|---|---|
| 1 | User chose Settings > Appearance > Glass effects: Off | own preference | Solid | normal | (setting shows "Off") |
| 2 | `Build.VERSION.SDK_INT < 31` | `RenderEffect` needs 31 | Solid | normal | `glass_off_unsupported` |
| 3 | Animations removed: `ANIMATOR_DURATION_SCALE == 0` | `Settings.Global` (observed with a `ContentObserver`) | Solid | reduced (section 9.6) | `glass_off_animations` |
| 4 | Battery saver on | `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` | Solid | reduced | `glass_off_battery` |
| 5 | Reduced transparency or higher contrast requested | `UiModeManager.getContrast() > 0` (API 34+); a platform "reduce transparency" setting where one is exposed through public API [hypothesis: none found yet; the in-app switch covers it] | Solid + 1 dp shell border in `onGlass.primary` at 60 % | normal | `glass_off_transparency` |
| 6 | Weak device | `ActivityManager.isLowRamDevice()`, or `MemoryInfo.totalMem < 3 GiB` | Solid | normal | `glass_off_device` |
| 7 | Measured jank | JankStats: > 5 % janky frames over the first 300 frames with glass visible, in two separate sessions; stored, cleared on app update | Solid | normal | `glass_off_device` |
| 8 | Device hot | `PowerManager.getCurrentThermalStatus() >= THERMAL_STATUS_SEVERE` (temporary, reverts when it cools) | Solid | normal | `glass_off_device` |
| - | otherwise | | Glass | normal | (setting shows "Automatic") |

Rules: a mode change never happens mid-gesture or mid-transition (it waits for the next idle frame) and switches
the shell at once (no motion token: ADR 0011 has none for it). The fallback never changes geometry: sizes,
radii, rims, shadows and positions are the same in both modes, only fills differ (`layouts.md` 1.5). Widgets and
screenshots of the Solid variant use exactly the same component code with `EffectsMode.Solid` injected.

### 7.4 Performance budget (blur in frame budget)

[hypothesis until measured] At most three blurred regions on screen at once (top bar, bar or rail, one sheet or
dialog); the bar and rail blur only their own bounds; the top bar blurs only while content is under it; nothing
inside the content (cards, chart, tooltip) is ever blurred.

Release gate, measured on the owner's phone with permission (macrobenchmark `FrameTimingMetric` plus
`dumpsys gfxinfo`), release build, glass on and solid fallback both measured:

| Item | Target |
|---|---|
| Frame budget | 1 / display refresh rate of the window (8.3 ms at 120 Hz, 11.1 ms at 90 Hz, 16.7 ms at 60 Hz); the owner's refresh rate is read from `Display.getRefreshRate()` during the run [hypothesis: 120 Hz] |
| Dropped frames | 0 frames with `frameOverrunMs > 0` in every scenario below, 3 runs each, outer and inner screen |
| Blur share | GPU time of the blur passes (Haze) <= 25 % of the frame budget, read from the GPU profile of the same runs |
| Scenarios | Home fling scroll (2, 4, 6 metrics); Home tile -> Metric detail morph and predictive back; period switch 7/30/90; chart scrub; History list fling and calendar month swipe; sheet open, drag, dismiss; fold -> unfold and unfold -> fold on the same screen |
| Failure handling | first reduce blur radius and the number of blurred regions (tokens only); if still over budget, the device rule 7 in section 7.3 switches it to Solid |


## 8. Iconography (shape carriers)

Own monochrome line icons (1.75 dp stroke on a 24 dp grid, round caps) or Material Symbols (Apache-2.0); no vendor
icons. Status glyphs are geometric shapes drawn in code, sized by `icon.size`, so they stay crisp and identical in
both themes: filled circle, circle with check, triangle up/down (equilateral, 10 dp in the band at 16 dp tile
height), rounded square with one or two triangles, dotted ring (8 dashes), dash (8 x 2 dp), half-filled circle,
slashed circle. Selected navigation items switch outline -> filled icon and the label weight 500 -> 700 (no
capsule). Back is an arrow, not a chevron; the disclosure chevron is a small 5 x 10 dp glyph in a 12 x 24 dp box.

## 9. Motion

All motion, back, scroll and press behaviour is one framework: `ui/interaction` ([ADR 0011](../adr/0011-interaction-framework.md)).
Every animated change is one scalar p in [0, 1] driven by one spring of the registry below and drawn by a pure pose
function of its kind; screens never animate, they change state and the host the state lives in derives the kind from
the change. Nothing grows above its size; data never overshoots. The tables 9.1 to 9.3 mirror
`ui/interaction/Physics.kt` and are compared with it by `MotionTokensDocTest`: a change on one side without the
other fails the build.

### 9.1 Springs (the registry)

| name | damping | stiffness | role |
|---|---|---|---|
| `Nav` | 1.2 | 860 | every navigation kind: page, tab, pager, swap, sheet, dialog, docked dialog, reveal [measured fit, HYPOTHESIS until a pixel re-measure] |
| `Quick` | 1.0 | 1500 | navigation icon outline -> filled |
| `Select` | 1.0 | 247 | tab underline, period thumb trailing edge, radio fill |
| `SelectLead` | 1.0 | 600 | period thumb leading edge (it stretches towards the new period) |
| `Data` | 1.0 | 400 | band, marker and value changes |
| `Switch` | 0.7 | 247 | switch knob, the one bounce of the canon |
| `PressIn` | 1.0 | 632 | press shrink of compact elements |
| `PressInWide` | 1.0 | 439 | press shrink of wide elements (tiles, bar, full-width buttons) |
| `PressOut` | 0.8 | 380 | press release, scale clamped at 1.0 |
| `HighlightIn` | 1.0 | 1755 | pressed highlight of buttons and tiles appearing |
| `HighlightOut` | 1.0 | 187 | pressed highlight of buttons and tiles fading |
| `RowHighlightIn` | 1.0 | 439 | pressed highlight of a row in a grouped card appearing |
| `RowHighlightOut` | 1.0 | 895 | pressed highlight of a row fading |
| `Overscroll` | 1.0 | 150 | overscroll return (100 px -> 0.5 px in about 0.65 s) |

`Nav` comes from `tools/design/fit-spring.py` over the back recording of `reference-analysis.md` 8.1 (free start
offset, free damping, both layers): rms 2.7 px on the incoming page and 1.7 px on the outgoing page; a linear
coupling of the two layers fits at 15.4 px only, hence the page exponent below. It settles (to `P_EPS`) in 460 ms;
the old page spring (1.0 / 220 on Compose's 0.01 px default threshold) ran about 1 s.

### 9.2 Constants

| name | value | use |
|---|---|---|
| `P_EPS` | 0.001 | settle tolerance of every p (a 1e-3 share of the travel, about 1 px of a page) |
| `OVERSCROLL_EPS_PX` | 0.5 | settle tolerance of the overscroll offset, px |
| `GLOW_IN_MS` | 110 | touch light appears, `easing.standard` (0.2, 0, 0, 1) [Iris] |
| `GLOW_OUT_MS` | 480 | touch light fades, `easing.exit` (0.3, 0, 1, 1) [Iris] |
| `PARALLAX` | 1/3 | share of the width the covered page moves [measured] |
| `DIM` | 0.3 | black dim over the fully covered page, dark theme [measured] |
| `PAGE_GAMMA` | 2.2 | upper page at `W (1 - s^gamma)` when a share s of it is shown [measured fit] |
| `DIALOG_START_SCALE` | 0.8 | a dialog grows from this scale |
| `DIALOG_OPAQUE_AT` | 0.35 | a dialog is opaque from this share of its motion |
| `REVEAL_RISE_DP` | 8 | a revealed element rises this far while it fades in |
| `RADIO_PULSE` | 0.08 | radio fill scale bump `1 + 0.08 sin(pi p)` |
| `SWITCH_STRETCH` | 0.3 | switch knob stretch, x1.3 |
| `SWITCH_STRETCH_PEAK` | 0.347 | where along the travel the stretch peaks |
| `PRESS_SCALE_SMALL` | 0.96 | press scale of 40 dp elements |
| `PRESS_SCALE_LARGE` | 0.98 | press scale of the bar, tiles, full-width buttons |
| `OVERSCROLL_FOLLOW` | 0.5 | share of a pull past the edge the content follows at o = 0 |
| `OVERSCROLL_MAX_DP` | 240 | largest overscroll translation |

The small top-bar title is a pure function of the scroll offset through `easing.title` (0.33, 0, 0.67, 1) over
40 -> 72 dp; the top-bar material is `scroll / 10 dp` clamped to [0, 1]. Neither has a clock of its own.

### 9.3 Kinds and poses

p is 0 at the state being left and 1 at the state being reached. e is the side: +1 the end edge (right in LTR).
s is the shown share of the upper layer of a stack pair: s = p for a push, s = 1 - p for a pop, on the same layers.

| kind | relation it comes from | pose | gesture -> p |
|---|---|---|---|
| Page | one level deeper or shallower in a stack: app routes, settings sub-pages (every layout), the two-pane detail pane's own stack, onboarding steps, the debug catalog | upper `tx = e W (1 - s^2.2)`; lower `tx = -e W/3 s`, dim `0.3 s`; no scale, no fade, no corners | the leaving page stays under the finger: `1 - s^2.2 = u0 + (1 - u0) g` |
| Tab | the top-level sections in bar order (bar, rail, Ctrl+1/2/3, "See all", "See history", Glance "More", back to Home) | the Page geometry, the higher bar index is the upper layer; e from the bar order whatever the swipe edge; the bar and rail stay in place | as Page |
| Pager | neighbours in a sequence: previous / next night (button, swipe, arrow keys), month (buttons, swipe, keys), the History detail pane's night | outgoing `tx = -e p W`, incoming `tx = e (1 - p) W`; newer values come from the end side | (a swipe decides by fling projection, then runs p) |
| Swap | unordered peers in a fixed place: Home's detail pane metric, Settings section in two panes | outgoing alpha `max(0, 1 - 2p)`, incoming `max(0, 2p - 1)` | - |
| Sheet | overlay from the bottom | `ty = (1 - p) H`, scrim alpha p | drag `p = 1 - dy / H`; back `p = 1 - g` |
| Dialog | centred overlay | scale `0.8 + 0.2 p`, alpha `min(1, p / 0.35)`, scrim alpha p | back `p = 1 - g` |
| DockedDialog | dialog docked at the bottom (phone widths) | `ty = (1 - p) H`, alpha `min(1, p / 0.35)` | back `p = 1 - g` |
| Reveal | an element appearing or disappearing in place (banners) | alpha p, height share p, rising 8 dp | - |

Release (R0): a gesture's release continues on the same spring from (p, v), v measured on p. Towards a state that
leaves the screen (a commit, a hide) nothing is capped and the motion ends at its first crossing; towards a visible
rest (a cancel, a sheet returning) v is capped at `omega (zeta + sqrt(zeta^2 - 1)) a`, so it never crosses. Both
settle no later than a tap from rest (ADR 0011, lemmas 1 and 2). Pager swipes and sheet drags are decided like a
fling: the platform decay projects the travel; past half (or the step threshold) it commits.

### 9.4 Press, highlight, glow

- **Press shrink**: 0.96 (side <= 40 dp) ... 0.98 (side >= 150 dp or aspect >= 4), linear between, on `PressIn`
  (`PressInWide` from 0.97); release on `PressOut`, never above 1.0. Rows inside a grouped card only light up.
- Every press, shell controls included, follows the clickable's press interactions: inside a scrolling container it
  starts after the tap timeout and ends as soon as a scroll takes the gesture.
- **Pressed highlight** `state.pressed` under the content on `HighlightIn` / `HighlightOut` (rows on the row springs).
- **Touch glow** on shell items, round shell buttons, the period thumb and primary buttons: radial `glow.neutral`,
  radius up to 140 dp, in `GLOW_IN_MS`, out `GLOW_OUT_MS`.
- **Haptics**: public `HapticFeedbackConstants` only (switch, segment, chart scrub tick, long press).

### 9.5 Scroll and overscroll

- One fling: the platform spline decay (`ScrollableDefaults.flingBehavior()`) on every framework container
  (`nbVerticalScroll`, `nbHorizontalScroll`, `NbLazyColumn`) [decision: ColorOS fling feel is unmeasured].
- Overscroll translates without distortion [measured: `reference-analysis.md` 8.2]: a pull adds
  `0.5 (1 - |o| / 240 dp)` of the leftover (it saturates); a drag away from the edge takes o back 1:1 and scrolls the
  remainder in the same event; a fling is always performed and the velocity the content cannot use seeds the
  `Overscroll` spring 1:1; a finger landing during the return stops it where it is. Content shorter than its
  viewport bounces the same way. None under reduced motion.
- Horizontal gestures inside a vertical scroll claim the pointer only when the finger has moved more than the touch
  slop and more horizontally than vertically at that moment (rule A).

### 9.6 Reduced motion

One rule, at the root: `reduced` when animations are removed (`ANIMATOR_DURATION_SCALE == 0`) or battery saver is on
[decision]. Then every p snaps to its target, press scale and glow are off (the static highlight remains), the
overscroll is off and a back gesture shows nothing and goes back at once. Fling stays on. Other duration scales are
applied by the platform's `MotionDurationScale`; the framework does not scale again.

### 9.7 Not built

- Shared-bounds morphs (Home tile -> Metric, History row -> Night) and pane resize animations on fold: not built;
  their tokens are gone. A fold or unfold re-projects the pages with a snap (ADR 0011 R6).

## 10. Interaction: targets, focus, hover

| name | value |
|---|---|
| `target.min` | 48 x 48 dp for every interactive element, including the `Glance` "More" button, icon-only rail items in `WideShort`, calendar cells and history-strip nights; a visually smaller element gets an invisible extended hit area. With a mouse too |
| `target.spacing` | >= 8 dp between the visible edges of adjacent small targets |
| `focus.ring` | 2 dp stroke in `color.focus.ring`, 2 dp outside the element's shape (radius = element radius + 2 dp; pill stays pill); shown for keyboard / D-pad focus only (`FocusInteraction` from a non-touch source), not after touch |
| `focus.order` | defined per layout in `layouts.md` 10.1 (bottom-bar layouts: top bar -> content -> bar; rail layouts: rail -> top bar -> list pane -> detail pane) |
| `hover` | `state.hover` on solid, `state.glassHover` on glass, pointer icon `Hand` on clickables; chart hover shows `chart.guide` and the tooltip (`layouts.md` 10.3) |
| `tooltip.delay` | 500 ms hover or long-press for icon-only items (labels also in content descriptions) |

## 11. Calibration against the references

| Token | Reference | Ours | Why |
|---|---|---|---|
| Window / card (dark) | #000000 / #1A1A1A [measured] | #000000 / #1A1A1A | match |
| Window / card (light) | #F0F1F2 / #FFFFFF, no card shadow [measured] | #F0F1F2 / #FFFFFF, no shadow | match |
| Secondary text (dark) | #969696 [measured] | #969696 | match; never on fills lighter than `surface.3` (4.59) |
| Secondary text (light) | #757575 [measured] | #666666 | #757575 is 4.07:1 on the window |
| Section header | #8A8A8A dark / #6E6E6E light [measured] | `text.tertiary` #8A8A8A / #6E6E6E | match |
| Hairline, chevron | #484848 / #5F5F5F dark, #E0E0E0 / #BDBDBD light [measured] | same | match |
| Accent | #428AF7 dark, #0078EF light, white label 3.4:1 [measured] | #428AF7 controls, #2A6BE0 label fills, #5C9DFF text (dark); #0068E0 / #0062D6 (light) | button labels and links pass 4.5:1 |
| Card radius | ≈ 20 dp [measured] | 20 dp, smoothing 0.55 | match, continuous corners |
| Bottom bar | 56 dp pill, 16 dp above the edge [measured] | 56 dp pill, 16 dp above the edge on gesture navigation | match |
| Selection in bar | filled icon + bold label, no capsule [measured] | filled icon + 700 label, no capsule | not colour alone: shape and weight carry it |
| Nav label | 10 sp, scaling capped [Iris] | 11 sp, never capped | legibility and 200 % font |
| Switch off track | #505050 dark, #D6D6D6 light, 2.16:1 [measured] | same | canon exception: the knob position carries the state |
| Unchecked radio | #5F5F5F (2.73:1) [measured] | `outline` #767676 (3.83:1) | control boundary reaches 3:1 |
| Disabled | whole item #5F5F5F, 2.7:1 [measured] | labels `text.tertiary` (>= 4.5:1), only control graphics in #5F5F5F | disabled labels still readable |
| Press | shrink + highlight under content, no ripple [Iris, COUI] | 0.96-0.98 shrink, `state.pressed` under content, no ripple | match |
| Overscroll | translate, ≈ 0.7 s return [measured] | `NbOverscrollEffect` | match |
| Back transition | ≈ 470 ms settle, no overshoot, covered page dimmed ≈ 0.33 -> 0 [measured] | `spring.page` 1.0 / 220, dim 0.3 | match |
