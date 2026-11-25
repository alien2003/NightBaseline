#!/usr/bin/env bash
# Fails when tracked (or staged) files contain things that must never be published: absolute home paths, private
# keys, signing passwords, tokens or personal email addresses. Health data is kept out by .gitignore (.data/).
#   tools/check-leaks.sh            check the working tree (tracked files)
#   tools/check-leaks.sh --staged   check the staged content
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
PATTERN='/home/[a-z_][a-z0-9_-]*/|/Users/[A-Za-z][^/<]*/|C:\\\\Users\\\\[A-Za-z]|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|(store|key)Password=[^$<]|gh[pousr]_[A-Za-z0-9]{20,}|[A-Za-z0-9._%+-]+@(gmail|protonmail|proton|yandex|mail|outlook|hotmail|icloud)\.[a-z]{2,}'
# The guard itself names the patterns.
EXCLUDE=(':!tools/check-leaks.sh' ':!*.jar' ':!*.png' ':!*.jpg' ':!*.webp')
if [[ "${1:-}" == "--staged" ]]; then
  out=$(git grep --cached -nIE "$PATTERN" -- . "${EXCLUDE[@]}" || true)
else
  out=$(git grep -nIE "$PATTERN" -- . "${EXCLUDE[@]}" || true)
fi
if [[ -n "$out" ]]; then
  echo "Possible leak (absolute home path, key, password, token or personal email):"
  echo "$out"
  exit 1
fi
