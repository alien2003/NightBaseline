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

## Round 1 (integration)

Date: 2026-11-25. Scope: wiring the stateless screens (`ui.home`, `ui.night`, `ui.metric`, `ui.history`,
`ui.onboarding`, `ui.learning`, `ui.settings`) to `:data-local`, `:data-hc` and `:work`, the navigation graph, the
adaptive shell on real window information, and the open items of `review-home.md`, `review-metric.md`,
`review-history.md` and `review-setup.md` that were cheap to close. Method: ViewModel unit tests over the data-local /
data-hc fakes, a Robolectric navigation and deep-link test, and the new integrated screenshot matrix
(`IntegratedScreenshotsTest`: Home, History and Settings x phone, flip-cover-square, owner-book, owner-tabletop,
tablet-land, tablet-portrait x light, dark x font 1.0, 2.0 x glass, solid = 144 images, each checked for unfilled
placeholders, clipped text and controls on a fold). The PNGs were read by eye; the fixes below come from them.

### What was wired

| Area | Change | Why |
|---|---|---|
| Application | `NightBaselineApp` is `@HiltAndroidApp` and `Configuration.Provider` with `HiltWorkerFactory`; the manifest removes `androidx.work.WorkManagerInitializer` (`tools:node="remove"`, as PulseStages); on start it creates both notification channels (`Channels` in `:work` is now public) and calls `ensureScheduled()` once onboarding is done. | `NightWorker` needs injected collaborators; the default initializer would build WorkManager without them. |
| Data glue (`app/`) | `AppData` (one injectable bundle: settings, nights, run log, platform status, check state, scheduler, clock, zone, reader, backfill, dispatchers); `AccessMonitor` (Health Connect availability, granted permissions, background feature, source installed + label, notification access; refreshed on start, every resume and after permission results); `CheckState` (WorkManager `RUNNING` of the unique chain); `NightPresenter` (pure: night state by the `states.md` 1 priorities, banners by the `states.md` 3 order, per-night UI, chart series with the range recomputed per night by `Evaluator` under the current sensitivity); `SourceDiscovery` (sources of the last 30 days and the metrics each writes). | The screens stay stateless; the rules live in pure, unit-tested code; Health Connect *data* is read only by the work and by explicit user steps (onboarding, source picker). |
| ViewModels | Hilt ViewModels per area: Home (plus the learning presentation), Night detail (date and previous / next in `SavedStateHandle`), Metric detail (period in `SavedStateHandle`), History (view, filter, month, selection in `SavedStateHandle`), Settings (page, section, pending source), Onboarding (step, selection, denial count in `SavedStateHandle`), and a root ViewModel (theme, glass, onboarding flag, newest night). Mapping runs on `Dispatchers.Default`, reads on IO; nothing is logged. | State survives fold / unfold, rotation and recreation without reloading (`layouts.md` 8). |
| Settings data | `AppSettings` gained `problemNotificationsEnabled`, `theme`, `notificationBannerDismissedAtMs` (`B-NOTIF-OFF`, 30 days) and `rangeReadySeen` (`B-RANGE-READY` once); `NightRepository.observeAll()` lists every source's nights. `NightRun` and `NightWorker` honour "Problems reading data". | Settings rows and banners needed stored state; a source change restarts learning (nights are stored per source) while History keeps the earlier source's nights. |
| Navigation | navigation-compose 2.10.2 with typed `@Serializable` routes (`HomeRoute`, `HistoryRoute`, `SettingsRoute(page)`, `NightRoute(date, section)`, `MetricRoute(metric, date, section)`, `OnboardingRoute(step)`); top-level navigation saves and restores each section; Ctrl+1/2/3, Ctrl+R, Esc and Alt+Left; shortcuts listed for the system helper. | `screens.md` 2-3, `layouts.md` 10.2. |
| List-detail hand-over | Home metrics and History nights select in place on two-pane windows; folding with an explicit selection pushes Metric / Night detail; unfolding with a pushed detail pops it and selects it in the pane (saved-state handshake). | `layouts.md` 8.2 rule 4; covered by `NavigationTest`. |
| Real detail panes | Home's local metric pane (`HomePanes.MetricDetailPane`) was removed; the detail pane is the real `MetricDetailPane` (the Metric detail blocks without scaffold; Medium arrangement from 640 dp). History's two-pane detail is the real Night detail content with its own title row. | One implementation per screen. |
| Deep links | `nightbaseline://night/{date}` and the `night_date` extra are parsed by `DeepLinks.parse` (validated with `NightIntents.nightDateOf`; unknown hosts / segments and malformed URIs ignored); the newest night opens Home, an older one Night detail above Home; links wait for onboarding. `fix/permission` launches the Health Connect request on Home, `fix/health-connect` opens Home with the update card, `fix/source-sync` opens the source picker. Opening a night cancels its notification. | `screens.md` 3, `states.md` 6. |
| Onboarding | Health Connect check, source discovery, the permission request through `PermissionController.createRequestPermissionResultContract()` with `HcPermissions` (feature-gated ones only where available; a second denial opens Health Connect), `POST_NOTIFICATIONS` on 33+, `HistoryBackfill` on the ready step, then `runNow()`, which also starts the daily chain. Before sleep may be read the source list holds only the installed default; "Check again" on an empty list asks for the permissions first. | `screens.md` 7.5, `states.md` 7.3. |
| Window info | `androidx.window:window` 1.5.1 (latest stable on Google Maven, 2026-11-25): `WindowInfoTracker` folding features (orientation, `isSeparating`, occlusion, bounds) and `WindowMetricsCalculator` window bounds, recomputed on each configuration change, mapped by the pure `layoutInputsOf` (`nav/FoldSpec.kt`). The manifest keeps `configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"`, no orientation lock, resizable (`MergedManifestTest` now asserts all five values and the removed initializer). | `layouts.md` 2, 3.1. |
| Release build | `proguard-rules.pro` keeps the typed routes' serializers, the core-algo serializers used by the stored JSON and the worker constructor. `:app:assembleRelease` succeeds unsigned without signing variables. | R8 safety for lookups by name. |

