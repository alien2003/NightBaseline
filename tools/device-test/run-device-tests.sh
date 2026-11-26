#!/usr/bin/env bash
# On-device end-to-end suite of NightBaseline against the RELEASE app INSTALLED on the phone
# (tools/device-test/README.md). Adapted from PulseStages' runner.
#
#   mise exec -- tools/device-test/run-device-tests.sh                 # build, verify, run every step
#   mise exec -- tools/device-test/run-device-tests.sh --grant-write   # the same, with the single test write
#   mise exec -- tools/device-test/run-device-tests.sh --dry-run       # print the adb commands, run nothing
#
# What this script does to the phone, and nothing else:
#   - pulls the installed app's APK to compare it with the local build (dex, signing certificate, resource ids) and
#     refuses to go on when they differ;
#   - installs ONLY the test APK (io.github.alien2003.nightbaseline.test), without -g, and uninstalls ONLY that package
#     at the end; the app itself is never installed, updated, cleared or uninstalled;
#   - with --grant-write only: grants WRITE_STEPS to the test package (never to the app); it goes with the uninstall;
#   - runs the test classes one by one with `am instrument` (Android force-stops the app when an instrumentation
#     starts and ends; inside it the app's WorkManager is an in-memory one, so none of the app's work runs);
#   - pulls the reports from the suite's own directories below /sdcard/Android/data/ and removes them;
#   - opens NightBaseline once at the end and returns to the home screen, so that its WorkManager schedules the daily
#     check again after the force-stops (--no-rearm skips it).
# Every adb call is time-limited and reads no terminal input. The exit trap (EXIT, INT, TERM, HUP) stops the current
# step, runs the test package's write cleanup when the write step started, pulls the reports, uninstalls the test
# package and prints the summary.

set -Eeuo pipefail

readonly APP_ID="io.github.alien2003.nightbaseline"
readonly TEST_ID="${APP_ID}.test"
readonly E2E_PACKAGE="${APP_ID}.e2e"
readonly RUNNER="${TEST_ID}/${E2E_PACKAGE}.E2eRunner"
readonly SELF_RUNNER="${TEST_ID}/${E2E_PACKAGE}.SelfE2eRunner"
readonly WRITE_PERMISSION="android.permission.health.WRITE_STEPS"
readonly DEVICE_APP_DIR="/sdcard/Android/data/${APP_ID}/files/e2e"
readonly DEVICE_TEST_DIR="/sdcard/Android/data/${TEST_ID}/files/e2e"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPO_ROOT
readonly OUTPUTS="${REPO_ROOT}/app/build/outputs/apk"
readonly TEST_APK="${OUTPUTS}/androidTest/e2e/app-e2e-androidTest.apk"
readonly RELEASE_APK="${OUTPUTS}/release/app-release.apk"
readonly E2E_APK="${OUTPUTS}/e2e/app-e2e.apk"
readonly OUT_ROOT="${REPO_ROOT}/tools/device-test/out"
MAIN_PID="$BASHPID"
readonly MAIN_PID

DRY_RUN=0
SKIP_BUILD=0
GRANT_WRITE=0
KEEP_GOING=0
REARM=1
SCREENSHOTS=1
UNLOCK_TIMEOUT=120
SERIAL="${ANDROID_SERIAL:-}"

# State for the exit trap.
TEST_APK_ATTEMPTED=0
INSTRUMENTED=0
WRITE_STARTED=0
STEP_PID_FILE=""
STEP_ACTIVE=0
STEP_PACKAGE=""
OUT_DIR=""
RUN_ID=""
VERSION_NAME=""
VERSION_CODE=""
LAST_RESULT=""
RESULTS=()
WARNINGS=()
LEFTOVERS=()
FAILED=0

