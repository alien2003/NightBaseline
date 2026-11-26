# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Release tags are `vMAJOR.MINOR.PATCH`, and
`vMAJOR.MINOR.PATCH-alpha.N`, `-beta.N` or `-rc.N` for pre-releases. The release workflow derives versionName and
versionCode from the tag and publishes the tag's section of this file as the release notes, so links inside a version
section are absolute ([docs/development.md](docs/development.md#releases)).

## [Unreleased]

### Added

- **The back gesture previews where it goes.** When you swipe back from a night, a measurement, a Settings page, an
  onboarding step or the History and Settings tabs, the page now shrinks slightly, rounds its corners and moves with
  your finger over the page you are going back to; let go to go back, or swipe back the other way to stay. On the
  first screen nothing changes, so the system's own back-to-home animation plays. With animations off there is no
  preview ([ADR 0009](https://github.com/alien2003/NightBaseline/blob/main/docs/adr/0009-predictive-back.md)).

### Changed

- **Smoother on high-refresh screens.** While you use the app it now asks for the screen's highest refresh rate at
  the current resolution (for example 120 Hz instead of 60 Hz), and gives it back 5 seconds after your last touch and
  when you leave the app; battery saver and the system's refresh-rate setting still take precedence
  ([ADR 0008](https://github.com/alien2003/NightBaseline/blob/main/docs/adr/0008-high-refresh-rate.md)).
- **Less work per frame and at start-up.** The calendar no longer redraws every day cell when one changes, the
  start-up work runs off the main thread, and the start-up profile now covers the whole blur library.

### Fixed

- **Sources other than OHealth.** On Android 11 and later the app could not see any other source app, so right after
  setup it said the source was no longer on the phone and showed package names instead of app names. The app can now
  see every app that works with Health Connect (and only those).
- **No more rows or tabs stuck pressed.** A quick tap in a scrolling screen (for example "Health Connect permissions"
  in Settings, or the "All / Moved / Learning and gaps" filters in History) could leave the grey pressed highlight on
  after the tap: the release arrived before the highlight had drawn its first frame and was skipped. Every press now
  ends on its own release or cancel, and a press also clears when the window loses focus or the app is paused or
  stopped.

## [0.1.0] - 2026-11-26

First release. NightBaseline reads last night's measurements from Health Connect once a day, compares each one with
your own recent nights and tells you, calmly, when two or more measurements moved outside your usual range. It is not
a medical device.

### Added

- **Nightly comparison with your own baseline.** Sleeping heart rate (computed from the raw heart-rate samples inside
  the night), respiratory rate, blood oxygen (SpO2) and sleep duration; HRV and skin temperature are picked up
  automatically when the source writes them. Each measurement gets a personal usual range from your last 28 valid
  nights (median and robust spread, at most 60 days back) after a learning period of 7 nights
  ([ALGORITHM.md](https://github.com/alien2003/NightBaseline/blob/main/ALGORITHM.md)).
- **Main-night selection.** Sessions less than 90 minutes apart are joined, naps are ignored, nights under 3 hours are
  shown but not compared, and a date with two sleeps of 3 hours or more is not compared.
- **Night-complete rule.** The night is evaluated once you are up and the source has finished uploading that night
  (late respiratory rate and blood oxygen included), or at the latest 12 hours after it ended; one re-evaluation after
  the source's next-morning rewrite. Only the night's own records count as "still uploading": daytime syncs (steps,
  all-day heart rate) no longer keep a complete night waiting, a problem found in the development builds.
- **One calm note.** A notification in the morning only when two or more measurements moved outside your usual range on
  the side that counts; sleep duration counts only together with another measurement. No values in the notification
  and a lock-screen version without details. A separate silent channel for problems reading data.
- **Screens.** Home ("Last night") with the night summary, a tile per measurement with its range band, and the last 7
  nights; Night detail; Metric detail with a 7 / 30 / 90-night chart of the range as it was each night, a text summary
  and plain explanations; History as a list or calendar with filters; Settings; a six-step first run.
- **Every state explained.** Learning progress, short and ambiguous nights, too few readings, a source that has not
  synced, missing or outdated Health Connect, revoked access, a removed source, measurements the source does not
  record; each with a plain message and an action.
- **History on first run.** With the past-data permission, up to 60 earlier nights are evaluated right away, so the
  learning period can be over on day one.
- **Sensitivity.** "Fewer notes", "Balanced" (default) and "More notes", with a preview over your own stored nights.
  A change applies to the stored nights too: they are evaluated again from their stored summaries, without notes.
- **Stored nights follow the algorithm.** After an update that changes how nights are evaluated, stored nights are
  evaluated again from their stored summaries (no new Health Connect read, no notifications).
- **Any source.** Choose any app that writes sleep sessions and heart rate to Health Connect; OHealth is the default
  and the first tested source. PulseStages sessions are always ignored.
- **Adaptive layouts** for phones, foldables (outer and inner screen, tabletop and book postures), tablets, split
  screen and very small windows, keeping your place across fold, unfold and resize; keyboard shortcuts.
- **ColorOS 17 look.** The whole interface follows the ColorOS 17 style of OPPO's system apps (own implementation
  and art, no vendor resources): frosted-glass navigation with an automatic plain fallback, reworked bottom sheets,
  light and dark themes, status shapes that never rely on colour alone, TalkBack descriptions and text summaries for
  every chart, support for 200 % text.
- **Aurora launcher icon**, an own design with an adaptive and a monochrome (themed) layer.
- **Smooth on the phone.** Scrolling and press feedback do no per-frame work, and switching between Home, History and
  Settings keeps each section composed instead of rebuilding it.
- **English and Russian.**
- **On-device end-to-end suite** for developers: runs inside the installed release app, compares other apps'
  Health Connect data before and after, and makes one tagged test write that it deletes right away
  ([tools/device-test/README.md](https://github.com/alien2003/NightBaseline/blob/main/tools/device-test/README.md)).

### Privacy and security

- No `INTERNET` or `ACCESS_NETWORK_STATE` permission; checked by a test of the merged manifests, in CI and on the
  signed release APK.
- Health Connect READ permissions only; nothing is written.
- Only nightly summaries are stored, in private storage excluded from backups.
- Each night check writes a few diagnostic lines to Android's system log (tag `NightBaseline`): dates, local clock
  times of its decisions and reasons, never a measured value; readable only over USB debugging or in a bug report
  ([privacy policy](https://github.com/alien2003/NightBaseline/blob/main/docs/privacy.md#5-what-is-stored-on-the-device)).
- Release APKs are signed with one release key; certificate SHA-256
  `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`.

[Unreleased]: https://github.com/alien2003/NightBaseline/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/alien2003/NightBaseline/releases/tag/v0.1.0
