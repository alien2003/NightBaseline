# ADR 0005: ColorOS 17 canon, and where accessibility wins

- Status: accepted
- Date: 2026-11-26

## Context

The design canon is ColorOS 17 as seen in OPPO's system apps (CLAUDE.md): neutral hue-free greys, one saturated
blue accent, no tonal containers, convex round shell buttons, a floating pill bar without a selection indicator,
progressive blur instead of bar backgrounds, a highlight-under-content press with a small shrink, translate-only
overscroll and critically damped springs. Five audits found the app looked like Material 3 instead
(`docs/design/coloros-conformance.md`). Some canon values fail our non-negotiable rules: 4.5:1 text, 3:1 graphics,
48 dp targets, colour never alone, 200 % font scale, calm-health tone (`principles.md`).

The owner resolved the open questions on 2026-11-26: single blue accent (the violet accent is retired), amber
moved colour starting from the ColorOS functional yellow #E5A100, a calm green within colour, a 30 dp dialog radius
(bottom-docked on phones), and light values taken from the new light reference captures.

## Decision

Adopt the canon values (`coloros-conformance.md` 4, implemented in `docs/design/tokens.md` and the theme), with
these recorded deviations, each because an accessibility or tone rule wins:

| Canon | Ours | Rule that wins |
|---|---|---|
| white label on #428AF7 (3.38:1) | label fills use `primaryFill` #2A6BE0 (4.91); #428AF7 stays for controls, graphics and links | 4.5:1 text |
| light accent #0078EF / #0080FF as text (4.26 / 3.8) | `primary` #0068E0, `primaryText` #0062D6 | 4.5:1 text |
| light summary #757575 (4.07 on the window) | #666666 | 4.5:1 text |
| status hues #24B232 / #E5A100 in light | #19762A / #8F5A00 (pass on every surface up to `surface.3`) | 4.5:1 text |
| disabled item #5F5F5F (2.73) | labels `text.tertiary`, only control graphics #5F5F5F | 4.5:1 text |
| unchecked radio #5F5F5F | #767676 (3.83) | 3:1 graphics |
| switch off track #505050 / #D6D6D6 (2.16) | kept: the knob position carries the state | canon exception, non-colour carrier |
| nav label 10 sp, scaling capped | 11 sp, never capped, wraps | 200 % font; exception to the 12 sp minimum (the icon and content description double it) |
| no focus ring | 2 dp keyboard-only focus ring | keyboard accessibility |
| 40 dp round buttons, 36 dp chips | same visual size, 48 dp target | 48 dp targets |
| top bar title over blur with a 0.35 darkening | darkening kept where no text sits; the title row gets `glass.topBar` (passes over white and black) | 4.5:1 text on glass |
| red destructive confirm | `primaryFill`; the wording carries the consequence | calm tone (no red) |
| COUI press release 0.4 / 158 (rebounds above 1.0), scale down to 0.85 | shrink 0.96-0.98 by size, release 0.8 / 380 with the scale clamped at 1.0 | the research finding that ColorOS never grows an element (`coloros17-glass.md` 4.4) |
| coloured "within" status everywhere | grey "Usual" label with a small green dot | calm tone |

Retired tokens (`primaryContainer`, `onPrimaryContainer`, `accent`, status containers, `band.edge`, `glass.rimMid`,
`glass.rimOuter`) become aliases of their neutral replacements, marked deprecated, and are deleted in the sweep once
no package uses them.

## Consequences

- `ContrastTest`, `ColorTokensTest` and `tools/design/contrast_check.py` keep the deviations enforced.
- The Material ripple is removed app-wide (`LocalIndication`, `LocalRippleConfiguration`), and `RippleBanTest` /
  `tools/check-ripple.sh` keep it out. Android's stretch overscroll is replaced by our own translate overscroll.
- Until the shell, controls and cards packages land, components that still use the retired roles render with the
  neutral aliases; white labels on `primary` (instead of `primaryFill`) fall to 3.38:1 in dark until the buttons are
  migrated (tracked in `coloros-conformance.md` WP3).
