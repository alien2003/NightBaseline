#!/usr/bin/env bash
# Multi-window checks for NightBaseline on an emulator (verified on the API 37 images, see README).
#
#   tools/emu/multiwindow.sh <port> split [left|right]   NightBaseline beside Settings (WM Shell split screen)
#   tools/emu/multiwindow.sh <port> freeform [<w>x<h>]   NightBaseline as a desktop (freeform) window, optional size
#   tools/emu/multiwindow.sh <port> resize <w>x<h>       display size override (`wm size`): any window width class
#   tools/emu/multiwindow.sh <port> reset                fullscreen again, no split, display size reset
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
mode="${2:?split|freeform|resize|reset}"
arg="${3:-}"
require_emulator

shell_cmd() { eadb shell dumpsys activity service SystemUIService WMShell "$@" >/dev/null; }

nb_task() {
  eadb shell am stack list | tr -d '\r' | sed -n "s/.*taskId=\([0-9]*\): $NB_PKG\/.*/\1/p" | head -1
}

launch_nb() {
  eadb shell am start -W -n "$NB_PKG/.MainActivity" "$@" >/dev/null
  sleep 2
  local t
  t="$(nb_task)"
  [[ -n "$t" ]] || die "NightBaseline task not found"
  echo "$t"
}

bounds() { eadb shell am stack list | tr -d '\r' | grep "taskId=$1:" | grep -o 'bounds=[^ ]*' | head -1; }

case "$mode" in
  split)
    # Settings first, then NightBaseline moved into the side stage (position 0 = left/top, 1 = right/bottom).
    pos=1
    [[ "$arg" == "left" ]] && pos=0
    eadb shell am start -W -a android.settings.SETTINGS >/dev/null
    t="$(launch_nb)"
    shell_cmd splitscreen moveToSideStage "$t" "$pos"
    sleep 2
    echo "split: NightBaseline task $t $(bounds "$t")"
    ;;
  freeform)
    eadb shell settings put global enable_freeform_support 1
    eadb shell settings put global force_resizable_activities 1
    t="$(launch_nb --windowingMode 5)"
    if [[ -n "$arg" ]]; then
      [[ "$arg" =~ ^([0-9]+)x([0-9]+)$ ]] || die "size as <w>x<h>"
      eadb shell am task resize "$t" 100 150 $((100 + BASH_REMATCH[1])) $((150 + BASH_REMATCH[2]))
      sleep 1
    fi
    echo "freeform: NightBaseline task $t $(bounds "$t")"
    ;;
  resize)
    [[ "$arg" =~ ^[0-9]+x[0-9]+$ ]] || die "size as <w>x<h>"
    eadb shell wm size "$arg"
    sleep 2
    echo "display: $(eadb shell wm size | tr -d '\r' | tr '\n' ' ')"
    ;;
  reset)
    t="$(nb_task)"
    if [[ -n "$t" ]]; then
      shell_cmd splitscreen exitSplitScreen "$t" || true
      shell_cmd desktopmode moveTaskOutOfDesk "$t" || true
    fi
    eadb shell wm size reset
    sleep 2
    [[ -n "$t" ]] && echo "NightBaseline task $t $(bounds "$t")"
    echo "display: $(eadb shell wm size | tr -d '\r' | tail -1)"
    ;;
  *) die "split|freeform|resize|reset" ;;
esac