usage() {
  cat <<'EOF'
Usage: tools/device-test/run-device-tests.sh [options]

Options:
  --dry-run                  print every adb (and Gradle) command instead of running it; touches neither the phone
                             nor the build
  --grant-write              grant WRITE_STEPS to the TEST package for this run (the single test write); without it
                             WriteDeleteE2e is skipped
  --skip-build               use the APKs already built
  --serial SERIAL            the phone to use (default: the only one connected)
  --keep-going               continue with the next steps after a failed one
  --no-screenshots           the UI step takes no screenshots
  --unlock-timeout SECONDS   how long the UI step waits for the phone to be unlocked (default 120)
  --no-rearm                 do not open the app at the end
  -h, --help                 this help
EOF
}

log() { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*"; }
warn() { printf '%s WARNING: %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
die() {
  printf '%s ERROR: %s\n' "$(date '+%H:%M:%S')" "$*" >&2
  exit 1
}
# --dry-run prints to fd 5 (the terminal's stderr, set up in main), which no caller's redirection captures.
dry() { printf '[dry-run] %s\n' "$*" >&5; }

parse_args() {
  while (($# > 0)); do
    case "$1" in
      --dry-run) DRY_RUN=1 ;;
      --grant-write) GRANT_WRITE=1 ;;
      --skip-build) SKIP_BUILD=1 ;;
      --keep-going) KEEP_GOING=1 ;;
      --no-rearm) REARM=0 ;;
      --no-screenshots) SCREENSHOTS=0 ;;
      --serial)
        SERIAL="${2:?--serial needs a value}"
        shift
        ;;
      --unlock-timeout)
        UNLOCK_TIMEOUT="${2:?--unlock-timeout needs a value}"
        shift
        ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        usage >&2
        die "unknown argument: $1"
        ;;
    esac
    shift
  done
  [[ "$UNLOCK_TIMEOUT" =~ ^[0-9]+$ ]] || die "--unlock-timeout must be a number of seconds"
}

# --- adb ---------------------------------------------------------------------------------------------------------

ADB="${ADB_WIN:-adb.exe}"
ADB_TARGET=()

resolve_adb() {
  ((DRY_RUN)) && return 0
  if ! command -v "$ADB" >/dev/null 2>&1; then
    if command -v adb >/dev/null 2>&1; then
      ADB="adb"
    else
      die "adb not found: run through 'mise exec --' (ADB_WIN from .mise.toml) or set ADB_WIN to adb.exe's path"
    fi
  fi
}

