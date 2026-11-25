# Reference analysis: ColorOS 17 system apps on the owner's phone (outer screen, dark theme)

Date: 2026-11-25. Purpose: measured input for NightBaseline's design tokens (`docs/design/`), complementing the desk
research in `docs/research/design-coloros17.md` (style principles, Iris/COUI-Kit numbers) and
`docs/research/large-screens.md`.

The reference material itself (screenshots, UI hierarchy dumps, screen recordings) lives only in the git-ignored
`.data/design-refs/folded/` (hard rule 5) and is OPPO's UI: it is never copied into the repository, and this document
describes only measurements and patterns, not the personal content visible in it. Nothing here is a vendor resource;
the numbers are used as calibration targets for our own tokens.

## Evidence tags

- **[measured on device screenshot]**: derived from a 1:1 device screenshot (PNG) or from the exact px bounds in the
  matching uiautomator dump (XML), converted to dp. Pixel-level edges carry about ±1 px (±0.4 dp) of anti-aliasing.
- **[measured on device recording]**: derived from frames of the two screen recordings (H.264, decoded with ffmpeg);
  colours there are shifted by compression and timing is limited by the recorder (frames are emitted on change only).
- **[estimate]**: an inference from the measurements (font size from glyph height, curve type, blur strength,
  intent of a pattern). Treat as a hypothesis.

## Capture conditions and conversion

| Item | Value | Tag |
|---|---|---|
| Device / OS | OPPO CPH2765, Android 17 (SDK 37), ColorOS 17.0, device state CLOSED (outer screen) | [measured on device screenshot] (`env.txt`) |
| Screen | 1140 × 2616 px, density override 378 dpi (physical 520) | [measured on device screenshot] |
| Conversion | 1 dp = 378 / 160 = **2.3625 px**; screen = **482.5 × 1107.3 dp** (compact width class) | [measured on device screenshot] |
| System font scale | **1.35** (all text below was rendered at this scale) | [measured on device screenshot] (`env.txt`) |
| Theme | dark; animation scales 1.0 | [measured on device screenshot] |
| Material | Settings (Display, Sound, Accessibility, Date & time), Clock, Calculator, Compass, Health Connect home; recordings: Settings scroll (4.2 s) and Settings back transition (1.6 s) | |

Font sizes in sp are derived as `em_dp = cap_height_dp / 0.70` (assumed cap-height ratio of the system sans font) and
`sp = em_dp / 1.35` (font scale). The rendered size in dp is the more reliable number; the sp value depends on the
assumed ratio and on whether the element scales with the system font at all.

## Tool

`tools/research/refs_measure.py` (no embedded data; every number comes from the files given on the command line).
Run with the project venv, isolated: `mise exec -- python -I tools/research/refs_measure.py <cmd> ...`.

| Command | Use |
|---|---|
| `nodes <dump.xml> [--text]` | node bounds in px and dp; text is hidden unless `--text` (it can be personal) |
| `row` / `col <png> <fixed> <from> <to>` | run-length colours along a pixel row/column: edges, insets, rims, gradients |
| `radius <png> x0 y0 x1 y1 --corner tl` | circular fit of a rounded corner from the edge profile |
| `ink <png> x0 y0 x1 y1` | vertical glyph extent and glyph colour inside a box (text size and text colour) |
| `mode <png> x0 y0 x1 y1` | dominant colours of a region (surfaces) |
| `track <frames...> --y Y --color #hex` | follows a long same-colour run across video frames (sliding edges) |

Pillow and numpy were installed into the project venv (`.tooling/venv`). Video frames were decoded with the
Windows `ffmpeg.exe` through WSL interop into `.data/design-refs/folded/frames-*` (git-ignored).

---

## 1. Page structure and spacing (Settings pattern)

