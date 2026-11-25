# NightBaseline — project rules

Android app: once a day, after sleep, it reads nightly measurements from Health Connect, compares them with the
person's own baseline and tells them when several measurements are outside it. Fully passive, local processing only,
no internet. Current state and plan: `docs/STATUS.md` (read it first in a new session). Decisions: `docs/adr/`.

## Language
- Everything in the repository is English: docs, ADRs, code, comments, commit messages, scripts.
- Exceptions required by the product: the app's Russian localization and `README.ru.md`.
- Chat with the owner is in Russian.

## Working mode
- Autonomous until release 0.1.0: no confirmation between stages; decide, record the decision and the reason in
  `docs/adr/`. **This does not apply to the phone** (see hard rule 10).
- Stop only when the owner's hands are needed. Batch manual actions, give exact instructions: what to open, what to
  tap, how to tell it worked.
- Never mark as done what was not verified. Label hypotheses as hypotheses.
- When stuck: three attempts with different approaches, then record in STATUS.md, work around, come back later. Never
  weaken tests to make them pass.
- Small meaningful commits (Conventional Commits), progress in `docs/STATUS.md`.
- While waiting for the owner or a new night, do what does not depend on it.

## Product scope (decided, do not revisit)
- One phone app on top of Health Connect, no watch component.
- No manual measurements, no real time, no persistent services. One computation a day, once the night is complete in
  the source.
- No INTERNET permission. Local processing only.
- v1 metrics: sleeping heart rate (computed by us from raw samples inside the sleep session), respiratory rate, SpO2,
  sleep duration; available extra types (HRV, skin temperature, …) are picked up automatically — metrics are
  pluggable.
- Never call the feature "Vitals". Wording: "measurements moved outside your usual range", no diagnoses. A "not a
  medical device" disclaimer in the app and the README.
- Vendor neutral: any Health Connect source; OHealth (com.heytap.health.international) is the default and the first
  verified source. The ColorOS look is styling only: no vendor SDKs or resources.

## Hard rules
1. Work in WSL Ubuntu, only inside the NightBaseline folder. Install or change nothing outside it. If unavoidable,
   stop and ask.
2. Toolchains only through mise (`.mise.toml`, following PulseStages). Docker only as a last resort and with consent.
3. Caches and state in `.tooling/` inside the project (git-ignored).
4. adb: only the Windows `adb.exe` through WSL interop (`ADB_WIN`), only the phone. Never `./gradlew installDebug` in
   WSL; install with `"$ADB_WIN" install -r "$(wslpath -w <apk>)"`.
5. Health data never reaches git, CI, build logs or release texts. Raw exports only in the git-ignored `.data/`. The
   repository gets summaries without raw values, and synthetic data. Repository screenshots only from synthetic data.
   Reference screenshots of system apps only in `.data/design-refs/`.
6. On the phone: Health Connect read-only, except one test record in the on-device tests, deleted right away. Never
   delete or change other apps' data or other apps.
7. Secrets and keys never reach git (signing key in `.secrets/`, ignored). Publish nothing outside the NightBaseline
   repository: no posts, no issues in other projects, no emails.
8. Do not analyse or use the private OHealth cloud API.
9. `../PulseStages` (../PulseStages) is read-only: change nothing, commit nothing, run no builds or commands
   that write there. Never touch or copy PulseStages secrets and keys.
10. **Phone only with permission.** Before EVERY access to the phone over adb (install, export, screenshots, launching
    anything, even reading getprop/dumpsys) tell the owner exactly what will be done and how long it takes, and wait
    for explicit permission. Never open or drive other apps without separate permission. Leave no persistent changes
    (stay-awake settings, apps left open that keep the screen on, temp files); afterwards report and clean up.
11. The owner's personal data (email, name, phone serial) never reaches the repository or external requests. git uses
    `alien2003@users.noreply.github.com`. The repository is PUBLIC (since 2026-11-26): no absolute paths with a user
    name in committed files (write repository-relative paths, `../PulseStages`, `<project>`); `tools/check-leaks.sh`
    and `tools/check-language.sh` run before every commit and in CI.

## Environment
- `mise exec -- <cmd>` (or activated mise) provides Java, the Android SDK in `.tooling/android-sdk`,
  `GRADLE_USER_HOME=.tooling/gradle`, `ADB_WIN=adb.exe`, `ANDROID_USER_HOME=.tooling/android-user`.
- Package `io.github.alien2003.nightbaseline`. License: Apache-2.0.
- github.com/alien2003/NightBaseline stays private until stage 5.
