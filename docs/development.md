# Development

How to set up, build, test and release NightBaseline, and the rules the code base follows. Contribution rules:
[CONTRIBUTING.md](../CONTRIBUTING.md). Architecture decisions: [docs/adr/](adr/). The algorithm:
[ALGORITHM.md](../ALGORITHM.md).

- [Requirements](#requirements)
- [One-time setup](#one-time-setup)
- [Build, test and lint](#build-test-and-lint)
- [Modules](#modules)
- [Screenshots](#screenshots)
- [Health data, the dataset exporter and algo-cli](#health-data-the-dataset-exporter-and-algo-cli)
- [Running on a phone](#running-on-a-phone)
- [Continuous integration](#continuous-integration)
- [Releases](#releases)

## Requirements

- Linux, or WSL 2 (Ubuntu) on Windows. Keep the checkout in the Linux file system (`~/…`), not under `/mnt/c`.
- `git`, [mise](https://mise.jdx.dev/) ([Getting started](https://mise.jdx.dev/getting-started.html)), plus `curl`,
  `unzip` and `sha1sum` for the SDK download below.
- For installing on a phone from WSL: `adb.exe` on Windows ([Running on a phone](#running-on-a-phone)).

Nothing is installed system-wide. mise keeps the JDK and Python in its per-user data directory; everything else lives
in the git-ignored `.tooling/` folder of the project:

| What | Where |
|---|---|
| Temurin 17 (JDK) and Python 3.12 | `.mise.toml` `[tools]`, installed by mise |
| Python virtual environment | `.tooling/venv` (created by mise) |
| Android SDK | `.tooling/android-sdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) |
| Gradle user home (wrapper distribution, caches) | `.tooling/gradle` (`GRADLE_USER_HOME`) |
| Android user state (debug keystore, adb keys) | `.tooling/android-user` (`ANDROID_USER_HOME`) |
| `sdkmanager` and `platform-tools` on `PATH`, `ADB_WIN=adb.exe` | `.mise.toml` `[env]` |
| Gradle | the wrapper `./gradlew` (Gradle 9.8.1) |
| Library and plugin versions | `gradle/libs.versions.toml` |

Run every command through `mise exec -- …` (or inside `mise exec -- bash`) so that the project's JDK, SDK and
environment variables are used.

Toolchain: AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37 (platform `android-37.0`), targetSdk 36,
minSdk 28, JVM target 17 ([ADR 0001](adr/0001-architecture-and-reuse.md)).

## One-time setup

```sh
git clone https://github.com/alien2003/NightBaseline.git ~/NightBaseline
cd ~/NightBaseline
mise trust          # only if mise asks to trust the project's .mise.toml
mise install        # Temurin 17 and Python 3.12

# Android command-line tools (official zip; SHA-1 from Google's repository2-3.xml)
mkdir -p .tooling/dl .tooling/android-sdk/cmdline-tools
curl -fL -o .tooling/dl/cmdline.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
echo "e025545c62a8e64c7559119566a569fb1dec5f60  .tooling/dl/cmdline.zip" | sha1sum -c -
unzip -q .tooling/dl/cmdline.zip -d .tooling/dl/extract
mv .tooling/dl/extract/cmdline-tools .tooling/android-sdk/cmdline-tools/latest

# SDK packages
yes | mise exec -- sdkmanager --licenses
mise exec -- sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "build-tools;36.0.0"

# English-only check before every commit
git config core.hooksPath tools/githooks
```

AGP 9.4 uses build-tools 36.0.0 by default. The Linux platform-tools are not used to install on a phone from WSL.

## Build, test and lint

```sh
mise exec -- ./gradlew :app:assembleDebug                 # debug APK: app/build/outputs/apk/debug/app-debug.apk
mise exec -- ./gradlew verifyModuleGraph ktlintCheck detekt
mise exec -- ./gradlew :core-algo:test :algo-cli:test :data-local:test :data-hc:test :work:test
mise exec -- ./gradlew :app:testDebugUnitTest              # app unit, Robolectric, screenshot and contrast tests
mise exec -- ./gradlew test                                # everything
mise exec -- ./gradlew ktlintFormat                        # fix formatting
bash tools/check-language.sh                               # English only
mise exec -- python3 tools/design/contrast_check.py docs/design/tokens.md
```

| Test | What it checks |
|---|---|
| `:core-algo:test` | Night selection, completeness, extraction, baseline and evaluation; `SyntheticBehaviourTest` (200 synthetic people × 90 nights: false-note rate and episodes found, [ALGORITHM.md §7](../ALGORITHM.md#7-validation)); the classpath purity check |
| `:data-local:test`, `:data-hc:test`, `:work:test` | Room and DataStore, Health Connect mapping and error handling (fakes), the daily run, scheduling and notifications |
| `:app:testDebugUnitTest` | ViewModels, navigation and deep links, layout resolution; `MergedManifestTest` (no `INTERNET` / `ACCESS_NETWORK_STATE` in the debug and release merged manifests); `StringResourcesTest` (en and ru have the same keys); `ContrastTest` (the Kotlin contrast pairs equal the pairs table of `docs/design/tokens.md` and reach their minimum in both themes); the JVM screenshot tests in `src/testDebug` |
| `tools/check-language.sh` | No Cyrillic in tracked files outside `res/values-ru*` and `README.ru.md` ([ADR 0002](adr/0002-project-language-and-history.md)); `--staged` checks the index (pre-commit hook). New files are checked once they are added to git |
| `tools/design/contrast_check.py` | WCAG contrast of every pair in the contrast table of `docs/design/tokens.md`, glass tints over white and black |

The app tests need a lot of memory: Robolectric's native graphics live outside the Java heap. The defaults are a 3 GB
test heap and one JVM for all classes; `-PtestMaxHeap=2g -PtestForkEvery=1` (as CI does) uses less.

Every test runs on synthetic data. Never point a test at `.data/`.

## Modules

| Module | Kind | Responsibility |
|---|---|---|
| `:core-algo` | pure Kotlin/JVM | Metric registry, main-night selection, completeness rule, extraction, robust baseline, evaluation, synthetic nights |
| `:data-local` | Android library | Room database (nights, run log), DataStore settings |
| `:data-hc` | Android library | Health Connect: permissions, availability, source discovery, paged reads, mapping to `:core-algo` types |
| `:work` | Android library | The daily WorkManager chain (08:00, waits for a complete night), history backfill, notifications |
| `:app` | Android application | Compose UI, design layer with Haze blur ([ADR 0003](adr/0003-ui-library-and-blur.md)), adaptive layouts, Hilt wiring |
| `:algo-cli` | JVM application | Offline runs over the local dataset and synthetic nights |
| `:hc-exporter` (`tools/hc-exporter`) | debug app, never released | Developer dataset export |

Allowed project dependencies (anything else fails `verifyModuleGraph`, which also runs before `:core-algo:test`):

```
app        -> work, data-hc, data-local, core-algo
work       -> data-hc, data-local, core-algo
data-hc    -> data-local, core-algo
data-local -> core-algo
algo-cli   -> core-algo
core-algo  -> (nothing; no Android plugin, pure JVM runtime classpath)
hc-exporter -> (nothing)
```

`:core-algo` is shared by the app and `algo-cli`, so offline runs execute exactly the app's code. A new metric needs
a `MetricSpec` in `:core-algo` and a channel mapping in `:data-hc`; the UI handles 2 to 6 metrics.

Style: ktlint and detekt (`config/detekt/`); the public API of `:core-algo`, `:data-local`, `:data-hc` and `:work`
carries KDoc. Strings live in `res/values/strings.xml` with the same keys in `res/values-ru/strings.xml`; the copy and
its tone are defined in [design/states.md](design/states.md).

## Screenshots

The screenshot tests in `app/src/testDebug/kotlin/…/screenshots/` render screens and components on the JVM
(Robolectric, hardware PixelCopy so that blur is captured) from **synthetic data only**. A normal test run writes the
PNGs to `app/build/screenshots/`; CI uploads them as the artifact `screenshots`. With `-PrecordScreenshots` they are
written to `docs/screenshots/` instead:

```sh
mise exec -- ./gradlew :app:testDebugUnitTest --tests '*ScreenshotsTest' -PrecordScreenshots
```

The README shows ten images recorded this way, light and dark, from synthetic nights: `home-light.png`,
`home-dark.png`, `metric-light.png`, `metric-dark.png`, `history-light.png`, `history-dark.png`,
`onboarding-light.png`, `onboarding-dark.png`, `settings-light.png`, `settings-dark.png`. Screenshots of real data
never go into the repository; reference screenshots of other apps stay in `.data/design-refs/`.

## Health data, the dataset exporter and algo-cli

**Health data never leaves the git-ignored `.data/` folder**: not into git, CI, build logs, release texts or
documents. Documents carry aggregates without raw values (for example
[research/metrics-matrix.md](research/metrics-matrix.md)); tests and screenshots use synthetic data.

- **Export** (developer only, needs the phone, see [Phone access](#phone-access)): `mise exec -- tools/hc-export.sh`
  builds and installs the debug exporter `tools/hc-exporter` (READ permissions only, no `INTERNET`), exports the
  readable Health Connect history to its private storage, pulls it to `.data/hc-dumps/<runId>/` and merges it into
  `.data/dataset/` with `tools/hc-dataset.py`. Formats: [DATASET.md](DATASET.md).
- **Summary without values:** `mise exec -- python3 -I tools/hc-dataset.py summary --data .data`.
- **Research scripts** in `tools/research/` print aggregates only:
  `mise exec -- python -I -W ignore tools/research/<script> .data`.
- **algo-cli** runs the algorithm offline:

  ```sh
  mise exec -- ./gradlew :algo-cli:installDist
  algo-cli/build/install/algo-cli/bin/algo-cli synthetic --seed 1
  algo-cli/build/install/algo-cli/bin/algo-cli origins --dataset .data/dataset
  algo-cli/build/install/algo-cli/bin/algo-cli run --dataset .data/dataset --origin com.heytap.health.international
  ```

  `run` prints aggregates only (status counts, notifications, per-metric counts). `--details <file.jsonl>` writes
  per-night results with values: keep that file under `.data/` (for example `.data/out/`). `--params <json>` overrides
  `AlgoParams`. Exit codes: 0 ok, 64 usage, 65 invalid input, 66 missing input.

## Running on a phone

### Phone access

A phone holds real health data. **Never access a phone over adb without its owner's explicit permission for that
specific action**: say what will be done (install, export, screenshots, launching an app, even `getprop` or `dumpsys`)
and how long it takes, then wait for the answer. Never open or drive other apps without separate permission, leave no
persistent changes (stay-awake, temporary files, apps left open), and clean up afterwards. On the phone, Health Connect
is used read-only. Automated agents working in this repository follow the same rule ([CLAUDE.md](../CLAUDE.md)).

### From WSL

On WSL the phone is connected to Windows, so the Linux `adb` cannot see it. Use the Windows `adb.exe` through WSL
interop: `.mise.toml` sets `ADB_WIN=adb.exe`, which works when `adb.exe` is on the Windows `PATH`; otherwise set
`ADB_WIN` to its full path.

```sh
"$ADB_WIN" devices
"$ADB_WIN" install -r "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
```

- **Do not use `./gradlew installDebug` in WSL.** It uses the Linux adb, which cannot see the phone.
- `wslpath -w` turns the Linux path into a Windows path that `adb.exe` can read.
- A release APK cannot be installed over a debug APK or the other way round (different signing keys). Uninstalling
  deletes NightBaseline's stored nights and revokes its Health Connect permissions; Health Connect data is not affected.

**Native Linux.** The Linux `adb` from `.tooling/android-sdk/platform-tools` works directly
(`mise exec -- adb install -r …`). This path has not been tested.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to a branch, on pull requests and on manual dispatch. Tag pushes are
left to the release workflow. There is no documentation-only skip: `tools/check-language.sh` checks every tracked file,
and `docs/design/tokens.md` is read by tests and the contrast check.

| Job | What it does |
|---|---|
| `checks` | `tools/check-language.sh` and `tools/design/contrast_check.py docs/design/tokens.md` (5 min) |
| `build` | `verifyModuleGraph ktlintCheck detekt :app:assembleDebug :app:processReleaseMainManifest`, then the module tests, then a direct check that the merged release manifest has no network permission. On `main` it uploads the debug APK (`nightbaseline-debug-apk`, 2 days); on failure, the reports (7 days) |
| `app-tests` | `:app:testDebugUnitTest` on a fresh runner; uploads the synthetic screenshots (`screenshots`, 3 days) and, on failure, the reports |

**Memory.** Hosted runners for private repositories have about 7 GB of RAM, and one Robolectric screenshot class peaks
at about 4.7 GB RSS with a 2 GB heap (native graphics memory). Therefore:

- `build` runs with `--max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.daemon.jvmargs=-Xmx1g`, and the module tests
  in a separate step with `--max-workers=1` and a 1.5 GB Gradle heap.
- `app-tests` runs in its own job on a fresh runner with `--max-workers=1 -PtestMaxHeap=2g -PtestForkEvery=1` (one
  JVM per test class) and Kotlin compiling inside the Gradle daemon
  (`-Pkotlin.compiler.execution.strategy=in-process`), and prints `free -m` and the largest processes at the end.

Every action is pinned to a full commit SHA; Dependabot (`.github/dependabot.yml`) proposes action and Gradle updates
once a month. Runs on `main` are never cancelled; a new push to another branch cancels its run in progress. No health
data reaches CI: `.data/` is git-ignored and never checked out.

## Releases

### Tags and version codes

A release is a pushed tag `vMAJOR.MINOR.PATCH`, or `vMAJOR.MINOR.PATCH-alpha.N` / `-beta.N` / `-rc.N` for a
pre-release. `.github/scripts/version-from-tag.sh` derives the versionName (the tag without `v`) and the versionCode
`MAJOR × 1000000 + MINOR × 10000 + PATCH × 100 + S` (S = N for alpha, 30 + N for beta, 60 + N for rc, 99 for the final
release), so every later version updates the installed one. Local builds are `0.1.0-dev`, versionCode 1;
`-PversionName=… -PversionCode=…` overrides both.

### What the release workflow does

`.github/workflows/release.yml` calls `.github/workflows/build-apks.yml`, which:

1. derives the version from the tag;
2. decodes the signing key from the [signing secrets](#signing-secrets) and opens it with `keytool` (a missing secret
   fails the run with the names of the missing secrets);
3. builds `:app:assembleRelease` without configuration cache, build cache or Gradle cache;
4. verifies the APK: `apksigner` signature, not an Android debug key, the expected versionName and versionCode, no
   network permission; prints the signing certificate SHA-256 in the log and the job summary;
5. hands the APK to the `publish` job (the only job with `contents: write`, holding no key), which creates the GitHub
   release "NightBaseline X.Y.Z" with `NightBaseline-X.Y.Z.apk` and `SHA256SUMS.txt`. The notes are the tag's section
   of [CHANGELOG.md](../CHANGELOG.md) (`.github/scripts/changelog-section.sh`), or GitHub's generated notes when there
   is none, followed by a downloads table and the release signing certificate SHA-256;
6. deletes the keystore, also after a failure.

`build-apks.yml` also runs on every push to `main` and on manual dispatch as a check build (versionName
`0.0.0-dry.<run number>`, versionCode 1): the signed APK is verified and discarded. Without the secrets the check build
is skipped with a notice.

### Dry run

Actions → Release → Run workflow, with "Dry run" checked (the default): builds and verifies a signed APK from the
selected branch or tag and publishes nothing. Use it to check the secrets before the first tag. With "Dry run"
unchecked on a `v*` tag it publishes that tag's release like a tag push.

### Release checklist

1. On `main` with CI green, rename `## [0.1.0] - unreleased` (or move the entries of `## [Unreleased]` into a new
   section) to `## [X.Y.Z] - YYYY-MM-DD`, leave an empty `## [Unreleased]` and update the link definitions at the end
   of the file. Use absolute links inside a version section: it becomes the release notes.
2. Check the notes: `bash .github/scripts/changelog-section.sh X.Y.Z CHANGELOG.md`.
3. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. Check the release: two assets, and the certificate line equals
   `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`.

### Obtainium link

The "Get it on Obtainium" buttons of README.md, README.ru.md and the user guide link to Obtainium's web redirect with
an `obtainium://app/` import ([Obtainium's deep links](https://wiki.obtainium.imranr.dev/deep_links/)). The import
relies on the asset name `NightBaseline-<version>.apk` and on the release key. If either changes, or the repository
moves, rebuild the link and replace the `[obtainium-import]` definition at the end of all three files:

```sh
python3 -I - <<'EOF'
import json, urllib.parse
settings = {  # additionalSettings is itself a JSON string
    "includePrereleases": False,
    "apkFilterRegEx": r"^NightBaseline-[0-9.]+\.apk$",
    "allowedSigningCertHashes": "26ec1dec3593adced268bb641ecb747b2e8b1542d5662478202fe0c6b1f4d377",
}
config = {
    "id": "io.github.alien2003.nightbaseline",
    "url": "https://github.com/alien2003/NightBaseline",
    "author": "alien2003",
    "name": "NightBaseline",
    "additionalSettings": json.dumps(settings, separators=(",", ":")),
}
encoded = urllib.parse.quote(json.dumps(config, separators=(",", ":")), safe="-_.!~*'()")
print("https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/" + encoded)
EOF
```

The button image `docs/get-it-on-obtainium.svg` was drawn for PulseStages (not Obtainium's artwork; see the comment in
the file) and copied unchanged.

### Signing secrets

The build workflow needs four repository secrets (Settings → Secrets and variables → Actions). If one is missing, a
release fails at "Decode signing key" with the names of the missing secrets, and a check build is skipped.

| Secret | Value |
|---|---|
| `NIGHTBASELINE_KEYSTORE_BASE64` | The release keystore, base64 without line breaks |
| `NIGHTBASELINE_KEYSTORE_PASSWORD` | Keystore password |
| `NIGHTBASELINE_KEY_ALIAS` | Key alias in the keystore |
| `NIGHTBASELINE_KEY_PASSWORD` | Key password; for a PKCS12 keystore, the keystore password |

`app/build.gradle.kts` reads `NIGHTBASELINE_KEYSTORE_PATH`, `NIGHTBASELINE_KEYSTORE_PASSWORD`,
`NIGHTBASELINE_KEY_ALIAS` and `NIGHTBASELINE_KEY_PASSWORD` from the environment; without all four the release APK is
unsigned (and the workflow fails). The workflow decodes the keystore to a temporary file and sets the path variable.

Create a keystore outside the repository and store the secrets:

```sh
mkdir -p .secrets && chmod 700 .secrets            # git-ignored
mise exec -- keytool -genkeypair -v -storetype PKCS12 -keystore .secrets/nightbaseline-release.jks \
  -alias nightbaseline -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 .secrets/nightbaseline-release.jks | gh secret set NIGHTBASELINE_KEYSTORE_BASE64
gh secret set NIGHTBASELINE_KEYSTORE_PASSWORD        # prompts for the value
gh secret set NIGHTBASELINE_KEY_ALIAS --body nightbaseline
gh secret set NIGHTBASELINE_KEY_PASSWORD
```

**Back up the keystore and its password outside the repository and this computer.** Android installs an update only
if it is signed with the same key; a lost key means every user has to uninstall and set the app up again. Never commit
a keystore or its base64 (`.gitignore` excludes `.secrets/`, `*.jks`, `*.keystore` and `keystore.properties`). The
signing key policy is in [SECURITY.md](../SECURITY.md#signing-key-policy).

**Local signed build** (as the workflow does it):

```sh
export NIGHTBASELINE_KEYSTORE_PATH="$PWD/.secrets/nightbaseline-release.jks"
export NIGHTBASELINE_KEYSTORE_PASSWORD=… NIGHTBASELINE_KEY_ALIAS=nightbaseline NIGHTBASELINE_KEY_PASSWORD=…
mise exec -- ./gradlew --no-configuration-cache --no-build-cache :app:assembleRelease
```
