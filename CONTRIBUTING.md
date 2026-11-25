# Contributing to NightBaseline

Thank you for helping. This file covers bug reports, proposals and pull requests. Setup, build, tests and releases are
in [docs/development.md](docs/development.md); all documents are listed in [docs/README.md](docs/README.md).

Issues, pull requests, commit messages, code comments and documentation are written in English. The only Russian
text in the repository is the app's translation (`app/src/main/res/values-ru*`) and [README.ru.md](README.ru.md);
`tools/check-language.sh` enforces this ([ADR 0002](docs/adr/0002-project-language-and-history.md)).

## Reporting a bug

Open an issue and include:

1. **Version** (Settings → "About NightBaseline") and whether you installed the release APK or your own build.
2. **Device:** phone model, Android version, the phone maker's system version (for example ColorOS), and on Android
   9–13 the Health Connect version.
3. **Source app** and its version, and the watch or ring.
4. **What happened and what you expected**, with steps to reproduce. Quote the exact text the app showed.

**Issues are public. Never post health data:** no values, no screenshots that show your measurements, no Health
Connect exports. A description of the message, the status and the dates is enough.

A night you think was judged wrongly is not necessarily a bug: the rules are in [ALGORITHM.md](ALGORITHM.md), and one
measurement moving on its own is expected now and then.

**Security problems:** do not open a public issue; follow [SECURITY.md](SECURITY.md).

## Proposing a change

- **Small fixes** (typos, an obvious bug with a clear fix): open a pull request.
- **Anything larger** (a feature, a setting, a new metric, a new dependency, a change to the algorithm or its
  defaults): open an issue first so the approach can be agreed.

Some constraints are deliberate and not up for change without a discussion first:

- **No network access.** The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE`; `MergedManifestTest`, CI and the
  release workflow fail if either comes back. Analytics, crash reporting and remote configuration are out of scope.
- **Read-only.** Health Connect READ permissions only.
- **Calm wording.** "Moved outside your usual range", "higher than usual", "lower than usual"; no diagnoses, no
  "alert", "warning", "abnormal" or "danger", no red, no exclamation marks. The feature is never called "Vitals".
  NightBaseline is not a medical device.
- **Vendor neutral.** Any Health Connect source; no vendor SDKs, icons, fonts or images.
- **Module graph.** Dependencies point downwards only (`app → work → data-hc → data-local → core-algo`,
  `algo-cli → core-algo`), checked by `verifyModuleGraph`; `:core-algo` stays pure Kotlin/JVM
  ([docs/development.md](docs/development.md#modules)).

## Pull requests

Before you open one:

- [ ] `mise exec -- ./gradlew verifyModuleGraph ktlintCheck detekt test :app:assembleDebug` passes.
- [ ] `bash tools/check-language.sh` exits 0 (enable the hook once: `git config core.hooksPath tools/githooks`).
- [ ] New or changed behaviour has tests; tests run on synthetic data only.
- [ ] Every new string exists in `res/values/strings.xml` and `res/values-ru/strings.xml` (`StringResourcesTest`).
- [ ] User-visible changes are listed under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).
- [ ] Documentation that describes the changed behaviour is updated (user guide, ALGORITHM.md, design docs).

Never weaken a test to make it pass.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/): `feat:`, `fix:`, `docs:`, `test:`,
`refactor:`, `ci:`, `build:`, `chore:`, with an optional scope (`feat(app): …`, `docs(design): …`). Small commits with
one meaningful change each.

## Changing algorithm defaults

Every default is in `AlgoParams` / `DefaultMetrics` / `CompletenessParams` and documented in
[ALGORITHM.md](ALGORITHM.md). A change needs its reason, the synthetic check (`algo-cli synthetic`,
`SyntheticBehaviourTest`) before and after, and an update of ALGORITHM.md. Results on real data are reported as
aggregates only.

## What not to commit

- **Health data of any kind**: exports, dumps, night files, per-night values, screenshots with real data. They belong
  in the git-ignored `.data/` only. Repository screenshots come from synthetic data.
- Keystores, passwords and other secrets (`.secrets/`, `*.jks`, `*.keystore`, `keystore.properties`).
- Personal data: names, e-mail addresses, device serial numbers. Use a GitHub `noreply` address for commits.

## Licence of contributions

NightBaseline is licensed under the [Apache License 2.0](LICENSE). By contributing you agree that your contribution is published
under the same licence.
