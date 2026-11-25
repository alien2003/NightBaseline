# Research: ColorOS 16/17 visual style and UI libraries for Compose (stage 3)

Date: 2026-11-25. Purpose: input data for the ADR on NightBaseline's visual design.

Evidence tags:
- **[verified in code/docs]** — verified in the sources/documentation of the cloned repositories or in our project;
- **[from official documentation &lt;url&gt;]** — from OPPO pages (oppo.com) or from official library/Android documentation;
- **[hypothesis]** — inference or observation from the press/third-party sources, not officially confirmed.

Reminder of the product boundary (CLAUDE.md): the ColorOS style is visual design only, with no vendor SDK or resources;
the app is vendor-neutral; project minSdk = 28 [verified in code/docs: `gradle/libs.versions.toml`].

---

## 1. Official name and style principles

### 1.1 ColorOS 17 — "流体设计" / Living Flow Design

- OPPO's official Chinese press release calls the design language **流体设计** ("fluid design") and lists three
  components: **凝光视效**, **流体动效**, **柔性反馈**; the design goes "深入系统渲染层" (deep into the system rendering layer)
  [from official documentation https://www.oppo.com/cn/newsroom/press/549/].
- OPPO's official English article: **Living Flow Design**, three components — **Luminous Effect** (this is
  凝光视效), **Fluid Motion** (流体动效), **Flexible Feedback** (柔性反馈)
  [from official documentation https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/].
