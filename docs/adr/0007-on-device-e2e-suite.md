# ADR 0007: On-device end-to-end suite inside the installed release

- Status: accepted
- Date: 2026-11-26

## Context

JVM tests (Robolectric, fakes, synthetic data) cannot show that the app works on the real phone: Health Connect's
real permissions and background-read rules, the configured source's real data, Android 17 and the R8-minified release
build. The phone runs the owner's release build with the owner's real data and permissions, and the project rules
allow nothing on it that changes other apps' data or the app's own data (CLAUDE.md rules 6 and 10). Two technical
constraints shape any suite:

1. An instrumentation can run inside an app only when the test APK is signed with the same certificate as that app.
   A separately installed debug build would have another package or key, none of the owner's permissions and none of
   the data, and replacing the release with a debug build would delete its data.
2. A test APK compiled against a minified app does not link: R8 renamed or removed hundreds of classes and members
   the tests and test libraries need (checked with R8's `tracereferences`).

Health Connect grants a permission to the package that declares it, and the app's manifest is read-only by design
(no WRITE permission; `MergedManifestTest`), yet one test needs to prove that a write and its deletion leave nothing
behind.

## Decision

- **Build type `e2e`**: the release build without R8 (`initWith(release)`, same signing config and sources), and
  `testBuildType = "e2e"`, so the test APK is signed with the release key. Its own app APK is never installed. Unit
  tests stay on `debug` in every module (`android.onlyEnableUnitTestForTheTestedBuildType=false` and a root rule).
- **The test APK carries an unminified copy of every class of the app** (`ScopedArtifacts.Scope.ALL` appended to the
  test APK). Android puts the instrumentation APK first on the instrumented app's class path, so test processes run
  that copy with the installed app's manifest, resources, uid, permissions, settings and database.
- **The copy must be the installed code.** Before installing anything the runner pulls the installed APK and refuses
  to run unless its dex equals this checkout's release build (same versionName and versionCode), its certificate
  equals the test APK's and every resource id matches. Only the test APK is installed and uninstalled; `installDebug`
  and `connectedAndroidTest` are never used.
- **One write, by the test package itself.** Only the test APK's manifest declares a write permission (`WRITE_STEPS`),
  granted by the runner to the test package only and only with `--grant-write`. A second, self-targeting
  instrumentation (`SelfE2eRunner`) makes exactly one `StepsRecord` in the year 2000 with a tagged clientRecordId
  (`nb-e2e:<uuid>`) and deletes it by id at once (`TestRecordWriter`, the only code that can write or delete; anything
  else throws). A cleanup step removes leftovers of an interrupted run.
- **Before and after snapshots** of other apps' data in a fixed window (counts, newest modification time and hashes,
  no values) must be identical; no tagged record may be left.
- **No app data, notifications or schedules are touched by the suite's code**: an in-memory WorkManager, an in-memory
  Room database of the app's own database class, a copy of the settings and a fake notifier. Hard read budgets per
  test class keep Health Connect's quota safe. Reports hold names, states, counts, flags and hashes only and stay in
  the git-ignored `tools/device-test/out/`.
- Runs only with the owner's permission for that run; never in CI. `--dry-run` prints every adb command, and a
  self-test runs the runner against a fake adb.

The details (each step, permissions, guarantees, remaining hypotheses and how to run) are in
[tools/device-test/README.md](../../tools/device-test/README.md).

## Consequences

- The suite tests the exact installed release code on real data, without replacing the app or losing its data.
- The checkout must match the installed release, and the release must be reproducible locally with the project's
  toolchain; a build from elsewhere (CI) may differ, and the runner then stops before installing anything.
- The app's manifest stays read-only; the test package's write permission exists on the phone only while the runner
  runs.
- Test processes do not use the installed app's precompiled code, so they are slower than the app.
- Some behaviour is a hypothesis until observed on the phone (class-path order on Android 17, read-back of the test
  package's own record with only WRITE_STEPS, library service files); the README lists them.
