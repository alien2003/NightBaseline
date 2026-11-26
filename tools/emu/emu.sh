#!/usr/bin/env bash
# Starts and stops the project's headless emulators (tools/emu/README.md).
#
#   tools/emu/emu.sh start <avd> [port]     headless start, waits for boot, prepares the device for testing
#   tools/emu/emu.sh stop <port>            graceful shutdown (kills the process if the console does not answer)
#   tools/emu/emu.sh list                   running emulators started by the bench
#   tools/emu/emu.sh clock <port> +<min>    moves the emulator clock forward (root; turns automatic time off)
#   tools/emu/emu.sh clock <port> sync      back to the host clock, automatic time on
#
# Default ports: nb-fold 5560, nb-phone 5562, nb-tablet 5564, nb-api28 5566.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

default_port() {
  case "$1" in
    nb-fold) echo 5560 ;;
    nb-phone) echo 5562 ;;
    nb-tablet) echo 5564 ;;
    nb-api28) echo 5566 ;;
    *) echo 5570 ;;
  esac
}

start() {
  local avd="${1:?avd name (nb-fold, nb-phone, nb-tablet, nb-api28)}"
  [[ -f "$ANDROID_AVD_HOME/$avd.ini" ]] || die "no AVD '$avd' in $ANDROID_AVD_HOME"
  use_port "${2:-$(default_port "$avd")}"
  if [[ "$(eadb get-state 2>/dev/null || true)" == "device" ]]; then
    require_emulator
    prepare
    echo "$SERIAL is already running ($(avd_name)); prepared it again"
    return 0
  fi
  mkdir -p "$EMU_STATE"
  local log="$EMU_STATE/$avd-$PORT.log"
  echo "starting $avd on $SERIAL (log: ${log#"$EMU_ROOT"/})"
  nohup "$ANDROID_HOME/emulator/emulator" -avd "$avd" -port "$PORT" -no-window -gpu swiftshader_indirect -no-audio \
    -no-boot-anim -no-snapshot >"$log" 2>&1 &
  echo $! >"$EMU_STATE/$PORT.pid"
  echo "$avd" >"$EMU_STATE/$PORT.avd"
  local start=$SECONDS waited=0 booted=""
  until [[ "$booted" == "1" ]]; do
    kill -0 "$(cat "$EMU_STATE/$PORT.pid")" 2>/dev/null || die "emulator exited during boot; see $log"
    ((waited < 420)) || die "boot did not complete within 420 s; see $log"
    sleep 3
    # The API 28 image's first adb shell session can hang forever; a reconnect unblocks it.
    if ! booted="$(timeout 15 "$ADB_BIN" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"; then
      if [[ "$(eadb get-state 2>/dev/null || true)" == "device" ]]; then
        timeout 15 "$ADB_BIN" -s "$SERIAL" reconnect >/dev/null 2>&1 || true
      fi
      booted=""
    fi
    waited=$((SECONDS - start))
  done
  require_emulator
  prepare
  echo "$SERIAL booted in ${waited}s: $(avd_name), API $(sdk_int), $(eadb shell wm size | tr -d '\r' | tail -1)"
}

# Deterministic test state: real animations (scale 1), no auto-rotation (rotate.sh sets the rotation), screen on and
# unlocked, no keyguard.
prepare() {
  local k
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    eadb shell settings put global "$k" 1
  done
  eadb shell settings put system accelerometer_rotation 0
  eadb shell settings put system user_rotation 0
  eadb shell settings put global stay_on_while_plugged_in 7
  eadb shell settings put secure lockscreen.disabled 1 >/dev/null 2>&1 || true
  eadb shell locksettings set-disabled true >/dev/null 2>&1 || true
  unlock
}

stop() {
  use_port "${1:?port}"
  local pidfile="$EMU_STATE/$PORT.pid" pid=""
  [[ -f "$pidfile" ]] && pid="$(cat "$pidfile")"
  if [[ "$(eadb get-state 2>/dev/null || true)" == "device" ]]; then
    eadb emu kill >/dev/null 2>&1 || true
  fi
  if [[ -n "$pid" ]]; then
    local i
    for i in $(seq 1 30); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
      if ((i == 20)); then kill "$pid" 2>/dev/null || true; fi
    done
  fi
  rm -f "$pidfile" "$EMU_STATE/$PORT.avd"
  echo "$SERIAL stopped"
}

list() {
  local f p
  shopt -s nullglob
  for f in "$EMU_STATE"/*.pid; do
    p="$(basename "$f" .pid)"
    if kill -0 "$(cat "$f")" 2>/dev/null; then
      echo "emulator-$p $(cat "$EMU_STATE/$p.avd" 2>/dev/null || echo '?') pid $(cat "$f")"
    fi
  done
}

# Waits until adbd answers again after a restart: state 'device' and sys.boot_completed 1 (at most ~60 s).
wait_adbd() {
  local i
  for i in $(seq 1 30); do
    if [[ "$(eadb get-state 2>/dev/null || true)" == "device" ]] &&
      [[ "$(timeout 10 "$ADB_BIN" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  die "$SERIAL did not come back after the adbd restart"
}

# Restarts adbd as root ($1 = root, expects uid 0) or as shell ($1 = unroot, expects uid 2000) and returns once it
# really runs that way and answers. `adb root` / `unroot` restart adbd: right after boot or other adb traffic the
# command can fail for a passing reason ("error: closed") although the restart has begun, and `wait-for-device` can
# return while the old adbd is still up. So: retry, and check the uid instead of trusting the exit code.
adbd_as() {
  local mode="$1" want="$2" i out="" uid=""
  for i in $(seq 1 10); do
    out="$(eadb "$mode" 2>&1 || true)"
    if [[ "$out" == *"not available"* || "$out" == *"cannot run as root"* ]]; then
      die "adb root is not available on this image: $out"
    fi
    timeout 30 "$ADB_BIN" -s "$SERIAL" wait-for-device >/dev/null 2>&1 || true
    uid="$(timeout 10 "$ADB_BIN" -s "$SERIAL" shell id -u 2>/dev/null | tr -d '\r' || true)"
    if [[ "$uid" == "$want" ]]; then
      wait_adbd
      return 0
    fi
    sleep 2
  done
  die "adb $mode did not take effect on $SERIAL (uid '${uid:-?}', last adb output: ${out:-none})"
}

# adb root restarts adbd twice (root, then unroot); clock returns only once adbd answers again.
clock() {
  use_port "${1:?port}"
  require_emulator
  local arg="${2:?+<minutes> or sync}"
  [[ "$arg" == "sync" || "$arg" =~ ^\+[0-9]+$ ]] || die "clock: +<minutes> or sync"
  adbd_as root 0
  if [[ "$arg" == "sync" ]]; then
    eadb shell date -u "@$(date -u +%s)" >/dev/null
    eadb shell settings put global auto_time 1
  elif [[ "$arg" =~ ^\+([0-9]+)$ ]]; then
    local now
    now="$(eadb shell date +%s | tr -d '\r')"
    eadb shell settings put global auto_time 0
    eadb shell date -u "@$((now + BASH_REMATCH[1] * 60))" >/dev/null
  fi
  adbd_as unroot 2000
  echo "$SERIAL clock: $(eadb shell date | tr -d '\r')"
}

cmd="${1:-}"
shift || true
case "$cmd" in
  start) start "$@" ;;
  stop) stop "$@" ;;
  list) list ;;
  clock) clock "$@" ;;
  *) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
