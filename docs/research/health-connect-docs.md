# Health Connect: what matters for NightBaseline

Research date: 2026-11-25. Tags:
- `[from official documentation <url>]` — developer.android.com;
- `[verified in code/docs]` — verified in the PulseStages code (read-only);
- `[hypothesis]` — our inference, to be checked on device.

Documentation root: https://developer.android.com/health-and-fitness/guides/health-connect

## 1. Library version

- `androidx.health.connect:connect-client`: latest stable is **1.1.0** (October 8, 2025), latest alpha is
  **1.2.0-alpha06** (August 26, 2026). There is no RC or beta.
  [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]
- What is new in 1.2.0-alpha:
  - alpha01 — backward compatibility for SkinTemperature and Mindfulness;
  - alpha03 — `getChanges(changeLogsToken, pageSize)` and record validation moved into the platform on Android U+;
  - alpha04 — Matchmaking API;
  - alpha05 — `minSdk 24` and protected intents before Android 14;
  - alpha06 — updated HC signing certificates.
  [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]
- Starting with 1.1.0, `compileSdk = 36` and AGP >= 8.9.1 are required. [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started]
- PulseStages uses `healthConnect = "1.1.0"`. [verified in code/docs: ../PulseStages/gradle/libs.versions.toml, read-only]
- Recommendation: stable 1.1.0, as in PulseStages. Everything we need — background reads, history, SkinTemperature,
  changes, feature availability — is already in 1.1.0. Take the alpha only for `getChanges(pageSize)`.
  [hypothesis]
- Test library `androidx.health.connect:connect-testing:1.0.0-alpha04` (August 12, 2026):
  `FakeHealthConnectClient`, `FakePermissionControllerOverrides`. alpha04 fixed a bug with an open-ended
  `TimeRangeFilter` in a non-UTC time zone. [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]

## 2. Platform

- On Android 14+ (API 34) Health Connect is part of the framework as a module; nothing needs to be installed. On Android 13
  and below the APK `com.google.android.apps.healthdata` is required. The HC app works on Android 9+.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started]
- Before use, check `HealthConnectClient.getSdkStatus(context)`; the manifest needs
  `<queries><package android:name="com.google.android.apps.healthdata"/></queries>`.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started]
- Check new capabilities via `healthConnectClient.features.getFeatureStatus(...)`. Flags we
  need: `FEATURE_READ_HEALTH_DATA_IN_BACKGROUND`, `FEATURE_READ_HEALTH_DATA_HISTORY`, `FEATURE_SKIN_TEMPERATURE`.
  System-module features are unavailable on Android 13 and below, even with the APK.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/feature-availability]
