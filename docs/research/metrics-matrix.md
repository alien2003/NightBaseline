# Metrics matrix: what the device data supports

Research date: 2026-11-25. Basis: the developer's own Health Connect export (`.data/`, never committed), about 58 nights
from the default source and the imported history of a previous wearable. Only aggregates appear here: counts,
coverage, sampling intervals, delays, and night-to-night **spreads and differences**. There are no per-night values,
no dates paired with values and no absolute personal levels (CLAUDE.md, rule 5).

Tags:
- `[verified on device data]`: measured on the export by a script in `tools/research/`;
- `[from documentation]`: from [health-connect-docs.md](health-connect-docs.md) or [analogs.md](analogs.md), which
  cite the original sources;
- `[hypothesis]`: our inference, to be re-checked as nights accumulate.

Sources: **OHealth** = `com.heytap.health.international` (default, current watch); **importer** =
`org.healthmerge.hcimporter` (history of the previous wearable, ends the day before OHealth starts); **SaA** =
`com.urbandroid.sleep` (Sleep as Android, history only); **PulseStages** = `io.github.alien2003.pulsestages`
(estimated stages derived from OHealth heart rate).

Scripts (aggregates only, no data embedded; run as
`mise exec -- python -I -W ignore tools/research/<script> .data`):

| Topic | Scripts |
|---|---|
| Heart rate, resting HR | `hr_structure.py`, `hr_night.py`, `hr_extra.py` |
| Sleep sessions, main night | `sleep_sessions.py`, `sleep_mainnight.py`, `sleep_importer.py` |
| SpO2, RR, temperature, HRV | `spo2-rr.py`, `spo2-rr-detail.py` |
| Publication delay | `latency.py`, `latency_shape.py`, `latency_batches.py` (terminal output only) |
| Spreads, baseline simulation | `matrix_spreads.py` |

Important limit: the export has only 3 runs, all within a few minutes of each other, and only the last run exported
anything other than sleep. So `observations.jsonl` carries no timing signal yet. All delay figures come from
`lastModifiedMs`, which OHealth overwrites on rewrites (section 3). [verified on device data]

## 1. Matrix

"Robust SD" means MAD × 1.4826 of the nightly aggregate across main nights. Spreads are in metric units and describe
variability, not level.

