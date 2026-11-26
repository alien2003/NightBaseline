# Screen map

Status: draft for stage 3 (design before code). Companion documents: `states.md` (state catalog, copy, notification
spec), `tokens.md` (color, type, shape, material, motion tokens), `layouts.md` (window size classes, postures,
measurements, the single source of layout modes and sizes; where this file and `layouts.md` disagree on geometry,
`layouts.md` wins). Index: `README.md`. Where this document names a token (`color.status.moved`, `material.glass.bar`, ...) the value lives in
`tokens.md`.

Inputs: `docs/research/design-coloros17.md`, `docs/research/large-screens.md`, `docs/research/analogs.md`, ADR 0003,
the algorithm model in `core-algo` (`NightStatus`, `MetricStatus`, `MissingReason`, `AlgoParams`), and measurements
taken from folded-screen reference dumps of system apps (stored outside the repository, `.data/design-refs/`).

## 1. Principles that shape every screen

1. **One question per screen.** Home answers "was last night usual for me?"; Metric detail answers "how does this
   measurement behave over time and why is that its range?"; History answers "which nights were unusual?".
2. **Glass is the shell, data is solid.** Glass (`material.glass.*`, backdrop blur with a light rim) only on: top bar
   (once content scrolls under it), floating bottom bar, navigation rail, bottom sheets, dialogs.
   Every card, band, chart, the chart tooltip, list row and calendar cell sits on a solid surface (`material.solid.*`). Text is
   never placed directly on blurred content: glass surfaces carry a tint layer that guarantees 4.5:1 against the
   worst-case backdrop (checked by the contrast test in `tokens.md`).
3. **Never color alone.** Every status has a shape glyph and a text label (section 6). A grayscale screenshot must
   still tell within / higher / lower / learning / missing apart.
4. **Calm.** No red, no exclamation marks, no sirens. "Moved outside your usual range", "higher than usual",
   "lower than usual". Deviations use `color.status.moved` (a warm, desaturated hue), not an error color. Error colors
   are reserved for nothing in this app; problems with data access use the neutral `color.status.info`.
5. **Stable order.** Metrics always appear in registry display order (`DefaultMetrics.ALL`: sleeping heart rate,
   respiratory rate, SpO2, sleep duration, HRV, skin temperature). Deviating metrics are not moved to the top; the
   night summary names them instead, so tiles never jump between nights.
6. **Pluggable metrics.** Every list/grid of metrics works for 2..6 items. Layouts never assume four.
7. **Same state, any window.** Every screen keeps route, selected metric, selected night, selected period and scroll
   position across fold/unfold, rotation, split screen and resize (section 5).

## 2. Destinations

| Id | Screen | Kind | Saved arguments | Reached from |
|---|---|---|---|---|
| `home` | Home ("Last night") | top level | scroll, selected metric (expanded only) | app launch, nav, notification, widget |
| `history` | History | top level | view mode (feed / calendar), visible month, scroll, selected night (wide only) | nav, Home history strip "See all" |
| `settings` | Settings | top level | scroll | nav |
| `night/{date}` | Night detail | pushed | date, scroll | History row/cell, Home history strip, notification (when not newest) |
| `metric/{metricId}?date&period` | Metric detail | pushed (compact/medium), detail pane (expanded) | metric, anchor date, period (7/30/90), chart selection, scroll | metric tile on Home or Night detail, History filter |
| `range-explainer` | "How your usual range works" | bottom sheet (Compact, CompactShort, Tabletop, Glance); glass dialog or the detail pane from Medium up (`layouts.md` 4.4) | metric | Metric detail info action, Home summary "Learn more", Learning card |
| `onboarding/{step}` | First run (6 steps) | full-screen flow, no nav | step | first launch, Settings > Data source > "Set up again" (`settings_setup_again`) |
| `settings/source` | Source picker | pushed (single pane) / detail pane (Settings list-detail) | - | Settings, onboarding step 3, state actions |
| `settings/sensitivity` | Sensitivity | pushed | - | Settings |
| `settings/about` | About | pushed | scroll | Settings, disclaimer link everywhere |
| `settings/licenses` | Open-source licenses | pushed | scroll | About |
| `debug/catalog` | Component catalog | pushed, debug builds only | section, theme pair, glass toggle | Settings > Debug (debug build) |
| (system) | Health Connect permission UI / HC settings / Play Store page for HC / app notification settings | external intent | - | state actions |

Top-level destinations are exactly three: **Last night**, **History**, **Settings**. Labels are always visible on the
bar and rail. Exceptions: `WideShort` uses an icon-only rail (labels in tooltip and content description) and `Glance`
has no bar at all: a "More" button (`action_more`) opens a glass sheet with labelled History and Settings entries
(`layouts.md` 3.2).

## 3. Navigation graph

```
                      first launch (no completed onboarding)
                                   |
                                   v
 onboarding/welcome -> how-it-works -> source -> permissions -> notifications -> ready
                                                    |   ^                         |
                                (HC missing/outdated)   |(returns from HC UI)      v
                                   v                    |                    +---------+
                         HC install/update (system) ----+                    |  home   |<---- launcher, widget
                                                                             +---------+
                                                                            /  |   |    \
                                           metric tile ------------------- /   |   |     \--- nav ---> settings
                                                                          v    |   |                     | | | |
                                                         metric/{id}?date&period   |                     | | | +-> debug/catalog
                                                                 |  info           |                     | | +---> about -> licenses
                                                                 v                 | history strip       | +-----> sensitivity
                                                         range-explainer (sheet)   v                     +-------> source
                                                                             night/{date} <--- row --- history <--- nav
                                                                                   |
                                                                                   +-- metric tile --> metric/{id}?date={date}

 notification tap --(deep link nightbaseline://night/{date})--> home        if {date} is the newest evaluated night
                                                            \-> night/{date} otherwise (back stack: home, night)
 status notification tap --(nightbaseline://fix/{problem})--> home with the problem state in view
```