- Android 16 (target API 36): APIs that previously required `BODY_SENSORS` / `BODY_SENSORS_BACKGROUND` now
  require `android.permission.health.*`, for example `READ_HEART_RATE` and `READ_HEALTH_DATA_IN_BACKGROUND`. This
  concerns direct sensor access. Nothing changes for reading from HC — the permissions are the same.
  [from official documentation https://developer.android.com/about/versions/16/behavior-changes-16]
  [from official documentation https://developer.android.com/health-and-fitness/health-services/permissions]
- Manifest requirements:
  - an activity with `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (up to Android 13);
  - an `activity-alias` with `android.intent.action.VIEW_PERMISSION_USAGE` + category
    `android.intent.category.HEALTH_PERMISSIONS` + `android:permission="android.permission.START_VIEW_PERMISSION_USAGE"`
    (Android 14+).
  The activity must show the same privacy policy as in Play Console.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started]
- Permissions are requested via `PermissionController.createRequestPermissionResultContract()`. The user can
  revoke them at any time, so check `getGrantedPermissions()` before every use.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started]

## 3. Record types and permissions

All permissions are read-only; we need no WRITE_* permissions, except for one temporary test record (see rule 6).
[from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types]

| Class | Kind | Fields | Permission | Flag |
|---|---|---|---|---|
| `HeartRateRecord` | Series | `samples[time, beatsPerMinute]`, `startTime`, `endTime`; aggregates BPM_AVG/MIN/MAX, MEASUREMENTS_COUNT | `READ_HEART_RATE` | — |
| `RestingHeartRateRecord` | Instantaneous | `beatsPerMinute`, `time` | `READ_RESTING_HEART_RATE` | — |
| `HeartRateVariabilityRmssdRecord` | Instantaneous | `heartRateVariabilityMillis`, `time` | `READ_HEART_RATE_VARIABILITY` | — |
| `OxygenSaturationRecord` | Instantaneous | `percentage`, `time` | `READ_OXYGEN_SATURATION` | — |
| `RespiratoryRateRecord` | Instantaneous | `rate`, `time` | `READ_RESPIRATORY_RATE` | — |
| `SkinTemperatureRecord` | Series | `deltas[time, delta]`, `baseline: Temperature?` (0–100 °C), `measurementLocation` (0 unknown, 1 finger, 2 toe, 3 wrist); aggregates TEMPERATURE_DELTA_AVG/MIN/MAX | `READ_SKIN_TEMPERATURE` | `FEATURE_SKIN_TEMPERATURE` |
| `SleepSessionRecord` | Interval | `startTime`, `endTime`, `stages[]`, `title`, `notes`; aggregate SLEEP_DURATION_TOTAL | `READ_SLEEP` | — |

All permissions are prefixed with `android.permission.health.`.

Additional read permissions [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types]:
- `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` — read while the app is not in the foreground;
- `android.permission.health.READ_HEALTH_DATA_HISTORY` — read data older than 30 days before the first permission grant.

Notes:
- `SkinTemperatureRecord` stores **deltas**. If `baseline` is set, deltas are relative to it; if not, to
  an unknown base. So only deltas are comparable across sources; absolute temperature is
  not always available. [from official documentation https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/SkinTemperatureRecord]
- Sleep and the data within it are linked **by time only**; there is no session ID: heart rate, SpO2 and respiration
  are read with separate requests using `TimeRangeFilter.between(session.startTime, session.endTime)`. The source
  writes the session only after waking up.
  [from official documentation https://developer.android.com/health-and-fitness/health-connect/experiences/sleep]
- The source computes `RestingHeartRateRecord` with its own algorithm. Our own "sleeping heart rate" from raw
  `HeartRateRecord` within the session is vendor-independent. [hypothesis, consistent with scope]
- What OHealth writes (whether HRV, temperature, respiration are present) must be checked on device. [hypothesis]

## 4. Read limits

- Without `READ_HEALTH_DATA_HISTORY`, other apps' data can be read **for 30 days before the moment any
  permission was first granted**. Own data on Android 14+ — no limit. Reading before this boundary returns an error. After
  reinstall the boundary shifts: 30 days from the new grant.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]
- 1.1.0-alpha11 clarified: without this permission, the error occurs only when reading an individual point beyond the boundary.
  [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]
- What this means for us: the 14–28-night baseline window fits within 30 days. The history permission is needed only for
  historical backfill older than 30 days on first launch. Without it, a 28-night baseline can be ready almost
  immediately if the source has data for the past month. [hypothesis]
- `ReadRecordsRequest`: default `pageSize = 1000`. Keep reading while there is a `pageToken`. End condition:
  `pageToken.isNullOrEmpty()`: on Android 12–13, `""` may arrive instead of `null`. Exceeding the quota throws
  `IllegalStateException` — backoff is needed.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]
- Aggregation (`aggregate`) removes duplicates itself and hits limits less often, so it is preferable for averages and sums.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]
  Sleeping heart rate needs raw samples (median, percentiles), hence `readRecords` within the session window.
  [hypothesis]
- Exceptions to handle: `IllegalStateException` (service unavailable or invalid request),
  `IOException`, `RemoteException`, `SecurityException` (missing permission).
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]

## 5. Background reads

- Background reads: declare `READ_HEALTH_DATA_IN_BACKGROUND`, check
  `FEATURE_READ_HEALTH_DATA_IN_BACKGROUND == FEATURE_STATUS_AVAILABLE`, work via `WorkManager`
  (`CoroutineWorker`). The documentation example uses an hourly periodic worker. If the user did not grant
  the background permission, the app keeps working with what is allowed.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]
- On Android 13 and below, the background and history permissions are supported starting with 1.1.0-alpha11.
  [from official documentation https://developer.android.com/jetpack/androidx/releases/health-connect]
- Without the background permission, reads are possible only in the foreground; sync may be interrupted at any time.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data]
- PulseStages already uses `READ_HEALTH_DATA_HISTORY` / `READ_HEALTH_DATA_IN_BACKGROUND` (manifest and
  `work/...`). [verified in code/docs: ../PulseStages/app/src/main/AndroidManifest.xml, read-only]
- For us: a periodic worker (e.g., every 1–3 h in the morning) with a battery constraint. A night is considered complete if
  `SleepSessionRecord.endTime` is already in the past and there are no new changes after it (N minutes of quiet).
  Computation runs once per night. [hypothesis]

## 6. Request limits

- Quotas are fixed, separately for foreground and background. Reads and changelog have a periodic and a
  daily limit. Writes additionally have memory limits. **Background limits are stricter.** Specific numbers are
  not published in the documentation.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/plan/rate-limiting]
- To avoid hitting limits, sync via the changelog rather than repeated raw reads.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/plan/rate-limiting]
- Our budget: 1 sleep request + one per metric type within the session window (plus pages), once a day.
  This is orders of magnitude below any reasonable quota. [hypothesis]

## 7. Changes API

- `getChangesToken(ChangesTokenRequest(recordTypes = setOf(...)))` → `getChanges(token)`. The response has `changes`
  (`UpsertionChange` with the full record or `DeletionChange` with only the `id`), `nextChangesToken`, `hasMore`;
  repeat while `hasMore`. Since 1.2.0-alpha03 there is `getChanges(token, pageSize)`.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data]
- An unused token **expires after 30 days**. After expiry: re-read data from the last known
  timestamp (or for 30 days) and deduplicate by `id`.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data]
- It is recommended to keep a separate token per type: if one permission is revoked, a shared token fails with an error.
  `DeletionChange` does not contain the record type. [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data]
- There are no notifications about new data; we must check ourselves: on returning to the foreground or periodically.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/sync-data]
- For us, changes are needed to notice that the source retroactively corrected or appended past nights
  (watch catch-up sync, deletion) and to recompute them. The main path is reading the last night's window by
  time. [hypothesis]

## 8. Record metadata

[from official documentation https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata]
- `id` — assigned by HC on insert.
- `dataOrigin.packageName` — who wrote the record, filled in by HC; for OHealth it is `com.heytap.health.international`.
- `lastModifiedTime` — time of the record's last modification or creation, filled in by HC. Useful to tell
  whether a night has settled (nothing changed for N minutes), and for deduplication on upsert from changes.
  [hypothesis for the application]
- `clientRecordId` / `clientRecordVersion` — source key and version for upsert; the record with the higher version
  wins.
- `device` (`type`, `manufacturer`, `model`) — optional.
- `recordingMethod`: 0 UNKNOWN, 1 ACTIVELY_RECORDED, 2 AUTOMATICALLY_RECORDED, 3 MANUAL_ENTRY.
- Since 1.1.0-alpha12 the `Metadata` constructor is internal: use the factories `autoRecorded(device)`, `manualEntry()`,
  `activelyRecorded(device)`, `unknownRecordingMethod()`; for auto and active, `Device.type` is mandatory.
  This matters for the test record and for synthetic data in tests (`populatedWithTestValues`).
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/metadata]
- Device types: WATCH, PHONE, RING, FITNESS_BAND, CHEST_STRAP, SCALE, etc. Extended types are behind
  `FEATURE_EXTENDED_DEVICE_TYPES`; otherwise `TYPE_UNKNOWN` is returned.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/metadata]
- For us:
  - discard `RECORDING_METHOD_MANUAL_ENTRY` (manual measurements are out of scope);
  - filter by the selected `dataOrigin` (OHealth by default): if several sources are read for one night,
    the median gets mixed;
  - whether OHealth fills in `recordingMethod` and `device` must be checked on the phone.
  [hypothesis]

## 9. Miscellaneous

- Since June 2026, steps computed by HC itself are attributed not to the `android` package but to a
  synthetic device package name. This does not affect sleep metrics, but shows that `dataOrigin` may
  look different than expected. `dataOrigin` values must not be hardcoded, except for the source package the user selected.
  [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data]
- Play Console requires a Health Connect data types declaration (Health apps declaration), otherwise permissions are not
  granted. This matters for publication at stage 5. [from official documentation https://developer.android.com/health-and-fitness/guides/health-connect/plan/data-types]
- For manual checks and debugging there is Health Connect Toolbox; for unit tests, `FakeHealthConnectClient`.
  [from official documentation https://developer.android.com/health-and-fitness/health-connect/experiences/vitals]
