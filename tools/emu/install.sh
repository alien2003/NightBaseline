#!/usr/bin/env bash
# Builds and installs NightBaseline and the synthetic seeder on an emulator, then grants health permissions.
#
#   tools/emu/install.sh <port> [debug|e2e|release] [--no-build] [--no-grant] [--fresh]
#
#   debug     (default) the debug build
#   e2e       the e2e build type (release code without R8), re-signed with the local debug key
#   release   the minified release build (R8), re-signed with the local debug key
#   --no-build  install the APKs already built
#   --no-grant  grant NightBaseline nothing: onboarding then goes through the Health Connect permission screen (the
#               seeder is still granted)
#   --fresh     uninstall NightBaseline first (clean onboarding; also needed when switching the signing key)
#   --no-visibility-fix  keep Android's package-visibility filtering for NightBaseline (see below)
#
# Package visibility: NightBaseline's manifest <queries> lists only Health Connect and OHealth, so on Android 11+ it
# cannot see any other source's package; the seeder then shows as its package name and Home reports it as "no longer
# on this phone". Until the app handles that, the bench disables the FILTER_APPLICATION_QUERY compat change for
# NightBaseline (emulator only; `am compat`, works for debuggable apps and on userdebug images).
#
# Grants: NightBaseline gets its whole READ set (incl. history and background reads, where the feature exists) and
# POST_NOTIFICATIONS (unless --no-grant); the seeder always gets WRITE + READ for the types it writes.
# shellcheck source=tools/emu/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

use_port "${1:?port}"
shift
variant="debug"
build=1
grant=1
fresh=0
visibility_fix=1
for a in "$@"; do
  case "$a" in
    debug | e2e | release) variant="$a" ;;
    --no-build) build=0 ;;
    --no-grant) grant=0 ;;
    --fresh) fresh=1 ;;
    --no-visibility-fix) visibility_fix=0 ;;
    *) die "unknown argument '$a'" ;;
  esac
done
require_emulator

task="assemble${variant^}"
if ((build)); then
  gradle ":app:$task" ":hc-seeder:assembleDebug" -q
fi

app_dir="$EMU_ROOT/app/build/outputs/apk/$variant"
seeder_apk="$EMU_ROOT/tools/hc-seeder/build/outputs/apk/debug/hc-seeder-debug.apk"
[[ -f "$seeder_apk" ]] || die "seeder APK missing: build without --no-build"
app_apk="$(find "$app_dir" -maxdepth 1 -name '*.apk' 2>/dev/null | sort | head -1)"
[[ -n "$app_apk" ]] || die "no APK in ${app_dir#"$EMU_ROOT"/}"

if [[ "$variant" != "debug" ]]; then
  # Without the release key the build is unsigned (or signed with a key the emulator need not know): re-sign with
  # the local debug key so it installs. Never distribute this APK.
  bt="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
  signed="$EMU_STATE/nightbaseline-$variant-debugkey.apk"
  mkdir -p "$EMU_STATE"
  "$bt/zipalign" -f -p 4 "$app_apk" "$signed.aligned"
  "$bt/apksigner" sign --ks "$ANDROID_USER_HOME/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$signed" "$signed.aligned"
  rm -f "$signed.aligned" "$signed.idsig"
  app_apk="$signed"
fi

if ((fresh)); then
  eadb uninstall "$NB_PKG" >/dev/null 2>&1 || true
fi
echo "installing ${app_apk#"$EMU_ROOT"/}"
if ! eadb install -r -t "$app_apk"; then
  echo "install failed: probably another signing key; retrying after uninstalling NightBaseline" >&2
  eadb uninstall "$NB_PKG" >/dev/null
  eadb install -r -t "$app_apk"
fi
echo "installing ${seeder_apk#"$EMU_ROOT"/}"
eadb install -r -t "$seeder_apk"

h="android.permission.health"
failed=()
if ((grant)); then
  for p in READ_SLEEP READ_HEART_RATE READ_OXYGEN_SATURATION READ_RESPIRATORY_RATE READ_HEART_RATE_VARIABILITY \
    READ_SKIN_TEMPERATURE READ_STEPS READ_HEALTH_DATA_HISTORY READ_HEALTH_DATA_IN_BACKGROUND; do
    eadb shell pm grant "$NB_PKG" "$h.$p" >/dev/null 2>&1 || failed+=("nightbaseline:$p")
  done
  if (($(sdk_int) >= 33)); then
    eadb shell pm grant "$NB_PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || failed+=("nightbaseline:POST_NOTIFICATIONS")
  fi
fi
# The seeder is always granted: it is a bench tool without its own onboarding, and seed.sh fails without them.
for t in SLEEP HEART_RATE OXYGEN_SATURATION RESPIRATORY_RATE HEART_RATE_VARIABILITY SKIN_TEMPERATURE STEPS; do
  for m in WRITE READ; do
    eadb shell pm grant "$SEEDER_PKG" "$h.${m}_$t" >/dev/null 2>&1 || failed+=("seeder:${m}_$t")
  done
done
if ((${#failed[@]})); then
  echo "not granted (feature or Health Connect missing on this image?): ${failed[*]}"
elif ((grant)); then
  echo "all health permissions granted"
else
  echo "seeder permissions granted; NightBaseline granted nothing (--no-grant)"
fi
if ((visibility_fix)) && (($(sdk_int) >= 30)); then
  eadb shell am compat disable FILTER_APPLICATION_QUERY "$NB_PKG" >/dev/null 2>&1 ||
    echo "could not disable package-visibility filtering (release build on a user image?)"
fi
echo "installed on $SERIAL ($(avd_name), API $(sdk_int))"