| Metric | OHealth | importer | SaA | Sampling | Night coverage | Gaps | Short nights / naps | Nightly aggregate (recommended) | Quality gate (recommended) | Typical night-to-night spread | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Sleeping HR** | yes: 1-sample point records, plus 1 Hz multi-sample records during workouts only | yes | yes, but in the OHealth era it duplicates the same watch's HR (difference to OHealth median 0, IQR −2..+1 bpm) | 2 min (median 120 s; 29.5–30.1 samples/h on every night); 20 s bursts while awake | 10-min bins with ≥1 sample: p0 97%, p5–p100 100% | largest in-night gap median 4 min, max 6.3 min; none > 10 min | same 2-min cadence in naps and fragments; fragment median HR within −1 bpm (IQR −4..+0.5) of its night | **p10** of background samples in the merged main night, **excluding the first 30 min**; fallback: lower-half mean (continuous); median if < 20 samples | ≥ 80% of 10-min bins, no gap > 30 min, ≥ 20 samples; drop multi-sample (1 Hz) records or deduplicate by timestamp | robust SD ≈ 3 bpm across all nights; ≈ 2–2.5 bpm inside a 28-night window; lag-1 autocorr ≈ 0.46 | verified; aggregate choice is a hypothesis |
| **Respiratory rate** | yes, only inside sleep (100% in sessions, 97.8% in main night) | yes (last ~111 days of the importer, previous watch) | no | median 10 min, bulk 8–13 min, p95 23 min; about 5.4/h | 30-min bins: median 100%, p5 88%; all nights ≥ 70% | first sample 18–19 min after session start almost every night (OHealth only); last sample median 5 min before end | 8 of 10 nap/other blocks have a few samples (median 2.5) | **mean or 10% trimmed mean**, excluding the first 30 min; never the median | ≥ 15 samples and ≥ 70% of 30-min bins, first 30 min excluded from both | mean: robust SD ≈ 0.3 breaths/min; median: robust SD 0 (0.5 quantum, 5 distinct nightly values) | verified |
| **SpO2** | yes; 75% of samples in sleep, plus ~2 daytime spot readings on most days | yes, three different regimes (dense, sparse, 30-min) | no | 30 min (p5–p95 in sleep 29.7–31.2 min); 30-min slots sometimes skipped; about 1.9/h | 1-h bins: median 100%, p5 83%; all nights ≥ 70% | first sample median 16 min after start (30-min grid not aligned to onset); last median 19 min before end | 7 of 10 nap/other blocks have 1–5 samples | **mean or 10% trimmed mean** of in-night samples; never the median; optional "≥ 2 samples ≥ 3 points below personal median" indicator | ≥ 6 samples and ≥ 60% of 1-h bins; daytime samples excluded by the night window | mean: robust SD ≈ 0.3 points; median and p10: robust SD 0 (integer, 74% of nights share one value) | verified |
| **Sleep duration** | yes, sessions without stages; nights often split into 2–4 sessions | yes, very fragmented, 86% staged | yes (history only), 98% staged | n/a | 58 of 58 wake dates have a main night | splits: gaps of ~20–60 min; none of 60–120 min | 27 non-main sessions: 16 night fragments (21–45 min from the main night) and 11 separate naps; 26% of main nights consist of > 1 session | sum of session durations of the **merged cluster** (G = 90 min); gaps between sessions count as awake | merged sleep ≥ 3 h; runner-up ≥ 3 h on the same key = ambiguous, skip | robust SD ≈ 1.4 h; lag-1 autocorr ≈ 0 | verified |
| **Resting HR (source record)** | 1 record per local day at 00:00, written ~32 h after the stamp | 1 per day, stamped mostly 17:00/18:00 local | ~48k records: a per-sample copy of SaA's own HR, not a daily value | daily | every day in range | none | n/a | **not a v1 metric**; at most an informational value for the previous calendar day | only after it has been rewritten (lastModified after D+1 00:00); ignore sources with > 2 records/day | robust SD ≈ 4.5 bpm, lag-1 autocorr 0.23 (noisier than sleeping HR) | verified; meaning is a hypothesis (section 4) |
| **HRV (RMSSD)** | permission granted, **0 records** | 0 | 0 | — | — | — | — | pluggable; enable on first data | N to be calibrated once a source writes it | unknown | verified absent |
| **Skin temperature** | **no records** | yes, history only: one interval record per night with exactly one delta; `baselineC` constant across all records | no | 1 value per night | 108 of ~110 nights in that era | — | at most one record per wake date | the single nightly delta (+ baseline if present); own personal baseline from deltas | record ≥ 4 h and ≥ 60% overlap with main night | delta robust SD ≈ 0.28 °C (importer) | verified (absent in OHealth); dormant |
| **Body temperature** | 0 records | 0 | 0 | — | — | — | — | pluggable | — | unknown | verified absent |
| **Steps (context only)** | yes; 35% of records rewritten on a later date | daily aggregates only | no | fine-grained for OHealth | — | — | — | not a metric; used as "user is up" signal for night completion | a record with ≥ 5 steps/min starting after the visible session end | — | verified as a signal; its use is a hypothesis |

Notes per metric:

- **Sleeping HR**:
  - Two record kinds: 99.9% of OHealth HR records are single points (start = end). The other 48 records are 1 Hz
    multi-sample records (about 210–10,800 samples each, median ≈ 1,300) that overlap exercise sessions and never overlap sleep. They
    duplicate 4,866 background sample times. [verified on device data]
  - `recordingMethod` = 0 and `device` = null on all OHealth records, so neither can identify the watch.
    [verified on device data] HC documents both fields as optional. [from documentation]
  - Night shape: the first 30 min run a median of +5.5 bpm (IQR +2..+9) above the rest of the night; the lowest
    30-min window sits at a median of 63% of the night. Within-night robust SD of samples is 3.0–7.4 bpm.
    [verified on device data]
  - Candidates compared (median, mean, p5, p10, lowest rolling 30/60-min, lower-half mean, IQM) all correlate strongly
    (Spearman 0.86–0.98), so the choice is low-stakes. p10 has the highest within-night reliability (0.91,
    block-bootstrap) and is insensitive to session-boundary shifts, 30% sample dropout, 5-min thinning and fragment
    merging. The lowest rolling 30-min mean has the lowest night-to-night autocorrelation and is undefined for sparse
    sources. [verified on device data] Choosing p10 is a [hypothesis].
  - OHealth HR is integer: percentiles are quantised to 1 bpm against a spread of about 3 bpm (p10 takes 16 distinct
    values over 58 nights). [verified on device data]
  - All candidates correlate weakly and negatively with session duration (Spearman −0.26..−0.34).
    [verified on device data]
  - Sleep and the vitals inside it are linked by time only, so HR is read with a time-range filter on the session.
    [from documentation]
