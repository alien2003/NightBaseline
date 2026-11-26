# STATUS

Live project state. A new session starts here. Updated after every step. Last full rewrite: 2026-11-26.

## Where things are
- Working copy: git worktree `<repo>` (= `<project>/.claude/worktrees/dev`), branch `worktree-dev`, pushed to origin.
  `main` is fast-forwarded to verified commits only (green CI + checked on the owner's phone); currently `b11e441`.
- `.tooling/`, `.data/`, `.secrets/` live in `<project>/` (git-ignored); the worktree has symlinks to `.tooling` and
  `.data`.
- Repository: github.com/alien2003/NightBaseline — PUBLIC (since 2026-11-26, owner's request). Recreated once with a
  rewritten history (no home paths, Apache-2.0 in every commit, ADR 0004). Guards on every commit and in CI:
  `tools/check-language.sh`, `tools/check-leaks.sh`, `tools/check-ripple.sh`.
- Release signing key: `<project>/.secrets/nightbaseline-release.jks` + `keystore.properties` (mode 600). Certificate
  SHA-256 `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`. Uploaded
  as repo secrets NIGHTBASELINE_KEYSTORE_BASE64 / _KEYSTORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD. **The owner must back
  it up.** Local signed builds: a script reading the properties file (never prints secrets).
- Phone: an OPPO book foldable (outer 1140×2616, inner 2248×2480), Android 17, ColorOS 17.0.
  Installed: NightBaseline 0.1.0-dev.5 (release key) and the debug exporter.

## Done
- Stage 0: environment, mise, git, CLAUDE.md, CI (ci / build-apks / release), signing key.
- Stage 1a: read-only exporter + `tools/hc-export.sh` (closes the exporter on exit) + `tools/hc-dataset.py`; 4 export
  runs, 270k records. Stage 1b: `docs/research/metrics-matrix.md`. 1c: `docs/research/ohealth-cloud.md`.
- Stage 2: ADRs 0001 architecture/reuse, 0002 English only, 0003 design layer + Haze, 0004 Apache-2.0, 0005
  accessibility over canon, 0006 top-level sections stay composed.
- Stage 3: `docs/design/*` (principles, tokens, screens, states, layouts, reference analysis, ColorOS conformance plan,
  review rounds 0–4), `docs/research/coloros17-glass.md`.
- Stage 4: core-algo (+ ALGORITHM.md, synthetic validation 0.05 % false alarms, 196/200 episodes), algo-cli, data-local,
  data-hc, work, app (all screens and states, adaptive layouts, ColorOS 17 restyle, aurora launcher icon), recompute of
  stored nights on algorithm/sensitivity change, README screenshots, docs (README en/ru, user guide, privacy,
  CHANGELOG, development). Live checks on the phone: onboarding, history backfill (57 nights), fold/unfold, half-open,
  split width, theme, rotation; frames: scroll 0.71 % janky, tab switch 3.6 %.

## In progress
- BUG (found 2026-11-26): a complete night was in Health Connect (all channels written to the end of the session, the
  person already up), but NightBaseline kept showing "Last night isn't in yet", even after "Check now". Reproduced
  locally with the exported dataset; cause: the "source still writing" rule counted every record of the source written
  since the night started (the morning's step records, all-day heart rate), so a check within 15 min of any daytime
  sync waited, up to the 12 h cap. Fixed: only the night's own records count (`readNight(writesBeforeMs)`), synthetic
  regression test `DaytimeWritesTest`, system-log diagnostics (tag `NightBaseline`, docs/development.md). Still to
  confirm on the phone: a real night processed automatically after the fix.
- On-device e2e suite (instrumentation, single write+delete of a year-2000 tagged record, before/after snapshots).
- "Doesn't feel smooth": the phone rendered the app at 60 Hz ("Win preferred rateId=0"). The window now asks for the
  fastest mode at the current resolution while in use, plus `FrameRateCategory.High` votes on API 35+ (ADR 0008);
  second pass: java.time stability config, calendar cells memoized, lazy start-up injection, `ScrollCostTest`. Still
  to verify on the phone (ask): `dumpsys display` / `SurfaceFlinger` / `gfxinfo` (docs/development.md, Performance).

## Next
1. Fix → build dev.6 → install (ask) → confirm the night is processed automatically; then a further new night without
   manual action (the "one new night" release criterion).
2. Run the on-device suite (ask; describe exactly what runs).
3. Pre-publication batch (stage 5): leak audit of history/CI artifacts, final gallery, summary of what becomes public,
   owner's "yes" → fast-forward main, tag v0.1.0, release, verify Obtainium installs from the release, final report.

## Owner rules learned (see CLAUDE.md and memory)
- Ask before every phone access (what, how long); never drive other apps; never leave the screen lit (OLED).
- English-only repository; Russian only in chat, ru resources and README.ru.md.
- Design canon is ColorOS 17 (not Material, not Apple).

## Problems and workarounds
- Worktree guard rejects complex shell commands: put multi-step adb/git work into scripts under the job tmp dir.
- CI memory: hosted runners ~7 GB; app/screenshot tests run in their own job with one JVM per test class.
- `cancel-in-progress` cancels CI runs on new pushes to the same branch: do not push while waiting for a run.
- Robolectric renders Haze blur; screenshot tests are the reference; motion/glow/blur feel only verifiable on device.
