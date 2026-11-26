#!/usr/bin/env bash
# Self-test of run-device-tests.sh with a fake adb (no phone, no build; adapted from PulseStages): the runner's control
# flow end to end on the host. Needs bash, python3 and coreutils; takes well under a minute.
#
#   tools/device-test/selftest/run-selftest.sh

set -Eeuo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${HERE}/../run-device-tests.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILURES=0
readonly APP="io.github.alien2003.nightbaseline"
readonly TEST="$APP.test"

# make_apk PATH DEX CERT: a zip with one classes.dex and the fake tools' sidecars (signing certificate, resources).
make_apk() {
  mkdir -p "$(dirname "$1")"
  python3 -I -c 'import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("classes.dex", sys.argv[2])
    z.writestr("AndroidManifest.xml", "manifest")' "$1" "$2"
  printf '%s\n' "$3" >"$1.cert"
  printf 'resource 0x7f010001 string/app_name\nresource 0x7f010002 string/nav_home\n' >"$1.res"
}

# A fake checkout around a copy of the runner, a fake SDK and the fake phone's state.
setup() {
  ROOT="${WORK}/$1"
  local tools="$ROOT/sdk/build-tools/37.0.0"
  mkdir -p "$ROOT/tools/device-test" "$tools" "$ROOT/state"
  cp "$RUNNER" "$ROOT/tools/device-test/"
  local apks="$ROOT/app/build/outputs/apk"
  make_apk "$apks/androidTest/e2e/app-e2e-androidTest.apk" "test dex" "aabbcc"
  make_apk "$apks/release/app-release.apk" "release dex" "aabbcc"
  make_apk "$apks/e2e/app-e2e.apk" "unminified dex" "aabbcc"
  make_apk "$ROOT/installed.apk" "release dex" "aabbcc"
  # shellcheck disable=SC2016 # The fake tools expand their own arguments.
  printf '#!/usr/bin/env bash\necho "Signer #1 certificate SHA-256 digest: $(cat "$3.cert")"\n' >"$tools/apksigner"
  # shellcheck disable=SC2016
  printf '#!/usr/bin/env bash\ncat "$3.res"\n' >"$tools/aapt2"
  chmod +x "$tools/apksigner" "$tools/aapt2"
}

# run [ENV=VALUE...] -- RUNNER_ARGS...: the runner with the fake adb; its output in $ROOT/screen.
run() {
  local vars=()
  while [[ "$1" != "--" ]]; do
    vars+=("$1")
    shift
  done
  shift
  (cd "$ROOT" && env FAKE_STATE="$ROOT/state" FAKE_INSTALLED_APK="$ROOT/installed.apk" ADB_WIN="${HERE}/fake-adb" \
    ANDROID_HOME="$ROOT/sdk" "${vars[@]}" bash tools/device-test/run-device-tests.sh --skip-build "$@" \
    >"$ROOT/screen" 2>&1 </dev/null) || true
  # The log's tee may finish a moment after the runner.
  sleep 0.3
}

# run_and_term SECONDS [ENV=VALUE...] -- RUNNER_ARGS...: as run, but SIGTERM after SECONDS.
run_and_term() {
  local after="$1"
  shift
  local vars=()
  while [[ "$1" != "--" ]]; do
    vars+=("$1")
    shift
  done
  shift
  (cd "$ROOT" && exec env FAKE_STATE="$ROOT/state" FAKE_INSTALLED_APK="$ROOT/installed.apk" ADB_WIN="${HERE}/fake-adb" \
    ANDROID_HOME="$ROOT/sdk" "${vars[@]}" bash tools/device-test/run-device-tests.sh --skip-build "$@" \
    >"$ROOT/screen" 2>&1 </dev/null) &
  local pid=$!
  sleep "$after"
  kill -TERM "$pid" 2>/dev/null || true
  wait "$pid" || true
  sleep 0.3
}

expect() {
  local what="$1"
  shift
  if "$@"; then
    printf '  ok    %s\n' "$what"
  else
    printf '  FAIL  %s\n' "$what"
    FAILURES=$((FAILURES + 1))
  fi
}

shows() { grep -qF -- "$1" "$ROOT/screen"; }
called() { grep -qF -- "$1" "$ROOT/state/calls.log" 2>/dev/null; }
not() { ! "$@"; }
classes() { cut -d' ' -f1 "$ROOT/state/instrumentations.log" 2>/dev/null | tr '\n' ' ' | sed 's/ $//'; }
installed() { test -f "$ROOT/state/installed"; }
only_test_package_installed() { [ "$(grep -c . "$ROOT/state/installs.log")" == 1 ] && grep -q 'install -r -t .*app-e2e-androidTest\.apk$' "$ROOT/state/installs.log"; }
never_g() { not grep -qE 'install .*-g' "$ROOT/state/installs.log"; }
only_test_package_uninstalled() { [ "$(sort -u "$ROOT/state/uninstalls.log")" == "$TEST" ]; }
no_grant() { test ! -s "$ROOT/state/grants.log"; }
no_run_dir() { ! compgen -G "$ROOT/tools/device-test/out/2*" >/dev/null; }
reports_pulled() { compgen -G "$ROOT/tools/device-test/out/2*/app/status/*.json" >/dev/null; }

readonly ALL="HealthConnectE2e HcSnapshotE2e#before WriteDeleteE2e WriteCleanupE2e HcSnapshotE2e#after PipelineE2e UiE2e"

