# ADR 0003: Own design layer on Compose, Haze for blur

- Status: accepted
- Date: 2026-11-25
- Evidence: `docs/research/design-coloros17.md`

## Context

The visual language follows ColorOS 17 ("Living Flow Design", "Luminous Effect"): frosted glass for the shell,
floating bottom navigation, large radii, spring motion. OPPO publishes no design guidelines or UI SDK for third-party
apps. Candidates: Iris UI Kit (Markus-Q/ColorOS-17-Jetpack-Compose), COUI-Kit (ItosEO/COUI-Kit), Haze
(chrisbanes/haze), or our own components. minSdk is 28.

## Options

| Option | For | Against |
|---|---|---|
| COUI-Kit as dependency | most complete, published on Maven Central | minSdk 31, key components API 34+; alpha without API guarantees; pulls Navigation3 and a newer Compose; MPL-2.0; icons apparently replicate vendor icons |
| Iris UI Kit as dependency | numbers fitted to real screenshots, Apache-2.0, our Compose version | three days old, one author, no tests, not published; haptics use undocumented vendor constants |
| Haze for blur + own components | mature (2.1.0, benchmarks, screenshot tests), minSdk 23, explicit fallback below API 31, `reduceTransparency` support | glass look must be designed by us |
| Everything own incl. blur | no dependency | re-solving blur capture, fallbacks and performance that Haze already solves |

## Decision

- Own thin design layer (tokens, glass and solid materials, components) on Compose foundation and Material3 where
  it helps (accessibility semantics, text fields, switches), styled by our tokens.
- `dev.chrisbanes.haze:haze` (+ `haze-blur`) for backdrop blur of the shell (bottom bar, rail, top bar, sheets,
  dialogs). `haze-glass` effects only after on-device verification.
- Iris UI Kit and COUI-Kit are references only. Ported fragments (if any) keep their license header; nothing is
  ported from `Haptics.kt` (vendor constants); no vendor icons, fonts or images. System font only.
- Fallback: API < 31, power saver, reduced transparency or disabled animations → solid surfaces with the same rim
  and shapes; documented in `docs/design/tokens.md`.

## Consequences

- We own a small component set (bars, cards, range band, chart, sheets) and its screenshot tests.
- Blur cost must be measured on the owner's phone (frame timing during scroll and transitions) before release.
