# State catalog

Status: draft for stage 3. Every data state of every screen: technical trigger, what the person sees (title, body,
action and where it leads, icon/shape cue) and the copy. Screen structure and components: `screens.md`. Glyph shapes:
`screens.md` section 6.

## 0. Copy conventions and where the Russian column lives

- Every visible string has a resource key (`snake_case`). The English text is in the tables below and becomes
  `res/values/strings.xml`.
- **Russian copy:** the `ru` column of every table is kept in `app/src/main/res/values-ru/strings.xml` (moved from
  the design draft), keyed by the same key; English is in `app/src/main/res/values/strings.xml`. Placeholders are
  positional (`%1$s`), always pre-formatted strings, with the same position for the same name in both languages; the
  `{name}` forms below are the readable names. It is not inline here because ADR 0002 (enforced by
  `tools/check-language.sh` and the pre-commit hook) allows Cyrillic only in `res/values-ru*` resources and
  `README.ru.md`. This file therefore contains no Cyrillic at all (review round 0). Each key in the tables below
  exists in both files (`StringResourcesTest` compares the key sets); the `ru` column shows "yes".
- Tone: calm, plain, second person, no diagnoses, no exclamation marks, never "Vitals", never "alert"/"warning"/
  "abnormal"/"danger". Core phrase: "moved outside your usual range" (ru: the same phrase family, see the values-ru draft).
- Placeholders: `{source}` source app label from the package manager; `{metric}` metric display name; `{n}`, `{k}`
  integers formatted per locale; `{date}` localized medium date; `{value}`, `{low}`, `{high}` per-metric number
  format + unit string. Counted nouns use `plurals` resources (Russian needs one/few/many/other).
- Metric names (keys `metric_<id>`, short form `metric_<id>_short` for `Glance` windows, the widget and notifications):

| key | en | ru |
|---|---|---|
| `metric_sleeping_hr` / `metric_sleeping_hr_short` | Sleeping heart rate / Heart rate | yes |
| `metric_respiratory_rate` / `metric_respiratory_rate_short` | Respiratory rate / Breathing | yes |
| `metric_spo2` / `metric_spo2_short` | Blood oxygen (SpO2) / SpO2 | yes |
| `metric_sleep_duration` / `metric_sleep_duration_short` | Sleep duration / Sleep | yes |
| `metric_hrv` / `metric_hrv_short` | Heart rate variability (HRV) / HRV | yes |
| `metric_skin_temperature` / `metric_skin_temperature_short` | Skin temperature / Skin temp. | yes |
| `unit_bpm`, `unit_breaths_per_min`, `unit_percent`, `unit_ms`, `unit_celsius`, `unit_fahrenheit`, `unit_hours_minutes` | bpm, breaths/min, %, ms, °C, °F, "{h} h {m} min" | yes |

## 1. Resolving which state a screen shows

Home and Night detail pick exactly one **night state** (summary card or state card) and zero or one **banner**,
then each tile picks a **metric state**. The night state is the first match in this order:

