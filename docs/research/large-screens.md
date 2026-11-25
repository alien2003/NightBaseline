# Large screens, foldables and flip cover screens: research (November 2026)

Goal: determine which parts of Google's current official guidance NightBaseline needs (Compose, one calculation per
day, several screens: "Today", night history, night details/metrics, settings) to work correctly on
tablets, book-style foldables (Oppo Find N*), flips and in multi-window mode. Sources checked on: 2026-11-25.

Tags: **[verified in code/docs]** - verified against the project code or against files/metadata read in this
session (maven-metadata, BOM); **[from official documentation <url>]** - taken from official documentation;
**[hypothesis]** - an assumption, not verified.

---

## 1. Library versions (as of 2026-11-25)

Verified against `https://dl.google.com/android/maven2/<group>/<artifact>/maven-metadata.xml` and the BOM POM
**[verified in code/docs]**:

| Artifact | Latest stable | Latest pre-release | Note |
|---|---|---|---|
| `androidx.compose:compose-bom` | **2026.11.01** | — | In the BOM: material3 1.5.0, material3-adaptive-navigation-suite 1.5.0, adaptive/adaptive-layout/adaptive-navigation 1.3.0, ui / runtime-saveable 1.13.0 |
| `androidx.compose.material3.adaptive:adaptive` | **1.3.0** (2026-08-12) | 1.4.0-alpha03 (2026-10-07) | |
| `androidx.compose.material3.adaptive:adaptive-layout` | **1.3.0** | 1.4.0-alpha03 | `ListDetailPaneScaffold`, `SupportingPaneScaffold` |
| `androidx.compose.material3.adaptive:adaptive-navigation` | **1.3.0** | 1.4.0-alpha03 | `NavigableListDetailPaneScaffold`, navigators |
| `androidx.compose.material3.adaptive:adaptive-navigation3` | **1.3.0** | 1.4.0-alpha03 | Navigation 3 integration (`ListDetailSceneStrategy`); not in the BOM - specify the version explicitly |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | **1.5.0** | 1.6.0-alpha01 | `NavigationSuiteScaffold` |
| `androidx.compose.material3:material3` | **1.5.0** | 1.6.0-alpha01 | |
| `androidx.window:window` / `window-core` | **1.5.1** (2025-11-19) | 1.6.0-alpha05 (2026-06-17) | `WindowInfoTracker`, `FoldingFeature`, `WindowSizeClass` |
| `androidx.navigation3:navigation3-ui` | **1.2.2** | 1.3.0-alpha05 | only if we choose Navigation 3 |
| `androidx.glance:glance-appwidget` | **1.2.0** | 1.3.0-alpha02 | for a widget (see §9) |

