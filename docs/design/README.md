# Design documents

Stage 3 (design before code). These documents define NightBaseline's UI: an Android (Compose) app that reads nightly
measurements from Health Connect once a day, compares each with the person's own usual range and sends a calm note
when two or more moved outside it. Not a medical device. The look is in the spirit of ColorOS 17 (Living Flow
Design, Luminous Effect) with our own tokens and no vendor resources (ADR 0003).

## Reading order

| File | Owns | Read it for |
|---|---|---|
| [`principles.md`](principles.md) | the eight design principles and how each is checked | deciding a case the other files do not cover (first listed principle wins) |
| [`tokens.md`](tokens.md) | every value: colour (light/dark), contrast pairs, typography, spacing, shapes, elevation, materials (glass and solid), fallback rules, performance budget, iconography, motion, targets/focus/hover | the single source of truth for numbers; other files only name tokens |
| [`screens.md`](screens.md) | destinations, navigation graph, deep links, component inventory, status vocabulary (shape + label + colour), content and order of every screen, chart text summary, disclaimer placement, accessibility rules per component | what is on each screen and in which order |
| [`states.md`](states.md) | every data state (night, banner, metric, screen-specific), its trigger, cue, copy keys and English copy, the per-screen state matrix, the notification spec | what the person sees in each situation and the exact words |
| [`app/src/main/res/values-ru/strings.xml`](../../app/src/main/res/values-ru/strings.xml) | the Russian copy for every key in `states.md` (moved from the design draft into the app; English in `app/src/main/res/values/strings.xml`) | Russian wording; the only place with Cyrillic (ADR 0002) |
| [`layouts.md`](layouts.md) | layout modes and breakpoints, pane widths, navigation shell geometry, per-mode layouts of every screen, fold/hinge/posture rules, state preservation, the owner's device mapping, keyboard/mouse, screenshot-test device matrix | where things go in every window size and posture; it wins on geometry |
| [`reference-analysis.md`](reference-analysis.md) | measurements of the owner's system apps (outer screen, dark theme) | the calibration numbers behind `[measured]` tokens |
| [`review.md`](review.md) | review rounds: findings, fixes and the requirement checklist | what was checked and what is still open |

Research inputs (not design decisions): `docs/research/design-coloros17.md`, `docs/research/large-screens.md`,
`docs/research/analogs.md`. Decisions: `docs/adr/0002-project-language-and-history.md` (English only, Russian only in
`res/values-ru*`), `docs/adr/0003-ui-library-and-blur.md` (own design layer, Haze blur, fallback).

## Ownership rules

- Values live only in `tokens.md`; geometry of layouts only in `layouts.md`; copy only in `states.md` (English) and
  `app/src/main/res/values-ru/strings.xml` (Russian). Other files reference them by name.
- Every string key in `states.md` and `screens.md` must exist in `app/src/main/res/values/strings.xml` and
  `app/src/main/res/values-ru/strings.xml` and vice versa (checked by `StringResourcesTest`).
- No Cyrillic in any Markdown file (`tools/check-language.sh`).
- Reference screenshots, UI dumps and recordings of system apps stay in the git-ignored `.data/design-refs/`; the
  repository gets only measurements and patterns, never vendor images, icons, fonts or personal content.

## Automatic checks

| Check | Command | Status |
|---|---|---|
| Text >= 4.5:1, graphics and focus >= 3:1, both themes, glass over white and black backdrops | `python3 -I tools/design/contrast_check.py` | runs locally, passes; pre-commit/CI wiring open (`review.md`) |
| English-only repository | `tools/check-language.sh` (pre-commit hook) | in place |
| Screenshot matrix and layout assertions (clipping, fold intersection, 48 dp targets) | `layouts.md` 11, `screens.md` 10 | stage 4 |
| Frame timing on the owner's phone (glass and solid) | `tokens.md` 7.4 | stage 4, with the owner's permission |