| Priority | State id | Trigger (technical) |
|---|---|---|
| 1 | `E-HC-NONE` | `HealthConnectClient.getSdkStatus()` == `SDK_UNAVAILABLE` |
| 2 | `E-HC-UPDATE` | `getSdkStatus()` == `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` |
| 3 | `E-PERM` | granted permissions lack `READ_SLEEP` or `READ_HEART_RATE` (checked on every resume and before every daily run) |
| 4 | `E-SOURCE-GONE` | the selected source package is no longer installed (`PackageManager` lookup fails); `{source}` is the last stored label |
| 5 | `LOADING` | no stored evaluation yet and the first read is running (shown only after 300 ms) |
| 6 | `L0` | `NightStatus.LEARNING` and `historyNights == 0` and no valid night stored |
| 7 | `L1`..`L6` | `NightStatus.LEARNING`, `historyNights` = 1..6 |
| 8 | `E-SYNC` | no night for the expected wake date, the current time is past 18:00 (the end of the date's night window in midpoint keying, `nightKeyShiftHours` = 6) or the daily run's last attempt finished, and the newest record of any type from `{source}` ends before the night's earliest plausible start (18:00 the previous day): the source has not written anything since last evening |
| 9 | `E-NODATA` | no night for the expected wake date, but `{source}` has written records that end after 03:00 on the wake date: the source synced, it just has no sleep session for that night |
| 10 | `E-SHORT` | `NightStatus.SHORT_NIGHT` (asleep minutes < `minNightMinutes`, 180) |
| 10b | `E-AMBIGUOUS` | `NightStatus.AMBIGUOUS_NIGHT` (two sleeps of >= 3 h compete for the same date, e.g. a long day sleep after a night shift) |
| 11 | `E-NOTEVAL` | `NightStatus.NOT_EVALUATED` (session exists, every metric missing) |
| 12 | `S-DEV` | `NightStatus.DEVIATIONS` (deviating >= `minDeviatingMetrics`): notification sent |
| 13 | `S-SOME` | `NightStatus.SOME_DEVIATIONS` (1 <= deviating < `minDeviatingMetrics`): no notification |
| 14 | `S-ALL` | `NightStatus.ALL_WITHIN` |

Rules:
- Before 15:00 with no night yet for today, Home keeps showing the **previous** night's state and adds banner
  `B-WAIT`. `E-SYNC` / `E-NODATA` only appear after 18:00 (or when the person taps "Check now" and the
  read finds nothing).
- States 1–4 replace the summary card but never hide stored results: below the state card Home shows the newest
  stored night under the heading "Last result: {night label}" (`home_last_result_heading`).
- "Expected wake date" = today if now >= 03:00 local, otherwise yesterday.

Banners (one at a time, first match wins): `B-BG` > `B-READ-FAILED` > `B-SOURCE-QUIET` > `B-WAIT` > `B-NOTIF-OFF` >
`B-RANGE-READY`.

## 2. Night states

Columns: trigger is in section 1; **cue** = glyph and color token; **action** = primary button -> destination
(secondary in brackets).

| Id | Cue | Title key / en | Body key / en | Action -> destination | ru |
|---|---|---|---|---|---|
| `LOADING` | static skeleton tiles (no shimmer in any mode: idle screens never animate), no glyph | - | `state_loading_body`: "Reading your nights from Health Connect…" | none | yes |
| `L0` | dotted ring, 0 of 7 segments | `state_l0_title`: "Learning starts tonight" | `state_l0_body`: "Sleep with your device as usual. NightBaseline needs 7 nights with sleep data to learn your usual range." | `action_how_it_works`: "How it works" -> range explainer sheet | yes |
| `L1`..`L6` | dotted ring, `LearningProgress` n of 7 segments filled (skipped short nights shown as half-filled segment) | `state_learning_title`: "Learning your usual range" + progress `learning_progress`: "{n} of 7 nights" | `state_learning_body` (plurals on {left}): "{left} more nights with sleep data and NightBaseline starts comparing." If last night was short, add `state_learning_short_note`: "Last night was under 3 hours, so it didn't count." | "How it works" -> range explainer sheet | yes |
| `S-ALL` | circle with check, `color.status.within` | `state_all_title`: "All within your usual range" | `state_all_body` (plurals on {n}): "{n} measurements compared with your last {w} nights." | none (tiles are the actions) | yes |
| `S-SOME` | rounded square with one triangle, `color.status.moved` tonal | `state_some_title_one`: "{metric} moved outside your usual range" / `state_some_title_many` (plurals): "{k} measurements moved outside your usual range" | `state_some_body`: "One measurement on its own often moves. NightBaseline shows it here without a notification." / `state_some_body_many` (when threshold > 2): "Fewer than {threshold} moved, so no notification was sent." | `action_see_metric`: "See {metric}" -> Metric detail (one metric) / none (several) | yes |
| `S-DEV` | rounded square with two triangles, `color.status.moved` | `state_dev_title` (plurals on {k}): "{k} measurements moved outside your usual range" | `state_dev_body`: "{metric list}. This is a change compared with your own recent nights, not a diagnosis. A late night, alcohol, travel, hard training or a cold can all do this." | `action_what_it_means`: "What this means" -> range explainer sheet, "meaning" section | yes |
| `E-SYNC` | cloud-with-clock outline, `color.status.info` | `state_sync_title`: "{source} hasn't sent last night yet" | `state_sync_body`: "Open {source} so it can sync with Health Connect. NightBaseline checks again on its own." | `action_open_source`: "Open {source}" -> source app launch intent [`action_check_now`: "Check now" -> one-time read] | yes |
| `E-NODATA` | empty circle with dash, `color.status.neutral` | `state_nodata_title`: "No sleep recorded last night" | `state_nodata_body`: "{source} has no sleep session that ended last night or this morning. Nights without the device are simply skipped." | `action_see_history`: "See history" -> History [`action_check_now`] | yes |
| `E-SHORT` | half-filled circle, `color.status.neutral` | `state_short_title`: "Short night" | `state_short_body`: "{source} recorded {duration} of sleep. Nights under 3 hours aren't compared and don't change your usual range." Tiles below show values with status "Not compared". | `action_see_history` -> History | yes |
| `E-AMBIGUOUS` | two overlapping half circles, `color.status.neutral` | `state_ambiguous_title`: "Two long sleeps on one day" | `state_ambiguous_body`: "{source} recorded two sleeps of 3 hours or more for this day, so it isn't clear which one was the night. This day isn't compared and doesn't change your usual range." | `action_see_history` -> History | yes |
| `E-NOTEVAL` | empty circle with dash | `state_noteval_title`: "Too few readings last night" | `state_noteval_body`: "{source} recorded your sleep but too few measurements during it to compare. This can happen when the device sits loosely or its battery runs out." | `action_see_history` -> History | yes |
| `E-PERM` | key outline, `color.status.info` | `state_perm_title`: "NightBaseline can't read your sleep data" | `state_perm_body`: "Health Connect access was turned off. Your earlier results are still here. Turn access back on to continue." | `action_open_hc_permissions`: "Turn on access" -> Health Connect permission request (falls back to HC app permission settings if the request contract returns without UI) | yes |
| `E-HC-UPDATE` | download-arrow outline, `color.status.info` | `state_hc_update_title`: "Health Connect needs an update" | `state_hc_update_body`: "NightBaseline reads your nights through Health Connect. Install or update it, then come back." | `action_open_store`: "Install or update" -> `market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding` [`action_check_again`: "Check again"] | yes |
| `E-SOURCE-GONE` | cloud-with-clock outline, `color.status.info` | `state_source_gone_title`: "{source} is no longer on this phone" | `state_source_gone_body`: "Choose another app that writes sleep data to Health Connect. Your earlier results stay here." | `action_choose_source`: "Choose source" -> Source picker | yes |
| `E-HC-NONE` | slashed circle, `color.status.neutral` | `state_hc_none_title`: "Health Connect isn't available here" | `state_hc_none_body`: "NightBaseline needs Health Connect, which this phone or profile doesn't support. Nothing is read or stored." | `action_about`: "About NightBaseline" -> About | yes |

Night label and headings: `night_label`: "Night of {start_date}–{end_date}" (en uses "Nov 24–25", ru uses the
wake-date form, literally "Night to Nov 25"), `home_title`: "Last night", `home_last_result_heading`: "Last result: {night
label}". All drafted in ru.

