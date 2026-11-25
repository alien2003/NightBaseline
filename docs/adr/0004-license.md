# ADR 0004: Apache License 2.0

- Status: accepted
- Date: 2026-11-26

## Context

The owner wants a standard licence that keeps the option of publishing the app in app stores, including paid
distribution. NightBaseline is written by its owner; pieces adapted from PulseStages come from the same author, and the
libraries it uses (AndroidX, Jetpack Compose, Hilt, Room, WorkManager, Health Connect client, Haze) are Apache-2.0.

## Options

| Licence | For | Against |
|---|---|---|
| Apache-2.0 | de facto standard of the Android ecosystem; explicit patent grant; compatible with every dependency; the copyright holder can sell builds in stores | anyone may also build and distribute (even sell) copies |
| MIT | short, very permissive | no patent grant |
| GPL-3.0 | others must keep copies open | discourages store distribution by third parties and some contributors; heavier obligations |

## Decision

Apache License 2.0, with a `NOTICE` file. The licence applies to the whole history: every commit carries it (the
repository was recreated with the rewritten history). As the copyright holder the owner can still publish paid store
builds or relicense future versions.

## Consequences

- Contributions are accepted under Apache-2.0 (CONTRIBUTING.md).
- Third-party notices are listed in the app's Licenses screen.
