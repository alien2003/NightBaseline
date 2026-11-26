# Research: ColorOS 17 "glass" compared with Apple Liquid Glass, and what NightBaseline adopts

Date: 2026-11-26. Purpose: settle the owner's observation that ColorOS 17 looks very much like Apple's Liquid Glass
(iOS 26 / macOS 26), and turn both references into concrete, own-implementation rules for NightBaseline's Compose
shell. Complements `docs/research/design-coloros17.md` (names, libraries) and `docs/design/reference-analysis.md`
(measurements on the owner's ColorOS 17 phone). ADR 0003 and `docs/design/tokens.md` section 7 are the current
decisions this document checks.

Evidence tags:
- **[official &lt;url&gt;]**: OPPO's own pages and launch copy (oppo.com), Apple's Newsroom, HIG, developer
  documentation or WWDC session pages, or a library's own documentation.
- **[press &lt;url&gt;]**: third-party outlets, including outlets quoting OPPO staff.
- **[measured]**: our own measurement, `docs/design/reference-analysis.md` (section given).
- **[verified in code/docs]**: read in the Haze sources or docs (local research copy) or in our repository.
- **[hypothesis]**: our inference or decision.

Boundaries: the canon is ColorOS 17 (CLAUDE.md); Apple is only a technical comparison where OPPO documents nothing.
No vendor images were downloaded or committed; no phone access was used. Chinese source quotes are given here as
English translations only (English-only rule); the original wording is at the cited URL.

---

## 1. What ColorOS 17's "glass" officially is

### 1.1 Names

| Item | Official wording | Tag |
|---|---|---|
| Design language | "Living Flow Design is built around three core ideas: Luminous Effect, Fluid Motion and Flexible Feedback" | [official https://www.oppo.com/en/newsroom/press/oppo-color-os-17/] (re-checked 2026-11-26) |
| Chinese name | "fluid design", an "all-senses design language born for smoothness" that goes "deep into the system rendering layer" | [official https://www.oppo.com/cn/newsroom/press/549/] |
| The material layer | **Luminous Effect** (Chinese name translates roughly as "condensed-light visual effect") | [official same URLs] |
| Rendering base | "Luminous Rendering Engine", "Unified Rendering Architecture" (rendering load down by up to 30 %) | [official https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/] |
| Page heading | "More Translucent. Inside and Out." | [official https://www.oppo.com/en/coloros17/] |
| Predecessor | ColorOS 16 "Luminous Design", "Light Shadow Icons" with "subtle glow outlines" | [official https://www.oppo.com/en/coloros16/] |

**OPPO never uses the words glass, blur, frosted, refraction, specular or Liquid Glass in official ColorOS 17
text.** The EN press release was re-read on 2026-11-26 and contains none of them; it uses "translucent" and
"lighting" [official https://www.oppo.com/en/newsroom/press/oppo-color-os-17/]. "Glass", "frosted", "progressive
blur", "floating pill" are press vocabulary. No developer design guidelines for ColorOS 17 exist publicly
[hypothesis: searches 2026-11-25/26; see design-coloros17.md section 1.3].

### 1.2 Official properties of the material (Luminous Effect)

- "softer, more translucent visuals, creating a refined sense of depth and hierarchy, while subtle lighting naturally
  guides attention and responds to touch" [official https://www.oppo.com/en/newsroom/press/oppo-color-os-17/]
- "softer translucency, layered lighting and carefully crafted textures"; "a lighter, more natural sense of depth
  while maintaining content clarity" [official https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/]
- "Subtle diffusion, ripple-like effects and dynamic lighting respond to touch and adapt to different contexts";
  "A touch-following glow and flowing light effects make the interaction feel more connected to the user's action"
  [official same story]
- Light has a job: "light is no longer just decoration, it guides the interface's intent", with three named
  behaviours: light chasing along a playing progress bar, light spreading from the finger while sliding, a ripple
  when the track changes [official, launch copy republished: https://i.ifeng.com/c/8wUTuZWFedT]
- Music progress bar: "pulsing light effect … every track switch and every tap met with a gentle glow"
  [official https://www.oppo.com/en/coloros17/]
- The system senses "the pressure and direction of your touch … responding with fluid, elastic motion and nuanced
  lighting effects" [official https://www.oppo.com/en/coloros17/]

### 1.3 Official motion and feedback (Fluid Motion, Flexible Feedback)

- "interface elements … move, merge and transform seamlessly from one state to the next"
  [official https://www.oppo.com/en/newsroom/press/oppo-color-os-17/]
- "Buttons, toolbars and other interactive elements visibly compress, stretch and rebound in response to taps"
  [official same]; "a light tap gives a delicate shrink, a drag stretches naturally, release springs back"; search
  bar and sidebar get unified tactile feedback [official, republished: https://i.ifeng.com/c/8wUTuZWFedT]
- "animations adjust their timing to the speed and intensity of each gesture"
  [official https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/]

### 1.4 Where it is used (official vs press)

- Official text names: buttons, toolbars, "bars", progress and seek bars, notification panels, Notification Center,
  Control Center, navigation, search bar, sidebar, the clock across AOD / lock screen / home screen, Fluid Cloud,
  app transitions, alarms, calls, OPPO Share [official URLs in 1.2 and 1.3].
- Not named officially: dock, dialogs, sheets, sliders, switches, icons for 17.
- Press, consistent across outlets: transparent **floating bottom bar** with capsule buttons in system apps (Files,
  Phone, Calendar, Notes, Photos, theme store) [press https://gadgets.beebom.com/stories/i-went-hands-on-with-coloros-17-its-design-won-me-over,
  https://www.smartprix.com/bytes/coloros-17-review/, https://www.ithinkdiff.com/coloros-17-copies-the-liquid-glass-look-oppo-promised-to-skip/];
  **progressive blur** "across nearly every system app" [press https://www.smartprix.com/bytes/coloros-17-first-look-heres-everything-new/];
  surfaces with "a sharp highlight at the top, a soft reflection at the bottom, and a subtle shadow at the base";
  coloured buttons glossy, solid buttons flat [press https://www.smartprix.com/bytes/coloros-17-review/].

### 1.5 OPPO's own position on Liquid Glass and readability (staff statements, via press)

- Before launch the ColorOS design director said the changes in 17 "really won't be big", while "some rivals are
  following Liquid Glass very aggressively" [press https://www.ithome.com/0/962/934.htm,
  https://gadgets.beebom.com/news/your-oppo-oneplus-and-realme-phones-are-not-getting-liquid-glass-ui].
- On transparent cards: "overall we don't favour transparent card designs; they hurt card readability a lot … we
  would only try it on very, very few non-reading cards" [press https://www.ithome.com/0/978/638.htm].
- Teaser: the system material was "fully upgraded", keeping "maximum translucency while improving how clearly
  information reads and reducing unnecessary visual noise" [press https://tech.ifeng.com/c/8vtvBAADA3L].
- Engineers, as reported by sspai: the base material is still **blur plus highlight**, acrylic-like, for readability
  in light and dark; light intensity and blur depend on a new notion of "mass"; light suggests an object's cast
  shadow and anchors the fluid visuals [press https://sspai.com/post/114728].

### 1.6 What reviewers see on devices

- sspai in-depth review: the dock / bottom bar is the most glass-like spot (Gaussian blur plus slight distortion
  underneath, strongest highlight); first-party primary buttons and tabs are "light-led" with a gradient shadow;
  dialer and calculator keys spill a live highlight onto neighbours; overall "between acrylic and glass", weak
  refraction, far from real glass; dark mode turns light into heavy grey-black blocks; on pure white the light is
  hard to see [press https://sspai.com/post/114728].
- Find X10 reviews call it "a visual design similar to Apple's Liquid Glass" and "the whole Liquid Glass clone
  trend"; transparency can be toned down in settings [press https://gadgets.beebom.com/reviews/oppo-find-x10-review,
  https://9to5google.com/2026/10/21/oppo-find-x10-pro-max-review].
- Our own phone (dark theme, outer screen): top bar has no background and blurs content progressively (sigma about
  0.8 dp near the bar bottom, about 4.5 dp under the status bar) with darkening; bar, back button and keys look
  opaque over black: vertical gradient, 1 px lighter top rim, 2–3 px bottom rim, soft shadow about 4–5 dp; cards
  solid #1A1A1A, no blur [measured reference-analysis sections 2, 4, 5, 7].

---

## 2. Apple Liquid Glass: official properties

| Property | Apple's wording | Tag |
|---|---|---|
| Optics | "This translucent material reflects and refracts its surroundings" | [official https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/] (re-checked 2026-11-26) |
| Lensing as the defining trait | "Where as previous materials scattered light, this new set of materials dynamically bends, shapes, and concentrates light in real time" | [official https://developer.apple.com/videos/play/wwdc2025/219/] |
| Layers | blur, luminosity adjustment, lensing, highlights, shadows; "each layer continuously adapts based on what's behind it" | [official WWDC25-219; https://developer.apple.com/design/human-interface-guidelines/materials] |
| Size-dependent thickness | larger glass: deeper shadows, more pronounced lensing, softer scattering | [official WWDC25-219] |
| Specular highlights | "dynamically reacts to movement with specular highlights"; lights travel around the silhouette on events, sometimes with device motion | [official Newsroom; WWDC25-219] |
| Touch | "flexing and energizing with light"; glow starts under the fingertip and spreads; `.interactive()` scales, bounces, shimmers | [official WWDC25-219; https://developer.apple.com/videos/play/wwdc2025/323/] |
| Tint | no inherent colour; small elements flip light/dark with the backdrop, large ones do not; labels monochrome and flip with the glass | [official https://developer.apple.com/design/human-interface-guidelines/color; WWDC25-219] |
| Variants | Regular (default, any content) and Clear (only over media, with 35 % dimming over bright content); never mixed | [official HIG Materials; WWDC25-219] |
| Placement | a functional layer for navigation and controls only; "Don't use Liquid Glass in the content layer"; no glass on glass | [official HIG Materials; WWDC25-219] |
| Shapes | fixed, capsule (r = h/2), concentric (inner r = outer r − padding); controls "concentric with the rounded corners of modern hardware and app windows" | [official https://developer.apple.com/videos/play/wwdc2025/356/; Newsroom] |
| Tab bar | floats; "when users scroll, tab bars shrink to bring focus to the content" (opt-in minimise) | [official Newsroom; https://developer.apple.com/design/human-interface-guidelines/tab-bars] |
| Scroll edge effect | "a subtle blur and fade effect applied to content under system toolbars", replaces dividers and bar backgrounds; soft and hard styles | [official WWDC25-323; https://developer.apple.com/design/human-interface-guidelines/scroll-views] |
| Adaptive shadow | stronger shadow over text, weaker over a light solid background | [official WWDC25-219] |
| Accessibility | Reduce Transparency (frostier), Increase Contrast (black/white plus border), Reduce Motion (no elastic properties) applied automatically; user "Clear / Tinted" choice since iOS 26.1 | [official WWDC25-219; HIG Materials] [press https://techcrunch.com/2025/11/04/ios-26-1-lets-you-turn-down-liquid-glass-transparency] |
| Numbers | none for blur, refraction, highlight or springs; only 35 % dimming, 4.5:1 / 3:1 contrast, 44 pt targets | [official HIG Materials, https://developer.apple.com/design/human-interface-guidelines/accessibility] |

---

## 3. Side by side

| Property | ColorOS 17 | Liquid Glass | Evidence |
|---|---|---|---|
| Official name of the material | "Luminous Effect" in "Living Flow Design"; never "glass" | "Liquid Glass" | [official OPPO EN press; Apple Newsroom] |
| Translucency | "softer, more translucent visuals" | translucent | [official both] |
| Backdrop blur | not stated; press and our phone: yes, progressive under the top bar | yes ("blurs content behind it") | [press smartprix] [measured §7] [official https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views] |
| Refraction / lensing | not stated; reviewers: weak, slight distortion only under the dock | the defining feature | [press sspai] [official WWDC25-219] |
| Reflection of surroundings | not stated | stated | [official Apple Newsroom] |
| Specular / rim light | "layered lighting"; press: sharp top highlight, soft bottom reflection; phone: 1 px top rim, 2–3 px bottom rim | motion-driven specular highlights on the silhouette | [official OPPO story] [press smartprix] [measured §4.1] [official Newsroom] |
| Touch-following light | official: "touch-following glow", light spreads from the finger, ripples | official: glow starts under the fingertip and spreads | [official OPPO story; WWDC25-219] |
| Light with meaning | official: progress-bar light chase, ripple on change, "guides the interface's intent" | not a stated principle | [official ifeng launch copy] |
| Elastic feedback | official: compress, stretch, rebound; velocity- and pressure-dependent | official: flex, bounce, gel-like | [official OPPO press; WWDC25-219/323] |
| Morphing / merging | official: "move, merge and transform" | official: morph, merge in containers | [official both] |
| Adaptive light/dark tint | not stated; dark theme reported weak | small elements flip with backdrop | [press sspai] [official HIG Color] |
| Floating bottom bar | press: yes, capsule; phone: 56 dp pill, 16 dp from bottom | official: floating tab bar | [press beebom, smartprix] [measured §4.1] [official HIG Tab bars] |
| Bar minimises on scroll | no evidence | official (opt-in) | [hypothesis: absent in all sources read] [official Newsroom] |
| Concentric radii | not stated; press: "G2 continuity" widget corners; radius mismatches while floating windows resize | official principle | [press smartprix, sspai] [official WWDC25-356] |
| Content cards | staff: not transparent, readability first; phone: solid | "Don't use Liquid Glass in the content layer" | [press ithome 978638] [measured §1] [official HIG Materials] |
| User transparency control | yes ("tone down the transparency") | yes (Reduce Transparency; Clear / Tinted) | [press 9to5google] [official WWDC25-219; press techcrunch] |

**How close they really are** [hypothesis, from the table]: in layout grammar they are almost the same (floating
capsule bar, chrome-only translucency, solid content, progressive blur under the top bar, touch glow, elastic
morphing), which is why reviewers say "Liquid Glass clone". In optics they differ: Liquid Glass is a lensing
material (refraction, reflection, motion specular, adaptive light/dark flip); ColorOS 17 is a blur-plus-tint
"acrylic" with static highlights and only faint distortion, tuned for readability, and adds functional light
(progress chase, ripples) and velocity/pressure-aware springs. On our dark-theme phone the shell reads close to
Apple's Reduce Transparency / Tinted look, not to Clear glass. So the right target for NightBaseline is
**ColorOS acrylic + light**, borrowing from Apple only rules OPPO leaves unstated (placement, concentric radii,
scroll edge behaviour, accessibility mapping).

---

## 4. Recommendations for NightBaseline's Compose implementation

All of this is our own code on Compose foundation plus Haze blur; no vendor assets, fonts, icons, sounds or haptic
constants. The current implementation (`ui/material/ShellMaterial.kt`: `haze-blur` with token tint, noise, 1 dp
gradient rim plus 0.5 dp outer hairline, shadow; solid fallback with identical geometry) already matches most of
this [verified in code/docs]. Items marked **change** are new.

### 4.1 Material recipe (one recipe, per-component numbers)

| Layer | Decision | Why |
|---|---|---|
| Blur | Keep `haze-blur` (2.1.0) with the radii of tokens.md 7.1: bar/rail 24 dp, top bar progressive 0 -> 20 dp, sheet 40 dp, dialog 32 dp | ColorOS base is blur [press sspai]; matches measured progressive blur [measured §7] |
| Tint | Keep a static per-theme tint (78–90 % alpha), strong enough for 4.5:1 over worst-case white and black backdrops | OPPO: readability first [press ithome 978638]; Apple's adaptive flip is unnecessary because our content is solid and theme-coloured [hypothesis] |
| Adaptive light/dark | **No** backdrop luminance sampling; light and dark come from the app theme only. Tune the dark variant separately (dark theme is ColorOS's reported weak spot) | [press sspai] [official HIG Color for the Apple alternative] |
| Rim / highlight | Keep the static rim: brighter top, faint bottom, 0.5 dp outer hairline. **Change**: make the bottom reflection a 2 dp inner band (phone shows 2–3 px bottom rim) rather than only the 1 dp stroke | [measured §4.1] [press smartprix "sharp highlight at the top, a soft reflection at the bottom"] |
| Specular motion | **No** gyro- or scroll-driven specular | not in ColorOS official text; NightBaseline is a passive once-a-day app [hypothesis] |
| Refraction / lensing | **None** in 0.1.0 (see 4.3) | ColorOS refraction is weak [press sspai]; Apple-defining, not canon (CLAUDE.md) |
| Noise | Keep 3 % Haze noise | grain seen on the system's translucent surfaces [measured §7] |
| Shadow | Keep `elevation.shell` for bar/rail. Optional **change**: raise bar shadow alpha by about 30 % while content is under the bar | [measured §4.1 shadow ≈ 4 dp] [official WWDC25-219 adaptive shadow] |

### 4.2 Per shell component

| Component | Material | Shape | Interaction | Evidence |
|---|---|---|---|---|
| Floating bottom bar (compact) | glass bar | capsule, r = h/2 (56 dp tall, 16 dp above the bottom) | item press: shrink plus touch-following glow (4.4); selection by filled icon and bold label, no indicator pill | [measured §4.1] [press beebom] |
| Navigation rail (expanded) | glass bar | capsule, width follows items | as bar | [hypothesis: no rail reference captured, measured §10] |
| Top bar | none at rest; progressive blur plus darkening once content scrolls under (scroll edge effect); no divider in glass mode | n/a | n/a | [measured §2, §7] [official WWDC25-323 "remove extra backgrounds"] |
| Back / trailing buttons | **solid convex** surface, not blur: vertical gradient plus rim plus soft shadow, 40 dp circle | circle | press shrink plus glow | [measured §2: buttons are the only opaque elements of the bar] |
| Bottom sheet | glass sheet; contents on top use fills, never a second glass layer | top corners 28 dp; inner items concentric (r = 28 − inset), not below 12 dp | drag with spring, velocity-aware settle | [official WWDC25-219 "no glass on glass"] [hypothesis: radius] |
| Dialog | glass dialog over a dim scrim | 28 dp; buttons inside concentric | appear by scale 0.96 -> 1 plus fade on a spring; no blur-in animation under reduced motion | [official WWDC25-356 modal dimming; HIG Accessibility] |
| Period selector (7/30/90) | content layer: **solid** track (`surface.2`); selected thumb a solid convex chip with rim | capsule track; thumb r = track r − inset | thumb slides on a spring and stretches slightly toward the target during the move (stretch/squish), touch glow on press | [press smartprix: menus "stretch, expand, and squish as the selection changes"] [official OPPO "a drag stretches naturally"] [official HIG Materials: content layer stays standard] |
| Primary button | accent gradient fill plus accent glow below (as measured FAB), plus a subtle top gloss band | capsule | shrink plus touch glow; label contrast per tokens (white on accent fails 4.5:1) | [measured §4.3, §6] [press smartprix "coloured buttons are glossy"] |
| Cards, charts, tooltip, rows | **always solid**, no blur, no translucency | fixed 20 dp | none beyond standard press | [press ithome 978638] [official HIG Materials] [measured §1] |

### 4.3 Haze `haze-glass` 2.1.0: decision

What it offers [official https://chrisbanes.github.io/haze/; verified in code/docs, local copy]: refraction
(Edge/Surface profiles, displacement, fold), depth blur, tint, Fresnel/ambient lift (`ambientResponse`), specular
(`specularIntensity`, `lightPosition`), edge shadow, chromatic aberration, interaction lighting with pressed
`scale`/`lightingIntensity` and pointer-following light, merging containers, `GlassAccessibilitySettings`
(`reduceTransparency`, `increaseContrast`, `showBorders`).

Findings that decide it:
- Full Glass rendering needs API 33 (`RuntimeShader`; the factory is `@RequiresApi(TIRAMISU)`). In the **released
  2.1.0** docs the fallback table omits blur and refraction entirely; blurred fallback on Android 12/12L,
  `rimHighlight`, `edgeOutline` and `ContinuousRoundedCornerShape` exist only in the unreleased 2.2.0-SNAPSHOT
  changelog [verified in code/docs: CHANGELOG "Unreleased" vs "2.1.0"; official published docs page fetched
  2026-11-26].
- Built-in styles are "informed by iOS 27 Liquid Glass" and "tuned side by side with iOS" [verified in code/docs],
  i.e. Apple's look, not the canon.
- Containers cost: two merged surfaces left about 4.6 ms less P90 frame margin and used about 14 % more GPU memory
  on a Pixel 8a [official https://chrisbanes.github.io/haze/ performance guide, Glass section].

**Decision [hypothesis, to record in an ADR 0003 amendment]:** do **not** use `haze-glass` in 0.1.0. Keep
`haze-blur` for blur and draw rim, gloss, glow and press transforms ourselves (cheap `drawWithCache` gradients that
work identically on API 28–37 and in solid mode). Refraction, Fresnel lift, chromatic aberration and containers:
not used. Re-evaluate only after a Haze release ships `rimHighlight` and the API 31–32 blurred fallback, and only as
an experiment on the bottom bar on API 33+: `refractionStrength` ≤ 0.2, `RefractionProfile.Edge(8.dp)`,
`chromaticAberrationStrength` 0, `specularIntensity` ≤ 0.3, passing the same frame gate as tokens.md 7.4.

### 4.4 Light, motion, feedback

- **Touch-following glow** (official on both sides): radial gradient centred at the pointer, clipped to the element,
  `color.accentGlow`; appears in about 110 ms, follows the finger, fades over about 480 ms after release (Iris
  timings, design-coloros17.md). Decorative only, never behind text that must be read; not on cards.
- **Press shrink and rebound**: scale 0.96 (small controls) to 0.98 (bar, sheet handle), spring dampingRatio about
  0.6, medium stiffness; release velocity feeds the spring so faster gestures rebound with more energy
  [official OPPO "timing to the speed and intensity of each gesture"]. No growth above 1.0 (that is Apple's
  interactive glass) [official WWDC25-323].
- **Functional light** (ColorOS-specific): one place only, the period selector thumb: a short highlight sweep in the
  move direction when the period changes. No progress-bar chase (we have none) and no idle glows (principles.md).
- **Bar minimising on scroll**: **do not adopt**. No ColorOS evidence; Apple makes it opt-in; we have 2–3
  destinations [hypothesis].
- **Morphing**: tile -> metric detail shared bounds stays as planned (principles.md); no glass-shape merging.

### 4.5 Accessibility and fallback

- Keep the single resolver of tokens.md 7.3 (API < 31, animations off, battery saver, contrast, low RAM, jank,
  thermal, user switch). Solid mode keeps geometry, rim and gloss; only fills change.
- Rename the user setting's intent to match both references: "Glass effects: Automatic / Off" is the equivalent of
  ColorOS's transparency toggle [press 9to5google] and iOS Reduce Transparency [official WWDC25-219].
- **Change**: on higher contrast (`UiModeManager.getContrast() > 0`, API 34+) use solid surfaces **plus a 1 dp
  contrasting border** (`onGlass.primary` at 60 %) on shell surfaces [official WWDC25-219 Increase Contrast].
- Reduced motion (`ANIMATOR_DURATION_SCALE == 0`): no scale, no glow spread (static highlight fade only), critically
  damped springs, no animation into or out of blur [official HIG Accessibility].
- Contrast: 4.5:1 body, 3:1 large or bold, in both themes, over worst-case backdrops [official HIG Accessibility];
  already enforced by `tools/design/contrast_check.py`.

### 4.6 Performance budget

Keep tokens.md 7.4: at most three blurred regions (top bar, bar or rail, one sheet or dialog); zero overrun frames
on the owner's phone; blur at most 25 % of the frame budget. Add: glow and gloss drawn in `drawWithCache` without a
graphics layer per item; no Haze glass containers; at most one glow animating at a time [hypothesis until
measured; measurement needs the owner's permission].

---

## 5. Items for `docs/design/coloros-conformance.md`

(Listed only; another process owns that file.)

1. Wording: call the style "in the spirit of ColorOS 17 Living Flow Design / Luminous Effect"; never claim "glass",
   "Liquid Glass" or "refraction" as OPPO terms.
2. Glass only on shell (bar, rail, top bar under scroll, sheet, dialog); cards, charts, tooltip, period selector
   track stay solid (OPPO staff on transparent cards; measured solid cards).
3. Material = blur + static per-theme tint + noise + rim (bright top, 2 dp faint bottom band) + soft shadow; no
   refraction, no reflection, no motion specular.
4. `haze-glass` not used in 0.1.0; re-evaluation criteria as in 4.3 (record in an ADR 0003 amendment).
5. Top-bar back and trailing buttons are solid convex circles (gradient + rim + shadow), not blurred.
6. Touch-following glow on shell items, back button, period thumb and primary buttons (110 ms in, 480 ms out).
7. Press shrink 0.96–0.98 with velocity-aware spring rebound; never scale above 1.0.
8. Period selector thumb: spring slide with slight stretch toward the target plus a one-shot highlight sweep.
9. Primary button: accent gradient, accent glow below, top gloss band; contrast per tokens.
10. Concentric radii: capsule bars (r = h/2), inner shapes r = outer − inset (min 12 dp), sheet/dialog 28 dp,
    cards fixed 20 dp.
11. No bar minimising on scroll; no adaptive light/dark flip from backdrop sampling.
12. Higher contrast: solid shell plus 1 dp contrasting border.
13. Reduced motion: no scale, no glow spread, no blur transitions, critically damped springs.
14. Dark theme tuned and reviewed separately (ColorOS's reported weak spot); light-theme reference capture still
    missing (reference-analysis section 10).

## 6. Sources

- OPPO: https://www.oppo.com/en/newsroom/press/oppo-color-os-17/ , https://www.oppo.com/en/newsroom/stories/oppo-color-os-17-smooth/ ,
  https://www.oppo.com/en/coloros17/ , https://www.oppo.com/hk/coloros17/ , https://www.oppo.com/cn/newsroom/press/549/ ,
  https://i.ifeng.com/c/8wUTuZWFedT (launch copy, republished), https://www.oppo.com/en/coloros16/
- Press on ColorOS 17: https://sspai.com/post/114728 , https://www.smartprix.com/bytes/coloros-17-first-look-heres-everything-new/ ,
  https://www.smartprix.com/bytes/coloros-17-review/ , https://gadgets.beebom.com/stories/i-went-hands-on-with-coloros-17-its-design-won-me-over ,
  https://gadgets.beebom.com/reviews/oppo-find-x10-review , https://9to5google.com/2026/10/21/oppo-find-x10-pro-max-review ,
  https://www.ithinkdiff.com/coloros-17-copies-the-liquid-glass-look-oppo-promised-to-skip/ , https://www.ithome.com/0/962/934.htm ,
  https://www.ithome.com/0/978/638.htm , https://tech.ifeng.com/c/8vtvBAADA3L , https://ifanr.com/1680691 ,
  https://www.notebookcheck.net/ColorOS-17-design-elements-spotted-in-ColorOS-16-0-8.1346964.0.html
- Apple: https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/ ,
  https://developer.apple.com/design/human-interface-guidelines/materials , https://developer.apple.com/design/human-interface-guidelines/color ,
  https://developer.apple.com/design/human-interface-guidelines/tab-bars , https://developer.apple.com/design/human-interface-guidelines/scroll-views ,
  https://developer.apple.com/design/human-interface-guidelines/accessibility , https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views ,
  https://developer.apple.com/videos/play/wwdc2025/219/ , https://developer.apple.com/videos/play/wwdc2025/356/ ,
  https://developer.apple.com/videos/play/wwdc2025/323/ , https://techcrunch.com/2025/11/04/ios-26-1-lets-you-turn-down-liquid-glass-transparency
- Haze: https://chrisbanes.github.io/haze/ , https://github.com/chrisbanes/haze (CHANGELOG, docs/effects/glass,
  docs/performance.md)
