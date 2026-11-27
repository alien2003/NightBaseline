#!/usr/bin/env bash
# The interaction boundary of ADR 0011: every animation, navigation, back, gesture, press, key, scroll, fling,
# overscroll and indication API is used inside the framework package ui/interaction only, so every change of the app
# moves on one registry, one clock and one release rule. Also: the framework never imports a screen package, and no
# spring or tween literal exists outside the registry (Physics.kt).
#   tools/check-interaction.sh   check the tracked app sources (main and debug)
# The same rules run in ./gradlew test (InteractionBoundaryTest) and in CI.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
framework='app/src/main/kotlin/io/github/alien2003/nightbaseline/ui/interaction/'
fail=0
mapfile -t files < <(git ls-files 'app/src/main/*.kt' 'app/src/debug/*.kt')
while IFS= read -r pattern; do
  [[ -z "$pattern" || "$pattern" == \#* ]] && continue
  for f in "${files[@]}"; do
    [[ "$f" == "$framework"* ]] && continue
    [[ -f "$f" ]] || continue
    # Code only: KDoc / comment lines and trailing comments do not count.
    hits=$(sed -E 's#//.*$##' "$f" | grep -nE "$pattern" | grep -vE '^[0-9]+:\s*\*' || true)
    [[ -z "$hits" ]] && continue
    if grep -qF "$f|$pattern|" tools/interaction-allowlist.txt; then continue; fi
    echo "$f uses '$pattern' outside ui/interaction (ADR 0011):"
    echo "$hits"
    fail=1
  done
done < tools/interaction-forbidden.txt
# The framework does not depend on screens.
rev=$(git ls-files "$framework*.kt" | xargs -r grep -nE 'import io\.github\.alien2003\.nightbaseline\.(nav|ui\.(home|history|night|metric|settings|onboarding|learning))\.' || true)
if [[ -n "$rev" ]]; then
  echo "ui/interaction imports a screen package:"
  echo "$rev"
  fail=1
fi
# The registry is the only place with spring / tween literals.
lit=$(git ls-files "$framework*.kt" | grep -v '/Physics.kt$' | xargs -r grep -nE '\b(spring|tween)\([^)]*[0-9]|\bNbSpring\(' || true)
if [[ -n "$lit" ]]; then
  echo "spring or tween literal outside the registry (ui/interaction/Physics.kt):"
  echo "$lit"
  fail=1
fi
exit $fail
