# ADR 0011: One interaction framework

- Status: accepted
- Date: 2026-11-27 (round 5, the navigation state machine: 2026-11-28)
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
  (Round 5: a change to either of the motion's two states is a new target of the same motion, N3.)
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
- **R7 time origin (round 4).** Without stalls a run is a pure function of time: every frame draws the spring at
  `frame time - origin`. The origin of a tap-driven change is its request (the composition that set it up); the
  origin of a release is the frame that drew the finger's last sample while the finger was still moving (no sample
  arrives between the last move and the lift), or the release itself once the finger had rested longer than the
  platform's stopped-pointer time (`POINTER_STOPPED_MS`, 40 ms), which then also has no velocity; the origin of a
  reversal or a retarget is the last frame the motion drew. Two bounds (`NbPlayClock`): a run **from rest** (no start
  velocity) is at most one display frame into the spring in its first frame, so the time a slow set-up frame took is
  never caught up as a jump (the screen showed the start pose until then); and no frame advances any run by more than
  `FRAME_STALL_MS` (50 ms): a stalled frame slows the motion instead of jumping it. Compose's `Animatable` starts its
  clock at its first frame and draws its start value there, so every spring of the framework runs on one frame loop
  (`nbRunSpring` on `TargetBasedAnimation`): the hosts' p, the reveal and the overscroll return.
- **R8 a change moves only what is drawn** (round 5: modal panels only; pages, sections and switchers move in
  their first frame, N4). A change from rest whose entering layer has no content yet (the page was
  just composed and its state is still loading; a panel not measured yet) holds p at its start, which draws exactly
  the frame before the change, until that layer has content, at most `ENTER_WAIT_MS` (300 ms) after the request; its
  first frame of motion is then at most one display frame into the spring (R7). A layer reports content when it
  emitted and measured some, and no content once it leaves the composition (`NbLayer`).
- **R9 a Back moves first** (superseded in round 5 by N2 and N7: the machine changes at once, the entries stay in
  transition until the motion settles). A Back of a stack or section host, by gesture or not (button, Esc, Alt+Left, 3-button,
  TalkBack), runs its motion to the end and applies its state change (the pop, the section) when the motion has
  settled; the host's next composition completes the change. The cost of the state change (lifecycles, the page
  below becoming the shown one, its flows resuming) falls after the last moving frame. During the motion no layer
  takes input; a second Back without a gesture waits for the first to settle and then runs; a back gesture that
  starts during it lands the first Back at once (its state change applied, its motion at its end) and goes back from
  there, as two Backs do. A Back that catches a cancel or a held gesture of the same change continues it from (p, v),
  and so does a state change that arrives while a cancel runs (V2-2). Modals keep their own close: the caller's state
  change comes first and the panel leaves on its spring.
- **P1 the press comes first** (round 5: the press runs in parallel and the click in the lift event, N4). A click runs after the frame that shows its press: the tap's frame draws the press
  (a press released before any frame drew it, as a quick tap in a scroll container, is drawn lit for one frame), and
  the click runs at the start of the next frame. A Back or a navigation key that comes before a waiting click runs
  that click first and is handled once its state change is composed, so input keeps its order.

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

### Round 3 (2026-11-27): the second measurement and review

Emulator measurements of round 2 (frame by frame, hardware-like evdev input) and a second review found these. Each
fix is in the framework; each has a test that fails without it (checked by undoing the fix).