### Fixes from the open items and the integrated screenshots

| # | Finding | Fix |
|---|---|---|
| 1 | Book (owner-book, 692 dp) showed the floating bar. | `Book` always uses the rail, icon-only: a labelled rail grows to about 94 dp at font 1.0 (more at 2.0) and the History rows then crossed the fold clearance (caught by the new fold assertion). `layouts.md` 3.2 and 4.2 updated. |
| 2 | `HistoryStrip` titled itself "Last 7 nights" while learning showed fewer. | Plural `home_strip_title_n` ("Last {n} nights", en + ru) below 7 nights. |
| 3 | Shared components put `widthIn(max)` after the caller's `fillMaxWidth()`, so their 600 dp cap never applied. | `NightSummaryCard`, `MetricRangeTile`, `InlineBanner`, `NightRow` and `SettingsGroup` apply the cap before the caller's modifier (`Modifier.widthIn(max).then(modifier)`) and take a `maxWidth` parameter; Home and Night detail pass their grid width. `NbCard`'s ineffective cap was removed (the caller owns its width). |
| 4 | `StringResourcesTest` read only `strings.xml`. | It loads every `strings*.xml` (except `strings_untranslatable.xml`), checks that each file exists in both languages with the same keys and that no key is defined twice. |
| 5 | Tabletop: the top half scrolled at 200 % font (`layouts.md` 6.4: only the bottom half scrolls). | Home and Night detail top halves are clipped, never scroll, and from font 1.5 show the compact summary (glyph, label, headline; shared `CompactNightSummary`). |
| 6 | Large (tablet-land 1280 dp) never showed the "Recent nights" supporting pane. | Geometry kept (rail + 360 + 360 + 320 + spacers + gutters need about 1360 dp); `layouts.md` 5.1 and 7 updated: the pane appears from about 1400 dp. |
| 7 | Book: the summary headline broke inside words ("measure / ments") in the narrow start half. | The summary card stacks its glyph above the text below 300 dp inner width; all type styles use `Hyphens.Auto`, so a word that still does not fit (e.g. "Notifications" at 200 %) breaks at a syllable with a hyphen. |
| 8 | Keyboard focus order put the rail after the content. | The rail is composed first (drawn on top with `zIndex`), the bar last; both are focus groups (`layouts.md` 10.1). |
| 9 | Home's range explainer, the "Not recorded by" sheet and the Glance "More" sheet had no host. | App-level overlays (`InfoOverlay` / `RangeExplainerOverlay` extracted from Metric detail; the More sheet with labelled destinations), saved across fold and recreation, closed by back and Esc. |
| 10 | `B-RANGE-READY` would show forever on any history that once was learning. | Offered only while the learning period ended at most 3 nights ago and it was not dismissed. |
| 11 | With the rail, the book start half (about 222 dp of content) cannot hold even the transposed month grid at 48 dp per cell (`HistoryScreenshotsTest` target assertion). | The transposed grid scrolls sideways when 6 week columns do not fit; cells keep 48 dp. |
| 12 | The clipped tabletop top half cut the history strip at font 1.0 (clipping assertion). | `FitColumn`: the top half places its view-only blocks in order and drops a block that does not fit whole (the strip), never cutting text. |
| 13 | Wiring `:work` merged WorkManager's `ACCESS_NETWORK_STATE` into the app (`MergedManifestTest`). | Removed with `tools:node="remove"`: the night check has no network constraint. |

### Decisions recorded here

- `E-SYNC` vs `E-NODATA` without a "newest record of the source" time from Health Connect (the UI does not read
  data): after 18:00 with no night for the expected wake date, `E-SYNC` when the night before is missing too (the
  source has been silent), otherwise `E-NODATA`. Hypothesis to revisit if the daily work starts storing the source's
  last write time.
- `LOADING` = no stored night while the unique night check is `RUNNING`; otherwise `L0`.
- The learning area has no ViewModel of its own: `HomeViewModel` emits either Home or the learning presentation
  (`screens.md` 7.6 makes learning a state of Home).

### Open after this round

