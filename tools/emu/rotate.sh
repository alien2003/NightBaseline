#!/usr/bin/env bash
# Locks the display rotation of an emulator.
#
#   tools/emu/rotate.sh <port> 0|1|2|3     0 natural, 1 = 90°, 2 = 180°, 3 = 270°
#   tools/emu/rotate.sh <port> free        auto-rotation back on
#
# Uses `wm user-rotation lock <n>` (auto-rotation off). Apps that fix their orientation may still be letterboxed or
# rotated by the system; that is what the check is for.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
r="${2:?0|1|2|3|free}"
require_emulator
case "$r" in
  0 | 1 | 2 | 3)
    if ! eadb shell wm user-rotation lock "$r" >/dev/null 2>&1; then
      eadb shell settings put system accelerometer_rotation 0
      eadb shell settings put system user_rotation "$r"
    fi
    ;;
  free)
    eadb shell wm user-rotation free >/dev/null 2>&1 || eadb shell settings put system accelerometer_rotation 1
    ;;
  *) die "0|1|2|3|free" ;;
esac
sleep 1
echo "rotation $(eadb shell wm user-rotation 2>/dev/null | tr -d '\r' | head -1): $(eadb shell dumpsys window displays | tr -d '\r' | grep -o 'cur=[0-9]*x[0-9]*' | head -1)"
