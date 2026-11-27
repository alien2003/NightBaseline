# ADR 0009: Predictive back for screens

- Status: accepted; the page pose, the settles and the timeout superseded by [ADR 0011](0011-interaction-framework.md)
- Date: 2026-11-26

## Context

The owner: "when doing the back gesture the page should move away under the finger, not just show the back arrow"
(as on Pixels). Until now only sheets and dialogs followed the system back gesture (`ModalHost`,
`PredictiveBackHandler`). Screens used plain `BackHandler`s (tab -> Home, Settings sub-pages, onboarding steps, the
debug catalog) and the NavHost push / pop transitions, which ran only after the gesture was released.
`enableOnBackInvokedCallback` is already on in the manifest.

The target look is item P3 of [coloros-conformance.md](../design/coloros-conformance.md): the leaving page scales
towards 0.92, its corners round to `shape.xl`, it travels about 12 % of its width with the finger, and the page it
uncovers waits in the covered pose of a pop (parallax, and the page dim of push / pop); a release completes on
`spring(1.0, 220)`, a cancel returns on `spring(1.0, 1500)`. **These values are estimates.** No slow back swipe of
an OPPO system app has been captured, so what ColorOS 17 actually does during an in-app back gesture is unverified.

Navigation-compose 2.10's NavHost has its own predictive back: it seeks its pop transition straight from the gesture
progress. That would slide the page over the whole width under the finger (an iOS-like drag), and an
`EnterTransition` / `ExitTransition` cannot round corners; its cancel is a linear tween of the remaining time.

## Decision

One mechanism for every "back a level" (`ui/shell/BackPreview.kt`):

- **`BackPreview`** holds which page follows the gesture (a key), the progress (an `Animatable`), the swipe edge and
  the phase. Pages read it in `Modifier.backPreviewPage` inside `graphicsLayer` only (scale, translation, smooth
  `shape.xl` clip), so a progress frame never recomposes page content. At rest the layer has no transform and no
  clip (screenshots stay pixel-identical). While a page carries a gesture its touches are swallowed, so a press under
  the finger is cancelled.
- **`BackPreviewHandler`** wraps `PredictiveBackHandler`: progress -> pose, cancel -> `motion.back.cancel` back to
  rest, commit -> the back action. Back without a gesture (3-button, keyboard Esc / Alt+Left, TalkBack) runs the
  action directly; so does any gesture under reduced motion (animator duration scale 0 or battery saver): no preview.
- **Two ways to finish a commit.** `Leave`: the back action starts a transition that takes the page away, the page
  keeps its pose while it leaves and the pose is released when it is disposed (or hidden), with a 1 s safety net.
  `InPlace`: the content changes in place and the pose springs back on `motion.back.release`.
- **Values are tokens** in `ui/motion/Motion.kt`: `NB_BACK_SCALE` 0.92, `NB_BACK_TRAVEL` 0.12, `NB_BACK_CORNER_DP`
  28 (`shape.xl`), `NbSprings.BackRelease` (= `spring.page`, 1.0 / 220), `NbSprings.BackCancel` (= `spring.follow`,
  1.0 / 1500). The sheet's own back scale uses the same `NB_BACK_SCALE`.
- **Direction.** "Travels towards the swipe edge" is read as: the page follows the finger, away from the edge the
  swipe starts at (right for a swipe from the left edge, left for one from the right edge). The uncovered page comes
  from the other side, and a committed page slides off in the finger's direction.

Per level:

| Level | Page underneath during the gesture | Commit |
|---|---|---|
| Pushed routes (Night, Metric, Settings route, anything pushed) | NavHost composes it at the start of its predictive pop-enter (a third of the width away, dimmed on the dark theme) | `Leave`: the NavHost plays the rest of the predictive pop on `spring.page` |
| Tab -> Home (History, Settings) | `TopLevelHost` places Home under the section in the covered pose (view only, semantics cleared) | `Leave`: the section crossfades out in its pose, Home springs to rest |
| Settings sub-page -> parent (single pane) | the sub-page `AnimatedContent` runs on a `SeekableTransitionState`, held at the start of the pop | `Leave`: the pop plays on `spring.page` |
| Settings Licenses -> About (two panes) | only the detail pane previews, never the window | `InPlace` |
| Onboarding step -> previous step | none (steps change in place) | `InPlace` |
| Debug catalog -> app | none | `InPlace` |

**NavHost bridge** (`nav/NavBackBridge.kt`): the NavHost is given a private `NavigationEventDispatcher` that only a
`DirectNavigationEventInput` feeds. Our handler on the window's dispatcher receives the gesture, draws the preview,
and tells the NavHost only that a gesture started (progress 0: both entries composed, the underlying one at the start
of `predictivePopEnterTransition`), was cancelled (after the page is back at rest) or completed (the pop runs the
predictive transitions to the end). Every destination gets the window's dispatcher back, so the destinations' own
handlers and sheets register exactly as before, later and therefore with priority. The NavHost API and public
`androidx.navigationevent` classes only; no reflection.

**Root.** On Home (and on the first onboarding step) no handler is enabled, so the window has no enabled callback
and the system's back-to-home animation plays.

**Robustness.** A sheet registers after the page handlers and handles the gesture first. A new gesture during a
cancel of the same page resumes from where the page is; any other gesture takes over and clears the previous pose. A
window change (fold, unfold, rotation, resize) during a gesture drops it at once (`BackPreview.abort`: every page at
rest, the NavHost told to cancel, the rest of that gesture ignored).

Tests: `PredictiveBackTest` drives start / progress / cancel and start / progress / commit through the
`OnBackPressedDispatcher` for a detail route, a second gesture during the settle, tab -> Home, a Settings sub-page,
an onboarding step, the two-pane detail pane, a window change, reduced motion and keyboard Back, and checks the
poses through `BackPreviewProbe`, the destinations, no recomposition per progress frame and no handler on Home.
`SheetFramesScreenshotsTest.predictiveBackFrames` writes 50 % and commit frames for review.

## Consequences

- Screens follow the finger like the sheets do; the values can be tuned in one place once a slow-gesture capture of
  an OPPO system app exists (P3 stays `[needs reference capture]` for the values).
- The NavHost no longer sees the window's back events directly. A future navigation-compose that changes how
  `NavHostEventHandler` reacts to started / cancelled / completed events must be checked against
  `PredictiveBackTest`.
- Not covered: Settings sub-pages in the Glance and Tabletop layouts take Back without a preview (they have no page
  transition); onboarding and the catalog show no page underneath during the gesture.