- **RR**: the step is 0.5 breaths/min and there are 17 distinct values. With ≥ 10 samples the median is stable to one
  step (90th-percentile subsampling error 0.5). [verified on device data] The 18-min onset delay may be a warm-up or
  an "after confirmed sleep onset" rule. [hypothesis]
- **SpO2**: integer with 8 distinct values. Cutting a night to 3–5 samples gives a median error of 1 point at the
  90th percentile, which is one quantum. Daytime samples sit about 1 point below the in-sleep median, so "all SpO2 of
  the day" must not be used. [verified on device data]
- **Importer SpO2 regimes**:
  - A1: about 12 months, near-continuous sampling, up to hundreds of samples per night.
  - A2: about 12 months, sparse, about 5 per day; 12% of nights have none.
  - B: the last ~111 days, 30-min grid like OHealth, phone metadata.
  - Era A has many readings at the scale ceiling and a long low tail, unlike OHealth. Several era-A zone offsets
    differ from the current device zone.

  So baselines must be kept **per source** and per-record offsets must be respected. [verified on device data]
- **Skin temperature**: HC stores deltas; when `baseline` is null the base is unknown, so only deltas are comparable.
  [from documentation] The importer behaves as one pre-aggregated nightly deviation. [verified on device data]
  Building our own baseline from it is a [hypothesis].
- **Not a v1 input**:
  - PulseStages sessions copy OHealth timing exactly (start and end differences 0 min at every percentile on 64/64)
    and merge OHealth split nights. They are derived, not independent evidence. [verified on device data]
    ADR 0001 already excludes them from sleep duration.
  - SaA has no sessions in the OHealth era. [verified on device data]

## 2. Main-night rule

Rule (proposed for `core-algo`):
1. Take sessions of the configured source only (exclude PulseStages and other derived writers).
2. Merge consecutive sessions while the gap is ≤ **G = 90 min**.
3. Key each cluster by the local date of (cluster midpoint + 6 h), i.e. a midpoint in [D−1 18:00, D 18:00).
4. Main night = the cluster with the largest summed sleep (sum of session durations, not span).
5. Accept only if summed sleep ≥ 3 h; otherwise "no main night / short night".
6. If a runner-up on the same key has ≥ 3 h, mark the date ambiguous and skip the evaluation.
7. Vital-sign windows use the sessions of the cluster (gaps between sessions treated as awake).

Evidence:
- OHealth gaps between consecutive sessions:

  | Gap | Count |
  |---|---|
  | 0–30 min | 10 |
  | 30–60 min | 7 |
  | 60–120 min | 0 |
  | 2–4 h | 4 |
  | 4–8 h | 5 |
  | > 8 h | 58 |

  So G anywhere in 60–120 min gives identical main nights (0 differences on 58 dates). G = 30 changes 7 of 58 dates
  by leaving split nights unmerged. G = 180 loses one date. [verified on device data]
- With G ≥ 60, 15 of 58 main nights (26%) consist of more than one session. Awake time inside a merged night is
  p95 ≈ 40 min. [verified on device data]
- Duration thresholds must be applied **after** merging. Before merging, 3 of 58 main nights look shorter than 4 h;
  after merging, 1 of 58 does. [verified on device data]
- Window-free keying, step 3:
  - On OHealth the midpoint key agrees with the "end in [03:00, 14:00)" rule on 58/58 dates for G = 30..120.
  - Yet 14% of OHealth main nights end in [12:00, 14:00), close to that hard edge.
  - On the importer, the end window drops about 30% of nights of ≥ 3 h (295 of 930 end at or after 14:00 local).
    The midpoint rule keeps 945 keyed dates versus 668. Where both pick a night they agree on 654 of 668.

  So the midpoint key is the source-neutral choice. [verified on device data]
- Ambiguity at G = 90:
  - On OHealth, 0 dates are ambiguous under the end-window rule. Under the midpoint key, 2 are, both long daytime
    naps competing on the same key.
  - On the importer, 6 dates are ambiguous under the end-window rule and 23 under the midpoint key (both counted
    with the "≥ 50% of the winner or ≥ 3 h" test).
  - The "≥ 50% of the winner" test flags real naps, so step 6 keeps only the absolute 3 h test. [verified on device
    data / hypothesis for the threshold]
