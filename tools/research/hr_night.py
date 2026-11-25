#!/usr/bin/env python3
"""OHealth heart rate around sleep: sampling inside/outside OHealth sleep sessions, coverage and gaps of main nights,
naps, candidate 'sleeping HR' definitions (spread, correlation, robustness), OHealth RestingHeartRateRecord vs the
candidates and vs daytime minimum, Sleep as Android resting HR records.

Prints aggregates only (counts, shares, intervals, spreads, differences, correlations). Per-night values are written
only to the git-ignored scratch file <data>/research/hr_nights.csv.

  python3 -I tools/research/hr_night.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np
import pandas as pd

OH = "com.heytap.health.international"
SAA = "com.urbandroid.sleep"
MAIN_MIN_H = 3.0
data = sys.argv[1]
ds = os.path.join(data, "dataset")
rng = np.random.default_rng(1)


def load(name):
    with open(os.path.join(ds, name + ".jsonl")) as f:
        return [json.loads(l) for l in f]


def q(a, ps=(0, 5, 25, 50, 75, 95, 100), fmt=".3g"):
    a = np.asarray(a, float)
    a = a[~np.isnan(a)]
    return (" ".join(f"p{p}={np.percentile(a, p):{fmt}}" for p in ps) + f" (n={len(a)})") if len(a) else "empty"


def rsd(a):
    a = np.asarray(a, float)
    a = a[~np.isnan(a)]
    return 1.4826 * np.median(np.abs(a - np.median(a)))


def sp(a, b):
    """Spearman correlation without scipy."""
    a, b = pd.Series(a, dtype=float), pd.Series(b, dtype=float)
    m = a.notna() & b.notna()
    return a[m].rank().corr(b[m].rank())


def local(ms, off):
    return dt.datetime.utcfromtimestamp(ms / 1000 + off)


# ---------------------------------------------------------------- HR streams
hr_all = load("heart_rate")
hr = [r for r in hr_all if r["origin"] == OH]
single = [r for r in hr if len(r["samples"]) == 1]
dense = [r for r in hr if len(r["samples"]) > 1]
OFF = collections.Counter(r["startZoneOffsetS"] for r in hr).most_common(1)[0][0]
bg = pd.Series({s[0]: s[1] for r in single for s in r["samples"]}).sort_index()  # time ms -> bpm
dn = pd.Series({s[0]: s[1] for r in dense for s in r["samples"]}).sort_index()
print(f"== streams: single-sample records {len(single)}, multi-sample records {len(dense)} "
      f"({sum(len(r['samples']) for r in dense)} samples)")
dup = len(set(bg.index) & set(dn.index))
print("background sample times also present in multi-sample records:", dup)

ex = [r for r in load("exercise") if r["origin"] == OH]
sl_all = load("sleep")
sl = sorted([r for r in sl_all if r["origin"] == OH], key=lambda r: r["start"])


def overlaps(r, sessions):
    return any(s["start"] < r["end"] and r["start"] < s["end"] for s in sessions)


print("multi-sample records overlapping an OHealth exercise session:", sum(overlaps(r, ex) for r in dense),
      "/", len(dense), "; overlapping an OHealth sleep session:", sum(overlaps(r, sl) for r in dense))
print("multi-sample record samples/record:", q([len(r["samples"]) for r in dense]))
print("multi-sample record intra-interval s:", q(np.concatenate([np.diff([s[0] for s in r["samples"]]) / 1000 for r in dense])))
print("OHealth exercise sessions:", len(ex), "with a multi-sample HR record:", sum(overlaps(e, dense) for e in ex))
print("multi-sample lastModified - end h:", q([(r["lastModifiedMs"] - r["end"]) / 3600e3 for r in dense]))
print("single-sample lastModified - time h:", q([(r["lastModifiedMs"] - r["start"]) / 3600e3 for r in single]))

# ---------------------------------------------------------------- sessions
print("\n== OHealth sleep sessions:", len(sl))
gaps_between = np.array([(b["start"] - a["end"]) / 60e3 for a, b in zip(sl, sl[1:])])
print("gap to next session min:", q(gaps_between), "; overlapping pairs:", int((gaps_between < 0).sum()),
      "; gaps < 60 min:", int(((gaps_between >= 0) & (gaps_between < 60)).sum()))
for r in sl:
    r["dur_h"] = (r["end"] - r["start"]) / 3600e3
    r["wake_date"] = (local(r["start"], r["startZoneOffsetS"]) + dt.timedelta(hours=12)).date()
    r["start_h"] = local(r["start"], r["startZoneOffsetS"]).hour
by_key = collections.defaultdict(list)
for r in sl:
    by_key[r["wake_date"]].append(r)
main, other = [], []
for k, rs in by_key.items():
    long_ = [r for r in rs if r["dur_h"] >= MAIN_MIN_H]
    m = max(long_, key=lambda r: r["dur_h"]) if long_ else None
    if m:
        main.append(m)
    other += [r for r in rs if r is not m]
main.sort(key=lambda r: r["start"])
print(f"wake-dates with sessions {len(by_key)}, main nights (>= {MAIN_MIN_H} h, longest) {len(main)}, other sessions {len(other)}")
print("sessions per wake-date:", collections.Counter(len(v) for v in by_key.values()))
print("main night duration h:", q([r["dur_h"] for r in main]))
print("main night start local hour:", sorted(collections.Counter(r["start_h"] for r in main).items()))
print("other session duration h:", q([r["dur_h"] for r in other]))
print("other session start local hour:", sorted(collections.Counter(r["start_h"] for r in other).items()))
oth_gap = []
for o in other:
    g = min([abs(o["start"] - m["end"]) for m in main] + [abs(m["start"] - o["end"]) for m in main]) / 60e3
    oth_gap.append(g)
print("other session: min distance to a main night (min):", q(oth_gap))
days_span = (sl[-1]["end"] - sl[0]["start"]) / 86400e3
print("days spanned by OHealth sessions:", round(days_span, 1))

bg_t = bg.index.values
bg_v = bg.values.astype(float)


def in_window(a, b, t=bg_t):
    i, j = np.searchsorted(t, a), np.searchsorted(t, b, side="right")
    return i, j


# ---------------------------------------------------------------- sampling inside/outside
print("\n== sampling interval of the background (single-sample) stream")
in_mask = np.zeros(len(bg_t), bool)
for r in sl:
    i, j = in_window(r["start"], r["end"])
    in_mask[i:j] = True
d_in, d_out = [], []
for r in main:
    i, j = in_window(r["start"], r["end"])
    d_in.append(np.diff(bg_t[i:j]) / 1000)
d_in = np.concatenate(d_in)
dall = np.diff(bg_t) / 1000
both_out = ~in_mask[:-1] & ~in_mask[1:]
d_out = dall[both_out]


def hist(d, label):
    bins = [0, 59, 61, 100, 119, 121, 179, 181, 299, 301, 600, 1800, 1e12]
    h, _ = np.histogram(d, bins)
    print(f"  {label}: median {np.median(d):.0f}s, " + ", ".join(
        f"[{a:g},{b:g}) {100 * c / len(d):.1f}%" for a, b, c in zip(bins[:-1], bins[1:], h) if c))


hist(d_in, "inside main nights")
hist(d_out, "outside all sessions")
print("  outside: share of time in gaps >10 min:", round(d_out[d_out > 600].sum() / d_out.sum() * 100, 1), "%",
      "; gaps >1h:", int((d_out > 3600).sum()), "; total outside gap hours >1h:", round(d_out[d_out > 3600].sum() / 3600, 1))
loc_sec = pd.to_datetime(bg_t + OFF * 1000, unit="ms")
print("  sample second-of-minute top:", collections.Counter(loc_sec.second).most_common(4))
# interval pattern outside sessions by local hour
dfo = pd.DataFrame({"h": loc_sec[:-1].hour[both_out], "d": d_out})
print("  outside median interval by local hour:", dfo.groupby("h")["d"].median().round(0).astype(int).to_dict())

# ---------------------------------------------------------------- coverage, gaps
print("\n== coverage of main nights (10-min bins with >=1 background sample)")
cov, maxgap, n10, n30, lead, tail, nsamp = [], [], [], [], [], [], []
for r in main:
    i, j = in_window(r["start"], r["end"])
    t = bg_t[i:j]
    nb = int(np.ceil((r["end"] - r["start"]) / 600e3))
    b = np.unique(((t - r["start"]) // 600e3).astype(int))
    cov.append(len(b) / nb * 100)
    edges = np.concatenate([[r["start"]], t, [r["end"]]])
    g = np.diff(edges) / 60e3
    maxgap.append(g.max())
    n10.append(int((np.diff(t) > 600e3).sum()))
    n30.append(int((np.diff(t) > 1800e3).sum()))
    lead.append(g[0])
    tail.append(g[-1])
    nsamp.append(len(t) / ((r["end"] - r["start"]) / 3600e3))
print("coverage %:", q(cov), "; nights with coverage <90%:", sum(c < 90 for c in cov))
print("samples per hour:", q(nsamp))
print("max gap per night min:", q(maxgap))
print("internal gaps >10 min per night:", collections.Counter(n10), "; >30 min:", collections.Counter(n30))
print("session start -> first sample min:", q(lead), "; last sample -> session end min:", q(tail))
dense_in_sleep = sum(((dn.index.values >= r["start"]) & (dn.index.values <= r["end"])).sum() for r in sl)
print("multi-sample (1 Hz) samples inside OHealth sleep sessions:", int(dense_in_sleep))

print("\n== other (short) sessions")
for r in other:
    i, j = in_window(r["start"], r["end"])
    r["_n"] = j - i
    r["_cov"] = len(np.unique(((bg_t[i:j] - r["start"]) // 600e3).astype(int))) / max(1, np.ceil((r["end"] - r["start"]) / 600e3)) * 100
print("samples per hour:", q([r["_n"] / r["dur_h"] for r in other]))
print("coverage %:", q([r["_cov"] for r in other]))
d_oth = np.concatenate([np.diff(bg_t[slice(*in_window(r["start"], r["end"]))]) / 1000 for r in other])
hist(d_oth, "inside other sessions")
# HR level in short sessions relative to the closest main night / to surrounding wake (shape only)
rel_main, rel_wake = [], []
for r in other:
    i, j = in_window(r["start"], r["end"])
    if j - i < 3:
        continue
    m = min(main, key=lambda m: abs(m["start"] - r["start"]))
    a, b = in_window(m["start"], m["end"])
    rel_main.append(np.median(bg_v[i:j]) - np.median(bg_v[a:b]))
    a, b = in_window(r["start"] - 2 * 3600e3, r["start"])
    wk = bg_v[a:b][~in_mask[a:b]]
    if len(wk) > 10:
        rel_wake.append(np.median(bg_v[i:j]) - np.median(wk))
print("short session median HR minus nearest main night median (bpm):", q(rel_main))
print("short session median HR minus median of the 2 h before it, awake (bpm):", q(rel_wake))

# ---------------------------------------------------------------- candidates
print("\n== candidate sleeping HR definitions (main nights)")


def candidates(t, v, start, end):
    out = {}
    if len(v) < 20:
        return None
    out["median"] = np.median(v)
    out["mean"] = np.mean(v)
    out["p10"] = np.percentile(v, 10)
    s = pd.Series(v, index=pd.to_datetime(t, unit="ms"))
    roll = s.rolling("30min", min_periods=10).mean()
    # only windows that are fully inside the session (window end >= start+30min)
    roll = roll[s.index >= pd.to_datetime(start + 1800e3, unit="ms")]
    out["min30"] = roll.min() if roll.notna().any() else np.nan
    k = t >= start + 1800e3
    out["median_x30"] = np.median(v[k]) if k.sum() >= 20 else np.nan
    out["p5"] = np.percentile(v, 5)
    r60 = s.rolling("60min", min_periods=20).median()
    r60 = r60[s.index >= pd.to_datetime(start + 3600e3, unit="ms")]
    out["min60med"] = r60.min() if r60.notna().any() else np.nan
    return out


rows = []
for r in main:
    i, j = in_window(r["start"], r["end"])
    c = candidates(bg_t[i:j], bg_v[i:j], r["start"], r["end"])
    if c:
        c["wake_date"] = r["wake_date"]
        c["dur_h"] = r["dur_h"]
        rows.append(c)
df = pd.DataFrame(rows).set_index("wake_date").sort_index()
C = ["median", "mean", "p10", "p5", "min30", "min60med", "median_x30"]
ref = df["median"].median()
print("nights:", len(df))
for c in C:
    x = df[c]
    diff = x - df["median"]
    dd = x.diff().dropna()
    print(f"  {c:11s} night-to-night robustSD={rsd(x):.2f} SD={x.std():.2f} IQR={x.quantile(.75) - x.quantile(.25):.2f} "
          f"| minus median: med={np.median(diff):+.2f} rsd={rsd(diff):.2f} | consecutive-night diff rsd={rsd(dd):.2f} "
          f"| lag1 autocorr={x.autocorr(1):.2f}")
print("Spearman correlations:")
print(df[C].corr(method="spearman").round(2).to_string())
print("Pearson correlations:")
print(df[C].corr().round(2).to_string())
print("corr of candidates with session duration (Spearman):", {c: round(sp(df[c], df["dur_h"]), 2) for c in C})

# robustness: random 30% dropout, session start +/- 30 min, end -60 min, thinning to 5-min sampling
print("robustness (median |change| vs full-night value, bpm; ratio to night-to-night robustSD):")
pert = {c: collections.defaultdict(list) for c in C}
for r in main:
    i, j = in_window(r["start"], r["end"])
    t, v = bg_t[i:j], bg_v[i:j]
    base = candidates(t, v, r["start"], r["end"])
    if not base:
        continue
    variants = {}
    keep = rng.random(len(t)) > 0.3
    variants["drop30%"] = candidates(t[keep], v[keep], r["start"], r["end"])
    k = t >= r["start"] + 1800e3
    variants["start+30m"] = candidates(t[k], v[k], r["start"] + 1800e3, r["end"])
    a, b = in_window(r["start"] - 1800e3, r["end"])
    variants["start-30m"] = candidates(bg_t[a:b], bg_v[a:b], r["start"] - 1800e3, r["end"])
    k = t <= r["end"] - 3600e3
    variants["end-60m"] = candidates(t[k], v[k], r["start"], r["end"] - 3600e3)
    a, b = in_window(r["start"], r["end"] + 1800e3)
    variants["end+30m"] = candidates(bg_t[a:b], bg_v[a:b], r["start"], r["end"] + 1800e3)
    k = np.concatenate([[True], np.diff((t // 300e3).astype(int)) > 0])
    variants["thin5min"] = candidates(t[k], v[k], r["start"], r["end"])
    for name, vv in variants.items():
        if vv:
            for c in C:
                pert[c][name].append(abs(vv[c] - base[c]))
names = list(next(iter(pert.values())).keys())
print("  " + " " * 11 + "  ".join(f"{n:>10s}" for n in names))
for c in C:
    s = rsd(df[c])
    print(f"  {c:11s}" + "  ".join(f"{np.nanmedian(pert[c][n]):5.2f}/{np.nanmedian(pert[c][n]) / s:4.2f}" for n in names))

# ---------------------------------------------------------------- resting HR
print("\n== OHealth RestingHeartRateRecord")
rhr_all = load("resting_heart_rate")
rh = sorted([r for r in rhr_all if r["origin"] == OH], key=lambda r: r["time"])
lt = [local(r["time"], r["zoneOffsetS"]) for r in rh]
print("records:", len(rh), "; local time-of-day stamps:", collections.Counter(x.strftime("%H:%M:%S.%f") for x in lt))
print("zone offsets:", collections.Counter(r["zoneOffsetS"] for r in rh), "; distinct local dates:", len(set(x.date() for x in lt)))
dates = [x.date() for x in lt]
alld = pd.date_range(min(dates), max(dates)).date
print("calendar days in range:", len(alld), "missing:", len(set(alld) - set(dates)))
print("lastModified - stamp h:", q([(r["lastModifiedMs"] - r["time"]) / 3600e3 for r in rh]))
lm_local_h = [local(r["lastModifiedMs"], r["zoneOffsetS"]).hour for r in rh]
print("lastModified local hour:", sorted(collections.Counter(lm_local_h).items()))
print("distinct lastModified:", len(set(r["lastModifiedMs"] for r in rh)))
R = pd.Series({d: r["value"] for d, r in zip(dates, rh)})
print("RHR day-to-day robustSD:", round(rsd(R), 2), " consecutive-day diff rsd:", round(rsd(R.diff().dropna()), 2),
      " lag1 autocorr:", round(R.autocorr(1), 2), " share integer:", np.mean([float(v).is_integer() for v in R]))

# per calendar day stats of the background stream
day_rows = {}
loc_dates = loc_sec.date
bgdf = pd.DataFrame({"t": bg_t, "v": bg_v, "date": loc_dates, "sleep": in_mask})
for d, g in bgdf.groupby("date"):
    s = pd.Series(g["v"].values, index=pd.to_datetime(g["t"].values, unit="ms"))
    w = g[~g["sleep"]]
    sw = pd.Series(w["v"].values, index=pd.to_datetime(w["t"].values, unit="ms"))
    day_rows[d] = {
        "day_min": g["v"].min(), "day_p5": g["v"].quantile(.05), "day_median": g["v"].median(),
        "day_min30": s.rolling("30min", min_periods=10).mean().min(),
        "wake_min": w["v"].min() if len(w) else np.nan,
        "wake_p5": w["v"].quantile(.05) if len(w) else np.nan,
        "wake_min30": sw.rolling("30min", min_periods=10).mean().min() if len(w) else np.nan,
        "wake_min5": sw.rolling("5min", min_periods=2).mean().min() if len(w) else np.nan,
        "n": len(g), "n_wake": len(w)}
D = pd.DataFrame(day_rows).T
D.index = pd.Index(D.index)
full = D["n"] >= 500  # roughly a full day at 2-min sampling (720)
print("days with >=500 samples:", int(full.sum()), "/", len(D))

print("RHR (stamp date D) vs night candidates; night keyed by wake date; lag k = night waking on D+k")
for c in ["median", "p10", "min30", "median_x30"]:
    for k in (-1, 0, 1, 2):
        x = pd.Series({d: df[c].get(d + dt.timedelta(days=k), np.nan) for d in R.index})
        m = x.notna()
        diff = (R - x)[m]
        print(f"  {c:11s} k={k:+d} n={m.sum():2d} RHR-cand med={np.median(diff):+.2f} rsd={rsd(diff):.2f} "
              f"spearman={sp(R[m], x[m]):.2f} pearson={R[m].astype(float).corr(x[m]):.2f}")
print("RHR (stamp date D) vs daytime/day stats of calendar day D+k (full days only)")
for c in ["day_min", "day_p5", "day_min30", "wake_min", "wake_p5", "wake_min5", "wake_min30", "day_median"]:
    for k in (-1, 0, 1):
        x = pd.Series({d: (D.loc[d + dt.timedelta(days=k), c] if (d + dt.timedelta(days=k)) in D.index and
                           full.get(d + dt.timedelta(days=k), False) else np.nan) for d in R.index}, dtype=float)
        m = x.notna()
        diff = (R - x)[m]
        exact = int((diff == 0).sum())
        print(f"  {c:11s} k={k:+d} n={m.sum():2d} RHR-x med={np.median(diff):+.2f} rsd={rsd(diff):.2f} exact={exact} "
              f"spearman={sp(R[m], x[m]):.2f}")
# percentile rank of RHR within day D samples (awake and all)
pr_all, pr_wake, pr_sleep = [], [], []
for d, v in R.items():
    g = bgdf[bgdf["date"] == d]
    if len(g) < 500:
        continue
    pr_all.append((g["v"] < v).mean() * 100 + (g["v"] == v).mean() * 50)
    w = g[~g["sleep"]]["v"]
    pr_wake.append((w < v).mean() * 100 + (w == v).mean() * 50)
print("percentile rank of RHR within the same calendar day's samples: all", q(pr_all), "\n   awake only", q(pr_wake))
# percentile rank within the window [D 00:00 - 24h, D 00:00) i.e. the previous day
pr_prev = []
for d, v in R.items():
    g = bgdf[bgdf["date"] == d - dt.timedelta(days=1)]
    if len(g) >= 500:
        pr_prev.append((g["v"] < v).mean() * 100 + (g["v"] == v).mean() * 50)
print("percentile rank within previous calendar day:", q(pr_prev))
out = df.join(R.rename("rhr_same_date"), how="outer").join(D, how="outer")
os.makedirs(os.path.join(data, "research"), exist_ok=True)
out.to_csv(os.path.join(data, "research", "hr_nights.csv"))

# ---------------------------------------------------------------- Sleep as Android resting HR
print("\n== Sleep as Android RestingHeartRateRecord")
srh = [r for r in rhr_all if r["origin"] == SAA]
shr = [r for r in hr_all if r["origin"] == SAA]
print("records:", len(srh), "; SaA HeartRateRecords:", len(shr), "with samples:", sum(len(r["samples"]) for r in shr))
shr_map = {s[0]: s[1] for r in shr for s in r["samples"]}
same_t = sum(1 for r in srh if r["time"] in shr_map)
same_tv = sum(1 for r in srh if shr_map.get(r["time"]) == r["value"])
print("RHR records whose time matches a SaA HR sample:", same_t, "; time and value match:", same_tv)
st = np.array(sorted(r["time"] for r in srh))
print("distinct times:", len(np.unique(st)), "; interval s:", q(np.diff(np.unique(st)) / 1000))
ssl = [r for r in sl_all if r["origin"] == SAA]
ins = 0
for r in ssl:
    ins += int(((st >= r["start"]) & (st <= r["end"])).sum())
print("inside a SaA sleep session:", ins, "/", len(st), "; SaA sessions:", len(ssl))
print("per SaA session RHR count:", q([int(((st >= r['start']) & (st <= r['end'])).sum()) for r in ssl]))
print("lastModified batches:", len(set(r["lastModifiedMs"] for r in srh)), "; recordingMethod:", collections.Counter(r["recordingMethod"] for r in srh))
sd = sorted(set(local(r["time"], r["zoneOffsetS"]).date() for r in srh))
print("local days with SaA RHR:", len(sd), "; days spanned:", (sd[-1] - sd[0]).days + 1)
after = st[st >= bg_t[0]]
print("SaA RHR records during the OHealth era:", len(after))
if len(after):
    vals = {r["time"]: r["value"] for r in srh}
    diffs = []
    for t in after:
        i = np.searchsorted(bg_t, t)
        cand = [k for k in (i - 1, i) if 0 <= k < len(bg_t) and abs(bg_t[k] - t) <= 90e3]
        if cand:
            k = min(cand, key=lambda k: abs(bg_t[k] - t))
            diffs.append(vals[t] - bg_v[k])
    print("SaA RHR minus nearest OHealth sample (<=90 s):", q(diffs))
print("other RHR origins:", collections.Counter(r["origin"] for r in rhr_all if r["origin"] not in (OH, SAA)))
for o in ("org.healthmerge.hcimporter",):
    rr = [r for r in rhr_all if r["origin"] == o]
    print(f"  {o}: local stamp times:", collections.Counter(local(r['time'], r['zoneOffsetS']).strftime('%H:%M') for r in rr).most_common(4),
          "; distinct dates:", len(set(local(r['time'], r['zoneOffsetS']).date() for r in rr)))
