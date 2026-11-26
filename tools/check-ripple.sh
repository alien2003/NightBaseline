#!/usr/bin/env bash
# Fails when Kotlin sources use Material's ripple: ColorOS has no ripple (docs/design/tokens.md 2.8). The press
# feedback is the app-wide NbPressIndication / nbClickable (highlight under the content plus press shrink).
#   tools/check-ripple.sh   check the tracked app sources (main and debug; tests may name the pattern)
# The same rule runs in ./gradlew test (RippleBanTest).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
pattern='(\bripple\(|rememberRipple\(|RippleConfiguration\(|LocalRippleTheme)'
hits=$(git ls-files 'app/src/main/*.kt' 'app/src/debug/*.kt' | xargs -r grep -nE "$pattern" || true)
if [[ -n "$hits" ]]; then
  echo "Material ripple is not used in NightBaseline (tokens.md 2.8):"
  echo "$hits"
  exit 1
fi