# A path on this machine as adb can read it: the Windows adb.exe (WSL interop) needs a Windows path.
host_path() {
  if [[ "$ADB" == *.exe ]] && command -v wslpath >/dev/null 2>&1; then
    wslpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

# adb_t SECONDS ARGS...: every adb call is time-limited and reads no terminal input. In --dry-run it only prints.
adb_t() {
  local seconds="$1"
  shift
  if ((DRY_RUN)); then
    dry "$ADB ${ADB_TARGET[*]} $*"
    return 0
  fi
  timeout --kill-after=10 "$seconds" "$ADB" "${ADB_TARGET[@]}" "$@" </dev/null 3>&- 4>&-
}

# adb_recorded PID_FILE SECONDS ARGS...: adb_t whose `timeout` process is written to PID_FILE (stop_current_step).
adb_recorded() {
  local pid_file="$1" seconds="$2"
  shift 2
  if ((DRY_RUN)); then
    dry "$ADB ${ADB_TARGET[*]} $*"
    return 0
  fi
  timeout --kill-after=10 "$seconds" "$ADB" "${ADB_TARGET[@]}" "$@" </dev/null 3>&- 4>&- &
  local pid=$!
  printf '%s' "$pid" >"$pid_file"
  wait "$pid"
}

# shell_t SECONDS COMMAND...: adb shell with the carriage returns of adb.exe removed.
shell_t() {
  local seconds="$1"
  shift
  adb_t "$seconds" shell "$@" | tr -d '\r'
}

# Exactly one authorised phone (or the one given with --serial); an unauthorised or offline one stops the run.
select_device() {
  if ((DRY_RUN)); then
    adb_t 60 devices
    SERIAL="${SERIAL:-SERIAL}"
    ADB_TARGET=(-s "$SERIAL")
    return 0
  fi
  local list devices others count
  list="$(adb_t 60 devices | tr -d '\r')" || die "adb devices failed"
  devices="$(awk 'NR > 1 && $2 == "device" { print $1 }' <<<"$list")"
  others="$(awk 'NR > 1 && NF >= 2 && $2 != "device" { print $1 " (" $2 ")" }' <<<"$list")"
  if [[ -n "$SERIAL" ]]; then
    grep -qx -- "$SERIAL" <<<"$devices" || die "the phone $SERIAL is not connected and authorised (connected: ${devices:-none}${others:+; not usable: ${others//$'\n'/, }})"
  else
    if [[ -n "$others" ]]; then
      die "a device is connected but not usable: ${others//$'\n'/, } (unlock it and accept 'Allow USB debugging', or pass --serial)"
    fi
    count="$(grep -c . <<<"$devices" || true)"
    [[ "$count" == "1" ]] || die "exactly one authorised phone must be connected (found ${count}: ${devices//$'\n'/, }); use --serial"
    SERIAL="$devices"
  fi
  ADB_TARGET=(-s "$SERIAL")
  log "phone: $(shell_t 20 getprop ro.product.manufacturer) $(shell_t 20 getprop ro.product.model), Android $(shell_t 20 getprop ro.build.version.release)"
}

check_installed_app() {
  if ((DRY_RUN)); then
    shell_t 60 dumpsys package "$APP_ID" >/dev/null
    VERSION_NAME="${EXPECTED_VERSION_NAME:-<installed versionName>}"
    VERSION_CODE="${EXPECTED_VERSION_CODE:-<installed versionCode>}"
    return 0
  fi
  local dump
  dump="$(shell_t 60 dumpsys package "$APP_ID")" || die "dumpsys package failed"
  VERSION_CODE="$(grep -o -m1 'versionCode=[0-9]*' <<<"$dump" | cut -d= -f2 || true)"
  VERSION_NAME="$(grep -o -m1 'versionName=[^ ]*' <<<"$dump" | cut -d= -f2 || true)"
  [[ -n "$VERSION_CODE" && -n "$VERSION_NAME" ]] || die "NightBaseline ($APP_ID) is not installed; this suite never installs it"
  if [[ -n "${EXPECTED_VERSION_CODE:-}" && "$VERSION_CODE" != "$EXPECTED_VERSION_CODE" ]]; then
    die "installed NightBaseline has versionCode $VERSION_CODE, expected $EXPECTED_VERSION_CODE"
  fi
  if grep -q 'DEBUGGABLE' <<<"$(grep -m1 'pkgFlags=' <<<"$dump" || true)"; then
    warn "the installed app is debuggable: this suite is meant for the release build"
  fi
  log "installed NightBaseline: versionName $VERSION_NAME, versionCode $VERSION_CODE"
}

# --- build -------------------------------------------------------------------------------------------------------

# The release key, for the test APK (an instrumentation must be signed like its target). From the environment, or
# from .secrets/keystore.properties of this checkout or of the main checkout of a git worktree. Never printed.
load_signing() {
  if [[ -n "${NIGHTBASELINE_KEYSTORE_PATH:-}" ]]; then
    return 0
  fi
  local file="" candidate common
  for candidate in "${NIGHTBASELINE_KEYSTORE_PROPERTIES:-}" "${REPO_ROOT}/.secrets/keystore.properties"; do
    [[ -n "$candidate" && -f "$candidate" ]] && file="$candidate" && break
  done
  if [[ -z "$file" ]]; then
    common="$(cd "$REPO_ROOT" && git rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
    [[ -n "$common" && -f "${common%/.git}/.secrets/keystore.properties" ]] && file="${common%/.git}/.secrets/keystore.properties"
  fi
  [[ -n "$file" ]] || die "no release signing: set NIGHTBASELINE_KEYSTORE_PATH, _PASSWORD, _KEY_ALIAS, _KEY_PASSWORD or provide .secrets/keystore.properties"
  local key value store=""
  while IFS='=' read -r key value; do
    value="${value%$'\r'}"
    case "$key" in
      storeFile) store="$value" ;;
      storePassword) export NIGHTBASELINE_KEYSTORE_PASSWORD="$value" ;;
      keyAlias) export NIGHTBASELINE_KEY_ALIAS="$value" ;;
      keyPassword) export NIGHTBASELINE_KEY_PASSWORD="$value" ;;
    esac
  done <"$file"
  [[ "$store" == /* ]] || store="$(dirname "$file")/$store"
  [[ -f "$store" ]] || store="$(dirname "$file")/$(basename "$store")"
  [[ -f "$store" ]] || die "the keystore named in $file does not exist"
  export NIGHTBASELINE_KEYSTORE_PATH="$store"
}

build_apks() {
  ((SKIP_BUILD)) && return 0
  local gradle=(./gradlew)
  command -v mise >/dev/null 2>&1 && gradle=(mise exec -- ./gradlew)
  local tasks=(:app:assembleRelease :app:assembleE2e :app:assembleE2eAndroidTest -PversionName="$VERSION_NAME" -PversionCode="$VERSION_CODE" --console=plain -q)
  if ((DRY_RUN)); then
    dry "(cd $REPO_ROOT && ${gradle[*]} ${tasks[*]})"
    return 0
  fi
  load_signing
  log "building the release APK (to compare), the e2e APK (resource ids) and the test APK: versionName $VERSION_NAME, versionCode $VERSION_CODE"
  (cd "$REPO_ROOT" && "${gradle[@]}" "${tasks[@]}" </dev/null 3>&- 4>&-) || die "the APKs did not build"
}

# --- verification ------------------------------------------------------------------------------------------------

build_tool() {
  local sdk="${ANDROID_HOME:-${REPO_ROOT}/.tooling/android-sdk}"
  find "$sdk/build-tools" -maxdepth 2 -name "$1" -type f 2>/dev/null | sort -V | tail -n 1
}

cert_digest() {
  "$1" verify --print-certs "$2" 2>/dev/null | sed -n -E 's/^.*[Ss]igner.*certificate SHA-256 digest:[[:space:]]*//p' |
    head -n 1 | tr -d ': ' | tr 'A-F' 'a-f'
}

# "name sha256" of every classes*.dex of an APK, sorted.
dex_digests() {
  python3 -I - "$1" <<'PY'
import hashlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    for name in sorted(n for n in z.namelist() if n.startswith("classes") and n.endswith(".dex")):
        print(name, hashlib.sha256(z.read(name)).hexdigest())
PY
}

resource_ids() { "$1" dump resources "$2" 2>/dev/null | grep -oE 'resource 0x[0-9a-f]+ [^ ]+' | sort -u; }

# The test APK runs the app from its own copy of the same sources, so it may only run when the installed APK is
# exactly the local release build: the same dex, the same signing certificate as the test APK, the same resource ids
# as the e2e build type the copy was compiled with.
verify_installed_matches_build() {
  local signer aapt device_path tmp
  signer="$(build_tool apksigner)"
  aapt="$(build_tool aapt2)"
  if ((DRY_RUN)); then
    shell_t 30 pm path "$APP_ID" >/dev/null
    adb_t 300 pull "<path of base.apk>" "<run dir>/installed-base.apk"
    dry "compare: dex of the installed APK = dex of $RELEASE_APK; signing certificate = that of $TEST_APK; resource ids = $E2E_APK"
    return 0
  fi
  [[ -x "$signer" && -x "$aapt" ]] || die "apksigner or aapt2 not found under \$ANDROID_HOME/build-tools (run through 'mise exec --')"
  local apk
  for apk in "$TEST_APK" "$RELEASE_APK" "$E2E_APK"; do
    [[ -f "$apk" ]] || die "missing $apk (build it, or drop --skip-build)"
  done
  device_path="$(shell_t 30 pm path "$APP_ID" | sed -n 's/^package://p' | grep 'base\.apk$' | head -n 1 || true)"
  [[ -n "$device_path" ]] || die "cannot find the installed APK of $APP_ID"
  tmp="${OUT_DIR}/installed-base.apk"
  adb_t 300 pull "$device_path" "$(host_path "$tmp")" >/dev/null || die "cannot pull the installed APK"
  local app_cert test_cert
  app_cert="$(cert_digest "$signer" "$tmp")"
  test_cert="$(cert_digest "$signer" "$TEST_APK")"
  [[ -n "$app_cert" && "$app_cert" == "$test_cert" ]] ||
    die "the installed app and the test APK are signed with different certificates (${app_cert:-?} vs ${test_cert:-?}): build with the release key"
  if [[ "$(dex_digests "$tmp")" != "$(dex_digests "$RELEASE_APK")" ]]; then
    die "the installed app is not the release build of this checkout (its dex differs): check out the commit it was built from (versionName $VERSION_NAME) and run again"
  fi
  local missing
  missing="$(comm -23 <(resource_ids "$aapt" "$tmp") <(resource_ids "$aapt" "$E2E_APK") | head -n 5)"
  [[ -z "$missing" ]] || die "resource ids of the installed app differ from the e2e build: ${missing//$'\n'/, }"
  rm -f "$tmp"
  log "verified: the installed app is this checkout's release build (dex identical), signed like the test APK; resource ids match"
}

# --- install, permissions, uninstall -----------------------------------------------------------------------------

instrumentations_registered() {
  ((DRY_RUN)) && return 0
  local list
  list="$(shell_t 30 pm list instrumentation)" || return 1
  grep -qF "$RUNNER" <<<"$list" && grep -qF "$SELF_RUNNER" <<<"$list"
}

install_test_apk() {
  log "installing ONLY the test APK ($TEST_ID). If the phone asks to confirm an installation over USB, tap Install."
  TEST_APK_ATTEMPTED=1
  local output
  # Never -g: that would grant the test package's WRITE_STEPS without asking.
  output="$(adb_t 300 install -r -t "$(host_path "$TEST_APK")" 2>&1 | tr -d '\r')" || true
  ((DRY_RUN)) || grep -q '^Success' <<<"$output" || die "installing the test APK failed: $output"
  instrumentations_registered || die "the instrumentations $RUNNER and $SELF_RUNNER are not registered"
}

grant_write() {
  if ((!GRANT_WRITE)); then
    log "WRITE_STEPS is not granted to the test package (no --grant-write): the single test write is skipped"
    return 0
  fi
  [[ "$TEST_ID" == "${APP_ID}.test" ]] || die "refusing to grant a permission to $TEST_ID"
  log "granting $WRITE_PERMISSION to the TEST package only (it goes with the uninstall at the end)"
  shell_t 30 pm grant "$TEST_ID" "$WRITE_PERMISSION" >/dev/null || die "pm grant $TEST_ID $WRITE_PERMISSION failed"
}

uninstall_test_package() {
  local package="$TEST_ID"
  # Only the test package, never the app.
  if [[ "$package" != "${APP_ID}.test" || "$package" == "$APP_ID" ]]; then
    die "refusing to uninstall $package"
  fi
  adb_t 120 uninstall "$package" >/dev/null 2>&1 || true
  ((DRY_RUN)) && return 0
  local packages
  if packages="$(shell_t 30 pm list packages "$package")" && ! grep -qx "package:${package}" <<<"$packages"; then
    log "the test package is uninstalled (the app stays installed)"
  else
    LEFTOVERS+=("the test package may still be installed: $ADB -s $SERIAL uninstall $package")
  fi
}

# --- steps -------------------------------------------------------------------------------------------------------

progress_lines() {
  local line
  while IFS= read -r line; do
    case "$line" in
      "INSTRUMENTATION_STATUS: e2e="*) printf '      %s\n' "${line#INSTRUMENTATION_STATUS: e2e=}" ;;
    esac
  done
}

