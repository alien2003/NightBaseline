# ADR 0011: One interaction framework

- Status: accepted
- Date: 2026-11-27
- Supersedes: the page pose, the Leave / InPlace settles and the 1 s safety timeout of
  [ADR 0009](0009-predictive-back.md); ADR 0009's decision to preview every back level stays.

## Context

The owner on the release build (OPPO book foldable, Android 17, ColorOS 17), all four at once:

- (a) a back gesture from a night opened from History is slow, the page seems stuck halfway, then disappears;
- (b) "See all" opens without animation while everything else animates;
- (c) gesture slide-outs run at a different speed from the bar and the round top-bar buttons;
- (d) scrolling: Settings is "tight and heavy and the fling works every other time", Home is similar, Night is the
  lightest.

And the requirement: consistency must not be a matter of feel; it must be guaranteed mathematically and
algorithmically, by one framework, not by patches in different places.

The inventory at 76c812a found four press systems, three back / switch motions, two predictive-back systems, four
top-bar wrappers, about 15 inline spec literals and ten unused tokens. The root causes, reproduced in Robolectric:

- (a) `NbSpring.spec()` had no visibility threshold, so the 1.0 / 220 page slide settled to Compose's 0.01 px and
  lasted about 1 s; a back gesture inside that window did not seek NavHost, which then played the push transitions
  on commit (the leaving page slid to -W/3 under a frozen 0.92 preview pose for about 900 ms, then vanished in one
  frame). Every commit restarted the pop from rest (navigation-compose calls `animateTo` with a null spec), dropping
  the finger's velocity.
- (b, c) a tab change was a 150 ms alpha crossfade, a back gesture to Home a spring plus that crossfade, a pushed
  page a 1.0 / 220 slide, the shell press a 0.6 / 400 release with no tap timeout.
- (d) the overscroll threw away a fling whenever an offset was left (`applyToFling` returned without
  `performFling`), so a flick within 150 to 300 ms of an edge bounce scrolled 0 px.

## Decision

`io.github.alien2003.nightbaseline.ui.interaction` is the only package allowed to use Compose animation, gesture,
scroll, overscroll, indication, key, back or navigation APIs. Screens state what changed; the framework moves it.

### The principle

Every animated change is one scalar **p** in [0, 1] of the host the state lives in:

- p is driven by **one spring of one registry** (`Physics.kt`: `NbPhysics`), with one settle tolerance
  `P_EPS = 1e-3` of the travel;
- a pure pose function of the **kind** turns p into what is drawn (`NbPoses`);
- the kind is a **total function of the relation** between the two states (`kindOf`), and the relation is derived by
  the host from the state change itself: a stack compares depths (Page), the section host compares bar order (Tab),
  a switcher with an order compares values (Pager), one without an order has peers (Swap), overlays are Sheet,
  Dialog or DockedDialog, an element that appears in place is a Reveal.

So the same change has the same motion whatever triggered it: a tap, a link, the bar, a round button, a key, 3-button
Back and a gesture all change the same state and meet the same host. There is no "none" kind; reduced motion is a
rendering of every kind (a snap).

### Hosts

- `NbStackHost` (Page): app routes (navigation-compose keeps the back stack, entries, ViewModels and saved state; a
  framework navigator records which entries are in a change; `NbNavStack` draws them instead of NavHost, whose commit
  cannot be continued), Settings sub-pages in every layout (single pane, Glance, Tabletop, the two-pane detail pane's
  own About -> Licenses stack), onboarding steps and the debug catalog. A pushed Settings route starts its stack at
  the page it opened, so one Back leaves it (inventory B11).
- `NbSectionHost` (Tab): the top-level sections stay composed (ADR 0006); the bar and rail stay in place while the
  pages move under them.
- `NbSwitcher` (Pager, Swap), `NbReveal` (Reveal), `ModalHost` (Sheet, Dialog, DockedDialog).
- All of them run on one class, `NbTransition`, so they share the clock, the release rule, the reversal and the end.

### Rules

- **R0 release.** A back gesture writes p (the leaving page stays under the finger) and its velocity is measured on
  p. The release continues on the same spring from (p, v). Towards a state that leaves the screen (commit, hide) the
  velocity is not capped and the motion ends at its first crossing of the target (the driver is bounded to [0, 1]);
  towards a visible rest (cancel, a sheet returning) it is capped at the crossing velocity
  `omega (zeta + sqrt(zeta^2 - 1)) a`. Framework-decided gestures (sheet drag, step swipes) project the travel with
  the platform fling decay, the same physics as scrolling.
- **R1 reversal.** A change back to the state being left runs from (1 - p, -v) on the same layers, which draws the
  same frame; a back gesture that catches a running push takes it over from where it is.
- **R2.** Any other new change while one runs snaps the running one to its end. Hosts are independent.
- **R3 lifecycle.** The leaving state is dropped in the first frame after p settles; its navigation entry is marked
  complete only then (its ViewModel is cleared then, and a pushed entry is resumed then, so a double tap pushes once).
  No timeout, no pose held after the end. During a gesture both layers swallow input; during a tap-driven change
  the leaving one does.