- **R1-1 the back gesture held, then jumped (report a).** Logged on the emulator: the platform calls back 10-20 ms
  after the last progress event; the release launched its spring dispatched, and `Animatable` drew its start value in
  its first frame, so the page showed the last finger pose for one or two more frames and then moved by all the time
  it had lost in one frame (69-175 ms holds at the emulator's slow frames). Fix: R7 (`NbDriver` frame loop, origin =
  the frame that drew the last sample, the stopped-pointer rule at the release, the release set up undispatched).
  Logged after the fix: the first frame after the platform's call is 19 ms of spring. The commit frame still performs
  the navigation pop (one recomposition); on the emulator that frame was once 33 ms, and the next frame shows the
  elapsed time exactly. Tests: `NbPhysicsTest` R7 (every frame on the closed form at its time since the release,
  release 1, 8 or 15 ms before the first frame; stopped finger), `PredictiveBackTest` (no frame after the release
  repeats the release pose; the first is one frame of the spring from the finger's (p, v)).
- **R1-2 a pushed page entered empty.** The Metric page renders nothing until its `ViewModel` has computed its state
  on a background thread; the push moved an empty layer for 3-4 frames, then the content appeared in one step at
  p = 0.83. Fix: R8. Logged after the fix: the push holds 150 ms on the emulator, then runs from the spring's first
  frame with the page drawn. Test: `FrameworkRound2Test` (no motion while the entering page is empty; the first moving
  frame is an early frame of the spring).
- **R1-3 / review the first gesture frame after a push stalled; the section host laid out its target in the first
  frame.** A parked page was measured but not placed, so its first gesture frame placed and recorded the whole page.
  Fix: parked layers (the page below the top of a stack and every visited section) are placed and drawn ahead in an
  invisible layer (alpha 0: the renderer skips it, its display lists exist), with no semantics, no input and no focus
  (`Hidden` cancels focus entering it). Tests: `FrameworkRound2Test`, `PredictiveBackTest` (the parked layer is
  reported before the gesture).
- **R1-4 a flick right after an edge bounce went 10-28 % farther.** The return spring kept running under the next
  drag, and after the drag the remaining offset relaxed on its own spring on top of the fling's full distance. Fix:
  a finger landing stops the spring at the down event; a fling towards the edge of a translated content takes the
  offset back first with its own distance, 1:1, as a drag does, then scrolls the rest; a touch that did not scroll
  lets the content return at the lift. The one remaining difference is the platform's catch: a finger on moving
  content takes it at once, while at rest the first touch slop of the travel only decides that it is a drag.
  Tests: `OverscrollFlingTest` (nothing but the finger moves the content), `InteractionSweep*` (the same flick 50 and
  150 ms after a bounce moves the content on screen exactly the rest distance plus the touch slop, within 2 px, in
  every container of every registered screen at three sizes).
- **R1-5 the first sheet open stalled, later ones skipped frames.** The sheet's spring ran while its content was
  composed and measured. Fix: R8 for modals (an opening panel waits until it is measured). Test:
  `FrameworkRound2Test`.
- **R1-6 dropped flings with `adb shell input swipe`.** Unchanged: an input-path artifact (V2), 10/10 with evdev input
  in the same cells.
- **R1-7 13-16 px hysteresis on a reversing finger.** Logged on the emulator: the platform's own `touchX` lags on a
  reversal (it reported 291.7 px for a finger back at 300 px, 302.5 px going forwards); the pose is a pure function of
  `touchX`. Test: `PredictiveBackTest` (forwards and back to the same touch position draw the same pose).
- **R1-8 the bar crossfaded its selection.** The selected icon now changes in one step with the selection, so the bar
  shows the target from the first frame of a change; the `Quick` spring and `NbCrossfade` are gone. During a back
  gesture between tabs the bar keeps the shown section until the commit (the gesture may still be cancelled)
  [decision].
- **R1-9 the sheet's fling looked faster.** The sheet content uses the same `nbVerticalScroll` and the same decay; the
  difference measured is rendering (sheet frames are about 80 ms on the emulator) [hypothesis, not changed].
- **R1-10 Home showed "Checking..." for minutes.** Outside the framework; not changed in this round.
- **Review.** A modal's back target is enabled only while its branch is shown (it ignored it, so a modal left open in
  a parked page took Back from the shown page); one routing rule for keys: an open modal takes the step keys too.
  A back gesture moves a modal by the finger's travel, as a page. Shell controls derive their press scale from their
  measured size (`nbPressScale`, as a clickable); under reduced motion every press light switches to the same full
  light and off (the shell had a static 0.6, the primary button's light never lit); the primary button's light is in
  the press model (leave rule, probe samples). A batched move and lift beyond the slop is no tap (the up is consumed
  before the clickable sees it). A step swipe counts its travel from the down and its lift sample, and hands its
  release velocity to the Pager change it makes (the pages still do not follow the finger while it drags: the
  neighbour is composed only once the step is made). A gesture that catches a running pop moves the page 1:1 with the
  finger from where it is. Host memory is written after a composition is applied (`SideEffect`). `AppScreens` is
  checked both ways (every route is opened by a registered screen) and has the Learning state. The boundary forbids
  any use of `androidx.compose.animation.`, fully qualified names included.

### Round 4 (2026-11-27): the third measurement and review

Emulator measurements of round 3 (V2-x, frame by frame, evdev input, two emulators running) and a review (Vx). Each
fix is in the framework; the tests named fail without it (checked by undoing the fixes together and running them:
15 of the round's 22 new tests failed, the others are guards or new API). Logged on one emulator (nb-phone, alone)
with a temporary trace of the back events and the drawn pose, not committed.

- **V2-1 the back gesture held, then jumped.** Two causes. The commit frame applied the pop (the History entry
  resumed, its page became the shown one, a recomposition of the host), so the release's first frame was long
  (70-190 ms with two emulators running), and R7 then caught the whole stall up in one frame. Fix: R9 (the pop after
  the motion) and the stall bound of R7. Logged after the fix, a fast gesture: the last finger pose at 549 px, then
  703, 843, 940, 1033 px on the next frames, the pop composed 16 ms after the last moving frame. The release's velocity
  is now measured on the finger's travel (linear in the finger) and mapped by the slope of the page's gesture map
  (`NbGestureMap.stackPopSlope`); measured on p it read up to 6 % low for fast flicks near the end of the travel. The
  first frames after a release still move faster than the finger: that is the Nav spring's pull towards the end
  (about 640 p/s^2 at p = 0.3), the same spring that starts every pop. Tests: `FrameworkRound3Test` (no state change
  composes while the page moves; the pop exactly once, at the end), `NbClockLawTest` (a stalled frame advances by at
  most `FRAME_STALL_MS`; without stalls every frame is the closed form; the release velocity is the finger's within
  1 %).
- **V2-2 a Back after a cancelled gesture made the page vanish in one frame.** A state change from the shown page to
  the page below found "this change already runs" (its cancel, heading to 0) and set up nothing: the cancel ran on to
  0 and the next frame showed the page below at rest. On the emulator the second gesture worked when it started after
  the cancel; the measured failure is the platform committing a gesture it never started (no start, a commit while the
  cancel returns). Fix: a state change or a Back that meets a cancel or a held gesture of the same change continues
  it to the end from (p, v) (`retarget`, `goBack`). Test: `FrameworkRound3Test` (a Back and a direct pop during a
  cancel's return: p rises monotonically from where the cancel was to the end).
- **V2-3 / V2-4 / review V2 slow first frames.** A run's origin was its request; a set-up frame of 150-450 ms (a first
  sheet open, a tab or page change) was caught up in its first frame (first visible p 0.2 to 0.95). R8 only covered an
  entering layer without content. Fix: R7's first-frame bound for every run from rest (one display frame, from the
  window's refresh rate). Tests: `NbClockLawTest`, `FrameworkRound3Test` (a push whose set-up frame took 200 ms starts
  at the spring's first frame; the same for a reveal). The first-open frames of the explainer sheet themselves stay
  long on the emulator (glass rendering on swiftshader) [not changed: rendering cost].
- **V2-5 pushes started late, with no press feedback.** The click ran in the lift's event, so the frame that composed
  the new page was the first frame after the tap; the R8 wait counted from its own first frame. Fix: P1, and R8's wait
  counted from the request. Tests: `FrameworkRound3Test` (a quick tap in a scroll container has its press drawn before
  its click runs; a push asked for 250 ms before its composition starts within 6 frames instead of 21).
- **V2-6 hysteresis of 6-12 px.** Not the framework: logged on the emulator for the same injected gesture (1 -> 200,
  hold, -> 400, hold, -> 200, hold), the platform delivered `touchX` 202.05 going forwards and 194.46 coming back for
  the finger held at 200 (input resampling: the last delivered position of a moving finger is interpolated or
  extrapolated, and nothing is delivered while it rests); the drawn pose was exactly the map of the delivered value
  both times. Test: `FrameworkRound3Test` (the same delivered position draws the same p on any path, exactly).
- **V2-7 the sheet did not scroll from its side padding.** The scroll container sat inside the panel's padding. Fix:
  `nbVerticalScroll(contentPadding)` applies a margin inside the container; info sheets and dialogs scroll edge to
  edge. Tests: `FrameworkRound3Test` (a margin inside the container scrolls), `ExplainerSheetScrollTest` (the real
  explainer sheet flings from its side margin as from its middle), `InteractionReachTest` (every scrollable container
  of a sheet or dialog reached one tap deep flings from the panel's margin).
- **V2-8 keyboard steps on Night did nothing.** Not the framework: the platform spends the first navigation key after
  a touch on leaving touch mode (it focuses the window), in every app. Logged on the emulator: the first Page Down
  after a tap did nothing, the second stepped, and the arrows stepped. Each measured trial pressed one key after a tap
  [not changed].
- **V2-9 occasional weak flings** (6 of about 620) and **V2-11 frame stalls** in most transition trials: injection and
  rendering on loaded emulators [hypothesis, not changed]; the stall bound of R7 keeps a stalled frame from showing as
  a jump.
- **V2-10 after a bounce a flick went up to 55 px farther** than the rest distance plus the touch slop on the
  emulator. The Robolectric sweep measures the same cells within 2 px [not reproduced; open].
- **V2-12 a swipe in the two-pane Night pane once changed the night but not the list's selection.** The pane's step
  and the list's selection are one value (`vm.select`); the one trial started from another state [hypothesis, not
  reproduced].
- **V1** a modal closed by its caller ran from its open request: every open and every close now takes its origin.
  **V3** the reveal runs on `NbDriver` (frames reported, kind Reveal) and the overscroll return on `nbRunSpring`.
  **V4** a step's hand-over is read in composition and consumed once it is applied; the transition's request time,
  the hosts' values, visited sections and targets' sides, and the modal's retained value are snapshot state or written
  after the composition is applied. **V5** a back gesture moves a modal by the finger's travel from wherever it is
  (`NbGestureMap.linear` = `p0 + g`). **V6** a layer that stops being shown gives up the focus it holds, and a step
  area of a branch not shown steps nothing. **V7** a layer that leaves reports no content. **V8** the parked page
  stays composed through a push, and a pop composes the page that will be below ahead. **V9** the parked probe runs
  in the layer's draw (Robolectric draws the window on request in the tests). **V10** `InteractionReachTest` goes one
  tap deeper (every pressable of every screen at three sizes; sheets, dialogs, toggles and panes), opens every screen
  in every other layout mode (Glance, CompactShort, WideShort, Tabletop, Medium, Expanded, Large) and the permission
  rationale, and checks that every scroll container id of the sources is reached, except a reviewed list that may only
  shrink: the not-recorded sheet (the sample data never opens it) and the two panes of a night opened from Home in Book
  (the registry opens nights from History, which fold into History's pane; registering that state made the fold-open
  pressable sweep report a swipe that changed a selection on it, not investigated in this round [open]); the boundary
  check requires a literal, unique id on every scroll container. **V11** pagers, staggered and horizontal grids, marquees, `android.view.animation` and raw motion events
  are forbidden outside the framework. **V12** a fold drops a modal's back gesture. **V13** the default indication and
  the clickable's highlight report their samples (`NbPressSample.Highlight`). Tests: `FrameworkRound3Test` (one per
  item), `InteractionBoundaryTest`, `InteractionReachTest`.
- A Back right after a modal opened found the root handler still disabled (it followed the targets one composition
  late); it now follows them at once.

### Round 5 (2026-11-28): one navigation state machine

Three rounds of fixes did not converge on navigation: each added logic for races between a gesture and a Back
(deferred Backs, queued Backs, landed Backs, a retarget of a running cancel), and the measurements and the review of
round 4 found new defects in exactly that logic (R3-1 the page under a second gesture sat still and then vanished,
R3-2 the bar showed target, old, target, R9-2 to R9-4 lost and replayed Backs) and in the delays it introduced (R3-3
a tap's push started 250 to 680 ms after the tap, a Back after 50 to 170 ms). The navigation part of the framework is
replaced by **one state machine per host**; the scroll, fling, overscroll and press models, the springs and tokens,
the boundary check and the invariant tests stay.

**N1 one source of truth.** Each host (the app's pages, the top-level sections, the Settings sub-pages in every
layout, the onboarding steps, the debug catalog, every in-place switcher) holds an `NbNavState`: its logical stack
(the stack every input has already been applied to; its top is what the host shows at rest, what Back leaves and
what fixed elements show) and **one** motion `NbMotion(from, to, p, v, drive, target)`, drive in {Finger, Spring}
(no motion = idle). `NbNavReducer.reduce(state, event)` is a pure function: there is no deferred, queued or replayed
input, no token and no abort bookkeeping. `NbTransition` only holds the state as snapshot state, applies events,
pumps display frames into the reducer while a motion exists and draws the two layers as pure functions of p.

**N2 every input is an event, applied at once.** A tap, a link, a round button, the bar, the rail, a key, a view
model's step (`Go`), the back gesture (`GestureStart`, `GestureMove`, `GestureEnd(commit)`), a display frame
(`Frame`) and a layout change (`Abort`). A Back changes the machine first and is then reported to the host's caller
(`onBack`: the controller pops, the view model steps back) in the same event; the caller's change then meets a
machine that already shows it (a no-op). The app's pages change only through the framework's calls (`nbPush`,
`nbGo`, `nbReset`, `nbPopTo`, `nbPop`), which apply the controller's new stack to the machine in the same input
event, and through the router's Back. A caller whose state disagrees with the machine at rest for two frames (its
Back did nothing) is followed by the machine.

**N3 a new event catches the running motion.** A `Go` whose new top is one of the motion's two states only changes
its target, from the current (p, v): a reversal, a retarget, a Back during a held gesture or during a cancel's return.
The layers, the pose functions and p stay, so every catch is C1-continuous by construction (the reducer never swaps
the layers and never maps p to 1 - p). Velocity follows R0 (kept towards the target, dropped away from it, capped
towards a visible rest) and the stopped-pointer rule (a finger that rested 40 ms has none; review R0-1). A gesture
start on a motion between the logical top and the page Back leads to (a cancel still returning, a push still
entering) takes it over at its current p: the finger's offset is the upper layer's current edge, nothing jumps
(R3-1). A commit is a spring to the commit side from (p, v), a cancel a spring back; a commit the platform sends
without a start, or after a voided gesture, is a Back: a `Go` that catches the motion where it is (R3-1). A third
state (neither of the two) ends the running motion at its target (R2) and starts the new one from rest: the only
discontinuity, and only between two different changes.

**N4 one kind, one spring, one pose, whatever the trigger; the first frame moves.** A motion from rest is set up
pre-advanced by one display frame (R7's first-frame bound, now exact): the frame that first draws a tap's push, a
Back, a bar tap or a key already shows the spring one frame in, and the next frames follow the R7 clock (at most
`FRAME_STALL_MS` per frame). Clicks run in the lift event (P1, round 5: the press is drawn in the same frame; round 4
ran the click two frames later). The entering page is composed in that first frame; R8's wait for its content is gone
for pages, sections and switchers (it delayed every push by up to 300 ms and differed by trigger, R3-3, R3-4): an
entering page shows what it has. Modals keep R8 for their own panel, and the explainer sheet is composed and drawn
once, invisibly, shortly after launch (`ModalHost(prewarm)`, R3-5).

**N5 the gesture maps the finger linearly.** The upper layer's edge is its edge at the gesture's start plus the
finger's travel (touch x minus its x at the start, along the motion's side, over the host width): `u = u0 + g`, with
u the upper layer's travel share `1 - s^2.2` and p its inverse. It is a pure function of the touch x, so the same x
draws the same edge in both directions (R3-8; the remaining difference measured on the emulator is the platform's
own resampling of `touchX`, V2-6). Its velocity comes from the platform's least-squares tracker on u, times dp/du.

**N6 the bar shows the logical section.** The fixed elements of the section host (the bar, the rail) show its
logical top: the target of a spring from its first frame (a tap, a key, Back), and during a back gesture the section
it started from until the release commits; a cancel never changes it. One value, so no third state (R3-2).

**N7 one path for every Back.** Every Back control goes through the router: the shell's round Back button routes
itself (`ShellRoundButton` with the Back glyph), so the Settings sub-page bars, the two-pane and tabletop back rows
and the onboarding Back (button and text button) are the same event as Esc, Alt+Left, 3-button Back, TalkBack and the
gesture (R9-1). The router decides on the machines' logical stacks, so a second Back right after a first one goes to
the next level at once (R9-2 to R9-4 cannot occur: there is nothing deferred to land, queue or replay). Night stepping
(buttons, swipe, keys) changes the night pager's value: a `Go` of the same reducer.

**N8 in-place results.** History's list / calendar toggle and its filters (including "Show all nights") are a Swap of
the same machine (`rememberNbSwap`): the old results fade out over the first half, the new ones in over the second;
the items change at p = 1/2, where both are invisible (MIG-1). The modal scrim takes taps through the framework's tap
(`nbTap`: tap slop, the same click path; P1-1). A reveal's reversal follows R0 (REV-1).

Superseded by N1 to N8: R2's snap of a reversal (a reversal is a new target), R3's "the leaving state is dropped and
its entry completed in the first frame after p settles" stays, R8 for pages, R9 (the state change no longer waits for
the motion: the machine changes at once and the controller's entries stay in transition, the leaving page alive and
the page below not resumed, until the motion settles) and P1 (the click no longer waits for its press frame).