stop_current_step() {
  ((STEP_ACTIVE)) || return 0
  local pid=""
  [[ -n "$STEP_PID_FILE" && -s "$STEP_PID_FILE" ]] && pid="$(cat "$STEP_PID_FILE")"
  if [[ "$pid" =~ ^[0-9]+$ ]]; then
    kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  fi
  # The instrumentation goes on without adb; stopping its process ends it.
  [[ -n "$STEP_PACKAGE" ]] && shell_t 30 am force-stop "$STEP_PACKAGE" >/dev/null 2>&1 || true
  STEP_ACTIVE=0
}

# run_step NAME RUNNER CLASS TIMEOUT BUDGET: one test class (or Class#method) in its own instrumentation.
run_step() {
  local name="$1" runner="$2" class="$3" seconds="$4" budget="$5"
  local output="${OUT_DIR}/instrumentation-${name}.txt" result rc
  local args=(shell am instrument -w -r -e class "${E2E_PACKAGE}.${class}" -e e2eRunId "$RUN_ID")
  ((budget > 0)) && args+=(-e e2eReadBudget "$budget")
  [[ "$name" == "Ui" ]] && args+=(-e e2eUnlockTimeoutSec "$UNLOCK_TIMEOUT" -e e2eScreenshots "$SCREENSHOTS")
  args+=("$runner")
  LAST_RESULT=""
  log "== ${name} (${class}, at most $((seconds / 60)) min)"
  INSTRUMENTED=1
  if ((DRY_RUN)); then
    adb_t "$seconds" "${args[@]}"
    RESULTS+=("${name}: DRY-RUN")
    LAST_RESULT="PASSED"
    return 0
  fi
  STEP_PACKAGE="$APP_ID"
  [[ "$runner" == "$SELF_RUNNER" ]] && STEP_PACKAGE="$TEST_ID"
  local rc_file="${OUT_DIR}/.rc-${name}"
  STEP_PID_FILE="${OUT_DIR}/.pid-${name}"
  rm -f "$rc_file" "$STEP_PID_FILE"
  STEP_ACTIVE=1
  {
    set +e
    adb_recorded "$STEP_PID_FILE" "$seconds" "${args[@]}" 2>&1
    printf '%s' "$?" >"$rc_file"
  } | sed -u 's/\r$//' | tee "$output" | progress_lines &
  wait "$!" || true
  STEP_ACTIVE=0
  rc="$(cat "$rc_file" 2>/dev/null || true)"
  [[ "$rc" =~ ^[0-9]+$ ]] || rc=255
  rm -f "$rc_file" "$STEP_PID_FILE"
  local passed skipped
  passed="$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' "$output" || true)"
  skipped="$(grep -c '^INSTRUMENTATION_STATUS_CODE: -4$' "$output" || true)"
  if ((rc == 124 || rc == 137)); then
    result="TIMEOUT"
    shell_t 30 am force-stop "$STEP_PACKAGE" >/dev/null || true
  elif grep -q 'shortMsg=Process crashed' "$output"; then
    result="CRASHED"
  elif grep -Eq '^INSTRUMENTATION_STATUS_CODE: -(1|2)$' "$output"; then
    result="FAILED"
  elif ! grep -q '^OK (' "$output"; then
    result="FAILED"
  elif ((passed == 0 && skipped > 0)); then
    result="SKIPPED"
  elif ((skipped > 0)); then
    result="PASSED (${skipped} skipped)"
  else
    result="PASSED"
  fi
  RESULTS+=("${name}: ${result}")
  LAST_RESULT="$result"
  log "== ${name}: ${result}"
  if [[ "$result" != PASSED* && "$result" != "SKIPPED" ]]; then
    sed -n '/^INSTRUMENTATION_STATUS: stack=/,/^INSTRUMENTATION_STATUS/p' "$output" | head -n 25 | sed 's/^/      /' || true
    FAILED=1
    return 1
  fi
  return 0
}