| Measure | px | dp | Tag |
|---|---|---|---|
| Status bar height | 107 | 45.3 | [measured on device screenshot] |
| Screen side margin to card edge | 38 | **16.1** | [measured on device screenshot] |
| Card width (482.5 dp screen) | 1064 | 450.3 | [measured on device screenshot] |
| Text start from screen edge | 77 | 32.6 | [measured on device screenshot] |
| Card inner horizontal padding (card edge to text, and trailing widget to card edge) | 39 | **16.5** | [measured on device screenshot] |
| Gap between two cards without a header | 38 | **16.1** | [measured on device screenshot] |
| Gap card bottom → section header row | 38 | 16.1 | [measured on device screenshot] |
| Section header row (text box 22.0 dp, 8 dp above, 8 dp below; touches the next card) | 90 | **38.1** | [measured on device screenshot] |
| Card bottom → next card top across a section header | 128 | 54.2 | [measured on device screenshot] |
| First card top below a collapsed top bar (bar ends at 97.4 dp) | 243 | 102.9 | [measured on device screenshot] |
| Bottom of a page: no extra footer surface; content simply ends | | | [measured on device screenshot] |

Pattern: one card per group with all rows inside it, separated by hairline dividers; groups are separated by 16 dp
of background, optionally with a quiet section header in between. Cards have no shadow and no border on a black
background [measured on device screenshot].

### 1.1 List rows (inside a card)

| Row type | Height dp | Tag |
|---|---|---|
| Single line title (+ chevron / switch / value) | **48.3** (114 px; text box 27.9 dp, 10.2 dp above and below) | [measured on device screenshot] |
| Same, as the last row in a card | 49.9 (extra 1.6 dp bottom) | [measured on device screenshot] |
| Title + 1-line summary | 72.4–75.8 | [measured on device screenshot] |
| Title + 2-line summary | 97.4 | [measured on device screenshot] |
| Title + 4-line summary (with switch) | 148.1 | [measured on device screenshot] |
| Slider row with title (title 31.7 dp box + 27.9 dp slider) | 71.5–81.3 | [measured on device screenshot] |
| Three-way choice row (icon 24 dp, label, radio; columns split by vertical hairlines) | 129.1 (content 89.3) | [measured on device screenshot] |
| Tab row (Accessibility) | 47.8 | [measured on device screenshot] |

- Vertical row padding is 10.2 dp (24 px) top and bottom at font scale 1.35; rows grow with text, there is no fixed
  height [measured on device screenshot].
- Trailing widgets are vertically centred on the whole row, including multi-line summaries [measured on device
  screenshot].
- Chevron: 11.9 × 24.1 dp box, glyph colour #5F5F5F, right edge at the card's inner padding [measured on device
  screenshot].
- Value text before a chevron or a up/down selector icon: secondary colour #969696, same size as the title
  [measured on device screenshot].
- Divider: **1 px hairline (0.42 dp)**, #484848 on #1A1A1A, from the text start to the inner end of the card
  (inset 16.5 dp on both sides) [measured on device screenshot].

### 1.2 Card corner radius

| Surface | Fit | Tag |
|---|---|---|
| Settings group card | r ≈ **19.3–20.4 dp** (45.6–48.2 px, rms ≤ 0.73 px) | [measured on device screenshot] |
| Clock alarm card | r ≈ 20.4 dp | [measured on device screenshot] |
| Floating bottom bar | r ≈ 28.4–28.6 dp = half of its 55.9 dp height (full pill) | [measured on device screenshot] |
| Switch, slider track, FAB, top-bar buttons | full pill / circle | [measured on device screenshot] |
| Corner shape | consistent with a circular arc at this resolution; the bottom bar's profile is slightly fuller than a circle near the straight edge, which may indicate corner smoothing (squircle). Not resolvable reliably | [estimate] |

