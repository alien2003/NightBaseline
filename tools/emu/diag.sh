#!/usr/bin/env bash
# Diagnostics of NightBaseline on an emulator: its night-check log, crashes and ANRs, frame stats, scheduled work.
#
#   tools/emu/diag.sh <port> [--reset-gfx]
#
# Writes the full report to build/emu-shots/<avd>/diag-<time>.txt and prints a short summary.
# --reset-gfx clears the frame statistics afterwards (so the next run measures only what happens in between).
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
reset_gfx=0
[[ "${2:-}" == "--reset-gfx" ]] && reset_gfx=1
require_emulator

# Filters `dumpsys jobscheduler` output on stdin to the JOB blocks of package $1.
package_jobs() {
  awk -v comp="$1/" '
    { lead = match($0, /[^ ]/) }
    /^ *JOB #/ { keep = index($0, comp) > 0; depth = lead; if (keep) print; next }
    keep && lead > depth { print; next }
    { keep = 0 }
  '
}

out="$(shots_dir)/diag-$(date +%Y%m%d-%H%M%S).txt"
pid="$(eadb shell pidof "$NB_PKG" 2>/dev/null | tr -d '\r' || true)"
{
  echo "== device"
  echo "$(avd_name) API $(sdk_int); $(eadb shell wm size | tr -d '\r' | tail -1); clock $(eadb shell date | tr -d '\r')"
  echo "NightBaseline $(eadb shell dumpsys package "$NB_PKG" | tr -d '\r' | grep -m1 versionName | sed 's/^ *//'); pid ${pid:-not running}"
  echo
  echo "== night-check log (tag NightBaseline)"
  eadb logcat -d -v time -s NightBaseline:V | tr -d '\r'
  echo
  echo "== crashes (crash buffer, this package)"
  eadb logcat -d -b crash -v time | tr -d '\r' | grep -A40 "Process: $NB_PKG" || echo "none"
  echo
  echo "== AndroidRuntime FATAL / ANR in main log"
  eadb logcat -d -v time | tr -d '\r' | grep -E "FATAL EXCEPTION|ANR in $NB_PKG" -A15 | grep -B2 -A15 "$NB_PKG" || echo "none"
  echo
  echo "== dropbox crash/ANR entries for the package"
  eadb shell dumpsys dropbox --print data_app_crash data_app_anr 2>/dev/null | tr -d '\r' | grep -B3 -A25 "Process: $NB_PKG" || echo "none"
  echo
  echo "== gfxinfo"
  eadb shell dumpsys gfxinfo "$NB_PKG" | tr -d '\r' | sed -n '/Total frames rendered/,/Number Frame deadline missed/p'
  echo
  echo "== scheduled work (jobscheduler, WorkManager's SystemJobService)"
  # `dumpsys jobscheduler <pkg>` does not filter on every release (API 28 lists every job), so keep only the JOB
  # blocks whose component belongs to the package: from a `JOB #u0a88/0: <hash> <pkg>/<service>` line through the
  # lines indented deeper than it.
  jobs_out="$(eadb shell dumpsys jobscheduler "$NB_PKG" 2>/dev/null | tr -d '\r' | package_jobs "$NB_PKG" |
    grep -E "JOB |Required constraints|Satisfied constraints|Unsatisfied|Run time|Enqueue time|Last run|Ready:|Pending" |
    head -60 || true)"
  echo "${jobs_out:-none}"
  echo
  echo "== alarms of the package"
  eadb shell dumpsys alarm | tr -d '\r' | grep -A3 "$NB_PKG" | head -30 || true
} >"$out" 2>&1

if ((reset_gfx)); then eadb shell dumpsys gfxinfo "$NB_PKG" reset >/dev/null; fi

echo "report: ${out#"$EMU_ROOT"/}"
echo "last night-check lines:"
grep -E ' I/NightBaseline' "$out" | tail -6 | sed 's/^/  /'
crash="$(sed -n '/^== crashes/,/^== AndroidRuntime/p' "$out" | grep -c "FATAL EXCEPTION" || true)"
anr="$(grep -c "ANR in $NB_PKG" "$out" || true)"
echo "crashes: $crash, ANRs: $anr"
grep -E "Total frames rendered|Janky frames" "$out" | head -2 | sed 's/^/  /'
jobs="$(sed -n '/^== scheduled work/,/^== alarms/p' "$out" | grep -c '^  JOB #' || true)"
echo "scheduled jobs: $jobs"
