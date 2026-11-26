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
# Prints the seeder's SEED_RESULT line; exits non-zero on an error. The line is tied to this run by a run id
# (`--es run_id`, echoed as ` run=<id>`), so older lines in the log never count, even after the clock was set back.
# late_upload: NightBaseline waits for the missing channels only within 12 h of the night's end (then CAP_REACHED);
# the script warns when the seeded night already ended 11 h or more ago.
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

# A run id instead of `logcat -T <time>`: -T filters by wall-clock timestamps, so after the emulator clock was set
# back an older run's line passes it. `logcat -c` is not used either: diag.sh reads the same buffer.
run_id="$(date +%s)-$$-$RANDOM"
extras+=(--es run_id "$run_id")
eadb shell am start -W -n "$SEEDER_ACTIVITY" "${extras[@]}" >/dev/null
result=""
for _ in $(seq 1 180); do
  result="$(eadb logcat -d -s NbSeeder:I 2>/dev/null | tr -d '\r' | grep -o "SEED_RESULT .* run=$run_id\$" | tail -1 || true)"
  [[ -n "$result" ]] && break
  sleep 1
done
# Leave nothing of the seeder on screen.
eadb shell am force-stop "$SEEDER_PKG"
[[ -n "$result" ]] ||
  die "no SEED_RESULT for run $run_id within 180 s (see: adb logcat -s NbSeeder; a seeder older than this script? reinstall: tools/emu/install.sh $PORT)"
result="${result% run=*}"
echo "$result"
[[ "$result" == "SEED_RESULT ok"* ]] || exit 1
if [[ "$scenario" == "late_upload" && "$result" =~ last_session_end_s=([0-9]+) ]]; then
  end_s="${BASH_REMATCH[1]}"
  ended=$(($(eadb shell date +%s | tr -d '\r') - end_s))
  if ((ended >= 11 * 3600)); then
    echo "warning: the seeded night ended $((ended / 3600)) h ago. NightBaseline stops waiting for missing channels 12 h" \
      "after a night's end (CAP_REACHED) and then evaluates without RR/SpO2 instead of showing \"waits: channel" \
      "incomplete\". For the wait: move the emulator clock to the next morning after 10:00 (emu.sh clock $PORT +<min>)" \
      "and seed late_upload again." >&2
  fi
fi