- Naps (start 09:00–19:00, < 4 h; 13 of 85 OHealth sessions by this definition, which overlaps some fragments;
  11 sessions are neither the main night nor adjacent to it) never win the main night and are excluded from all
  nightly metrics. Night fragments adjacent to the night (16 sessions, 21–45 min away) are merged by G. Merging
  changes sleeping-HR aggregates by a median of ≤ 0.5 bpm (p10: 0). [verified on device data]
- Analogs use similar minimum-sleep filters: Samsung ≥ 4 h, Fitbit ≥ 3 h. [from documentation]

## 3. Publication delay, multi-upload behaviour, "night complete" rule

What the data shows:
- **OHealth rewrites in place**, and the dataset keeps only the newest version. [verified on device data]

  | Records | Share last written on a later local date |
  |---|---|
  | HR | 99% |
  | Sleep sessions | 86% |
  | Resting HR | 98% |
  | Steps | 35% |
  | SpO2 | 10% |
  | RR | 5% |

  For HR and sessions, the next morning's pull re-saves the whole previous calendar day. Main-night session
  lastModified minus end is about 1220/1410/1440 min (p25/p50/p75). So **lastModified is not a first-publication time
  for HR and sessions**. For SpO2 and RR it is a usable upper bound.
- **Pulls** (clusters of OHealth writes across all types): about 5.3 per day.
  - Atomic (p95 duration ≤ 1 min).
  - None between 03:00 and 06:59.
  - One morning pull happens every day at a nearly fixed clock time (IQR 0.32 h), not at wake time. Session end
    relative to it is −30..+257 min (p5..p95).
  - The other pulls look user-driven, with an evening peak.

  This is consistent with the PulseStages finding that OHealth's periodic background pull never runs. [verified on
  device data] The fixed morning pull may be a watch reconnection schedule. [hypothesis]
- **Several uploads per night**:
  - 93% of nights (SpO2) and 98% (RR) have in-night samples in ≥ 2 write clusters.
  - 71% of nights have a pull while still asleep.
  - Share of in-night samples by the pull that last wrote them:

    | Metric | During sleep | First pull after end | Later, same date | Later date |
    |---|---|---|---|---|
    | SpO2 | 60% | 35% | 4% | 1% |
    | RR | 56% | 25% | 13% | 5% |

  [verified on device data]
- **Lag of vitals inside a pull** (write time minus newest sample): SpO2 p50 61 min, RR p50 26 min, HR ≈ 0.
  [verified on device data] The watch computes RR and SpO2 late, so the last 20–60 min of a night are missing from
  the wake-up pull. [hypothesis]
- **Session end to the first pull after it**:
  - p25 1, p50 1, p75 71, p95 ≈ 610 min.
  - 60% of nights within 15 min, 73% within 60 min.
  - 58% of nights end 0–3 min before a pull, so the data cannot tell a real end from a provisional cut at a pull.

  [verified on device data]
- A night's HR carries 1–3 distinct lastModified values (median 2), which fall into a single write cluster. The newest HR lastModified of a night is a median of 23.8 h after the end,
  which nearly matches the session's own lastModified (difference median ≈ 0 h). [verified on device data]
- HC has no notifications about new data; the app must poll. Changes tokens expire after 30 days.
  `lastModifiedTime` is the time of the last modification. [from documentation] The earlier draft rule "session
  ended and N minutes of quiet" from health-connect-docs.md §5 is **too weak for OHealth** (see the simulation).

Simulated completion rules (45 nights, 15-min checks). The model is a [hypothesis] built from the PulseStages log
of 3 nights: each in-sleep pull publishes a provisional session cut at the pull. The numbers are
[verified on device data] under that model.

| Rule | Premature (session still provisional) | Delay after real end, p50 |
|---|---|---|
| A: ended ≥ 60 min, quiet ≥ 30 min | 38% | 61 min |
| B: ended ≥ 120 min, quiet ≥ 60 min | 29% | 122 min |
| G: A + RR reaches end − 30 min + SpO2 reaches end − 60 min | 11–13% | 155 min |
| C: a ≥ 5 steps/min record after the visible end is written, quiet ≥ 15 min | 0% (12 h cap) / 2% (6 h cap) | 418 / 361 min |
| F: C + RR/SpO2 coverage | 0% (12 h cap) / 2% (6 h cap) | 496 / 361 min (p25 184) |

