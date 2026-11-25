# NightBaseline user guide

This guide is for people who use NightBaseline on their phone. It covers installation, the first run, every screen,
every message the app can show and what to do about it, the settings, notifications, foldables and tablets, and
common questions. Labels in quotes are the English texts of the app. The app also ships a Russian translation and
follows the phone's language; any other language gets the English texts.

NightBaseline compares your night measurements with your own recent nights. It is not a medical device: it doesn't
diagnose, treat or prevent any condition and can't tell you whether you are ill. The rules behind every result are in
[ALGORITHM.md](../ALGORITHM.md).

## Contents

- [Terms](#terms)
- [Before you start](#before-you-start)
- [Install](#install)
  - [Install and update with Obtainium](#install-and-update-with-obtainium)
  - [Install from the Releases page](#install-from-the-releases-page)
- [First run](#first-run)
- [When results appear](#when-results-appear)
- [Reading the home screen](#reading-the-home-screen)
- [Night statuses](#night-statuses)
- [Measurement statuses](#measurement-statuses)
- [Messages and what to do](#messages-and-what-to-do)
  - [Night messages](#night-messages)
  - [Banners](#banners)
- [Learning period](#learning-period)
- [Metric detail and the usual range](#metric-detail-and-the-usual-range)
- [Night detail](#night-detail)
- [History](#history)
- [Settings](#settings)
  - [Sensitivity](#sensitivity)
- [Notifications](#notifications)
- [Foldables, tablets and small windows](#foldables-tablets-and-small-windows)
- [Accessibility](#accessibility)
- [FAQ](#faq)
- [Troubleshooting](#troubleshooting)

## Terms

| Term | Meaning |
|---|---|
| Source | The app that writes your sleep and measurements to Health Connect, for example OHealth for OPPO watches. NightBaseline reads one source at a time. |
| Night | The main sleep of a date. Pieces of a night interrupted by a wake-up are joined; naps and other shorter sleeps of the same date are ignored. A night belongs to the date you wake up on. |
| Measurement | One of: sleeping heart rate, respiratory rate, blood oxygen (SpO2), sleep duration, and, when the source records them, heart rate variability (HRV) and skin temperature. |
| Usual range | For each measurement, the middle value of your recent valid nights plus or minus how much your nights normally vary. |
| Valid night | A night of 3 hours or more that is not ambiguous. Only valid nights are compared and build the usual range. |
| Learning period | The first 7 valid nights, before a usual range exists. |
| Note | The one notification NightBaseline sends in the morning when two or more measurements moved outside your usual range. |

## Before you start

- **Android 9 or later.**
- **Health Connect.** On Android 14 and later it is part of the system (Settings → Security and privacy → Privacy →
  Health Connect, or a similar place depending on the phone maker). On Android 9–13 it is an app from Google Play;
  NightBaseline offers to install or update it. The Android 9–13 path has not been tested on a device yet; reports
  are welcome.
- **A source app that writes sleep sessions and heart rate to Health Connect.** In Health Connect → "App
  permissions", the source must be allowed to write sleep and heart rate. Respiratory rate, blood oxygen, HRV and skin
  temperature are used when the source writes them. The tested source is OHealth with an OPPO Watch X3
  ([README](../README.md#tested-sources)).
- **Wear the device at night** and let the source app sync in the morning.

NightBaseline has no `INTERNET` permission. It cannot download anything, check for updates or send data anywhere
([privacy.md](privacy.md)).

## Install

NightBaseline is published on the [Releases page](https://github.com/alien2003/NightBaseline/releases) of its GitHub
repository. Each release has two files: `NightBaseline-<version>.apk` (the signed app) and `SHA256SUMS.txt` (its
checksum).

### Install and update with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) is a free, open-source Android app that installs apps from their
release pages and installs new releases as updates. It is a separate project: NightBaseline itself has no update check
and no internet access.

1. **Install Obtainium** from one of the sources its README lists under
   [Installation](https://github.com/ImranR98/Obtainium#installation).
2. **Add NightBaseline.** Open this page on the phone and tap the button:

   [![Get it on Obtainium](get-it-on-obtainium.svg)][obtainium-import]

   The button opens a redirect page of Obtainium's app directory (apps.obtainium.imranr.dev), which hands the settings
   below to Obtainium; that page loads the directory's analytics and ad scripts. Obtainium asks "Import app" and shows
   the settings under "Raw JSON"; confirm.
3. **Install NightBaseline** from Obtainium. The first time, Android asks you to allow Obtainium to install apps.

The button gives Obtainium these settings:

| Obtainium setting | Value | Why |
|---|---|---|
| "App source URL" | `https://github.com/alien2003/NightBaseline` | The releases of this repository. |
| "Include prereleases" | Off | Final releases only; a tag such as `v0.2.0-beta.1` is a pre-release. |
| "Filter APKs by regular expression" | `^NightBaseline-[0-9.]+\.apk$` | Picks `NightBaseline-<version>.apk` and nothing else. |
| "Expected signing certificate hashes" | `26ec1dec3593adced268bb641ecb747b2e8b1542d5662478202fe0c6b1f4d377` | The release signing certificate: Obtainium refuses an APK signed with another key. Older Obtainium versions ignore this setting. |

**Adding it by hand.** If the button does not work, open Obtainium's "Add app" screen, enter
`https://github.com/alien2003/NightBaseline` as "App source URL", set the filter and, optionally, the certificate hash
from the table, and add it. Labels may differ between Obtainium versions.

### Install from the Releases page

1. Download `NightBaseline-<version>.apk` from the
   [latest release](https://github.com/alien2003/NightBaseline/releases/latest).
2. Open it. Android asks once to allow the browser or file manager to install apps.
3. Optional check: the signing certificate SHA-256 printed by `apksigner verify --print-certs` must be
   `26:EC:1D:EC:35:93:AD:CE:D2:68:BB:64:1E:CB:74:7B:2E:8B:15:42:D5:66:24:78:20:2F:E0:C6:B1:F4:D3:77`
   ([SECURITY.md](../SECURITY.md#verifying-a-download)).

**Updates.** Install a newer `NightBaseline-<version>.apk` over the installed one, or let Obtainium do it. Your nights,
settings and Health Connect permissions are kept. Android installs an update only if it is signed with the same key.

## First run

The first run has six steps; "Step n of 6" is shown at the top. "Back" returns to the previous step.

1. **Welcome.** What NightBaseline does in three points: it reads once a day, after you wake up; it compares each
   night with your own recent nights; everything stays on this phone, with no internet access. The full "not a medical
   device" text is on this step. Tap "Get started".
2. **How it works.** The first 7 nights teach NightBaseline your usual range for each measurement. After that, you get
   a note when two or more move outside it on the same night. Tap "Next".
3. **Where your nights come from.** A list of the apps that wrote sleep data to Health Connect in the last 30 days,
   with the date of their last sleep. OHealth is preselected if present, otherwise the source with the most recent
   sleep. Pick the app your watch or ring uses and tap "Use {source}". If the list is empty, see
   [No sleep data in Health Connect yet](#night-messages).
4. **Allow reading in Health Connect.** The step lists what will be read and why: sleep, heart rate, respiratory rate,
   blood oxygen, HRV and skin temperature (the last two only if your source records them), reading in the background
   (so the morning check runs without opening the app) and past data (optional: up to 60 earlier nights can be used
   right away). NightBaseline only reads; it never writes, changes or shares your data. Tap "Allow in Health Connect"
   and allow the permissions there.
   - Sleep and heart rate are required. Without them the step shows "Sleep and heart rate are needed" and "Try
     again". After a second refusal Health Connect stops showing its dialog; the button then opens the Health Connect
     permission screen of NightBaseline, where you can turn them on.
   - If you leave some optional measurements off, the step says "Some measurements are off. You can turn them on
     later in Settings." and continues.
5. **A calm note in the morning.** Allow notifications (Android 13 and later ask for this permission), or tap "Not
   now". Without notifications, results still appear in the app.
6. **Ready.**
   - If 7 or more valid past nights were found: "Found {n} past nights. Your usual range is ready." Last night is
     already compared.
   - Otherwise: "Learning starts tonight", with how many of the 7 nights are already there.

   Tap "Done" to open Home.

If Health Connect is missing or needs an update, the first run shows that first ([Messages](#night-messages)).

You can run the setup again at any time: Settings → "Data source" → "Set up again".

## When results appear

- **Once a day.** The morning check starts at 08:00 local time. It then waits until the night is complete in your
  source: you are up (a step record after the end of the night; for sources without steps, 3 hours after the end), the
  measurements reach the end of the night, and the source has written nothing new for 15 minutes. While it waits, it
  checks again on its own. At the latest 12 hours after the end of the night, the night is evaluated with what is there.
- **Opening the app** shows the newest stored result. "Check now", shown in some messages, starts one read right away.
- **One re-evaluation.** Some sources rewrite the night the next morning. NightBaseline evaluates such a night again
  once; a second note is sent only if the result changed into one that deserves a note.
- **Before 15:00**, if last night is not in yet, Home keeps showing the previous night with the banner "Last night
  isn't in yet."

## Reading the home screen

Home ("Last night") answers one question: was last night usual for you? From top to bottom:

1. **Title and night label**, for example "Night of Nov 24–25".
2. **At most one banner** for something that does not block the result, such as notifications being off
   ([Banners](#banners)).
3. **The night summary**: a status mark, a headline such as "All within your usual range", and a sentence saying how
   many measurements were compared with how many of your last nights. When there is no result, a message card takes
   its place ([Night messages](#night-messages)).
4. **One tile per measurement**, always in the same order: sleeping heart rate, respiratory rate, blood oxygen, sleep
   duration, then HRV and skin temperature if your source records them. Each tile shows last night's value, a band
   with your usual range and a marker for the value, and a status label. Tiles that moved are not moved to the top;
   the summary names them instead. Tap a tile to open [Metric detail](#metric-detail-and-the-usual-range).
5. **"Not recorded by {source}: …"**: optional measurements your source doesn't send (for example "HRV, Skin
   temperature"). The info icon explains that they appear automatically if the source starts writing them.
6. **"Last 7 nights"**: one mark per night; tap a night to open it, or "See all" to open History.
7. **"Not a medical device."** with a link to About.

Every status has a shape and a text label, not only a colour, so the screen also reads in grayscale. No status uses
red; moved measurements use one calm warm colour whichever direction they moved.

## Night statuses

| Mark | Label | Meaning | Note sent |
|---|---|---|---|
| Circle with a check | "All within your usual range" | Every compared measurement is inside its usual range. | No |
| Rounded square with one triangle | "{metric} moved outside your usual range" or "{k} measurements moved outside your usual range" | At least one measurement moved, fewer than needed for a note. One measurement on its own often moves. | No |
| Rounded square with two triangles | "{k} measurements moved outside your usual range" | Two or more measurements moved outside on the side that counts. This is a change compared with your own recent nights, not a diagnosis. | Yes, once per night |
| Dotted ring | "Learning your usual range" | Fewer than 7 valid nights so far. | No |
| Half-filled circle | "Short night" | Less than 3 hours of sleep: shown, not compared. | No |
| Two overlapping half circles | "Two long sleeps on one day" | Two sleeps of 3 hours or more on the same date (for example after a night shift): not compared. | No |
| Empty circle with a dash | "Too few readings last night" / "No data" | Sleep was recorded but no measurement had enough readings, or there was no sleep session. | No |

Sleep duration counts towards the two needed for a note only together with at least one other measurement: on its own
it changes with everyday life.

## Measurement statuses

| Mark | Label | Meaning |
|---|---|---|
| Filled circle inside the band | "Usual" | Inside your usual range. |
| Upward triangle right of the band | "Higher than usual" | Clearly above your usual range. The detail says by how much. |
| Downward triangle left of the band | "Lower than usual" | Clearly below your usual range. |
| Filled circle, marker outside the band | "Usual" | Outside on a side that doesn't count for this measurement (for example blood oxygen above your range, or a lower heart rate). Shown, never counted. |
| Dotted ring, dotted band | "Learning, n of 7" | This measurement has fewer than 7 values (for example the source started writing it recently). It doesn't count until it has a range. |
| Dash | "No value last night" | No readings of this measurement during your sleep, or fewer than needed (the detail says how many there were and how many are needed). |
| Dash | "Partial night" | Readings covered too little of your sleep to compare. |
| Dash next to the value | "Not compared" | The night was short. |
| Slashed circle | "Not recorded by {source}" | Your source doesn't send this measurement to Health Connect. If it starts, NightBaseline picks it up and learns its range automatically. |
| Key outline | "Access off" | Health Connect access to this measurement is turned off. |

The side that counts: higher for sleeping heart rate, respiratory rate and skin temperature; lower for blood oxygen and
HRV; both for sleep duration (only together with another measurement).

## Messages and what to do

### Night messages

When last night has no normal result, a message card takes the place of the summary. When the problem is access to
data (the first four rows), your newest stored result stays visible below it under "Last result: {night}".

| Message | What it means | What to do |
|---|---|---|
| "Health Connect isn't available here" | This phone or profile doesn't support Health Connect (for example some work profiles). Nothing is read or stored. | Nothing can be done in the app. "About NightBaseline" explains what the app needs. |
| "Health Connect needs an update" | Health Connect is missing or too old (Android 9–13). | Tap "Install or update" (opens Google Play), then come back and tap "Check again". |
| "NightBaseline can't read your sleep data" | Access to sleep or heart rate was turned off in Health Connect. Earlier results stay. | Tap "Turn on access" and allow sleep and heart rate. |
| "{source} is no longer on this phone" | The source app was uninstalled. Earlier results stay. | Tap "Choose source" and pick another app that writes sleep data. |
| "Reading your nights from Health Connect…" | The first read is running. | Wait; it usually takes seconds. With a long history it can take longer. |
| "Learning starts tonight" | No valid night yet. | Sleep with your device as usual ([Learning period](#learning-period)). |
| "Learning your usual range" | 1 to 6 valid nights so far. "Last night was under 3 hours, so it didn't count." is added after a short night. | Nothing; NightBaseline starts comparing after 7 nights. |
| "{source} hasn't sent last night yet" | It is past 18:00, or the day's checks are over, and the source has written nothing since last evening. | Tap "Open {source}" so it can sync with Health Connect, or "Check now" after it synced. NightBaseline also checks again on its own. |
| "No sleep recorded last night" | The source synced but has no sleep session for last night. Nights without the device are simply skipped. | Nothing, if you didn't wear the device. Otherwise check in the source app that it recorded the night and shares sleep with Health Connect; then "Check now". |
| "Short night" | Less than 3 hours of sleep were recorded. The tiles show values with "Not compared". | Nothing. Short nights aren't compared and don't change your usual range. |
| "Two long sleeps on one day" | Two sleeps of 3 hours or more on the same date; it isn't clear which one was the night. | Nothing. The day isn't compared and doesn't change your usual range. |
| "Too few readings last night" | Sleep was recorded, but too few measurements during it. This can happen when the device sits loosely or its battery runs out. | Wear the device snugly and charge it before the night. |
| "No sleep data in Health Connect yet" (first run) | No app wrote sleep data in the last 30 days. | Let your watch or ring app share sleep and heart rate with Health Connect, then tap "Check again" (or "Open Health Connect"). |

### Banners

A banner sits above a result that is still valid. Only one is shown at a time.

| Banner | What it means | What to do |
|---|---|---|
| "The daily check needs background access. Without it, results appear only when you open the app." | Health Connect's "read in the background" permission is off. | Tap "Allow". |
| "Health Connect didn't answer this morning. NightBaseline tries again later." | The read failed for a passing reason (service busy, rate limit, timeout). | Nothing; or "Check now". |
| "No new data from {source} for {n} days." | The source has written nothing for 3 days or more. | Open the source app and let it sync, or "Choose source". |
| "Last night isn't in yet. {source} usually syncs after you wake up." | Before 15:00 and no night yet. The previous night is shown. | Nothing; or open the source app, then "Check now". |
| "Notifications are off. Results show up here, but you won't get a morning note." | The notification permission or the "Night results" channel is off. | Tap "Turn on", or dismiss it (it stays hidden for 30 days). |
| "Your usual range is ready. From now on each night is compared with it." | The learning period just ended. Shown once. | Dismiss. |

History shows "Access is off. New nights won't appear." or "Health Connect needs an update. New nights won't appear."
at the top while the matching problem lasts.

## Learning period

NightBaseline needs **7 valid nights** before it can say what is usual for you. During that time:

- Home shows "Learning your usual range" with a 7-segment progress ("3 of 7 nights") and how many nights are left.
  Short nights are shown as skipped segments and don't count.
- The tiles under "Already visible" show last night's values with a dotted band outline and no status, so you can see
  data is arriving.
- "What to expect": after 7 nights each measurement gets its usual range. The range keeps adjusting over the next
  weeks and uses up to 28 nights. Notes start after the learning period.
- No notes are sent. When the range is ready, Home shows "Your usual range is ready" once and, if notifications are
  on, a silent notification says the same.

**Skipping the wait.** If you allowed "past data" in the first run, NightBaseline reads up to 60 earlier nights of your
source and the learning period may be over right away. Without that permission, Health Connect gives apps only the
last 30 days of other apps' data, so earlier nights cannot be used.

A measurement that appears later (for example your source starts writing HRV) learns on its own: only its tile shows
"Learning, n of 7", the night status ignores it, and the "range ready" message is not repeated.

## Metric detail and the usual range

Tap a tile to see how one measurement behaves over time. From top to bottom:

1. **Header:** the value of the selected night (last night by default), its status, your usual range (for example
   "52–61 bpm") and one sentence, such as "6 bpm above your usual range." or "Inside your usual range."
2. **Period:** 7, 30 or 90 nights (default 30). With less history, the chart shows what exists ("Showing {n} nights.
   More appear as they're recorded.").
3. **Chart:** one point per night and your usual range as a band, as it was on each night. Nights without a value are
   gaps; learning nights are hollow points; points outside the range have the triangle shapes. Drag along the chart to
   move the selection; tap to select the nearest night; double tap to open that night. If the band's middle moved a lot
   within the period (for example after a source change), a note says "Your usual range shifted on {date}."
4. **Summary text** under the chart, for example "Sleeping heart rate, last 30 nights. Your usual range is 52 to 61
   bpm. Last night: 67 bpm, higher than usual. 2 of 30 nights were outside the range…". It is also what TalkBack
   reads for the chart.
5. **Figures:** "Inside your usual range: k of n nights", "Usual range", "Middle value".
6. **"How your usual range is computed"**:
   - For each measurement, NightBaseline takes your recent valid nights (up to 28, at most 60 days back) and finds the
     middle value (the median). Your usual range is that middle value plus or minus how much your nights normally vary
     (a robust spread that single unusual nights barely move).
   - The spread has a floor per measurement close to the normal night-to-night variation, so a very stable history
     doesn't make tiny changes look like a change.
   - A night counts as outside only when it is clearly away from the middle value: both compared with your normal
     variation (2.5 spreads with the default sensitivity) and by a minimum amount (for example 3 bpm for heart rate,
     1 percentage point for blood oxygen, 60 minutes for sleep duration).
   - A line for the measurement says which side counts, for example "Only lower values count for SpO2. Higher values
     are shown but never counted."
7. **"What 'outside your usual range' means"**: a measurement was different from your own recent nights; it isn't a
   diagnosis. One measurement often moves on its own. Late meals, alcohol, travel, hard training, a cold, new
   medication or how the device sits can all play a part. If you feel unwell or are worried, talk to a health
   professional.
8. **"Measured by {source}. Last sync {time}."**

The info button in the top bar ("How the range works") opens the same explanation as a sheet.

How the nightly value of each measurement is computed:

| Measurement | Nightly value | Needs |
|---|---|---|
| Sleeping heart rate | The settled low level: 10th percentile of the samples, first 30 min left out | 30 samples, 60 % of the night covered |
| Respiratory rate | Mean after dropping the lowest and highest 10 %, first 30 min left out | 6 samples, 50 % covered |
| Blood oxygen (SpO2) | Mean | 4 samples, 50 % covered |
| Sleep duration | Minutes asleep (awake stages excluded when the source writes stages) | A sleep session |
| HRV (RMSSD) | Median | 3 samples, 30 % covered |
| Skin temperature | Mean change from the source's baseline | 1 record |

The source's own "resting heart rate" is not used: on OHealth it is a daytime value, unrelated to sleep. Details:
[ALGORITHM.md §4–5](../ALGORITHM.md#4-nightly-values).

## Night detail

Any past night opened from History or the "Last 7 nights" strip shows the same blocks as Home for that night: the
summary or message, the tiles and the "not recorded" row. Use the arrows in the top bar (or swipe the summary card,
or the Left and Right keys) for the previous and next night. Tapping a tile opens Metric detail with that night
selected.

## History

History answers "which nights were unusual?".

- **List** (default): every stored night, newest first, grouped by month. Each row shows the night, its status ("All
  usual", "{k} moved", "Learning", "Short night", "Two long sleeps", "No data", "Too few readings") and a small mark per
  measurement.
- **Calendar**: a month grid with the night status mark in each day; swipe or use the arrows to change month. "What the
  marks mean" explains each shape. Today is outlined.
- **Filters:** "All", "Moved" (both one and several moved) and "Learning and gaps". If "Moved" finds nothing: "No
  nights moved outside your usual range in this period." with "Show all nights".
- Tap a row or a day to open [Night detail](#night-detail).
- Before the first night: "Your nights will appear here".

Re-tapping History in the navigation jumps back to the newest night.

## Settings

| Group | Row | What it does |
|---|---|---|
| Data source | "Source" | The app the nights are read from; opens "Choose a source". See [changing the source](#can-i-change-the-source). |
| | "Health Connect permissions" | Opens NightBaseline's permissions in Health Connect. |
| | "Last read" | When the last daily check finished ("Not read yet" before the first one). |
| | "Set up again" | Runs the first-run steps again from the source step. |
| Notifications | "Night results" | The morning note. Disabled with "Turned off in system settings" when Android blocks it; tapping the row then opens the system notification settings. |
| | "Problems reading data" | Silent notifications when NightBaseline can't read your nights ([Notifications](#notifications)). |
| Sensitivity | "Sensitivity" | See [below](#sensitivity). |
| Appearance | "Theme" | "System", "Light" or "Dark". |
| | "Glass effects" | "Automatic" or "Off". Automatic uses translucent, blurred bars and sheets where the phone handles them well, and plain surfaces on Android 11 and earlier, with battery saver, reduced transparency or removed animations, or on devices where scrolling would stutter; the row says why when they are off. Off always uses plain surfaces. Nothing else changes. |
| | Motion note | Motion follows the system "Remove animations" setting. |
| About | "About NightBaseline" | Version, the full "not a medical device" text, "Your data stays on this phone. NightBaseline has no internet access.", open-source licences and the source code address. |

Debug builds add a "Debug" group with a component catalog and synthetic data; release builds don't have it.

### Sensitivity

Three presets decide how far from your usual range a value must be before it counts:

| Preset | In the app | What changes |
|---|---|---|
| "Fewer notes" | "Only larger changes count." | A value must be 3 spreads away from your middle value. |
| "Balanced" (default) | "Recommended. Two measurements must move." | 2.5 spreads. |
| "More notes" | "Smaller changes count too." | 2 spreads. |

The minimum amounts per measurement ([Metric detail](#metric-detail-and-the-usual-range)) apply with every preset.
After the learning period, the screen previews the effect: "With this setting, {k} of your last {n} nights would have
sent a note." (computed on the phone from your stored nights; during learning: "A preview appears after the learning
period.").

"Advanced" holds the range window ("Range window", up to 28 nights) and "Use for comparison" per measurement. "Reset to
default" asks first, then sets Balanced and 28 nights.

## Notifications

NightBaseline uses two notification channels. You can change both in Android's notification settings for the app.

**"Night results"** (default importance: sound or vibration as your phone is set, no pop-up, respects Do Not
Disturb):

- Sent once per night, at the end of the morning check, only when two or more measurements moved outside your usual
  range, the "Night results" switch is on, and the night is from today or yesterday. A late sync of older nights
  updates the app silently.
- Title: "{k} measurements moved outside your usual range". Text: which ones and the night, for example "Heart rate
  and Breathing. Night of Nov 24–25." Expanded, it says which were higher and which lower and ends with "This compares
  the night with your own recent nights and isn't a diagnosis. Not a medical device." It never contains values.
- On the lock screen it shows only "NightBaseline — Last night's result is ready."
- Tap it to open that night. A newer night replaces the older note; opening the night clears it. It has no buttons.
- Never sent for one moved measurement, a usual night, the learning period or a problem.
- After the learning period, one silent "Your usual range is ready" note (if notifications are on).

**"Problems reading data"** (silent, in the notification shade only): once per problem when the morning check finds
that access was turned off, background access is missing, Health Connect needs an update, the source app was removed,
or the source has sent nothing for 3 days. It is removed when the problem is fixed, and tapping it opens Home at the
matching message. Its texts contain no health data.

On Android 13 and later notifications need the system permission; the first run asks for it, and the "Notifications
are off" banner and Settings lead to it later.

## Foldables, tablets and small windows

The layout adapts to the window, not to the device, and keeps your place (screen, selected measurement, night, chart
period, scroll position) when you fold, unfold, rotate, resize or use split screen.

- **Phone or folded outer screen:** one column with a floating bottom bar ("Last night", "History", "Settings").
- **Unfolded inner screen, tablets, landscape:** a navigation rail on the side; tiles in two columns; on wide windows
  list and detail side by side (for example Home with Metric detail, History with Night detail).
- **Tabletop posture** (half folded, hinge horizontal): the upper half shows the result or chart to look at; the lower
  half holds the lists, buttons and navigation. Nothing you can tap sits on the hinge.
- **Book posture** (half folded, hinge vertical): two panes split at the fold.
- **Very small windows** (flip cover screens, tiny freeform windows): a compact text layout, one line per
  measurement, with "More" for History and Settings.
- Keyboard: Ctrl+1/2/3 switch between the three destinations; Esc and Alt+Left go back; Left and Right step through
  nights; `[` and `]` change the chart period.

## Accessibility

- Every status has a shape and a label, never colour alone.
- TalkBack reads each tile as one item, for example "Sleeping heart rate: 58 bpm. Usual. Usual range 52 to 61 bpm.",
  and each chart as its text summary; "Previous night" and "Next night" actions step through the chart.
- Text scales up to 200 % without clipping; touch targets are at least 48 dp.
- With "Remove animations" on, nothing moves.

## FAQ

### Why was a night not compared?

One of these applies: it was shorter than 3 hours ("Short night"); two sleeps of 3 hours or more fell on the same date
("Two long sleeps on one day"); no measurement had enough readings ("Too few readings last night"); or you are still in
the [learning period](#learning-period). Such nights don't change your usual range either. A nap does not count as a
night: only the longest sleep of a date is used.

### Why is a measurement missing?

- "Not recorded by {source}": your source doesn't send it to Health Connect at all (OHealth, for example, sends no HRV
  and no skin temperature). Nothing to fix in NightBaseline.
- "Access off": you didn't allow it in Health Connect. Settings → "Health Connect permissions".
- "No value last night" or "Partial night": too few readings during your sleep, for example because the device sat
  loosely, its battery ran out, or the source measures that value only now and then. Respiratory rate and blood oxygen
  can also arrive up to an hour after the night; NightBaseline waits for them, but at most 12 hours.
- "Learning, n of 7": the measurement is new and needs 7 nights of its own.

### Can I change the source?

Yes: Settings → "Source", or "Set up again". Devices measure a little differently, so NightBaseline asks first ("Switch
to {source}?") and then learns your usual range again from the new source's nights; earlier nights of Health Connect
data from that source (up to 60 with the past-data permission, otherwise 30 days) are used right away. The nights of
the previous source stay in History. NightBaseline reads one source at a time and never mixes sources in one range.

### Why only one note, and only for two measurements?

One measurement on its own moves often, for everyday reasons. Two moving together on the same night is a clearer sign
that the night was different. On simulated people the default settings sent a false note on about 0.05 % of normal
nights ([ALGORITHM.md §7](../ALGORITHM.md#7-validation)). You still see single moved measurements in the app.

### Does it drain the battery?

No measurable amount is expected. NightBaseline has no service running in the background and no real-time
measurement. It reads Health Connect once in the morning (plus a few short re-checks while it waits for the night to be
complete) and then does nothing until the next morning. Battery saver settings that stop background work only delay
the result until you open the app.

### Does it work without a watch from OPPO?

It should, with any app that writes sleep sessions and heart rate to Health Connect, but only OHealth has been tested.
Reports are welcome as [issues](https://github.com/alien2003/NightBaseline/issues).

### Does it change my data?

No. It only reads from Health Connect and declares no write permission ([privacy.md](privacy.md)).

### Is this a diagnosis?

No. "Moved outside your usual range" means a measurement was different from your own recent nights. NightBaseline is
not a medical device. If you feel unwell or are worried, talk to a health professional.

## Troubleshooting

| Problem | Check |
|---|---|
| No result in the morning | Open the source app so it syncs with Health Connect; then "Check now". Some sources upload the night only when you open them. |
| Results appear only when I open the app | Allow background reading ("Allow" on the banner, or Settings → "Health Connect permissions"). Make sure battery optimisation doesn't restrict NightBaseline (Android's "Unrestricted" battery setting; phone makers name and place it differently). |
| No notes at all | Notes are sent only when two or more measurements moved: most mornings have none. Check Settings → "Night results", Android's notification settings for the app, and that the learning period is over. |
| The source list in the first run is empty | The source must have written sleep data to Health Connect in the last 30 days and be allowed to write sleep there. |
| "Health Connect needs an update" keeps coming back | Update Health Connect from Google Play, then "Check again". |
| A measurement always shows "Not recorded by {source}" | The source doesn't send it. Some sources have their own switch for sharing each data type with Health Connect. |
| A night shows wrong times | NightBaseline uses the source's sessions as they are in Health Connect; check the night in the source app. A night rewritten the next morning is evaluated again once. |
| A night was split or a nap was taken as the night | Pieces less than 90 minutes apart are joined; otherwise the longest sleep of a date wins. Two sleeps of 3 hours or more on one date are not compared. |
| I want to delete everything | Uninstall NightBaseline, or clear its storage in Android's app settings ([privacy.md](privacy.md#8-deleting-your-data)). Your Health Connect data is not affected. |

Something else? Open an [issue](https://github.com/alien2003/NightBaseline/issues) with your Android version, phone,
source app and the exact text the app shows. Issues are public: never attach health data or screenshots with your
values.

<!-- Same link as README.md; docs/development.md, "Obtainium link", rebuilds it. -->
[obtainium-import]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.alien2003.nightbaseline%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Falien2003%2FNightBaseline%22%2C%22author%22%3A%22alien2003%22%2C%22name%22%3A%22NightBaseline%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ENightBaseline-%5B0-9.%5D%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%2226ec1dec3593adced268bb641ecb747b2e8b1542d5662478202fe0c6b1f4d377%5C%22%7D%22%7D
