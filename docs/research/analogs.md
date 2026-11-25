# Analogs: personal nightly baseline and deviations

Research date: 2026-11-25. Sources: manufacturer support pages and reviews; the apps were
not run. Tags:
- `[from official documentation <url>]`: stated directly on the manufacturer's page;
- `[from reviews <url>]`: third-party review, forum or blog; not confirmed by the manufacturer;
- `[hypothesis]`: our own inference or assumption.

## Summary table

| Product | Nightly metrics | Nights until first baseline | Baseline window | Deviation rule | Display |
|---|---|---|---|---|---|
| Apple Watch Vitals (watchOS 11+) | heart rate, respiratory rate, SpO2, wrist temperature, sleep duration (on Series 12 / Ultra 4, HRV replaces sleep duration) | 7 nights | not disclosed | morning notification if **>=2** overnight metrics are outside the typical range | each metric has a "Typical" or "Outlier" status, dots, 7-day history; in the Health app, an outlier count and High/Typical/Low days |
| Samsung Health Vitals (Galaxy Watch) | heart rate, respiratory rate, skin temperature, SpO2, HRV | 7 nights | not disclosed | "significant changes", threshold not disclosed | Vitals tile on the watch, "About vitals" on the phone |
| Garmin Health Status | heart rate, HRV, respiratory rate, skin temperature, SpO2 during sleep | 3 weeks | not disclosed | per metric: inside or outside the baseline | blue: within baseline, orange: outside baseline; tap for history, baseline, tips |
| Garmin HRV Status | overnight HRV | 3 weeks | 7-day average vs. baseline range | Balanced / Unbalanced / Low; Poor: by age-based norm | colored baseline-range bar, green / orange / red |
| Fitbit / Google Health Metrics | respiratory rate, HRV, skin temperature variation, SpO2, resting heart rate | 1 full day (temperature: 3 nights) | up to 30 days | outside the "personal range" | dashboard with a personal range for each metric |
| Oura Readiness | resting heart rate (nightly minimum), HRV Balance, body temperature, Recovery Index, sleep, etc. | up to 2 weeks | short-term: 14 days, "long-term": 2 months, HRV: 3 months | per-contributor thresholds, combined into a score | 0-100: Optimal / Good / Fair / Pay attention, red bar on the contributor |
| WHOOP Recovery and Health Monitor | HRV, resting heart rate, sleep, respiratory rate; Health Monitor adds skin temperature and SpO2 | 4 days (first days are gray) | not officially disclosed, about 30 days per forum | each metric inside or outside the "personal normal range" | Health Monitor: green check or yellow/orange/red |
| Withings ScanWatch (2 / Vitals / Nova) | nightly temperature with a baseline; heart rate and breathing at night as charts | not disclosed | not disclosed | notifications about temperature baseline shifts | tapping the watch shows the deviation from baseline |

## Apple Watch: Vitals (watchOS 11+)

This is the closest analog in logic. The feature must not be named "Vitals" (see CLAUDE.md).

