# Design tokens

Status: design, stage 3 (before code). Date: 2026-11-25.
Inputs: `principles.md`, `reference-analysis.md` (measurements of the owner's system apps, outer screen, dark theme),
`docs/research/design-coloros17.md` (Iris numbers, Haze recommendation), ADR 0003, `screens.md`, `states.md`,
`layouts.md`. This file is the single source of truth for values; the other design documents only name tokens.

Our own token set in the spirit of ColorOS 17 (Living Flow Design, Luminous Effect). Nothing here is a vendor
resource: no vendor fonts, icons, images or colour files; reference measurements are calibration targets only.

Tags: **[measured]** (from `reference-analysis.md`), **[Iris]** (third-party reverse-engineered numbers, see the
research file), **[decision]** (ours, with the reason), **[hypothesis]** (to verify on device or emulator).

Automatic check: `python3 -I tools/design/contrast_check.py` parses the colour tables (header
`| token | light | dark | ... |`) and the pairs table (header `| pair | fg | bg | theme | min |`) in this file
and fails on any pair below its threshold. Keep both formats exact; see section 2.9. It runs locally today; wiring
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
| `primary`, `accent` | actions, selection, progress | never carry a measurement status |
| `status.*` | within / moved / neutral / info, plus aliases | always with a shape glyph and a label |
| `band.*`, `marker.*`, `chart.*` | range band, value marker, chart | marks must reach 3:1 |
| `glass.*`, `solid.*`, `scrim` | shell materials (glass only on bar, rail, top bar, sheet, dialog) | section 7 |
| `focus.ring` | keyboard focus | 3:1 on every surface incl. glass worst case |

## 2. Colours

### 2.1 Neutrals and text

Dark is calibrated to the measured system apps (true black window, #1A1A1A cards, #E9E9E9 / #969696 text
[measured]), with a faint cool tint of our own and secondary text a step lighter so it also passes 4.5:1 on the
selected surface. Light is our own (no light references were captured).

| token | light | dark | use |
|---|---|---|---|
| `color.background` | `#F1F2F5` | `#000000` | window background, page behind cards |
| `color.surface.1` | `#FFFFFF` | `#1A1B1E` | cards, tiles, chart card, settings groups (level 1) |
| `color.surface.2` | `#F6F7F9` | `#232428` | nested areas inside a card: stats row, explainer box (level 2) |
| `color.surface.3` | `#E9EBF0` | `#2D2E33` | selected row, segmented control track, pressed fill (level 3) |
| `color.text.primary` | `#16171A` | `#ECEDF0` | titles, values, body |
| `color.text.secondary` | `#50535B` | `#A3A6AE` | summaries, units, axis labels, row values |
| `color.text.tertiary` | `#62656D` | `#8E919A` | section headers, captions, disabled labels; only on `background` and `surface.1` |
| `color.separator` | `#DCDEE3` | `#34353A` | 1 px hairline inside cards, inset to the text column; decorative, never the only boundary |
| `color.outline` | `#747883` | `#8A8E98` | essential boundaries: unchecked radio, switch-off track rim, text field, dotted learning band |

### 2.2 Primary and accent

The measured system accent (#428AF7, white on it 3.4:1 [measured]) fails 4.5:1 for button labels. Ours is an own
indigo-blue: darker in light (white label 5.9:1) and light in dark with a dark label [decision].

| token | light | dark | use |
|---|---|---|---|
| `color.primary` | `#2C5BD6` | `#8FB0FF` | primary button fill, links, switch-on track, selected radio |
| `color.onPrimary` | `#FFFFFF` | `#0B1C45` | label and icon on `primary` |
| `color.primaryContainer` | `#DCE5FB` | `#25314F` | selected navigation capsule, selected period, tonal button |
| `color.onPrimaryContainer` | `#12306F` | `#D7E2FF` | label and icon on `primaryContainer` |
| `color.accent` | `#6650C8` | `#B6A8FF` | learning progress (counted segments), onboarding illustration strokes |
| `color.accentGlow` | `#6650C8 @ 18%` | `#B6A8FF @ 22%` | finger-following light on glass items (decorative, never behind text) |

### 2.3 Status

Calm by construction: there is no red and no error colour in the app (`principles.md` 1). Higher and lower share
one hue, the direction is carried by the triangle (`principles.md` 2). Every status has a glyph and a label
(`screens.md` section 6); the glyph is listed here so the token and its non-colour carrier are never separated.

| token | light | dark | glyph that always accompanies it | use |
|---|---|---|---|---|
| `color.status.within` | `#1E7566` | `#74CDB9` | filled circle (metric), circle with check (night) | inside the usual range |
| `color.status.moved` | `#95560F` | `#E8B56E` | triangle(s) in the direction of change; rounded square with 1 or 2 triangles (night) | moved outside the usual range (warm desaturated ochre) |
| `color.status.above` | `= color.status.moved` | `= color.status.moved` | upward triangle, marker right of the band | higher than usual |
| `color.status.below` | `= color.status.moved` | `= color.status.moved` | downward triangle, marker left of the band | lower than usual |
| `color.status.neutral` | `#5B606B` | `#A4A9B4` | dotted ring, dash, half circle, slashed circle | learning, missing, short night, not provided |
| `color.status.learning` | `= color.status.neutral` | `= color.status.neutral` | dotted ring, dotted band outline | learning period, per-metric learning |
| `color.status.missing` | `= color.status.neutral` | `= color.status.neutral` | short dash (no value), slashed circle (not provided) | no value, too few readings, not recorded |
| `color.status.info` | `#3E5C96` | `#A9BDE6` | outline icons: key, cloud with clock, download arrow | data-access problems (permission, sync, Health Connect update) |
| `color.status.within.container` | `#E1F2ED` | `#143029` | | tonal chip / glyph container |
| `color.status.within.onContainer` | `#0F4A3F` | `#B1E8DA` | | label on the within container |
| `color.status.moved.container` | `#F8EAD6` | `#3A2A14` | | tonal chip, `S-SOME` tonal summary |
| `color.status.moved.onContainer` | `#5C3306` | `#F5D3A2` | | label on the moved container |
| `color.status.neutral.container` | `#ECEDF1` | `#2A2C31` | | learning / missing chip |
| `color.status.neutral.onContainer` | `#3A3E47` | `#D2D5DC` | | label on the neutral container |
| `color.status.info.container` | `#E3E9F6` | `#1F2A42` | | state card icon container |
| `color.status.info.onContainer` | `#233C6B` | `#CFDBF5` | | label on the info container |

Notification accent colour (`states.md` 6.1): the light value of `color.status.moved`.

### 2.4 Range band, value marker, chart

The band fill is deliberately quiet (it is the "usual", not news); its boundary is carried by `band.edge` lines,
which reach 3:1 against both the card and the fill. Markers get a 2 dp halo in `marker.halo` so they stay
separable where they overlap the band edge.

| token | light | dark | use |
|---|---|---|---|
| `color.band.fill` | `#DCE4F5` | `#2B3550` | personal range area (tile band, chart stepped area) |
| `color.band.edge` | `#6F7FA6` | `#8794B8` | 1.5 dp low/high edge lines and the median tick |
| `color.band.learningOutline` | `= color.outline` | `= color.outline` | dotted outline where the band will be (learning) |
| `color.marker.halo` | `= color.surface.1` | `= color.surface.1` | 2 dp ring around every value marker |
| `color.chart.point` | `#4A4F5A` | `#B9BDC6` | chart points inside the range (filled circle); learning points are hollow in the same colour |
| `color.chart.grid` | `#E6E8EC` | `#2C2D32` | grid lines (decorative; values are in labels and the text summary) |
| `color.chart.guide` | `= color.text.secondary` | `= color.text.secondary` | vertical selection guide under the finger / pointer |

Marker colours: `status.within` (filled circle), `status.moved` (triangle), `status.neutral` (hollow, learning),
`chart.point` (chart points inside the range).

### 2.5 Glass tints, rim, text on glass

Tints are composited over the blurred backdrop. Their alpha is chosen so that `onGlass.*` text passes 4.5:1 even
if the backdrop were pure white or pure black (section 2.9 checks both). The rim is a 1 dp vertical gradient
stroke drawn inside the shape, plus an outer hairline that keeps the edge visible on a same-coloured backdrop;
calibrated to the measured shell controls (light top rim, slightly brighter bottom edge [measured]).

| token | light | dark | use |
|---|---|---|---|
| `color.glass.bar` | `#F7F8FA @ 80%` | `#1C1D21 @ 78%` | floating bottom bar and navigation rail tint |
| `color.glass.topBar` | `#F1F2F5 @ 82%` | `#0E0E10 @ 74%` | top bar tint (only while content is under it) |
| `color.glass.sheet` | `#F7F8FA @ 90%` | `#1E1F23 @ 88%` | bottom sheet tint |
| `color.glass.dialog` | `= color.glass.sheet` | `= color.glass.sheet` | dialog tint |
| `color.glass.rimTop` | `#FFFFFF @ 90%` | `#FFFFFF @ 16%` | rim gradient, top stop |
| `color.glass.rimMid` | `#FFFFFF @ 40%` | `#FFFFFF @ 5%` | rim gradient, 50 % stop |
| `color.glass.rimBottom` | `#FFFFFF @ 60%` | `#FFFFFF @ 11%` | rim gradient, bottom stop |
| `color.glass.rimOuter` | `#000000 @ 7%` | `#000000 @ 45%` | 0.5 dp outer hairline outside the rim |
| `color.onGlass.primary` | `#111215` | `#F2F3F5` | titles, labels, selected icons on glass |
| `color.onGlass.secondary` | `#3E4047` | `#CDD0D6` | unselected nav icons and labels, secondary text in sheets and dialogs |

The chart tooltip is **not** glass: it floats over data, so it is always `material.solid.tooltip` (section 7.2)
and uses `text.*` [decision, review round 0: glass is reserved for the shell, ADR 0003].

The touch light (`accentGlow`, section 9.5) is composited *under* the tint layer, as part of the backdrop, so the
worst-case contrast model (backdrop pure white or pure black) still bounds every label on a pressed glass item.

Inside a glass sheet or dialog, content cards (for example a settings group or an explainer box) are solid
`surface.1` and use `text.*` again; only text placed directly on the tint uses `onGlass.*`.

### 2.6 Solid fallback for glass, and scrim

Used in `EffectsMode.Solid` (section 7.3), for the home-screen widget and, in both modes, for the chart tooltip. The bar keeps a quiet vertical gradient
(the "lit" look of the measured shell controls: #1B1B1B -> #232323 [measured]) and the same rim tokens as glass, so
the fallback looks finished rather than switched off.

| token | light | dark | use |
|---|---|---|---|
| `color.solid.barTop` | `#FCFCFD` | `#1C1D21` | bar and rail fill, gradient top |
| `color.solid.barBottom` | `#F2F3F6` | `#24252A` | bar and rail fill, gradient bottom |
| `color.solid.topBar` | `= color.background` | `= color.background` | top bar fill (plus `separator` hairline once content scrolls under it) |
| `color.solid.sheet` | `#FFFFFF` | `#1E1F23` | sheet fill |
| `color.solid.dialog` | `= color.solid.sheet` | `= color.solid.sheet` | dialog fill |
| `color.solid.tooltip` | `#FFFFFF` | `#2A2B30` | chart tooltip fill in both effect modes (with `elevation.raised`) |
| `color.scrim` | `#0E1014 @ 32%` | `#000000 @ 60%` | dims content behind sheets and dialogs, both modes |

### 2.7 Focus

| token | light | dark | use |
|---|---|---|---|
| `color.focus.ring` | `#1F4FD1` | `#A9C2FF` | 2 dp keyboard focus ring (section 10) |

### 2.8 State layers (not colours of their own)

| name | value | where |
|---|---|---|
| `state.hover` | `text.primary` at 8 % over the element | solid rows, tiles, chips, buttons |
| `state.pressed` | `text.primary` at 12 % | solid elements (plus press scale, section 9.5) |
| `state.dragged` | `text.primary` at 16 % | chart scrub handle, sheet handle |
| `state.glassHover` | rim alphas x 1.5 and tint alpha + 4 points | glass items (no grey layer on glass) |
| `state.glassPressed` | `accentGlow` radial light under the finger (section 9.5) | glass items |
| `state.disabled` | label in `text.tertiary`, glyph in `outline`, no state layers | disabled controls; a reason text is shown next to the control when one exists |

State layers never reduce the contrast of text below the checked pairs: at 12 % the darkest layer in the light
theme turns `surface.1` into roughly `surface.3`, which is checked.

### 2.9 Contrast pairs (machine-checked)

Format (parsed by `tools/design/contrast_check.py`):

- `pair`: name; the first part is the kind: `text` (normal text, >= 4.5), `large` (text >= 24 sp, or >= 19 sp at
  weight 700; >= 3.0), `graphic` (icons, glyphs, band edges, markers, essential boundaries; >= 3.0), `focus`
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
| text.secondary.background | color.text.secondary | color.background | both | 4.5 |
| text.secondary.surface1 | color.text.secondary | color.surface.1 | both | 4.5 |
| text.secondary.surface2 | color.text.secondary | color.surface.2 | both | 4.5 |
| text.secondary.surface3 | color.text.secondary | color.surface.3 | both | 4.5 |
| text.tertiary.background | color.text.tertiary | color.background | both | 4.5 |
| text.tertiary.surface1 | color.text.tertiary | color.surface.1 | both | 4.5 |
| text.link.surface1 | color.primary | color.surface.1 | both | 4.5 |
| text.link.background | color.primary | color.background | both | 4.5 |
| text.onPrimary | color.onPrimary | color.primary | both | 4.5 |
| text.onPrimaryContainer | color.onPrimaryContainer | color.primaryContainer | both | 4.5 |
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
| text.status.within.onContainer | color.status.within.onContainer | color.status.within.container | both | 4.5 |
| text.status.moved.onContainer | color.status.moved.onContainer | color.status.moved.container | both | 4.5 |
| text.status.neutral.onContainer | color.status.neutral.onContainer | color.status.neutral.container | both | 4.5 |
| text.status.info.onContainer | color.status.info.onContainer | color.status.info.container | both | 4.5 |
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
| text.primary.tooltip | color.text.primary | color.solid.tooltip | both | 4.5 |
| text.secondary.tooltip | color.text.secondary | color.solid.tooltip | both | 4.5 |
| text.status.within.tooltip | color.status.within | color.solid.tooltip | both | 4.5 |
| text.status.moved.tooltip | color.status.moved | color.solid.tooltip | both | 4.5 |
| text.status.neutral.tooltip | color.status.neutral | color.solid.tooltip | both | 4.5 |
| text.navSelected | color.onPrimaryContainer | color.primaryContainer | both | 4.5 |
| large.value.surface1 | color.text.primary | color.surface.1 | both | 4.5 |
| graphic.status.within.container | color.status.within | color.status.within.container | both | 3.0 |
| graphic.status.moved.container | color.status.moved | color.status.moved.container | both | 3.0 |
| graphic.status.neutral.container | color.status.neutral | color.status.neutral.container | both | 3.0 |
| graphic.status.info.container | color.status.info | color.status.info.container | both | 3.0 |
| graphic.band.edge.surface1 | color.band.edge | color.surface.1 | both | 3.0 |
| graphic.band.edge.fill | color.band.edge | color.band.fill | both | 3.0 |
| graphic.marker.within.fill | color.status.within | color.band.fill | both | 3.0 |
| graphic.marker.within.surface1 | color.status.within | color.surface.1 | both | 3.0 |
| graphic.marker.moved.fill | color.status.moved | color.band.fill | both | 3.0 |
| graphic.marker.moved.surface1 | color.status.moved | color.surface.1 | both | 3.0 |
| graphic.marker.neutral.fill | color.status.neutral | color.band.fill | both | 3.0 |
| graphic.chart.point.surface1 | color.chart.point | color.surface.1 | both | 3.0 |
| graphic.chart.point.fill | color.chart.point | color.band.fill | both | 3.0 |
| graphic.learningOutline.surface1 | color.band.learningOutline | color.surface.1 | both | 3.0 |
| graphic.outline.surface1 | color.outline | color.surface.1 | both | 3.0 |
| graphic.outline.surface2 | color.outline | color.surface.2 | both | 3.0 |
| graphic.outline.background | color.outline | color.background | both | 3.0 |
| graphic.accent.surface1 | color.accent | color.surface.1 | both | 3.0 |
| graphic.primary.surface1 | color.primary | color.surface.1 | both | 3.0 |
| graphic.navIcon.bar | color.onGlass.secondary | color.glass.bar | both | 3.0 |
| focus.ring.background | color.focus.ring | color.background | both | 3.0 |
| focus.ring.surface1 | color.focus.ring | color.surface.1 | both | 3.0 |
| focus.ring.surface3 | color.focus.ring | color.surface.3 | both | 3.0 |
| focus.ring.glassBar | color.focus.ring | color.glass.bar | both | 3.0 |
| focus.ring.glassSheet | color.focus.ring | color.glass.sheet | both | 3.0 |
| focus.ring.solidBarBottom | color.focus.ring | color.solid.barBottom | both | 3.0 |
| focus.ring.primaryContainer | color.focus.ring | color.primaryContainer | both | 3.0 |

Not in the table on purpose: `separator`, `chart.grid`, `band.fill` against the card, `glass.rim*`, `accentGlow` and
`scrim` are decorative or are backed by a checked boundary (band edges, labels, text summary). The large value pair
is held to 4.5 although large text would allow 3.0 [decision: values are the most important text].

The model's limits: the worst case composites the tint over pure white and pure black in sRGB, which is stricter
than any real blurred backdrop; Haze may add extra blend layers that the script does not model. The component
catalog still renders every glass component over a busy synthetic backdrop for a visual check, and the screenshot
tests sample rendered text and backing pixels on device where blur is available [hypothesis: sampling approach to
be designed with the screenshot tests].

---

## 3. Typography

System font only (`FontFamily.Default`: Roboto or the device's system sans, which also covers Cyrillic); no
bundled or vendor fonts. Hierarchy comes from size, weight and grey level, as in the references [measured].
Sizes and line heights are in sp, so both scale; Android 14+ non-linear font scaling compresses the largest sizes.
Letter spacing 0 everywhere; no all-caps (Russian labels get long). `includeFontPadding = false`,
`LineHeightStyle(alignment = Center, trim = None)`. Minimum size 12 sp.

Numeric styles use `fontFeatureSettings = "tnum, lnum"` (tabular, lining figures) so values, axis labels and
stats do not jitter when digits change; numbers are formatted per locale (`NumberFormat`), units come from string
resources and use the matching non-numeric style at a smaller size.

| name | size sp | line height sp | weight | figures | use | reference |
|---|---|---|---|---|---|---|
| `type.largeTitle` | 28 | 34 | 700 | | top-level screen title at rest (collapses to `type.title`) | Clock large title ≈ 24 sp+ [measured] |
| `type.headline` | 22 | 28 | 600 | | night summary headline, state card title | |
| `type.title` | 20 | 26 | 600 | | collapsed top bar title, pane titles, sheet and dialog titles | top bar ≈ 14 sp in references; larger here for the half-awake glance [decision] |
| `type.titleSmall` | 16 | 22 | 600 | | card titles, metric name in a tile, row titles | row title ≈ 16 sp medium [measured] |
| `type.body` | 16 | 24 | 400 | | explanations, state card body, onboarding text | |
| `type.bodySmall` | 14 | 20 | 400 | | summaries under row titles, chart summary, banner text | row summary ≈ 13-14 sp [measured] |
| `type.label` | 14 | 20 | 600 | | buttons, status chips, period selector | |
| `type.labelSmall` | 12 | 16 | 600 | | navigation labels, section headers, weekday letters | section header ≈ 12 sp [measured]; nav label ≈ 10-11 sp, raised to 12 for legibility [decision] |
| `type.caption` | 12 | 16 | 400 | | footnotes, disclaimer, "updated" times | |
| `type.numeric.display` | 48 | 56 | 500 | tnum | Metric detail header value | |
| `type.numeric.value` | 32 | 38 | 500 | tnum | last-night value on a tile and in the glance layout (28/34 in `Glance`) | |
| `type.numeric.small` | 15 | 20 | 500 | tnum | stats row figures, tooltip value, history row values, range text numbers | |
| `type.numeric.axis` | 12 | 16 | 400 | tnum | chart axis labels | |
| `type.unit` | 16 | 22 | 400 | | unit next to `numeric.value` (13/18 next to `numeric.small`) | |

WCAG "large text" (>= 24 sp regular or >= 19 sp at 700): only `largeTitle`, `numeric.display` and `numeric.value`
qualify; everything else is checked at 4.5:1 (and the values too, section 2.9).

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
| `card.padding` | 16 dp horizontal, 16 dp vertical | 16.5 dp inner padding [measured] |
| `row.paddingVertical` | 12 dp (row min height 56 dp, grows with text) | 10.2 dp at font 1.35, 48 dp rows [measured]; 56 dp for touch comfort [decision] |
| `card.gap` | 12 dp between tiles and list cards | 11.9 dp (Clock) [measured] |
| `group.gap` | 16 dp between groups; section header row 8 dp above / 8 dp below its text, 16 dp above the row | [measured] |
| `content.bottomClearance` | bar height + bar offset + 16 dp | `layouts.md` 4.1 |
| `icon.size` | 24 dp (standard), 20 dp (inline with text), 16 dp (status glyph in chips), 40 dp (summary card glyph in a 56 dp container) | 24 dp nav icon [measured] |
| `hinge.clearance` | 12 dp on each side of a separating fold | `layouts.md` |

## 5. Shapes

Radii are calibrated to the references: cards ≈ 20 dp, bars and controls full pill [measured].

| name | radius | smoothing | use |
|---|---|---|---|
| `shape.xs` | 4 dp | none | chart tooltip pointer, median tick ends, smallest nested boxes |
| `shape.s` | 8 dp | none | calendar day cell, small icon containers |
| `shape.m` | 12 dp | none | inline banner, explainer box nested in a card, tooltip |
| `shape.l` | 20 dp | 0.6 | cards, tiles, chart card, settings groups, state cards |
| `shape.xl` | 28 dp | 0.6 | night summary hero card, onboarding illustration card |
| `shape.sheet` | 32 dp (top corners only) | 0.6 | bottom sheets (`screens.md`) |
| `shape.dialog` | 32 dp | 0.6 | dialogs (`screens.md`) |
| `shape.pill` | 50 % of the short side | n/a | bottom bar, rail, buttons, chips, switch, period selector, selection capsule, range band track |
| `shape.circle` | 50 % | n/a | value markers, glyph containers, icon buttons, radio |

**Squircle decision** [decision]: radii >= 16 dp use continuous ("smoothed") corners with smoothing 0.6, drawn by
our own `SmoothCornerShape` (cubic curves per corner, path cached per size, convex so platform shadows still work
on API 28-29). Reason: the reference bottom bar's corner profile is slightly fuller than a circle near the
straight edge, consistent with smoothing (not resolvable reliably [measured]), and Iris uses ≈ 0.5-0.6 [Iris];
smoothing below 16 dp is invisible at phone densities, so small radii stay circular (cheaper). Pills are exact
semicircles. If profiling shows path cost in scrolling lists, tiles fall back to `RoundedCornerShape(20.dp)`
without changing size (visual difference under 1 px) [hypothesis: cost not measured yet].

Nested radius rule: inner radius = outer radius - inset, never below 4 dp (a 12 dp box inside a 20 dp card with
16 dp padding uses `shape.s`). Content inside cards is padded, not clipped, so smoothed shapes are drawn as
backgrounds and borders; only the chart canvas clips, with a plain rounded rectangle.

## 6. Elevation and shadows

The references use no shadows on cards over black and soft shadows under shell controls [measured]. Shadows are
drawn with Compose `Modifier.dropShadow` (offset y, blur radius, colour), identical in glass and solid modes.

| name | light | dark | use |
|---|---|---|---|
| `elevation.flat` | none | none | cards and tiles in dark; everything inside cards |
| `elevation.card` | y 1 dp, blur 3 dp, black 6 % | none | cards and tiles in light (separation from `#F1F2F5`) |
| `elevation.shell` | y 6 dp, blur 20 dp, black 10 % | y 4 dp, blur 16 dp, black 50 % | floating bar, rail |
| `elevation.raised` | y 4 dp, blur 12 dp, black 12 % | y 4 dp, blur 12 dp, black 50 % | tooltip, dragged handle |
| `elevation.overlay` | y 12 dp, blur 32 dp, black 14 % | y 12 dp, blur 32 dp, black 60 % | sheets, dialogs (above `scrim`) |

Dark relies on the rim, not the shadow, to separate the shell (shadows barely show on black).

## 7. Materials

### 7.1 Glass (EffectsMode.Glass)

Implemented with Haze (`dev.chrisbanes.haze:haze` + `haze-blur` 2.1.0, ADR 0003): the content root is a
`hazeSource`; each shell component is a `hazeEffect` with a `HazeStyle(tint, blurRadius, noiseFactor)` built from
these tokens, plus our own rim and shadow. No refraction or dispersion (`haze-glass` only after on-device
verification). Text never sits on the blur without the tint (section 2.5).

| name | component | blur radius dp | tint | noise | rim | shadow | reference |
|---|---|---|---|---|---|---|---|
| `material.glass.bar` | floating bottom bar | 24 | `glass.bar` | 3 % | 1 dp gradient `rimTop` -> `rimMid` -> `rimBottom` + 0.5 dp `rimOuter` | `elevation.shell` | Iris bar sigma 25 px @ 480 dpi ≈ 8.3 dp [Iris] |
| `material.glass.rail` | floating navigation rail | 24 | `glass.bar` | 3 % | as bar | `elevation.shell` | same as bar |
| `material.glass.topBar` | top bar, only once content scrolls under it | progressive: 0 at the bar's bottom edge -> 20 under the status bar | `glass.topBar`, alpha driven by scroll offset: full once content has moved 8 dp under the bar's bottom edge, i.e. before it reaches the title text | 0 | none (bottom edge is a 0.5 dp `separator` line when scrolled) | none | progressive blur and darkening to ≈ x0.7 under the status bar, sigma ≈ 0.8 -> 4.5 dp [measured]; Iris top bar ≈ 6.7 dp [Iris] |
| `material.glass.sheet` | bottom sheet | 40 | `glass.sheet` | 3 % | 1 dp gradient on the top edge and corners | `elevation.overlay` | Iris sheet ≈ 22 dp sigma [Iris] |
| `material.glass.dialog` | dialog | 32 | `glass.dialog` | 3 % | 1 dp gradient all round | `elevation.overlay` | Iris dialog ≈ 11 dp sigma [Iris] |

Blur radius unit [hypothesis]: the values are Haze `blurRadius` in dp. Android's blur radius maps to a Gaussian
sigma of about `0.58 x radius` (Skia's radius-to-sigma conversion), so 24 dp ≈ 14 dp sigma; this is deliberately a
little stronger than Iris's bar so small labels under the bar never read through. Calibrate once on the owner's
phone against the system bar look and adjust these numbers only (no other token changes). Noise is generated by
Haze at runtime (no texture file); it matches the fine grain seen on the system's translucent surfaces [measured]
and hides gradient banding.

Over the backdrop: the glass shell sits over solid content, so in practice the blurred backdrop is cards and the
window background; the worst-case contrast model (section 2.9) does not rely on that.

### 7.2 Solid (always for data; fallback for the shell)

| name | fill | rim | shadow | used for |
|---|---|---|---|---|
| `material.solid.card` | `surface.1` | none | `elevation.card` | cards, tiles, chart card, settings groups, state cards |
| `material.solid.nested` | `surface.2` | none | none | areas inside cards |
| `material.solid.bar` | vertical gradient `solid.barTop` -> `solid.barBottom` | same rim tokens as glass | `elevation.shell` | bar and rail in fallback |
| `material.solid.topBar` | `solid.topBar` (+ `separator` hairline when scrolled) | none | none | top bar in fallback |
| `material.solid.sheet` | `solid.sheet` | same rim as glass sheet | `elevation.overlay` | sheet in fallback |
| `material.solid.dialog` | `solid.dialog` | same rim as glass dialog | `elevation.overlay` | dialog in fallback |
| `material.solid.tooltip` | `solid.tooltip` | 1 dp `separator` outline | `elevation.raised` | chart tooltip, both modes (it sits over data, so never glass) |
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
| 5 | Reduced transparency or higher contrast requested | `UiModeManager.getContrast() > 0` (API 34+); a platform "reduce transparency" setting where one is exposed through public API [hypothesis: none found yet; the in-app switch covers it] | Solid | normal | `glass_off_transparency` |
| 6 | Weak device | `ActivityManager.isLowRamDevice()`, or `MemoryInfo.totalMem < 3 GiB` | Solid | normal | `glass_off_device` |
| 7 | Measured jank | JankStats: > 5 % janky frames over the first 300 frames with glass visible, in two separate sessions; stored, cleared on app update | Solid | normal | `glass_off_device` |
| 8 | Device hot | `PowerManager.getCurrentThermalStatus() >= THERMAL_STATUS_SEVERE` (temporary, reverts when it cools) | Solid | normal | `glass_off_device` |
| - | otherwise | | Glass | normal | (setting shows "Automatic") |

Rules: a mode change never happens mid-gesture or mid-transition (it waits for the next idle frame) and crossfades
the shell over `motion.duration.short` (instant under reduced motion). The fallback never changes geometry: sizes,
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
slashed circle. Selected navigation items switch outline -> filled icon in addition to the capsule and the label
weight 600 -> 700.

## 9. Motion

Springs follow the reverse-engineered range of ColorOS 17 (dampingRatio 0.55-1.0, stiffness 160-1366 [Iris]) and
the measured back transition (exit ≈ 200 ms, entry from ≈ -1/3 width, settle ≈ 470 ms, no overshoot [measured]).
Gesture-driven motion always uses springs and inherits the finger's velocity; data (values, bands, markers) never
overshoots.

### 9.1 Durations (for tweens and fades)

| name | ms | use |
|---|---|---|
| `motion.duration.instant` | 0 | reduced-motion replacement |
| `motion.duration.micro` | 100 | state layer in, icon outline -> filled |
| `motion.duration.short` | 150 | crossfades, top bar tint in, effects-mode switch |
| `motion.duration.medium` | 250 | dialog enter, banner in |
| `motion.duration.reduced` | 120 | screen crossfade that replaces morphs and pushes under reduced motion |
| `motion.duration.long` | 350 | onboarding page change |
| `motion.duration.glowIn` | 110 | touch light appears under the finger [Iris] |
| `motion.duration.glowOut` | 480 | touch light fades after release [Iris] |

### 9.2 Easing

| name | cubic-bezier | use |
|---|---|---|
| `motion.easing.standard` | (0.2, 0, 0, 1) | general property changes |
| `motion.easing.flow` | (0.3, 0, 0.1, 1) | entering elements (dialog, banner, tooltip) [decision: a generic strong-decelerate curve; the same numbers appear in a third-party kit (research file), no code or resource is taken from it] |
| `motion.easing.exit` | (0.3, 0, 1, 1) | leaving elements |
| `motion.easing.linear` | linear | scroll-driven values (large title collapse, top bar tint by scroll offset) |

### 9.3 Springs

| name | dampingRatio | stiffness | use |
|---|---|---|---|
| `motion.spring.page` | 1.0 | 220 | screen push / pop, predictive back release (settles ≈ 470 ms, matches [measured]) |
| `motion.spring.morph` | 0.9 | 380 | shared-bounds morph tile -> Metric detail header, row -> Night detail, list -> pane |
| `motion.spring.selection` | 0.72 | 600 | nav capsule and period capsule moving between items (≤ 4 % overshoot: the "elastic" feel) |
| `motion.spring.press` | 0.6 | 1200 | press scale 1 -> 0.97 and release |
| `motion.spring.follow` | 1.0 | 1500 | anything tracking the finger: chart scrub, capsule drag, sheet drag |
| `motion.spring.sheet` | 0.92 | 420 | sheet settle after drag or open |
| `motion.spring.pane` | 1.0 | 300 | pane resize / list-detail changes on resize, fold and unfold |
| `motion.spring.data` | 1.0 | 400 | band, marker and value changes when the selected night changes (never overshoot) |

### 9.4 Transitions (morphing between screens)

| transition | spec |
|---|---|
| Home tile -> Metric detail | `SharedTransitionLayout`: tile container `sharedBounds` -> header card, radius `shape.l` kept, value text `sharedElement`; other content fades in after 60 % progress; `spring.morph` |
| History row -> Night detail | same pattern, row -> header |
| Push (non-shared) | incoming slides in from the end edge, outgoing moves to -1/3 width (parallax); `spring.page`; no scale, no dimming (as measured). Mirrored in RTL |
| Predictive back | during the gesture the page scales to 0.92 and its corners grow to `shape.xl`; release completes with `spring.page`, cancel returns with `spring.follow` |
| Top-level section switch | crossfade `duration.short`; nav capsule moves with `spring.selection` |
| Sheet | enters with `spring.sheet` from below; exit tween 200 ms `easing.exit`; scrim fades with it |
| Dialog | scale 0.94 -> 1 plus fade, `duration.medium` `easing.flow`; exit fade 150 ms |
| Tooltip | appears at the finger with fade + scale 0.9 -> 1, `duration.short`; position follows with `spring.follow` |
| Learning -> range ready (`B-RANGE-READY`) | dotted band outline morphs into the filled band: dashes close, fill fades in, `spring.data` |
| Fold / unfold / resize | panes animate with `spring.pane`; content keeps its scroll anchor; no reload, no fade of data |

### 9.5 Finger-following feedback

- Press: scale 0.97 with `spring.press` on tiles, rows, buttons and shell items (not on chart points).
- Glass items: a radial `accentGlow` light (radius 48 dp) appears under the finger in `duration.glowIn`, follows
  the finger, fades in `duration.glowOut` after release. Solid elements use `state.pressed` instead.
- Navigation capsule and period capsule follow a horizontal drag across the bar with `spring.follow` and snap to
  the nearest item with `spring.selection`.
- Haptics: public `HapticFeedbackConstants` only (`CLOCK_TICK` on chart point change, `CONFIRM` on snap); no
  vendor constants (ADR 0003).

### 9.6 Reduced motion

Active when `ANIMATOR_DURATION_SCALE == 0` ("Remove animations"), when battery saver is on, or when the system
reports the accessibility "remove animations" option (same setting). With a scale between 0 and 1, durations and
spring settle times are simply scaled by Compose; with 0, these variants replace the normal ones:

| normal | reduced variant |
|---|---|
| shared-bounds morph, push with parallax | crossfade `motion.duration.reduced` (forced to 120 ms by our own clock even at scale 0, so the change of screen stays visible) |
| predictive back scale and corner growth | none; back completes without preview |
| springs with overshoot (`selection`, `press`) | snap to the end state (capsule moves without travel) |
| touch glow, press scale | static `state.pressed` layer only |
| progressive top bar tint by scroll | appears at a fixed scroll threshold, no fade |
| `B-RANGE-READY` morph | static filled band |
| fold/unfold pane animation | instant relayout |

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
| Window / card (dark) | #000000 / #1A1A1A [measured] | #000000 / #1A1B1E | same structure, faint cool tint of our own |
| Secondary text (dark) | #969696 [measured] | #A3A6AE | 4.5:1 on `surface.3` too |
| Accent | #428AF7, white label 3.4:1 [measured] | #2C5BD6 / #8FB0FF | own hue; button labels pass 4.5:1 |
| Card radius | ≈ 20 dp [measured] | 20 dp, smoothing 0.6 | match, continuous corners |
| Bottom bar | 56 dp pill, 16 dp above bottom [measured] | 64 dp pill, nav inset + 12 dp (`layouts.md` 4.1) | 12 sp labels at font 1.0 with room to grow |
| Selection in bar | filled icon + bold label, no capsule [measured] | capsule + filled icon + weight | selection not by colour alone, and a morphing target for Living Flow motion |
| Disabled grey | #5F5F5F, 2.7:1 [measured] | `text.tertiary`, >= 4.5:1 | disabled labels still readable |
| Back transition | ≈ 470 ms settle, no overshoot [measured] | `spring.page` 1.0 / 220 | match |
