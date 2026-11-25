# Design principles

Status: design, stage 3 (before code). Date: 2026-11-25.
Companion documents: `tokens.md` (exact values), `screens.md` (components and screens), `states.md` (states and
copy), `layouts.md` (window sizes, postures, input). Visual language: in the spirit of ColorOS 17 (Living Flow
Design, Luminous Effect), see `docs/research/design-coloros17.md` and ADR 0003. Measured calibration data:
`reference-analysis.md`. Index of all design documents: `README.md`; review log: `review.md`.

These principles decide the cases the other documents do not cover. When two of them conflict, the one listed
first wins (a calm, correct glance beats visual effect). Each principle names how it is checked, because a
principle nobody verifies drifts.

---

## 1. A calm morning glance

The person opens the app half awake. Home answers one question, "was last night usual for me?", in about three
seconds, and the answer never sounds like an alarm.

| Do | Don't |
|---|---|
| One headline per night: "All within your usual range" or "2 measurements moved outside your usual range". | Several competing headlines, scores, rings or badges fighting for attention. |
| Use `color.status.moved` (warm, desaturated ochre) for deviations; red is not in the palette. | Red, flashing, vibration patterns, exclamation marks, "alert", "warning", "abnormal", "danger". |
| Put the explanation one tap away ("What this means"), in plain words, with everyday causes (late night, alcohol, travel, a cold). | Diagnoses, risk percentages, health advice, or the word "Vitals". |
| Keep the last-night value the largest text on a tile; the band and label right below it. | Make the user scroll or open a detail to learn whether the night was usual. |

Checked by: copy review against `states.md` section 0 (banned words list is enforced by a unit test on string
resources); screenshot review of Home in every night state.

## 2. Your own normal is the reference

Every number is shown against the person's own range (median and robust spread over their recent nights), never
against population norms, and the app never judges a direction as good or bad.

| Do | Don't |
|---|---|
| Show each value on top of its personal `RangeBand`, with the range in words ("Usual range 52 to 58 bpm"). | Show a value alone, or against a textbook range ("normal resting heart rate 60-100"). |
| Use the same moved color and mirrored triangle for "higher than usual" and "lower than usual". | Style lower HRV or higher heart rate as "worse" with a stronger color. |
| Say how the range is computed and how many nights it uses, on Metric detail. | Hide the window, or change it silently (a shifted band gets a note under the chart). |

Checked by: `color.status.above` and `color.status.below` are the same value in `tokens.md`; every metric
component has a range text in its content description (`a11y_tile`).

## 3. Data on solid ground, glass for the shell

Glass (backdrop blur, tint, light rim) belongs only to the frame of the app: floating bottom bar, navigation rail,
top bar once content scrolls under it, bottom sheets, dialogs. Measurements, bands, charts, the chart tooltip, lists
and calendar cells sit on stable, opaque surfaces.

| Do | Don't |
|---|---|
| Put every card, tile, band and chart on `material.solid.*`. | Blur, tint or make translucent anything that carries a number. |
| Give every glass surface a tint layer strong enough for 4.5:1 text over a pure white and a pure black backdrop. | Place text directly on blurred, busy content without a backing. |
| Use `onGlass.*` text tokens on glass; normal text tokens on solid. | Reuse `text.secondary` on glass and hope the backdrop is dark enough. |
| Make the solid fallback a finished design: same geometry, same rim, a quiet vertical gradient. | Treat the fallback as "glass with the blur switched off" (a flat grey slab). |

Checked by: `tools/design/contrast_check.py` (glass pairs against both worst-case backdrops); component catalog shows
every glass component over a busy synthetic backdrop in both modes; screenshot tests render glass and fallback for
the same layout and expect identical geometry.

## 4. Meaning never by color alone

A grayscale screenshot must still tell within, higher, lower, learning and missing apart, and so must a person with
any kind of color vision.

| Do | Don't |
|---|---|
| Pair every status with a shape (filled circle, up/down triangle, dotted ring, dash, slashed circle) and a text label. | Encode status only as a colored dot, a colored band, or a colored row background. |
| Mark selection by shape and weight too (capsule behind the item, filled icon, bold label). | Show the selected tab or period only as a different hue. |
| Give charts a visible text summary that is also their content description. | Rely on the viewer seeing which points are colored. |