- **R4 back routing.** One back callback at the root (`OnBackPressedCallback`), enabled only while some target can
  go back (so at the root the system's back-to-home plays). The target of a gesture is chosen at its start event:
  the deepest enabled target of the shown branch (modal above everything; a sub-page stack above the page stack above
  the section host; each layer of a swap is its own branch, so a leaving layer is no target). Esc, Alt+Left,
  3-button Back and TalkBack take the same targets without a gesture. The finger's travel is the touch position's
  distance from the start event over the host's width, not the platform's progress (shaped on Android 14+).
- **R5 reduced motion**, one rule at the root: animations removed (animator scale 0) or battery saver on
  [decision: battery saver also removes decoration, as before]. Every p snaps; press scale, glow and overscroll are
  off; a back gesture goes back at once. Fling stays (content physics). Other duration scales come from the
  platform's `MotionDurationScale` and are not applied again.
- **R6 layout is not navigation.** A fold or unfold that re-projects the pages (`FoldBack`) snaps, and drops a
  running back gesture; the rest of that gesture does nothing.

### The two lemmas (proved in `NbPhysicsTest` by closed form and frame by frame)

Write d = p - target, a = |d0| and let the spring have natural frequency omega and damping zeta >= 1, roots
r1 = -omega (zeta - sqrt(zeta^2 - 1)) and r2 = -omega (zeta + sqrt(zeta^2 - 1)).

1. *No crossing.* From (d0 = -a, v0 >= 0), d(t) = c1 e^(r1 t) + c2 e^(r2 t) has a zero for t > 0 iff
   v0 > -r2 a = omega (zeta + sqrt(zeta^2 - 1)) a. Below it, p rises monotonically to the target.
2. *Never slower than a tap.* By linearity d(t) = -a phi(t) + v0 psi(t), with phi the response to a unit
   displacement and psi to a unit velocity, both positive for t > 0 when zeta >= 1. With 0 <= v0 below the crossing
   velocity and a <= 1: |d(t)| = a phi(t) - v0 psi(t) <= phi(t), the tap from rest, for every t. So with the same
   tolerance a release settles no later than a tap. Above the crossing velocity (the uncapped commit) the bound
   stops the motion at the crossing, which is earlier still.

The per-frame step of a release is bounded by the exact supremum of the speed of the closed form (the review showed
the earlier hand bound `max(v0, omega a / e)` to be false); the test samples it.

### Values

- `Nav` = damping 1.2, stiffness 860, with the page pose `upper tx = e W (1 - s^2.2)`, `lower tx = -e W/3 s`,
  dim `0.3 s` (dark theme). Fitted by `tools/design/fit-spring.py` to the per-frame offsets of the ColorOS Settings
  back recording (`reference-analysis.md` 8.1) with a free start offset and free damping: rms 2.7 px (incoming page)
  and 1.7 px (outgoing page). A linear coupling of the two layers fits only at 15.4 px, so the layers are two pure
  functions of the one p. A cubic-bezier control fits at 4.6 / 1.1 px. The earlier 1.0 / 220 page spring is gone.
  **HYPOTHESIS** until the offsets are re-measured at pixel level from the frames (the fit uses the table, which
  was read from them).
- The Tab kind uses the Page geometry [decision: no OPPO tab-switch recording exists; with it, a bar tap and a back
  gesture to Home are one motion by construction]. If OPPO crossfades tabs, it is one pose function to change, and
  the gesture back to Home follows.
- Overscroll: pull `0.5 (1 - |o| / 240 dp)`, return on `Overscroll` (1.0 / 150) seeded 1:1 with the leftover fling
  velocity [estimate; the old 0.24-0.34 follow felt heavy].

All values are mirrored in `tokens.md` 9 and compared with the registry by `MotionTokensDocTest`.

### Round 2 (2026-11-27): measured violations and their root causes

Emulator measurements of the first round (frame-by-frame, injected input) and a review found these; each fix is in the
framework and each has a test that fails without it.

- **V1 bump after every fling.** Compose's `OverscrollEffect.applyToFling` hands a `performFling` that returns the
  velocity the content **consumed**; the effect took it as the leftover, so every fling threw the content about
  40 px past its rest and back (measured on every page, absent in the sheet). Fix: leftover = velocity - consumed
  (`OverscrollFlingTest`; `InteractionSweep*`: no overscroll frame during a fling that rests inside the content).
- **V2 dropped flings.** Measured with logging on the emulator: every dropped flick had a gap of 56-59 ms before its
  last sample, every kept flick at most 39 ms. The platform's velocity tracker takes a pointer that reports nothing
  for 40 ms as stopped (velocity 0). The gaps come from `adb shell input swipe` injecting each event after the busy
  emulator handled the previous one; a real touchscreen timestamps samples in hardware. The fling itself was never
  lost. [decision: the platform's stop rule stays; a pause before lifting is not a fling] `FrameworkHostsTest`
  reproduces both cases; the first round's scroll test counted a fling at velocity 0 as a fling, and
  `InteractionSweep*` now require a release velocity above 1000 px/s, at rest, 50 and 150 ms after a bounce, and from
  a pressable, at three window sizes.
- **V3 a swipe became a tap.** The tap rule is now part of the press model: once the finger moves beyond the touch
  slop, the press is cancelled. The night step area is the whole page (one rule for every sequence), and Page Up /
  Down and the arrows step it with or without focus through the router's key path. Observed on the emulator: after
  a tap the first `DPAD_LEFT` changes nothing and every following arrow or Page key steps; the platform spends the
  first navigation key in touch mode on leaving touch mode before the activity sees it (the same in every app), which
  is what the first round measured as "DPAD does not step".
- **V5 / review 4 first gesture frame stalls.** The page below the top of a stack is composed and measured ahead,
  parked (not placed, not drawn, no semantics, no input); a gesture or pop only places it.
- **V7 broken back after a fold.** A fold dropped a running push and the settle of that drop completed every entry in
  transition, including the page the fold's snap was still showing; its ViewModel store was destroyed while it was
  drawn (reproduced deterministically by opening a night in two panes). Fix: entries are completed only when the host
  is at rest.
- **Review 1** the bar of a leaving section showed the old section: every section's fixed elements read the host's
  selection (`nbShownSection`). **Review 3** the page followed the platform's progress, not the finger. **Review 6**
  shell controls pressed on another spring: one press model (`NbPressModel`). **Review 9** keys: screens get decoded
  intents (`NbKeyIntent`) only; scroll commands, `VelocityTracker`, `PointerEventPass`, `Modifier.indication`,
  `BackEventCompat` and `NbSpring(` (now an internal constructor) are forbidden outside the framework. **Review 10**
  `go()` writes snapshot state only (a discarded composition rolls it back); the reprojection flag lives on the app's
  router, not in a global. **Review 11** overscroll in-progress is a count. **Review 13** the night pager spans the
  window width.
- Registries: `AppScreens` (every destination with every page argument; a test fails for an unregistered
  destination), `NbScrollContainer` (the id is required and unique per screen) and `NbPressable` (set by every
  framework pressable). The sweep enumerates screens, containers and pressables from them.

### Enforcement

- `tools/check-interaction.sh` (pre-commit hook and the CI checks job) and `InteractionBoundaryTest` (`./gradlew
  test`) read one list of forbidden APIs (`tools/interaction-forbidden.txt`) and one allowlist
  (`tools/interaction-allowlist.txt`, empty; it may only shrink). They also forbid spring or tween literals outside
  `Physics.kt` and imports of screen packages inside the framework.
- `NbPhysicsTest` (I1, I2, I3, R1, I12), `InteractionCatalogTest` (I1, I7, I8 on the real app: every trigger of the
  inventory's table 2 that changes what is shown runs its kind with >= 3 frames on the one spring object and
  settles; bar / "See all" / Ctrl+2 and round Back / Esc / 3-button Back draw identical p sequences),
  `InteractionSweepPhoneTest` / `FoldClosedTest` / `FoldOpenTest` (every registered screen, container and pressable
  at 412, 380 and 749 dp in two panes: fling occurrence, equal fling distance within 1 px, no bump, flings after a
  bounce and from a pressable, a swipe is never a click, and every trigger of one kind draws the identical p curve on
  the Nav spring), `PredictiveBackTest` (every level under the finger, also with a shaped progress; cancel, commit,
  release velocity continuity, the page below composed ahead, R1 during a push, R6, reduced motion),
  `FrameworkHostsTest` (section bar, swap back scope, one press model, tap slop, the pointer-stop rule, the key
  decoder), `OverscrollFlingTest` (no bump, exact throw).

## Consequences

- (a) is fixed by construction: the gesture and the commit are one motion on one spring, a gesture during a push
  reverses it, and nothing outlives its spring. (b, c) every section change, from any trigger, and every page change
  run the same kind on the same spring object. (d) the fling can no longer be dropped and the same flick scrolls the
  same on every screen; every press follows the same rules (tap timeout, cancel on scroll).
- The page underneath the top of a stack is composed ahead and parked (round 2): unplaced nodes take no focus, no
  semantics and no input; its navigation entry stays in its lifecycle state (CREATED), so its flows keep the last
  values they collected while it was shown.
- Night and month pagers step on a release decided by the fling projection; the neighbouring page is not dragged
  under the finger (the view models have no neighbour content) [not changed].
- Item placement animations of the History list, the shared-bounds morphs and pane resize animations do not exist;
  their tokens are gone.
- A detekt type-resolved rule and a public API snapshot of the package are not set up; the source scan is
  regex-based (fully qualified names or type aliases could slip past it) [open].
- On-device confirmation of (a) to (d) needs the owner's phone (ask first).
