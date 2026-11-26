#!/usr/bin/env bash
# Exports the whole Health Connect history readable by the debug exporter (tools/hc-exporter) from the phone and
# merges it into the local dataset. Health data stays in the git-ignored .data/ folder.
#
#   mise exec -- tools/hc-export.sh            # build + install (if needed), grant, export, pull, merge
#   mise exec -- tools/hc-export.sh --no-build # reuse the installed exporter
#
# Read-only on the phone: the exporter declares only READ health permissions. Nothing is uninstalled or deleted.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB_WIN:-adb.exe}"
PKG="io.github.alien2003.nightbaseline.exporter"
DATA="$ROOT/.data"
BUILD=1
[[ "${1:-}" == "--no-build" ]] && BUILD=0

die() { echo "error: $*" >&2; exit 1; }
# Never leave the exporter (which keeps the screen on) in front, whatever happens.
trap '"$ADB" shell am force-stop "$PKG" >/dev/null 2>&1 || true' EXIT
adb() { "$ADB" "$@" | tr -d '\r'; }

state="$(adb get-state 2>/dev/null || true)"
[[ "$state" == "device" ]] || die "no authorised phone (adb get-state: '${state:-none}'). Connect it and allow USB debugging."

if (( BUILD )); then
  (cd "$ROOT" && ./gradlew :hc-exporter:assembleDebug -q --console=plain)
  APK="$ROOT/tools/hc-exporter/build/outputs/apk/debug/hc-exporter-debug.apk"
  echo "installing exporter"
  adb install -r "$(wslpath -w "$APK")" >/dev/null
fi

echo "granting READ permissions over adb (may be refused by the platform)"
perms=$(adb shell dumpsys package "$PKG" | sed -n 's/^ *\(android\.permission\.health\.READ_[A-Z_]*\).*/\1/p' | sort -u)
for p in $perms; do
  if out=$(adb shell pm grant "$PKG" "$p" 2>&1) && [[ -z "$out" ]]; then echo "  granted $p"; else echo "  NOT granted $p: ${out}"; fi
done

before=$(adb shell run-as "$PKG" cat files/export/latest.json 2>/dev/null || true)
adb shell input keyevent KEYCODE_WAKEUP >/dev/null || true
adb shell am start -S -n "$PKG/.ExportActivity" --ez autoExport true >/dev/null
echo "export started; waiting for it to finish (keep the phone unlocked)"
for _ in $(seq 1 360); do
  sleep 5
  now=$(adb shell run-as "$PKG" cat files/export/latest.json 2>/dev/null || true)
  if [[ -n "$now" && "$now" != "$before" ]]; then break; fi
done
[[ -n "${now:-}" && "$now" != "$before" ]] || die "export did not finish in 30 min (screen locked? permissions missing?)"
RUN=$(sed -n 's/.*"runId":"\([^"]*\)".*/\1/p' <<<"$now")
echo "run $RUN finished, pulling"

mkdir -p "$DATA/hc-dumps"
"$ADB" exec-out run-as "$PKG" tar cf - -C files/export "$RUN" > "$DATA/hc-dumps/$RUN.tar"
mkdir -p "$DATA/hc-dumps/$RUN"
tar xf "$DATA/hc-dumps/$RUN.tar" -C "$DATA/hc-dumps"
rm "$DATA/hc-dumps/$RUN.tar"
[[ -f "$DATA/hc-dumps/$RUN/manifest.json" ]] || die "pulled dump has no manifest.json"
# The exporter keeps the screen on while its window is in front: close it as soon as the dump is pulled, so the
# phone's display (OLED) is never left lit.
adb shell am force-stop "$PKG" >/dev/null || true

python3 -I "$ROOT/tools/hc-dataset.py" merge --data "$DATA"
