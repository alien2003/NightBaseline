# ADR 0010: One name per measurement, and "no access" is not "not recorded"

- Status: accepted
- Date: 2026-11-26

## Context

The emulator walkthrough found two wording rules that made the app contradict itself.

1. Names. The summary and the notification used the short names ("Breathing and SpO2") right above cards and History
   rows with the full names ("Respiratory rate", "Blood oxygen (SpO2)"). The Russian lists also kept the capital
   letter of every standalone label inside a sentence. The owner asked for one consistent name per measurement
   across the summary, the notification, the cards and History.
2. Missing values. The stored night summary recorded "the permission was off" as `MissingReason.NOT_PROVIDED` ("the
   source doesn't write this"). Once access was granted again, those nights kept saying "{source} doesn't send this
   measurement to Health Connect", while the Source picker listed the same measurement for the same source.

## Decision

- Every sentence, card, History row and the notification uses the full metric name. The short names stay for the
  compact surfaces only (Glance windows, the widget, the compact split-window lines). The full names move from `:app`
  to `:work` (`work/src/main/res`), so the notification and the app read the same resource. The app no longer copies
  the notification strings of `:work`: the copies won the resource merge and had lost their trailing spaces
  ("Breathing.Lower than usual").
- Lists of names go through `InlineNames` (`:work`). In Russian every item after the first is lowercased, and the
  first one too when the list follows other words (after a colon). Only a word that is a capital letter followed by
  lowercase letters changes, so SpO2 and HRV stay as they are. English keeps its title-case names.
- `MissingReason.NO_ACCESS` (core-algo) marks a channel whose permission was not granted when the night was read
  (`HealthDataReader.noAccessChannels()`). It is never evaluated (like `NOT_PROVIDED`), the UI shows it as
  `M-PERM-THEN` ("No access that night"), never as "Not recorded by {source}". When the permission is granted again,
  the daily check reads such nights again once (final or not) so they get their values.
- Stored evaluations do not change: existing summaries contain no `NO_ACCESS`, so `AlgoFingerprint.ALGO_VERSION`
  stays at 2. Nights stored before this change with a permission-caused `NOT_PROVIDED` keep it (the reason cannot be
  told apart afterwards); they age out of the 90-night views.

## Consequences

- The notification body is longer ("Sleeping heart rate and Respiratory rate. Night of Nov 24–25."); the collapsed
  line may ellipsize, the expanded text shows all of it.
- A new metric needs its full and short names in `:work` (en and ru).
