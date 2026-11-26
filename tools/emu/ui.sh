#!/usr/bin/env bash
# Drives the emulator UI (tools/emu/README.md).
#
#   tools/emu/ui.sh <port> launch                 starts NightBaseline (its launcher activity)
#   tools/emu/ui.sh <port> shot [name]            screenshot to build/emu-shots/<avd>/<name>.png
#   tools/emu/ui.sh <port> dump [name]            UI hierarchy to build/emu-shots/<avd>/<name>.xml + the text list
#   tools/emu/ui.sh <port> text-list              visible texts / content descriptions with their tap centres
#   tools/emu/ui.sh <port> tap <text>             taps the node whose text or description is <text> (exact, then
#                                                 case-insensitive substring); fails when none is visible
#   tools/emu/ui.sh <port> wait <text> [sec]      waits until <text> is visible (default 20 s)
#   tools/emu/ui.sh <port> tapxy <x> <y>
#   tools/emu/ui.sh <port> swipe up|down|left|right | <x1> <y1> <x2> <y2> [ms]
#   tools/emu/ui.sh <port> back | home
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
cmd="${2:-}"
shift 2 || true
require_emulator

dump_xml() {
  eadb shell uiautomator dump /sdcard/nb-ui.xml >/dev/null 2>&1 || true
  eadb exec-out cat /sdcard/nb-ui.xml 2>/dev/null || true
  eadb shell rm -f /sdcard/nb-ui.xml
}

# Reads a uiautomator dump on stdin. Mode "list": one line per node with text or description:
# `x,y<TAB>clickable<TAB>text<TAB>desc`. Mode "find <text>": prints `x y` of the best match.
parse() {
  python3 -I -c '
import re, sys, xml.etree.ElementTree as ET
mode = sys.argv[1]
raw = sys.stdin.read()
start = raw.find("<?xml")
if start < 0:
    sys.exit(3)
root = ET.fromstring(raw[start:raw.rfind(">") + 1])
nodes = []
for n in root.iter("node"):
    b = re.findall(r"\d+", n.get("bounds", ""))
    if len(b) != 4:
        continue
    x1, y1, x2, y2 = map(int, b)
    if x2 <= x1 or y2 <= y1:
        continue
    t, d = n.get("text", ""), n.get("content-desc", "")
    nodes.append(((x1 + x2) // 2, (y1 + y2) // 2, n.get("clickable") == "true", t, d))
if mode == "list":
    for x, y, c, t, d in nodes:
        if t or d:
            print("%d,%d\t%s\t%s\t%s" % (x, y, "click" if c else "-", t, d))
    sys.exit(0)
want = sys.argv[2]
exact = [n for n in nodes if want in (n[3], n[4])]
loose = [n for n in nodes if want.lower() in n[3].lower() or want.lower() in n[4].lower()]
hits = exact or loose
if not hits:
    sys.exit(1)
x, y = hits[0][0], hits[0][1]
print(x, y)
' "$@"
}

case "$cmd" in
  launch)
    eadb shell monkey -p "$NB_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    ;;
  shot)
    name="${1:-$(date +%Y%m%d-%H%M%S)}"
    out="$(shots_dir)/$name.png"
    display="$(active_display_id)"
    if [[ -n "$display" ]]; then
      eadb exec-out screencap -p -d "$display" >"$out"
    else
      eadb exec-out screencap -p >"$out"
    fi
    echo "${out#"$EMU_ROOT"/}"
    ;;
  dump)
    name="${1:-$(date +%Y%m%d-%H%M%S)}"
    out="$(shots_dir)/$name.xml"
    dump_xml >"$out"
    parse list <"$out" || die "no hierarchy (screen changing? try again)"
    echo "${out#"$EMU_ROOT"/}"
    ;;
  text-list)
    dump_xml | parse list || die "no hierarchy (screen changing? try again)"
    ;;
  tap)
    want="${1:?text}"
    xy=""
    for _ in 1 2 3; do
      xy="$(dump_xml | parse find "$want" || true)"
      [[ -n "$xy" ]] && break
      sleep 1
    done
    [[ -n "$xy" ]] || die "'$want' is not on screen"
    read -r x y <<<"$xy"
    eadb shell input tap "$x" "$y"
    echo "tapped '$want' at $x,$y"
    ;;
  wait)
    want="${1:?text}"
    limit="${2:-20}"
    end=$((SECONDS + limit))
    while ((SECONDS < end)); do
      if dump_xml | parse find "$want" >/dev/null 2>&1; then
        echo "'$want' visible"
        exit 0
      fi
      sleep 1
    done
    die "'$want' not visible within ${limit}s"
    ;;
  tapxy)
    eadb shell input tap "${1:?x}" "${2:?y}"
    ;;
  swipe)
    size="$(eadb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*x[0-9]*\)$/\1/p' | tail -1)"
    w="${size%x*}"
    h="${size#*x}"
    case "${1:?direction or coordinates}" in
      up) eadb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 300 ;;
      down) eadb shell input swipe $((w / 2)) $((h / 4)) $((w / 2)) $((h * 3 / 4)) 300 ;;
      left) eadb shell input swipe $((w * 4 / 5)) $((h / 2)) $((w / 5)) $((h / 2)) 300 ;;
      right) eadb shell input swipe $((w / 5)) $((h / 2)) $((w * 4 / 5)) $((h / 2)) 300 ;;
      *) eadb shell input swipe "$1" "${2:?y1}" "${3:?x2}" "${4:?y2}" "${5:-300}" ;;
    esac
    ;;
  back) eadb shell input keyevent KEYCODE_BACK ;;
  home) eadb shell input keyevent KEYCODE_HOME ;;
  *) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