Missing samples at firing: under rule A, SpO2 is incomplete on 80% of nights (mean 17% missing). Under F, SpO2 is
incomplete on 36% (mean 3%) and RR on 93% (mean 7%, dominated by next-date writes, which may be rewrites).

**Proposed "night complete" rule (all metrics)** [hypothesis]. The night is complete when all of these hold:
1. A main night (section 2) from the configured source is visible.
2. "User is up": a step record from the same source with ≥ 5 steps/min, starting ≥ 2 min after the visible end, has
   been written. Sources without fine-grained steps fall back to "ended ≥ 3 h ago and no write from the source for
   60 min", which simulated at 13% premature on OHealth.
3. HR covers the night up to ≥ end − 5 min.
4. Per metric, the newest written sample reaches end − lag: RR end − 30 min, SpO2 end − 60 min. Lags are per source,
   taken from the p75–p95 of its write lag.
5. No new record from the source inside the night window for 15 min, over two consecutive checks.
6. Hard cap: 12 h after the visible end. Then compute with what is there and mark missing metrics. Allow one
   re-evaluation after the next morning's rewrite, and notify again only if the verdict changes.

Expected OHealth behaviour: ~0–2% premature [hypothesis, model-based]; delay p25 ≈ 3 h, p50 ≈ 6–8 h, driven by when
the user opens OHealth. Aggregates must tolerate ~10% missing RR/SpO2 samples. The resting HR record cannot be part
of night completeness (written only the next morning). [verified on device data]

## 4. What the OHealth resting HR record means

Facts [verified on device data]:
- One record per local calendar day D, stamped D 00:00:00.000, no missing days. Integer; recordingMethod 0;
  device null.
- Written a median of 32 h after the stamp, about 8 h after D ends (IQR 7.8–8.0 h), at the next morning's pull.
  The current day's record also exists earlier, written mid-day.
- Relative to that night's sleeping HR, the record sits higher by a median of +12..+13 bpm vs the median and about
  +17 bpm vs p10; robust SD of the difference 4.5–5.2 bpm.
- Correlation with sleeping HR is weak at every lag. Spearman 0.45–0.51 with the night ending on the morning of D,
  0.25–0.36 with the night before, 0.40–0.54 with the next nights.
- Against awake statistics of the same day D, the closest match is the awake 5th percentile: median difference 0
  (robust SD 5.9), Spearman 0.48, best at lag 0. The awake 30-min rolling minimum is +3.4 bpm. The all-day minimum and
  the day median do not match. Its rank among awake samples varies (IQR 3.8–8.5%).
- Own noise: day-to-day robust SD 4.45 bpm, lag-1 autocorrelation 0.23. Sleeping-HR candidates are ≈ 3 bpm and ≈ 0.47.

Interpretation [hypothesis]: a **daytime, awake, low-activity resting HR for calendar day D**. It is a vendor
algorithm near the low tail of awake HR, computed after D ends; the early record for the current day is provisional
and rewritten. It is not derived from sleep and is not a substitute for sleeping HR. HC documents resting HR as
computed by each source with its own algorithm. [from documentation]

Other sources [verified on device data]:
- SaA writes every HR reading of its tracking as a resting HR record: all ~48k match its own HR samples exactly.
  Only 17% of them fall inside an SaA sleep session.
- The importer stamps its daily record at 17:00/18:00 (some 00:00/00:01).

So code must not assume midnight stamps or one record per day. Ignore sources with > 2 resting HR records per day.
[hypothesis for the filter]

## 5. Open questions to re-check on new nights

1. **Real publication delay.** Run `tools/hc-export.sh` every 15–30 min on a few mornings, from before the fixed
   morning pull until early afternoon, plus once in the evening and the next morning. Do not open OHealth during the
   test, because it may trigger a pull. Measure first-seen time minus session end, versions per id, and whether
   rewrites change values. Suggested exporter change: store each version's session end and sample range in
   `observations.jsonl`. (Exporting needs the phone, so ask the owner first, rule 10.)
2. Whether the next-morning rewrite of HR and sessions changes values or sample counts, or only re-saves them.
3. Whether RR "later date" writes are late arrivals or rewrites. This decides the RR lag in completion rule step 4.
4. Whether the fixed morning pull is a watch schedule that the owner can change, and how a sleep-in past it behaves
   (provisional session, then extended).