# The steps. The write runs only right after a passed "before" snapshot, and is always followed by the test package's
# cleanup and the "after" snapshot with its comparison.
main_steps() {
  run_step HealthConnect "$RUNNER" HealthConnectE2e 900 150 || ((KEEP_GOING)) || return 1
  if run_step SnapshotBefore "$RUNNER" 'HcSnapshotE2e#before' 600 100 && [[ "$LAST_RESULT" == "PASSED" ]]; then
    WRITE_STARTED=1
    run_step WriteDelete "$SELF_RUNNER" WriteDeleteE2e 600 0 || true
    run_step WriteCleanup "$SELF_RUNNER" WriteCleanupE2e 600 0 || true
    WRITE_STARTED=0
    run_step SnapshotAfter "$RUNNER" 'HcSnapshotE2e#after' 600 100 || ((KEEP_GOING)) || return 1
  else
    RESULTS+=("WriteDelete: NOT RUN (no passed snapshot before the write)")
    [[ "$LAST_RESULT" == "SKIPPED" ]] || ((KEEP_GOING)) || return 1
  fi
  run_step Pipeline "$RUNNER" PipelineE2e 2400 600 || ((KEEP_GOING)) || return 1
  run_step Ui "$RUNNER" UiE2e 900 0 || ((KEEP_GOING)) || return 1
  return 0
}