- `E-SOURCE-GONE` names the source by its package (the label of an uninstalled app is not stored).
- F6 / Ctrl+Tab between panes, Alt+Up / Alt+Down in list-detail and re-tap-to-top on the bar are not implemented.
- Book at 200 % font: the 238 dp start half is tight (hyphenated titles); a denser row for narrow panes would help.
- The integrated matrix captures each top-level destination at rest; pushed destinations are covered by the per-area
  tests and `NavigationTest`, not by integrated images.
- State preservation is verified by the saved-state design and `NavigationTest` (fold / unfold hand-over), not yet by
  a `StateRestorationTester` run.

## Round 2: live check on the owner's phone (2026-11-26)

Device: OPPO CPH2765 (book foldable), Android 17, ColorOS 17, release build 0.1.0-dev.1 signed with the release key.
Driven over adb (taps on NightBaseline only; system settings restored afterwards). Device screenshots contain real
data and stay in the git-ignored `.data/device-shots/`.

Verified on the device:
- First run end to end: source discovery found OHealth with its channels (heart rate, breathing, SpO2, sleep), the
  history backfill found 57 past nights, the range was ready right after onboarding.
- Home on the unfolded inner screen: two columns, icon + label rail, 7-night strip, not-recorded metrics collapsed.
- Light/dark switch, rotation and fold (`cmd device_state` CLOSED) keep the screen, the data and the selected period;
  folding moves to the outer screen with the bottom bar.
- Half-opened posture: book layout split at the fold with the icon-only rail.
- Half-width window (split-screen width, simulated with `wm size`): compact layout with the bottom bar.
- Frames (`dumpsys gfxinfo`, scrolling History and Metric detail, switching periods): 6 janky of 2986 frames (0.20 %),
  GPU p99 12 ms, UI thread slow 3.
- The daily `NightWorker` is registered with JobScheduler.
- Not verifiable over adb: a real freeform window (ColorOS ignores `--windowingMode 5` from adb) and the physical
  hinge angle in tabletop; the outer-screen widget is not built.

Findings to fix:
1. Metric detail: content scrolls under the glass top bar and the "This period" heading sits on the blur without a
   backing (principle: text never directly on blur); the night card touches the screen title.
2. Unfolding returned Metric detail to the detail pane with the period reset from 90 to 30 nights (state must persist).
3. Explainer copy says "last 14 to 28 nights"; the algorithm uses the last 28 valid nights, at least 7.
4. Night label differs between screens ("Night of 25 Nov" on Home, "Night of 24–25 Nov" in Metric detail).
5. Sleep duration range on real data reads "2 h 33 min – 11 h 30 min": correct for a very variable person but its lower
   edge is below the 3 h short-night cut-off. Clamp the lower edge to the short-night limit and explain the width.
6. Onboarding on the wide inner screen: the start pane (step and illustration) floats in empty space.

### Round 2 fixes

1. Top bar: once content is under it, the title row gets an opaque `solid.topBar` backing driven directly by the
   scroll offset (full after 8 dp, before content reaches the title); only the status-bar strip keeps the progressive
   glass. Panes that scroll on their own (two-pane Home detail and supporting panes, Book start panes of Metric and
   Night detail, Settings list pane, Learning end pane) register with the shell (`rememberPaneScrollState`), so they
   also drive the tint. Metric detail starts its content below the measured bar bottom (status bar + bar) + 12 dp.
   New `-scrolled` screenshots of Metric detail show content part-way under the bar.
2. Fold / unfold hand-over keeps the chart period and the selected night: `MetricRoute` carries `period` and `point`
   (Home's detail pane to the pushed screen), and the pushed screen hands both back to Home (`SELECT_PERIOD`,
   `SELECT_POINT`). `NavigationTest` reproduces the hand-over with 90 nights in both directions.
3. `explainer_range` (en, ru) now says the last 28 nights with readings from the past 60 days, at least 7.
4. One night label everywhere: `NightLabel` (:work) always names the evening before the wake date to the wake date in
   the locale's short range form ("Nov 24 – 25" en-US, "24–25 Nov" en-GB; ru "evening to wake date" form), used by
   Home, Night detail, Metric detail, History, Learning and the notification. Sleep that started after midnight no
   longer produces a single-date label.
5. Sleep duration: `Baseline.low` never goes below `minNightMinutes` (3 h) in :core-algo (test added; statuses do not
   change, shorter nights are never compared). A range wider than 6 h adds `detail_sleep_range_wide` to the Metric
   detail explainer (new `DurationWide` screenshot sample).
6. Onboarding (Medium, WideShort, Book, card): the two columns form one block, top-aligned (step indicator level with
   the title), centred vertically in the window and scrolling as one; the illustration is 240 dp tall there.
7. Sensitivity copy: `sens_fewer_desc`, `sens_balanced_desc`, `sens_more_desc` (en, ru) now say that the presets only
   change how big a change must be (fewer / more notes) and that two measurements must move for all three, matching
   `SensitivityLevel` (z 3.0 / 2.5 / 2.0) and ALGORITHM.md; "Fewer notes" uses `sens_fewer_desc` again and the
   interim `setup_sens_fewer_desc` is removed. `screens.md` 7.7 lists the z values per preset.
