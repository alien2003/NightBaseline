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
- **R4 back routing.** One `PredictiveBackHandler` at the root, enabled only while some target can go back (so at the
  root the system's back-to-home plays). The target of a gesture is chosen at its start: the deepest enabled target
  of the shown branch (modal above everything; a sub-page stack above the page stack above the section host). Esc,
  Alt+Left, 3-button Back and TalkBack take the same targets without a gesture.
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

### Enforcement

- `tools/check-interaction.sh` (pre-commit hook and the CI checks job) and `InteractionBoundaryTest` (`./gradlew
  test`) read one list of forbidden APIs (`tools/interaction-forbidden.txt`) and one allowlist
  (`tools/interaction-allowlist.txt`, empty; it may only shrink). They also forbid spring or tween literals outside
  `Physics.kt` and imports of screen packages inside the framework.
- `NbPhysicsTest` (I1, I2, I3, R1, I12), `InteractionCatalogTest` (I1, I7, I8 on the real app: every trigger of the
  inventory's table 2 that changes what is shown runs its kind with >= 3 frames on the one spring object and
  settles; bar / "See all" / Ctrl+2 and round Back / Esc / 3-button Back draw identical p sequences),
  `ScrollInvariantTest` (I4: the same flick scrolls the same and flings on every screen's framework container, also
  50 and 150 ms after an edge bounce), `PredictiveBackTest` (every level under the finger, cancel, commit, R1 during
  a push, R6, reduced motion).

## Consequences

- (a) is fixed by construction: the gesture and the commit are one motion on one spring, a gesture during a push
  reverses it, and nothing outlives its spring. (b, c) every section change, from any trigger, and every page change
  run the same kind on the same spring object. (d) the fling can no longer be dropped and the same flick scrolls the
  same on every screen; every press follows the same rules (tap timeout, cancel on scroll).
- The page underneath a pushed page is composed only while a change runs (the first gesture frame over History
  composes it, about 40-60 ms in Robolectric) [not changed: keeping it composed while covered needs lifecycle and
  focus rules for hidden entries, left for later].
- Night and month pagers step on a release decided by the fling projection; the neighbouring page is not dragged
  under the finger (the view models have no neighbour content) [not changed].
- Item placement animations of the History list, the shared-bounds morphs and pane resize animations do not exist;
  their tokens are gone.
- A detekt type-resolved rule and a public API snapshot of the package are not set up; the source scan is
  regex-based (fully qualified names or type aliases could slip past it) [open].
- On-device confirmation of (a) to (d) needs the owner's phone (ask first).
