#!/usr/bin/env bash
# Writes a synthetic scenario into Health Connect on an emulator through the seeder app (tools/hc-seeder).
#
#   tools/emu/seed.sh <port> <scenario> [nights] [--now <iso>] [--end-days-ago <n>]
#   tools/emu/seed.sh <port> append_tail     late upload: the last late_upload seed's RR + SpO2 of the last night
#   tools/emu/seed.sh <port> wipe            deletes everything the seeder wrote (its own data origin only)
#
# Scenarios: normal deviations learning learning6 short_last ambiguous no_spo2_rr gap hrv_skin late_upload.
# Every seed replaces the previous one. --now is an ISO instant (2026-11-26T08:30:00Z) or local date-time
# (2026-11-26T08:30); default: the emulator clock. --end-days-ago moves the newest night that many days back.
# Prints the seeder's SEED_RESULT line; exits non-zero on an error.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
scenario="${2:?scenario}"
shift 2
extras=()
case "$scenario" in
  append_tail) extras+=(--es action append_tail) ;;
  wipe) extras+=(--es action wipe) ;;
  *) extras+=(--es scenario "$scenario") ;;
esac
while (($#)); do
  case "$1" in
    --now) extras+=(--es now "${2:?iso time}"); shift 2 ;;
    --end-days-ago) extras+=(--ei end_days_ago "${2:?days}"); shift 2 ;;
    [0-9]*) extras+=(--ei nights "$1"); shift ;;
    *) die "unknown argument '$1'" ;;
  esac
done
require_emulator
eadb shell pm path "$SEEDER_PKG" >/dev/null 2>&1 || die "seeder not installed: tools/emu/install.sh $PORT"

since="$(eadb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
eadb shell am start -W -n "$SEEDER_ACTIVITY" "${extras[@]}" >/dev/null
result=""
for _ in $(seq 1 180); do
  result="$(eadb logcat -d -T "$since" -s NbSeeder:I 2>/dev/null | tr -d '\r' | grep -o 'SEED_RESULT.*' | tail -1 || true)"
  [[ -n "$result" ]] && break
  sleep 1
done
# Leave nothing of the seeder on screen.
eadb shell am force-stop "$SEEDER_PKG"
[[ -n "$result" ]] || die "no SEED_RESULT within 180 s (see: adb logcat -s NbSeeder)"
echo "$result"
[[ "$result" == "SEED_RESULT ok"* ]]