Release dates: [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive],
[from official documentation https://developer.android.com/jetpack/androidx/releases/window].

Recommendation: use BOM 2026.11.01 and do not pull in alphas **[hypothesis]** (rationale: the adaptive alpha APIs
changed in almost every release, see the 1.3.0-alphaXX changelog).

Key points from the changelogs:
- adaptive 1.2.0 (2025-10-22): `currentWindowAdaptiveInfo()` supports Large/XLarge widths (via the
  `supportLargeAndXLargeWidth` parameter), `calculatePosture()` and `currentWindowDpSize()` were stabilized, and the
  Reflow and Levitate adaptation strategies were added [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive].
- adaptive 1.3.0-alpha10 (2026-04-08): **`currentWindowAdaptiveInfo` was deprecated and
  `currentWindowAdaptiveInfoV2()` was introduced**; 1.3.0 shipped a stable `adaptive-navigation3`, and `ListDetailPaneScaffold`
  and `SupportingPaneScaffold` gained margins and edge-to-edge support
  [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive].
- adaptive 1.4.0-alpha01: the Component Override APIs of `NavigationSuiteScaffold` and `ThreePaneScaffold` were removed
  [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive].
- window 1.5.0 (2025-09-24): Large and XLarge breakpoints were added to `WindowSizeClass`; `WindowMetrics` can now
  be computed from the Application Context [from official documentation https://developer.android.com/jetpack/androidx/releases/window].
- window 1.6.0-alpha03/05: engagement mode (`WindowInfoTracker.windowEngagementInfo()`, `EngagementMode.PRECISE_POINTER`),
  callbacks directly on `WindowLayoutInfo`, `WindowInfoTrackerCallbackAdapter` deprecated; Compose `compileSdk` = 37,
  requires AGP ≥ 9.2.0 [from official documentation https://developer.android.com/jetpack/androidx/releases/window].

---

## 2. Window size classes

Breakpoints [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes]:

| Class | Width | Typical device |
|---|---|---|
| Compact | < 600dp | phone in portrait (99.96%) |
| Medium | 600–840dp | tablet in portrait, most book-foldable inner displays in portrait |
| Expanded | 840–1200dp | tablet in landscape, book-foldable inner displays in landscape |
| Large | 1200–1600dp | large tablets |
| Extra-large | ≥ 1600dp | desktop monitors |

| Class | Height |
|---|---|
| Compact | < 480dp (phone in landscape) |
| Medium | 480–900dp |
| Expanded | ≥ 900dp |

API:
- `currentWindowAdaptiveInfoV2(): WindowAdaptiveInfo` (adaptive ≥ 1.3.0) - `windowSizeClass` with Large/XLarge
  **by default** and `windowPosture` [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware].
- The old `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)` - deprecated since 1.3.0 but still
  appears in documentation samples [from official documentation https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/package-summary].
- Class `androidx.window.core.layout.WindowSizeClass`; checks via
  `isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND / WIDTH_DP_EXPANDED_LOWER_BOUND)` and
  `isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)`. Do not use the old `WindowWidthSizeClass` enums.
- Rules from the documentation: classes describe the **window**, not the device - no `isTablet` logic; the class can
  change during the app's lifetime (fold, rotation, split). Make the scaffold decision at the top level
  and pass it down as state; for local decisions inside a component use modifiers or
  `BoxWithConstraints` (more expensive, defers composition until layout) [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes].
- For an open flip in landscape the width is medium but the height is compact - a two-pane layout is impractical there,
  so take height into account [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes].

---

## 3. NavigationSuiteScaffold

[from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation]
- Artifact `androidx.compose.material3:material3-adaptive-navigation-suite` (in the BOM).
- Default: **a bottom navigation bar** if width or height is compact or the device is in the tabletop
  posture; otherwise a **rail** (side navigation column). It switches on the fly when the window changes.
- `navigationSuiteItems = { item(icon, label, selected, onClick) }`; keep the current destination in
  `rememberSaveable`.
- Override: `layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)` or a custom
  `NavigationSuiteType`.
- `NavigationSuiteType` values: `NavigationBar`, `NavigationRail`, `NavigationDrawer`, `None` (legacy; the documentation
  recommends replacements), `ShortNavigationBarCompact`, `ShortNavigationBarMedium`, `WideNavigationRailCollapsed`,
  `WideNavigationRailExpanded` (M3 Expressive) [from official documentation https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/navigationsuite/NavigationSuiteType].
  Instead of `None`, they advise hiding navigation via `NavigationSuiteScaffoldState`.

For NightBaseline: 3 top-level sections (Today / History / Settings) - a natural candidate
**[hypothesis]**.

---

## 4. ListDetailPaneScaffold and canonical layouts

[from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail]
- Dependencies: `adaptive` (`Posture`, `HingeInfo`), `adaptive-layout` (`ListDetailPaneScaffold`,
  `SupportingPaneScaffold`), `adaptive-navigation` (`NavigableListDetailPaneScaffold`,
  `NavigableSupportingPaneScaffold`).
- `rememberListDetailPaneScaffoldNavigator<T>()` → `ThreePaneScaffoldNavigator<T>`; `navigateTo(ListDetailPaneScaffoldRole.Detail, key)`,
  `navigateBack(BackNavigationBehavior.…)` (suspend), `currentDestination?.contentKey`. If `T` is `Parcelable`,
  the navigator saves and restores the selection across configuration changes by itself.
- `NavigableListDetailPaneScaffold` adds navigation and predictive back; wrap panes in `AnimatedPane`.
- `BackNavigationBehavior`: `PopUntilScaffoldValueChange` (default), `PopUntilContentChange`,
  `PopUntilCurrentDestinationChange`, `PopLatest`.
- In a wide window list and detail are side by side; in a narrow one, a single pane.
- Adaptation strategies (adaptive 1.2+): Reflow (a pane moves below another), Levitate (a pane floats on top,
  with a scrim and drag-to-resize) [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive].
- With Navigation 3: `rememberListDetailSceneStrategy()` from `adaptive-navigation3` 1.3.0; `NavDisplay` takes
  `List<SceneStrategy>` (the `then` variant was deprecated in 1.3.0-alpha09) [from official documentation https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive].
- Tier 2 explicitly names `ListDetailPaneScaffold` as the recommended way to build multi-pane UI in Compose
  [from official documentation https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2].

For NightBaseline: "Night history → night details" is a direct list-detail case **[hypothesis]**.

---

## 5. Jetpack WindowManager: fold, hinge, postures

[from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware]
- In Compose: `collectFoldingFeaturesAsState()` (adaptive, android) → `State<List<FoldingFeature>>`, lifecycle-aware
  by itself. Lower level: `WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity)` → `Flow<WindowLayoutInfo>`,
  filtered with `displayFeatures.filterIsInstance<FoldingFeature>()`.
- `FoldingFeature`: `state` (`FLAT` / `HALF_OPENED`), `orientation` (`HORIZONTAL` / `VERTICAL`),
  `occlusionType` (`NONE` / `FULL`), `isSeparating`, `bounds`. The API does not expose the hinge angle.
- Tabletop posture: `HALF_OPENED` and `HORIZONTAL`; book posture: `HALF_OPENED` and `VERTICAL`. Simpler in Compose:
  `currentWindowAdaptiveInfoV2().windowPosture.isTabletop`; `Posture` has `hingeList`,
  and extensions `separatingVerticalHingeBounds`, `occludingVerticalHingeBounds`, `allHorizontalHingeBounds`, etc.
  [from official documentation https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/package-summary].
- When `isSeparating == true`, do not place controls near the fold; when `occlusionType == FULL`, do not put
  content inside `bounds`.
- `WindowInfoTracker#supportedPostures` appeared in window 1.4.0-alpha01 - lets you know in advance whether the
  device supports tabletop [from official documentation https://developer.android.com/jetpack/androidx/releases/window].
- `WindowMetricsCalculator.computeCurrentWindowMetrics(activity)` / `computeMaximumWindowMetrics` - window size
  with backward compatibility; `Display.getSize/getMetrics` are deprecated [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode].

For NightBaseline: special postures are hardly needed (no video or camera). It is enough for ListDetail
and NavigationSuite to account for the hinge - the adaptive scaffolds already receive `Posture`/`HingeInfo` via
`calculatePaneScaffoldDirective(adaptiveInfo)` **[hypothesis]** (based on the component descriptions; not verified on
a real Find N).

---

## 6. Preserving state across fold/unfold, configChanges

- Tier 3 (`Config_Changes`, `Config_Combinations`): on rotation, folding and unfolding, and window resizing,
  scroll position, entered text and keyboard state are preserved; combinations of such
  changes are handled [from official documentation https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3].
- Android 16: because orientation is ignored, the activity is recreated more often; save state per
  https://developer.android.com/topic/libraries/architecture/saving-states
  [from official documentation https://developer.android.com/about/versions/16/behavior-changes-16].
- Compose practice: UI state in `rememberSaveable` (selected tab, selected night as a Parcelable key,
  `rememberLazyListState()` is already saveable), data in `ViewModel` + `SavedStateHandle`; do not load data as a
  side effect of the current size; pass all data to all layout variants (UDF, hoisted state such as
  `showMore`) [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes].
- `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"` - officially described as
  a way to handle window changes yourself (`onConfigurationChanged` instead of recreation)
  [from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode].
  For Compose this removes recreation and flicker; `rememberSaveable`/`ViewModel` are still mandatory,
  because the process can be killed, and a language/theme change still recreates the activity **[hypothesis]**.
  Decision for NightBaseline: add these `configChanges` (+ `density`/`keyboard|keyboardHidden` optionally)
  while still writing code as if recreation can happen. Record it as an ADR **[hypothesis]**.

---

## 7. Android 16 / 17: orientation and resizing on large screens

[from official documentation https://developer.android.com/about/versions/16/behavior-changes-16]
- With targetSdk 36, on displays with **smallest width ≥ 600dp** (in fullscreen and multi-window modes)
  the following are ignored: `screenOrientation`, `resizeableActivity`, `minAspectRatio`, `maxAspectRatio`,
  `setRequestedOrientation()`, `getRequestedOrientation()`. Ignored orientation values: portrait,
  reversePortrait, sensorPortrait, userPortrait, landscape, reverseLandscape, sensorLandscape, userLandscape.
  The app fills the whole window; there are no side bars (pillarbox).
- Exceptions: games (`android:appCategory="game"`), the user explicitly chose the default behavior in the
  aspect ratio settings, screens < sw600dp (i.e. **the Find N outer display and regular phones are not
  affected**).
- Temporary opt-out: `<property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true"/>`
  in `<activity>` or `<application>`. Testing: compat flag `UNIVERSAL_RESIZABLE_BY_DEFAULT`.
- **Android 17 (API 37): the opt-out no longer works** [from official documentation https://developer.android.com/about/versions/17/behavior-changes-17].
- Also for targetSdk 36: edge-to-edge can no longer be disabled (`windowOptOutEdgeToEdgeEnforcement` is disabled),
  predictive back is on by default (`onBackPressed` is not called). API 37 adds a bitmap memory limit in widget
  `RemoteViews` (`6 * width * height` bytes; exceeding it throws `IllegalArgumentException`) - relevant to the widget
  from §9 [from official documentation https://developer.android.com/about/versions/17/behavior-changes-17].

Conclusion for NightBaseline: do not lock orientation at all; on the Find N inner display and on tablets it
will be ignored anyway **[from official documentation https://developer.android.com/about/versions/16/behavior-changes-16]**.

---

## 8. Multi-window mode, freeform / desktop windowing

[from official documentation https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode]
- Modes: split-screen, PiP, desktop windowing (free-form windows). Since Android 12, on large screens any
  app can run in multi-window mode.
- Multi-resume (Android 10+): all visible activities are `RESUMED`; track shared resources via
  `onTopResumedActivityChanged()`. This barely matters to us (no camera or microphone).
- `<layout android:defaultWidth/defaultHeight/gravity/minWidth/minHeight>` - default and minimum
  window size in desktop windowing.
- `Activity.isInMultiWindowMode()`, `onMultiWindowModeChanged()`; `FLAG_ACTIVITY_LAUNCH_ADJACENT` (+ `NEW_TASK`) -
  open adjacent; `ActivityOptions.setLaunchBounds()` - window bounds in desktop mode.
- Multi-instance: `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` (API 35+). NightBaseline does not need this
  **[hypothesis]**.
- Test: several resizes in a row, entering and leaving multi-window mode, readability in a narrow window.

---

## 9. Keyboard, mouse, trackpad

- Tier 3: text input from an external keyboard, switching between physical and on-screen keyboard without restart;
  basic click, selection and scrolling with mouse/trackpad
  [from official documentation https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3].
- Tier 2: Tab and arrow-key navigation (`Keyboard_Navigation`), keyboard shortcuts (`Keyboard_Shortcuts`), Esc
  closes dialogs and menus (`Keyboard_Exit`), right-click context menu, Ctrl+wheel zoom, hover states
  (`Hover_States`), visible focus state on custom interactive elements (`Drawable_Focus`)
  [from official documentation https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2].
- Compose API: `Modifier.focusable()`, `FocusRequester`, `Modifier.focusProperties {}`, `onKeyEvent` /
  `onPreviewKeyEvent`, `Modifier.hoverable()`, `Modifier.pointerHoverIcon(PointerIcon.Hand)`; standard
  M3 components already provide focus, hover and ripple **[hypothesis]** (API names are known; this page was not
  opened in the session, except the shortcuts page).
- Keyboard Shortcuts Helper (Meta+/): override `Activity.onProvideKeyboardShortcuts(data, menu, deviceId)`,
  add `KeyboardShortcutGroup(KeyboardShortcutInfo(...))`; show it from code with
  `requestShowKeyboardShortcuts()` [from official documentation https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/keyboard-shortcuts-helper].
- Android 17 + physical keyboard: passwords are fully hidden (`show_passwords_physical`) - not relevant to us
  [from official documentation https://developer.android.com/about/versions/17/behavior-changes-17].

---

## 10. Quality tiers (Adaptive app quality, formerly Large screen app quality)

[from official documentation https://developer.android.com/docs/quality-guidelines/large-screen-app-quality]
(the page now redirects to "Adaptive app quality guidelines"; the old requirements are in an archive dated 2026-01-21, with
an ID mapping table).
- **Tier 3 - Adaptive ready (baseline)**: full screen or window, no letterbox or compat mode, configuration-change
  handling with state preservation, full multi-window support, multi-resume, basic keyboard,
  mouse and stylus. This is Google Play's baseline standard for large screens.
- **Tier 2 - Adaptive optimized**: adaptive layouts by window size classes, rail instead of bar, multi-pane
  (`ListDetailPaneScaffold`), bottom sheets, buttons and input fields not stretched to full width, touch targets
  ≥ 48dp, extended keyboard and mouse support.
- **Tier 1 - Adaptive differentiated**: foldable postures, drag and drop, stylus, multitasking.
- Tier 2 + Tier 1 earn the Google Play "Large Screen" badge.
- Test configurations: foldable with a 1:1 (802×802dp) and 4:3 (1024×768dp) inner display, tablets
  1024×640 and 1280×800dp, Chromebook 1600×900dp; emulators "Foldable (1:1 inner display)", "Foldable (4:3 inner
  display)", "Pixel Fold", "Medium Tablet", "Desktop (Preview)", plus the resizable emulator.
- Play screenshots: 1:1 and 4:3/3:4 inner displays, outer displays 10:16/16:10 and 9:16/16:9.

Target for NightBaseline 0.1.0: Tier 3 in full and the core of Tier 2 (rail, list-detail for history, limited
content width) **[hypothesis]**.

---

## 11. Flip cover screens and outer displays

### 11.1 Oppo Find N (book-style: Find N5, Find N6)
- Book foldables have a regular phone-sized outer display (Find N6: 6.62″, same as N5)
  [from official documentation https://www.oppo.com/en/newsroom/press/oppo-launches-find-n6/] (diagonal
  per press materials and reviews). There is no separate restricted cover screen: any app runs on the outer
  display, and the same app runs on the inner one in the medium/expanded class. Moving between displays is an ordinary
  configuration change (window size) **[hypothesis]** (based on the general Android model for foldables; not verified on device).
- The outer display is narrow (< 600dp wide) → Android 16 does not ignore orientation there, but we do not
  lock it anyway.

### 11.2 Oppo Find N2 Flip / N3 Flip (clamshells)
- Find N2 Flip: 3.26″ cover screen, 17:9 vertical; it hosts Oppo widgets and quick replies; the third-party Spotify widget
  was built jointly by Oppo and Spotify [from official documentation https://www.oppo.com/ae/newsroom/press/a-new-benchmark-for-oppo-flip-phone-cover-screens/].
- Find N3 Flip: 3.26″ cover screen, 720×382, 60 Hz
  [from official documentation https://www.oppo.com/cn/smartphones/series-find-n/find-n3-flip/specs/] (per search
  results; the full page was not opened). "80+" full apps are supported on the cover screen, including
  third-party ones (Gmail, Maps, Messages, WeChat, TikTok, etc.); the list is set by the user via the "App Lab" section
  in the cover screen settings (effectively an Oppo allowlist) - per the press: https://9to5google.com/2023/10/05/find-n3-flip-cover-display/,
  https://technophileph.com/2024/01/12/oppo-shares-the-things-you-can-do-with-the-find-n3-flip-cover-screen
  **[hypothesis]** (unofficial sources).
- **No public Oppo developer guide on adding an app to the cover screen or on cover-screen widgets
  could be found** (open.oppo.com did not turn up in search). Admission appears to go through a manual Oppo
  allowlist or a partnership **[hypothesis]**.
- **Find N5 Flip / N6 Flip**: no official announcement found. Oppo's latest clamshell, judging by search, is the
  Find N3 Flip (2023) **[hypothesis]**.
- Practical conclusion: on an Oppo flip cover screen, the main channel for NightBaseline is a **notification** (cover screens show
  notifications). A separate cover-screen UI is not needed **[hypothesis]**.

### 11.3 Samsung Flex Window (Z Flip5 and newer) - for comparison
- Flex Window widget: two `<meta-data>` in `<receiver>`: `android.appwidget.provider` (regular XML with
  `widgetCategory="keyguard"`, `resizeMode="horizontal|vertical"`, example `minWidth=352dp`, `minHeight=339dp`) and
  `com.samsung.android.appwidget.provider` → XML `<samsung-appwidget-provider display="sub_screen">`
  [from official documentation https://developer.samsung.com/galaxy-z/flex_window.html]. The Samsung blog gives the sizes
  the other way round (minHeight 352, minWidth 339)
  [from official documentation https://developer.samsung.com/sdp/blog/en/2024/01/09/best-practices-of-app-development-for-various-screen-sizespowered-by-good-lock];
  needs on-device verification.
- Launching an activity on the cover screen from a widget: `ActivityOptions.makeBasic().setLaunchDisplayId(1)` (0 - main display,
  1 - cover screen) in a `PendingIntent` [from official documentation https://developer.samsung.com/galaxy-z/flex_window.html].
- Ongoing notifications are visible on the Flex Window (`setOngoing(true)`, `setColor`, `setSmallIcon`)
  [from official documentation https://developer.samsung.com/galaxy-z/flex_window.html].
- Full apps: system-level, only "compatible" ones in the Apps widget
  [from official documentation https://www.samsung.com/us/support/answer/ANS10013200/]; any app via
  Good Lock → MultiStar → "I ♡ Galaxy Foldable" → Launcher Widget
  [from official documentation https://developer.samsung.com/sdp/blog/en/2024/01/09/best-practices-of-app-development-for-various-screen-sizespowered-by-good-lock].
  Samsung also recommends `configChanges="screenSize|smallestScreenSize|screenLayout"` and `resizeableActivity=true` there.

### 11.4 What this means for NightBaseline
- No vendor SDKs (project rule: ColorOS style is visual styling only). The
  `com.samsung.android.appwidget.provider` attributes are harmless on other devices, but this is vendor code; do not
  add it in 0.1.0 **[hypothesis]**.
- If there is ever a Glance widget: account for the Android 17 bitmap limit; `widgetCategory` with `keyguard` only makes
  sense for Samsung **[hypothesis]**.

---

## 12. Final recommendations for NightBaseline (everything below is [hypothesis]; record as ADR)

1. BOM `2026.11.01`; `material3-adaptive-navigation-suite` and `adaptive`, `adaptive-layout`, `adaptive-navigation`
   from the BOM (1.5.0 / 1.3.0); `androidx.window:window:1.5.1` only if something beyond
   `collectFoldingFeaturesAsState()` is needed.
2. `currentWindowAdaptiveInfoV2()` at the top level; a single decision point for "one pane or two".
3. `NavigationSuiteScaffold`: Today / History / Settings.
4. History → details: `NavigableListDetailPaneScaffold` with a `Parcelable` night key (date).
5. Do not lock orientation, do not set `resizeableActivity=false` or an aspect ratio; targetSdk 36 (or 37).
6. Limit the width of cards and text (`widthIn(max = 600–840.dp)`), buttons not full-width, touch targets
   ≥ 48dp.
7. State: `rememberSaveable` and `ViewModel` + `SavedStateHandle`; add
   `configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`.
8. Edge-to-edge and predictive back are mandatory with targetSdk 36.
9. Test matrix: Foldable 1:1 and 4:3 emulators, Medium Tablet, resizable emulator, split screen; on the owner's real
   device - whatever is available.
10. Flip cover screens: notification only; no vendor widgets in 0.1.0.

## Sources
- https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
- https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes
- https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail
- https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware
- https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode
- https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/keyboard-shortcuts-helper
- https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/package-summary
- https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/navigationsuite/NavigationSuiteType
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/jetpack/androidx/releases/window
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/docs/quality-guidelines/large-screen-app-quality (→ adaptive-app-quality)
- https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3
- https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2
- https://dl.google.com/android/maven2/ (maven-metadata.xml, compose-bom-2026.11.01.pom)
- https://developer.samsung.com/galaxy-z/flex_window.html
- https://developer.samsung.com/sdp/blog/en/2024/01/09/best-practices-of-app-development-for-various-screen-sizespowered-by-good-lock
- https://www.samsung.com/us/support/answer/ANS10013200/
- https://www.oppo.com/ae/newsroom/press/a-new-benchmark-for-oppo-flip-phone-cover-screens/
- https://www.oppo.com/en/newsroom/press/oppo-launches-find-n6/
- Press (unofficial): https://9to5google.com/2023/10/05/find-n3-flip-cover-display/,
  https://technophileph.com/2024/01/12/oppo-shares-the-things-you-can-do-with-the-find-n3-flip-cover-screen