Checked by: the component catalog has a grayscale toggle; screenshot tests include a grayscale variant of Home and
Metric detail; TalkBack labels are asserted in UI tests.

## 5. Honest about what is not known

Missing, partial and learning states are normal parts of a night tracker. They are named plainly, keep their place,
and never look like a value.

| Do | Don't |
|---|---|
| Keep a tile in place with a dash and the reason ("No value last night", "Not recorded by {source}"). | Drop the tile, show 0, or reuse yesterday's value. |
| Show learning as progress ("4 of 7 nights") and show what is already visible (values, a dotted band outline). | Show an empty screen or a spinner for the first week. |
| Give each problem state its own clear message and one action that fixes it (open source, turn on access). | Use a generic "Something went wrong" or an error color for data-access problems. |
| Leave gaps in charts for nights without a value. | Interpolate across missing nights. |

Checked by: every state in `states.md` has a screenshot test; no state shares title text with another.

## 6. Stable and predictable

The person learns the screen once. Metrics keep their order, components keep their position, and changing the
window (fold, unfold, rotate, split, resize) changes the layout but never the content or the place the person was
looking at.

| Do | Don't |
|---|---|
| Keep metric order fixed (registry order) for every night; name deviating metrics in the summary instead. | Move deviating metrics to the top, so tiles jump between nights. |
| Give every window class its own layout from one `resolveLayout` decision, and preserve route, selection, period and scroll across changes. | Stretch the phone layout, reload data on fold, or reset the chart period on rotation. |
| Keep important content and controls out of the hinge area plus 12 dp. | Center a bar, a value or a chart axis across the fold. |

Checked by: state preservation tests in `layouts.md` section 11; layout assertions that no interactive node
intersects fold bounds.

## 7. Motion follows the finger and explains the change

Motion in the spirit of Living Flow Design is physical: it starts from the finger, keeps velocity, and shows where
something came from (a tile morphs into its detail). It never loops or decorates while the person reads.

| Do | Don't |
|---|---|
| Drive gestures with springs that inherit velocity; selection capsules and the chart scrub follow the finger. | Fixed-duration tweens that ignore the gesture, or overshoot on data (values and bands never bounce). |
| Morph a tile into the Metric detail header and back (shared bounds), including predictive back. | Animate idle screens: shimmering glass, breathing glows, looping illustrations. |
| Replace morphs with short crossfades, and springs with snaps, when the system removes or reduces animations. | Ignore `ANIMATOR_DURATION_SCALE`, or keep parallax and glow when animations are off. |

Checked by: macrobenchmark frame timing on the owner's phone (scenarios, frame budget and blur share in `tokens.md`
7.4) with zero frames over budget as the release gate, in glass and in solid mode; UI tests with animations disabled.

## 8. Works for every person, window and device

Large text, TalkBack, keyboards, tiny cover screens, old Android versions, battery saver and slow phones are design
inputs, not afterthoughts. The degraded look is designed, not accidental.

| Do | Don't |
|---|---|
| Let text scale to 200 %: rows grow, labels wrap, values never ellipsize, text never auto-shrinks; small windows scroll instead. | Fix heights in dp for anything that contains text. |
| Keep every touch target at least 48 x 48 dp, with a visible 2 dp focus ring and hover state. | Shrink targets on dense layouts, or hide actions behind hover or long-press only. |
| Switch to solid surfaces and simple motion automatically on API < 31, battery saver, reduced transparency or higher contrast, animations off, low-RAM, hot or janky devices (`tokens.md` 7.3), and say why in Settings. | Keep blurring at the cost of dropped frames, or turn glass off silently with no explanation. |
| Check text contrast (4.5:1) and graphics contrast (3:1) for both themes from the token table, automatically. |
| Give every element a TalkBack description or mark it decorative, and give every chart a visible text summary (`screens.md` 8 and 11). | Leave icon-only controls unlabeled or let TalkBack read raw numbers without units. | Tune colors by eye on one phone in one theme. |

Checked by: `tools/design/contrast_check.py`; screenshot matrix (font 1.0 / 1.35 / 2.0, glass / fallback, all
window sizes); layout assertions (`hasVisualOverflow == false`, targets >= 48 dp).
