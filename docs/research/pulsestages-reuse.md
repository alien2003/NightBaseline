# Reusing PulseStages in NightBaseline

Date: 2026-11-25. PulseStages (`../PulseStages`) was examined read-only. Nothing was written there and
nothing was built. `.tooling/signing` and the keys were not opened. Paths below are relative to the PulseStages root
unless stated otherwise. Kotlin sources live in `<module>/src/<set>/kotlin/io/github/alien2003/pulsestages/…`,
abbreviated below as `…/`.

Claim tags:
- `[verified in code/docs]` — verified in the code or documents of PulseStages or NightBaseline;
- `[from official documentation <url>]` — official documentation (often via a link in PulseStages);
- `[hypothesis]` — our inference, or behavior not yet verified for NightBaseline.

**The PulseStages design is not a model.** Its theme, palette, screens (hypnogram, Compare, settings catalog)
and overall look are not carried over. We take only the skeleton: the Compose shell, the theme mechanism and the
styling tests. NightBaseline's visual style is done separately, in the spirit of ColorOS and without vendor resources
(CLAUDE.md, "Product boundaries"). [verified in code/docs]

## Contents
- [1. Summary table](#1-summary-table)
- [2. Details by area](#2-details-by-area)
- [3. Reuse mechanism](#3-reuse-mechanism)
- [4. PulseStages sleep stages as input](#4-pulsestages-sleep-stages-as-input)
- [5. What to re-check on the device](#5-what-to-re-check-on-the-device)
- [Report verification](#report-verification)

---

## 1. Summary table

Decisions: **as is** — copy without changing logic (only names change); **adapt** — copy and rework;
**pattern only** — repeat the idea, write the code from scratch; **no** — do not take.

| Candidate | Decision | Reason | Where in NightBaseline |
|---|---|---|---|
| `.mise.toml` | as is (already done, with edits) | CLAUDE.md rule 2 requires mise following the PulseStages example. Python kept for `tools/hc-dataset.py`, venv moved to `.tooling/venv`, `ANDROID_USER_HOME` added | `/.mise.toml` |
| `.tooling/` (SDK, Gradle home) | adapt (already done) | Rule 3: caches inside the project. Android SDK copied from PulseStages. Keys from `.tooling/signing` are not copied | `.tooling/` (ignored) |
| `ADB_WIN` + WSL interop | as is | Rule 4 requires exactly this. The phone is connected to Windows; Linux adb does not see it | `.mise.toml`, `docs/development.md`, `tools/` scripts |
| Ban on `./gradlew installDebug` in WSL | as is | Rule 4. Same reason: Linux adb does not see the phone. Use `"$ADB_WIN" install -r "$(wslpath -w <apk>)"` instead | `docs/development.md` |
| Module structure | adapt | The same layers and dependency rule fit. Composition differs: `core-baseline` instead of `core-algo`, CLI optional | `settings.gradle.kts`, `verifyModuleGraph` |
| Health Connect reading (incl. SpO2/RR/HRV) | adapt | Only the "read" half is needed: pagination, 24 h padding, mapping, permissions, errors, background guard. Writing, Changes API for writing, eraser not needed | `:data-hc` |
| Knowledge of OHealth behavior | pattern only + constants | Observations n = 3–4 nights on one phone; for us these are hypotheses until re-observed. Simplify the "night complete" logic | `:data-hc` (night completeness rule), ADR |
| `*.night.json` interchange format + CLI | adapt | The idea of one implementation for the phone and offline runs is good. Format differs (per-metric summaries), `schemaVersion` needed | `docs/DATASET.md`, optional `:baseline-cli` |
| WorkManager + notifications | adapt | Daily work, re-check, outcome mapping, notification channel and opening the night on tap fit almost one to one | `:work` |
| Numeric utilities | as is (`Stats`) + adapt (`SignalFilters`) | Median, numpy-style percentile, Hampel with MAD floor. Need to add `RobustStats` (MAD·1.4826, robust z) | `:core-baseline` |
| Synthetic night generator | pattern only | The stage model is not needed. Need a history of N nights with several metrics and injected deviations | `:core-baseline` testFixtures |
| Testing approach | as is (pattern) | JUnit 5 + Robolectric via Vintage, 200-seed invariant property tests, determinism test, edge cases, structural tests | all modules |
| On-device e2e with HC safeguards | adapt (later, stage 4) | Rule 6: one test write and immediate deletion. Guard repository, before/after snapshots, read budget, runner on `adb.exe` | `app/src/androidTest`, `tools/device-test/` |
| detekt (+ ktlint) | as is | Config is generic, versions consistent (detekt 1.23.8 on Kotlin 2.0 PSI) | `config/detekt/`, root `build.gradle.kts` |
| CI: `ci.yml` | adapt | Remove the `python` job, rename the artifact, sync the document lists | `.github/workflows/ci.yml` |
| CI: `build-apks.yml`, `release.yml` | adapt | Signing and APK verification chain is generic. Rename files, env variables and texts; own keys | `.github/workflows/` |
| `version-from-tag.sh`, `changelog-section.sh`, `dependabot.yml` | as is | Scripts are fully generic | `.github/` |
| Obtainium button + import link | adapt | SVG copied as is. JSON differs: id, url, name, APK filter, **our own** certificate hash | `README.md`, `docs/user-guide.md`, `docs/get-it-on-obtainium.svg` |
| Screenshots from synthetic data | adapt | Matches rule 5. `ScreenHost` + `ScreenshotsTest` + `-PrecordScreenshots` + demo sources only in debug | `app/src/testDebug`, `app/src/debug` |
| Documentation style | pattern only | Structure of README/CHANGELOG/SECURITY/CONTRIBUTING and the rule "every claim verifiable". Own language and texts | `README.md`, `docs/` |
| Package id | no (own) | `io.github.alien2003.nightbaseline` per CLAUDE.md. Same scheme `io.github.alien2003.<app>.<module>` | all modules |
| PulseStages visual design | **no** | Not a model. Own palette and ColorOS-style screens | — |
| Training `tools/train`, stage model, HMM/HSMM | no | NightBaseline has no model to train | — |
| HC writing, `VerifiedUpsert`, `MyDataEraser`, backfill, Sleep as Android | no | NightBaseline only reads HC (rule 6) | — |

---

## 2. Details by area

### 2.1 Environment: `.mise.toml`, `.tooling/`, `.editorconfig`, `.gitignore`, wrapper

PulseStages **`.mise.toml`** (16 lines) [verified in code/docs]:
- `java = "temurin-17"`, `python = "3.12"`;
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` = `{{config_root}}/.tooling/android-sdk`;
- `GRADLE_USER_HOME` = `{{config_root}}/.tooling/gradle`;
- `ADB_WIN = "adb.exe"` (line 11). The comment (line 10) gives a fallback path
  `/mnt/c/Users/<user>/AppData/Local/Android/Sdk/platform-tools/adb.exe`;
- `_.path` = cmdline-tools/latest/bin and platform-tools;
- `_.python.venv` = `tools/train/.venv`, needed only for training.

NightBaseline already copied the file. Python was kept because `tools/hc-dataset.py` is written in it. The venv moved
to `.tooling/venv`, and `ANDROID_USER_HOME = .tooling/android-user` was added so the debug key does not live in `~/.android`
(rule 1). [verified in code/docs: `.mise.toml`]

**Android SDK** (`docs/development.md:46-70`) [verified in code/docs]:
- cmdline-tools 23.0 from `commandlinetools-linux-16111833_latest.zip`
  (SHA-1 `e025545c62a8e64c7559119566a569fb1dec5f60`), unpacked to `.tooling/android-sdk/cmdline-tools/latest`;
- `sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0" "build-tools;37.0.0"`;
- platform-tools 37.0.1; AGP 9.4 uses build-tools 36.0.0 by default;
- everything runs as `mise exec -- …`, working copy on the Linux filesystem, not in `/mnt/c`.

**`.editorconfig`** — as is, except the `[docs/prereg/**]` section (PulseStages only) [verified in code/docs]. There are also
`[*.gradle.kts]` with `ktlint_chain_method_rule_force_multiline_when_chain_operator_count_greater_or_equal_than = 5` and
`[*.md] trim_trailing_whitespace = false` — we take those too:
utf-8, lf, 4 spaces, `max_line_length = 140`, `ktlint_code_style = ktlint_official`,
`ktlint_function_naming_ignore_when_annotated_with = Composable`, indent 2 for yml/yaml/json/toml,
`[LICENSE] end_of_line = crlf` (PulseStages keeps its licence text byte for byte).

**`.gitignore`**. NightBaseline already has `.secrets/`, `.data`, `tools/**/out/`, `.claude/worktrees/`
[verified in code/docs]. Add `*.b64`: PulseStages `development.md:548-549` states outright that `.b64` is
not ignored there, and it is the intermediate key file during `gh secret set` [verified in code/docs].

**Gradle wrapper** — as is (already copied): Gradle 9.8.1,
`distributionSha256Sum=dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f`,
`validateDistributionUrl=true`. [verified in code/docs]

**`gradle.properties`** — as is: `-Xmx4g`, parallel, caching, configuration-cache, `kotlin.code.style=official`,
`kotlin.daemon.jvmargs=-Xmx2g`, `android.useAndroidX=true`. [verified in code/docs]

### 2.2 ADB_WIN, WSL interop and the `installDebug` ban

- Commands (`docs/development.md:283-287`) [verified in code/docs]:
  ```sh
  "$ADB_WIN" devices
  "$ADB_WIN" install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
  "$ADB_WIN" logcat -s PulseStages:V     # for us the tag is NightBaseline
  ```
- **Why `./gradlew installDebug` cannot be used in WSL**: the task uses Linux adb from `.tooling/android-sdk/platform-tools`,
  which does not see a phone connected to Windows (`development.md:289-290`). `wslpath -w` produces a path that
  `adb.exe` can read. [verified in code/docs]
- A second possible reason: Linux adb may start its own adb server and conflict with the Windows server.
  [hypothesis]
- `connectedAndroidTest` is not used either: it installs the app APK. On-device tests install only the test
  APK and run it via `am instrument` (`development.md:304-316`). [verified in code/docs]
- Pitfalls (`development.md:294-301`) [verified in code/docs]:
  - release and debug APKs cannot be installed over each other (different keys);
  - a local build with versionCode 1 over a published one gives `INSTALL_FAILED_VERSION_DOWNGRADE`;
    use `-d` for debuggable or `-PversionCode=`.
- In scripts: `ADB="${ADB_WIN:-adb.exe}"`, `wslpath -w` if adb ends in `.exe`, stripping `\r` from
  adb.exe output (`tools/device-test/run-device-tests.sh:150-197`). [verified in code/docs]

### 2.3 Modules and build

**PulseStages modules** (`settings.gradle.kts:31-38`; `docs/architecture.md:30-71`) [verified in code/docs]:
`:core-algo` (pure JVM), `:algo-cli`, `:data-local` (Room, DataStore, log), `:data-hc` (HC boundary and pipeline),
`:work` (WorkManager, notifications), `:app` (UI, DI root).

Allowed edges: app → work, data-hc, data-local, core-algo; work → data-hc, data-local, core-algo;
data-hc → data-local, core-algo; data-local → core-algo; algo-cli → core-algo; core-algo → nothing.

Checked in three places [verified in code/docs]:
- root task `verifyModuleGraph` (`build.gradle.kts:76-174`): `allowedEdges` map, ban on Android plugins
  in core, configuration-cache compatible, hooked to `check`;
- `:core-algo:verifyPureJvmRuntimeClasspath` (`core-algo/build.gradle.kts:28-72`): fails on any `androidx.*`,
  `com.android.*`, `com.google.android.*` on the runtime classpath;
- `NoAndroidImportsTest`: regex over main sources.

**Decision for NightBaseline** [hypothesis: design decision, recorded in the architecture ADR]:
- `:core-baseline` — pure Kotlin/JVM: night metrics (sleeping HR from raw samples), robust statistics, baseline,
  the "≥2 metrics" rule, synthetic data in testFixtures. The same three purity checks.
- `:data-local` — Room (nights, baseline snapshots, sent notifications), DataStore settings, log without values.
- `:data-hc` — HC read only, assembling a night from fragments, night completeness rule.
- `:work` — daily computation, re-check, notification.
- `:app` — UI, DI, manifest, structural tests, screenshots.
- `:baseline-cli` — optional, only if offline runs over `.data/` are needed (see 2.6).
- `:hc-exporter` (`tools/hc-exporter`) already exists and stays a debug module outside the app graph.
  [verified in code/docs: NightBaseline `settings.gradle.kts`]

**Versions** (`gradle/libs.versions.toml`; the header says everything was checked against Maven metadata on 2026-10-13)
[verified in code/docs]:

| Key | Value |
|---|---|
| compileSdk / minor | 37 / 0 (`android-37.0`) |
| targetSdk / minSdk / JVM | 36 / 28 / 17 |
| AGP | 9.4.1 (built-in Kotlin, no `kotlin.android`) |
| Kotlin / KSP / Hilt | 2.4.21 / 2.3.12 / 2.60.1 |
| ktlint-gradle / ktlint | 14.2.0 / 1.8.0 |
| detekt / detektKotlin | 1.23.8 / 2.0.21 |
| core-ktx / activity-compose / lifecycle | 1.19.1 / 1.13.0 / 2.11.0 |
| navigation-compose / Compose BOM / m3-adaptive | 2.10.2 / 2026.09.00 / 1.3.0 |
| androidx.hilt / Room / Work / DataStore | 1.4.0 / 2.8.5 / 2.12.0 / 1.2.1 |
| connect-client | 1.1.0 |
| coroutines / serialization | 1.11.0 / 1.11.0 |
| JUnit 5 BOM / JUnit 4 / Robolectric | 5.14.4 / 4.13.2 / 4.17 |
| android-all-instrumented | 15-robolectric-13954326-i7 (SDK 35; SDK 36/37 require Java 21) |
| android-junit (mannodermaus) | 2.0.1 |

- The catalog is already copied into NightBaseline [verified in code/docs: `docs/STATUS.md`]. Before stage 4, re-check
  for newer stable versions: more than a month has passed. [hypothesis]
- connect-client 1.1.0 is the latest stable, 1.2.0-alpha06 the latest alpha. 1.1.0 already has
  `SkinTemperatureRecord`, background reading and history.
  [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]
  (details in `docs/research/health-connect-docs.md`).
- Constraints to remember [verified in code/docs]:
  - detekt 1.23.8 parses Kotlin 2.0 PSI: do not use guard conditions in `when`, multi-dollar interpolation,
    context parameters (`development.md:189-191`);
  - in AGP 9, Android modules run unit tests only via `testDebugUnitTest`;
  - in WorkManager 2.12, `Configuration.Provider.workManagerConfiguration` is a property;
  - the root declares Kotlin plugins `apply false` to bump KGP 2.2.10 from AGP to 2.4.21
    (`build.gradle.kts:9-10`).

**`settings.gradle.kts`**: `google {}` restricted to groups `com\.android.*`, `com\.google.*`, `androidx.*`,
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Already carried over. [verified in code/docs]

**`app/build.gradle.kts`** (295 lines) — adapt [verified in code/docs]:
- version from `-PversionName` / `-PversionCode`, default `0.1.0-dev` / 1 (`:13-16`);
- release signing only if all four env variables are set, otherwise `app-release-unsigned.apk` (`:18-24, 53-73`);
  rename `PULSESTAGES_KEYSTORE_*` → `NIGHTBASELINE_KEYSTORE_PATH/_PASSWORD`, `NIGHTBASELINE_KEY_ALIAS`,
  `NIGHTBASELINE_KEY_PASSWORD`;
- `compileSdk { version = release(37) { minorApiLevel = 0 } }`;
- release: `isMinifyEnabled`, `isShrinkResources`, `proguard-android-optimize.txt` + `proguard-rules.pro`;
- `androidResources.localeFilters += listOf("en","ru")` — decide separately for NightBaseline (UI likely
  ru + en) [hypothesis];
- unit test arguments via `CommandLineArgumentProvider`: merged manifests, release APK + R8 mapping + aapt2,
  offline Robolectric, screenshots directory. Rename all `pulsestages.*` system properties to `nightbaseline.*`;
- offline Robolectric is repeated in every Android module (`robolectricAndroidAll` → `prepareRobolectricDependencies`
  → `robolectric.offline=true`, `robolectric.properties` with `sdk=35`). For us, move it into a convention plugin or shared
  script to avoid duplication [hypothesis].

**Manifest**: `INTERNET` removed via `tools:node="remove"`, `android:allowBackup="false"`, `MergedManifestTest`
checks this (`app/src/main/AndroidManifest.xml:9-11, 62`). Take as is: absence of INTERNET is a product boundary.
[verified in code/docs]

### 2.4 Health Connect reading (incl. SpO2, RR, HRV)

Source: `data-hc/…/data/hc/` (below `hc/`), `docs/health-connect.md`.

**What we copy and trim** [verified in code/docs]:
- `HealthRepository.kt:185-284` — the single HC boundary. Keep `availability`, `grantedPermissions`,
  `isFeatureAvailable`, `readSleepSessions`, `readHeartRate`, `readSteps`, `newestSteps`, `readSpo2`,
  `readRespiratoryRate`, `readHrv`; remove writing, deletion and source scanning (or keep the scan for the sources
  screen). Add `readSkinTemperature`.
- `HealthConnectRepository.kt` (533 lines): page loop (`:401-430`), `PAGE_SIZE = 1000`,
  `SERIES_LEFT_PADDING_MS = 24 h` (`:471-474`), client cache `getOrCreate(applicationContext)`.
- `HcRecordMapper.kt:77-137`: HR `beatsPerMinute` trimmed by sample time; SpO2 `percentage.value`;
  RR `rate`; HRV `heartRateVariabilityMillis` (RMSSD); filter `time in [start, end)`, sorting.
- `HealthPermissions.kt:15-93`: split into REQUIRED / OPTIONAL / SPECIAL with feature gating.
- `HealthErrors.kt`: `RetryPolicy.DEFAULT` 1, 2, 4, 8, 16 s; SecurityException → PermissionDenied; on API 34+
  `HealthConnectException` arrives as `IllegalStateException` with a cause, code 7 (rate limit) and 8 (sync in progress)
  → QuotaExceeded with retry, 9 → Unavailable; RemoteException/IOException → Transient. On Android 9–13 the rate limit
  looks like a RemoteException.
- `BackgroundRuleHealthRepository.kt:20-142` + `pipeline/HealthAccessGuard.kt:51-75`.
- testFixtures (`data-hc/src/testFixtures`): `FakeHealthRepository` (filter by start with 24 h padding, source
  filter, history limit via configurable `historyStartMs`, both background behaviors THROWS / SELF_ONLY),
  `testutil/TestData.kt`. `FakeHealthConnectClient` is not in testFixtures but in `data-hc/src/test`.

**Key HC rules that must be carried over exactly**:
- HC filters by record **start time**, not by overlap: `start >= filter.start AND start < filter.end`.
  Hence HR and steps series are read with 24 h left padding and then trimmed by sample time
  (`docs/health-connect.md:159-172`). [from official documentation AOSP RecordHelper.java, as quoted
  in PulseStages; the AOSP code itself was not re-read]
- Sleep is read with ±1 day padding (`NightWindowReader.OVERLAP_MS = 24h`). [verified in code/docs]
- The page loop stops on a `null` **or empty** token: the APK path (Android 9–13) returns `""`, and sending `""`
  back throws NumberFormatException (`health-connect.md:176-179`). [verified in code/docs]
- An empty `dataOriginFilter` means "all apps". An empty package set must be returned as an empty result,
  not as an unfiltered query (`HealthConnectRepository.kt:409-410`). [verified in code/docs]
- Background reading: without `READ_HEALTH_DATA_IN_BACKGROUND` a background app can read only its own data.
  HC from android15-release throws SecurityException, while android14-release **silently** replaces the source filter with
  the app's own package and returns empty (`health-connect.md:195-199`). [from official documentation AOSP
  HealthConnectServiceImpl, as quoted in PulseStages] Without the guard, an empty read would enter the baseline as "no
  data". The guard is mandatory: background without permission → outcome Blocked, not "no night". [verified in code/docs]
- "Foreground" = process importance ≤ `IMPORTANCE_FOREGROUND_SERVICE`; if the app went to the background during the call,
  the result is discarded (`BackgroundRuleHealthRepository.kt:31-36, 128-132`). [verified in code/docs]
- Without `READ_HEALTH_DATA_HISTORY`, HC silently hides other apps' data older than 30 days before the first permission grant
  (`health-connect.md:65` — permissions table, details in §3.4 `:181-191`). For a 28-night baseline this means that right after installation at most ~30
  recent nights are available. [from official documentation https://developer.android.com/health-and-fitness/health-connect/read-data]
- PulseStages does not call `aggregate()`. `readRecords` returns overlapping sessions from all apps;
  deduplication by HC priority exists only in `aggregate()`.
  [from official documentation https://developer.android.com/health-and-fitness/health-connect/aggregate-data]
- Quotas (AOSP RateLimiter, android16-qpr2, flag values): reads 1000 / 15 min and 8000 / 24 h in background,
  2000 / 16000 in foreground; each page is one call. One night costs ~2–9 reads, well within quota.
  [from official documentation https://developer.android.com/health-and-fitness/health-connect/rate-limiting]

**NightBaseline permissions** [hypothesis: design decision]:
- required: `READ_SLEEP`, `READ_HEART_RATE`;
- needed for the night completeness rule: `READ_STEPS` (checking a step minute after waking, see 2.5);
- v1 metrics, requested but may be absent: `READ_RESPIRATORY_RATE`, `READ_OXYGEN_SATURATION`;
- pluggable: `READ_HEART_RATE_VARIABILITY`, `READ_SKIN_TEMPERATURE` (behind feature `FEATURE_SKIN_TEMPERATURE`);
- special with gating: `READ_HEALTH_DATA_IN_BACKGROUND` (critical for the daily background run),
  `READ_HEALTH_DATA_HISTORY`;
- **no WRITE** in the app. The only test write is in androidTest (see 2.12).
- `SkinTemperatureRecord` stores deltas relative to an optional `baseline`.
  [from official documentation https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/SkinTemperatureRecord]

**HC manifest** — as is [verified in code/docs]:
- all health permissions under `<manifest>`, not under `<application>` (`AndroidManifest.xml:14-26`, checked by a test);
- `PermissionsRationaleActivity` with `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 9–13);
- `activity-alias ViewPermissionUsageActivity` with `VIEW_PERMISSION_USAGE` + category `HEALTH_PERMISSIONS` and
  `android:permission="android.permission.START_VIEW_PERMISSION_USAGE"` (`:108-121`). Comment: without it Android 14+
  does not grant permissions, and Android 16 revokes them.
  [from official documentation https://developer.android.com/health-and-fitness/health-connect/get-started and
  https://developer.android.com/about/versions/16/behavior-changes-16]
- `<queries>` with `com.google.android.apps.healthdata`, OHealth and intent queries for both rationale intents, without
  `QUERY_ALL_PACKAGES`. We do not need `com.urbandroid.sleep`.

**Default data source.** HR and other metrics are read only from the package that wrote the sleep session
(`hrSources = SAME_AS_SLEEP`: rule in `NightInputReader.kt:128-133`, default value in
`data-local/…/settings/AppSettings.kt:57`; the same package set goes into the SpO2/RR/HRV reads, `NightInputReader.kt:87-112`). Otherwise Sleep as Android mixes in its own 10-minute
HR and SpO2 averages (`health-connect.md:1079-1083`). [verified in code/docs] For NightBaseline this is the default
rule. [hypothesis]

### 2.5 OHealth behavior

Everything below is the PulseStages author's observations: OHealth 4.62.7, OPPO Watch X3, Android 16 / ColorOS 16, October 17–21,
2026, n = 3–4 nights, plus decompiled OHealth code. In the PulseStages documents this is recorded
[verified in code/docs]; **for NightBaseline it is [hypothesis] until re-observed** (section 5).

**A night in several uploads** (`docs/health-connect.md:703-709, 822-840`; `PLAN.md:792-799`):
- first a preliminary session, cut off at a nearly fixed early-morning clock time while the person is still asleep
  (every observed night); on one night an intermediate version a few hours later; on two nights the final arrived a
  few minutes after actual waking, but on another up to about 3 h after session end (`SourceSettlement.kt:472-477`),
  so "a few minutes" is not a rule;
- the **same uuid** is updated with a later end;
- OHealth rewrites the session even without time changes: new version, same clientRecordId, no stages;
  `lastModifiedTime` changes even if start and end stay the same (`SourceSettlement.kt:18-19`);
- the next day's morning upload may bring late HR for the previous night.

**When OHealth uploads** (`health-connect.md:833-872`):
- writing to HC is a side effect of saving data from the watch, there is no schedule; uploads happen on watch reconnect,
  the watch reporting "woke up", opening the OHealth main screen, pull-down on the "Home" tab;
- the 2-hour background upload in OHealth code **does not work** (the service is not declared in the manifest);
- the full night and post-wake HR often arrive only when OHealth is opened, sometimes after ~6 h;
- no app can silently trigger an OHealth upload (caller signature check);
- HR about once every 2 minutes; steps only from activity data, one StepsRecord per minute with ≥1 step.

**SpO2, RR, HRV from OHealth**: "OHealth currently writes none of these at night" (`ALGORITHM.md:131-132`),
"OHealth writes neither RMSSD nor raw motion" (`ALGORITHM.md:2567`), the question about SpO2/RR at night is open
(`PLAN.md:826`). [verified in code/docs] Consequence: with the default source it may turn out that only sleeping HR
and duration exist, and the "≥2 metrics" rule would rest on exactly two. This is the main product risk; check it
with a `tools/hc-export.sh` export. [hypothesis]

**"Night complete"** (`hc/pipeline/SourceSettlement.kt:145-235, 340-394, 468-516`) [verified in code/docs]:
- in scope if `max(end, lastModified) > now − 12 h` (`SETTLE_TIMEOUT_MS`);
- HR rule: the source package has an HR sample at a time ≥ `max(end + mergeGapMin + 30 min, lastModified + 5 min)`
  (by default end + 60 min); samples later than now + 5 min are ignored (`CLOCK_TOLERANCE_MS`);
- if there is no HR inside the session at all — the session has not changed for `QUIET_MS = 60 min`;
- steps rule for a source with per-minute steps: after the threshold there is a record with ≥ 5 steps/min
  (`AWAKE_STEPS_PER_MIN = 5`, `STEPS_LOOKBACK_MS = 7 d`). This guards against an "HR only" upload during a back-to-sleep period,
  in which the early-morning cutoff would look final;
- the margin cannot be reduced: the back-to-sleep fragment arrived after gaps of 21–36 min, and OHealth needs 20–27 min to
  report a sleep period; that leaves 3–11 min (`SourceSettlement.kt:479-484`, `PLAN.md:410-411`; in
  `health-connect.md:755` the same 20–27 min are called session length — sources disagree, we trust the code and PLAN);
- session version = `uuid:start:end:lastModified`.

For NightBaseline [hypothesis: design decision, ADR]: take the HR rule, the steps rule and the constants with their
rationale; drop the ledger, Sleep as Android, the 15-min confirmation streak. Consider the night complete when the
HR rule holds (+ steps for sources with per-minute steps), or 12 h after `max(end, lastModified)`. If the session version
changes later — recompute the night and, if needed, update the notification (tag = night date).

**Night assembly, main night, naps** (`hc/pipeline/NightCandidates.kt:188-275, 390-458`) [verified in code/docs]:
- dedup by uuid, zero-length sessions dropped;
- fragments from one package are merged while the gap < `mergeGapMin = 30` min; night end = the latest fragment end;
- OHealth starts a new session after > 20 min without sleep data; in the author's history gaps < 4 h were 21–38 min, then
  126 and 195 min, nothing between 39 and 125 min; an experiment with a different `mergeGapMin` gave nothing, 30 min stayed
  (`ALGORITHM.md:2148-2160, 2238-2241`);
- a group < 90 min is ignored, 90–180 min is a nap, ≥ 180 min is a night;
- **PulseStages has no "main night of the day" selection**: each group is its own candidate;
- **naps are not cut out of the night**: they are separate OHealth sessions, classified by length only; nap HR
  is excluded from the baseline (higher nadir);
- cross-source duplicates: overlap > 50% of the shorter → one sleep, priority wins.

For NightBaseline [hypothesis]: dedup by uuid → merge fragments with gap < 30 min → groups < 180 min do not enter
the baseline → "night of the day" = the longest merged group ending in the morning (this is our rule, PulseStages
does not have it). Separately: PulseStages sessions (`io.github.alien2003.pulsestages`, clientRecordId `ps:…`) are always
excluded, see section 4.

**Time zones** [verified in code/docs]: for the written session, offsets are taken from the first fragment (start) and the
last by end (end); if absent — the offset already stored in the ledger for that night, otherwise the device offset
at that moment (`health-connect.md:221`, `ZoneOffsetProvider.kt`); changing the phone's zone does not rewrite
history. The daily run is local time with DST taken into account (`DailySchedule.nextRunMs`). Allowed watch/phone
clock skew is 5 min.

**Resting HR at 00:00 from OHealth**: not mentioned in PulseStages at all. [verified in code/docs: absence] For us
this is a question for the export; such a record must not be confused with sleeping HR, which we compute ourselves. [hypothesis]

### 2.6 Interchange format and CLI

**PulseStages format** (`docs/INTERCHANGE.md:7-27`) [verified in code/docs]:
- `*.night.json` (one object per file) or `nights.jsonl`;
- fields `id, subject, variant, sessionStartMs, sessionEndMs, hr [[timeMs,bpm]], steps [[start,end,count]],
  spo2 [[timeMs,percent]]`, optional `hrv`, `respiratoryRate`, `forcedAwake`, `baselineNadirBpm`, `labels`;
- time in epoch ms, units bpm, %, ms, breaths/min;
- **no `schemaVersion` field** on the night (versions exist only on the model bundle and personal model).

**CLI** (`algo-cli/…/Main.kt:14-46`, `INTERCHANGE.md:31-71`) [verified in code/docs]:
- `run(args, out, err): Int` — tested without `exitProcess`;
- exit codes 0 / 2 / 64 usage / 65 data / 66 no input / 74 IO; exception → one line, no stack trace;
- `evaluate` runs in parallel over nights, result does not depend on scheduling; `synthesize` for smoke tests;
- launched from Python as a subprocess, built once (`installDist`).

**Decision** [hypothesis]:
- the NightBaseline dataset format is described in `docs/DATASET.md` (stage 1b) and is already partly defined by the
  `tools/hc-exporter` export (JSONL). From day one — `schemaVersion`; metrics — an open map
  `{"metricId": {...}}`, since metrics are pluggable; units in the field name or in the document;
- raw exports only in `.data/` (rule 5); synthetic fixtures go in the repository;
- CLI `:baseline-cli` (`replay --nights <dir> --out report.json`, `synthesize`) — only if we need to run
  `.data/` through the same code as on the phone. Take the `build.gradle.kts` pattern (`application`) and the `Main.kt` dispatcher.

### 2.7 WorkManager and notifications

Source: `work/…/schedule/WorkRequests.kt`, `WorkManagerWorkScheduler.kt`, `worker/*`, `notify/*`,
`docs/architecture.md:621-678`. [verified in code/docs]

**Work**:
- daily `estimate-daily`: 24-hour periodic with `setInitialDelay(next − now)` and
  `setNextScheduleTimeOverride(next)`; `DailySchedule.nextRunMs` accounts for DST; after each non-retry run
  `realignDaily()`; re-pinning on drift > 30 min (`DRIFT_TOLERANCE_MS`);
- default `dailyTimeMinutes = 540` (09:00) plus an hourly check;
- deferred one-time work (`sleep-as-android-follow-up`) — the "check later while the night is not complete" mechanism;
- user-triggered run — expedited with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, foreground service `dataSync`, so that HC
  reads count as foreground;
- all requests: `BackoffPolicy.EXPONENTIAL` 30 s, common tag and work tag; schedule tag
  (`schedule:periodic:60min:batteryNotLow=true`) → on settings change `ExistingPeriodicWorkPolicy.UPDATE`;
- constraint `setRequiresBatteryNotLow`.

**Outcomes** (`run/WorkResults.kt:51-60`, limits `run/WorkOutcome.kt:124-127`; called from `WorkerSupport.kt:69-79`):

| Outcome | WorkManager result |
|---|---|
| Completed | success |
| Blocked (no permission, HC unavailable, background without permission) | success with reason |
| Retry (quota, transient error) | retry while `runAttemptCount` < 20 |
| Unexpected | retry while < 3 |
| Failed | failure |

Workers are thin `@HiltWorker` adapters over runners that are tested without WorkManager. `PulseStagesApp`
implements `Configuration.Provider` with `HiltWorkerFactory`; `WorkManagerInitializer` is removed from the manifest.

**Notifications** (`notify/SleepAsAndroidNotifications.kt`, `notify/OpenNight.kt`):
- a separate IMPORTANCE_DEFAULT channel, created lazily (channel importance cannot be raised later);
- pure function `allowed(sdkInt, enabled, granted)` + POST_NOTIFICATIONS on API 33+; result
  SHOWN / NOT_ALLOWED / CHANNEL_OFF;
- `notify(tag = <night id>, id)` — a new notification for the same night replaces the old one; `BigTextStyle`, `setAutoCancel`,
  `setOnlyAlertOnce`, `setSilent` on replacement, `setLocalOnly(true)`, `VISIBILITY_PRIVATE` + `setPublicVersion` without
  details;
- tap: explicit immutable PendingIntent on `Intent.makeMainActivity(launcher)` with the night extra, flags
  NEW_TASK | CLEAR_TOP | SINGLE_TOP, request code `id.hashCode()` and not 0; ignores restore and
  `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`; `MainActivity` reads it in `onCreate` and `addOnNewIntentListener`.

**Decision** [hypothesis: design decision]:
- one daily work after the configured morning time + a deferred one-time re-check every 30–60 min until
  12 h after session end (`REPLACE`), no hourly periodic — this is closer to "one computation per day" from CLAUDE.md;
- deviation channel following the pattern above; tag = night date; public version without metrics and values, the private text has
  only metric names and the wording "metrics deviated from your norm", no diagnoses;
- `setLocalOnly` — decide separately (whether to show on the watch);
- we do not take Sleep as Android, the estimator or the model: `saa/`, `estimate/` and `SleepAsAndroidNotifications.kt` are about
  1,930 of the module's 3,980 main-code lines, i.e. roughly half.

### 2.8 Numeric utilities

[verified in code/docs]
- `core-algo/…/numeric/Stats.kt` (90 lines, `internal object`): `median` (mean of the two middle values for even n),
  `percentile` (numpy linear, h = (n−1)·p/100, `:19-33`), `mean`, `populationStd`, `slope`, `windowSlope`, `finite`;
  empty input → NaN, input not mutated. Take as is.
- `preprocess/SignalFilters.kt:13-92`: Hampel (window 5, threshold 3, `MAD_SCALE = 1.4826`, floor `minScale` 1 bpm for
  integer data, where MAD = 0 is common), `runningMedian`, `movingAverage` without smoothing across gaps.
  Take for cleaning raw HR.
- Preprocessing (`ALGORITHM.md:85-105`): range [30, 180] bpm, duplicate timestamps → mean, Hampel, 60 s epochs
  (mean, interpolation when neighbors ≤ 10 min), median 3 + mean 5. A good template for "sleeping HR".
- `features/NightStatistics.kt:35-62`: P10, P50, nadir of smoothed HR within the session.
- **Stats has no MAD, IQR, Qn/Sn or robust z** — we write our own `RobustStats` (median, MAD·1.4826, when MAD = 0 a floor based on
  the metric resolution, analogous to `minScale`; fallback IQR/1.349). [hypothesis]
- For "sleeping HR", use a low percentile (P10) or the median of smoothed HR, not the nadir: the nadir is a single extreme
  point, sensitive to artifacts. [hypothesis]
- `LogMath`, `SpecialFunctions` (logGamma, gamma functions for HSMM) are not needed.
- Baseline precedent: `data-local/…/db/LedgerDaos.kt:97-145` — median of nadirs of the last N nights, minimum 3
  (`NightBaseline.MIN_NIGHTS`), sessions shorter than `napMode.maxSessionMin` = 180 min are not counted. N = 14
  (`AlgoParams.kt:135`, `baselineNights`), "elevated" flag when nadir > baseline·1.12 (`elevatedNightPct = 12`,
  `AlgoParams.kt:137`, check in `features/NightStatistics.kt:53`). Supports the idea, but a fixed +12% is cruder than our median + robust
  spread and minimum of 7 nights. [verified in code/docs] / [hypothesis for the assessment]
- The `ParamsHasher` idea: a hash of the settings and the set of available channels affecting the result is stored with the night; on change
  the night is stale. PulseStages includes in the hash which optional channels were read (`health-connect.md:68-69`).
  Take as a pattern. [verified in code/docs]

### 2.9 Synthetic night generator

[verified in code/docs] `core-algo/…/synthetic/SyntheticNightGenerator.kt` (401 lines):
- `SyntheticNightGenerator(seed)` on `kotlin.random.Random(seed)`; `SyntheticOptions` (`:33-50`): duration,
  long gap, constant, single sample, steps, `dropRate = 0.03`, `outlierRate = 0.01`, `person`;
- `SyntheticPerson` (`:62-80`) is separated from the random stream: one can make a "shifted person" with the same noise;
- HR = baseline + stage shift + drift + lag + AR(1) noise (φ 0.8, σ 1.2), samples every 120 ± 10 s, integer bpm;
- the docs state explicitly: synthetic data is only for regression guards, not for choosing defaults
  (`ALGORITHM.md:2518-2545`).

Decision — pattern only [hypothesis]: `SyntheticHistoryGenerator(seed)` in `:core-baseline` testFixtures, N nights
with raw samples or summaries for HR, RR, SpO2, duration (+ HRV, temperature), person profile separate from
noise, options: injected deviation (e.g. +6 bpm and +2 breaths/min on night k), gaps, missing metrics,
short nights, relocation/DST, fragments and naps. The same generator feeds demo mode and screenshots.

### 2.10 Testing approach

[verified in code/docs] (`docs/architecture.md:960-1053`)
- core: JUnit 5 only; Android modules: JUnit 5 for logic, Robolectric via Vintage for Room, WorkManager
  (`TestListenableWorkerBuilder`) and the Hilt graph;
- `StatsTest` is checked against numpy by hand (`percentile([1,2,3,4],10) = 1.3`);
- property tests without a library: 200 seeded nights × random parameters, `Invariants.check`, determinism
  (`OutputPropertiesTest.kt:19-140`, `Invariants.kt:11-60`);
- regression threshold = first observed mean − 0.05 (`SyntheticRecoveryTest.kt`);
- `EdgeCaseTest.kt:32-260`: single sample, all values equal, midnight and DST, gap across midnight, exactly 90 min,
  outliers, no HR, low coverage;
- `RealAppGraphTest`, `EndToEndWiringTest` (real graph + fake HC), `OpenNightActivityTest`;
- structural tests: `MergedManifestTest` (no INTERNET, permissions, singleTop, queries, alias, one launcher),
  `ReleaseApkTest` (no demo classes or strings in release), `ResourcesParityTest` (en/ru and placeholder parity),
  `ResourceFormatsTest`;
- Room migrations: `exportSchema = true`, the test builds a version 1 DB from JSON and migrates, no destructive fallback.

For NightBaseline [hypothesis]: the same layers; baseline invariants over many seeded histories: no output with < 7
valid nights, baseline finite, robust z finite, result idempotent and deterministic, a missing metric does not produce
a deviation, an injected deviation in ≥ 2 metrics is detected, in one — not.

### 2.11 On-device e2e with Health Connect safeguards

[verified in code/docs] (`tools/device-test/README.md:72-170`, `run-device-tests.sh`, `app/src/androidTest`)
- runner: `ADB="${ADB_WIN:-adb.exe}"`, every call `timeout --kill-after=10 … </dev/null`, stripping `\r`;
  installs only the test APK (`install -r -t`), refuses to uninstall another package, checks the versionCode and certificate
  of the installed app; each step is a separate `am instrument -w -r -e class …`;
- order: SnapshotBefore → ShadowPipeline → RealWrite → UiTour → SnapshotAfter → Compare; trap on EXIT/INT/TERM/HUP
  with cleanup up to 3 times (pauses 60 and 900 s);
- `SafetyGuardRepository`: writes and deletes pass only if every clientRecordId starts with `ps-e2e:`,
  otherwise `SafetyViolation` (an `Error`, so `catch (Exception)` will not swallow it); deletion only by clientRecordId,
  never by time range;
- before/after snapshots: own records byte for byte; others' — per package count + SHA-256 over (id, lastModified); settings files;
- E2eRunner installs an in-memory WorkManager before `Application.onCreate`;
- `ReadBudget`: 600 reads / 15 min and 4000 / run, accounting for the last 24 h;
- one real write: the clientRecordId is recorded on the phone **before** insertion, lifetime < 60 s, deletion in `finally`
  and in the runner cleanup;
- runner selftest on a fake adb (`selftest/fake-adb`) via pty, ~3 min.

Decision — adapt at stage 4 (rule 6) [hypothesis]:
- one write of a type we already read (e.g. `HeartRateRecord` or `RespiratoryRateRecord` 30+ days
  ago), clientRecordId `nb-e2e:<uuid>`, check that our reader ignores its own package, delete by id;
- guard repository, snapshots, read budget, trap with retries — as in PulseStages; ShadowPipeline is simpler since the
  app does no writing;
- the device serial number in `out/` file names (`.read-history-<serial>.tsv`) — hash it (rule 11 of the current
  CLAUDE.md, personal data; rule 10 is now phone access only with permission);
- **copy nothing from PulseStages `tools/device-test/out/`**: it contains real device logs and health data
  (the directory exists, its contents were not opened).

### 2.12 detekt and ktlint

[verified in code/docs] Root `build.gradle.kts:29-74`, `config/detekt/detekt.yml` (62 lines):
- ktlint on all projects (`android.set(false)`, `/build/` excluded); detekt on subprojects:
  `buildUponDefaultConfig`, `parallel`, `source = src` (all source sets), html + xml reports, `jvmTarget = 17`,
  Kotlin for the `detekt` configuration pinned to 2.0.21 via `resolutionStrategy`;
- `warningsAsErrors: true`, no baseline; `LongParameterList` 6/8, `LongMethod` 60, `@Composable` ignored;
  `MagicNumber` outside tests, `.kts` and `ui/theme`; `MaxLineLength` 140; `WildcardImport`, `ForbiddenComment`;
- `public-api.yml` (KDoc on public API) for core only.

Take as is, `public-api.yml` for `:core-baseline`. Mentions of `HealthRepository` and `SleepStage.index` in
config comments are cosmetic, fix them.

### 2.13 CI: ci, build-apks, release

**`ci.yml`** (195 lines) [verified in code/docs]:
- `push` on branches `"**"` (tags go to release), `paths-ignore` by document list; `pull_request` without path filter;
  `workflow_dispatch`; `permissions: contents: read`;
- concurrency: not cancelled on `main`;
- job `changes` (PR only): file list via `gh api --paginate`, `is_doc()` mirrors `paths-ignore`; on an
  incomplete list or > 3000 files — `code=true` (fail open);
- job `build`: checkout `3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1` with `persist-credentials: false`,
  setup-java `de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1` temurin 17,
  setup-gradle `3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0` (`cache-provider: basic`, only main writes the cache),
  extra install of `platforms;android-37.0` and `build-tools;36.0.0`, `./gradlew ktlintCheck detekt test assembleDebug`,
  on main — debug APK as an artifact for 2 days (upload-artifact `cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2`),
  on failure — reports for 7 days;
- job `python` — PulseStages only.

Adapt: remove `python` (or replace it with a check of `tools/hc-dataset.py` if tests appear), rename the
artifact to `nightbaseline-debug-apk`, keep `paths-ignore` and `is_doc` in sync. CI must not see health
data: fixtures are synthetic only (rule 5). Re-check action SHAs for updates. [hypothesis]

**`release.yml`** (63 lines) and **`build-apks.yml`** (315 lines, `workflow_call`) [verified in code/docs]:
- release: tags `v*` and `workflow_dispatch` with `dry_run` (default `true`); `permissions: {}`, the release job has
  `contents: write`;
- secrets (names only): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, optional
  `DEBUG_KEYSTORE_BASE64`;
- version from tag (`.github/scripts/version-from-tag.sh`): `versionCode = MAJOR·1000000 + MINOR·10000 + PATCH·100 + S`,
  S = N (alpha), 30+N (beta), 60+N (rc), 99 (final); v0.1.0 → 10099; dry run = `0.0.0-dry.<run>` / 1;
- Gradle without cache and without configuration cache (otherwise it would store signing values);
- key decoding with `umask 077`, `keytool` check, key deletion in `if: always()`;
- APK checks: signed, `apksigner verify`, release signer is not `CN=Android Debug`, release and debug certificates
  differ, `aapt2 dump badging` matches the version;
- `SHA256SUMS.txt`, notes = CHANGELOG section (`changelog-section.sh`) + downloads table + certificate SHA-256s;
  the release APK is never uploaded as an artifact.

Adapt: rename `pulsestages-release.jks`, `PulseStages-$VERSION_NAME.apk`, `-debug.apk`, the release
title, env `PULSESTAGES_*` → `NIGHTBASELINE_*` (4 env mentions in `build-apks.yml`, lines 118 and 142-144; in total
the word "pulsestages" in any case occurs there 17 times — rename all). Release and debug keys are
**new**, stored in `.secrets/` (rule 7), not in `~/keys` as in `development.md:507-541`. Release texts contain
no health data (rule 5). Scripts `version-from-tag.sh`, `changelog-section.sh` and `dependabot.yml`
(github-actions, monthly, `cooldown.default-days: 7`) — as is.

### 2.14 Obtainium button and import

[verified in code/docs] (`README.md:24-25, 152, 156-157, 332-335`; `docs/user-guide.md:84-135, 1461-1463`;
`docs/development.md:418-448`)
- markup: `[![Get it on Obtainium](docs/get-it-on-obtainium.svg)][obtainium-import]` + link to the latest release;
  in user-guide the image path is relative;
- link: `https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/` + `encodeURIComponent(JSON)`; GitHub
  strips bare `obtainium://`, hence the web redirect is needed
  [from official documentation https://wiki.obtainium.imranr.dev/deep_links/];
- JSON: `id`, `url`, `author`, `name`, `additionalSettings` — a string containing JSON:
  `includePrereleases: false`, `apkFilterRegEx`, `allowedSigningCertHashes`;
- generator in `development.md`: `json.dumps(separators=(",",":"))` + `urllib.parse.quote(safe="-_.!~*'()")`;
- the README honestly states that the redirect page loads ads and analytics;
- `docs/get-it-on-obtainium.svg` (1149 bytes) — original artwork, without the project name, copied as is.

For NightBaseline: `id` = `io.github.alien2003.nightbaseline`, `url` = `https://github.com/alien2003/NightBaseline`,
`name` = `NightBaseline`, filter `^NightBaseline-[0-9.]+\.apk$`, `allowedSigningCertHashes` — SHA-256 of **our**
release certificate (will appear once the key is created). Do not reuse the PulseStages hash. The README button only
by stage 5 (the repository is private for now). [hypothesis for timing]

### 2.15 Screenshots from synthetic data

[verified in code/docs] (`docs/SCREENSHOTS.md`, `app/src/testDebug/…/screenshots/`, `app/build.gradle.kts:165-215`)
- Robolectric native graphics + Compose test rule (not Roborazzi, not Paparazzi);
- `@GraphicsMode(NATIVE)`, `@Config(application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-port-xhdpi")`
  (Hilt and WorkManager do not start), UTC, `Locale.US`, fixed clock;
- `ScreenHost.show()` → `Robolectric.buildActivity(ComponentActivity)` → `setContent { Theme(dynamicColor=false) { AppShell } }`
  → `waitUntil(ready)` → `captureToImage()` → PNG;
- by default into `app/build/screenshots` (every test run is a smoke test), with `-PrecordScreenshots` into `docs/screenshots`;
- no comparison against a reference;
- `ScreenConfig`: ru/en, font scale 2.0, wide window; `assertTexts()` catches unfilled `%1$s` and line breaks in buttons;
- data: ViewModel built by hand with fakes; demo sources only in `src/debug` on `SyntheticNightGenerator` with fixed
  seeds; `DemoModeStore` in a separate prefs file; `ReleaseApkTest` proves there is no demo code in release;
- Robolectric native graphics on JDK 17 — SDK 35 only.

Decision — adapt almost verbatim: this directly implements rule 5. Own `DemoData`: ~28 synthetic nights with
2–3 injected deviations, to show the states "≥ 2 metrics deviated", "< 7 nights", "metric unavailable".
Theme and shell are our own (PulseStages design is not a model). [hypothesis for the set of states]

### 2.16 Documentation style

[verified in code/docs]
- README: centered icon, `# Name`, CI badge, links line, `## TL;DR`, screenshots, quick start, features,
  documentation, status, license, trademark line;
- `docs/README.md` — index by reader ("Using the app" / "How it works" / "Working on the code");
- CHANGELOG per Keep a Changelog 1.1.0 + SemVer 2.0.0, intro paragraph for each version, absolute links (the section
  becomes the release notes);
- CONTRIBUTING: Conventional Commits `<type>(<scope>): <summary>`, scopes = modules;
- SECURITY: latest version only, private advisory, signing key policy, download verification
  (`sha256sum -c`, `apksigner verify --print-certs`), pipeline description;
- "every claim verifiable in code or a source; mark unverified ones" (CONTRIBUTING:136-143);
- one authoritative page per topic, others link to it (stated explicitly only for algorithm numbers:
  `CONTRIBUTING.md:141`, `docs/README.md`); wrapping at ~120 columns is an observation from the files, there is no such rule
  [hypothesis].

We take the pattern. Differences: CLAUDE.md and research are in Russian; the README/UI language is a separate decision; the README and
app must carry a "not a medical device" disclaimer; do not use the word "Vitals". [verified in code/docs:
NightBaseline CLAUDE.md]

### 2.17 Package id and renames

- Our package is `io.github.alien2003.nightbaseline`, modules `io.github.alien2003.nightbaseline.<module>`. [verified in
  code/docs: CLAUDE.md]
- Rename when copying: `rootProject.name`, namespace/applicationId, test runner, system properties
  `pulsestages.*`, env `PULSESTAGES_*`, APK and `.jks` names in CI, CI artifact, issue/PR templates, logcat tag,
  `proguard-rules.pro` header, URLs in README/SECURITY, e2e prefix `ps-e2e:` → `nb-e2e:`, DB name, demo-mode prefs,
  notification channels. [verified in code/docs]
- New, not copied: signing keys, certificate hash for Obtainium, CHANGELOG history. [verified in code/docs]

---

## 3. Reuse mechanism

**Decision: copy sources and adapt. No dependency on PulseStages artifacts** (no Gradle
included build, no published libraries, no git submodule, no symlink to its files). [hypothesis:
design decision, recorded in an ADR]

Rationale:
1. **Rule 9**: PulseStages is read-only, builds in it are forbidden. An included or composite build
   would run Gradle over its tree and write into its `build/` and `.gradle/`. [verified in code/docs: CLAUDE.md]
2. **No published artifacts**: PulseStages modules are not published to Maven, `:core-algo` contains `internal`
   objects (`Stats`), and `data-hc` is tied to its own `data-local` types via `api(...)`. We would have to depend on the whole
   graph for a few files. [verified in code/docs]
3. **Different domain**: the share of useful code is small and all of it needs editing (HC writing, Sleep as Android, stages,
   ledger are removed). A shared library would drag in foreign requirements. [verified in code/docs] / [hypothesis for
   the share estimate]
4. **Independent releases and signing**: each app has its own key, versions and CI; coupling via artifacts
   would break build reproducibility when PulseStages changes. [hypothesis]
5. **The license allows it**: the same author owns both projects. [verified in code/docs]
6. **No git history**: files are copied as new code (no `git subtree`/`cherry-pick`), so as not to drag in history,
   CHANGELOG and mentions of others' data. [hypothesis]

Copying procedure:
- copy a file, immediately rename the package and strings, remove the excess, run ktlint/detekt/tests;
- write nothing about provenance at the top of the copied file (the code is self-contained); record the source
  in the commit message and in this report;
- copy nothing from `.tooling/signing`, `tools/device-test/out/`, `tools/train/data|out`, keys and `*.b64`;
- copy by reading via `cat`/`cp` from PulseStages into NightBaseline; no commands that write into PulseStages.

---

## 4. PulseStages sleep stages as input

**What PulseStages writes** [verified in code/docs]:
- its own `SleepSessionRecord` with dataOrigin `io.github.alien2003.pulsestages`, on top of the source session,
  possibly 1 s earlier (`write.startBeforeSource`);
- stages AWAKE=1, LIGHT=4, DEEP=5, REM=6, UNKNOWN=0 (`StageMapping.kt:10-62`); naps by default only
  AWAKE=1/SLEEPING=2; `write.awakeAsLight` writes awakenings as LIGHT (in naps as SLEEPING);
- clientRecordId `"ps:" + sha256(sorted source uuids)`; notes JSON with `algo`, `mode`, `version`,
  `paramsHash`, `coverage`, `confidence`, `sourceUuids`; `Metadata.unknownRecordingMethod`;
- accuracy: LOSO on 31 PSG nights, κ = 0.339 ± 0.169, per-night error DEEP ~33 min, REM ~41 min
  (`ALGORITHM.md:44-62`).

**Conclusion: in v1, PulseStages stages are not used as a metric.** [hypothesis]
- **Circular dependency**: DEEP is derived mostly from HR being lowest and steadiest relative to P10/P50 of the same
  night. "HR in deep sleep" ≈ a low HR percentile of the same night, which we already compute from raw data.
  [verified in code/docs for the method; hypothesis for the conclusion]
- **Per-night normalization** (hrNorm) and shifting the fractions toward population values mean that an illness-related HR rise does not change
  the DEEP fraction predictably — a deviation detector based on stage fractions would be weak. [verified in code/docs for the method]
- **Noise**: an error of 33–41 min per night is probably larger than the effects we are looking for. [hypothesis]
- **Version drift**: records are recomputed with a new `paramsHash`/`algoVersion`; a 14–28-night baseline would mix versions.
  [verified in code/docs]
- **Vendor neutrality**: coupling with a neighboring app is an extra mode to test.

**Mandatory regardless of the decision** [hypothesis, strongly recommended]: PulseStages sessions are night duplicates.
The session reader filters by source package (OHealth by default) and always excludes dataOrigin
`io.github.alien2003.pulsestages` and clientRecordId with the `ps:` prefix, otherwise duration doubles or the
wrong session gets picked. The same for NightBaseline's own records (the app has none, but tests have `nb-e2e:`).

**Later and optional** [hypothesis]:
- a general "stages present" metric (DEEP/REM minutes) for information only, **not** counted in the "≥ 2 metrics" rule,
  and only from sources writing measured stages (not `unknownRecordingMethod`);
- for PulseStages one soft use is acceptable: excluding AWAKE epochs from the "sleeping HR" window. First check the gain over
  the percentile on synthetic data;
- the stage parser must accept SLEEPING=2 and UNKNOWN=0.

---

## 5. What to re-check on the device

Everything below is [hypothesis] for NightBaseline until confirmed by a `tools/hc-export.sh` export or an on-device test.

1. Whether OHealth writes `OxygenSaturationRecord`, `RespiratoryRateRecord`, `HeartRateVariabilityRmssdRecord`,
   `SkinTemperatureRecord` at night; if not — how many metrics are actually available for the "≥ 2" rule.
2. Whether OHealth provides `RestingHeartRateRecord` (e.g. at 00:00) and whether it gets confused with sleeping HR.
3. HR density at night (~every 2 min?) and the structure of `HeartRateRecord` (one point or a series per record).
4. Preliminary early-morning night cutoff, intermediate versions, updates of the same uuid, `lastModifiedTime` change
   without time change.
5. How long after waking the final session, post-end HR and steps arrive; whether OHealth must be opened.
6. Per-minute steps after waking (≥ 5 steps/min) — whether the steps rule works on the current firmware.
7. Gaps between night fragments and naps (21–38 min within a night, ≥ 126 min before a nap) — whether
   `mergeGapMin = 30` and the 180 min threshold hold.
8. Late HR for the previous night in the next morning's upload.
9. Background reading on the current firmware (PulseStages observations are on Android 16 / ColorOS 16, `PLAN.md:31`; per
   NightBaseline `docs/STATUS.md` the phone is now on Android 17, SDK 37): SecurityException or silent filter replacement without
   `READ_HEALTH_DATA_IN_BACKGROUND`; availability of the HISTORY, BACKGROUND, SKIN_TEMPERATURE features.
10. What permissions actually grant (and via adb for the exporter), and what is visible without HISTORY (≤ 30 days).
11. Time zone offsets in OHealth sessions (whether `startZoneOffset`/`endZoneOffset` are set).
12. Whether the phone has PulseStages (`ps:`), Sleep as Android or other apps' sessions
    overlapping the OHealth night.
13. Whether `activity-alias` with `VIEW_PERMISSION_USAGE` works for granting permissions on Android 16 (without it — revocation).
14. WorkManager daily run on ColorOS: whether it fires at the right time, whether battery optimization kills it,
    whether the notification reaches the lock screen only in its public version.
15. Whether the app fails to install over a debug build with a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), versionCode
    for local builds.
16. HC quotas: whether the budget suffices for the first read of 28–30 nights of history (expected yes, ~2–9 reads per night).

---

## Report verification

2026-11-25: every concrete claim (versions, files, lines, numbers, CI steps, thresholds) was checked against the files in
`../PulseStages` by reading only (`cat`/`grep`/`sed`/`wc`/`awk`), without builds and without `.tooling/signing`.
Matched: `.mise.toml`, wrapper and `gradle.properties`, the whole version catalog, `verifyModuleGraph` and
`verifyPureJvmRuntimeClasspath`, manifest, pagination and `PAGE_SIZE`/24 h padding, `RetryPolicy` and codes 7/8/9, background
guard, `SourceSettlement` constants (12 h, 5 min, 60 min, 5 steps/min, 7 d), `mergeGapMin = 30`, thresholds 90/180 min,
WorkManager schedule and backoff, retry limits 20/3, notifications and `OpenNight`, `Stats`/Hampel, synthetic data
(φ 0.8, σ 1.2, 120 ± 10 s), tests (200 seeds, `percentile([1,2,3,4],10) = 1.3`, −0.05), e2e (`ps-e2e:`, 600/4000,
pauses 60/900 s, < 60 s), CI action SHAs, versionCode formula (v0.1.0 → 10099), Obtainium, SVG 1149 bytes,
κ 0.339 ± 0.169, DEEP 33.1 / REM 40.8 min. [verified in code/docs]

Corrected:
1. `settings.gradle.kts:27-34` → `:31-38` (the `include` list).
2. `app/build.gradle.kts`: 296 → 295 lines.
3. `ci.yml` 196 → 195, `release.yml` 64 → 63, `build-apks.yml` 316 → 315 lines.
4. "17 mentions of `PULSESTAGES_*`" in `build-apks.yml`: there are 4 env variables (lines 118, 142-144); 17 is all occurrences
   of "pulsestages" in any case.
5. `HealthConnectRepository.kt:410-412` → `:409-410` (empty package set).
6. `NightInputReader.kt:184-187` does not exist (the file has 135 lines): the `hrSources` rule is at `:128-133`, the default
   value in `AppSettings.kt:57`; clarified that the same package filter also goes into SpO2/RR/HRV.
7. `FakeHealthConnectClient` is not in testFixtures but in `data-hc/src/test`; the fake's "30-day limit" is actually
   the configurable `historyStartMs`.
8. Reference for the 30-day history limit: added §3.4 `health-connect.md:181-191` (lines 58-66 are the permissions table).
9. The final OHealth night version "within a few minutes after waking": this held only on two of the observed
   nights; on another the final arrived up to about 3 h after session end (`SourceSettlement.kt:472-477`).
10. "Back-to-sleep fragments lasted 20–27 min": per the code and PLAN, 20–27 min is the time OHealth needs to report
    a sleep period; `health-connect.md:755` says otherwise — discrepancy noted.
11. `PLAN.md:828` → `:826` (open question about SpO2/RR).
12. WorkManager outcomes: mapping in `run/WorkResults.kt:51-60`, limits in `run/WorkOutcome.kt:124-127`, not in
    `WorkerSupport.kt:69-79` (only the call is there).
13. Share of Sleep as Android + estimator in `:work`: not "~a third" but about half (≈1,930 of 3,980 lines).
14. Baseline precedent: `LedgerDaos.kt` has only the median and minimum 3; 14 nights and +12% come from `AlgoParams.kt:135, 137`, the check
    in `NightStatistics.kt:53`; naps are cut off by `napMode.maxSessionMin` = 180 min.
15. Time zones: between the source offset and the device offset there is a "ledger offset" step
    (`health-connect.md:221`).
16. CLAUDE.md rule number for personal data: 10 → 11 (rule 10, phone access only with permission, was added to CLAUDE.md).
17. "Wrapping at ~120 columns" and "one authoritative page per topic" are not PulseStages rules; it is stated explicitly
    only for ALGORITHM.md. Marked as an observation [hypothesis].
18. `.editorconfig`: added the missing `[*.gradle.kts]` and `[*.md]` sections.
19. Nap stages: with `awakeAsLight` awakening is written as SLEEPING, not LIGHT; range `StageMapping.kt:10-62`.
20. Section 5, item 9: PulseStages observations were made on Android 16 / ColorOS 16 (`PLAN.md:31`), while per `docs/STATUS.md`
    the NightBaseline phone is now on Android 17. HC background behavior must be re-checked on that version.

Not verified (these are not PulseStages files): AOSP and developer.android.com quotes (left with source tags) and
the contents of `tools/device-test/out/`, which were intentionally not opened.
