# NightBaseline algorithm

How NightBaseline turns a night of Health Connect data into "within your usual range" or "moved outside your usual
range". The code is `:core-algo` (pure Kotlin, no Android); the app and the command-line tool `:algo-cli` run exactly
the same code. NightBaseline is not a medical device: it compares a person with their own recent nights and makes no
diagnosis.

Evidence behind the choices: [docs/research/metrics-matrix.md](docs/research/metrics-matrix.md) (the developer's own
data, aggregates only) and [docs/research/analogs.md](docs/research/analogs.md) (Apple, Samsung, Garmin, Withings,
Oura, Fitbit). Every number below is a default in `AlgoParams` / `DefaultMetrics` / `CompletenessParams`.

## 1. Pipeline

```
sleep sessions + samples of the selected source (Health Connect)
  → main night per date          NightSelector
  → is the night complete?       Completeness
  → nightly value per metric     MetricExtractor
  → personal range per metric    Evaluator.baseline
  → night status                 Evaluator.evaluate
```

Only the selected source app is read (OHealth by default; any app that writes sleep sessions works). Sessions written
by PulseStages are always ignored: they are derived from the same heart rate and would count the night twice.

## 2. Main night

1. Sessions of the source closer than **90 min** are merged into one cluster (a night interrupted by a wake-up). On
   the developer's data any gap between 60 and 120 min gives identical nights.
2. A cluster belongs to the local date of its midpoint **+ 6 h**: a midpoint between 18:00 the previous day and
   18:00 belongs to that day. Unlike a fixed wake-up window this keeps late sleepers' nights (a fixed "ends before
   14:00" window dropped about 30 % of nights in one historical source).
3. The cluster with the most sleep is the night; other clusters of the date (naps) are ignored.
4. If a second cluster of the same date also has **≥ 3 h**, the date is *ambiguous* (for example a long day sleep after
   a night shift) and is not compared.
5. Nights with less than **3 h** of sleep are *short*: shown, not compared, not used for the range.

Sleep time is the sum of the session lengths; when the source writes stages, awake stages are excluded. Samples are
taken only inside the sessions; gaps between merged sessions count as awake.

## 3. When is the night complete?

Sources publish a night in several uploads and some measurements arrive late (on OHealth SpO2 about an hour and
respiratory rate about half an hour after the measurement). The night is evaluated when all of these hold:

- the person is up: a step record of ≥ 5 steps/min begins at least 2 min after the visible end of the night (sources
  without step records: the night ended at least 3 h ago);
- heart rate reaches the end of the night minus 5 min, respiratory rate minus 30 min, SpO2 minus 60 min (only for
  measurements the source writes at all);