Tests (`NavMachineTest`, `NavFramesTest`; each fails on round 4, as stated per test): random sequences of 80 events
(taps, Backs, gesture start, moves, cancel and commit, frames at random intervals with 220 ms stalls, layout changes)
over 400 seeds: the logical stack equals the instant model's after every event; the motion rests on the logical top;
p is unchanged and v kept or reduced (never reversed) at every catch; a motion from rest starts exactly one frame in;
no frame advances a spring by more than the stall bound; every sequence settles within 3 s. Frames: R3-1 (a second
gesture during a cancel's return, with moves, without moves, without a start: the edge follows the finger and the
commit runs monotonically over at least 6 frames, never a jump); R3-2 (the bar changes once per committed change);
R3-3 / R3-4 (a tap, a Back, a bar change, a gesture commit and a pager step move in the first frame after the input,
at most one spring frame in); R3-8 (the edge equals the finger's travel within 1 px both ways, from any start); R3-5 /
R3-7 (the first sheet open and the first gesture frames within twice the median frame).

### Enforcement

- Navigation: `NavMachineTest` (reducer properties over random event sequences) and `NavFramesTest` (frames of
  the open items of round 4); the boundary forbids the controller's `navigate` and `popBackStack`, `composable<`,
  NavHost and its transition parameters outside the framework, so every navigation is an event of a machine.
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
- The page underneath the top of a stack and every visited section are composed, placed and drawn ahead in an
  invisible layer (round 3): no focus can enter them, they have no semantics and take no input; the page's navigation
  entry stays in its lifecycle state (CREATED), so its flows keep the last values they collected while it was shown.
  They are laid out when their content changes, a cost paid while they are hidden instead of in a gesture's frame.
- A pushed page whose state is still loading enters up to 300 ms later (R8) instead of sliding in empty.
- Night and month pagers step on a release decided by the fling projection and start with the swipe's velocity; the
  neighbouring page is not dragged under the finger (the view models have no neighbour content) [not changed].
- Item placement animations of the History list, the shared-bounds morphs and pane resize animations do not exist;
  their tokens are gone.
- A detekt type-resolved rule and a public API snapshot of the package are not set up; the source scan is
  regex-based (fully qualified names or type aliases could slip past it) [open].
- On-device confirmation of (a) to (d) needs the owner's phone (ask first).