pull_results() {
  if adb_t 600 pull "$DEVICE_APP_DIR" "$(host_path "${OUT_DIR}/app")" >/dev/null 2>&1; then
    log "app-process reports pulled to ${OUT_DIR}/app"
  else
    warn "could not pull ${DEVICE_APP_DIR}"
  fi
  # The test package's own directory goes with the uninstall; pulled first.
  adb_t 300 pull "$DEVICE_TEST_DIR" "$(host_path "${OUT_DIR}/self")" >/dev/null 2>&1 || true
}

remove_device_reports() {
  [[ "$DEVICE_APP_DIR" == "/sdcard/Android/data/${APP_ID}/files/e2e" ]] || return 1
  if ! shell_t 60 rm -rf "$DEVICE_APP_DIR" >/dev/null; then
    LEFTOVERS+=("the reports are still on the phone: $ADB -s $SERIAL shell rm -rf $DEVICE_APP_DIR")
  fi
}

rearm_app() {
  ((REARM && INSTRUMENTED)) || return 0
  log "opening NightBaseline once so that WorkManager schedules its daily check again, then the home screen"
  local output
  output="$(shell_t 60 am start -W -n "${APP_ID}/.MainActivity" 2>&1)" || output="Error: $output"
  if grep -q '^Error' <<<"$output"; then
    LEFTOVERS+=("NightBaseline could not be opened at the end: open it once on the phone, so that its daily check is scheduled again")
    return 0
  fi
  ((DRY_RUN)) || sleep 5
  shell_t 15 input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
}