- the source has written nothing new for the night for 15 min (records with data inside the night: its sessions and
  samples; the day's step records and daytime heart rate do not count);

or **12 h** have passed since the end; then the night is evaluated with what is there. The app checks again later
while it waits. One re-evaluation is allowed after the source's next-morning rewrite; a second notification is sent
only if the result changed into a notification-worthy one.

## 4. Nightly values

| Metric | Value | Ignored | Quality gate |
|---|---|---|---|
| Heart rate during sleep | 10th percentile of the samples | first 30 min | ≥ 30 samples, ≥ 60 % of 10-min bins covered |
| Respiratory rate | mean after dropping the lowest and highest 10 % | first 30 min | ≥ 6 samples, ≥ 50 % of 30-min bins |
| SpO2 | mean | — | ≥ 4 samples, ≥ 50 % of 60-min bins |
| Sleep duration | minutes asleep | — | — |
| HRV (RMSSD), if the source writes it | median | — | ≥ 3 samples, ≥ 30 % of 60-min bins |
| Skin temperature, if the source writes it | mean delta (interval records placed at their midpoint) | — | ≥ 1 record |

Samples outside plausible ranges (e.g. heart rate outside 25–200 bpm) are dropped. A metric without enough data has
no value that night ("too few readings", "low coverage", "not recorded by the source") and simply does not count.

Why the 10th percentile for heart rate: it is the settled low level of the night. On the developer's data it varies
less from night to night than the median and is not pulled up by awakenings. The source's own "resting heart rate"
record is not used: on OHealth it is a daytime value for the calendar day, unrelated to sleep.

## 5. Personal range

For each metric: the values of the last **28 valid nights** (at most **60 days** back, excluding the night itself and
short or ambiguous nights).

- centre = median
- spread = max(1.4826 × median absolute deviation, **floor**)
- the night deviates when it is more than **2.5 × spread** (and at least a minimum absolute distance) from the centre,
  on the side that matters for the metric

| Metric | Floor | Minimum distance | Side that counts |
|---|---|---|---|
| Heart rate during sleep | 2.5 bpm | 3 bpm | higher |
| Respiratory rate | 0.3 /min | 0.8 /min | higher |
| SpO2 | 0.3 % | 1 % | lower |
| Sleep duration | 45 min | 60 min | both, but only together with another metric |
| HRV | 3 ms | 8 ms | lower |
| Skin temperature | 0.2 °C | 0.3 °C | higher |

A value outside the range on the side that does not count (e.g. a lower heart rate) is shown as "outside your usual
range" but never contributes to a notification.

The lower edge of the sleep duration range never goes below the short-night limit (3 h): a shorter night is a short
night and is not compared, so a lower edge under it would only be misleading. When a person's sleep length varies a
lot the range is wide (Metric detail then says that only big changes are noticed).

The floors are close to the typical night-to-night variability measured on real data. Without them a very stable
history makes tiny changes look like deviations; with floors at one measurement step (1 bpm, 0.5 %, 0.5 /min) heart
rate alone left the range on 16–18 % of nights.

A range needs **7 valid nights** (the learning period, as Apple and Samsung do). Until then the night shows
"learning, n of 7".

## 6. Night status and notification

| Status | Meaning | Notification |
|---|---|---|
| All within | every compared metric inside its range | no |
| Some moved | at least one metric outside, below the notification rule | no |
| Moved | **≥ 2** metrics outside on their counting side | **yes**, once per night |
| Learning | fewer than 7 valid nights | no |
| Short night | under 3 h | no |
| Two long sleeps | ambiguous date | no |
| Not evaluated | no metric had enough data | no |

Sleep duration counts towards the two only together with at least one physiological metric: on its own it is the
noisiest metric and changes with everyday life.

Sensitivity setting: *Balanced* (default) z 2.5; *Fewer notes* z 3.0; *More notes* z 2.0.

Stored evaluations follow the current algorithm. Each stored night carries a fingerprint: `AlgoFingerprint.ALGO_VERSION`
(bumped on every change that can change an evaluation of the same summaries) and a hash of the effective parameters,
sensitivity included. When a stored fingerprint differs from the current one (after an app update or a sensitivity
change), every stored night of that source is evaluated again from its stored summary against the other stored
summaries, at app start, after the setting changes and before each night check. Nothing is read from Health Connect
for this, and re-evaluated past nights are never notified.

## 7. Validation

**Synthetic people** (`SyntheticBehaviourTest`, 200 people × 90 nights, with one 3-night episode each that raises heart
rate and respiratory rate and lowers SpO2, plus missing, short, split nights and naps): false notifications on
**0.05 %** of normal nights (about once in 6 years), episodes found **196 of 200**. These numbers check that the
algorithm does what it is designed to do; parameters were not tuned to the developer's data alone.

**Real history** (the developer's own, `algo-cli run`, aggregates only):

| Source | Nights | Compared | Notifications | Single-metric nights |
|---|---|---|---|---|
| OHealth (Oppo Watch X3), 2 months | 58 | 51 | 0 | 5 |
| Previous wearable (imported), several years | 945 | 856 | 7 (about one every 4 months) | 69 |

No labelled illness episodes exist in this history, so only the false-alarm side can be judged on real data.

## 8. Reproducing

```sh
mise exec -- ./gradlew :core-algo:test                     # unit and synthetic tests
mise exec -- ./gradlew :algo-cli:installDist
algo-cli/build/install/algo-cli/bin/algo-cli synthetic --seed 1
algo-cli/build/install/algo-cli/bin/algo-cli run --dataset .data/dataset --origin <package>   # own export, see docs/DATASET.md
```
