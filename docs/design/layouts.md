# Layouts per window size, posture and input

Status: design, stage 3 (before code). Date: 2026-11-25.
Inputs: `docs/research/large-screens.md` (APIs, versions, breakpoints, quality tiers),
`docs/research/design-coloros17.md` and ADR 0003 (glass shell, floating navigation, Haze blur),
`docs/research/analogs.md` (how ranges and deviations are shown), measurements of the owner's system apps on the
outer screen (reference screenshots and UI dumps live only in the git-ignored `.data/design-refs/`; only numbers are
quoted here). Related design docs: `tokens.md` (colors, type, shapes, materials, motion, fallback),
`screens.md` / `states.md` (content and wording of each screen and state). This file owns *where things go*:
layout modes, breakpoints, pane widths, bar/rail/top-bar geometry and chart heights; when another design document
disagrees on one of these, this file wins. Copy in wireframes is illustrative; the exact strings are in `states.md`.
Index: `README.md`.

Tags as in the research docs: **[verified]** (checked in this session against code, docs or dumps),
**[from official documentation]** (see the research file for the URL), **[hypothesis]** (to verify on the
emulator or, with permission, on the owner's phone).

---

## 1. Principles

1. **The window decides, never the device.** No `isTablet`, no `isFoldable`, no manufacturer checks. Inputs are the
   window size in dp, the font scale, the posture (folding features) and the pointer/keyboard situation. The same
   phone produces several layouts (outer screen, inner screen, split screen, tabletop).
2. **One decision point.** A pure function `resolveLayout(LayoutInputs): LayoutSpec` runs once at the top of the
   UI tree (in `NbAppScaffold`) and is passed down. Components use only `LayoutSpec` and local constraints, never
   `currentWindowAdaptiveInfoV2()` themselves. Screenshot tests construct `LayoutInputs` directly (see section 10),
   so every layout is testable on the JVM without real hardware.
3. **Each class has its own layout, not a stretched phone.** Wider windows get more panes or columns, side-by-side
   chart and explanation, or a supporting pane, not wider cards.
4. **Same state, any layout.** All layouts read the same ViewModel state and the same hoisted UI state. Changing the
   layout never reloads data and never loses the selection, the period, the scroll anchor or an open sheet
   (section 8).
5. **Shell is glass, data is solid** (ADR 0003): navigation bar/rail, top bar, sheets and dialogs are glass; cards,
   range bands, charts, the chart tooltip and long text sit on solid surfaces. This is identical in every layout.
   Fallback (`tokens.md` 7.3: API < 31, battery saver, reduce transparency or higher contrast, animations off, weak,
   hot or janky device) swaps glass for solid in every layout identically;
   it never changes geometry, so screenshots of glass and fallback for the same layout align pixel for pixel
   except for surface fill.
6. **Hinge and fold are never under a control** and never under a value, a band or a chart axis label.
7. **Readable widths.** Body text column max 600 dp (about 70 characters at default size), charts max 840 dp wide,
   any single card max 600 dp. Extra space becomes margins or another pane, never longer lines.
8. **Nothing clips at 200 % font scale.** Text wraps; rows grow; values never truncate (labels may wrap to more
   lines, a value never ellipsizes). Pane minimum widths scale with font scale (section 3.3). Note: the owner's
   phone runs with `font_scale=1.35` **[verified: `.data/design-refs/folded/env.txt`]**, so 1.35 is a first-class
   test value, not only 1.0 and 2.0.

---

## 2. APIs and versions

Versions as verified in `large-screens.md` section 1 (2026-11-25) and the project catalog
(`gradle/libs.versions.toml`).

| Need | API | Artifact and version |
|---|---|---|
| Window size class (incl. Large/XLarge) and posture | `currentWindowAdaptiveInfoV2()` -> `WindowAdaptiveInfo(windowSizeClass, windowPosture)`; checks via `windowSizeClass.isWidthAtLeastBreakpoint(...)`, `isHeightAtLeastBreakpoint(...)` | `androidx.compose.material3.adaptive:adaptive` **1.3.0** (catalog `material3Adaptive = "1.3.0"`) |
| Exact window size in dp | `currentWindowDpSize()` (adaptive 1.2+) | same |
| Folding features | `Posture.hingeList`, `isTabletop`, extensions `separatingVerticalHingeBounds`, `occludingVerticalHingeBounds`, `allHorizontalHingeBounds`; lower level `collectFoldingFeaturesAsState()` | `adaptive` 1.3.0; `androidx.window:window` **1.5.1** only if `FoldingFeature.state`/`occlusionType` beyond `Posture` are needed |
| List-detail | `NavigableListDetailPaneScaffold`, `rememberListDetailPaneScaffoldNavigator<NightKey/MetricKey>()`, `AnimatedPane`, `calculatePaneScaffoldDirective(adaptiveInfo)` (customised, section 3.3) | `adaptive-layout`, `adaptive-navigation` **1.3.0** |
| Supporting pane (Large/XL only) | `NavigableSupportingPaneScaffold` | same |
| Navigation shell | **Custom** `NbNavigationShell` (glass floating bar / floating rail). Decision logic follows `NavigationSuiteScaffold` rules with two documented deviations (section 4). `NavigationSuiteScaffold` itself is not used: its bar and rail are edge-docked M3 components and the component override API was removed in 1.4.0-alpha01 | `material3-adaptive-navigation-suite` **1.5.0** used only for `NavigationSuiteType` constants and as a behavioural reference, or not at all |
| Shared element morph | `SharedTransitionLayout`, `Modifier.sharedBounds`, `AnimatedContent` | Compose BOM |
| Saved UI state | `rememberSaveable`, `rememberLazyListState()` / `rememberLazyGridState()` (saveable), `ViewModel` + `SavedStateHandle` | Compose BOM, lifecycle 2.11.0 |
| Keyboard shortcuts helper | `Activity.onProvideKeyboardShortcuts`, `KeyboardShortcutGroup` | platform |
| Hover, focus | `Modifier.hoverable`, `pointerHoverIcon(PointerIcon.Hand)`, `focusable`, `focusGroup`, `focusProperties`, `onPreviewKeyEvent` | Compose BOM |

Notes:
- **BOM.** The catalog pins `composeBom = "2026.09.00"` **[verified]**; `large-screens.md` recommends
  2026.11.01 (material3 1.5.0, navigation-suite 1.5.0, adaptive 1.3.0). The adaptive artifacts are pinned separately to
  1.3.0 either way, so `currentWindowAdaptiveInfoV2()` is available. Bumping the BOM is an architecture decision
  (ADR), not part of this document.
- **No alphas** (adaptive 1.4.0-alpha, window 1.6.0-alpha): APIs moved between alpha releases.
- **Navigation library.** The catalog has Navigation Compose 2.10.2. The list-detail scaffolds live *inside* the
  Last night (Home) and History destinations. If the architecture ADR switches to Navigation 3, the same pane split maps to
  `ListDetailSceneStrategy` from `adaptive-navigation3` 1.3.0 (explicit version, not in the BOM); the layout rules
  below do not change.
- **Manifest** (from `large-screens.md` sections 6-8): no `screenOrientation`, no `resizeableActivity=false`, no
  aspect ratio limits (ignored on sw >= 600 dp with targetSdk 36 anyway, and the opt-out is gone on API 37);
  `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"` so fold/unfold does not
  recreate the activity; code is still written as if recreation (process death, locale, theme) happens.
  `<layout android:defaultWidth="480dp" android:defaultHeight="840dp" android:minWidth="220dp"
  android:minHeight="220dp"/>` for freeform/desktop windows **[hypothesis: values to confirm on the resizable
  emulator]**.
- Edge-to-edge is mandatory (targetSdk 36). All layouts consume `WindowInsets.safeDrawing`; floating shell elements
  add their own offset on top of the system bar insets.

---

## 3. Layout model

### 3.1 Inputs

```kotlin
data class LayoutInputs(
    val widthDp: Float, val heightDp: Float,      // currentWindowDpSize()
    val fontScale: Float,                          // LocalDensity.current.fontScale
    val verticalFold: FoldInfo?,                   // from Posture: x position in dp, separating, occluding width
    val horizontalFold: FoldInfo?,                 // y position in dp, separating
    val precisePointer: Boolean,                   // a mouse/trackpad was used (hover enabled)
)
```

`FoldInfo` is computed from `Posture.hingeList`: a vertical hinge with `isSeparating` = **book posture**; a horizontal
hinge with `isSeparating` = **tabletop**. A non-separating fold (inner screen lying FLAT) is still recorded, because
the crease is visible: controls must not straddle it (section 6.3).

The owner's phone reports device states CLOSED, TENT, HALF_OPENED and OPENED **[verified, STATUS.md]**. The app never
reads device states; it reacts only to the window size and the folding features that WindowManager reports for the
current display. How TENT appears to apps (outer or inner display, which `FoldingFeature`) is
**[hypothesis: verify with permission]**; whatever it reports maps to one of the layouts below.

### 3.2 Layout modes

`resolveLayout` picks exactly one mode, checked in this order:

| # | Mode | Condition | Navigation | Panes |
|---|---|---|---|---|
| 1 | `Glance` | `min(w, h) < 320` or (`w < 400` and `h < 480`) | none; "More" button opens a glass sheet with sections | 1, glance content |
| 2 | `Tabletop` | separating horizontal fold and `h >= 480` | floating bottom bar in the lower half | top half: view, bottom half: controls |
| 3 | `Book` | separating vertical fold and `w >= 600` | rail if the start pane keeps >= 360 dp next to it, else floating bar inside the start pane | 2, split exactly at the fold |
| 4 | `CompactShort` | `w < 600` and `h < 480` | floating bar, hides on scroll down | 1 |
| 5 | `Compact` | `w < 600` | floating bottom bar | 1 |
| 6 | `WideShort` | `w >= 600` and `h < 480` | floating rail (icons only) | 1 or 2 (side by side, no vertical stacking) |
| 7 | `Medium` | `600 <= w < 840` | floating rail | 1 (grid / reflow), 2 only if the pane minimums fit |
| 8 | `Expanded` | `840 <= w < 1200` | floating rail | 2 (list-detail) |
| 9 | `Large` | `1200 <= w < 1600` | floating rail with labels | 2 + optional supporting pane |
| 10 | `ExtraLarge` | `w >= 1600` | floating rail with labels | 3, whole layout centered, max 1600 dp |

Breakpoints are the Material ones from `large-screens.md` section 2 (width 600 / 840 / 1200 / 1600; height 480 / 900)
**[from official documentation]**. Height `Expanded` (>= 900) only changes chart heights and whether the Home history
strip is shown expanded; it does not create its own mode.

### 3.3 Pane widths and when two panes are allowed

| Token | Value | Meaning |
|---|---|---|
| `gutterCompact` | 16 dp | side margin, compact and glance (glance: 12 dp) |
| `gutterMedium` | 24 dp | medium and up |
| `gutterLarge` | 32 dp | large and extra-large |
| `paneSpacer` | 24 dp (16 dp in medium) | gap between panes; on a separating fold the spacer is the hinge area plus 12 dp on each side |
| `listPaneMin` | 320 dp | metric list, nights feed, settings categories |
| `listPaneCalendar` | 360 dp | month grid: 7 x 48 dp cells + 2 x 12 dp padding |
| `listPaneMax` | 400 dp (Large/XL: 400) | list never grows past this; extra space goes to detail |
| `detailPaneMin` | 360 dp | night detail, metric detail |
| `textMax` | 600 dp | body text column, cards, settings column |
| `chartMax` | 840 dp | chart width cap |
| `supportingPane` | 320 dp | Large/XL "Recent nights" pane |
| `railArea` | 96 dp | floating rail 72 dp + 12 dp start offset + 12 dp clearance |

**Two-pane rule.** Two panes are used only when
`w - railArea - 2*gutter - paneSpacer >= listPaneMin*s + detailPaneMin*s`, where
`s = clamp(fontScale, 1.0, 1.5)`. Otherwise the layout falls back to its single-pane form. This is implemented by
customising the directive: `calculatePaneScaffoldDirective(adaptiveInfo).copy(maxHorizontalPartitions = if (twoPanes)
2 else 1, horizontalPartitionSpacerSize = paneSpacer)`. In `Book` mode two panes are always used and the split is
the fold, regardless of the rule (the hinge forces it), with content inside each half reflowing.

Worked examples (owner's inner screen, see section 9): at 520 dpi the window is about 692 dp wide -> `Medium`;
`692 - 96 - 48 - 16 = 532 < 680` -> single pane. At the 378 dpi override it is about 951 dp -> `Expanded`;
`951 - 96 - 48 - 24 = 783`, which fits two panes at font scale 1.0 (needs 680) but not at the owner's font scale
1.35 (needs `680 * 1.35 = 918`). So with the owner's own font scale the 951 dp window shows the Medium-style single
wide pane in History and Home; this is intended (no cramped panes) and is covered by screenshot tests. Two panes
appear on the owner's phone in Book posture (forced by the hinge) and on any window >= about 1110 dp at font 1.35.

---

## 4. Navigation shell

Three top-level sections: **Last night** (Home, `nav_home`), **History**, **Settings**. Metric detail and Night detail are child
destinations. Onboarding is outside the shell (no navigation shown).

### 4.1 Floating bottom bar (Compact, CompactShort, Tabletop, Book with narrow start pane)

Measured on the owner's outer screen (system apps, 1 dp = 2.3625 px, font scale 1.35) **[verified: UI dumps]**:
floating pill 237 x 77 dp (2 items) and 327 x 56 dp (3 items), centered, bottom edge 16-22 dp above the screen bottom;
icons 24-33 dp; item hit areas 70-97 x 48-66 dp; the bar's hosting area is 88-121 dp tall. Our token values:

| Property | Value |
|---|---|
| Shape | full pill (corner = height / 2), `material.glass.bar` (fallback `material.solid.bar`), 1 dp light rim |
| Height | 64 dp at font 1.0; grows with the label (min 64, label max 2 lines, never clipped) |
| Width | wraps items: per item max(80 dp, label width + 24 dp), plus 8 dp inner padding; max `w - 2*gutter`. If the items do not fit (narrow window at large font), labels wrap to 2 lines; if they still do not fit, items share the width equally (each >= 48 dp) and the bar grows in height. Never clipped, never ellipsized |
| Position | centered horizontally; bottom = navigation bar inset + 12 dp |
| Item | icon 24 dp + label, hit area >= 48 x 48 dp; selected item: solid tinted capsule behind icon + label (shape and weight carry selection, not color alone) |
| Content padding | scrolling content gets bottom padding = bar height + offset + 16 dp, so the last row can scroll above the bar |
| Behaviour | always visible in `Compact`; in `CompactShort` slides away on scroll down and returns on scroll up or when focus/TalkBack moves to it |
| Fold rule | the bar is never centered across a vertical fold: in `Book` it is centered in the start pane |

### 4.2 Floating rail (Medium, Expanded, WideShort, Book with wide start pane)

| Property | Value |
|---|---|
| Shape | vertical pill, 72 dp wide, `material.glass.rail` (fallback `material.solid.bar`), same rim as the bar |
| Position | start edge + 12 dp (plus start system inset / cutout), vertically centered in the area below the top bar |
| Items | icon + label below (medium, expanded; labels wrap to 2 lines at large font and the rail grows, scrolling if taller than the window); `WideShort`: icon only, label in tooltip and content description |
| Large / XL | widens to a labelled pill (icon + label side by side, about 180 dp), same position |
| Content | panes start after `railArea` (96 dp); content never scrolls under the rail |

**Deviations from `NavigationSuiteScaffold` defaults** (which choose a bottom bar when *width or height* is compact):
1. `WideShort` (phone landscape, e.g. 915 x 412 dp) uses the rail: a bottom bar would take about 25 % of a 412 dp
   height; a rail takes about 10 % of the width.
2. `Book` keeps the bar inside the start pane when a rail would leave the start pane under 360 dp.
`Tabletop` matches the default (bar).

### 4.3 Top bar

`material.glass.topBar` once content is under it (transparent over the solid window background at rest). `Compact`: large title (about 112 dp) collapsing to 56 dp on scroll (the system apps measure
129 -> 52 dp with font 1.35 **[verified: UI dumps]**). `Medium` and up: fixed 64 dp, title start-aligned to the first
pane. In two-pane layouts each pane has its own title row inside the pane (solid), and the glass top bar only holds
the section title and global actions. `CompactShort`, `WideShort`: fixed 48 dp, no large title. `Glance`: no top bar;
the date line is the first content row, the "More" button sits at the end of it.

### 4.4 Sheets and dialogs

Bottom sheets (glass) in `Compact`, `CompactShort`, `Tabletop` (sheet limited to the lower half) and `Glance`
(full height). From `Medium` up and in `WideShort`, the same content opens as a centered glass dialog (max 560 dp
wide) or, for the "How the range works" explanation, in the detail pane when one exists. In `Book` a dialog is
placed in the end pane (never across the fold).

---

## 5. Screens per mode

Legend for wireframes: `[ ]` solid card, `( )` glass shell element, `|||` range band, `*` last night's value marker,
`^`/`v` "higher"/"lower than usual" triangle (always with a text label, never color alone), `o` within (filled
circle), `<^^>` night glyph "2 moved" (rounded square with two triangles), `(v)` circle with check, `-` no value,
`||` fold/hinge. Sizes are dp. Content is synthetic; wireframe text is shortened, real copy is in `states.md`. No
wireframe uses an exclamation mark: nothing in the app is an alarm.

### 5.1 Home (Last night)

Content blocks (defined in `screens.md`): **Night summary** (night label, headline "All within your usual range" or
"2 measurements moved outside your usual range", night status glyph), **Metric rows** (2..6; name, value, unit,
personal range band with last night's marker, status glyph and label), **Recent nights strip** (`HistoryStrip`: last
7 nights, each a night status glyph from `screens.md` 6: circle with check = all within, rounded square with one or
two triangles = moved, dashed circle = no data), and state cards (learning period, no data, permission revoked, ...)
which replace or precede the summary.

**Compact (`w < 600`)** - one column, `textMax` 600, gutter 16. Each metric row is full width; the band sits under
the value line.
```
+------------------------------+
| (Last night           )      |  glass large title, collapses
| [ Night of Nov 24-25         ]|
| [ <^^> 2 measurements moved  ]|  Night summary, solid
| [ outside your usual range   ]|
| [ Heart rate      58 bpm  ^  ]|
| [ |||||||||| *  Higher than  ]|  band + marker + label
| [ Respiratory    15.1 /min ^ ]|
| [ ||||||||||*   Higher than  ]|
| [ SpO2           96 %     o  ]|
| [ ||||*|||||||  Usual        ]|
| [ Sleep          7 h 05 min o]|
| [ |||*||||||||  Usual        ]|
| [ Last 7 nights (v)(v)-(v)<^^>]|
|                              |
| ( Last night History Settings)|  floating glass pill
+------------------------------+
```
With 5-6 metrics the column just grows; the summary stays first. Tap on a metric row morphs it into Metric detail
(shared bounds on the card and the band).

**CompactShort (`w < 600`, `h < 480`, e.g. split screen half)** - same order, no large title, summary and the first
metric visible without scrolling; the floating bar auto-hides on scroll. Metric rows become a single line
(name - value - icon) with the band in a second line only if width >= 360; at 200 % font rows wrap to 3 lines.

**Medium (600-839)** - rail; summary card spans the content width (max 600 for its text); metric rows become a
**2-column grid of metric tiles** (each tile >= 260 dp; with an odd count the last tile spans both columns). Recent
nights strip becomes a 14-night strip beside the summary if width allows (>= 720), else a 7-night strip below it
(the owner's 692 dp inner screen: below). Tap on a tile ->
Metric detail as a full pane (single pane, morph).
```
+--------------------------------------------------+
|(R)| (Last night)                                 |
|(a)| [ Night of Nov 24-25  <^^> 2 measurements  ] |
|(i)| [ Heart rate 58 ^ ] [ Respiratory 15.1 ^   ] |
|(l)| [ ||||||||| *     ] [ |||||||||*           ] |
|   | [ SpO2 96 o       ] [ Sleep 7 h 05 o       ] |
|   | [ ||||*|||||      ] [ |||*|||||||          ] |
|   | [ Last 7 nights  (v)(v) - (v)(v)(v) <^^>   ] |
+--------------------------------------------------+
```

**Expanded (840-1199)** - list-detail via `NavigableListDetailPaneScaffold<MetricKey>`. List pane (320-400):
summary + compact metric rows + recent nights strip (the list pane is narrower than 7 x 48 dp + padding, so the
strip is one target that opens History, `screens.md` 4). Detail pane (rest, chart max 840): Metric detail of the
selected metric. Default selection when the user has not chosen: first metric that moved outside, else the first
metric; the default is **not** pushed to the navigator (so folding back to compact shows Home, not a detail screen).
If the two-pane rule fails (large font), it renders the Medium layout.
```
+-------------------------------------------------------------------+
|(R)| (Last night)                                                  |
|(a)| [ Night of Nov 24-25] | Sleeping heart rate                   |
|(i)| [ <^^> 2 moved      ] | [ 58 bpm ^ Higher than usual        ] |
|(l)| [>Heart rate 58 ^   ] | [ usual 49-55 bpm                   ] |
|   | [ Respiratory 15 ^  ] | ( 7 | 30 | 90 nights )                |
|   | [ SpO2 96 o         ] | [  chart with band, 30 nights       ] |
|   | [ Sleep 7 h 05 o    ] | [  ......*...                       ] |
|   | [ Last 7 nights     ] | [ How your usual range is computed  ] |
+-------------------------------------------------------------------+
```

**Large (1200-1599)** - as Expanded plus a **supporting pane** (320) at the end: "Recent nights" list (last 14
nights with marks, tap opens History at that night). Detail pane chart capped at 840; extra width goes to margins
around the detail pane.

**ExtraLarge (>= 1600)** - same three panes; the whole layout is centered with max width 1600; outer space is plain
background (no stretched cards).

**WideShort (`w >= 600`, `h < 480`, phone landscape)** - rail (icons); two side-by-side columns without vertical
stacking: left (360) summary + recent strip, right metric rows (scrolls). If the two-pane rule passes for list-detail
(e.g. 915 x 412 at font 1.0) the right column is a metric list and tapping opens detail in place of it (single
detail pane with a back affordance), because a 412 dp height cannot hold list + chart + text in a third column.

**Tabletop** - top half: Night summary large (night label, headline, night glyph) and the last 7 nights as a
view-only glyph row; no controls in the top half. Bottom half: metric rows (scroll), "See all", floating bar. The
fold area plus 12 dp above and below stays empty.
```
+------------------------------------------+
| Night of Nov 24-25                       |
| <^^> 2 measurements moved outside        |
|      your usual range                    |  top half: view only
| (v) (v) - (v) (v) (v) <^^>               |
|==================== fold ===============|
| [ Heart rate 58 bpm  ^ Higher than usual]|
| [ Respiratory 15.1   ^ Higher than usual]|  bottom half: scroll
| [ SpO2 96 %          o Usual           ] |
|      ( Last night  History  Settings )   |
+------------------------------------------+
```

**Book (vertical separating fold)** - start half: summary + metric list (+ floating bar centered in this half if the
rail would leave < 360 dp); end half: Metric detail of the selected metric. Pane boundary = fold; 12 dp clearance on
both sides of the hinge.
```
+---------------------++---------------------+
| [ Night of Nov 24-25] || Sleeping heart rate |
| [ <^^> 2 moved     ] || [ 58 bpm ^ Higher ] |
| [>Heart rate 58 ^  ] || ( 7 | 30 | 90 )     |
| [ Respiratory 15 ^ ] || [ chart + band    ] |
| [ SpO2 96 o        ] || [ How it works    ] |
| (LastN Hist Sett)  ||                     |
+---------------------++---------------------+
```

**Glance (flip cover screen, tiny freeform window)** - no shell except the "More" button (`action_more`, 48 dp;
opens a glass sheet with History and Settings). Content: night label, headline with the night glyph, one line per
metric (status glyph + short name + value + status label; the label wraps below the value when narrow), one action.
No bands below 300 dp width; from 300 dp a thin band per metric. Text never shrinks; the content scrolls at large
font. Tapping a metric line opens a glance Metric detail (7-night glyph row with a thin band and the usual-range
text). States show only title, body and one button.
```
+------------------------+
| Nov 24-25        (...) |  "More": sheet with History, Settings
| <^^> 2 measurements    |
|  moved outside your    |
|  usual range           |
| ^ Heart rate  58       |
|     Higher than usual  |
| ^ Breathing   15.1     |
|     Higher than usual  |
| o SpO2  96   Usual     |
| o Sleep 7 h 05 Usual   |
+------------------------+
```

**States on Home in every mode.** A state card (solid, icon + title + body + one primary action, `states.md` 2)
takes the place of the Night summary; metric rows for unaffected metrics still show (for example "metric not
provided by the source" collapses only that row with its own label). In two-pane modes the state card is at the top
of the list pane and the detail pane shows the longer explanation of the state. Learning period: the learning card
("{n} of 7 nights", `LearningProgress` segments + text) replaces the summary; metric rows show last night's values
with a dotted band outline and the label "Learning, {n} of 7" (`screens.md` 7.6).

### 5.2 Metric detail

Blocks (order and copy in `screens.md` 7.3): title (metric name), **header card** (last night's value, unit,
status chip, usual range, one `detail_*` sentence; the morph target of the tile), **period selector** (7 / 30 / 90
nights, segmented, each segment >= 48 dp), **chart** (nightly values as points over a solid surface, never a line
across missing nights; range band as a filled corridor; out-of-range nights marked with a shape, not only color; a
text summary below for TalkBack and everyone), **stats row**, **explanation** ("How your usual range is computed",
"What 'outside your usual range' means", not-a-medical-device note).

Chart heights: compact 220, medium 240, expanded and up 280, height-expanded (>= 900) +40, never taller than 45 % of
the window height; tabletop = top half minus 24. Chart width = pane width minus gutters, capped at 840.

**Compact** - single column: header card, selector, chart, text summary, stats, explanation. Top bar has back.
```
+------------------------------+
| (<  Sleeping heart rate  (i))|
| [ 58 bpm  ^ Higher than    ] |  header card (morph target)
| [ usual  usual 49-55 bpm   ] |
| ( 7 | 30 | 90 nights )       |
| [ chart 220dp, band, *     ] |
| 3 of 30 nights were outside  |  text summary
| the range, most recently ... |
| [ How the range works  >   ] |
| ( Last night History Settings)|
+------------------------------+
```

**CompactShort** - selector in the top bar row (end), chart takes the visible height minus 24 dp; text below on
scroll.

**Medium** - single pane: header card and stats side by side on top; chart full content width (max 840) with the
selector right-aligned above it; below it **two columns**: text summary (start), explanation cards (end), each max
600.

**Expanded / Large / XL** - Metric detail is the detail pane of Home (section 5.1), or of History when opened from a
night. Inside the pane: same as Medium if the pane is >= 640 wide, else as Compact.

**WideShort** - chart start (60 % of width, full available height), scrolling column end (40 %): header card,
selector, summary, explanation.
```
+-----------------------------------------------------+
|(R)| ( < Heart rate)          ( 7 | 30 | 90 )        |
|   | [ chart, full height ] | [ 58 bpm ^ Higher   ] |
|   | [ ...*..             ] | [ usual 49-55       ] |
|   | [                    ] | How the range works  |
+-----------------------------------------------------+
```

**Tabletop** - the owner's mandatory case: top half = chart only (plus title and last night's value and status as a
large label); bottom half = period selector, step buttons, text summary, stats, explanation (scroll), floating bar. Chart is
touch-scrubbable but scrubbing also works from the bottom half: Left/Right step buttons ("Previous night",
"Next night", 48 dp) under the selector, so nothing needs to be touched near the fold.
```
+------------------------------------------+
| Sleeping heart rate   58 bpm ^ Higher    |
| [ chart, band, 30 nights, *            ] |  view only
|==================== fold ===============|
| ( 7 | 30 | 90 nights )   (<) (>)          |
| 3 of 30 nights were outside the range.   |
| [ How the range works                  ] |
|      ( Last night  History  Settings )   |
+------------------------------------------+
```

**Book** - when opened from Home: end half (see Home Book). When it is the only content (deep link from a
notification): start half = header card + selector + chart, end half = summary, stats + explanation.

**Glance** - title + value + status glyph and label, 7-night glyph row with a thin band (no selector; 7 nights
fixed), one line of usual range text, "Open on the main screen" hint is **not** shown (the app cannot move itself between displays;
the user unfolds and state carries over).

### 5.3 History

Two views, switched by a segmented control "List / Calendar": **List** (nights, newest first, grouped by month;
each row: night label, night glyph, status label "2 moved" / "All usual" / state label) and **Calendar** (month
grid, each day a 48 dp cell with a night glyph). Selecting a night opens **Night detail** (that night's Home-like summary and
metric rows; a metric row opens Metric detail with that night pre-selected on the chart).

| Mode | Layout |
|---|---|
| Compact | single pane; List or Calendar; tap -> Night detail route (morph from row) |
| CompactShort | List by default (Calendar allowed, scrolls); segmented control in the top bar |
| Medium | single pane; List rows widen to show a mini mark per metric (icon + shape), Calendar shows the month grid at 48-64 dp cells with the selected night's summary **below** (reflow) |
| WideShort | Calendar start (360, scrolls), selected night summary end |
| Expanded | list-detail `NightKey(epochDay)`: list 360 (List or Calendar), detail = Night detail |
| Large / XL | list 400 + Night detail + supporting pane = Metric detail of the metric chosen in Night detail (or none) |
| Tabletop | top half: Night detail summary of the selected night (view); bottom half: List/Calendar (controls) |
| Book | start half: List/Calendar; end half: Night detail |
| Glance | List only, compact rows (night label + glyph + count); tap -> glance Night detail |

Expanded:
```
+-------------------------------------------------------------------+
|(R)| (History)              ( List | Calendar )                    |
|(a)| November               | Night of Nov 22-23                   |
|(i)| [>Nov 22-23 <^^> 2 moved]| [ <^^> 2 measurements moved ...  ] |
|(l)| [ Nov 21-22 (v) All usual]| [ Heart rate 58 ^   ||||||||| *   ] |
|   | [ Nov 20-21 -  No data ] | [ Respiratory 15 ^  |||||||||*    ] |
|   | [ Nov 19-20 (v) All usual]| [ SpO2 96 o         ||||*|||||    ] |
+-------------------------------------------------------------------+
```

### 5.4 Settings

Groups (content in `screens.md` 7.7): Data source (source, Health Connect permissions, last read, set up again),
Notifications (night results, problems reading data), Sensitivity (three presets; advanced: range window 14-28
nights, per-metric "Use for comparison", preview), Appearance (Theme System/Light/Dark; Glass effects
Automatic/Off with the reason it is off; motion note), About (not a medical device, licenses, version).

| Mode | Layout |
|---|---|
| Compact | grouped single column (solid group cards); group with sub-options (Sensitivity, Source) opens its own screen |
| CompactShort / Glance | same, plain list, no illustrations; glance shows only Source, Notifications, About |
| Medium | single column max 600 centered in the content area (start-aligned if a vertical fold is present, section 6.3); Sensitivity expands inline instead of a sub-screen |
| WideShort | same as Medium |
| Expanded / Large / XL | list-detail: groups 320 | group content max 600; About opens in the detail pane |
| Tabletop | top half: current source and sync status (view); bottom half: groups list |
| Book | start half: groups; end half: group content |

Sensitivity controls (sliders/steppers) use draft state: changes apply on release and are announced; the draft
survives fold/unfold (section 8).

### 5.5 Onboarding (outside the shell)

Steps (content in `screens.md` 7.5 and `states.md` 7.3): 1 Welcome (what the app does and does not: not a medical
device), 2 How it works, 3 Source (with the Health Connect availability check), 4 Permissions, 5 Notifications,
6 Ready (past nights found, or learning starts). Step indicator = visible text `onb_step` "Step 2 of 6" plus dots
(dots are decorative).

| Mode | Layout |
|---|---|
| Compact | top: illustration (own abstract shapes, 160 dp, hidden at font >= 1.5 or height < 640), text, primary button pinned at the bottom (above nav inset), secondary as text button |
| CompactShort / Glance | no illustration; text scrolls; buttons stacked at the end of content (not pinned when height < 400) |
| Medium | two columns: start = illustration + step indicator (40 %), end = text + actions (max 480) |
| Expanded / Large / XL | centered card max 840: same two columns; outside is background |
| WideShort | two columns, illustration replaced by the step title in large type |
| Tabletop | top half: illustration + step title (view); bottom half: text + buttons |
| Book | start half: illustration + step indicator; end half: text + buttons |

Source selection list rows are radio rows >= 56 dp. Permission step hands off to Health Connect; returning keeps the
step (section 8).

### 5.6 Widget (optional, last)

Only after everything above is done. Glance layout content in a Glance app widget; sizes and the API 37 bitmap limit
per `large-screens.md` section 7. No vendor cover-screen widget metadata.

---

## 6. Fold, hinge and posture rules

1. **Separating fold** (`isSeparating = true`): panes split exactly at the hinge (`ListDetailPaneScaffold` does this
   through the pane scaffold directive's hinge handling **[hypothesis: verify on the Foldable emulator and, with
   permission, on the phone]**; our custom layouts use `separatingVerticalHingeBounds` / `allHorizontalHingeBounds`
   directly). 12 dp clearance on each side of the hinge bounds. Nothing - text, value, band, chart, control - crosses.
2. **Occluding fold** (`occlusionType = FULL`): nothing is drawn inside the bounds (background only).
3. **Non-separating fold (inner screen FLAT)**: single-column text and charts may cross the crease (it is flat and
   readable), but **controls must not straddle it**: centered small controls (period selector, segmented
   List/Calendar, the floating bar, dialog buttons) are start-aligned or split so that their bounds do not intersect
   the fold +- 12 dp. Implemented with a `Modifier.avoidFold()` that offsets a control to the nearer side.
4. **Tabletop**: top half = view only (no interactive element), bottom half = all controls, scrolling lists and the
   floating bar. Sheets stay in the bottom half. Dialogs: in the bottom half, max height = half minus 24 dp.
5. **Book**: start half = navigation/list, end half = detail. Dialogs in the end half.
6. Posture changes animate panes with the standard pane motion; with "remove animations" they switch instantly.

---

## 7. Special cases

| Case | Typical size (dp) | Mode | Notes |
|---|---|---|---|
| Flip cover screen | 260-400 x 260-480 | Glance | notifications remain the main cover-screen channel (`large-screens.md` 11.2); if the system lets the app run there, Glance must be complete and finished-looking |
| Very short height | any w x < 480 | CompactShort / WideShort; < 320 -> Glance | no large titles, bar hides on scroll, sheets become dialogs when `w >= 600`, charts min 160 dp tall else replaced by text summary + sparkline |
| Split screen, phone | ~ 412 x 430 | CompactShort | as above |
| Split screen, inner screen | ~ 346 x 763 (520 dpi) or 475 x 1050 (378 dpi) | Compact | same as the outer screen layout |
| Split screen, tablet | ~ 640 x 800 | Medium | |
| Freeform tiny window | 220-400 x 220-480 | Glance | min window 220 x 220 dp declared; content scrolls, nothing clips |
| Freeform resizing | continuous | any | layout recomputed on every size; state kept (section 8); no reload, no flicker |
| Tablet portrait | 800 x 1280 | Medium, height expanded | 2-column metric grid, taller chart; History single pane with reflow; Settings centered column |
| Tablet landscape | 1280 x 800 | Large | list-detail + supporting pane |
| Small tablet landscape | 1024 x 640 | Expanded | list-detail |
| Desktop / Chromebook | 1600 x 900 | ExtraLarge | centered 1600, mouse + keyboard first-class |
| Phone landscape | 915 x 412 | WideShort | rail (deviation 1, section 4.2) |

---

## 8. State preservation (fold, unfold, resize, rotate, recreate)

Requirement: fold/unfold keeps the same screen, scroll position, selected period and metric, no reload.

### 8.1 Where state lives

| State | Holder | Survives |
|---|---|---|
| Night results, baselines, chart series, sync/permission state | `ViewModel` (`StateFlow`, `stateIn(viewModelScope, WhileSubscribed(5_000))`), backed by Room | config changes; reloaded from Room (not Health Connect) after process death |
| Current top-level section | nav back stack (saved) | everything |
| Selected metric (Home) | `rememberListDetailPaneScaffoldNavigator<MetricKey>()`, `MetricKey` is `@Parcelable` (metric id string) | everything |
| Selected night (History) | navigator with `NightKey(epochDay: Long)` `@Parcelable` | everything |
| Selected period (7/30/90) | `MetricDetailViewModel` `SavedStateHandle["period"]`, per metric | everything |
| Chart scrub position (selected night on chart) | `rememberSaveable` epochDay, hoisted above the layout switch | everything |
| History view (List/Calendar), shown month | `rememberSaveable` (enum name, `YearMonth` as string) in `HistoryRoute` | everything |
| Scroll positions | hoisted per screen (section 8.2) | everything |
| Open sheet / dialog and which one | `rememberSaveable` enum at the screen route | everything |
| Settings drafts (sensitivity before release) | `rememberSaveable` | everything |
| Onboarding step, "waiting for permission result" | `OnboardingViewModel` `SavedStateHandle` | everything incl. returning from Health Connect |
| Expanded/collapsed text sections | `rememberSaveable` booleans keyed by section id | everything |

### 8.2 Rules

1. **Hoist above the layout switch.** Every saveable UI state of a screen is created in the screen's route
   composable, *before* `when (layoutSpec.mode)`. Layout variants receive state as parameters. A `rememberSaveable`
   inside a variant-only branch is a bug (it is lost when the variant changes) and is checked in review.
2. **Scroll by anchor key, not index.** Layout variants use different containers (column, 2-column grid, list pane),
   so indexes do not map. Each scrollable list has stable item keys (`metric:<id>`, `night:<epochDay>`,
   `settings:<group>`). The route stores the first fully visible key (`rememberSaveable`, updated with
   `snapshotFlow` on scroll) and, when a new layout's list state is created, calls `requestScrollToItem(indexOf(key))`
   before the first frame. When the same container persists (e.g. compact -> compact split), the saveable
   `LazyListState` is reused directly.
3. **No loading as a side effect of size.** No `LaunchedEffect(windowSize)` that fetches; data flows from the
   ViewModel regardless of layout. Health Connect is read only by the daily worker and explicit "Check again".
4. **Detail visibility across modes.** Expanded -> Compact: if the user explicitly opened a detail (navigator has a
   Detail destination), Compact shows that detail screen; if the detail pane only showed the default selection,
   Compact shows the list (Home/History). Compact -> Expanded: the open detail becomes the detail pane with the same
   key, the list pane scrolls to make the selected row visible. Back from a detail in Compact returns to the list with
   the previous scroll anchor.
5. **Tabletop/Book enter and exit** never change the destination, only the arrangement.
6. **Glance <-> full**: the destination and selection are kept; Glance renders the closest glance view (Home ->
   glance Home, Metric detail -> glance Metric detail, Settings -> glance Settings list, Night detail -> glance
   Night detail).
7. `configChanges` avoids recreation on fold, but all of the above is also verified with forced recreation
   (`StateRestorationTester` in tests; "Don't keep activities" on the emulator).
8. Morph (shared bounds) runs only for user navigation, not for layout changes caused by fold/resize; layout
   changes use a short pane crossfade (or none with animations off).

---

## 9. Owner's device mapping

OPPO CPH2765, book foldable, Android 17, ColorOS 17.0 **[verified, STATUS.md / env.txt]**.

| Screen / posture | px | density | dp (approx.) | Mode |
|---|---|---|---|---|
| Outer, CLOSED | 1140 x 2616 | 378 (override) | 482 x 1107 | Compact |
| Outer, landscape | 2616 x 1140 | 378 | 1107 x 482 | Expanded width, height medium (just above 480): Expanded layout with short-height chart rules **[hypothesis: actual window height after insets may fall under 480 -> WideShort; both are tested]** |
| Inner, OPENED flat | 2248 x 2480 | 520 physical | 692 x 763 | Medium (+ non-separating vertical fold at x ~ 346 **[hypothesis: whether ColorOS reports a FoldingFeature when flat]**) |
| Inner, OPENED flat | 2248 x 2480 | 378 if overridden | 951 x 1050 | Expanded (falls back to one pane at font 1.35, section 3.3) |
| Inner, HALF_OPENED, held as a book | as above | 520 / 378 | 692 x 763 / 951 x 1050 | Book (halves 346 / 475) |
| Inner, HALF_OPENED, held landscape on a table | rotated | 520 / 378 | 763 x 692 / 1050 x 951 | Tabletop (halves ~346 / ~475 tall) |
| Inner, split screen | | 520 / 378 | ~346 x 763 / ~475 x 1050 | Compact |
| TENT | ? | ? | ? | **[hypothesis]** whatever window/fold is reported |

Inner-screen references are not available (only folded ones), so the inner layouts are validated by screenshot
tests and, with permission, a short on-device check.

---

## 10. Keyboard, mouse, trackpad

Target: Tier 3 fully, Tier 2 keyboard/mouse items (`large-screens.md` section 9-10).

### 10.1 Focus order

- Single pane with bottom bar: top bar (back, actions) -> content in reading order (summary -> metric rows ->
  history strip) -> floating bar items. The floating bar is last in traversal although it is drawn on top.
- Rail layouts: rail -> top bar actions -> list pane -> detail pane (header card -> period selector -> chart -> summary ->
  explanation links) -> supporting pane. Each pane is a `focusGroup()`; **F6 / Ctrl+Tab** moves between rail and panes.
- Tabletop: top half has no focusable elements except the chart (focusable for its text summary); bottom half then bar.
- Book: start half (list, bar if there) -> end half.
- Glance: More button -> metric lines -> action.
- After a layout change, focus stays on the same logical element (same key) if it still exists; otherwise it moves
  to the pane title.
- Arrow keys: rail/bar items (Up/Down, Left/Right), calendar grid (2D, Page Up/Down = previous/next month), chart
  (Left/Right = previous/next night, Home/End = first/last night of the period; the text summary of the focused
  night is announced), segmented controls (Left/Right).

### 10.2 Shortcuts (listed in the Meta+/ helper via `onProvideKeyboardShortcuts`)

| Keys | Action |
|---|---|
| Ctrl+1 / Ctrl+2 / Ctrl+3 | Last night / History / Settings |
| Esc | close sheet, dialog or menu; otherwise back from a detail (`Keyboard_Exit`) |
| Alt+Left | back |
| `[` / `]` in Metric detail | shorter / longer period (7 / 30 / 90) |
| Alt+Up / Alt+Down in list-detail | previous / next item in the list pane, detail follows |
| Left / Right in Night detail | previous / next night (same as the top bar chevrons) |
| Ctrl+R on `E-SYNC`, `E-NODATA`, `B-WAIT`, `B-READ-FAILED` | Check now (`action_check_now`) |

### 10.3 Hover and pointer

- All clickable rows, tiles, chips and shell items: `state.hover` on solid, `state.glassHover` on glass (rim and
  tint brighten, no grey layer), `pointerHoverIcon(PointerIcon.Hand)` (`tokens.md` 2.8, 10).
- Chart: hover shows a vertical guide (`chart.guide`) and the `ChartTooltip` (solid, `material.solid.tooltip`) with
  date, value and status label; click pins the selection (same state as touch scrub).
- Mouse wheel scrolls lists and panes; horizontal wheel/trackpad pans the chart only when the period exceeds the
  visible width (never in 7/30). Scrollbars appear on hover in panes taller than the window.
- Focus indicator: 2 dp ring, contrast >= 3:1 against both glass and solid surfaces (token in `tokens.md`), offset
  2 dp, follows the element's shape.
- Touch targets stay >= 48 dp with a mouse too; no hover-only actions (everything reachable by touch and keyboard).

---

## 11. Screenshot-test device matrix

Rendered on the JVM (Robolectric, as decided in ADR 0001) with qualifiers `w<W>dp-h<H>dp-<density>` and a
fake `LayoutInputs` for folds (posture cannot be produced by Robolectric; the fold is injected through the same
`LayoutInputs` used in production, so the layout code path is identical). Glass via `RenderEffect` needs API 31+;
Robolectric's native graphics (SDK 35) with `robolectric.pixelCopyRenderMode=hardware` does render the Haze blur
**[verified 2026-11-25: `HazeBlurProbeTest`, stripes blurred to mid grey while the backdrop halves stay apart]**, so
the JVM screenshots include real glass; the probe fails if that ever changes, and then Robolectric renders the
fallback variant only.

| Id | Represents | W x H dp | Density | Posture / fold | Mode |
|---|---|---|---|---|---|
| `phone` | typical phone | 412 x 915 | 420 dpi (2.625) | none | Compact |
| `phone-small` | small phone | 360 x 640 | 320 dpi (2.0) | none | Compact |
| `owner-outer` | owner, folded | 482 x 1107 | 378 dpi (2.3625) | none | Compact |
| `phone-land` | phone landscape | 915 x 412 | 420 dpi | none | WideShort |
| `owner-outer-land` | owner, folded, landscape | 1107 x 482 | 378 dpi | none | Expanded (short) |
| `phone-split` | split screen half | 412 x 430 | 420 dpi | none | CompactShort |
| `flip-cover-square` | flip cover screen | 340 x 352 | 320 dpi | none | Glance |
| `flip-cover-tall` | narrow vertical cover | 256 x 480 | 240 dpi (1.5) | none | Glance |
| `freeform-tiny` | tiny freeform window | 260 x 260 | 320 dpi | none | Glance |
| `flip-main-tabletop` | flip phone half-opened | 412 x 915 | 420 dpi | horizontal, separating, y = 457 | Tabletop |
| `owner-inner` | owner, open, 520 dpi | 692 x 763 | 520 dpi (3.25) | vertical, FLAT non-separating, x = 346 | Medium |
| `owner-inner-378` | owner, open, overridden | 951 x 1050 | 378 dpi | vertical, FLAT non-separating, x = 475 | Expanded |
| `owner-book` | owner, half-opened book | 692 x 763 | 520 dpi | vertical, separating, x = 346 | Book |
| `owner-book-378` | same, overridden density | 951 x 1050 | 378 dpi | vertical, separating, x = 475 | Book |
| `owner-tabletop` | owner, half-opened on table | 763 x 692 | 520 dpi | horizontal, separating, y = 346 | Tabletop |
| `owner-tabletop-378` | same, overridden density | 1050 x 951 | 378 dpi | horizontal, separating, y = 475 | Tabletop |
| `owner-inner-split` | inner screen split | 346 x 763 | 520 dpi | none | Compact |
| `foldable-1-1` | Material test config | 802 x 802 | 420 dpi | vertical, FLAT, x = 401 | Medium |
| `foldable-4-3` | Material test config | 1024 x 768 | 420 dpi | vertical, FLAT, x = 512 | Expanded |
| `tablet-port` | tablet portrait | 800 x 1280 | 320 dpi | none | Medium (tall) |
| `tablet-land` | tablet landscape | 1280 x 800 | 320 dpi | none | Large |
| `tablet-small-land` | small tablet | 1024 x 640 | 320 dpi | none | Expanded |
| `tablet-split` | tablet split | 640 x 800 | 320 dpi | none | Medium |
| `desktop` | Chromebook / desktop window | 1600 x 900 | 160 dpi | none | ExtraLarge |

Dimensions to combine (from the owner's requirements): screen and state x theme (light, dark) x language (en, ru)
x font scale (1.0, 1.35, 2.0) x material (glass, fallback) x device above. The full product is too large to render
on every build, so:
- **Full combination** on `owner-outer`, `owner-inner`, `owner-tabletop`, `flip-cover-square`, `tablet-land`.
- **Reduced** on all other devices: both themes, `en` + `ru` at font 2.0 (longest text, largest type), fallback +
  glass for Home only. `tablet-port`, `owner-book` and `flip-cover-tall` additionally render every screen at font 1.0
  in both effect modes (the owner's required window list: phone, flip cover, open book, half-opened, tablet portrait
  and landscape).
- **Grayscale** variant of Home and Metric detail (every night and metric state) on `owner-outer`, both themes: the
  states must stay distinguishable (`principles.md` 4).
- Every state from `states.md` on at least `owner-outer`, `owner-inner`, `flip-cover-square` in both themes.
- Layout assertions (not only images): no text node is clipped (`TextLayoutResult.hasVisualOverflow == false`),
  no interactive node intersects fold bounds +- 12 dp, every interactive node >= 48 x 48 dp, contrast >= 4.5:1
  for text (checked from the token pairs, see `tokens.md`).
- State preservation tests (Compose UI tests with `StateRestorationTester` and a size-switching test host): Compact
  -> Expanded -> Tabletop -> Compact keeps section, selected metric, period, chart selection and scroll anchor.

---

## 12. Open items (to verify, not decided here)

- Whether ColorOS 17 reports a `FoldingFeature` on the inner screen when FLAT, and what TENT reports
  **[hypothesis]**; on-device check only with the owner's permission.
- Actual inner-screen density used by the owner (520 physical or an override) **[hypothesis]**.
- Haze blur cost on the inner screen at two panes plus rail (frame timing during scroll and fold transition);
  ADR 0003 requires the measurement before release.
- ~~Robolectric support for `RenderEffect` blur in screenshots~~ verified: it renders (`HazeBlurProbeTest`).
