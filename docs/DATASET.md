# Health Connect dataset (developer tooling)

How the developer's own Health Connect history is exported from the phone and kept for research and for offline runs
of the algorithm. **Raw health data never leaves the git-ignored `.data/` folder**: it is not committed, not uploaded to
CI and not quoted in documents. Documents only contain aggregate summaries without raw values; tests and screenshots use
synthetic data.

## Pipeline

```
phone: tools/hc-exporter (debug app, READ permissions only, no INTERNET)
   └─ files/export/<runId>/{manifest.json, <type>.jsonl}       private app storage
          │  adb exec-out run-as … tar                           tools/hc-export.sh
          ▼
.data/hc-dumps/<runId>/…                                       one folder per run, never modified
          │  tools/hc-dataset.py merge
          ▼
.data/dataset/<type>.jsonl, observations.jsonl, runs.json        merged dataset
          │  algo-cli nights (stage 4)
          ▼
.data/nights/nights.jsonl                                      night files (PulseStages interchange, extended)
```

Run: `mise exec -- tools/hc-export.sh` (phone connected, USB debugging allowed, screen unlocked). A repeated run
exports the whole readable history again and the merge adds what is new: records are keyed by Health Connect record
id, the version with the newest `lastModifiedMs` wins. Every version of every record is also listed in
`observations.jsonl` with the run in which it was first seen, which gives an upper bound on when a record appeared in
Health Connect (used to measure the publication delay of a night).

## Dump format (`formatVersion` 1)

`manifest.json`: `format` = `nightbaseline-hc-dump`, `formatVersion`, `runId` (UTC `yyyyMMdd-HHmmss`), `startedAtMs`,
`finishedAtMs`, `zoneId`, `device` (manufacturer, model, Android release, SDK, build display id; no serial numbers or
other identifiers), `healthConnectApkVersionCode`, `historyFeatureAvailable`, `skinTemperatureFeatureAvailable`,
`grantedPermissions`, and `types.<type>` = `{status: OK|NOT_GRANTED|FEATURE_UNAVAILABLE|ERROR, count, firstMs, lastMs,
origins: {package: count}, error?}`.

`<type>.jsonl`: one record per line, common fields:

| Field | Meaning |
|---|---|
| `id` | Health Connect record id |
| `origin` | package that wrote the record (`metadata.dataOrigin`) |
| `lastModifiedMs` | `metadata.lastModifiedTime` |
| `recordingMethod` | `metadata.recordingMethod` (0 unknown, 1 active, 2 automatic, 3 manual) |
| `device` | `{type, manufacturer, model}` of the recording device when the source sets it |
| `time`, `zoneOffsetS` | instantaneous records |
| `start`, `end`, `startZoneOffsetS`, `endZoneOffsetS` | interval records |

Type-specific fields:

| Type file | Health Connect record | Payload |
|---|---|---|
| `sleep` | `SleepSessionRecord` | `stages: [[startMs, endMs, stage], …]` (HC stage codes); titles and notes are never exported |
| `heart_rate` | `HeartRateRecord` | `samples: [[timeMs, bpm], …]` |
| `spo2` | `OxygenSaturationRecord` | `value` (%) |
| `respiratory_rate` | `RespiratoryRateRecord` | `value` (breaths/min) |
| `resting_heart_rate` | `RestingHeartRateRecord` | `value` (bpm) |
| `hrv_rmssd` | `HeartRateVariabilityRmssdRecord` | `value` (ms) |
| `skin_temperature` | `SkinTemperatureRecord` | `baselineC`, `location`, `deltas: [[timeMs, °C], …]` |
| `body_temperature` | `BodyTemperatureRecord` | `value` (°C), `location` |
| `steps` | `StepsRecord` | `value` (count) |
| `exercise` | `ExerciseSessionRecord` | `exerciseType`, `segments` (count) |

A new type is added in one place (`RECORD_TYPES` in `tools/hc-exporter`), plus its READ permission in the exporter's
manifest; the merge and the summary pick it up automatically.

## Night files

`algo-cli run` reads the merged dataset directly (sessions and samples of one origin) and runs the same pipeline as
the app; see [ALGORITHM.md](../ALGORITHM.md#8-reproducing). No separate night-file format is needed.