## 3. Banners (non-blocking, above still-valid content)

| Id | Trigger | Cue | Text key / en | Action -> destination | ru |
|---|---|---|---|---|---|
| `B-WAIT` | before 15:00, no night yet for the expected wake date | moon outline | `banner_wait`: "Last night isn't in yet. {source} usually syncs after you wake up." | `action_check_now` -> one-time read | yes |
| `B-BG` | `READ_HEALTH_DATA_IN_BACKGROUND` not granted (core permissions present) | key outline | `banner_bg`: "The daily check needs background access. Without it, results appear only when you open the app." | `action_allow`: "Allow" -> HC permission request | yes |
| `B-READ-FAILED` | the last daily run's Health Connect read failed for a reason other than permissions (service error, rate limit, timeout) and will be retried | cloud-with-clock outline | `banner_read_failed`: "Health Connect didn't answer this morning. NightBaseline tries again later." | `action_check_now` -> one-time read | yes |
| `B-SOURCE-QUIET` | no record of any type from `{source}` for >= 3 days | cloud outline | `banner_source_quiet` (plurals): "No new data from {source} for {n} days." | `action_choose_source`: "Choose source" -> Source picker | yes |
| `B-NOTIF-OFF` | `POST_NOTIFICATIONS` denied or channel `night_results` blocked, and the person has not dismissed this banner | bell with slash | `banner_notif_off`: "Notifications are off. Results show up here, but you won't get a morning note." | `action_turn_on`: "Turn on" -> system app notification settings; dismiss (x) remembers for 30 days | yes |
| `B-RANGE-READY` | first night with a non-learning status, shown once | dotted ring turning solid (morph; static under reduced motion) | `banner_range_ready`: "Your usual range is ready. From now on each night is compared with it." | dismiss | yes |

## 4. Metric states (tiles, Metric detail header, notification lists)

| Id | Trigger | Glyph / band | Label key / en | Detail sentence key / en | Action on detail | ru |
|---|---|---|---|---|---|---|
| `M-WITHIN` | `MetricStatus.WITHIN` | filled circle inside band | `status_within`: "Usual" | `detail_within`: "Inside your usual range." | - | yes |
| `M-HIGH` | `ABOVE`, `deviates` | up triangle right of band | `status_higher`: "Higher than usual" | `detail_higher`: "{delta} above your usual range." | - | yes |
| `M-LOW` | `BELOW`, `deviates` | down triangle left of band | `status_lower`: "Lower than usual" | `detail_lower`: "{delta} below your usual range." | - | yes |
| `M-SIDE-IGNORED` | value outside on a side the metric's `Sensitivity` ignores (e.g. SpO2 above range) | filled circle, marker outside band but no triangle | `status_within` | `detail_side_ignored`: "Higher values of {metric} are shown but don't count as a change." | - | yes |
| `M-LEARN` | `MetricStatus.LEARNING` (metric has < 7 values, e.g. appeared later) | dotted ring, dotted band outline | `status_learning`: "Learning, {n} of 7" | `detail_learning`: "This measurement needs {left} more nights before it gets a range. It doesn't count until then." | "How it works" | yes |
| `M-NODATA` | `MISSING`, `MissingReason.NO_DATA` | dash, no marker, band shown | `status_no_value`: "No value last night" | `detail_no_data`: "{source} recorded no {metric} during your sleep last night." | - | yes |
| `M-FEW` | `MISSING`, `TOO_FEW_SAMPLES` | dash | `status_no_value` | `detail_too_few`: "Only {k} readings during sleep; at least {min} are needed to compare." | - | yes |
| `M-COVERAGE` | `MISSING`, `LOW_COVERAGE` | dash | `status_partial`: "Partial night" | `detail_low_coverage`: "Readings covered {p} of your sleep; at least {min} is needed to compare." | - | yes |
| `M-NOTCOMPARED` | night is `SHORT_NIGHT` | dash next to the value | `status_not_compared`: "Not compared" | `detail_not_compared`: "Short nights aren't compared." | - | yes |
| `M-NOTPROVIDED` | `MissingReason.NOT_PROVIDED` (source never writes the channel) | slashed circle, no band | `status_not_provided`: "Not recorded by {source}" | `detail_not_provided_title`: "{source} doesn't record {metric}" + `detail_not_provided_body`: "{source} doesn't send this measurement to Health Connect. If it starts, NightBaseline picks it up and learns its range automatically." | `action_choose_source` -> Source picker (secondary) | yes |
| `M-PERM` | permission for this metric's record type not granted (core ones present) | key outline, no band | `status_access_off`: "Access off" | `detail_access_off`: "Health Connect access to {metric} is turned off." | `action_open_hc_permissions` | yes |

