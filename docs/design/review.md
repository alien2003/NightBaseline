# Design review log

## Round 0: design documents review

Date: 2026-11-25. Scope: every file in `docs/design/` (`principles.md`, `tokens.md`, `screens.md`, `states.md`,
`layouts.md`, `reference-analysis.md`, `res/values-ru/strings.xml`) checked against the owner's UI requirements, ADR
0002 and ADR 0003. Method: requirement-by-requirement reading, cross-file comparison of names, sizes, states and
copy, a key-set comparison between `states.md`/`screens.md` and the Russian draft, a Cyrillic scan, and
`python3 -I tools/design/contrast_check.py` (160 checks, all pass after the fixes).

Severity: **high** = breaks an owner requirement; **medium** = contradiction between documents that would produce
two different implementations; **low** = gap or wording that would cause rework.

### Findings and fixes

| # | Sev. | Where | Finding | Fix |
|---|---|---|---|---|
| 1 | high | `tokens.md` 2.5, 7.1; `principles.md` 3; `screens.md` 1, 4 | The chart tooltip was a glass surface (`color.glass.tooltip`, `material.glass.tooltip`). The requirement and ADR 0003 limit glass to the shell (bar, rail, top bar, sheets, dialogs); the tooltip floats over the chart, i.e. over data. | Tooltip is solid in both modes (`material.solid.tooltip`, `text.*` colours, 1 dp outline). Glass tooltip tokens and pairs removed; new pairs `text.primary/secondary/status.*.tooltip` added and checked. |
| 2 | high | `layouts.md` 5.1–5.3 wireframes | Wireframes used `(!)` as the night glyph: an exclamation mark, which the tone rules ban, and not the glyph defined in `screens.md` 6. | All wireframes use the defined glyphs (`<^^>` rounded square with triangles, `(v)` circle with check, `o`, `-`); legend states that no wireframe uses an exclamation mark. |
| 3 | high | `screens.md` 7.1 (micro) | Glance headline "auto-shrinks one step" contradicts "font scale up to 200 % without clipping" (shrinking defeats the user's font choice). | Text never shrinks; Glance content scrolls (`screens.md` 7.1, `principles.md` 8, `layouts.md` 5.1). |
| 4 | high | `screens.md` 4 `HistoryStrip`; `layouts.md` 5.1 Expanded | In a 320–400 dp list pane the 7-night strip gives about 41 dp per night: touch targets under 48 dp. | Each night is a >= 48 dp target only when 7 x 48 dp fit; otherwise (narrow panes, tabletop top half) the whole strip is one target that opens History, with `a11y_history_strip`. |
| 5 | high | `screens.md` 7.1, 7.4 Tabletop | Tabletop top half held interactive elements (history strip on Home, the tappable calendar on History), against the tabletop rule "top half view only". | Home: strip is a view-only glyph row in the top half, "See all" below the fold. History: selected night's summary on top, list/calendar below. Night detail and Metric detail tabletop also specified (step buttons below the fold). |
| 6 | high | `screens.md` 11 (new); `states.md` 9 | TalkBack descriptions were defined for Home, History and the chart only; nav items, period selector, state cards, banners, sheets, calendar, settings rows, onboarding steps and illustrations had none. | New `screens.md` 11 table: node, role/state and string key for every component; new keys `a11y_history_strip`, `a11y_period`, `a11y_back`, `a11y_close`, `a11y_dismiss`, `a11y_range_info`, `a11y_prev_month`, `a11y_next_month` (en + ru). Units announced through `TtsSpan`. |
| 7 | high | `tokens.md` 7.3 | Performance requirement ("no dropped frames, measured; blur in frame budget") had only a hypothesis budget, no frame budget, no scenario list, no blur share, no failure handling. | New `tokens.md` 7.4: frame budget = 1 / refresh rate, 0 overrun frames over named scenarios (scroll, morph, predictive back, period switch, scrub, history, sheet, fold/unfold), blur <= 25 % of the budget, glass and solid both measured, failure path. `principles.md` 7 points to it. |
| 8 | medium | `screens.md` 5 vs `layouts.md` 3.2 | Two incompatible window-class systems: `screens.md` had Micro (< 320 w or h < 360) / Compact / Compact height (rail) / Medium / Expanded; `layouts.md` had Glance / Tabletop / Book / CompactShort (bar) / Compact / WideShort (rail) / Medium / Expanded / Large / ExtraLarge with other thresholds. | `screens.md` 5 now uses the `layouts.md` modes and thresholds verbatim; `layouts.md` declared the owner of geometry. "Micro" renamed `Glance` everywhere. |
| 9 | medium | `screens.md` 4 vs `layouts.md` 4 | Shell geometry disagreed: bar offset 16 dp above inset vs inset + 12 dp; rail inset 16 vs 12 dp; rail used for "height < 480" vs bar in `CompactShort`; Glance "icon-only bottom bar" vs "no bar, More button"; top bar 56 dp vs 112/56/64/48 dp. | `screens.md` components now quote the `layouts.md` values (bar inset + 12 dp, rail start + 12 dp, rail modes, Glance "More" sheet, top bar heights per mode). |
| 10 | medium | `screens.md` 4 vs `layouts.md` 4.4 | Sheets: "max 640 dp centered on wide windows" vs "from Medium up a dialog, max 560 dp"; dialog max 480 vs 560. | Sheets only in Compact/CompactShort/Tabletop/Glance; from Medium up a dialog (480 dp confirmations, 560 dp sheet content) or the detail pane. `range-explainer` destination updated. |
| 11 | medium | `screens.md` 5 vs `layouts.md` 3.3, 6 | Hinge clearance 8 dp in `screens.md`, 12 dp in `layouts.md` and `tokens.md` (`hinge.clearance`). | 12 dp everywhere. |
| 12 | medium | `screens.md` 7.3 vs `layouts.md` 5.2 | Chart heights 220/280 vs 200/240/280; Metric detail order differed (header card first vs chart first; "tonight card"); `layouts.md` allowed a line through missing nights ("points/line"). | One set: 220 / 240 / 280, +40 at height >= 900, max 45 % of the window, tabletop = top half - 24. Header card first everywhere (it is the morph target). Points only, no line across gaps. |
| 13 | medium | `layouts.md` 4, 5, 10 | Top-level destination called "Today" in `layouts.md` but "Last night" (`nav_home`) in `screens.md`/`states.md`; "Feed" vs "List" for the History toggle; "Tonight 58 bpm", "above usual", "= usual", "Night N of 7", "Tue 24 Nov" in wireframes. | `layouts.md` uses "Last night", "List / Calendar", "Higher than usual", "Usual", "{n} of 7 nights", "Night of Nov 24–25", matching the string keys. |
| 14 | medium | `layouts.md` 5.1 vs `screens.md` 7.6 | Learning tiles: "without a band, 'range available after 7 nights'" vs "dotted band outline, 'Learning, N of 7'". | Dotted band outline and `status_learning` in both. |
| 15 | medium | `layouts.md` 5.1 vs `screens.md` 6 | Recent-nights marks: "ring with arrow = moved outside" vs "rounded square with one or two triangles". | `layouts.md` uses the `screens.md` 6 vocabulary. |
| 16 | medium | `screens.md` 7.1 Medium vs `layouts.md` 5.1 | Home Medium: summary and strip side by side 3/5 : 2/5 vs summary full width with a 14-night strip beside it only from 720 dp. | `layouts.md` version adopted in both (owner's 692 dp inner screen: strip below). |
| 17 | medium | `screens.md` 7.4, 7.7 vs `layouts.md` 5.3, 5.4 | History Medium: "month grid left, moved nights right" (does not fit: the grid needs 360 dp) vs single pane with reflow. Settings Medium: list-detail vs single column with inline Sensitivity. | `screens.md` follows `layouts.md` (two-pane rule fails at 692 dp). |
| 18 | medium | `screens.md` 7.5 vs `layouts.md` 5.5 vs `states.md` 7.3 | Onboarding had 6 steps (screens, states) but 5 steps in a different order in `layouts.md` ("Step 2 of 5"). Indicator "announced" vs "visible text". | 6 steps everywhere (welcome, how it works, source, permissions, notifications, ready); visible `onb_step` "Step {n} of 6" + decorative dots. |
| 19 | medium | `layouts.md` 5.4; `screens.md` 7.7 | Settings groups in `layouts.md` ("theme follows system; reduce transparency override", "learning period display") did not match `screens.md` (Theme System/Light/Dark, Glass effects Automatic/Off, presets + advanced). The Notifications row "Problems with data access" did not match its channel name "Problems reading data". "Set up again" was referenced as an entry to onboarding but had no row. | `layouts.md` lists the `screens.md` groups; row renamed "Problems reading data"; "Set up again" row added (`settings_setup_again`). |
| 20 | medium | `screens.md` 7.7 vs `tokens.md` 7.3 | Settings said Automatic glass turns off on API < 31, battery saver, reduced transparency, animations removed; the resolver also uses higher contrast, low-RAM, jank and thermal rules. | Settings text lists every rule and points to `tokens.md` 7.3. |
| 21 | medium | `tokens.md` 10 vs `layouts.md` 10.1 | Focus order "top bar -> content -> bar/rail" vs rail layouts "rail -> top bar -> panes". | `tokens.md` defers to `layouts.md` 10.1 per layout. |
| 22 | medium | `states.md` 6.1 | Notification body "{metric list} for the night of {night label}" doubled the words: `night_label` is already "Night of Nov 24–25". | `notif_dev_body` = "{metric list}. {night label}." (en and ru); examples updated; locale rule added. |
| 23 | medium | `states.md` 5 | Widget cue for `E-SYNC` was "moon" (the `B-WAIT` cue) instead of the state's cloud-with-clock; `E-NOTEVAL` widget said "No data" instead of "Too few readings". | Fixed; widget labels get keys `widget_waiting`, `widget_update_needed`, `widget_choose_source`. |
| 24 | medium | `states.md` 3, 2 | "Static in fallback" for the `B-RANGE-READY` morph and "no shimmer in fallback" for `LOADING`: the effects fallback is not the reduced-motion switch, and a shimmer is an idle animation `principles.md` 7 bans. | Morph is static under reduced motion; skeletons never shimmer. |
| 25 | medium | `tokens.md` 9.6; `screens.md` 7.5 | Onboarding background "slow gradient drift" is a looping decoration while the person reads (`principles.md` 7). | Static gradient in every mode; drift removed from the reduced-motion table. |
| 26 | medium | `tokens.md` 7.1 | Top bar tint faded in over 150 ms by time, so for a moment the title could sit over blurred content without full tint (text on blur without backing). | Tint alpha is driven by scroll offset and is full before content reaches the title text. |
| 27 | medium | `tokens.md` 2.5, 9.5 | The finger-following `accentGlow` on glass items is drawn behind labels; its place relative to the tint was undefined, so the worst-case contrast model might not hold. | The glow is composited under the tint, as part of the backdrop, so the white/black worst case still bounds the label. |
| 28 | low | `states.md` 1, 2, 6.2 | Missing states: the selected source app uninstalled; a Health Connect read that fails for a non-permission reason (service error, rate limit). | New night state `E-SOURCE-GONE` (priority 4, card, Choose source, data access notification) and banner `B-READ-FAILED` (Check now); matrix, deep links and notifications updated; en + ru copy. |
| 29 | low | `states.md` 6.2 | `B-BG` triggers the data access notification but had no notification copy; the channel had no icon/accent spec. | `notif_bg_title/body`, `notif_source_gone_*` added; icon `ic_stat_night`, accent `status.info`. |
| 30 | low | `screens.md` 3 | Deep link `fix/{...}` did not say which state maps to which segment. | Mapping table in `screens.md` 3 and `states.md` 6.2. |
| 31 | low | `screens.md` | Visible strings without keys or Russian copy: "See all", "Last 7 nights", "What to expect", stats row, source line, History toggle and filters, settings group and row titles, sensitivity advanced controls, reset dialog, About lines, "More". | New `states.md` section 10 (55 keys) and the matching Russian entries; `screens.md` names the keys. Key sets compared: identical. |
| 32 | low | `screens.md` 7.3 | Header sentence "Last night was 6 bpm higher than your usual range." did not match `detail_higher` ("{delta} above your usual range."). "Median" in the stats row is jargon next to the explainer's "middle value". | Uses the `detail_*` keys; stats row says "Middle value". |
| 33 | low | `screens.md` 6 | Status vocabulary had "1 moved" / "2 moved" fixed labels (wrong for k >= 3 or the "Fewer notes" threshold) and no row for data-access states. | `{k} moved` via `history_status_moved`; a row for data-access states with their outline icons. |
| 34 | low | `screens.md` 5; `layouts.md` 3.1 | TENT stated as fact ("inner screen off, outer behaves as compact") in `screens.md`, hypothesis in `layouts.md`. | Marked as hypothesis in both. |
| 35 | low | `tokens.md` 9.2 | `motion.easing.flow` was tagged as a vendor kit's curve. A cubic-bezier is not an asset, but the tag read as copying from a vendor. | Re-tagged `[decision]`: a generic strong-decelerate curve; no code or resource is taken from any kit. |
| 36 | low | `tokens.md` 9.1, 9.6 | `motion.duration.medium` (250) carried a stray "(120 ms)" for the reduced crossfade; at animation scale 0 a Compose crossfade snaps, so the "crossfade" never shows. | New token `motion.duration.reduced` = 120 ms, run on our own clock so the change of screen stays visible. |
| 37 | low | `tokens.md` 2.9 | Status text colours were not checked on `surface.3` (selected and pressed rows), although the state-layer note relies on `surface.3` being checked. | Pairs `text.status.*.surface3` added (pass, lowest 4.64:1). |
| 38 | low | `layouts.md` 4.1, 4.2 | At 200 % font in narrow windows (e.g. 346 dp split screen) three labelled bar items do not fit; behaviour undefined. Same for a tall rail. | Labels wrap to 2 lines, then items share the width (each >= 48 dp) and the bar grows; the rail grows and scrolls. Never clipped. |
| 39 | low | `screens.md` 7.9 | Widget accessibility and large font were not specified. | Content description per widget view; at large font the metric lines drop before anything clips. |
| 40 | low | `screens.md` 7.10, 10; `layouts.md` 11 | Component catalog lacked the grayscale and reduced-motion toggles `principles.md` relies on, banners, nav states and notification previews; `screens.md` 10 had its own device list (e.g. "medium 690x760", "expanded 1000x1100") different from `layouts.md` 11; the grayscale variant was missing from the matrix. | Catalog controls and entries extended; `screens.md` 10 references the `layouts.md` 11 device ids; grayscale variant and extra coverage for `tablet-port`, `owner-book`, `flip-cover-tall` added. |
| 41 | low | `layouts.md` 10.2 | Ctrl+R mapped to "Check again" on the sync state, whose action is "Check now"; Left/Right in Night detail was only in `screens.md`. | Ctrl+R = Check now on `E-SYNC`, `E-NODATA`, `B-WAIT`, `B-READ-FAILED`; Left/Right row added. `screens.md` 3 defers to `layouts.md` 10. |
| 42 | low | `tokens.md` header | "Checked automatically" contrast: the script exists and passes but is not yet run by the pre-commit hook or CI. | Recorded as open item (below); `tokens.md` header says so. |
| 43 | info | task instruction vs ADR 0002 | The review brief asked for Russian copy inline in `states.md` copy columns. ADR 0002 and `tools/check-language.sh` (pre-commit) allow Cyrillic only in `res/values-ru*` and `README.ru.md`; inline Russian in `states.md` would fail the check. | Kept the existing, rule-compliant arrangement: Russian copy only in `docs/design/res/values-ru/strings.xml` (the draft of the app resource), `states.md` marks each key "yes". No Cyrillic in any Markdown file (scan: only `strings.xml`). |

Checked and found fine: no use of "Vitals" in any copy; no "alert", "warning", "abnormal", "danger", red or error
colour in the UI; no diagnoses (every deviation text says "not a diagnosis" or compares with the person's own
nights); the notification is default importance, calm, without values on the lock screen; no vendor SDK, icon, font,
image or wallpaper is referenced (source-app icons are read at runtime, Material Symbols are Apache-2.0, system font
only); no image, font or video file exists in the repository; `reference-analysis.md` describes only measurements and
patterns, no personal content of the reference screenshots.

### Open items after round 0

- Wire `tools/design/contrast_check.py` into the pre-commit hook (when `docs/design/tokens.md` or theme code is
  staged) and into CI (stage 4/5).
- Measure frame timing and blur cost on the owner's phone (`tokens.md` 7.4) - needs the owner's permission.
- Verify on device or emulator: fold reporting on the flat inner screen and in TENT, inner-screen density, Robolectric
  support for blur (`layouts.md` 12).
- A light-theme and sheet/dialog reference capture would calibrate the light tokens (`reference-analysis.md` 10).

### Requirement checklist

| Requirement | Where covered | Status |
|---|---|---|
| Visual language in the spirit of ColorOS 17 (frosted glass with light rim, floating bottom bar, large radii, finger-following motion, morphing) | `tokens.md` 5, 7, 9; `screens.md` 4; `layouts.md` 4 | OK |
| Own tokens: colour, typography, shapes, materials (glass and solid), motion; light and dark | `tokens.md` 2–9 | OK |
| No vendor SDKs or copied resources; system font | `tokens.md` 3, 8; `screens.md` 4; ADR 0003 | OK (finding 35) |
| Glass only for shell (bar, top bar, sheets, dialogs); data on solid | `principles.md` 3; `tokens.md` 2.5, 7; `screens.md` 1 | OK after finding 1 |
| Text never on busy blur without backing | `tokens.md` 2.5, 7.1 | OK after findings 26, 27 |
| Text contrast >= 4.5:1 both themes, checked automatically | `tokens.md` 2.9; `tools/design/contrast_check.py` (160/160 pass) | OK; CI wiring open (finding 42) |
| Fallback on API < 31, weak devices, battery saver, reduce transparency, remove animations; looks finished | `tokens.md` 2.6, 7.2, 7.3, 9.6; `principles.md` 3, 8; `layouts.md` 1.5 | OK (finding 20) |
| No dropped frames (measured), blur in frame budget | `tokens.md` 7.4; `principles.md` 7 | OK as plan after finding 7; measurement pending |
| Colour never the only carrier of meaning | `principles.md` 4; `screens.md` 6; `tokens.md` 2.3, 8 | OK (findings 2, 15, 33) |
| TalkBack for all elements; chart text summary | `screens.md` 8, 11; `states.md` 9 | OK after finding 6 |
| Font scale up to 200 % without clipping | `tokens.md` 3; `layouts.md` 1.8, 4.1, 11; `principles.md` 8 | OK after findings 3, 38 |
| Touch targets >= 48 dp | `tokens.md` 10; `layouts.md` 11 assertions | OK after finding 4 |
| Home: result at a glance, value on personal range band, short history | `screens.md` 7.1; `layouts.md` 5.1 | OK |
| Metric detail: 7/30/90 chart with band, how the range is computed, what a deviation means | `screens.md` 7.3; `states.md` 7.1, 8; `layouts.md` 5.2 | OK (finding 12) |
| History: calendar or feed with deviation marks | `screens.md` 7.4; `states.md` 7.2; `layouts.md` 5.3 | OK |
| Onboarding: what it does, permissions, source selection | `screens.md` 7.5; `states.md` 7.3; `layouts.md` 5.5 | OK after finding 18 |
| Learning period: "N of 7 nights", what is visible, what to expect | `screens.md` 7.6; `states.md` 2, 10 | OK (finding 14) |
| Settings: source, notifications, sensitivity, about | `screens.md` 7.7; `states.md` 7.4, 10; `layouts.md` 5.4 | OK after finding 19 |
| Notification: text, icon, tap action | `states.md` 6 | OK after findings 22, 29 |
| State: no data for the night | `states.md` `E-NODATA`, `M-NODATA` | OK |
| State: source did not sync | `states.md` `E-SYNC`, `B-WAIT`, `B-SOURCE-QUIET` | OK |
| State: permission revoked | `states.md` `E-PERM`, `M-PERM`, `B-BG` | OK |
| State: Health Connect unavailable | `states.md` `E-HC-NONE`, `E-HC-UPDATE` | OK |
| State: night too short | `states.md` `E-SHORT`, `M-NOTCOMPARED` | OK |
| State: metric not provided by the source | `states.md` `M-NOTPROVIDED`, 4.1 | OK |
| Each state with its own message and action | `states.md` 2–4, 7 | OK (findings 28, 31) |
| Widget optional, last | `screens.md` 7.9; `layouts.md` 5.6 | OK (findings 23, 39) |
| Debug component catalog: every component, both themes, all states | `screens.md` 7.10 | OK after finding 40 |
| Own layout per compact / medium / expanded | `layouts.md` 3.2, 5; `screens.md` 5, 7 | OK after finding 8 |
| Floating bottom bar on compact, rail on wide, same visual language | `layouts.md` 4; `screens.md` 4 | OK after finding 9 |
| Wide: two columns, max content width for text and charts | `layouts.md` 3.3; `screens.md` 5 | OK |
| Foldables: avoid hinge; tabletop chart above, explanations and actions below | `layouts.md` 6, 5.2; `screens.md` 5, 7.3 | OK after findings 5, 11 |
| Fold/unfold keeps screen, scroll, period, metric; no reload | `layouts.md` 8; `screens.md` 5 | OK |
| Flip cover screen: night result at a glance | `layouts.md` 3.2 `Glance`, 5.1; `screens.md` 7.1 | OK after finding 3 |
| No orientation lock; resizable, split screen, freeform; never breaks | `layouts.md` 2, 7 | OK |
| Tablets portrait/landscape; keyboard and mouse (focus, scroll, hover) | `layouts.md` 7, 10; `tokens.md` 10 | OK (findings 21, 41) |
| Glass and fallback identical across layouts | `layouts.md` 1.5; `screens.md` 5; `tokens.md` 7.3 | OK |
| Owner's device (outer compact, inner medium/expanded near-square, CLOSED/TENT/HALF_OPENED/OPENED) | `layouts.md` 9, 11 | OK; TENT and flat-fold reporting are hypotheses (open items) |
| English + Russian, numbers and dates per locale | `states.md` 0, 10; `res/values-ru/strings.xml`; `tokens.md` 3; `screens.md` 8 | OK (findings 22, 31, 43) |
| Never "Vitals"; calm "moved outside your usual range" | `states.md` 0; `principles.md` 1 | OK (finding 2) |
| Not a medical device | `screens.md` 9; `states.md` 8 | OK |
| Vendor neutral, any Health Connect source, OHealth default | `screens.md` 7.5; `states.md` 7.3 | OK |
| Screenshot tests: screen x state x theme x language x font x glass/fallback x window sizes | `layouts.md` 11; `screens.md` 10 | OK after finding 40 |
| No health data or vendor screenshots in the repo | `reference-analysis.md` intro; `README.md` | OK |
