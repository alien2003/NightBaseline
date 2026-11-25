#!/usr/bin/env bash
# Fails when Cyrillic text appears in tracked (or staged) files outside the allowed places: the project is English;
# Russian is allowed only in Android resources for the ru locale and in README.ru.md.
#   tools/check-language.sh            check the working tree (tracked files)
#   tools/check-language.sh --staged   check the staged content (pre-commit hook)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
allowed='(^|/)res/values-ru(-[^/]*)?/|^README\.ru\.md$'
if [[ "${1:-}" == "--staged" ]]; then
  files=$(git diff --cached --name-only --diff-filter=ACMR)
  show() { git show ":$1"; }
else
  files=$(git ls-files)
  show() { cat "$1"; }
fi
bad=0
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  [[ "$f" =~ $allowed ]] && continue
  # Text files only (binary jars and images may contain arbitrary bytes).
  if show "$f" | grep -qI . 2>/dev/null && show "$f" | grep -qP '[\x{0400}-\x{04FF}]' 2>/dev/null; then
    echo "Cyrillic text in $f"
    bad=1
  fi
done <<<"$files"
exit $bad
