# On-device end-to-end suite

Tests NightBaseline on the real phone, against the **release build that is already installed** there and the owner's
real Health Connect data, without replacing the app and without changing its data or any other app's data. The suite
lives in `app/src/androidTest/kotlin/…/e2e/`; `run-device-tests.sh` builds and installs only the test APK and runs the
test classes one by one. Adapted from PulseStages' suite (`../PulseStages/tools/device-test/`).

- [What runs on the phone](#what-runs-on-the-phone)
- [How it runs against the installed release](#how-it-runs-against-the-installed-release)
- [The single write and delete](#the-single-write-and-delete)
- [Permissions](#permissions)
- [Safety guarantees](#safety-guarantees)
- [What else changes on the phone](#what-else-changes-on-the-phone)
- [Remaining risks and open hypotheses](#remaining-risks-and-open-hypotheses)
- [How to run](#how-to-run)
- [Reports](#reports)
- [Testing the runner](#testing-the-runner)

## What runs on the phone

Each step is its own `am instrument` call, in this order. "App" means the step runs inside the installed app's process
(`E2eRunner`), with the app's uid and READ permissions; "test package" means the test APK's own process
(`SelfE2eRunner`).

| Step | Class | Process | What it does | Writes to Health Connect |
|---|---|---|---|---|
| 1 | `HealthConnectE2e` | app | SDK available; the required permissions granted (and nothing the app does not declare); the configured source's sessions, channels and newest night of the last 7 days read through the app's own reader within a read budget; source discovery lists the default source (OHealth) first; the background-read feature status recorded | no |
| 2 | `HcSnapshotE2e#before` | app | Snapshot of steps and sleep sessions, per data origin, in a fixed window (31 Dec 1999 – 3 Jan 2000) and in the last 7 days: count, newest `lastModifiedTime`, SHA-256 over the sorted `id:lastModified` pairs; no `nb-e2e:` record may exist | no |
| 3 | `WriteDeleteE2e` | test package | **The only write** (below). Skipped unless WRITE_STEPS was granted with `--grant-write` | one record, deleted at once |
| 4 | `WriteCleanupE2e` | test package | Deletes any `nb-e2e:` record of the test package left in its window and checks that none is left (also run by the exit trap when step 3 was interrupted) | deletes only `nb-e2e:` records of the test package |
| 5 | `HcSnapshotE2e#after` | app | The snapshot again, compared with step 2: every other origin's data in the fixed window identical (else FAIL), no `nb-e2e:` record anywhere (else FAIL), changes in the last 7 days listed as warnings (other apps keep syncing) | no |
| 6 | `PipelineE2e` | app | `NightProcessor` (which runs the `Evaluator`) over the real data of the configured source, with an in-memory Room database of the app's own database class, an in-memory copy of the settings and a fake notifier: the outcome must be an evaluation or a valid waiting / nothing-new state (Blocked fails); no notification posted | no |
| 7 | `UiE2e` | app | MainActivity of the installed app with its own Hilt graph and data. Onboarding not done: onboarding must show. Done: Home → History → Settings → Home with Compose testing, each page's title found, the tabs selected; any crash fails it. Screenshots of each screen. It presses the three navigation tabs and nothing else | no |

Steps 3–5 run only when step 2 passed. A step that is skipped because of a missing permission says why in the summary.

## How it runs against the installed release

The phone runs the owner's **release** build: minified with R8, signed with the release key, not debuggable. The
constraints:

1. Android lets an instrumentation run inside an app only when the test APK is signed with the **same certificate** as
   that app (on a user build there is no exception for non-debuggable apps). A debug build installed side by side is
   no option either: it would have another package or another signature, none of the owner's permissions and none of
   the owner's data, and installing a debug build over the release is impossible without uninstalling it (which would
   delete its data).
2. A test APK compiled against a **minified** app does not link: R8 renamed or removed the classes and members the
   test libraries (Compose testing, work-testing, androidx.test) and the tests need. Checked with R8's
   `tracereferences`: hundreds of missing references against the release APK.

The chosen setup changes nothing on the phone except installing the test APK:

- A build type `e2e` (`app/build.gradle.kts`) is the release build without R8 (`initWith(release)`, same signing
  config, same sources including `src/release`). `testBuildType = "e2e"`, so the test APK
  (`app-e2e-androidTest.apk`, package `io.github.alien2003.nightbaseline.test`) is signed with the **release key**. Its
  own app APK is never installed. (AGP 9 would then create unit tests for `e2e` only: `gradle.properties` sets
  `android.onlyEnableUnitTestForTheTestedBuildType=false` and the root `build.gradle.kts` keeps unit tests on
  `debug` in every module, exactly the `testDebugUnitTest` tasks there were before.)
- The test APK carries its **own unminified copy of every class of the app** (the app's own classes, the
  Hilt-generated ones, the R classes and all libraries: task `e2eAppClassesE2e`, Variant API `ScopedArtifacts.Scope.ALL`
  appended to the test APK's classes). Android puts the instrumentation APK **first** on the instrumented app's class
  path, so inside a test process every class (from `NightBaselineApp` and `MainActivity` down to Room and Compose) is
  loaded from that copy, while the manifest, resources, uid, permissions, settings and database are the installed
  app's. `tracereferences` finds no missing reference in the test APK on its own (apart from `sun.misc.*`, which
  Android provides).
- The copy is only valid for the same sources. Before it installs anything, the runner pulls the installed APK and
  **refuses to run** unless (a) its `classes*.dex` are byte-identical to the local `assembleRelease` output of this
  checkout, built with the installed versionName and versionCode, (b) it is signed with the same certificate as the
  test APK, and (c) every resource id of the installed APK has the same name and id in the `e2e` build. So the code
  running in the test process is the code of the installed app, without R8; in particular it can never open the
  app's database with another schema (no migration, no downgrade).
- `connectedAndroidTest` and `installDebug` are never used (they would install the app APK). Nothing is installed
  but the test APK, and only the test package is uninstalled at the end.

`E2eRunner` installs work-testing's in-memory WorkManager (`E2eWorkManager`) in `Instrumentation.onCreate`, after
Android created the app's Application object and before it calls `Application.onCreate`: the app's start-up code
(`scheduler.ensureScheduled()`) finds that WorkManager, the app's WorkManager database is never opened, nothing goes to
JobScheduler, and any worker it would start is refused (counted in every report as `refusedWorkers`).

## The single write and delete

The app's manifest declares no WRITE permission (`MergedManifestTest` checks the merged debug, release and e2e
manifests) and stays that way. A Health Connect permission is granted to the package that declares it, so the one
test write cannot be made as the app. It is made by the **test package itself**:

- Only the test APK's manifest (`app/src/androidTest/AndroidManifest.xml`) declares a write permission:
  `android.permission.health.WRITE_STEPS`, one record type. It also declares the permission-rationale activity and the
  Android 14+ `VIEW_PERMISSION_USAGE` alias that Health Connect requires before it grants a health permission.
- `WriteDeleteE2e` runs in the test package's own process (`SelfE2eRunner`, a second instrumentation of the test APK
  that targets the test package; `app/build.gradle.kts` adds it to the merged test manifest). Its data origin is
  `io.github.alien2003.nightbaseline.test`, never the app's.
- `TestRecordWriter` is the only code of the suite that can write or delete. It allows exactly one insert per run: one
  `StepsRecord` of 1 step from 2000-01-01 03:00 to 03:01 UTC (far outside every real night), clientRecordId
  `nb-e2e:<random uuid>`, manual entry. Deletes are by record id only (never by time range): the id that insert
  returned, or the test package's own `nb-e2e:` leftovers in its window. Anything else throws `SafetyViolation`.
- Sequence: delete own leftovers of an interrupted earlier run → insert → read back (own data origin: exactly one
  record, the same id, clientRecordId, start, end and count) → delete by id in `finally` → read again: none left. The
  record's lifetime is asserted under 60 s (normally well under a second).
- Around it, in the app's process (which can see other apps' data): the snapshot before (step 2) and after (step 5).
  Other origins' data in the fixed window must be identical; no `nb-e2e:` record may be left anywhere.
- Health Connect keeps the records of an uninstalled package, so `WriteCleanupE2e` runs after the write and again from
  the exit trap if the write step was interrupted, before the test package is uninstalled.

## Permissions

| Permission | Package | Needed by | How |
|---|---|---|---|
| READ_SLEEP, READ_HEART_RATE (required), the optional READ_* the owner granted | the app | steps 1, 2, 5, 6, 7 | already granted during onboarding |
| READ_HEALTH_DATA_IN_BACKGROUND | the app | steps 1, 2, 5, 6 (an instrumentation is a background caller; without it Health Connect silently returns only the caller's own data, so the snapshot refuses to run and the write is not attempted) | already granted during onboarding |
| READ_HEALTH_DATA_HISTORY | the app | other apps' data in the fixed window of 2000 is visible only with it; without it the fixed-window comparison covers what is visible | already granted during onboarding |
| READ_STEPS | the app | the steps part of the snapshots (sleep is always compared) | optional |
| WRITE_STEPS | **the test package only** | step 3 | `--grant-write` (below); otherwise step 3 is SKIPPED |

**The test WRITE permission.** The test package exists on the phone only while the runner runs, so there is nothing
to grant in Health Connect's settings beforehand. With `--grant-write` the runner, after installing the test APK, runs
`adb shell pm grant io.github.alien2003.nightbaseline.test android.permission.health.WRITE_STEPS` (the test package
only, this one permission; `pm grant` of health permissions works on the owner's Android 17). The grant disappears
with the test package when the runner uninstalls it. Without `--grant-write` nothing is granted, the write is skipped
with the message "WRITE_STEPS is not granted to io.github.alien2003.nightbaseline.test: the single test write is
skipped …", and every other step runs. The runner never installs with `-g` (which would grant it silently) and the
suite never requests a permission on its own.

## Safety guarantees

- **The app is never installed, updated, downgraded, cleared, uninstalled or stripped of a permission.** Only
  `io.github.alien2003.nightbaseline.test` is installed (`adb install -r -t`, no `-g`) and uninstalled.
- **Health Connect is read-only except for the one record of the test package** (above). The app-process steps reach
  Health Connect only through the app's `HealthConnectReader` / gateway (which have no write or delete method) and the
  snapshot's `readRecords`; the UI step drives the app's own screens, which read like when the owner opens them.
- **The app's own data is not written by the suite's code.** The pipeline runs on an in-memory Room database and an
  in-memory copy of the settings; settings are read once through the app's own repository. The UI step presses only
  the three navigation tabs (no "Check now", no setting, no source change); the screens may do what they do whenever
  the owner opens the app (for example the app's own read of Health Connect status), and since the code is the
  installed code (dex-verified), it re-evaluates nothing it would not re-evaluate anyway.
- **No notification**: the pipeline's notifier is a fake (posts nothing); the test also checks that the app's active
  notifications did not change.
- **Read budget**: every class has a hard cap on Health Connect read calls (`ReadBudget`, an `Error` when exceeded so
  no `catch (e: Exception)` of the app swallows it): HealthConnect 150, each snapshot 100, Pipeline 600 (about 7 reads
  per night of the last 62 days). Health Connect's background quota is about 1000 reads per 15 minutes (AOSP).
- **No health values in logs or reports.** Status files hold the test name, PASSED / FAILED / SKIPPED, the duration,
  counts and flags (the report types allow nothing else); a failure is reported by its exception class. Snapshots
  hold counts, timestamps of modification and hashes; data origins are hashed. Progress lines in logcat and on the
  terminal hold counts and states only.
- **No network**: the test APK requests no INTERNET permission.

## What else changes on the phone

- The test package is installed for the run and uninstalled at the end (also when the run fails or is interrupted).
- Android force-stops the app whenever an instrumentation starts and ends; that cancels its scheduled daily check.
  The runner opens NightBaseline once at the end and returns to the home screen, so that its real WorkManager
  schedules the check again (as whenever the owner opens it). `--no-rearm` skips this; then open the app once.
- Reports are written to `/sdcard/Android/data/io.github.alien2003.nightbaseline/files/e2e/` (and the test package's
  own directory), pulled, and removed. Nothing else of the app's storage is touched by the suite's code.

## Remaining risks and open hypotheses

Not verified on the phone yet (the suite was written and compiled, never run there). Labelled as hypotheses:

- **H1**: Health Connect lets the test package read back its own record with only WRITE_STEPS (no READ_STEPS). If not,
  step 3 fails at the read-back; the record is still deleted in `finally` (deleting own records needs only the write
  permission), and steps 4 and 5 still check that nothing is left.
- **H2**: Android 17 still puts the instrumentation APK first on the instrumented app's class path (AOSP
  `LoadedApk.makePaths`), so the unminified copy is what runs. If not, the app-process steps fail with a linkage
  error before reading anything.
- **H3**: Java resources of libraries (`META-INF/services`) come from the installed APK, where R8 rewrote them to
  minified names; the copy does not use ServiceLoader on the paths the tests cover (kotlinx.coroutines takes its fast
  path), but a library that does would fail with a ServiceConfigurationError.
- The dex comparison requires the release build to be reproducible from the same commit with the project's toolchain
  (mise: Temurin 17, the Gradle wrapper, AGP's R8). A release built elsewhere (CI) may differ; the runner then stops
  before installing anything and says so.
- During its lifetime (well under a minute) other apps allowed to read steps could see the test record of 1 January
  2000; its origin is the test package and its clientRecordId says what it is.
- If another app syncs during the run, changes in the last 7 days are warnings, not failures; a change in the fixed
  window of 2000 by another app would fail the comparison (NightBaseline cannot change other apps' data).
- The installed app's precompiled code is not used in test processes (the class path differs), so they are slower
  than the app.

## How to run

Prerequisites: the project set up with mise (`docs/development.md`), `adb.exe` on Windows reachable as `ADB_WIN` (WSL),
USB debugging allowed for this computer, the release signing key (environment `NIGHTBASELINE_KEYSTORE_PATH`,
`_PASSWORD`, `NIGHTBASELINE_KEY_ALIAS`, `NIGHTBASELINE_KEY_PASSWORD`, or `.secrets/keystore.properties` of the main
checkout), and the checkout at the commit the installed release was built from.

```sh
mise exec -- tools/device-test/run-device-tests.sh --dry-run        # prints every adb command, runs nothing
mise exec -- tools/device-test/run-device-tests.sh                  # everything except the write
mise exec -- tools/device-test/run-device-tests.sh --grant-write    # everything, with the single write and delete
```

The runner: checks that exactly one authorised phone is connected (`--serial` to choose); reads the installed app's
versionName and versionCode; builds `:app:assembleRelease :app:assembleE2e :app:assembleE2eAndroidTest` with them;
pulls the installed APK and verifies dex, certificate and resource ids; removes the suite's report directory of an
earlier run; installs the test APK; grants WRITE_STEPS to the test package with `--grant-write`; runs the steps;
pulls the reports to `tools/device-test/out/<timestamp>/` (git-ignored); removes them from the phone; uninstalls the
test package; opens the app once; prints the summary (`Result: PASSED`, `FAILED` or `INCOMPLETE`, with the command
that fixes anything left behind). Options: `--dry-run`, `--grant-write`, `--skip-build`, `--serial SERIAL`,
`--keep-going`, `--no-screenshots`, `--unlock-timeout SECONDS` (default 120), `--no-rearm`. Every adb call has a
timeout and reads no terminal input; Ctrl-C stops the current step, then the exit trap cleans up.

What the owner does: connect the phone over USB, unlock it and leave it on the home screen; when ColorOS asks whether
to install "Tests for io.github.alien2003.nightbaseline", tap Install; then leave the phone alone until the summary
appears (a few minutes; the pipeline step reads about two months of nights). The UI step waits up to two minutes for
an unlocked screen and says "UNLOCK IT NOW" when the phone is locked; the suite never unlocks it.

## Reports

`tools/device-test/out/<timestamp>/`: `runner.log`, `instrumentation-<step>.txt` (raw `am instrument` output),
`app/` (pulled from the app's directory: `status/*.json` per test, `snapshot-before.json`, `snapshot-after.json`,
`snapshot-compare.json`, `ui/*.png`) and `self/` (the test package's `status/*.json`). The screenshots show the
owner's real screens: they stay on this computer and never go into the repository (screenshots in the repository come
only from synthetic data).

## Testing the runner

`tools/device-test/selftest/run-selftest.sh` runs `run-device-tests.sh` against a fake adb (`selftest/fake-adb`, no
phone, fake APKs and build tools) and checks: a normal run with `--grant-write` (order of the steps, the write in the
test package's process, only the test APK installed and never with `-g`, the grant only to the test package, only the
test package uninstalled, reports pulled, the app opened at the end); a run without `--grant-write` (no grant, the
write skipped, everything else run); `--dry-run` (no adb call at all, every command printed); two phones, an
unauthorised phone, the app not installed, another build installed (dex differs), another signing key, other resource
ids (each refused before anything is installed); a failed and a skipped "before" snapshot (no write); and SIGTERM
during the write (the test package stopped, the write cleanup run before the uninstall). It needs bash, python3 and
coreutils and takes under a minute.
