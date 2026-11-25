# ADR 0002: English-only repository

- Status: accepted
- Date: 2026-11-25

## Decision

Everything in the repository and its history is English: code, comments, docs, ADRs, commit messages, scripts. The
only Russian text allowed is the app's `ru` localization (`res/values-ru*`) and `README.ru.md`, both product
requirements. `tools/check-language.sh` enforces this (run before every commit and in CI).

## Context and consequences

Early research documents were produced in Russian. The branch history was squashed so that no commit contains
Russian text, and the documents were translated before being committed. Old commits may survive on GitHub as
unreachable objects; before the repository is made public it may be recreated from the clean history (decided in the
pre-publication check with the owner).