### 4.1 "Metric not provided by the source" on Home and Metric detail

- **Core metrics** (sleeping heart rate, respiratory rate, SpO2, sleep duration): if not provided, the tile stays in
  its place but collapses to a single-line "unavailable" tile: name, slashed-circle glyph, "Not recorded by {source}",
  no value, no band, muted text color (still >= 4.5:1). It never counts towards the night status, and the summary body
  says how many were compared ("3 measurements compared…"). Sleep duration is always provided when a session exists.
- **Optional metrics** (HRV, skin temperature, future ones): no tile. One row after the tiles:
  `home_not_recorded_row`: "Not recorded by {source}: {metric list}" with an info icon -> sheet with
  `detail_not_provided_body`. The row is hidden when every metric is provided.
- **Metric detail** (reachable via an old night, History, or deep link): header card shows the `M-NOTPROVIDED` state.
  If stored nights from an earlier source exist, the chart still shows them with the band they had, plus the line
  `detail_not_provided_since`: "No values since {date}." If there are none, the chart area is replaced by a
  `StateCard` with the same title and body and the secondary action "Choose source".
- If the source starts writing the channel, the metric enters `M-LEARN` and `B-RANGE-READY` is not repeated (per-metric
  learning completes silently).

## 5. Per-screen state matrix

Which states each screen shows and how (`card` = state card replaces the main content, `banner`, `tile` = per-metric,
`inline` = small text in place, `-` = not applicable).

| State | Home | Night detail | Metric detail | History row / cell | Widget | Notification |
|---|---|---|---|---|---|---|
| `LOADING` | skeleton | skeleton | skeleton header + chart frame | skeleton rows | "–" glyph, content description `state_loading_body` | - |
| `L0` | learning card 0 of 7 | - | `M-LEARN` header, empty chart with dotted band area, `detail_learning` | `H-EMPTY` (section 7.2) | "Learning, 0 of 7" | - |
| `L1`..`L6` | learning card, tiles with values, dotted bands | learning card for that night | header `M-LEARN`, chart shows points (hollow), no band | dotted ring, "Learning" | "Learning, n of 7" | - |
| `S-ALL` | summary card + tiles | same | header `M-WITHIN` | check circle, "All usual" | check + "All usual" | none |
| `S-SOME` | summary card + tiles | same | header with metric state | one-triangle square, "1 moved" | "1 moved" | none |
| `S-DEV` | summary card + tiles | same | header with metric state | two-triangle square, "{k} moved" | "{k} moved" | yes (section 6) |
| `E-SYNC` | card (previous night below under "Last result") | card for that date if it was never filled | inline `detail_waiting_sync` under header: "Waiting for {source} to sync." | not listed (no night) | cloud-with-clock + "Waiting for sync" | status notification once `B-SOURCE-QUIET` reaches 3 days (section 6.2) |
| `E-NODATA` | card | card | chart gap; tooltip "No value" | dash circle, "No data" (only for dates that later got no session after the source synced) | dash + "No data" | - |
| `E-SHORT` | card + tiles with `M-NOTCOMPARED` | same | point drawn hollow-gray, tooltip "Short night, not compared" | half circle, "Short night" | half circle + "Short night" | - |
| `E-NOTEVAL` | card | card | gap | dash circle, "Too few readings" | dash + "Too few readings" | - |
| `E-SOURCE-GONE` | card + last result | card | card in place of the header sentence, stored chart below | rows still listed | cloud-with-clock + "Choose source" | status notification (6.2) |
| `E-PERM` | card + last result | card | card in place of chart, stored chart below | rows still listed; header banner `banner_perm_history`: "Access is off. New nights won't appear." | key + "Access off" | status notification (6.2) |
| `E-HC-UPDATE` | card + last result | card | card | banner as E-PERM with `banner_hc_history`: "Health Connect needs an update. New nights won't appear." | download glyph + "Update needed" | status notification |
| `E-HC-NONE` | card only | - | - | `H-EMPTY` | slashed circle | - |
| `M-NOTPROVIDED` | collapsed tile (core) / footer row (optional) | same | section 4.1 | per-metric glyph row omits it | omitted | omitted |
| `B-*` banners | banner | `B-BG`, `B-READ-FAILED`, `B-NOTIF-OFF` only | - | `B-SOURCE-QUIET` only | - | `B-BG`, `B-SOURCE-QUIET` (6.2) |

