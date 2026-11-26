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

clock() {
  use_port "${1:?port}"
  require_emulator
  local arg="${2:?+<minutes> or sync}"
  eadb root >/dev/null 2>&1 || die "adb root is not available on this image"
  eadb wait-for-device
  if [[ "$arg" == "sync" ]]; then
    eadb shell date -u "@$(date -u +%s)" >/dev/null
    eadb shell settings put global auto_time 1
  elif [[ "$arg" =~ ^\+([0-9]+)$ ]]; then
    local now
    now="$(eadb shell date +%s | tr -d '\r')"
    eadb shell settings put global auto_time 0
    eadb shell date -u "@$((now + BASH_REMATCH[1] * 60))" >/dev/null
  else
    die "clock: +<minutes> or sync"
  fi
  eadb unroot >/dev/null 2>&1 || true
  eadb wait-for-device
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
