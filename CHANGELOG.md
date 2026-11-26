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
- **"Set up again" asks first.** Settings > "Set up again" now asks for confirmation. Back on the source step returns
  to Settings, and the app stays set up until the new setup is finished, so History and Settings stay reachable even
  without Health Connect.
- **Test bench: emulator console ports are limited to 5554..5584**, the range adb scans. `seed.sh late_upload` warns
  when the 12-hour cap will skip the "waits: channel incomplete" state, and the bench README explains it.

### Fixed

- **Sources other than OHealth.** On Android 11 and later the app could not see any other source app, so right after
  setup it said the source was no longer on the phone and showed package names instead of app names. The app can now
  see every app that works with Health Connect (and only those).
- **No more rows or tabs stuck pressed.** A quick tap in a scrolling screen (for example "Health Connect permissions"
  in Settings, or the "All / Moved / Learning and gaps" filters in History) could leave the grey pressed highlight on
  after the tap: the release arrived before the highlight had drawn its first frame and was skipped. Every press now
  ends on its own release or cancel, and a press also clears when the window loses focus or the app is paused or
  stopped.
- **Back gesture after a cancelled one.** A second swipe right after cancelling one on a page opened from History or
  Settings now goes back one level, and the page continues from where it was instead of jumping back.
- **Settings pages move as a whole.** The title and back button of a Settings page now move with the page during the
  back gesture and page transitions.
- **No double pages.** A fast double tap on a card or row no longer opens the same page twice.
- **Background access.** "Allow" on the background-access banner now opens Health Connect when Health Connect no
  longer shows its permission dialog.
- **Keyboard shortcuts right after a touch.** Ctrl+1/2/3, Ctrl+R, Esc and Alt+Left now work right after touching the
  screen, without first pressing Tab.
- **Source names.** Settings shows the source app's name instead of a package id broken mid-word when the app is not
  installed.
- **Learning progress** now counts last night too and is the same on every screen; after 7 nights it says the range
  is ready.
- **Access that was off.** Nights read while a Health Connect permission was off now show "No access that night"
  instead of saying the source doesn't send the measurement, and are read again once access is back.
- **One message at a time.** Home no longer shows "Last night isn't in yet" or other read banners on top of an access
  or Health Connect problem.
- **"Check now"** shows "Checking…" while it runs, then "Checked at {time}. Nothing new from {source} yet."
- **Time zones.** "Last read" and dates follow a time zone change while the app is open.
- **Notification text** keeps the spaces between its sentences.
- **Names and wording.** One name per measurement in the summary, the notification, the cards and History; Russian
  sentences no longer capitalise names in the middle, month headings start with a capital letter, and the middle
  value is called "median" there instead of "mean". A low value on a side that doesn't count now says "Lower values … don't count". Skin
  temperature is shown as a signed change ("−0.1 °C vs usual"). Values never wrap between the number and the unit,
  and night titles never wrap inside the date. The chart summary counts the same nights as "This period".
- **Health Connect links.** "Health Connect permissions" opens NightBaseline's own Health Connect permission page;
  "Install or update" falls back to the Play web page and shows a message if nothing can open.
- **First run.** Step 3 asks for access instead of saying there is no sleep data and preselects the source once
  access is granted; step 4 shows which optional permissions are off; step 6 no longer claims last night was already
  compared.
- **Settings.** An empty source list shows a message; the Glass "Automatic" row says whether glass is available
  instead of always "On now"; in very narrow windows rows no longer stack the title one letter per line.
- **System bars.** Status and navigation bar icons stay readable after rotating when the app theme differs from the
  system theme. With 3-button navigation, content no longer runs under the system buttons. Without glass (older
  Android, glass off), the collapsed page title sits on a solid background instead of overlapping the content.
- **Layout.** In landscape with a side cutout, the navigation rail no longer overlaps the cards; at large font sizes
  the bottom bar icons stay level and the bar keeps its side margins; the History calendar scrolls under the
  collapsing title without an empty band; on tablets the onboarding "Next" button stays inside the card; Home no
  longer jumps down on a cold start.
- **Accessibility.** The keyboard focus ring is fully visible on list rows, the recent-nights strip and onboarding
  buttons, and the History calendar no longer reads "No data" for days before your first night.
- **Test bench.** The "deviations" scenario now reliably moves 3 measurements (heart rate +14 bpm, breathing +2.5,
  SpO2 -4), whatever the night count. `emu.sh clock` retries `adb root`/`unroot`, checks that it took effect and waits
  for adbd to come back, so scripted runs right after boot no longer break. `seed.sh` ties the result line to its own
  run, so it no longer reports an older result after the emulator clock was set back. `install.sh --no-grant` still
  grants the seeder, so seeding works on a fresh emulator. `multiwindow.sh reset` restores the freeform settings that
  `freeform` turned on. `rotate.sh` works on API 28, checks the lock and fails loudly if it did not apply. `diag.sh`
  lists only NightBaseline's scheduled jobs on API 28 too.

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