## 6. Notification spec

### 6.1 Night result notification

| Property | Value |
|---|---|
| Channel id / name / description | `night_results` / `channel_night_results_name`: "Night results" / `channel_night_results_desc`: "A note in the morning when two or more measurements move outside your usual range." (ru drafted) |
| Importance | `IMPORTANCE_DEFAULT` (sound/vibration per system settings, no heads-up pop-up, respects Do Not Disturb). Not HIGH: nothing here is urgent. |
| When posted | Once per wake date, at the end of the daily run, only if `NightEvaluation.notify` (`S-DEV`), notifications are enabled in Settings, and the wake date is today or yesterday (a late sync from older days updates the app silently). Never for `S-SOME`, `S-ALL`, learning or problem states. |
| Id / tag / replacement | Fixed id `1001`, tag `night`: a newer night replaces the older notification. `setOnlyAlertOnce(true)` so a re-run for the same date does not ring again. |
| Grouping | None: at most one night notification exists at a time, so no group summary. Status notifications use a separate id (6.2) and are not grouped with it. |
| Small icon | Own monochrome glyph `ic_stat_night` (crescent above a horizontal band), 24 dp vector, alpha only, drawn for the project. No vendor or source-app icon, no large icon, no emoji in any text. |
| Accent color | `color.status.moved` (light theme value), used by the system only for the icon tint. |
| Category / visibility | `CATEGORY_STATUS`; `VISIBILITY_PRIVATE` with a public version (below). |
| Title | `notif_dev_title` (plurals on {k}): "{k} measurements moved outside your usual range" |
| Body (collapsed) | `notif_dev_body`: "{metric list}. {night label}." (e.g. "Heart rate and Breathing. Night of Nov 24–25."; `{night label}` is `night_label`, which already contains "Night of", so it is never wrapped in another "night of"). Metric list uses short names, max 3, then `notif_list_more` (plurals): "and {n} more". No values. |
| Body (expanded, `BigTextStyle`) | `notif_dev_body_long`: "Higher than usual: {higher list}. Lower than usual: {lower list}. This compares the night with your own recent nights and isn't a diagnosis. Not a medical device." Empty direction groups are omitted (`notif_higher_prefix`, `notif_lower_prefix`). |
| Public version (lock screen) | Title `notif_public_title`: "NightBaseline"; body `notif_public_body`: "Last night's result is ready." No metric names, no counts, no values. |
| Tap action | `PendingIntent` (immutable) -> `nightbaseline://night/{date}`: Home if this is the newest evaluated night, otherwise Night detail; synthetic back stack to Home. Onboarding gate applies (`screens.md` section 3). |
| Action buttons | None. One clear tap target keeps it calm. |
| Auto-cancel / clearing | `setAutoCancel(true)`; also cancelled when the person opens Home or Night detail for that date. No timeout (replaced by the next night's note at the latest). |
| Wording variants | k = 2: "2 measurements moved outside your usual range"; k >= 3 same template; with `minDeviatingMetrics` = 3 in "Fewer notes" the template does not change. Examples: "Heart rate and Breathing. Night of Nov 24–25." / "Heart rate, Breathing, SpO2 and 1 more. Night of Nov 24–25." |
| Locale | Title, body, numbers and the night label follow the app locale (en, ru); the notification is rebuilt on locale change if still shown. |

### 6.2 Data access notification (problems only)

| Property | Value |
|---|---|
| Channel id / name / description | `data_access` / `channel_data_access_name`: "Problems reading data" / `channel_data_access_desc`: "When NightBaseline can't read your nights, for example after access was turned off." |
| Importance | `IMPORTANCE_LOW` (silent, shade only) |
| Triggers | `E-PERM` or `B-BG` found by the daily run; `E-HC-UPDATE`; `E-SOURCE-GONE`; `B-SOURCE-QUIET` reaching 3 days. Posted once per problem; posted again only after the problem was resolved and came back. Cancelled automatically when resolved. Switchable in Settings ("Problems reading data"). |
| Id | `2001`, tag = problem id |
| Title / body | `notif_perm_title`: "NightBaseline can't read your sleep data" / `notif_perm_body`: "Health Connect access was turned off. Tap to turn it back on." — `notif_hc_title`: "Health Connect needs an update" / `notif_hc_body`: "Tap to see how to continue." — `notif_quiet_title` (plurals): "No new data from {source} for {n} days" / `notif_quiet_body`: "Tap to check your source." — `notif_bg_title`: "The morning check needs background access" / `notif_bg_body`: "Tap to allow it in Health Connect." — `notif_source_gone_title`: "{source} is no longer on this phone" / `notif_source_gone_body`: "Tap to choose another source." |
| Small icon / accent | same `ic_stat_night` glyph; accent `color.status.info` (light value), not the moved hue |
| Public version | Same text (contains no health data), but `{source}` replaced by "your source" (`notif_quiet_title_public`, `notif_source_gone_title_public`). |
| Tap | `nightbaseline://fix/{permission\|health-connect\|source-sync}` -> Home with the matching state in view (`E-PERM`, `B-BG` -> `permission`; `E-HC-UPDATE` -> `health-connect`; `B-SOURCE-QUIET`, `E-SOURCE-GONE` -> `source-sync`) |

### 6.3 One-time "range ready" note

Silent post on `night_results` (`setSilent(true)`) after the learning period ends, only if notifications are on:
`notif_ready_title`: "Your usual range is ready" / `notif_ready_body`: "From tonight on, each night is compared with
it." Tap -> Home.

## 7. Screen-specific states

### 7.1 Metric detail

| Id | Trigger | Presentation and copy | ru |
|---|---|---|---|
| `D-PERIOD-SHORT` | selected period > stored nights | chart shows what exists; `detail_period_short` (plurals): "Showing {n} nights. More appear as they're recorded." | yes |
| `D-BAND-CHANGED` | the band's median moved > 1 spread within the period (e.g. after a source change) | note under chart `detail_band_changed`: "Your usual range shifted on {date}." | yes |
| `D-SELECTED` | point selected | tooltip: `chart_tooltip`: "{date}: {value}, {status label}"; night without a value `chart_tooltip_no_value`: "{date}: no value"; short night `chart_tooltip_short`: "{date}: short night, not compared" | yes |
| chart summary | always | keys `chart_summary_header`, `chart_summary_range`, `chart_summary_last`, `chart_summary_outside`, `chart_summary_all_inside`, `chart_summary_extremes`, `chart_summary_gaps` (pattern and English text in `screens.md` section 8) | yes |

### 7.2 History

| Id | Trigger | Presentation and copy | Action | ru |
|---|---|---|---|---|
| `H-LOADING` | first page query running > 300 ms | skeleton rows | - | - |
| `H-EMPTY` | no stored nights | glyph dotted ring, `history_empty_title`: "Your nights will appear here" / `history_empty_body`: "After each night with sleep data, NightBaseline adds it to this list." | none | yes |
| `H-FILTER-EMPTY` | filter "Moved" with no matches | `history_filter_empty`: "No nights moved outside your usual range in this period." | `action_show_all`: "Show all nights" | yes |
| rows | per night | labels `history_status_all`: "All usual", `history_status_moved` (plurals): "{k} moved", `history_status_learning`: "Learning", `history_status_short`: "Short night", `history_status_ambiguous`: "Two long sleeps", `history_status_nodata`: "No data", `history_status_few`: "Too few readings" | row -> Night detail | yes |

### 7.3 Onboarding

| Id | Trigger | Title / body (en) | Action -> destination | ru |
|---|---|---|---|---|
| `O-WELCOME` | step 1 | `onb_welcome_title`: "NightBaseline" / `onb_welcome_body`: "Notices when your night measurements move outside your usual range." Points `onb_point_daily`: "Reads once a day, after you wake up", `onb_point_own`: "Compares each night with your own recent nights", `onb_point_local`: "Everything stays on this phone. No internet access." + `disclaimer_full`. Step indicator on every step: `onb_step`: "Step {n} of 6" | `onb_start`: "Get started" -> step 2 | yes |
| `O-HOW` | step 2 | `onb_how_title`: "How it works" / `onb_how_body`: "The first 7 nights teach NightBaseline your usual range for each measurement. After that, you get a note when two or more move outside it on the same night." | `action_next`: "Next" | yes |
| `O-SOURCE` | step 3, sources found | `onb_source_title`: "Where your nights come from" / `onb_source_body`: "Any app that writes sleep and heart rate to Health Connect works. Pick the one your watch or ring uses." Row summary `source_last_sync`: "Last sleep: {date}" | `onb_use_source`: "Use {source}" -> step 4 | yes |
| `O-NOSOURCE` | no app wrote sleep data in the last 30 days (only detectable after permissions; before that the list shows apps that have Health Connect access per the HC client, or is empty) | `onb_nosource_title`: "No sleep data in Health Connect yet" / `onb_nosource_body`: "Let your watch or ring app share sleep and heart rate with Health Connect, then check again." | `action_check_again` [`action_open_hc`: "Open Health Connect"] | yes |
| `O-HC-UPDATE` / `O-HC-NONE` | as `E-HC-UPDATE` / `E-HC-NONE` | same copy as section 2 | same | yes |
| `O-PERM` | step 4 | `onb_perm_title`: "Allow reading in Health Connect" / `onb_perm_body`: "NightBaseline only reads. It never writes, changes or shares your data." Lines per type `onb_perm_sleep`: "Sleep: when you slept and for how long", `onb_perm_hr`: "Heart rate during sleep", `onb_perm_rr`: "Respiratory rate", `onb_perm_spo2`: "Blood oxygen (SpO2)", `onb_perm_hrv`: "Heart rate variability, if your source records it", `onb_perm_skin`: "Skin temperature, if your source records it", `onb_perm_background`: "In the background, so the morning check runs without opening the app", `onb_perm_history`: "Past data, so up to 60 earlier nights can be used right away (optional)" | `onb_perm_allow`: "Allow in Health Connect" -> HC permission request | yes |
| `O-PERM-DENIED` | sleep or heart rate not granted after the request | `onb_perm_denied_title`: "Sleep and heart rate are needed" / `onb_perm_denied_body`: "Without them there is nothing to compare. Other measurements are optional." | `action_try_again`: "Try again" (second denial: Health Connect stops showing the dialog -> `action_open_hc` opens HC app permissions) | yes |
| `O-PERM-PARTIAL` | core granted, some optional not | inline note `onb_perm_partial`: "Some measurements are off. You can turn them on later in Settings." | continues | yes |
| `O-NOTIF` | step 5 | `onb_notif_title`: "A calm note in the morning" / `onb_notif_body`: "Only when two or more measurements move outside your usual range on the same night." | `onb_notif_allow`: "Allow notifications" -> system prompt (API 33+) [`action_not_now`: "Not now"] | yes |
| `O-READY-HISTORY` | step 6, past nights found and >= 7 valid | `onb_ready_history_title` (plurals): "Found {n} past nights" / `onb_ready_history_body`: "Your usual range is ready. Last night is already compared." | `action_done`: "Done" -> Home | yes |
| `O-READY-LEARN` | step 6, < 7 valid past nights | `onb_ready_learn_title`: "Learning starts tonight" / `onb_ready_learn_body` (plurals on {n}): "{n} of 7 nights are already there." | `action_done` -> Home (learning) | yes |

### 7.4 Settings

| Id | Trigger | Presentation and copy | ru |
|---|---|---|---|
| source row | always | `settings_source`: "Source", value `{source}`; summary `settings_last_read`: "Last read {time}" or `settings_never_read`: "Not read yet" | yes |
| `ST-NOTIF-BLOCKED` | system permission denied or channel blocked | switch disabled; summary `settings_notif_blocked`: "Turned off in system settings"; row tap -> system notification settings | yes |
| `ST-SENS-LEARNING` | learning period | sensitivity preview hidden; `settings_sens_preview_learning`: "A preview appears after the learning period." | yes |
| sensitivity presets | always | `sens_fewer`: "Fewer notes" / `sens_fewer_desc`: "Only larger changes count, and three measurements must move."; `sens_balanced`: "Balanced" / `sens_balanced_desc`: "Recommended. Two measurements must move."; `sens_more`: "More notes" / `sens_more_desc`: "Smaller changes count too. Two measurements must move."; preview `sens_preview` (plurals): "With this setting, {k} of your last {n} nights would have sent a note." | yes |
| `ST-SOURCE-CHANGE` | person picks another source | `GlassDialog`: `source_change_title`: "Switch to {source}?" / `source_change_body`: "Devices measure a little differently, so NightBaseline learns your usual range again from {source}'s nights. Your history stays." Buttons `action_switch`: "Switch", `action_cancel`: "Cancel". (Proposal: ranges restart per source; confirm in the algorithm ADR.) | yes |
| appearance | always | `settings_theme`: "Theme" (`theme_system`: "System", `theme_light`: "Light", `theme_dark`: "Dark"); `settings_glass`: "Glass effects" (`glass_auto`: "Automatic", `glass_off`: "Off"); summary when auto resolved to off: `glass_off_battery`: "Off now: battery saver is on", `glass_off_transparency`: "Off now: reduce transparency is on", `glass_off_animations`: "Off now: animations are off", `glass_off_unsupported`: "Not available on this Android version", `glass_off_device`: "Off on this device to keep scrolling smooth" | yes |

## 8. Range explainer sheet (copy)

| key | en | ru |
|---|---|---|
| `explainer_title` | How your usual range works | yes |
| `explainer_range` | For each measurement, NightBaseline takes your last 14 to 28 nights and finds the middle value. Your usual range is that middle value plus or minus how much your nights normally vary. Unusual single nights barely move it. | yes |
| `explainer_learning` | It needs 7 nights to start. Nights shorter than 3 hours and nights without readings are left out. | yes |
| `explainer_outside` | A night counts as outside when it is clearly away from your middle value, both compared with your normal variation and by a minimum amount, so tiny changes don't count. | yes |
| `explainer_meaning_title` | What "outside your usual range" means | yes |
| `explainer_meaning` | It means a measurement was different from your own recent nights. It isn't a diagnosis. One measurement often moves on its own; when two or more move on the same night, NightBaseline sends a note. Late meals, alcohol, travel, hard training, a cold, new medication or how the device sits can all play a part. | yes |
| `explainer_care` | If you feel unwell or are worried, talk to a health professional. | yes |
| `disclaimer_short` | Not a medical device. | yes |
| `disclaimer_full` | NightBaseline is not a medical device. It doesn't diagnose, treat or prevent any condition and can't tell you whether you are ill. It only compares your night measurements with your own recent nights. | yes |
| `footer_about_link` | About | yes |

## 9. Accessibility strings (TalkBack)

| key | en pattern | ru |
|---|---|---|
| `a11y_tile` | "{metric}: {value}. {status label}. Usual range {low} to {high}." | yes |
| `a11y_tile_learning` | "{metric}: {value}. Learning your usual range, {n} of 7 nights." | yes |
| `a11y_tile_missing` | "{metric}: {status label}. {detail sentence}" | yes |
| `a11y_summary` | "{night label}. {title}. {body}" | yes |
| `a11y_history_day` | "{date}: {night status label}" | yes |
| `a11y_learning_progress` | "{n} of 7 nights collected" | yes |
| `a11y_prev_night` / `a11y_next_night` | "Previous night" / "Next night" | yes |
| `a11y_onb_step` | "Step {n} of 6" (same text as the visible `onb_step`) | yes |
| `a11y_chart_point` | "{date}: {value}, {status label}" | yes |
| `a11y_history_strip` | "Last 7 nights: {list of night status labels}. Opens History." | yes |
| `a11y_period` | "{n} nights" (plurals) | yes |
| `a11y_back` / `a11y_close` / `a11y_dismiss` | "Back" / "Close" / "Dismiss" | yes |
| `a11y_range_info` | "How the range works" | yes |
| `a11y_prev_month` / `a11y_next_month` | "Previous month" / "Next month" | yes |
| `nav_home` / `nav_history` / `nav_settings` | "Last night" / "History" / "Settings" | yes |

## 10. Other screen copy (labels, headings, settings)

Every visible string named in `screens.md` that is not a state above. Same rules as section 0.

| key | en | ru |
|---|---|---|
| `action_more` | More | yes |
| `action_see_all` | See all | yes |
| `action_learn_more` | Learn more | yes |
| `action_range_info` | How the range works | yes |
| `action_reset` | Reset | yes |
| `home_strip_title` | Last 7 nights | yes |
| `home_strip_title_14` | Last 14 nights | yes |
| `home_already_visible` | Already visible | yes |
| `home_expect_title` | What to expect | yes |
| `home_expect_body` | After 7 nights each measurement gets its usual range. The range keeps adjusting over the next weeks and uses up to 28 nights. Notes start after the learning period. | yes |
| `period_nights` (plurals) | {n} nights | yes |
| `stats_inside` (plurals) | Inside your usual range: {k} of {n} nights | yes |
| `stats_range` | Usual range: {low}–{high} | yes |
| `stats_median` | Middle value: {value} | yes |
| `detail_computed_title` | How your usual range is computed | yes |
| `detail_source_line` | Measured by {source}. Last sync {time}. | yes |
| `history_title` | History | yes |
| `history_view_list` / `history_view_calendar` | List / Calendar | yes |
| `history_filter_all` / `history_filter_moved` / `history_filter_gaps` | All / Moved / Learning and gaps | yes |
| `history_legend_title` | What the marks mean | yes |
| `source_picker_title` | Choose a source | yes |
| `settings_title` | Settings | yes |
| `settings_group_source` | Data source | yes |
| `settings_hc_permissions` | Health Connect permissions | yes |
| `settings_last_read_title` | Last read | yes |
| `settings_setup_again` | Set up again | yes |
| `settings_group_notifications` | Notifications | yes |
| `settings_notif_results` | Night results | yes |
| `settings_notif_problems` | Problems reading data | yes |
| `settings_group_sensitivity` | Sensitivity | yes |
| `settings_group_appearance` | Appearance | yes |
| `settings_motion_note` | Motion follows the system "Remove animations" setting. | yes |
| `settings_group_about` | About | yes |
| `settings_about` | About NightBaseline | yes |
| `settings_group_debug` / `settings_catalog` / `settings_synthetic` | Debug / Component catalog / Show synthetic data (debug builds only) | yes |
| `sens_advanced` | Advanced | yes |
| `sens_window` | Range window | yes |
| `sens_window_value` (plurals) | {n} nights | yes |
| `sens_use_metric` | Use for comparison | yes |
| `sens_reset` | Reset to default | yes |
| `sens_reset_title` | Reset sensitivity? | yes |
| `sens_reset_body` | Sensitivity goes back to Balanced and the range window to 28 nights. | yes |
| `about_version` | Version {version} | yes |
| `about_local` | Your data stays on this phone. NightBaseline has no internet access. | yes |
| `about_any_source` | Works with any app that writes to Health Connect. | yes |
| `about_licenses` | Open-source licenses | yes |
| `about_source_code` | Source code: {url} | yes |
| `widget_waiting` / `widget_update_needed` / `widget_choose_source` | Waiting for sync / Update needed / Choose source | yes |
