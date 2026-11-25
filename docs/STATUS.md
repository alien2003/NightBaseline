# STATUS

Live project state. A new session starts here. Updated after every step.

## Where things are
- Working copy: git worktree `<repo>`, branch `worktree-dev` (pushed to
  origin). `main` holds only the initial commit until the pre-release merge.
- `.tooling/` and `.data/` in the worktree are symlinks to `<project>/.tooling` and
  `<project>/.data` (independent of the worktree, both git-ignored).
- Repository: github.com/alien2003/NightBaseline (private).

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