- The press writes "Fluid Design" (a calque of 流体设计) [hypothesis: https://gadgets.beebom.com/news/coloros-17-is-official-with-new-fluid-design-powerful-ai-features-more].
- **"Liquid Acrylic" — no official confirmation found**; it is not an OPPO name. "Liquid Glass" is the name of
  Apple's design; the press uses it only for comparison; according to press reports, ColorOS representatives denied
  copying Liquid Glass [hypothesis: https://www.notebookcheck.net/ColorOS-17-design-elements-spotted-in-ColorOS-16-0-8.1346964.0.html,
  https://gadgets.beebom.com/stories/i-went-hands-on-with-coloros-17-its-design-won-me-over].
- **Naming conclusion:** for ColorOS 17 the official name is "流体设计 / Living Flow Design"; the "glass/light"
  visual layer itself is officially **凝光视效 / Luminous Effect**. In project documents write: "a style in the spirit of ColorOS 17
  (Living Flow Design, Luminous Effect)", without logos or trademarks.

### 1.2 ColorOS 16 — "Luminous Design" / "光场设计"

- The ColorOS 16 page on oppo.com calls the visual section **Luminous Design** (subsection "Light Shadow Icons":
  natural light and shadow, "subtle glow outlines" on icons; particles and halos in animations); the engine is the
  **Luminous Rendering Engine** (unified rendering architecture) [from official documentation https://www.oppo.com/en/coloros16/].
- Official English press release: "refreshed design inspired by the interplay of light and shadow"
  [from official documentation https://www.oppo.com/en/newsroom/press/coloros-16-upcoming-global-unveil/].
- Chinese press (IT之家) — "全新光场设计", English-language press — "Light Field Design" [hypothesis:
  https://www.ithome.com/0/889/567.htm, https://gadgets.beebom.com/news/oppo-coloros-16-unveiled-in-china].
  I did not find the wording "Light Field" on oppo.com.
- According to the press, the full "glass" effect appeared in late 16.0.x builds and was developed further in 17 [hypothesis:
  https://www.androidauthority.com/p-3658552/].

### 1.3 Official guidelines for third-party developers

- **Not found.** Searching open.oppomobile.com / developers.oppomobile.com yielded only services (push, publishing,
  games); there are no pages with a ColorOS design specification or a public COUI library for third-party apps
  [hypothesis: search 2026-11-25; page https://open.oppomobile.com/new/developmentDoc/info?id=10426 has no content;
  https://developers.oppomobile.com].
- COUI is the internal UI framework of OPPO system apps; no public SDK/license for third-party apps
  was found [hypothesis]. The only "COUI" artifacts on Maven Central are third-party projects (io.github.itoseo:coui)
  [from official documentation https://central.sonatype.com/artifact/io.github.itoseo/coui].
- Consequence: there are no exact official numbers (radii, durations, curves, blur parameters). All numbers below are
  either from third-party reverse measurements or our own decisions.

### 1.4 Style properties (summary)

| Property | What is known | Tag |
| --- | --- | --- |
| Translucent "frosted" surfaces | "softer translucency, layered lighting and carefully crafted textures" | [from official documentation https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/] |
| Blur under surfaces | The word blur is not used officially; press and leaks: "质感模糊" (textured blur), frosted glass in the notification shade, side panel, capsules | [hypothesis: https://www.ithome.com/0/964/767.htm, beebom] |
| Light edge (rim) | Leaks: "光感描边" (light outline); in ColorOS 16 — "glow outlines" on icons | [hypothesis] for 17; [from official documentation https://www.oppo.com/en/coloros16/] for 16 icons |
| Light following the finger | "touch-following glow", "flowing light" in the progress bar; "光随指弥散", ripple on track change | [from official documentation oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/] |
| Floating bottom navigation | Press: floating "pill" menu at the bottom in system apps (Files, Phone, Calendar, Notes) | [hypothesis: https://gadgets.beebom.com/stories/i-went-hands-on-with-coloros-17-its-design-won-me-over] |
| Morphing/merging | "Interface elements move, merge and transform smoothly"; the clock moves AOD→lock screen→home screen, changing size and position | [from official documentation oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/] |
| Elastic feedback | "Controls react with subtle elasticity and rebound"; animations depend on gesture velocity; "轻点细腻收缩、拖拽自然拉伸、松手弹性回位" | [from official documentation, same source] |
| Large radii, "smoothed" corners | No official numbers; Iris and COUI-Kit use squircle corners (corner smoothing ≈0.5) | [verified in code/docs: Iris `IrisShapes.kt`] / [hypothesis] about the system |
| Font | OPPO Sans — OPPO's brand font (iF award), updated in ColorOS 13; no license for embedding in third-party apps → we use the system font (`FontFamily.Default`) | [from official documentation https://oppo.com/en/newsroom/stories/introducing-aquamorphic-design-of-coloros13] + [hypothesis] about the license |
| Motion durations/curves | **Not officially published.** Third-party measurements: Iris — springs (dampingRatio 0.55–1.0, stiffness 160–1366), touch-glow appear 110 ms / fade 480 ms; COUI-Kit — `CubicBezierEasing(0.3,0,0.1,1)` for dialog and radio entry, `(0.33,0,0.67,1)` for the title | [verified in code/docs: Iris `IrisMotion.kt`, `Glow.kt`, `DragPhysics.kt`; COUI-Kit `CouiDialogDefaults.kt`, `RadioButton.kt`] |
| Glass parameters (third-party reverse) | Iris: blur sigma (px at 480 dpi) — bottom bar 25, top bar 20, popup 26.7, dialog 33.3, bottom sheet 66.7; layers with blend mode Overlay/Luminosity/ColorDodge instead of a flat tint; progressive blur under the top bar | [verified in code/docs: Iris `docs/glass-and-blur.md`, obtained by fitting to screenshots `tools/analysis/*.java`] |

---

## 2. Library evaluation

### 2.1 Iris UI Kit — github.com/Markus-Q/ColorOS-17-Jetpack-Compose

Sources: `.tooling/research/ColorOS-17-Jetpack-Compose` (archive downloaded via `gh api …/tarball`:
`git clone` into `.tooling/` is blocked by worktree isolation; content is the same as `--depth 1` at HEAD 15bcecb9).
Scripts and gradle were not run.

| Criterion | Value | Tag |
| --- | --- | --- |
| License | Apache-2.0 (+NOTICE) | [verified in code/docs] |
| Activity | created 2026-11-22, last commit 2026-11-23, 26 commits, 1 author, 1 star, 0 forks; version 0.1.1-alpha | [verified in code/docs: `gh api repos/Markus-Q/ColorOS-17-Jetpack-Compose`] |
| Publishing | not published to Maven; the suggested approach is to copy the `iris-*` modules into the project | [verified in code/docs: `docs/getting-started.md`] |
| minSdk / compileSdk | 26 / 37 (blur from API 31) | [verified in code/docs: `build.gradle.kts`] |
| Versions | AGP 9.3.2, Kotlin 2.4.21, Compose BOM 2026.09.00 (same as ours); **Material3 not used** (only ui/foundation/animation) | [verified in code/docs: `gradle/libs.versions.toml`] |
| Blur | own implementation: `GraphicsLayer.record` copies the background into a layer + `BlurEffect` (RenderEffect, API 31+, TileMode.Decal) + blend-mode layers; below API 31 — a flat color "as the material over a fallback background" | [verified in code/docs: `iris-effects/.../Backdrop.kt`, `docs/glass-and-blur.md`] |
| Size/quality | ~9.8k lines of Kotlin, 55 files, good KDoc documentation, API reference, GIF recordings; **no tests** | [verified in code/docs] |
| Performance | the background is redrawn into a separate layer on every change; progressive blur = 5 stacked blur layers; no benchmarks | [verified in code/docs] / [hypothesis] about cost |
| Proprietary | no OPPO fonts/icons (stated and confirmed by searching for .ttf/.otf); **but** `Haptics.kt` uses undocumented ColorOS constants for `performHapticFeedback` (300–306…) and hidden HAL effect ids on Oplus devices | [verified in code/docs: `iris-effects/.../Haptics.kt`] |
| Origin of numbers | fitting to screenshots and recordings of a real phone (Java utilities in `tools/analysis`, touch injector `tools/inject`); no decompilation found | [verified in code/docs] |

Conclusion: a tidy but three-day-old single-author project with no tests and no release channel. Valuable as a **reference
for numbers and recipes** (glass materials, springs, squircle shape), not as a dependency.

### 2.2 COUI-Kit — github.com/ItosEO/COUI-Kit

Sources: `.tooling/research/COUI-Kit` (archive of HEAD 5c75d0a7). Scripts and gradle were not run.
Forks/analogues were also found (suqi8/coui, Costben/coui17, TIANLI0/COUI17); the "main" one by stars is ItosEO.

| Criterion | Value | Tag |
| --- | --- | --- |
| License | **MPL-2.0** (file-level copyleft) + a separate name policy `TRADEMARKS.md`; some files are BSD (Skia) and Apache-2.0 (AndroidX) | [verified in code/docs: `LICENSE`, `NOTICE`] |
| Activity | created 2026-10-23, ~130 commits, 4 contributors, 177 stars, 15 forks; daily commits; version 0.0.1-alpha20, "binary compatibility is not guaranteed" | [verified in code/docs: gh api, `docs/guide/compatibility.md`] |
| Publishing | Maven Central `io.github.itoseo:coui`, `coui-material3`, `coui-icons` | [verified in code/docs: README] |
| **minSdk** | **31**; `RuntimeShader` effects (progressive blur, rim light, merging) — API 33+; floating `NavigationBar`, FAB, `FloatingToolbar`, `SegmentedControl` — **API 34+** (`@RequiresApi(34)`) | [verified in code/docs: `coui/build.gradle.kts`, `docs/guide/compatibility.md`] |
| Versions | AGP 9.4.1, Kotlin 2.4.20, Compose **1.13.0** (newer than our BOM 2026.09.00), optional Material3 1.5.0, Navigation3 1.2.1 (api dependency of the core), DynamicAnimation 1.1.0 | [verified in code/docs: `gradle/libs.versions.toml`, `coui/build.gradle.kts`] |
| Blur | own: RenderEffect (API 31+) + AGSL `RuntimeShader` (33+), Fast Kawase for menus on 33+, Gaussian below; own Skia-based shadow tessellator | [verified in code/docs: grep `RuntimeShader`/`RenderEffect` in `coui/src`, `NOTICE`] |
| Size/quality | ~24.9k lines of Kotlin in the core (145 files), detailed documentation (Chinese), CI; **no unit/instrumented tests** | [verified in code/docs] |
| Performance | claims only public Android/AndroidX API; shader costs not measured; renderer creation may fail — the host must have a fallback UI | [verified in code/docs: README, compatibility.md] |
| Proprietary | no fonts; **`coui-icons` (133 files) — per CHANGELOG "保留原厂日夜配色与透明度" (factory day/night colors and transparency preserved), i.e. the icons apparently replicate ColorOS icons** → risk of third-party resources | [verified in code/docs: `CHANGELOG.md`] + [hypothesis] about origin |

Conclusion: the most mature one found, but **incompatible with our minSdk 28** (the core requires 31, the style-defining
components require 34), pulls in Navigation3 and a newer Compose, alpha with no API guarantees, MPL-2.0 requires keeping
modified files under MPL (inconvenient for an Apache-2.0 repository when copying code). Do not take the icons.

### 2.3 Haze — github.com/chrisbanes/haze

Sources: `.tooling/research/haze` (archive of HEAD ae467951).

| Criterion | Value | Tag |
| --- | --- | --- |
| License | Apache-2.0 | [verified in code/docs] |
| Activity | ~1568 commits, 29+ contributors, ~2.7k stars; latest release **2.1.0 (2026-11-02)**, 2.0.0 — 2026-09-22 | [verified in code/docs: gh api] |
| minSdk | libraries — **API 23** | [from official documentation docs/blur/platforms.md in the repository, https://chrisbanes.github.io/haze/] |
| Versions | Compose 1.12.1 / CMP 1.12.2, Kotlin 2.4.21; Material3 only in the optional `haze-*-material3` | [verified in code/docs: `gradle/libs.versions.toml`] |
| Modules | `haze` (core), `haze-blur`, `haze-materials`, `haze-glass` (refraction, rim/Fresnel, specular, interactive light, shape-merging containers), `*-material3` | [verified in code/docs] |
| Behavior by API | 33+: everything; 31–32: blur available, progressive blur approximated, extra redraws (API 31 — re-capture on any snapshot state change, cost not measured); **≤30: blur disabled, a translucent scrim is drawn** (optionally experimental RenderScript) | [from official documentation docs/blur/platforms.md] |
| Glass fallback | on 31–32 — blur + approximated tint; otherwise scrim; refraction/dispersion are dropped; `reduceTransparency` with an opaque fallback is available | [from official documentation docs/effects/glass/index.md] |
| Performance | Macrobenchmark and published results exist, `HazePerformanceMode` modes (Quality/Balanced…), `inputSharing` | [verified in code/docs: `docs/performance.md`, `docs/benchmark-results.md`] |
| Proprietary | none | [verified in code/docs] |

Conclusion: mature, tested (screenshot tests, benchmarks), minimum requirements below our minSdk,
predictable fallback. Suitable as the basis for blur/glass. Risk: major version 2.x is fresh; `haze-glass` provides
"Apple-like" refraction, which ColorOS 17 reportedly toned down — use sparingly or only `haze-blur`.

Platform reference: `RenderEffect.createBlurEffect` is available from API 31
[from official documentation https://developer.android.com/reference/android/graphics/RenderEffect],
`RuntimeShader` (AGSL) — from API 33 [from official documentation https://developer.android.com/reference/android/graphics/RuntimeShader].

---

## 3. Recommendation for the ADR

**Write our own thin design layer on top of Compose foundation (+ Material3 where convenient), blur via Haze;
Iris and COUI-Kit only as references, with no dependency and no copying of resources.**

1. **COUI-Kit — do not take** as a dependency: minSdk 31 (>28), style-defining components on API 34+, alpha with no
   API compatibility, extra transitive dependencies (Navigation3, Compose 1.13), MPL-2.0, icons of likely
   factory origin. Component behavior can be studied in its documentation [verified in code/docs].
2. **Iris — take as a reference**: glass material numbers, springs, squircle shapes, touch-glow and press-scale techniques.
   If we port a code fragment (e.g. `SmoothRoundedCornerShape`) — keep the Apache-2.0 header and NOTICE.
   Do not port `Haptics.kt` with Oplus constants (violates vendor neutrality): only public
   `HapticFeedbackConstants` [verified in code/docs].
3. **Haze (`dev.chrisbanes.haze:haze` + `haze-blur`, version 2.1.0) — take** as the blur basis for the floating bottom
   bar, top bar and sheets. On API 28–30 — a deliberate fallback: opaque/translucent surface
   with a thin light edge, no RenderScript. `haze-glass` — optional, after on-device verification.
4. **Own design layer** (`ui/theme`): color tokens (light/dark, dynamic color optionally), radii
   (cards ~24–28 dp, bars — pill; [hypothesis], tune against reference screenshots in `.data/design-refs/`),
   light edge = `border` 0.5–1 dp with a vertical low-alpha white gradient, Iris-style springs,
   press-scale 0.96–0.98, system font. Morphing — `SharedTransitionLayout`/`animateContentSize` from Compose.
5. Account for product scope: the app has 2–4 screens (today, history, settings), so a "full UI kit"
   is not needed; the amount of our own code is expected to be small [hypothesis].
6. On-device verification (Oppo, Android 16/17): profile frames with blur under a scrolling list; on
   frame drops — Haze `Balanced` or disable blur [hypothesis until measured].

## 4. Sources

- OPPO, ColorOS 17 (EN): https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/
- OPPO, ColorOS 17 (CN, press release): https://www.oppo.com/cn/newsroom/press/549/
- OPPO, ColorOS 16: https://www.oppo.com/en/coloros16/
- OPPO, ColorOS 16 announcement: https://www.oppo.com/en/newsroom/press/coloros-16-upcoming-global-unveil/
- OPPO, Aquamorphic Design / OPPO Sans (ColorOS 13): https://oppo.com/en/newsroom/stories/introducing-aquamorphic-design-of-coloros13
- OPPO Developers: https://developers.oppomobile.com , https://open.oppomobile.com
- Press: https://gadgets.beebom.com/news/coloros-17-is-official-with-new-fluid-design-powerful-ai-features-more ,
  https://gadgets.beebom.com/stories/i-went-hands-on-with-coloros-17-its-design-won-me-over ,
  https://tech.ifeng.com/c/8wURrl36Zda , https://www.ithome.com/0/964/767.htm , https://www.ithome.com/0/889/567.htm ,
  https://designcompass.org/en/2026/09/28/coloros-17-fluid-motion-design-system/ ,
  https://www.notebookcheck.net/ColorOS-17-design-elements-spotted-in-ColorOS-16-0-8.1346964.0.html ,
  https://www.androidauthority.com/p-3658552/ , https://gadgets.beebom.com/news/oppo-coloros-16-unveiled-in-china
- Libraries: https://github.com/Markus-Q/ColorOS-17-Jetpack-Compose , https://github.com/ItosEO/COUI-Kit ,
  https://central.sonatype.com/artifact/io.github.itoseo/coui , https://github.com/chrisbanes/haze ,
  https://chrisbanes.github.io/haze/
- Android: https://developer.android.com/reference/android/graphics/RenderEffect ,
  https://developer.android.com/reference/android/graphics/RuntimeShader