Rules:
- Back from any pushed screen returns to its parent; back from a top-level destination other than Home goes to Home;
  back from Home leaves the app. Predictive back is on (targetSdk 36+); the outgoing screen morphs back into the tile
  or row it came from (shared element: tile container -> detail header card).
- Deep links always build a synthetic back stack starting at `home`, so back never leaves the user on an empty task.
- If onboarding is not completed, every deep link opens onboarding at the first unfinished step; the link target is
  kept and opened after "ready".
- Nav bar re-tap on the current destination scrolls to top (and on History: jumps to the newest night).
- Keyboard: the full shortcut and focus-order list is `layouts.md` 10 (Ctrl+1/2/3, Esc, Alt+Left, Left/Right in
  Night detail and in a focused chart, `[`/`]` for the period, ...). `Tab` order follows the visual top-to-bottom,
  start-to-end order. Shortcuts are listed in the Keyboard Shortcuts Helper.

### Deep links

| URI | Opens | Built by |
|---|---|---|
| `nightbaseline://night/{yyyy-MM-dd}` | Home or Night detail (see above), scrolled to the night summary card | night result notification, widget |
| `nightbaseline://metric/{metricId}?date={yyyy-MM-dd}` | Metric detail with that night selected on the chart | widget metric cells (wide widget) |
| `nightbaseline://fix/{permission\|health-connect\|source-sync}` | Home showing the matching state card (`permission`: `E-PERM`, `B-BG`; `health-connect`: `E-HC-UPDATE`; `source-sync`: `B-SOURCE-QUIET`, `E-SOURCE-GONE`) | status notification |

Deep links are internal (explicit `PendingIntent`s to the single activity); no exported browsable intent filter is
needed. The URI form is still fixed here because screenshot tests and the notification code share it.

## 4. Components (inventory)

Shell (glass, fallback solid; same component on every window size):

| Component | Notes |
|---|---|
| `ShellTopBar` | Heights per mode in `layouts.md` 4.3 (Compact: large title about 112 dp collapsing to 56 dp; Medium and up: 64 dp; short windows: 48 dp; all grow with font scale, never clip). Transparent while content is at rest (the title then sits on the solid window background); `material.glass.topBar` once content scrolls under it, with the tint at full strength before content reaches the title (`tokens.md` 7.1). Title, optional back, up to two 48 dp actions. Large-title variant on top-level screens: `type.largeTitle` at rest, `type.title` collapsed. |
| `FloatingNavBar` | `material.glass.bar` pill, centered, hugging its items, bottom = navigation bar inset + 12 dp, height 64 dp at font 1.0 (labels below icons, grows with the label), each item >= 48x48 dp touch target (`layouts.md` 4.1). Selected item: solid tonal capsule (`primaryContainer`) plus filled icon and bold label, morphing (position + width, `spring.selection`) between items and following the finger while dragging along the bar. Measured reference pattern: floating pill 56–77 dp high, 16–22 dp above the screen bottom, not edge-docked. |
| `FloatingNavRail` | `material.glass.rail`, vertical pill 72 dp wide, start edge + 12 dp, vertically centered, icon + label items (icon only in `WideShort`, labelled wide pill in `Large`/`ExtraLarge`). Used in `Medium`, `Expanded`, `Large`, `ExtraLarge`, `WideShort` and `Book` with a wide start pane (`layouts.md` 3.2, 4.2); `CompactShort` keeps the bottom bar. |
| `GlassSheet` | Bottom sheet, `material.glass.sheet`, top corners `shape.sheet` (32 dp), drag handle (48 dp target, also closable with Esc and a labelled close action for TalkBack). Used in Compact, CompactShort, Tabletop (lower half only) and Glance; from Medium up the same content opens as a `GlassDialog` or in the detail pane (`layouts.md` 4.4). |
| `GlassDialog` | Centered, `material.glass.dialog`, `shape.dialog` (32 dp); max width 480 dp for confirmations (sensitivity reset, source change), 560 dp when it hosts sheet content on wide windows. Never across a fold (`layouts.md` 6). |
| `ChartTooltip` | Small **solid** bubble (`material.solid.tooltip`, both effect modes: it floats over data, so it is not glass) that follows the finger or pointer over the chart, with date, value + unit, status glyph and label. |

Content (always solid):