- Nightly metrics: heart rate, respiratory rate, wrist temperature, SpO2, sleep duration. On Series 12 and
  Ultra 4, HRV replaces sleep duration, and a daytime view appears (resting heart rate and HRV).
  [from official documentation https://support.apple.com/guide/watch/vitals-apd15aa7ed96/watchos]
  [from official documentation https://support.apple.com/en-gb/120142]
- The typical range appears after 7 nights wearing the watch: "Wear your Apple Watch to sleep for seven days to
  establish your typical range". [from official documentation https://support.apple.com/en-gb/120142]
- Notification rule: "By default, you receive notifications when at least two of your overnight metrics are
  outside of your typical range". The notification arrives the next morning and names possible factors:
  medication, elevation, alcohol, illness. [from official documentation https://support.apple.com/guide/watch/vitals-apd15aa7ed96/watchos]
  [from official documentation https://support.apple.com/en-gb/120142]
- Screen: a nightly summary with all metrics; "More Info" explains whether metrics are in the typical range. Tapping
  a metric shows factors that raise or lower it. A calendar shows the last 7 days. In the
  Health app (Browse > Vitals) there are two views: "Metrics" with the number of outliers per metric over the period, and "States" with
  the number of days in High / Typical / Low. Scales D / W / M / 6M.
  [from official documentation https://support.apple.com/en-gb/120142]
- What it looks like, per reviews: each metric has a "Typical" or "Outlier" status. Five dots: filled means
  within range, outlined means outlier; outliers are highlighted in pink. A single outlier alone does not trigger a notification.
  [from reviews https://www.wareable.com/apple/how-vitals-app-finally-makes-apple-watch-a-wellness-powerhouse]
  [from reviews https://www.digitaltrends.com/wearables/apple-watch-vitals-one-of-the-best-apps-ive-used-in-years/]
  [from reviews https://tomsguide.com/wellness/smartwatches/watchos-11-public-beta-i-tried-the-new-apple-watch-vitals-health-app-heres-how-it-works-and-first-impressions]
- Tone: neutral, no diagnoses; "Your Apple Watch is not a medical device". Beyond the 7-day window, the baseline
  algorithm is not disclosed. [from official documentation https://support.apple.com/guide/watch/vitals-apd15aa7ed96/watchos]

## Samsung Health: Vitals (Galaxy Watch)

- Five metrics during sleep: heart rate, respiratory rate, skin temperature, SpO2, HRV. To get a baseline,
  you need to sleep wearing the watch for 7 nights. The watch looks for "significant changes", but the threshold and notifications are not described on the page.
  [from official documentation https://www.samsung.com/us/support/answer/ANS10013280/]
- Skin temperature: requires at least 4 hours of wear during sleep and at least 5 nights per week, with the watch snug on the wrist.
  One "representative" temperature is shown per day; skin temperature is not body temperature.
  [from official documentation https://www.samsung.com/us/support/troubleshoot/TSG10004565]
- Reviews: the temperature baseline is built from roughly a week of nights.
  [from reviews https://techradar.com/news/what-is-the-hot-new-sensor-on-the-samsung-galaxy-watch-5-and-why-cant-you-use-it]
- How the baseline and deviations are shown on screen is not visible from the available pages. [hypothesis: likely similar to Apple]

## Garmin: Health Status and HRV Status (Morning Report)

- Health Status: built from sleep data: heart rate, HRV, respiratory rate, skin temperature, SpO2 during sleep (the set
  depends on the model). Blue: inside the calculated baseline, orange: outside it. After 3 weeks of wear,
  each metric unlocks history, baseline and tips.
  [from official documentation https://www8.garmin.com/manuals/webhelp/GUID-3A4F9C4A-8735-46C0-8DA9-65F11400B150/EN-US/GUID-6335963E-C254-4239-9450-A0212850F303.html]
- HRV Status: requires 3 weeks of consistent sleep data. The overnight 7-day average is compared with the personal
  baseline range: Balanced (in range, green), Unbalanced (above or below, orange), Low (well
  below, red). Poor: below the age-based norm, without comparison to the personal baseline. "No status": not enough data for a
  7-day average.
  [from official documentation https://www8.garmin.com/manuals/webhelp/GUID-E3AB50C9-691D-4482-8387-7EEB3961CA87/EN-US/GUID-9282196F-D969-404D-B678-F48A13D8D0CB.html]
- Training Readiness takes into account last night's sleep score, sleep over 3 nights, HRV status, load, and stress over 3 days.
  [from official documentation https://www8.garmin.com/manuals/webhelp/GUID-31D23DBB-57C2-4DF7-A0C9-8D1A00AB4BE7/EN-US/GUID-C21BE0C8-A08E-4DA1-B6C6-2E0E2DDDB372.html]
- Morning Report: a customizable morning screen. Some data appears after a week, the full baseline after
  3 weeks. [from reviews https://the5krunner.com/2022/06/28/garmin-morning-report/]
- Garmin does not disclose the baseline range width or the algorithm. [from official documentation, see above]

## Fitbit / Google Health: Health Metrics

- Metrics: respiratory rate, HRV, skin temperature variation, SpO2, resting heart rate (the set depends on the device).
  [from official documentation https://support.google.com/googlehealth/answer/14236917?hl=en]
- Personal range "based on the average of your recent data", computed from **up to 30 days** of data. Requires
  at least one full day of wear; for temperature, 3 nights. Most metrics need at least 3 hours
  of quality sleep. [from official documentation https://support.google.com/googlehealth/answer/14236917?hl=en]
- Disclaimer: "provides information for well-being and is not intended to diagnose or treat any medical
  condition". [from official documentation https://support.google.com/googlehealth/answer/14236917?hl=en]
- The dashboard shows which metrics have moved away from the baseline. One support page mentions a
  notification when a metric is out of range.
  [from reviews https://9to5google.com/2023/03/14/fitbit-health-metrics/]

## Oura: Readiness

- Nine contributors in three groups. Body: Resting Heart Rate, HRV Balance, Body Temperature, Recovery
  Index. Sleep: Sleep, Sleep Balance, Sleep Regularity. Activity: Previous Day Activity, Activity Balance.
  [from official documentation https://support.ouraring.com/hc/en-us/articles/360057791533]
- Calibration: "It can take up to two weeks for the Oura App to learn your average values".
  [from official documentation https://support.ouraring.com/hc/en-us/articles/360057791533]
- Windows:
  - "long-term": the last 2 months;
  - HRV Balance: 14 days vs. the 3-month average, recent days weighted more;
  - Activity Balance: 14 days vs. 2 months;
  - Sleep Balance and Sleep Regularity: 2 weeks;
  - resting heart rate: last night's minimum heart rate vs. the long-term average;
  - temperature: last night vs. the long-term nightly average.
  [from official documentation https://support.ouraring.com/hc/en-us/articles/360057791533]
- Thresholds: the resting heart rate score drops at +3-5 bpm or -10-15 bpm from usual (asymmetric!). High
  temperature triggers a notification and suggests enabling Rest Mode.
  [from reviews: paraphrase of an Oura support page in search results, the text itself not checked; hypothesis until verified]
  [from official documentation https://ouraring.com/blog/readiness-score/]
- Scale 0-100: 85+ Optimal, 70-84 Good, 60-69 Fair, <60 Pay attention. A problematic contributor is marked with a red
  bar and the label "pay attention".
  [from official documentation https://support.ouraring.com/hc/en-us/articles/360057791533]

## WHOOP: Recovery and Health Monitor

- Recovery is computed from HRV, resting heart rate, sleep and respiratory rate. Respiratory rate noticeably affects the score
  only when it is elevated relative to the baseline.
  [from official documentation https://whoop.com/thelocker/adding-respiratory-rate-to-recovery]
- The first 4 days are gray: the baseline is calibrating. Full calibration takes about 30 days (this is a forum answer, not
  documentation); another forum post says 14 days.
  [from official documentation https://whoop.com/thelocker/adding-respiratory-rate-to-recovery]
  [from reviews https://community.whoop.com/t/how-long-does-it-take-for-whoop-to-learn-your-baseline-metrics/359]
- Health Monitor shows whether each metric is in the "personal normal range": HRV, resting heart rate, respiratory rate,
  skin temperature, SpO2. Green check: in range, otherwise yellow or orange and red. Caveat: all metrics
  green does not necessarily mean green Recovery.
  [from official documentation https://whoop.com/thelocker/health-monitor-feature]

## Withings: ScanWatch 2 / Vitals / Nova

- Temperature: the starting baseline is a standard one. Status is built from nightly measurements because at night
  they are more stable and a baseline shift is more noticeable. Tapping the watch shows the deviation from it. There are notifications about
  temperature baseline shifts. Values are inaccurate during a fever.
  [from official documentation https://support.withings.com/hc/en-us/articles/18005024715281]
  [from official documentation https://www.withings.com/sg/en/scanwatch-2/shop]
- At night, heart rate and breathing are shown as charts; whether they are compared with a personal baseline is not described. Detailed
  breathing analytics are in the Withings+ subscription.
  [from official documentation https://withings.com/ca/en/scanwatch]

## Takeaways for NightBaseline

1. The ">=2 metrics out of range" threshold matches Apple, the only manufacturer that explicitly publishes
   a multi-metric rule. [from official documentation https://support.apple.com/guide/watch/vitals-apd15aa7ed96/watchos]
   The others evaluate metrics individually or combine them into a score. [from official documentation, see sections above]
2. The ">=7 nights before first conclusions" threshold matches Apple and Samsung. Garmin waits 3 weeks, Oura up to 2 weeks,
   WHOOP 4 days before first scores. Our 14-28 night window is comparable to Fitbit (up to 30 days) and Oura (14 days
   vs. 2-3 months). [from official documentation, see above]
3. In the UI, each metric has a two-level status: "in range" / "out of range" (Apple Typical/Outlier, Garmin
   blue/orange, WHOOP check), plus an up or down direction and a baseline band on the chart. Only Apple has its own history
   window (7 days). [hypothesis: for us, 7 history points and a band based on median and spread]
4. Thresholds are better made asymmetric by direction: for heart rate a rise is concerning, for HRV a drop, for SpO2 a
   drop. Oura explicitly penalizes resting heart rate differently up vs. down. [hypothesis, based on Oura]
5. Tone: "typical range", "outside your typical range", "changed from recent history", possible factors
   (alcohol, illness, elevation, medication) and a "not a medical device" disclaimer. All of this is consistent with
   CLAUDE.md. [from official documentation https://support.apple.com/en-gb/120142]
6. Example for a cheap quality filter: Samsung requires >=4 h of sleep, Fitbit >=3 h of quality sleep.
   [hypothesis: include a night in the baseline only if sleep >=3-4 h]
