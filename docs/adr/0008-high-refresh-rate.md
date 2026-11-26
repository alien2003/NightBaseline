# ADR 0008: Ask for the panel's high refresh rate while the app is in use

- Status: accepted
- Date: 2026-11-26

## Context

The owner says the release build "doesn't feel smooth" on the phone (OPPO foldable, Android 17, ColorOS 17), although
the frame counters are fine (scrolling 0.7 % janky, tab switch 3.6 %, [STATUS.md](../STATUS.md)). System logs taken
earlier while NightBaseline was in front showed the outer display at `renderFrameRate 60.0` although the panel offers
120 / 90 / 60 Hz, and the ColorOS refresh-rate manager logged the app's window with "Win preferred rateId=0" (no
preference). ColorOS's own apps run at 90–120 Hz next to it, so the app looks slower even when no frame is dropped.

What the platform and the libraries already do (checked in the resolved jars, Compose BOM 2026.09.00 = Compose UI
1.12.1):

- On API 35+ Compose takes part in the frame-rate category system: `AndroidComposeView` forwards votes to
  `View.setRequestedFrameRate`, and every frame in which a layout node or layer moves (scroll, slides) votes
  `FrameRateCategory.High` by itself (`NodeCoordinator.placeSelf`, `GraphicsLayerOwnerLayer.move`). Fades and redraws
  in place (alpha, colours, the blur under the bar) do not vote.
- `Modifier.preferredFrameRate(FrameRateCategory)` and `Modifier.preferredFrameRate(Float)` are public, non-
  experimental API in Compose UI 1.12. The vote is cast when the node draws, and is passed to the layers below it.
- Even with those votes the phone stayed at 60 Hz with "rateId=0". Hypothesis (not verified yet): the ColorOS
  refresh-rate manager decides a third-party window's rate from the window's own preference
  (`WindowManager.LayoutParams.preferredDisplayModeId`), not from the view's category votes.

## Decision

Ask for the high rate in a battery-friendly way, public API only (`display/HighRefreshRate.kt`,
`display/DisplayModes.kt`):

- **Window preference, API 30+ (including 35+).** While `MainActivity` is resumed, its window sets
  `preferredDisplayModeId` to the mode with the highest refresh rate *at the current resolution*
  (`DisplayModes.highestRefreshAt`: never another resolution; the current mode wins a tie; no preference when the
  resolution offers a single rate or no mode matches). It is held from resume and from every touch or key, and
  released (`0`, the system's choice) 5 seconds after the last finger lifts and on pause, so an idle screen goes
  back to the panel's low rate. A display change (fold / unfold switches panels, a resolution change) recomputes the
  mode while it is held. The attribute is written only when it changes (each write relayouts the window).
- **Frame-rate categories, API 35+.** The navigation root carries `Modifier.preferredFrameRate(FrameRateCategory.High)`,
  so every frame the app draws votes High: scroll and slides (already voted by Compose), and also fades, tab
  crossfades, sheets and the blur under the bar. An idle screen draws nothing and votes nothing. The window's touch
  boost stays on (`Window.setFrameRateBoostOnTouchEnabled(true)`, the platform default, set explicitly).
- **API 28–29: nothing.** The system default stays; seamless refresh-rate switching is only guaranteed from API 30.
- The category vote is applied once at the root instead of on each `LazyColumn` / `verticalScroll`: the vote flows
  down to every layer under it, and scroll already votes High in Compose itself. `PermissionsRationaleActivity` (a
  static text page) has no request.

## Consequences

- Scrolling, transitions and touch run at the panel's top rate (120 Hz on the owner's phone if the hypothesis
  holds) while the person uses the app; 5 seconds after the last touch the request is dropped.
- More frames per second while in use: the per-frame budget falls from 16.7 ms to 8.3 ms. The jank counters must be
  measured again at 120 Hz; a frame that fit at 60 Hz may count as janky now. The second optimisation pass of the
  same change (Compose stability, calendar cells, start-up) lowers the per-frame work; the shell blur stays as it is.
- Battery saver and the person's refresh-rate setting still win: the platform's display-mode director ranks them above
  an app's request.
- `0` (no preference) is the release state, so leaving the app or idling costs nothing extra.
- If the ColorOS manager also ignores `preferredDisplayModeId`, the next step is `preferredRefreshRate` /
  `preferredMaxDisplayRefreshRate` and the logs again; private ColorOS interfaces stay out (vendor neutral).

## Verification on the phone

Needs the owner's permission before any adb access ([CLAUDE.md](../../CLAUDE.md), hard rule 10). The commands are in
[development.md](../development.md#performance): `dumpsys display` (active mode and `renderFrameRate`, the app's
request vote), `dumpsys SurfaceFlinger` (active mode, `--frametimeline`) and `dumpsys gfxinfo` (frame times at the new
rate), each while scrolling History, and again 10 seconds after the last touch (back to the system's choice).
