# OHealth: what is stored in the cloud and how to officially obtain a copy of your data

Status: reconnaissance from public sources only (date: 2026-11-25). The private OHealth cloud API was not
analyzed or used (rule 8), no apps were decompiled, and no requests were sent on the owner's behalf.

Tags: **[from official documentation <url>]**, **[verified in code/docs]** (our repositories),
**[from reviews]** (user reports), **[hypothesis]**.

## 1. Conclusion

1. **The watch and OHealth collect more than is visible in Health Connect.** The Watch X3 specifications page
   lists sleep stages (deep / light / REM / awake), sleep score, sleep respiratory rate, SpO2 during sleep and
   around the clock, "sleep benchmark heart rate", resting heart rate, wrist temperature, a "Health Insights"
   section in OHealth, and "Support Google Health Connect service"
   [from official documentation https://www.oppo.com/en/accessories/watch-x3/specs/]. The Watch X3 press release
   mentions an HRV-based condition assessment [from official documentation https://www.oppo.com/en/newsroom/press/oppo-launches-watch-x3/].
2. **According to public data, OHealth writes little to Health Connect:** sleep sessions without stages (start and
   end only), heart rate roughly every 2 minutes, and steps [verified in code/docs: ../PulseStages/README.md, lines 55–57;
   tools/train/pulsetrain/ohealth.py `GRID_MS`]. A third-party aggregator lists the same three types: `SleepSessionRecord`,
   `HeartRateRecord`, `StepsRecord` [from reviews / third-party service https://sahha.ai/integrations/ohealth/].
   One Reddit user writes that OHealth requests write access for sleep, heart rate and "respiratory" data
   [from reviews https://libreddit.winscloud.net/r/WearOS/comments/1dhnvfm/oneplus_watch_2_step_count_issues_solved/l8zmtu2/?context=3].
   Whether OHealth writes nightly SpO2 and respiratory rate is not confirmed [verified in code/docs: PulseStages README.md, line 295,
   "Not verified yet"]. OPPO does not publish an official list of the types OHealth writes to Health Connect.
   Sync to Health Connect may not run in the background and may only trigger when OHealth is opened
   [from reviews https://support.heiaheia.com/connecting-ohealth-with-heiaheia; consistent with the observation
   in PulseStages README.md, lines 286–293].
3. **What exactly is stored in the cloud.** No standalone OHealth privacy policy could be found publicly. For the
   related app OnePlus Health (same HeyTap codebase), the policy states that with "Auto sync health data" enabled,
   health and workout data are automatically uploaded to the cloud and restored on account sign-in; it names
   heart rate, sleep (time, duration, awakenings, quality), SpO2 during sleep, "psychological load" (stress), VO2max,
   and GPS tracks [from official documentation https://www.oneplus.in/oneplus-health/privacy-policy].
   HRV, skin temperature and respiratory rate are not explicitly named in that policy. That OHealth stores in the
   cloud at least what it shows in its own history (sleep stages, SpO2, respiratory rate, wrist temperature) is a
   **[hypothesis]**: history is restored after reinstall on account sign-in, but the format and granularity (raw
   heart-rate samples or aggregates) are not publicly described.
4. **Official export.** No "export data" button in OHealth was found in public sources (neither in descriptions
   nor in policies) [hypothesis: it does not exist; only the owner can check in the app]. The official route is a
   right of access / portability request (GDPR Art. 15 and 20) to the HeyTap account operator:
   - HeyTap European policy: the data controller is HeyTap PTE LTD; a copy can be obtained "in a structured,
     commonly used and machine-readable format"; channels: the portal https://id.heytap.com/static/userdata_index.html,
     the Data Subject Rights Platform form https://brand.heytap.com/en/privacy-feedback.html, email privacy@heytap.com;
     response typically within one month, extendable by two more months with notice; identity verification possible
     [from official documentation https://muc.heytap.com/document/heytap/europe/privacyPolicy/privacyPolicy_en-GB.html];
   - general OPPO policy: DSR platform https://www.oppo.com/en/privacy-feedback/, a "data management platform"
     in the account https://id.heytap.com/index.html (access, copy, deletion; being rolled out "gradually"), email
     privacy@oppo.com [from official documentation https://www.oppo.com/en/privacy/]; for Europe privacy.eu@oppo.com,
     DPO Collegium Auditores GmbH; there is no separate section for Russia/CIS
     [from official documentation https://www.oppo.com/en/privacy/contact-person/].
   - OPPO's EU Data Act notice for watches lists only sensor/technical data (barometer, accelerometer,
     battery, etc.); heart rate, sleep and SpO2 are not there [from official documentation https://www.oppo.com/en/legal/eudataact/].
     Therefore, for health data, rely on a GDPR request rather than the Data Act.
   - The forms on brand.heytap.com and id.heytap.com are script-rendered and cannot be read without signing in; which
     fields and product choices they offer is not verified [hypothesis].
5. **SDK / open platform.** No active public OHealth SDK for third-party apps was found. In 2020 a
   "HeyTap Health open platform" with partners was announced [from reviews (press) https://m.c114.com.cn/w16-1138896.html],
   and OPPO mentioned an "OPPO Health Research Kit" for researchers
   [from official documentation https://oppo.com/en/newsroom/stories/smart-health-technology-health-management-dr-zeng];
   there is no current documentation or access terms. NightBaseline does not need this: the product works only through
   Health Connect and without INTERNET (CLAUDE.md, "Product boundaries") [verified in code/docs].
6. **Health Connect itself** supports scheduled export (Android 14+, "Manage data -> Backup and restore ->
   Scheduled export", an encrypted ZIP to cloud storage, daily/weekly/monthly)
   [from official documentation https://support.google.com/android/answer/15323271]. This is a copy of what is already
   in Health Connect, not of the OHealth cloud.

**Bottom line for the project.** Nothing about the cloud export changes the architecture: the app still reads only
Health Connect. A GDPR export is useful to the owner as a one-off offline reference (to compare what OHealth shows
internally, i.e. stages, SpO2, respiratory rate, temperature, with what reaches Health Connect) and only for `.data/`.
Expectations: the response may arrive as an archive with a non-obvious structure, without raw samples, and take up to
1–3 months [hypothesis].

Note on CLAUDE.md rule numbers: rule 5 is the `.data/` storage rule, rule 8 forbids private APIs; rule 10 is
phone access with permission and rule 11 is personal data.

## 2. Owner instructions: how to request a copy of your data (official channels only)

Before starting: put all received files only in the ignored `.data/` directory (rule 5); do not post them to git,
issues or chats. The owner sends the request in their own name; the agent does not do this.

### Step 0. Check what the app itself offers (2 minutes)
1. Open OHealth -> "Me" -> "Settings" -> look for items such as "Privacy", "Data management",
   "Export data", "Download a copy of data".
2. In the same place, make sure "Cloud data sync" / "Auto sync health data" is enabled; otherwise the cloud
   may contain nothing [hypothesis: menu path by analogy with the OnePlus Health policy].
3. If an export button is found, use it and stop here. Expected result: a file at the end, or an email with a
   link to an archive.

### Step 1. HeyTap account data portal
1. In a browser, open https://id.heytap.com/static/userdata_index.html (or https://id.heytap.com/index.html ->
   the personal data management section).
2. Sign in with the same HeyTap/OPPO account used in OHealth.
3. Find an item such as "Get a copy of data" / "Data portability" / "Download your data", select Health/OHealth data
   if the product can be selected, and verify identity (code via SMS/email).
4. Result: a "request accepted" screen, then an email with a link to the archive. If the item is missing (OPPO says
   the feature is being rolled out gradually), go to step 2.

### Step 2. Data Subject Rights Platform form
1. Open https://brand.heytap.com/en/privacy-feedback.html (for HeyTap accounts and internet services) or the general
   https://www.oppo.com/en/privacy-feedback/.
2. Select country/region and the request type "access / copy of data / portability" (access / portability).
3. In the text, state: product OHealth (`com.heytap.health.international`), OPPO Watch X3 watch; request a copy of all
   health data linked to the account in a machine-readable format (CSV/JSON), including sleep sessions and stages,
   heart rate (per-sample with timestamps if possible), HRV, SpO2, respiratory rate, wrist temperature,
   for the entire retention period. Enter the account number/email only in the form itself, not in project files.
4. Result: confirmation that the request is registered; per the HeyTap policy, a response usually within one month
   (may be extended by two, with notice).

### Step 3. If the form does not work: email
- To: privacy@heytap.com (HeyTap account operator); CC: privacy@oppo.com; for EU residents also
  privacy.eu@oppo.com.
- Subject: "Data subject access and portability request (GDPR Art. 15 and 20) — OHealth".
- Body: same as step 2, item 3; send from the address linked to the account to simplify identity
  verification. Do not attach documents unless the operator specifically requests them.

### Step 4. If there is no response
- After one month, send a reminder via the same channel referencing the date of the first request.
- For EU residents, file a complaint with the local data protection supervisory authority (list:
  https://edpb.europa.eu/about-edpb/board/members_en) [from official documentation https://www.oppo.com/en/privacy/].

### Step 5. What to do with the archive
- Save it to `NightBaseline/.data/ohealth-export/` (the directory is ignored by git).
- Tell the agent only that it was received and the list of files/fields (without values); the agent will then produce
  a structure summary without raw values for `docs/research/`.

## 3. Sources

- OPPO Watch X3, specifications: https://www.oppo.com/en/accessories/watch-x3/specs/
- OPPO Watch X3, press release: https://www.oppo.com/en/newsroom/press/oppo-launches-watch-x3/
- OPPO Privacy Policy (Global): https://www.oppo.com/en/privacy/
- OPPO data protection contacts: https://www.oppo.com/en/privacy/contact-person/
- OPPO EU Data Act notice: https://www.oppo.com/en/legal/eudataact/
- HeyTap Privacy Notice (Europe): https://muc.heytap.com/document/heytap/europe/privacyPolicy/privacyPolicy_en-GB.html
- HeyTap Account SDK privacy notice: https://muc.heytap.com/document/heytap/accountPolicyUpdate/accountSDK/privacyPolicySdk_en-US.html
- OnePlus Health privacy policy (related app): https://www.oneplus.in/oneplus-health/privacy-policy
- Google Play, OHealth Data safety: https://play.google.com/store/apps/datasafety?id=com.heytap.health.international
  ("No data shared with third parties", Fitness info — optional, a way to request deletion exists)
- Health Connect, backup and export: https://support.google.com/android/answer/15323271
- HeiaHeia, connecting OHealth: https://support.heiaheia.com/connecting-ohealth-with-heiaheia
- Sahha, OHealth integration: https://sahha.ai/integrations/ohealth/
- Press on HeyTap Health open platform (2020): https://m.c114.com.cn/w16-1138896.html
- PulseStages (read-only): ../PulseStages/README.md, tools/train/pulsetrain/ohealth.py
