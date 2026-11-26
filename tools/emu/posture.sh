#!/usr/bin/env bash
# Foldable postures of the nb-fold emulator (Pixel 9 Pro Fold profile).
#
#   tools/emu/posture.sh <port> closed|half|open|rear|state
#
# closed / half / open move the emulated hinge (`adb emu posture 1|2|3`), which is what a real fold does: the
# device-state service follows the hinge (CLOSED = outer display 1080x2424, HALF_OPENED and OPENED = inner display
# 2076x2152). rear is REAR_DISPLAY_MODE, which has no hinge position: it is a device-state override
# (`cmd device_state state 3`) and is cleared by the next closed/half/open.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
want="${2:?closed|half|open|rear|state}"
require_emulator

state() { eadb shell cmd device_state state | tr -d '\r' | sed -n "s/.*name='\([A-Z_]*\)'.*/\1/p" | head -1; }

case "$want" in
  state)
    echo "$(state) $(eadb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //')"
    exit 0
    ;;
  closed | half | open)
    eadb shell cmd device_state state reset >/dev/null
    case "$want" in
      closed) eadb emu posture 1 >/dev/null ;;
      half) eadb emu posture 2 >/dev/null ;;
      open) eadb emu posture 3 >/dev/null ;;
    esac
    ;;
  rear) eadb shell cmd device_state state 3 >/dev/null ;;
  *) die "closed|half|open|rear|state" ;;
esac

expect=""
case "$want" in
  closed) expect=CLOSED ;;
  half) expect=HALF_OPENED ;;
  open) expect=OPENED ;;
  rear) expect=REAR_DISPLAY_MODE ;;
esac
for _ in $(seq 1 10); do
  [[ "$(state)" == "$expect" ]] && break
  sleep 1
done
now="$(state)"
[[ "$now" == "$expect" ]] || die "device state is $now, expected $expect (not a foldable AVD?)"
sleep 1
unlock
sleep 1
echo "$now $(eadb shell wm size | tr -d '\r' | tail -1 | sed 's/.*: //')"