echo "a normal run with --grant-write"
setup normal
run -- --grant-write
expect "every step, in order" [ "$(classes)" == "$ALL" ]
expect "Result: PASSED" shows "Result: PASSED"
expect "the write step passed" shows "WriteDelete: PASSED"
expect "the one write happened" [ "$(cat "$ROOT/state/hc-writes.log")" == "wrote-and-deleted" ]
expect "only the test APK is installed" only_test_package_installed
expect "never with -g" never_g
expect "WRITE_STEPS granted to the test package only" [ "$(cat "$ROOT/state/grants.log")" == "pm grant $TEST android.permission.health.WRITE_STEPS" ]
expect "the write runs in the test package (SelfE2eRunner)" grep -q "^WriteDeleteE2e .*$TEST/$APP.e2e.SelfE2eRunner$" "$ROOT/state/instrumentations.log"
expect "the other steps run in the app (E2eRunner)" grep -q "^PipelineE2e .*$TEST/$APP.e2e.E2eRunner$" "$ROOT/state/instrumentations.log"
expect "only the test package is uninstalled" only_test_package_uninstalled
expect "the test package is gone" not installed
expect "reports pulled" reports_pulled
expect "the summary shows the status files" shows "FakeE2e.test: PASSED in 12 ms (reads 3)"
expect "the app's report directory is removed from the phone" grep -qx "rm -rf /sdcard/Android/data/$APP/files/e2e" "$ROOT/state/removed.log"
expect "nothing else is removed" [ "$(sort -u "$ROOT/state/removed.log")" == "rm -rf /sdcard/Android/data/$APP/files/e2e" ]
expect "the app is opened at the end" test -f "$ROOT/state/rearmed"
expect "the app is never installed or uninstalled" not grep -qE "(uninstall $APP\$|install .*app-(release|e2e)\.apk)" "$ROOT/state/calls.log"
expect "runner.log written" grep -qF "Result: PASSED" "$ROOT"/tools/device-test/out/2*/runner.log

echo "a run without --grant-write"
setup nogrant
run --
expect "no permission is granted" no_grant
expect "the write is skipped" shows "WriteDelete: SKIPPED"
expect "nothing is written" test ! -f "$ROOT/state/hc-writes.log"
expect "the other steps still run" [ "$(classes)" == "$ALL" ]
expect "Result: PASSED" shows "Result: PASSED"

echo "--dry-run"
setup dry
run -- --dry-run --grant-write
expect "no adb call at all" test ! -s "$ROOT/state/calls.log"
expect "it prints the test APK install" shows "install -r -t"
expect "it prints the grant" shows "pm grant $TEST android.permission.health.WRITE_STEPS"
expect "it prints each instrumentation" [ "$(grep -c 'am instrument' "$ROOT/screen")" == 7 ]
expect "it prints the uninstall of the test package" shows "uninstall $TEST"
expect "Result: DRY-RUN" shows "Result: DRY-RUN"
expect "no run directory is created" no_run_dir

echo "two phones"
setup two
run FAKE_DEVICES='AAA\tdevice\r\nBBB\tdevice' --
expect "refused" shows "exactly one authorised phone"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "a phone that is not authorised"
setup unauthorized
run FAKE_DEVICES='AAA\tunauthorized' --
expect "refused" shows "not usable"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "the app is not installed"
setup missing
run FAKE_NOT_INSTALLED=1 --
expect "refused" shows "is not installed; this suite never installs it"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "the installed app is another build (dex differs)"
setup otherdex
make_apk "$ROOT/installed.apk" "older release dex" "aabbcc"
run --
expect "refused" shows "is not the release build of this checkout"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "the installed app is signed with another key"
setup otherkey
make_apk "$ROOT/installed.apk" "release dex" "ddeeff"
run --
expect "refused" shows "signed with different certificates"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "resource ids differ"
setup otherres
printf 'resource 0x7f010009 string/app_name\n' >"$ROOT/installed.apk.res"
run --
expect "refused" shows "resource ids of the installed app differ"
expect "nothing installed" test ! -s "$ROOT/state/installs.log"

echo "the snapshot before the write fails"
setup nosnapshot
run FAKE_FAIL_CLASS='HcSnapshotE2e#before' -- --grant-write
expect "the write does not run" not grep -q "^WriteDeleteE2e" "$ROOT/state/instrumentations.log"
expect "it says why" shows "WriteDelete: NOT RUN"
expect "Result: FAILED" shows "Result: FAILED"
expect "the test package is still uninstalled" only_test_package_uninstalled

echo "the snapshot before the write is skipped (other apps not visible)"
setup snapshotskip
run FAKE_SKIP_CLASS='HcSnapshotE2e#before' -- --grant-write
expect "the write does not run" not grep -q "^WriteDeleteE2e" "$ROOT/state/instrumentations.log"
expect "the later steps still run" grep -q "^UiE2e" "$ROOT/state/instrumentations.log"

echo "SIGTERM during the write"
setup interrupted
run_and_term 3 FAKE_SLEEP_CLASS=WriteDeleteE2e FAKE_SLEEP=30 -- --grant-write
expect "the test package's process is stopped" grep -qx "am force-stop $TEST" "$ROOT/state/force-stops.log"
expect "the write cleanup runs before the uninstall" [ "$(classes)" == "HealthConnectE2e HcSnapshotE2e#before WriteDeleteE2e WriteCleanupE2e" ]
expect "the test package is uninstalled" only_test_package_uninstalled
expect "Result: FAILED" shows "Result: FAILED"
expect "the interruption is reported" shows "interrupted by a signal"

if ((FAILURES > 0)); then
  printf '%s check(s) failed\n' "$FAILURES"
  exit 1
fi
echo "all checks passed"
