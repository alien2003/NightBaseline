# Privacy policy

NightBaseline processes health data: sleep sessions, heart rate, and, if your source records them and you allow them,
respiratory rate, blood oxygen, heart rate variability and skin temperature. This policy describes what the app
reads, what it stores, where the data stays and how to delete it. In short:

- **Everything happens on the phone.** The app has **no `INTERNET` permission** (and no `ACCESS_NETWORK_STATE`). It
  cannot send anything anywhere, and it cannot check for updates.
- **Read-only.** It reads from Health Connect and declares no write permission: it never writes, changes or deletes
  health data, its own or another app's.
- **Only summaries are kept.** It stores one summary per night (the nightly values it computed and their evaluation)
  in its private storage. Raw samples are not stored; they are read again from Health Connect when needed.
- **No analytics, no crash reporting, no accounts, no ads, no third-party services.**
- **Deleting is simple:** uninstall the app, or clear its storage.

Contents:

- [1. Who is responsible](#1-who-is-responsible)
- [2. What is read and why](#2-what-is-read-and-why)
- [3. What is written](#3-what-is-written)
- [4. Everything stays on the device](#4-everything-stays-on-the-device)
- [5. What is stored on the device](#5-what-is-stored-on-the-device)
- [6. Notifications](#6-notifications)
- [7. Backups and device transfer](#7-backups-and-device-transfer)
- [8. Deleting your data](#8-deleting-your-data)
- [9. Children](#9-children)
- [10. Changes to this policy](#10-changes-to-this-policy)
- [11. Contact](#11-contact)

## 1. Who is responsible

NightBaseline is an independent open-source project, published at
[github.com/alien2003/NightBaseline](https://github.com/alien2003/NightBaseline). No company or server is involved:
the developer never receives any of your data, because the app has no way to send it.

## 2. What is read and why

NightBaseline reads only the data of the **one source app** you chose (OHealth by default), through Health Connect.
Health Connect asks you for each permission; you can change them at any time in Health Connect or in NightBaseline's
Settings → "Health Connect permissions".

| Health Connect permission | Data | Why | Required |
|---|---|---|---|
| `READ_SLEEP` | Sleep sessions (start, end, stages if the source writes them) | Finding the main night of each date and its sleep duration | Yes |
| `READ_HEART_RATE` | Heart rate samples | The sleeping heart rate, computed from the samples inside the night | Yes |
| `READ_RESPIRATORY_RATE` | Respiratory rate | Nightly respiratory rate | No |
| `READ_OXYGEN_SATURATION` | Blood oxygen (SpO2) | Nightly blood oxygen | No |
| `READ_HEART_RATE_VARIABILITY` | HRV (RMSSD) | Nightly HRV, if the source writes it | No |
| `READ_SKIN_TEMPERATURE` | Skin temperature | Nightly skin temperature change, if the source writes it | No |
| `READ_STEPS` | Step records | Only to tell when you are up, so a night is evaluated once it is complete; no step count is stored or shown | No |
| `READ_HEALTH_DATA_IN_BACKGROUND` | (access mode) | Lets the morning check read while the app is closed | No; without it results appear only when you open the app |
| `READ_HEALTH_DATA_HISTORY` | (access mode) | Lets the app use nights older than 30 days, so the learning period can finish right away | No |

Measurements are read only for the time inside your sleep sessions (and the step records just after them). The
sessions of the PulseStages app are always ignored. To list the possible sources in the first run and in Settings,
the app also asks Health Connect which apps wrote sleep data in the last 30 days, and Android for those apps' names and
icons; nothing of that is stored except the package name of the source you choose.

Health Connect itself shows which apps read your data and when (Health Connect → "App permissions" → NightBaseline).

## 3. What is written

Nothing. The app declares no Health Connect write permission, so it cannot write, change or delete any health data. It
writes no files outside its private storage and exports nothing; the only other output is a few value-free
diagnostic lines in Android's system log ([section 5](#5-what-is-stored-on-the-device)).

## 4. Everything stays on the device

- The app manifest removes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions, even if a library asks for them.
  A test of the merged manifests and the release workflow both fail if either permission appears; you can check the
  installed app's permissions in Android's app info screen.
- Without network access, the app cannot upload data, load ads, report crashes or fetch anything. There is no account
  and no sign-in.
- The app does not share data with other apps. It opens other apps only when you tap a button (for example "Open
  {source}", Health Connect's permission screen, Google Play's Health Connect page, or Android's notification
  settings); no health data is passed.

## 5. What is stored on the device

In the app's private storage, which other apps cannot read:

| What | Contents | Kept |
|---|---|---|
| Nights | Per source and date: the night's start, end and sleep minutes, the nightly value of each measurement (for example the sleeping heart rate of that night), the evaluation (status of each measurement and of the night, the usual range used) and when a note was shown | Until you delete the app's data |
| Run log | What each daily check did: dates, statuses, counts, error kinds. It never contains measurement values | 30 days |
| Settings | Source package, notification switches, sensitivity, theme, glass effects, first run done, dismissed banners | Until you delete the app's data |

The nightly values are needed to build your usual range from your recent nights and to draw the charts and History.
No raw heart rate, blood oxygen, respiratory rate, step or other samples are stored.

**Diagnostic lines in the system log.** Every night check (the morning check and "Check now") writes a few lines to
Android's system log (logcat) under the tag `NightBaseline`, in every build including the release. They contain only
dates and local clock times of its decisions and the reasons: when the check ran, which night it looked at and that
night's start and end as the source recorded them, when the newest data of the night arrived, when the next check
runs, and why it waited or what it did ("evaluated and stored", "waits"). They never contain a measured value, a
duration or a count. Android keeps the system log in a small buffer that is overwritten within hours to days and
cleared on restart; other apps cannot read it. It can be read only with a computer over USB debugging (`adb`) or in a
bug report that you create yourself, so if you share a bug report, it includes your recent sleep start and end times.

## 6. Notifications

The "Night results" note names the measurements that moved and the night, never values. Its lock-screen version only
says "Last night's result is ready." The "Problems reading data" notifications contain no health data. Notifications
are shown by Android on your phone; NightBaseline sends them nowhere.

## 7. Backups and device transfer

The app's private data is excluded from Android backups and device-to-device transfer (`allowBackup="false"`). On a
new phone NightBaseline starts fresh; with the past-data permission it can rebuild your nights from Health Connect.

## 8. Deleting your data

- **Everything:** uninstall NightBaseline, or Android Settings → Apps → NightBaseline → Storage → "Clear storage"
  (labels differ between phone makers). This deletes all stored nights, the run log and the settings.
- **Access only:** revoke NightBaseline's permissions in Health Connect. Uninstalling also revokes them.
- Your data in Health Connect and in your source app is not affected by either; NightBaseline never stored it outside
  its own summaries.

## 9. Children

NightBaseline is not directed at children and collects nothing from anyone.

## 10. Changes to this policy

Changes are published in this file in the repository and noted in [CHANGELOG.md](../CHANGELOG.md). A change that
would make the app send data anywhere would require adding a network permission, which the app does not have.

## 11. Contact

Questions about privacy: open an issue in the repository,
[github.com/alien2003/NightBaseline/issues](https://github.com/alien2003/NightBaseline/issues). Issues are public: do
not include health data. To report a security problem privately, see [SECURITY.md](../SECURITY.md).
