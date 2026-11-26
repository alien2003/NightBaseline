# ADR 0006: Top-level sections stay composed across tab switches

- Status: accepted
- Date: 2026-11-26

## Context

Measured on the owner's phone (release 0.1.0-dev.4, unfolded inner screen, rail and two panes, warm): scrolling
History alone was 0.76 % janky, but switching Last night <-> History six times gave 12 janky frames of 100 and about
two slow UI-thread frames per switch. Home, History and Settings were separate navigation-compose destinations.
`popUpTo(Home) { saveState = true }` with `restoreState` kept their ViewModels and saveable state, but
navigation-compose disposes a destination's composition when another one is shown, so every switch composed,
measured and drew the whole destination again (shell, rail, top bar, lazy rows, the Night detail pane, ICU
formatting in each row) on the UI thread.

## Decision

- Home, History and the Settings list share one destination, `TopLevelRoute`, hosted by `TopLevelHost`. A section
  is composed on its first visit and stays composed while the destination is on the back stack; hidden sections are
  neither measured, placed nor drawn and their semantics are cleared. A switch crossfades two already composed trees
  through the layers' alpha (150 ms, 120 ms under reduced motion, as before), read in the draw phase only.
- The selected section is saved state of the `TopLevelRoute` entry (`TOP_LEVEL_TAB`), set by `toTop`, so deep links,
  shortcuts and the More sheet keep working and it survives process death. Saveable state is kept per section by a
  `SaveableStateHolder`. Back from History or Settings selects Home (the former `popUpTo(Home)` behaviour).
- Effects of a hidden section that navigate (fold hand-over) or take Back wait until it is shown (`active`, read
  outside composition so a switch recomposes no section).
- Pushed pages (Night detail, Metric detail, Settings sub-pages, onboarding) stay ordinary destinations with the
  push / pop transitions.

## Consequences

- A warm tab switch composes nothing and maps nothing (`TabSwitchCostTest` counts screen and row compositions and
  ViewModel mappings through `PerfProbe` and fails on a regression).
- A switch pops pages pushed above the sections (rail layouts can switch from a pushed page): the per-section back
  stacks of navigation-compose's multiple back stacks are not kept. Two-pane layouts show detail in panes, so this
  only affects single-pane rail windows; accepted for the cost saving.
- Hidden sections keep their ViewModel subscriptions (cheap: Room and DataStore flows; mapping runs on the compute
  dispatcher).
- Returning from a pushed page rebuilds only the selected section; the others are built again on their next visit.