| Component | Purpose |
|---|---|
| `NightSummaryCard` | Hero card: night status glyph, headline (`state_*_title`), body (`state_*_body`, which carries the compared count, e.g. "4 measurements compared with your last 28 nights."), night label ("Night of Nov 24–25"). |
| `MetricRangeTile` | One metric: name, last-night value + unit (largest text), `RangeBand`, `StatusLabel` (shape + words, no background). Variants: within, higher, lower, learning (n of 7), no value tonight (reason), not recorded by source. Whole tile is one 48 dp+ touch target opening Metric detail. |
| `RangeBand` | Slider-like neutral track: personal range as a solid grey segment from low to high (no edge lines, no median tick), value marker whose **shape** encodes status (white dot with a ring inside, amber triangle outside, hollow ring while learning). Values beyond the visible track are clamped to the edge with a chevron. No band while learning: dotted outline of where it will be. |
| `StatusLabel` / `StatusGlyph` | Small shape + words, 6 dp apart, no background or tonal chip: 8 dp dot and grey 13 sp "Usual" inside, 10 dp triangle and amber 14 sp medium label outside, grey ring / dash for learning / missing; see section 6. |
| `HistoryStrip` | Last 7 nights as 7 columns (14 in Medium when width >= 720 dp) in one card: weekday letter (12/400 secondary), night status glyph. The title ("Last 7 nights") is a section header outside the card with "See all" at its end. Each night is a >= 48 dp target -> Night detail; when the strip is narrower than 7 x 48 dp (narrow list panes) or sits in a tabletop top half, the nights are not individually tappable and the whole strip is one target -> History. "See all" (`action_see_all`) -> History. |
| `TrendChart` | Per-night points (no connecting line across missing nights) for 7/30/90 nights, personal range drawn as a band that follows the baseline as it was on each night (stepped area), gaps for nights without value, hollow points for learning nights, status shapes for points outside. Finger-following selection with `ChartTooltip`. |
| `PeriodSelector` | Segmented control 7 / 30 / 90 nights (`Chart.kt`, the app's one segmented control): a solid 40 dp track (content layer, never glass) in a 48 dp target row, segments >= 52 dp; the selected segment sits on a solid convex thumb (vertical gradient + 1 dp top rim + soft shadow, inset 4 dp, concentric) that slides on `spring.selection` (1.0 / 247), its leading edge on a stiffer spring so it stretches toward the target while moving; no tonal capsule. Snaps under reduced motion. |
| `ChartSummary` | Visible text under every chart, also used as the chart's content description (section 8). |
| `StatsRow` (readout card) | One card of 48 dp readout rows split by hairlines: grey label at the start, value at the end; nights inside the range (k of N), usual range, median. |
| `ExplainerCard` | Title + paragraphs + optional "Learn more" (opens `range-explainer`). |
| `LearningProgress` | 7 segments filling in, "N of 7 nights" label, segment shape differs for counted / skipped (short night) / pending. |
| `StateCard` | Shared pattern for every data problem: icon, title, body, primary action button, optional secondary text button. Used inline on Home/Night detail/Metric detail and full-screen when nothing else can be shown. |
| `InlineBanner` | Solid banner (`shape.m`, wraps to more lines at large font) with icon, text and an action, for non-blocking problems shown above still-valid content (e.g. notifications off, last night not in yet). Announced once as a polite live region. |
| `NightRow` | History feed row, one row of a month's grouped 20 dp card: 24 dp night glyph, night label (16/500) and status summary (14/400), small per-metric glyph row at the end (2..6 glyphs, wraps); >= 48 dp, padding 16 / 10, hairline above inset 56 / 16 dp; the selected row on the neutral `state.selected` fill. Lights up on press, does not shrink. |
| `MonthCalendar` | History calendar on one 20 dp card: month title (16/500) between 40 dp round previous / next buttons, weekday letters 12/400, 7 columns of transparent >= 48 dp day cells: number 16/400 in a 32 dp box and the night status glyph below it (shape, not only colour). Today: number in `primaryText` at 500. Selected: a `primaryFill` disc behind a white 500 number. |
| `SettingsGroup` / `SettingsRow` | ColorOS grouped list (measured on `settings-display`, dark and light): the section header outside the card (12/16/500 `text.tertiary`, 24 dp above, 8 dp below, text 32 dp from the screen edge), then one 20 dp card per group whose rows are split by 1 px hairlines inset 16 dp at both ends. Rows >= 48 dp, padding 16 / 10, title 16/22/500 + optional summary 14/19/400 2 dp below, trailing element 12 dp from the text: `NbSwitch`, trailing `NbRadio`, value + chevron, or chevron (12 x 24 dp box, 1.3 dp stroke, `color.chevron`). The whole row is the touch target and one TalkBack node (role switch / radio / button, state); rows light up on press but do not shrink. Read-only notes that belong to a group are a footer under its card (12 sp `text.tertiary`). |
| `NbSwitch` | 44 x 24 dp pill, 18 dp white knob 3 dp inside in every state, no outline; track `primary` when on, `control.switchOff` when off (2.16:1 dark, recorded exception: the knob position carries the state). Knob on `spring.switch` (0.7 / 247) with a stretch to x1.3 early in the travel; snaps under reduced motion. Disabled: alpha 0.3. Flipping plays `TOGGLE_ON` / `TOGGLE_OFF` (API 34+, else `CLOCK_TICK`). |
| `NbRadio` | Always trailing. 24 dp box; unchecked a 20 dp ring, 1.3 dp, #767676 dark / #858585 light (3:1 on the card); checked a `primary` disc with a white centre dot, growing in over 300 ms with a 1.08 pulse (`easing.flow`). |
| `NbTabRow` | ColorOS tab row: 48 dp, a centred group of text tabs (label + 12 dp each side), selected 500 `text.primary`, others 400 `text.tertiary`; a 2 dp square-ended underline as wide as the selected label in the label colour (not the accent), sliding on `spring.selection`. Placed under the top bar, never inside it. |
| `NbChoiceRow` | Visual single choice (theme): equal columns, each a decorative drawing, a 14 sp label and an `NbRadio` below, split by 1 px vertical hairlines; selected label `primaryText` 500. Falls back to trailing-radio rows at font scale >= 1.5 or when a column would be narrower than 96 dp x font scale. |
| `NbChipTab` | Filter chip: 36 dp pill in a 48 dp target, 16 dp padding, no outline, no check glyph; unselected neutral fill with a secondary label, selected brighter fill + 1 dp rim + 700 label. |
| `SourceRow` | Source app icon (40 dp, read from the system package manager at runtime, never bundled; a neutral letter disc when unavailable), app label 16/500 in every state, last sync and written measurements, trailing `NbRadio`; hairline inset 72 dp. |
| `DisclaimerFooter` | One line: "Not a medical device." + "About" link. |
| `PrimaryButton` | Accent gradient capsule (`primaryFill`, darker toward the bottom so the white 16/22/500 label is >= 4.5:1 everywhere), a soft accent glow below (y 4, blur 12), a faint white top gloss band that ends above the label, and a white touch light that follows the finger (110 ms in, 480 ms out). >= 50 dp tall, padding 28 / 12, max 360 dp. Disabled: same fill and label at alpha 0.3, no glow. |
| `SecondaryButton` / `TextButton` | Secondary: neutral capsule (white @ 10 % dark, black @ 6 % light), `text.primary` 16/500 label, 50 dp (replaces the tonal button). Text button: `primaryText` 16/22/500, padding 12 / 8, 48 dp target. |

Icons: our own monochrome line icons drawn for the project, or Material Symbols (Apache-2.0). No vendor icons, fonts
or images. Source app icons are read at runtime from the installed package, never bundled.

## 5. Adaptive behaviour common to all screens

Layout modes, breakpoints and exact sizes are defined once in `layouts.md` 3.2 (one `resolveLayout` decision); this
section lists the navigation per mode and what each screen does with it. Mode names below are the `layouts.md` names.

| Mode (`layouts.md` 3.2) | Typical window | Navigation | Content layout |
|---|---|---|---|
| `Glance` | `min(w, h) < 320` or (`w < 400` and `h < 480`): flip cover screens, tiny freeform windows | no bar; "More" button opens a glass sheet with History and Settings | glance layouts (7.1, 7.3); charts become the text summary plus a 7-night glyph row; content scrolls, nothing auto-shrinks |
| `Compact` | `w < 600` (owner's outer screen ~482 x 1107 dp) | `FloatingNavBar` | single column, gutter 16 dp |
| `CompactShort` | `w < 600`, `h < 480` (phone split screen) | `FloatingNavBar`, hides on scroll down | single column, no large title |
| `WideShort` | `w >= 600`, `h < 480` (phone landscape) | `FloatingNavRail`, icons only | two side-by-side columns, no vertical stacking |
| `Medium` | 600–839 dp (owner's inner screen at 520 dpi, tablet portrait) | `FloatingNavRail` | its own layout: 2-column grids, reflowed blocks; single pane unless the two-pane rule passes |
| `Expanded` | 840–1199 dp (inner screen at 378 dpi, small tablet landscape) | `FloatingNavRail` | list-detail when the two-pane rule (`layouts.md` 3.3) passes, otherwise the Medium layout |
| `Large` / `ExtraLarge` | >= 1200 dp (tablet landscape, desktop) | labelled rail | list-detail + supporting pane; centered, max 1600 dp |
| `Tabletop` | separating horizontal fold | `FloatingNavBar` in the lower half | top half view only, bottom half controls |
| `Book` | separating vertical fold, `w >= 600` | rail, or bar inside the start pane | two panes split exactly at the fold |

Width caps everywhere: text columns and single cards max 600 dp, charts max 840 dp (`layouts.md` 3.3).

Foldables and postures:
- **Separating hinge or fold** (`FoldingFeature.isSeparating`, or occluding): no text, value, button or chart point is
  placed in the hinge bounds plus `hinge.clearance` (12 dp) on each side. With a vertical separating fold the two
  panes split exactly at the fold. Whether the owner's inner screen reports a fold when flat is a hypothesis to verify;
  layouts must still pass the screenshot test with a synthetic vertical hinge, and controls never straddle a
  non-separating crease either (`layouts.md` 6.3).
- **Tabletop** (HALF_OPENED, horizontal fold): the screen splits at the fold. Top half: view-only "look" content
  (night summary on Home, chart on Metric detail, the selected night's summary on History); nothing interactive.
  Bottom half: lists, explanations, controls, and the navigation bar. Scrolling happens only in the bottom half.
- **Book** (HALF_OPENED, vertical fold): list-detail, panes split at the fold.
- **TENT** [hypothesis, `layouts.md` 3.1]: expected to run on the outer screen as `Compact`; whatever window and fold
  WindowManager reports maps to one of the modes above.

State continuity (fold, unfold, rotate, split, resize):
- Route, selected metric, selected night, chart period, chart selection, history view mode and visible month, and
  scroll positions live in saved state (`rememberSaveable` / `SavedStateHandle`); data comes from the local database
  through the ViewModel and is never reloaded because the window changed.
- Compact Metric detail -> unfold -> expanded: list-detail with that metric selected in the detail pane, list pane
  scrolled so the selected tile is visible. Expanded with a detail selected -> fold -> compact: the detail screen is on
  top, back returns to the list. Same for History <-> Night detail.
- Config changes `screenSize|smallestScreenSize|screenLayout|orientation|density` are handled without recreation; the
  code is still written as if recreation can happen.

Glass and fallback are identical in every window size: the same component decides glass vs solid from the global
`EffectsMode` (glass / solid), never from the layout.

## 6. Status vocabulary (shape + label + color)

| Status | Glyph (shape) | Label (en) | Color token |
|---|---|---|---|
| Metric within range | filled circle inside the band | "Usual" | `color.status.within` |
| Metric higher than range | upward triangle, marker sits right of the band | "Higher than usual" | `color.status.moved` |
| Metric lower than range | downward triangle, marker left of the band | "Lower than usual" | `color.status.moved` |
| Metric higher/lower on a side that does not count (e.g. SpO2 above range) | filled circle, label adds "(doesn't count)" in detail only | "Usual" | `color.status.within` |
| Metric learning | dotted ring | "Learning, 3 of 7" | `color.status.neutral` |
| No value this night | short horizontal dash | "No value last night" | `color.status.neutral` |
| Not recorded by source | slashed circle | "Not recorded by {source}" | `color.status.neutral` |
| Night: all within | circle with check | "All within your usual range" | `color.status.within` |
| Night: one or more moved, below threshold (`S-SOME`) | rounded square with one triangle | "{k} moved" (`history_status_moved`) | `color.status.moved` (tonal) |
| Night: deviations (notified, `S-DEV`) | rounded square with two triangles | "{k} moved" | `color.status.moved` |
| Night: learning | dotted ring | "Learning" | `color.status.neutral` |
| Night: short | half-filled circle | "Short night" | `color.status.neutral` |
| Night: no data / too few readings | empty circle with dash | "No data" / "Too few readings" | `color.status.neutral` |
| Night: data access problem (permission, Health Connect, source removed, not synced) | the state's outline icon (key, download arrow, cloud with clock) | state title | `color.status.info` |

Triangles always point in the direction of the change. HRV and SpO2 lower values and heart rate higher values are not
styled differently from other directions: the app does not judge which direction is "bad".

## 7. Screens

Each screen lists the content hierarchy top to bottom for compact, then what medium and expanded do differently.
All screens end with `content.bottomClearance` (bar height + bar offset + 16 dp, `tokens.md` 4) so the last row can
scroll above the floating bar. Layout per mode: `layouts.md` 5; this section owns content and order.

### 7.1 Home ("Last night")

Purpose: last night's result at a glance; entry to every metric and to recent history.

Compact, top to bottom:
1. `ShellTopBar` large title "Last night"; subtitle = night label ("Night of Nov 24–25"). No actions (settings are in
   the nav).
2. `InlineBanner` slot (0..1, first match in the `states.md` section 1 banner order).
3. `NightSummaryCard` or a `StateCard` (when the night has no result: learning with 0 nights, source did not sync,
   no data, short night, too few readings, permission revoked, source app removed, Health Connect unavailable). Learning period: `LearningProgress` card replaces the summary
   (see states.md, L-states).
4. Metric list: one `MetricRangeTile` per metric the source provides (2..6), registry order. Tiles in learning show a
   dotted band and "Learning, N of 7"; tiles with no value show the reason.
5. Row "Not recorded by {source}: HRV, skin temperature" (only if optional metrics are missing as a capability),
   info icon -> sheet explaining that these appear automatically if the source starts writing them.
6. `HistoryStrip` titled "Last 7 nights", trailing "See all".
7. `DisclaimerFooter`.

Medium: summary card across the content width (its text max 600 dp); history strip as 14 nights beside the summary
when width >= 720 dp, else below it; metric tiles in a 2-column grid (each >= 260 dp; 3 rows max for 6 metrics); an
odd last tile spans both columns.

Expanded: list-detail. List pane: summary card, metric tiles (single column, compact tile variant), history strip,
footer. Detail pane: Metric detail of the selected metric (default: the first metric that moved, otherwise the first
metric). Selection is shown on the tile with a solid outline and `selected` semantics.

Tabletop: top half = summary card + history strip as a view-only glyph row (no targets near the fold); bottom half =
scrollable metric tiles, "See all", and the nav bar.

Glance (flip cover screen, tiny window): night label row with the "More" button at its end, status glyph (40 dp),
headline (wraps, never shrinks), then one line per metric: glyph + short name + value + status label (`status_*`;
the label may wrap below the value at large font). A thin band per metric only from 300 dp width. No strip, no footer
(the disclaimer is in About and onboarding). The content scrolls when it does not fit (200 % font). Each metric line
is a >= 48 dp target that opens the glance Metric detail (summary text + last 7 nights as a glyph row).

Actions: primary = tap a metric tile (Metric detail); secondary = history strip night (Night detail), "See all"
(History), state-card actions.

TalkBack (keys in `states.md` 9; general rules in section 11): summary card is one node (`a11y_summary`): "{night
label}. {headline}. {body}." Each tile is one node, role button (`a11y_tile`): "{metric}: {value} {unit}. {status
label}. Usual range {low} to {high} {unit}." Learning tile: `a11y_tile_learning`; missing / not provided:
`a11y_tile_missing`. History strip: one node per night (`a11y_history_day`), or one node `a11y_history_strip` when
the strip is a single target.

### 7.2 Night detail (`night/{date}`)

Purpose: the Home view for any past night.

Hierarchy: `ShellTopBar` with back, title = night label, actions: previous night / next night (48 dp chevrons,
`a11y_prev_night` / `a11y_next_night`, also `Left`/`Right` keys and horizontal swipe on the summary card). Then the same blocks as Home 3–5 for that night,
without the history strip. A state card if the night had a problem (short, no data, not synced at the time).

Medium: tiles in 2 columns. Expanded: used as the detail pane of History, tiles in a single column (no mini charts,
kept simple); tapping a tile opens Metric detail inside the detail pane, with back returning to the night.
Tabletop: top half = summary card (view only), bottom half = previous/next buttons, tiles, nav bar. Glance: as glance
Home for that night.

### 7.3 Metric detail (`metric/{metricId}`)

Purpose: how this measurement behaves over time, where the personal range comes from, what a deviation means.

Compact, top to bottom:
1. `ShellTopBar`: back, metric name, action "How the range works" (info icon) -> `range-explainer` sheet.
2. Header card (solid; shared-element target of the tile): value for the anchor night (default last night), unit,
   `StatusLabel`, usual range "52–61 bpm", one sentence from the `detail_*` keys (`states.md` 4), e.g. "6 bpm above
   your usual range." or "Inside your usual range.". State variants: learning (progress), no value (reason), not
   recorded by source, access off.
3. `PeriodSelector`: 7 / 30 / 90 nights (default 30; 7 while fewer than 30 nights exist; periods longer than the
   available history stay enabled and show what exists).
4. `TrendChart` (solid card; heights per mode in `layouts.md` 5.2: 220 dp Compact, 240 dp Medium, 280 dp Expanded and
   up, +40 dp when height >= 900 dp, never taller than 45 % of the window height except in Tabletop, where it fills
   the top half minus 24 dp).
   Range band as it was on each night; points; selected point with `ChartTooltip`. Drag scrubs the selection
   (finger-following), tap selects the nearest point, double tap opens that night (Night detail).
5. `ChartSummary` text (always visible, 2–4 lines).
6. `StatsRow` (`stats_inside`, `stats_range`, `stats_median`): "Inside your usual range: 27 of 30 nights", "Usual
   range: 52–61 bpm", "Middle value: 56 bpm".
7. `ExplainerCard` "How your usual range is computed": median of your last 28 valid nights within 60 days (at least 7), plus or minus a robust
   spread; nights shorter than 3 hours are left out; a value counts as outside only when it is clearly away from the
   median (both a statistical distance and a minimum absolute difference). Metric-specific line, for example SpO2:
   "Only lower values count for SpO2. Higher values are shown but never counted." Copy: `detail_computed_title`,
   `explainer_range`, `explainer_learning`, `explainer_outside`, metric line `detail_side_ignored`. Sleep duration
   whose range spans more than 6 h (its lower edge never goes below the 3 h short-night limit) gets
   `detail_sleep_range_wide` as its metric line: "Your sleep length varies a lot from night to night, so your usual
   range is wide and only big changes are noticed."
8. `ExplainerCard` "What 'outside your usual range' means": a change compared with your own recent nights, not a
   diagnosis; one measurement moving is common; NightBaseline notifies only when two or more move in the same night;
   common everyday reasons (late meal, alcohol, travel and time zones, intense exercise, a cold, new medication, how
   the device sits on the wrist); "If you feel unwell or are worried, talk to a health professional." Copy:
   `explainer_meaning_title`, `explainer_meaning`, `explainer_care`.
9. Source line (`detail_source_line`): "Measured by {source}. Last sync {time}."
10. `DisclaimerFooter`.

Medium: header card and stats side by side; chart full width below; the two explainer cards side by side.

Expanded: rendered as the detail pane of Home (or History > night > metric). Chart max width 840 dp, explainer text
max 600 dp.

Tabletop: top half = metric name + last-night value and status as a large label + chart (view only, fills the half);
bottom half = period selector, "Previous night" / "Next night" step buttons (48 dp) that move the chart selection,
summary, stats, explainers, nav. Nothing interactive is near the fold.

Glance: header line (value + glyph + label), chart replaced by `ChartSummary` text and a 7-night glyph row with a
thin band, one line of usual-range text; no period selector (7 nights fixed).

Actions: primary = period selection and chart scrubbing; secondary = range explainer, open a night.

### 7.4 History

Purpose: find unusual nights.

Compact:
1. `ShellTopBar` large title "History"; under it, centred, an `NbTabRow` "List / Calendar" (`history_view_list` /
   `history_view_calendar`, persisted).
2. List mode: `NightRow` feed, newest first, one grouped card per month under a sticky section header (solid window
   background, not glass). Rows for
   learning, short and no-data nights use their own glyphs. Infinite scroll over the local database (all stored
   nights).
3. Calendar mode: `MonthCalendar` with month swipe and previous/next buttons; below it a legend row (glyph + label for
   each night status, so the shapes are learnable); tapping a day opens Night detail.
4. Filter chips (`NbChipTab`; `history_filter_all`, `history_filter_moved`, `history_filter_gaps`): "All", "Moved",
   "Learning and gaps" ("Moved" includes both levels, `S-SOME` and `S-DEV`). Each chip >= 48 dp tall, selection shown
   by fill, rim and weight, not only color.

Medium: single pane (the two-pane rule does not pass at 692 dp, `layouts.md` 3.3). List mode stays one column (max
600 dp, centered; rows add a mini glyph per metric). Calendar mode shows the month grid with 48–64 dp cells and the
selected night's summary below it (reflow).

Expanded: list-detail; list pane = feed or calendar, detail pane = Night detail of the selected night (default: the
newest night).

Tabletop: top half = the selected night's summary (view only); bottom half = list or calendar (all interactive
elements), nav bar.

Glance: list only, compact rows (night label + glyph + count); tap -> glance Night detail.

TalkBack: a row reads "{night label}: {night status label}. {k} moved: {metric names}." Calendar cells read
"{full date}: {night status label}"; the grid has collection semantics (row/column info).

### 7.5 First run (onboarding)

Full screen, no nav, solid background with a quiet static gradient (no looping motion in any mode). Step indicator
at the top: visible text `onb_step` "Step 2 of 6" plus decorative dots (6 dp, the current one a 16 x 6 dp `primary`
pill, the others neutral), led by a 40 dp round "Back" button on steps that have one. At the bottom (ColorOS
large-button pattern): a full-width `PrimaryButton` (max 360 dp, 50 dp) and, 8 dp below, the secondary action as a
centred text button; always above the system navigation inset and outside the hinge. In Tabletop the top half stays
view only, so "Back" is a text button under the other buttons there.

| Step | Content | Primary action | Secondary |
|---|---|---|---|
| 1 welcome | App name, one line "Notices when your night measurements move outside your usual range." Three short points: reads once a day after sleep / compares with your own recent nights / everything stays on this phone, no internet. Disclaimer paragraph (full text). | "Get started" | - |
| 2 how it works | Illustration: a band with points, one outside. "The first 7 nights teach NightBaseline your usual range. After that it tells you when two or more measurements move outside it." | "Next" | "Back" |
| 3 source | List of Health Connect sources that wrote sleep data in the last 30 days (`SourceRow`), default selection OHealth if present, otherwise the source with the most recent sleep session. Text: "Any app that writes sleep and heart rate to Health Connect works." If none found: state O-NOSOURCE. | "Use {source}" | "Back" |
| 4 permissions | What will be read and why, one line per data type (sleep, heart rate, respiratory rate, oxygen saturation, HRV, skin temperature), plus "Read in the background" and "Read past data" (optional, lets the app use up to 60 days of earlier nights so the learning period can finish right away). Read-only, never writes. | "Allow in Health Connect" -> HC permission UI | "Back" |
| 5 notifications | "Get a calm note in the morning when two or more measurements move outside your usual range." Android 13+: system permission prompt. | "Allow notifications" | "Not now" |
| 6 ready | If past nights exist: "Found {n} past nights. Your usual range is ready." -> Home with results. Otherwise "Learning starts tonight." with `LearningProgress` at 0 of 7. | "Done" | - |

Branches: Health Connect missing or needs an update (state E-HC), permissions denied (state E-PERM, onboarding
variant), notifications denied (continue; banner later on Home).

Per mode (`layouts.md` 5.5): Medium two columns (illustration + step indicator start, text + actions end, max 480 dp);
Expanded and up a centered card max 840 dp; Tabletop: illustration + step title top half, text + buttons bottom half;
Book: illustration start half, text + buttons end half; Glance/CompactShort: no illustration, text scrolls, buttons at
the end of content. The illustration is our own abstract band-and-points drawing (vector, no vendor art), hidden at
font scale >= 1.5.

### 7.6 Learning period (Home in learning state)

Not a separate route: Home and Metric detail switch to learning presentation while `NightStatus.LEARNING`.

Home hierarchy in learning:
1. `ShellTopBar` "Last night".
2. Learning card: `LearningProgress` "3 of 7 nights", title "Learning your usual range", body "4 more nights with
   sleep data and NightBaseline starts comparing.", link "How it works" -> `range-explainer`.
3. "Already visible" block: metric tiles with last night's values and a dotted band outline (no status), so the
   person sees data is arriving.
4. "What to expect" card: after 7 nights each measurement gets a range; the range keeps adjusting over the next weeks
   (uses up to 28 nights); notifications start after the learning period.
5. `HistoryStrip` (learning glyph for counted nights, short-night glyph for skipped ones).
6. Footer.

Per-metric learning (a metric that appeared later, e.g. the source starts writing HRV): only that tile is in learning
state; the night status ignores it.

### 7.7 Settings

Compact (grouped solid cards, top bar large title "Settings"):
1. **Data source**: row "Source" (value = app name, chevron -> source picker); row "Health Connect permissions"
   (-> HC app permissions screen); row "Last read" (time of the last successful daily run, read-only); row "Set up
   again" (-> onboarding step 3).
2. **Notifications**: switch "Night results" (disabled with explanation if the system permission or channel is off;
   then the row action opens system notification settings); switch "Problems reading data" (status channel, same
   name as the channel).
3. **Sensitivity**: row "Sensitivity" (value = preset name) -> `settings/sensitivity`.
4. **Appearance**: two grouped cards under their own headers, "Theme" (System / Light / Dark as an `NbChoiceRow`:
   three columns with our own small phone drawing, label and radio, split by vertical hairlines; trailing-radio rows
   at large font) and "Glass effects" (Automatic / Off, trailing radios; Automatic turns them off on
   Android < 12, when animations are removed, battery saver, reduced transparency or higher contrast, weak, hot or
   janky devices: `tokens.md` 7.3; the summary names the reason, `glass_off_*`), "Motion" read-only note
   `settings_motion_note` as a footer under the glass card: follows the system "Remove animations" setting.
5. **About**: "About NightBaseline" -> About; "Not a medical device" line.
6. **Debug** (debug builds only): "Component catalog", "Show synthetic data".

Sensitivity screen: three presets as rows of one grouped card, each with one plain-language summary line and a
trailing `NbRadio` (no outline, no weight change):
- "Fewer notes": a value must be further from your usual range (z 3.0); 2 measurements must move.
- "Balanced" (default): defaults of `AlgoParams` (z 2.5, 2 measurements).
- "More notes": smaller changes count (z 2.0); 2 measurements must move.
The presets change only the threshold z; the notification rule (two measurements) is the same for all three.
Below: "Advanced" (collapsed): range window 14–28 nights (stepper), per-metric switches "Use for comparison" for each
metric the source provides. A preview line: "With these settings, {k} of your last 30 nights would have sent a note."
(computed locally from stored nights). "Reset to default" -> `GlassDialog` confirmation.
(The exact preset parameters are decided in the algorithm ADR; this screen shows the words, not z values.)

About: version, the full disclaimer, "Your data stays on this phone. NightBaseline has no internet access.",
"Works with any app that writes to Health Connect.", licenses, source code link (text only, no browser intent needed).

Medium: single centered column (max 600 dp), Sensitivity expands inline. Expanded and up, Book: list-detail, groups
list on the start side, the sub-screen (source, sensitivity, about) on the end side. Tabletop: current source and
last read on top (view), groups below. Glance: Source, Notifications, About only (`layouts.md` 5.4).

### 7.8 Notification

See `states.md`, section "Notification spec". Summary: one notification per evaluated night that reached the
deviation threshold, channel "Night results", default importance, no values in the public version, tap ->
`nightbaseline://night/{date}`.

### 7.9 Home-screen widget (optional, after everything else passes review)

Glance widget, solid surfaces only (no blur in RemoteViews), own rim and radius tokens, follows system light/dark.
- Small (2x2): night status glyph, headline (e.g. "All within your usual range" / "2 moved"), night label.
- Wide (4x2): adds one line per metric (glyph + short name + status label), up to 6, two columns.
- Learning: "Learning, 3 of 7". Problem states: the state's glyph + short label from `states.md` 5 (widget column)
  + tap opens Home.
- Every widget view has a content description (same text as `a11y_summary`); text uses sp and the widget tolerates
  200 % font by dropping the metric lines before clipping; colors from the same tokens (`material.solid.widget`).
Tap -> `nightbaseline://night/{date}`. Respects the Android 17 widget bitmap limit (no bitmaps; vector glyphs).

### 7.10 Debug component catalog (debug builds only)

Purpose: every component in both themes and all states on one screen, and the source of screenshot-test previews.
- Top: controls (glass/solid toggle, light/dark/both, grayscale toggle (`principles.md` 4), reduced-motion toggle,
  font scale 1.0 / 1.35 / 2.0, locale en/ru, layout mode preset (every `layouts.md` 3.2 mode, incl. synthetic fold),
  metric count 2..6).
- Sections per component (section 4 order). Each entry renders light and dark side by side (stacked on compact),
  over a busy synthetic backdrop for glass components, with every state variant: tile x {within, higher, lower,
  learning, no value (each reason), partial, not compared, not recorded, access off}, summary card x every night
  status, state card x every state in `states.md`, banner x every `B-*`, band x {value inside, outside left/right,
  clamped, learning outline}, chart x {7, 30, 90, gaps, learning nights, band shift, tooltip}, nav bar and rail x
  {each selected item, focus, hover, pressed}, notification previews (collapsed, expanded, public) as static mock-ups.
- Each entry shows its measured contrast ratio and touch-target size as text (debug overlay), failing entries in a
  "fails" list at the top.
- All data is synthetic (`SyntheticNights`); never real Health Connect data.

## 8. Chart accessibility: text summary pattern

Every chart has a visible `ChartSummary` and uses the same text as its content description. The pattern (string
keys in `states.md` 7.1; Russian in `app/src/main/res/values-ru/strings.xml`):

```
{metric}, last {N} nights.                                      chart_summary_header
Your usual range is {low} to {high} {unit}.                     chart_summary_range
Last night: {value} {unit}, {status label}.                     chart_summary_last
{k} of {N} nights were outside the range, most recently {date}. chart_summary_outside   (or: All {N} nights were inside the range.)
Lowest {min} on {date_min}, highest {max} on {date_max}.        chart_summary_extremes
{g} nights had no value.                                        chart_summary_gaps      (omitted when 0)
```

Example (en, 30 nights): "Sleeping heart rate, last 30 nights. Your usual range is 52 to 61 bpm. Last night: 67 bpm,
higher than usual. 2 of 30 nights were outside the range, most recently Nov 25. Lowest 50 on Nov 3, highest 67 on
Nov 25. 1 night had no value."

Interactive traversal: the chart node exposes custom accessibility actions "Previous night" / "Next night"; each step
announces "{date}: {value} {unit}, {status label}" and moves the visual selection (same as `Left`/`Right` keys). Range
band and axes are decorative (cleared semantics). Numbers and dates are formatted with the current locale
(`NumberFormat`, `DateTimeFormatter.ofLocalizedDate(MEDIUM)` / skeleton "MMMd"), units from string resources.

## 9. Disclaimer placement

| Place | Form |
|---|---|
| Onboarding step 1 | Full paragraph, read before "Get started" (no checkbox) |
| Home, Night detail, Metric detail | `DisclaimerFooter` one line at the end of content, link to About |
| Metric detail "What it means" card | Sentence "This is not a diagnosis..." + "talk to a health professional" |
| Range explainer sheet | Last paragraph |
| About | Full text |
| Notification | Not included (too long); the expanded text ends with "Not a medical device." |
| README / README.ru.md | Full text |
| Widget, `Glance` window | Not shown (space); reachable via About |

## 10. Screenshot test matrix (for later)

The device list, densities and fold injection are defined once in `layouts.md` 11 (ids `phone`, `owner-outer`,
`flip-cover-square`, `flip-cover-tall`, `owner-inner`, `owner-book`, `owner-tabletop`, `tablet-port`, `tablet-land`,
...). Dimensions: every screen and every state in `states.md` x theme (light, dark) x language (en, ru) x font scale
(1.0, 1.35 = the owner's setting, 2.0) x effects (glass, solid fallback) x device x metric count (2, 4, 6), plus a
grayscale variant of Home and Metric detail (`principles.md` 4) and a reduced-motion run of the transition tests.
Pruning rules (which combinations are exhaustive and which sampled) are in `layouts.md` 11.

## 11. Accessibility rules for every component

| Element | TalkBack node | Role / state | Other |
|---|---|---|---|
| `ShellTopBar` title | heading | `heading()` | back button `a11y_back` "Back"; info action `a11y_range_info` |
| `FloatingNavBar` / `FloatingNavRail` items | one node per item, label = `nav_*` | tab, `selected` | in `WideShort` the icon-only items keep the label as content description and a tooltip |
| `Glance` "More" | `action_more` "More" | button | opens the sheet; focus moves to its first entry |
| `NightSummaryCard` | one merged node, `a11y_summary` | none (text) | glyph decorative (meaning is in the text) |
| `MetricRangeTile` | one merged node, `a11y_tile*` | button, `selected` in list-detail | band and marker decorative; the range is in the text |
| `RangeBand` alone (Metric detail header) | cleared semantics | - | the header text carries value, status and range |
| `StatusLabel` / `StatusGlyph` | merged into the parent | - | never announced as a color |
| `HistoryStrip` | one node per night `a11y_history_day`, or `a11y_history_strip` when it is a single target | button | |
| `TrendChart` | one node, content description = `ChartSummary` text | custom actions `a11y_prev_night` / `a11y_next_night`; selection announced with `a11y_chart_point` | section 8 |
| `PeriodSelector` | one node per segment `a11y_period` "{n} nights" | tab, `selected` | |
| `StateCard` | title as heading, body text, action button | heading + button | the card's icon is decorative |
| `InlineBanner` | text + action button, polite live region (announced once) | button for the action; dismiss `a11y_dismiss` "Dismiss" | |
| `LearningProgress` | one node `a11y_learning_progress` | progress (`ProgressBarRangeInfo` 0..7) | segment shapes decorative |
| `MonthCalendar` | grid with collection info; cells `a11y_history_day` | button, `selected` | previous/next month buttons `a11y_prev_month` / `a11y_next_month` |
| `NightRow` | one merged node (see 7.4) | button | |
| `SettingsRow` | one merged node: title, value / summary, state | switch / radio / button | disabled rows read their reason |
| `GlassSheet` / `GlassDialog` | pane title | dialog; focus trapped inside, restored on close | close action `a11y_close` "Close" |
| `ChartTooltip` | not a separate node | - | its text is the chart's selection announcement |
| Onboarding step | `onb_step` "Step {n} of 6" announced on page change | - | dots decorative |
| Illustrations | decorative | - | |

Rules: every interactive node >= 48 x 48 dp (`tokens.md` 10); numbers are announced with units (unit strings, not
symbols: `unit_*` have spoken forms where the symbol is ambiguous, e.g. "bpm" is read "beats per minute" through a
`TtsSpan`); dates use the locale's long form in announcements; at font scale 2.0 nothing clips and nothing becomes
unreachable (screenshot assertions in `layouts.md` 11).
