# STATUS

Live project state. A new session starts here. Updated after every step.

## Where things are
- Working copy: git worktree `<repo>`, branch `worktree-dev` (pushed to
  origin). `main` holds only the initial commit until the pre-release merge.
- `.tooling/` and `.data/` in the worktree are symlinks to `<project>/.tooling` and
  `<project>/.data` (independent of the worktree, both git-ignored).
- Repository: github.com/alien2003/NightBaseline — PUBLIC since 2026-11-26 at the owner's request (before the
  release). On 2026-11-26 the repository was deleted and recreated (owner's decision) with a rewritten history: no
  absolute home paths, Apache-2.0 in every commit (ADR 0004); no unreachable old commits remain on GitHub. Signing
  secrets were uploaded again. `tools/check-leaks.sh` and `tools/check-language.sh` guard every commit and CI.

## Done
- [x] Stage 0: environment checked (mise, Java 17, Python 3.12, Android SDK copied from PulseStages into
  `.tooling/android-sdk`, `gh` authenticated), git, private repository, CLAUDE.md, .gitignore, .mise.toml.
- [x] Gradle skeleton (wrapper 9.8.1, version catalog from PulseStages) and `tools/hc-exporter` (debug Health Connect
  exporter, READ permissions only). The APK builds.
- [x] `tools/hc-export.sh` (install, grant over adb, export, pull through run-as) and `tools/hc-dataset.py` (merge runs
  into the dataset, value-free summary).
- [x] Phone: OPPO CPH2765 (book foldable: outer 1140×2616, inner 2248×2480), Android 17 (SDK 37), ColorOS 17.0. Device
  states: CLOSED, TENT, HALF_OPENED, OPENED. The outer screen is a full phone screen (not a small flip cover screen).
- [x] Health permissions can be granted over adb (`pm grant` works on Android 17). Without
  `READ_HEALTH_DATA_IN_BACKGROUND` only the first type is read while the phone is locked (reads count as background
  reads), so the exporter now declares it.
- [x] First full export: 269,199 records. OHealth since 2026-09-28 (58 nights); before that imported history of a
  previous device (`org.healthmerge.hcimporter`, with SpO2, respiratory rate, skin temperature), Sleep as Android,
  PulseStages.
- [x] Reference screenshots, folded posture only (`.data/design-refs/folded/`, ignored). Unfolded set NOT taken.

## In progress
- Desk research workflow → `docs/research/` (PulseStages reuse, ColorOS 17 + UI kits, large screens, analogs, Health
  Connect docs, OHealth cloud). Subagents were told to write Russian: **translate to English before final commit.**
- Stage 1b data analysis workflow → `docs/research/metrics-matrix.md` (same: translate).
- `core-algo`: metrics registry, night selection, extraction, baseline/evaluation, synthetic generator, tests (not
  built yet).

## Next
1. Build and test core-algo; ADR for architecture (stage 2); design (stage 3); implementation (stage 4).

- [x] core-algo builds; synthetic check: 0.49 % false alarms per normal night, 199/200 episodes detected.
- [x] History squashed so no commit contains Russian (ADR 0002); remote branch replaced. `tools/check-language.sh`
  guards commits (run it before `git commit`; hook in `tools/githooks`).
- [x] Phone cleanup (with permission, 2026-11-25): stay-awake is off (0, unchanged), exporter force-stopped, no temp
  files left.

- [x] Stage 1b: `docs/research/metrics-matrix.md` (aggregates only, audited). Key facts: OHealth rewrites the previous
  day each morning; SpO2/RR arrive 26–61 min late; resting HR record is a daytime value (unused in v1).
- [x] Stage 2: ADR 0001 (architecture/reuse), 0002 (English only), 0003 (own design layer + Haze).
- [x] core-algo revised from the data: midpoint night keying, ambiguous nights, p10 sleeping HR, floors, z 2.5,
  duration only with a physiological metric, night-complete rule. Synthetic: 0.05 % false alarms, 196/200 found.
  Real data: OHealth 0 notifications / 51 nights; importer 7 / 856 nights.
- [x] algo-cli (aggregate output; details only into `.data/out/`). ktlint + detekt + module graph green.

- [x] data-local, data-hc, work modules (80 tests green at commit time).
- [x] :app phase 1: design system, Haze glass + solid fallback, components, debug catalog, JVM screenshots (blur
  renders under Robolectric), contrast test, merged-manifest test.
- [x] Release signing key: `<project>/.secrets/nightbaseline-release.jks` + `keystore.properties`
  (mode 600, never committed). Certificate SHA-256 `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`.
  Uploaded as repo secrets NIGHTBASELINE_KEYSTORE_BASE64 / _KEYSTORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD.
  **The owner must back it up: without it no update can be installed over the released app.**

## ColorOS 17 restyle (2026-11-26)
- WP1-WP4 of `docs/design/coloros-conformance.md` merged into worktree-dev, then the integration sweep: no Material
  dependency (NbText / NbIcon, own theme locals), retired tokens deleted, package token files read shared values from
  NbColors, one round button (ShellRoundButton) and one segmented control (period selector), README screenshots
  re-recorded. Not verified on the phone yet: blur strength, page dim, overscroll feel (ask the owner first).

## Tab-switch cost and title alignment (2026-11-26)
- Owner's measurement (unfolded, release dev.4): 12 % janky frames when switching Last night <-> History (about two
  slow UI-thread frames per switch), 0.76 % when scrolling. Cause: every switch rebuilt the whole destination.
  Fix (ADR 0006): the sections stay composed in `TopLevelHost`; ICU formatters and night labels cached in `UiText`;
  History's derived lists computed once per state off the main thread; History's mapping cached by input; baseline
  profile extended to navigation-compose, lifecycle / Hilt Compose glue, saveable, lazy and animation.
  `TabSwitchCostTest` guards it. Not measured on the phone yet (ask the owner first).
- Page titles start at the card column's start edge on wide windows (`TitleColumn`; `CardAlignmentTest` checks
  every top-level screen).

## In progress (2026-11-25 evening)
- Screens workflow: 4 agents in isolated worktrees (home+night, metric, history, setup) → merge into worktree-dev.
- CI workflows agent (.github only).
- UI review notes so far: book (medium) shows a bottom bar instead of the rail; chart clips the newest point;
  uneven y-axis ticks.

## Next
- Design docs (done) → review; add AMBIGUOUS_NIGHT state to states.md.
- Android modules: data-local, data-hc, work; then app UI.
- ALGORITHM.md (parameters + justification) for stage 5 docs.
- Re-export on new nights to measure real publication delay (needs the phone: ask first).

## Waiting for the owner
- Nothing right now. **Phone: ask before every access (what, how long).**

## For the pre-publication batch (stage 5)
- Propose recreating the GitHub repository from the clean history (old unreachable commits with Russian text and a
  symlink path may persist on GitHub).

## Problems and workarounds
- `InstantaneousRecord`/`IntervalRecord` are internal in connect-client 1.1.0: time encoded per type.
- 2026-11-25: the design-refs watcher opened system apps after the owner unlocked the phone, without explicit
  permission. Owner objected. Rule 10 in CLAUDE.md: ask before every phone access.

## Decisions (short; details in docs/adr/)
- License Apache-2.0.
- Work in a worktree branch; `main` untouched until the merge confirmed in the second manual batch.
- git author `alien2003 <alien2003@users.noreply.github.com>` (no personal email).
- Project language English; Russian only for the app's ru locale and README.ru.md.