Contrast (Google's Material 3 Health Connect app on the same phone): separate tiles 72 dp tall with 2.1 dp gaps,
outer corners 20.6 dp, inner corners 4.6 dp, background #171A1F, tiles #282C34 [measured on device screenshot]. The
ColorOS apps instead use one continuous card with hairline dividers. Both use about 20 dp outer radius and a 16 dp
screen margin.

### 1.3 Clock app (card list with large title)

| Measure | dp | Tag |
|---|---|---|
| Expanded app bar total (status bar + toolbar + collapsing title area) | 167.6 | [measured on device screenshot] |
| Large title cap top / subtitle box | 99.9 / 137.1–155.8 | [measured on device screenshot] |
| First card top | 175.7 (8 dp below the app bar) | [measured on device screenshot] |
| Card height (two-line content + switch) | 90.6 | [measured on device screenshot] |
| Gap between cards | **11.9** (28 px) | [measured on device screenshot] |
| Card content inset (horizontal) | 11.9 (vs 16.5 in Settings) | [measured on device screenshot] |
| Card content inset (vertical) | 10.2 | [measured on device screenshot] |
| Disabled item | whole content dimmed to #5F5F5F, card unchanged | [measured on device screenshot] |

---

## 2. Top bar

| Measure | Value | Tag |
|---|---|---|
| Toolbar height (below the status bar) | **52.1 dp** | [measured on device screenshot] |
| Collapsed app bar total | 97.4 dp | [measured on device screenshot] |
| Back button | **40.2 dp circle**, left margin 15.7 dp, vertically centred in the toolbar | [measured on device screenshot] |
| Back button surface | vertical gradient #1F1F1F (top) → #2A2A2A (bottom), 1 px lighter rim (#313131–#353535 top, #3E3E3E bottom), soft drop shadow ≈ 5 dp below fading into black | [measured on device screenshot] |
| Back arrow glyph | ≈ 20 dp wide, ≈ 1.5 dp stroke, #E9E9E9 | [measured on device screenshot] |
| Title start | 65.6 dp (9.7 dp gap after the button), single line, left-aligned | [measured on device screenshot] |
| Title text | cap 13.1 dp → em ≈ 18.7 dp → **≈ 14 sp** at scale 1.35; medium weight; #E3E3E3 | [estimate] |
| Trailing "more" button (Clock) | 40.2 dp circle, right margin 15.6 dp, same surface as back | [measured on device screenshot] |
| Utility apps (Calculator, Compass) | larger circular buttons **55.0 dp**, margin 22.0 dp, same convex surface | [measured on device screenshot] |
| Tabs under the top bar | row 47.8 dp; centred group; selected label #E6E6E6 with a 2.1 dp underline exactly as wide as the label (55 dp); unselected labels grey, no underline | [measured on device screenshot] |

The top bar has **no solid background**: content scrolls underneath it and is blurred progressively (section 7).
The buttons are the only opaque elements of the bar [measured on device recording].

## 3. Typography (rendered at font scale 1.35)

| Style | Cap height dp | Rendered em dp | ≈ sp | Colour (dark) | Line pitch | Tag |
|---|---|---|---|---|---|---|
| Large title (Clock) | 22.9 | 32.7 | ≈ 24 (≈ 32 if exempt from scaling) | #E6E6E6, bold | | [estimate] |
| Top bar title | 13.1 | 18.7 | ≈ 14 | #E3E3E3, medium | | [estimate] |
| Row title | 14.8 | 21.1 | **≈ 16** | #E9E9E9, medium | box 27.9 dp (×1.32) | [estimate] |
| Row value (trailing) | ≈ 14 | ≈ 20 | ≈ 15 | #969696, regular | | [estimate] |
| Row summary | 12.7 | 18.1 | **≈ 13–14** | #969696, regular | 24.5 dp (×1.35) | [estimate] |
| Section header | 11.4 | 16.3 | **≈ 12** | #8A8A8A on black, medium | box 22.0 dp | [estimate] |
| Large-title subtitle (Clock) | 10.2 | 14.6 | ≈ 11 | #8A8A8A | box 18.6 dp | [estimate] |
| Bottom bar label | 8.0 | 11.4 | ≈ 8.5 at 1.35, i.e. likely ≈ 10–11 sp with capped scaling | selected #E9E9E9 bold, unselected #C0C0C0 | box 13.5 dp | [estimate] |
| Calculator result digits | 86.3 (digit height) | ≈ 120 | display size, likely exempt from scaling | #FFFFFF, medium | | [measured on device screenshot] (sp: [estimate]) |

Observations: a single sans family with two or three weights; titles medium/bold, summaries regular; hierarchy comes
from size and grey level rather than colour [measured on device screenshot]. The bottom bar label is the smallest
text and does not grow as much as body text at scale 1.35 (capped scaling) [estimate].

## 4. Floating bottom navigation bar and FAB

### 4.1 Clock (4 destinations)

| Measure | Value | Tag |
|---|---|---|
| Bar size | **327.2 × 55.9 dp**, horizontally centred (not full width: 77.5 dp free on each side) | [measured on device screenshot] |
| Bottom offset | **16.1 dp** from the screen's bottom edge (gesture handle area included) | [measured on device screenshot] |
| Shape | full pill (r ≈ 28 dp) | [measured on device screenshot] |
| Surface | #1B1B1B–#1D1D1D with a vertical gradient brightening to #232323 at the bottom; 1 px top rim #363636; 2–3 px bottom rim #3B3B3B–#3D3D3D; shadow ≈ 4 dp below | [measured on device screenshot] |
| Items | 4 × **69.8 × 47.8 dp**, pitch 77.9 dp, gaps 8.1 dp, side padding 11.9 dp | [measured on device screenshot] |
| Item content | icon 24.1 dp on top (3.8 dp from item top), label box 13.5 dp below (2 dp gap) | [measured on device screenshot] |
| Selection | selected: filled icon and bold label #E9E9E9; unselected: outline icon and label #C0C0C0. **No pill indicator** behind the selected item | [measured on device screenshot] |
| Translucency | not verifiable here: the content behind the bar is pure black | [estimate] |

### 4.2 Compass (2 destinations, utility app)

| Measure | Value | Tag |
|---|---|---|
| Bar size | 237.0 × 77.0 dp, centred | [measured on device screenshot] |
| Bottom offset | 22.0 dp | [measured on device screenshot] |
| Items | 2 × 96.5 × 66.0 dp; icon 33 dp; label box 18.6 dp | [measured on device screenshot] |
| Surface | #202020 on a #0D0D0D background, top rim #4D4D4D (2 px) | [measured on device screenshot] |

So the bar's width follows its items (it is not a fixed width), and the item size varies between apps
[measured on device screenshot]; we take the Clock geometry (56 dp tall, 70 × 48 dp items, 16 dp offset) as the
compact default [estimate].

### 4.3 Floating action button (Clock)

| Measure | Value | Tag |
|---|---|---|
| Size | **55.9 dp circle**, horizontally centred | [measured on device screenshot] |
| Position | bottom edge 8.0 dp above the bottom bar's top edge | [measured on device screenshot] |
| Surface | accent gradient #31AAFD (left) → #2099FD (right/bottom); plus glyph ≈ 20 dp, white | [measured on device screenshot] |
| Glow | accent-tinted shadow spreading ≈ 6 dp below the button (#041425 on black) instead of a grey shadow | [measured on device screenshot] |
| Contrast | white on #1E97FE is 3.0:1 (enough for an icon, not for text) | [measured on device screenshot] |

## 5. Controls

| Control | Geometry | Colours (dark) | Tag |
|---|---|---|---|
| Switch | track **44.0 × 24.1 dp** pill; knob ≈ 17.6 dp white circle, 3 dp inset; the whole row is the touch target | on #428AF7 (Settings) / #1A8CFF (Clock); off #505050–#535353; knob #FFFFFF | [measured on device screenshot] |
| Radio (unchecked) | ring ≈ 20 dp diameter, ≈ 1.3 dp stroke; view box 24.1 × 27.5 dp | ring #5F5F5F | [measured on device screenshot] |
| Radio (checked) | filled accent circle ≈ 20.6 dp, white centre dot ≈ 11.8 dp | #428AF7 / #FFFFFF | [measured on device screenshot] |
| Slider (brightness) | thick pill track **20.3 dp** tall; white thumb ≈ 11.9 dp drawn inside the active part, 3 dp from its end; end icons 24 dp | active #428AF7, inactive #333333 | [measured on device screenshot] |
| Calculator keys | 98.2 dp circles, 16.5 dp gaps | convex: 1–2 px specular top rim (#7D7D7F), gradient #353638 → #212222 downwards, faint lighter bottom rim; background #171719 | [measured on device screenshot] |
| Compass dial | large circular surface with a thin light rim and a soft outer glow | | [measured on device screenshot] |

The "Luminous Effect" shows up on opaque controls as a **light rim plus a vertical gradient**, not as colour
[measured on device screenshot]. Interactive state is carried by fill and by knob/dot position, not by colour alone
(off switch is grey with the knob left; checked radio has a dot) [measured on device screenshot].

## 6. Colours (dark theme)

| Role | Hex | Contrast | Tag |
|---|---|---|---|
| Window background (Settings, Clock) | **#000000** | | [measured on device screenshot] |
| Window background (utility apps) | #171719 (Calculator), #0D0D0D (Compass bar area) | | [measured on device screenshot] |
| Card / grouped surface | **#1A1A1A** | | [measured on device screenshot] |
| Bottom bar / top-bar buttons | #1B1B1B–#2A2A2A gradients with #31–#3E rims | | [measured on device screenshot] |
| Divider | **#484848** (1 px) | 1.9:1 on card | [measured on device screenshot] |
| Primary text | **#E9E9E9** (titles #E3E3E3–#E6E6E6) | 14.3:1 on card, 17.3:1 on black | [measured on device screenshot] |
| Secondary text (summary, value) | **#969696** | 5.9:1 on card | [measured on device screenshot] |
| Section header text | **#8A8A8A** | 6.1:1 on black (5.0:1 on card) | [measured on device screenshot] |
| Disabled text, chevrons | **#5F5F5F** | 2.7:1 on card (fails 4.5:1) | [measured on device screenshot] |
| Accent (Settings) | **#428AF7** | 5.2:1 on card; white on it 3.4:1 | [measured on device screenshot] |
| Accent (Clock switch / FAB) | #1A8CFF / #1E97FE (gradient #31AAFD → #2099FD) | white on it 3.0:1 | [measured on device screenshot] |
| Unselected nav item | #C0C0C0 | 9.5:1 on bar | [measured on device screenshot] |
| Translucent system surface (heads-up notification) | #3C3C3C centre → #5A5A5A at the bottom edge, rim #6B6B6B–#8A8A8A | | [measured on device screenshot] |

Takeaways for our tokens [estimate]: a neutral grey ramp (no hue tint) on true black; the two text greys pass 4.5:1,
the disabled grey and the chevron grey do not, so our disabled text must not rely on #5F5F5F-level contrast for
anything a person has to read; white text on the accent fails 4.5:1, so accent-filled buttons with text need a darker
accent or a dark label.

## 7. Translucency and blur

| Where | What was seen | Tag |
|---|---|---|
| Top bar on scroll (Settings) | No bar background. Content passing under the bar is blurred **progressively**: sharp just below the bar, slightly soft at the toolbar's lower part (a 3 px chevron stroke spreads to ≈ 8 px, sigma ≈ 0.8 dp), strongly blurred under the status bar (a white knob edge spreads over ≈ 27 px, sigma ≈ 4.5 dp). The blurred content is also darkened towards the top (card #1A1A1A → ≈ #101010–#131313, about ×0.65–0.75). Status bar text, back button and title stay sharp on top | [measured on device recording] (sigma and darkening: [estimate]) |
| Clock app bar | The dump contains a view named `blur_overlay` covering 0–175.7 dp, i.e. exactly the expanded app bar | [measured on device screenshot] (UI dump) |
| Heads-up notification (system) | Lighter translucent grey slab (#3C–#5A) with a bright rim (#6B top, #8A bottom), lighter at the bottom edge; fine ±2-level noise across the surface, consistent with a grain/dither layer; blur not verifiable over the near-uniform content behind it | [measured on device screenshot]; grain: [estimate] |
| Bottom bar, top-bar buttons, FAB | Opaque-looking over black; rims and vertical gradients give the "lit glass" look. Whether they are translucent cannot be told from these references | [estimate] |
| Cards, list rows, charts-like content (Compass readout) | Solid, opaque, no blur | [measured on device screenshot] |

This matches the owner's rule "glass for the shell only": in the references, only system chrome (top bar zone, nav
bar, notification) is treated as glass; content surfaces are solid [estimate]. Blur parameters of Iris
(`docs/research/design-coloros17.md`: bottom bar sigma 25 px at 480 dpi ≈ 8.3 dp, top bar 20 px ≈ 6.7 dp,
progressive blur under the top bar) are in the same order as the ≈ 4.5 dp seen at the top of the progressive zone
here [estimate].

## 8. Motion (from the two recordings)

Recordings are 2248 × 2616 px frames with the 1140 px wide outer screen centred; the recorder emits frames only on
change (variable frame rate, ≈ 16.7 ms spacing while animating).

### 8.1 Back transition (Settings sub-page → parent)

| Measure | Value | Tag |
|---|---|---|
| Outgoing page | slides **to the right**, starting slowly (+41 px in the first frame), then fast (≈ 100–250 px/frame), leaves the screen ≈ **12 frames ≈ 200 ms** after the start | [measured on device recording] |
| Incoming page | slides in **from the left** with parallax, not from 0: first visible at ≈ −1/3 of the width | [estimate] |
| Incoming offset over time | ≈ −124 px at ~100 ms, −63 px at ~150 ms, −24 px at ~215 ms, −8 px at ~285 ms, −3 px at ~350 ms, 0 at **≈ 470 ms** | [measured on device recording] |
| Curve | strong deceleration with a long exponential tail (per-frame offset ratio ≈ 0.8, time constant ≈ 75 ms); looks like a critically damped spring, no overshoot | [estimate] |
| Other properties | no scale, no visible fade or dimming of either page (both on black), top bar moves with its page | [measured on device recording] |

### 8.2 Scroll and overscroll (Settings, Display page)

| Measure | Value | Tag |
|---|---|---|
| Frame pacing while moving | 263 frame intervals, mean 15.9 ms; one 39 ms gap during motion; the two long gaps (0.8 s, 1.1 s) are idle periods | [measured on device recording] |
| Overscroll at the top | content translates down (≥ 37 dp displacement observed) and springs back over ≈ 0.7 s; no stretch distortion visible | [measured on device recording] (timing: [estimate], frames sampled every 15th) |

Screen recording is not a jank measurement; the owner-phone frame-time measurement required by the performance
requirement still has to be done with our own app (macrobenchmark / `dumpsys gfxinfo`) [estimate].

For durations and spring parameters we keep Iris's reverse-engineered numbers as the baseline (springs with
dampingRatio 0.55–1.0 and stiffness 160–1366, touch glow 110 ms in / 480 ms out;
`docs/research/design-coloros17.md`); the back transition above (≈ 200 ms exit, ≈ 470 ms settle, no overshoot) is
consistent with a spring of dampingRatio ≈ 1.0 and medium-low stiffness [estimate].

## 9. Summary: numbers to carry into the tokens

| Token candidate | Reference value | Tag |
|---|---|---|
| Screen side margin | 16 dp | [measured on device screenshot] |
| Card inner padding (horizontal / vertical in rows) | 16 dp / 10 dp | [measured on device screenshot] |
| Gap between cards / between list cards (Clock) | 16 dp / 12 dp | [measured on device screenshot] |
| Section header row | 38 dp (22 dp text box, 8 dp above and below), 16 dp above it | [measured on device screenshot] |
| Single-line row height | 48 dp at font scale 1.35 (grows with text) | [measured on device screenshot] |
| Card radius | 20 dp | [measured on device screenshot] |
| Divider | 1 px hairline, inset to the text column | [measured on device screenshot] |
| Top bar | 52 dp toolbar, 40 dp circular back button with rim and shadow, title at 66 dp | [measured on device screenshot] |
| Floating bottom bar | 56 dp tall pill, width = items (4 × 70 × 48 dp), 16 dp above the screen bottom; icon 24 dp + label; selection by fill/weight, no indicator pill | [measured on device screenshot] |
| FAB | 56 dp circle, centred, 8 dp above the bar, accent gradient + accent glow | [measured on device screenshot] |
| Switch / radio / slider | 44 × 24 dp / 20 dp / 20 dp thick track | [measured on device screenshot] |
| Text sizes | title ≈ 16 sp, summary ≈ 13–14 sp, section header ≈ 12 sp, top bar title ≈ 14 sp, large title ≈ 24 sp+ | [estimate] |
| Dark colours | bg #000000, card #1A1A1A, divider #484848, text #E9E9E9 / #969696 / #8A8A8A, accent #428AF7 | [measured on device screenshot] |
| Glass | progressive blur + darkening under the top bar; rims and vertical gradients on shell controls; content solid | [measured on device recording] / [estimate] |
| Motion | back: exit ≈ 200 ms, parallax entry from ≈ −1/3 width, settle ≈ 470 ms, no overshoot | [measured on device recording] / [estimate] |

## 10. Gaps

- Only the folded (outer screen) posture and only the dark theme were captured: no light-theme colours, no
  inner-screen layouts (navigation rail, two-pane), no tabletop posture [measured on device screenshot].
- Translucency of the floating bar and of dialogs/bottom sheets could not be judged (black backgrounds; no sheet or
  dialog was opened). A light-theme capture over colourful content would settle it.
- Light theme, unfolded captures and a dialog/bottom-sheet capture are candidates for the next reference session
  (phone access only with the owner's permission, hard rule 10).