# The value-free status files: test, result, duration and counts.
print_status_files() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  python3 -I - "$dir" <<'PY' || true
import json, pathlib, sys
for f in sorted(pathlib.Path(sys.argv[1]).rglob("status/*.json")):
    try:
        s = json.loads(f.read_text())
    except (OSError, ValueError):
        continue
    counts = ", ".join(f"{k} {v}" for k, v in s.get("counts", {}).items())
    print(f"    {s.get('test')}: {s.get('result')} in {s.get('durationMs')} ms" + (f" ({counts})" if counts else ""))
PY
}

summary() {
  printf '\n===== NightBaseline on-device E2E: %s =====\n' "$RUN_ID"
  local entry
  for entry in "${RESULTS[@]}"; do printf '  %s\n' "$entry"; done
  for entry in "${WARNINGS[@]}"; do printf '  warning: %s\n' "$entry"; done
  if [[ -n "$OUT_DIR" && -d "$OUT_DIR" ]]; then
    print_status_files "$OUT_DIR"
    [[ -d "${OUT_DIR}/app/e2e/ui" || -d "${OUT_DIR}/app/ui" ]] && printf '  Screenshots (your real screens, keep them local): %s\n' "${OUT_DIR}/app"
    printf '  Everything: %s\n' "$OUT_DIR"
  fi
  if ((${#LEFTOVERS[@]} > 0)); then
    printf '  Left behind, and the command that fixes it:\n'
    for entry in "${LEFTOVERS[@]}"; do printf '  !! %s\n' "$entry"; done
  fi
  if ((DRY_RUN)); then
    printf '  Result: DRY-RUN (nothing was run)\n'
  elif ((FAILED)); then
    printf '  Result: FAILED\n'
  elif ((${#LEFTOVERS[@]} > 0)); then
    printf '  Result: INCOMPLETE\n'
  else
    printf '  Result: PASSED\n'
  fi
}

finish() {
  local rc=$?
  [[ "$BASHPID" == "$MAIN_PID" ]] || exit "$rc"
  trap '' INT TERM HUP
  set +e
  ((rc != 0)) && FAILED=1
  stop_current_step
  if ((TEST_APK_ATTEMPTED)); then
    if ((WRITE_STARTED)) && ! ((DRY_RUN)); then
      log "the write step was interrupted: deleting what the test package may have left before it is uninstalled"
      run_step WriteCleanup "$SELF_RUNNER" WriteCleanupE2e 600 0 ||
        LEFTOVERS+=("the write cleanup did not confirm: run the suite again with --grant-write (it deletes leftovers of the test package first)")
    fi
    pull_results
    remove_device_reports
    uninstall_test_package
  fi
  rearm_app
  summary
  local code=0
  ((rc != 0 || FAILED || ${#LEFTOVERS[@]} > 0)) && code=1
  exit "$code"
}

on_signal() {
  [[ "$BASHPID" == "$MAIN_PID" ]] || exit 130
  trap '' INT TERM HUP
  printf '\n'
  warn "interrupted: stopping the current step, then cleaning up (keep the phone connected)"
  stop_current_step
  RESULTS+=("interrupted by a signal")
  FAILED=1
  exit 130
}

main() {
  parse_args "$@"
  resolve_adb
  RUN_ID="$(date '+%Y%m%d-%H%M%S')"
  OUT_DIR="${OUT_ROOT}/${RUN_ID}"
  exec 5>&2
  if ((DRY_RUN)); then
    OUT_DIR="${OUT_ROOT}/<run>"
    log "NightBaseline on-device E2E, DRY RUN: the commands below are printed, not run"
  else
    mkdir -p "$OUT_DIR"
    exec > >(trap '' INT TERM HUP; exec tee -a "${OUT_DIR}/runner.log") 2>&1
    log "NightBaseline on-device E2E, run ${RUN_ID}; adb: ${ADB}"
  fi
  trap finish EXIT
  trap on_signal INT TERM HUP

  select_device
  check_installed_app
  build_apks
  verify_installed_matches_build
  # The suite's own report directory of an earlier run, never anything else of the app.
  shell_t 60 rm -rf "$DEVICE_APP_DIR" >/dev/null || true
  install_test_apk
  grant_write
  log "from now on: keep the phone connected, unlocked and untouched until the summary appears"
  main_steps || FAILED=1
}

main "$@"
