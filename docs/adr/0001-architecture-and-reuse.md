# ADR 0001: Architecture and reuse of PulseStages

- Status: accepted
- Date: 2026-11-25

## Context

NightBaseline reads nightly measurements from Health Connect once a day, builds a personal baseline per metric and
reports nights where several metrics are outside it. The sibling project PulseStages (same author) already solves
reading Health Connect, OHealth's night publication behaviour, background work, screenshots from synthetic data,
on-device tests and release automation. NightBaseline must not depend on PulseStages and must not modify it
(`../PulseStages` is read-only). Evidence: `docs/research/pulsestages-reuse.md`.

## Options considered

1. **Depend on PulseStages modules** (included build or published artifacts). Rejected: an included build runs Gradle
   inside the read-only folder; PulseStages publishes no artifacts; the domains differ (stage estimation vs baselines);
   independent release cadence and signing.
2. **Fork PulseStages and strip it.** Rejected: most of its code (HMM/HSMM, hybrid model, Sleep as Android sync,
   session writing) is irrelevant; stripping costs more than writing the small parts needed; its UI is explicitly not
   a model.
3. **Copy and adapt selected pieces, same layering** (chosen). PulseStages is the same author's own code, so copying it is free; each copied
   piece is adapted to NightBaseline names and reviewed.

## Decision

Same layering and dependency rule as PulseStages, enforced by a module-graph check:

| Module | Kind | Responsibility | May depend on |
|---|---|---|---|
| `:core-algo` | pure Kotlin/JVM | metric registry, main-night selection, extraction, robust baseline, evaluation, synthetic nights, night-complete rule | nothing (classpath purity check) |
| `:data-local` | Android library | Room database (night summaries, evaluations, run log), DataStore settings | core-algo |
| `:data-hc` | Android library | Health Connect access: permissions, feature checks, source discovery (which types a source writes), paged reads, mapping to `core-algo` types | core-algo, data-local |
| `:work` | Android library | daily WorkManager run (wait for a complete night, evaluate, notify), notifications | core-algo, data-hc, data-local |
| `:app` | Android application | Compose UI, adaptive layouts, design system, DI wiring | all above |
| `:algo-cli` | JVM application | offline runs over the dataset (`.data/`) and synthetic nights | core-algo |
| `:hc-exporter` (`tools/hc-exporter`) | debug app, never released | developer dataset export | nothing |

Key choices:
- **Pluggable metrics**: a metric is a `MetricSpec` (channel, aggregation, sensitivity, spreads, quality gates). A new
  Health Connect type needs a channel mapping in `:data-hc` and a spec; nothing else changes. Metrics are evaluated
  only when the selected source writes their channel (source discovery at runtime).
- **Vendor neutrality**: the source package is a setting; OHealth is the default. Vital signs are read from the same
  origin as the sleep session. PulseStages' own sessions are always excluded (they are derived from the same heart
  rate and would double sleep duration).
- **Stack** aligned with PulseStages (versions from its verified catalog): AGP 9.4.1, Kotlin 2.4.21, Compose BOM
  2026.09.00, Hilt, Room, WorkManager, DataStore, connect-client 1.1.0, JUnit 5 + Robolectric for JVM screenshots,
  detekt + ktlint. compileSdk 37, targetSdk 36, minSdk 28.
- **Taken from PulseStages (adapted)**: mise/.tooling layout, ADB_WIN and the install rule, the module-graph and
  classpath-purity checks, Health Connect read patterns (paging, error mapping, background-read guard, permission
  manifest pattern with the rationale activity-alias), the night-settlement idea for "complete night", WorkManager
  outcome mapping and DST-safe daily scheduling, notification conventions, the Robolectric screenshot pipeline,
  on-device e2e runner with Health Connect safety guards, detekt config, CI/release workflows with version-from-tag,
  the Obtainium button. Not taken: the stage estimators, Sleep as Android integration, session writing, UI.
- **PulseStages sleep stages** are not an input in v1 (derived from the same heart rate, low agreement with PSG,
  algorithm version drift). May be revisited as an informational overlay.

## Consequences

- Copied code diverges from PulseStages over time; that is accepted (no shared library).
- `:core-algo` stays testable on the JVM and is shared by the app and the CLI, so offline dataset runs execute the
  exact app code.
