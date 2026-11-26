#!/usr/bin/env bash
# System display and accessibility preferences on an emulator.
#
#   tools/emu/prefs.sh <port> dark | light                 system dark theme (cmd uimode night yes|no)
#   tools/emu/prefs.sh <port> font <scale>                 font size, e.g. 0.85, 1, 1.3, 2 (system font_scale)
#   tools/emu/prefs.sh <port> bold 0|1                     bold text (secure font_weight_adjustment 300)
#   tools/emu/prefs.sh <port> density <dpi>|reset          display size (wm density)
#   tools/emu/prefs.sh <port> animations 0|1               all three animation scales ("Remove animations" = 0)
#   tools/emu/prefs.sh <port> reduce-blur 0|1              Accessibility > Color & motion > "Reduce blur effects"
#                                                          (global disable_window_blurs; verified on API 37)
#   tools/emu/prefs.sh <port> contrast <-1..1>             secure contrast_level read by UiModeManager.getContrast()
#                                                          (no Settings UI on this image; app effect unverified)
#   tools/emu/prefs.sh <port> outline-text 0|1             high-contrast / outline text
#   tools/emu/prefs.sh <port> show                         current values
#   tools/emu/prefs.sh <port> reset                        light, font 1, no bold, density reset, animations 1, blur on,
#                                                          contrast 0, outline text off
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
what="${2:?setting}"
val="${3:-}"
require_emulator

need() { [[ -n "$val" ]] || die "$what needs a value"; }
flag() { [[ "$val" == 0 || "$val" == 1 ]] || die "$what: 0 or 1"; }

animations() {
  local k
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    eadb shell settings put global "$k" "$1"
  done
}

show() {
  echo "night: $(eadb shell cmd uimode night | tr -d '\r')"
  echo "font_scale: $(eadb shell settings get system font_scale | tr -d '\r')"
  echo "font_weight_adjustment: $(eadb shell settings get secure font_weight_adjustment | tr -d '\r')"
  echo "density: $(eadb shell wm density | tr -d '\r' | tr '\n' ' ')"
  echo "animator_duration_scale: $(eadb shell settings get global animator_duration_scale | tr -d '\r')"
  echo "disable_window_blurs: $(eadb shell settings get global disable_window_blurs | tr -d '\r')"
  echo "contrast_level: $(eadb shell settings get secure contrast_level | tr -d '\r')"
  echo "high_text_contrast_enabled: $(eadb shell settings get secure high_text_contrast_enabled | tr -d '\r')"
}

case "$what" in
  dark) eadb shell cmd uimode night yes >/dev/null ;;
  light) eadb shell cmd uimode night no >/dev/null ;;
  font)
    need
    eadb shell settings put system font_scale "$val"
    ;;
  bold)
    flag
    eadb shell settings put secure font_weight_adjustment $((val * 300))
    ;;
  density)
    need
    if [[ "$val" == reset ]]; then eadb shell wm density reset; else eadb shell wm density "$val"; fi
    ;;
  animations)
    flag
    animations "$val"
    ;;
  reduce-blur)
    flag
    eadb shell settings put global disable_window_blurs "$val"
    ;;
  contrast)
    need
    eadb shell settings put secure contrast_level "$val"
    ;;
  outline-text)
    flag
    eadb shell settings put secure high_text_contrast_enabled "$val"
    ;;
  show) ;;
  reset)
    eadb shell cmd uimode night no >/dev/null
    eadb shell settings put system font_scale 1.0
    eadb shell settings put secure font_weight_adjustment 0
    eadb shell wm density reset
    animations 1
    eadb shell settings put global disable_window_blurs 0
    eadb shell settings put secure contrast_level 0
    eadb shell settings put secure high_text_contrast_enabled 0
    ;;
  *) die "unknown setting '$what' (see the header of $0)" ;;
esac
show