5. Stability of G = 90: whether gaps of 60–120 min appear, for example a long awakening at night.
6. Whether the 18-min RR onset delay holds on every night, and whether the first SpO2 sample stays aligned to a
   global 30-min grid.
7. Whether the 1 Hz workout HR records ever overlap a sleep session, for example a workout ending in a nap.
8. Whether HRV, skin or body temperature start appearing after an OHealth or firmware update (pluggable metrics).
9. Resting HR: whether the provisional same-day value differs from the rewritten one; whether lag 0 stays the best
   match.
10. Baseline behaviour across real deviations (illness, alcohol, travel). There are no labelled events yet, so only
    the false-alarm side is measured (section 6).
11. Importer late wake times (+2/+3 h offset eras): a later schedule or wrong stored offsets. This matters only if
    importer history is used to seed baselines.

## 6. Implications for algorithm defaults

All spreads below are typical **variability in metric units**, not personal levels.

| Metric | Nightly aggregate | Typical robust SD (all nights) | Typical robust SD inside a 28-night window | Proposed scale floor | Concern direction |
|---|---|---|---|---|---|
| Sleeping HR | p10 excl. first 30 min | ≈ 3 bpm | mostly < 2.5 bpm (floor active on 95–96% of nights) | **2.5 bpm** (≥ 1 bpm just for quantisation) | up |
| RR | mean / trimmed mean excl. first 30 min | ≈ 0.3 breaths/min | ≈ 0.3 | **0.3 breaths/min** (one 0.5 step is too coarse for a mean) | up |
| SpO2 | mean / trimmed mean | ≈ 0.3 points | ≈ 0.3 | **0.3 points** | down |
| Sleep duration | merged summed sleep | ≈ 1.4 h | above 0.75 h on 92–100% of nights | **0.75 h** | down (informational) |
| Skin temperature (if a source appears) | nightly delta | ≈ 0.28 °C (importer) | n/a | 0.2 °C [hypothesis] | up |

[verified on device data: spreads, `matrix_spreads.py`, `hr_night.py`, `spo2-rr.py`] Floors are a [hypothesis].

Baseline simulation on OHealth main nights (rolling median, robust-SD scale with floor, no labelled events) [verified
on device data under these settings]:
- Floors set to the typical spreads above (HR 2.5, RR 0.3, SpO2 0.3, sleep 0.75 h), window 28, min nights 7:

  | Metric | Share of nights over concern-direction z > 2.5 |
  |---|---|
  | HR | 2% |
  | SpO2 | 0% |
  | RR | 0% |
  | Sleep duration | 6% |

  Nights with ≥ 2 metrics over z > 2.5: 0 of 51. Over z > 2: 1 of 51.
- Floors at one quantum (HR 1.5, RR 0.5, SpO2 0.5):
  - SpO2 and RR never cross z > 2; their floor is active on almost every night, which effectively disables them.
  - HR crosses z > 2.5 on 16–18% of nights, which is too noisy.

  So the floor must be near the typical spread, not the quantum.
- Window 14 vs 28: similar rates. Window 28 with min nights 14 gives the same picture with fewer evaluable nights.

Proposed defaults [hypothesis]:
- **Window**: the last 28 valid main nights (not calendar days), excluding the evaluated night. This fits the 30-day
  HC read limit without the history permission. [from documentation]
- **Minimum nights**: 7 valid nights per metric before a metric can be "outside" (Apple and Samsung use 7); show
  "calibrating" before that. Full confidence at 14. [from documentation, analogs]
- **Scale**: max(robust SD of the window, floor above). Keep baselines per source. Never seed an OHealth baseline
  from importer data without a source-shift check, because sampling and value distributions differ.
- **z threshold**: |z| ≥ 2.5 in the concern direction per metric, and notify only when **≥ 2 metrics** are outside
  (Apple's rule [from documentation]). On this data that gives 0 nights over 51 at 2.5 and 1 at 2.0.
- **Sleep duration** is evaluated but, alone, is the noisiest flagger (6–8% over z > 2.5, lag-1 autocorr ≈ 0). It
  should count toward the "≥ 2 metrics" rule only together with a physiological metric. [hypothesis]
- Use asymmetric thresholds by direction (HR and RR up, SpO2 down). [from documentation, analogs: Oura]
- Ignore the source's resting HR in v1 (section 4).
- Treat PulseStages and SaA as non-independent: exclude PulseStages sessions, and treat SaA HR as a duplicate of the
  default source when both exist.
