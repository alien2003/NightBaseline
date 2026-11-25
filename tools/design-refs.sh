#!/usr/bin/env bash
# Captures design reference material from system apps on the phone into the git-ignored .data/design-refs/<posture>/:
# screenshots, UI hierarchy dumps (bounds in px) and short screen recordings of a scroll and a back transition.
# Only neutral screens without personal data; nothing is tapped inside the apps (only scroll gestures and BACK).
# NEVER commit the output: it contains OPPO's UI.
#
#   tools/design-refs.sh <posture-label>     e.g. folded, unfolded
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB_WIN:-adb.exe}"
LABEL="${1:?posture label}"
OUT="$ROOT/.data/design-refs/$LABEL"
mkdir -p "$OUT"
adb() { "$ADB" "$@" | tr -d '\r'; }

size=$(adb shell wm size | sed -n 's/.*: \([0-9]*x[0-9]*\)$/\1/p' | tail -1)
W=${size%x*}; H=${size#*x}
{
  echo "posture=$LABEL size=$size"
  adb shell wm density
  adb shell cmd device_state state 2>/dev/null | head -3 || true
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do echo "$k=$(adb shell settings get global $k)"; done
  echo "font_scale=$(adb shell settings get system font_scale)"
  echo "ui_night_mode=$(adb shell cmd uimode night)"
} > "$OUT/env.txt"

shot() {
  local name="$1"
  sleep 2
  "$ADB" exec-out screencap -p > "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/nb-ui.xml >/dev/null 2>&1 || true
  "$ADB" exec-out cat /sdcard/nb-ui.xml > "$OUT/$name.xml" 2>/dev/null || true
  adb shell rm -f /sdcard/nb-ui.xml
}

scroll_rec() {
  local name="$1"
  adb shell "screenrecord --time-limit 5 /sdcard/nb-rec.mp4" &
  local pid=$!
  sleep 1
  adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 250
  sleep 1.5
  adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 250
  wait $pid || true
  "$ADB" exec-out cat /sdcard/nb-rec.mp4 > "$OUT/$name-scroll.mp4"
  adb shell rm -f /sdcard/nb-rec.mp4
}

open() { adb shell am start -W "$@" >/dev/null; }

# Settings sub-pages (the main page shows the account, so it is skipped).
open -a android.settings.DISPLAY_SETTINGS;       shot settings-display;  scroll_rec settings-display
open -a android.settings.SOUND_SETTINGS;         shot settings-sound
open -a android.settings.ACCESSIBILITY_SETTINGS; shot settings-accessibility
open -a android.settings.DATE_SETTINGS;          shot settings-date
# Back transition from a sub-page (recorded).
adb shell "screenrecord --time-limit 4 /sdcard/nb-rec.mp4" & rp=$!
sleep 1; adb shell input keyevent KEYCODE_BACK; wait $rp || true
"$ADB" exec-out cat /sdcard/nb-rec.mp4 > "$OUT/settings-back.mp4"; adb shell rm -f /sdcard/nb-rec.mp4

for pkg in com.coloros.alarmclock com.coloros.calculator com.coloros.compass2 com.google.android.healthconnect.controller; do
  adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  shot "${pkg##*.}"
done
open -a android.health.connect.action.HEALTH_HOME_SETTINGS; shot healthconnect-home
adb shell input keyevent KEYCODE_HOME
shot launcher-home-hidden-not-for-analysis || true
rm -f "$OUT/launcher-home-hidden-not-for-analysis".*
echo "captured $(ls "$OUT" | wc -l) files into $OUT"
