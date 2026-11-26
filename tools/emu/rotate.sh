#!/usr/bin/env bash
# Locks the display rotation of an emulator.
#
#   tools/emu/rotate.sh <port> 0|1|2|3     0 natural, 1 = 90°, 2 = 180°, 3 = 270°
#   tools/emu/rotate.sh <port> free        auto-rotation back on
#
# API 29+: `wm user-rotation lock <n>` (auto-rotation off). API 28 has no `wm user-rotation`: the script writes the
# settings system accelerometer_rotation 0 / user_rotation <n> instead. Either way it then checks (up to ~5 s) that the
# settings hold the requested values, writes them again once if not, and fails if they still do not, so a scripted run
# never takes screenshots in the wrong orientation. A display rotation that differs from the lock is only reported:
# apps that fix their orientation may still be letterboxed or rotated by the system; that is what the check is for.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
r="${2:?0|1|2|3|free}"
require_emulator
[[ "$r" =~ ^([0-3]|free)$ ]] || die "0|1|2|3|free"
sdk="$(sdk_int)"

setting() { eadb shell settings get system "$1" | tr -d '\r'; }

# The display rotation (0..3) WindowManager reports, or empty when the dump has no recognisable line. The field name
# differs between releases (mRotation= on API 28, also mCurrentRotation= / ROTATION_<deg> on newer ones).
display_rotation() {
  local v
  v="$(eadb shell dumpsys window displays | tr -d '\r' |
    grep -o -E '(mCurrentRotation|mRotation)=(ROTATION_)?[0-9]+' | head -1 | grep -o -E '[0-9]+$' || true)"
  case "$v" in
    0 | 1 | 2 | 3) echo "$v" ;;
    90 | 180 | 270) echo $((v / 90)) ;;
    *) echo "" ;;
  esac
}

# Applies the lock (or free). Settings path on API < 29, or when `wm user-rotation` fails.
apply() {
  if [[ "$r" == "free" ]]; then
    if ((sdk < 29)) || ! eadb shell wm user-rotation free >/dev/null 2>&1; then
      eadb shell settings put system accelerometer_rotation 1
    fi
  elif ((sdk < 29)) || ! eadb shell wm user-rotation lock "$r" >/dev/null 2>&1; then
    eadb shell settings put system accelerometer_rotation 0
    eadb shell settings put system user_rotation "$r"
  fi
}

# True once the settings hold the requested state (polls up to ~5 s).
settled() {
  for _ in $(seq 1 10); do
    if [[ "$r" == "free" ]]; then
      [[ "$(setting accelerometer_rotation)" == "1" ]] && return 0
    elif [[ "$(setting accelerometer_rotation)" == "0" && "$(setting user_rotation)" == "$r" ]]; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

apply
if ! settled; then
  # A write the WindowManager settings observer missed: change the value for real, then write it again.
  if [[ "$r" == "free" ]]; then
    eadb shell settings put system accelerometer_rotation 0
    eadb shell settings put system accelerometer_rotation 1
  else
    eadb shell settings put system accelerometer_rotation 0
    eadb shell settings put system user_rotation $(((r + 1) % 4))
    eadb shell settings put system user_rotation "$r"
  fi
  settled || die "rotation $r did not take effect: accelerometer_rotation=$(setting accelerometer_rotation)" \
    "user_rotation=$(setting user_rotation)"
fi

shown=""
if [[ "$r" != "free" ]]; then
  for _ in $(seq 1 10); do
    shown="$(display_rotation)"
    [[ -z "$shown" || "$shown" == "$r" ]] && break
    sleep 0.5
  done
else
  sleep 1
  shown="$(display_rotation)"
fi
cur="$(eadb shell dumpsys window displays | tr -d '\r' | grep -o 'cur=[0-9]*x[0-9]*' | head -1)"
echo "rotation $r: accelerometer_rotation=$(setting accelerometer_rotation) user_rotation=$(setting user_rotation)" \
  "display=${shown:-?} $cur"
if [[ "$r" != "free" && -n "$shown" && "$shown" != "$r" ]]; then
  echo "note: the display shows rotation $shown, not $r (an app with a fixed orientation in front?)" >&2
fi
