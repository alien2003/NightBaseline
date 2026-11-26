# shellcheck shell=bash
# Shared helpers of the emulator test bench (tools/emu/README.md). Sourced, never run.
#
# Safety: every adb call goes through `eadb`, which always addresses `emulator-<port>` with the Linux adb of the
# project SDK and refuses a device that is not an emulator. The phone is never addressed from here (CLAUDE.md, rules 4
# and 10).
set -euo pipefail

EMU_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$EMU_ROOT/.tooling/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$EMU_ROOT/.tooling/android-user}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$ANDROID_USER_HOME/avd}"
export ANDROID_EMULATOR_HOME="${ANDROID_EMULATOR_HOME:-$ANDROID_USER_HOME}"
ADB_BIN="$ANDROID_HOME/platform-tools/adb"
EMU_STATE="$EMU_ROOT/build/emu"
# shellcheck disable=SC2034 # used by the scripts that source this file
NB_PKG="io.github.alien2003.nightbaseline"
SEEDER_PKG="io.github.alien2003.nightbaseline.seeder"
# shellcheck disable=SC2034
SEEDER_ACTIVITY="$SEEDER_PKG/.SeedActivity"

die() {
  echo "error: $*" >&2
  exit 1
}

# Validates an emulator console port and sets PORT and SERIAL. adb scans only adb ports 5555..5585, i.e. console
# ports 5554..5584; the emulator accepts higher ones but then warns "ADB may not function properly" and the first adb
# calls can hang.
use_port() {
  local p="${1:-}"
  [[ "$p" =~ ^[0-9]+$ ]] || die "port expected (e.g. 5560), got '$p'"
  ((p >= 5554 && p <= 5584 && p % 2 == 0)) ||
    die "emulator console ports are even numbers in 5554..5584 (adb scans adb ports 5555..5585), got $p"
  PORT="$p"
  SERIAL="emulator-$p"
}

# adb against the selected emulator only (call use_port first).
eadb() {
  [[ -n "${SERIAL:-}" && "$SERIAL" == emulator-* ]] || die "no emulator selected"
  "$ADB_BIN" -s "$SERIAL" "$@"
}

# Fails unless SERIAL is a running emulator (ro.kernel.qemu / ro.boot.qemu = 1).
require_emulator() {
  local state qemu
  state="$(eadb get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || die "$SERIAL is not running (adb state '${state:-none}'); start it with tools/emu/emu.sh start <avd> $PORT"
  qemu="$(eadb shell 'getprop ro.kernel.qemu; getprop ro.boot.qemu' 2>/dev/null | tr -d '\r' | tr -d '\n')"
  [[ "$qemu" == *1* ]] || die "$SERIAL does not report itself as an emulator: refusing"
}

# The AVD name of the selected emulator (for output folders).
avd_name() {
  local name
  name="$(eadb emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
  if [[ -z "$name" || "$name" == "KO"* ]]; then
    name="$(cat "$EMU_STATE/$PORT.avd" 2>/dev/null || echo "emulator-$PORT")"
  fi
  echo "$name"
}

shots_dir() {
  local dir
  dir="$EMU_ROOT/build/emu-shots/$(avd_name)"
  mkdir -p "$dir"
  echo "$dir"
}

# Wakes the screen and dismisses the (insecure) keyguard: a fold change locks the device like a power press does.
unlock() {
  eadb shell input keyevent KEYCODE_WAKEUP
  eadb shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

# Physical id of the first display that is on (foldables have two; screencap needs -d then). Empty on one display.
active_display_id() {
  local ids
  ids="$(eadb shell dumpsys display | tr -d '\r' | grep 'DisplayDeviceInfo{' | grep -c 'uniqueId="local:' || true)"
  ((ids > 1)) || return 0
  eadb shell dumpsys display | tr -d '\r' | grep 'DisplayDeviceInfo{' | grep ', state ON,' |
    sed -n 's/.*uniqueId="local:\([0-9]*\)".*/\1/p' | head -1
}

sdk_int() { eadb shell getprop ro.build.version.sdk | tr -d '\r'; }

# Runs Gradle through mise from the repository root.
gradle() {
  (cd "$EMU_ROOT" && MISE_TRUSTED_CONFIG_PATHS="${MISE_TRUSTED_CONFIG_PATHS:-$EMU_ROOT}" mise exec -- ./gradlew "$@" --console=plain)
}
