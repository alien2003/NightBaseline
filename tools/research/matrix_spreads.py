#!/usr/bin/env python3
"""Night-to-night spreads and baseline-parameter simulation for the metrics matrix.

Main night (OHealth): sessions of one origin merged while gap <= 90 min, keyed by local date of (midpoint + 6 h),
the cluster with the largest summed sleep, accepted if >= 3 h. Per night it computes (in memory only):
  sleep duration (summed sessions), HR p10 of background samples excluding the first 30 min,
  HR lower-half mean, SpO2 mean / trimmed mean, RR mean / trimmed mean (both excluding the first 30 min).
Prints ONLY aggregates: robust SD (MAD*1.4826), quantisation info, lag-1 autocorrelation, and for a rolling
baseline (median / robust SD with floor) the share of nights over |z| thresholds and of nights with >= 2 metrics
out. Never prints per-night values or absolute levels.

  mise exec -- python -I tools/research/matrix_spreads.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np

data = sys.argv[1]
OH = "com.heytap.health.international"
MIN, H = 60_000, 3_600_000
G = 90


def load(name):
    with open(os.path.join(data, "dataset", name + ".jsonl")) as f:
        return [json.loads(line) for line in f]


sleep = [r for r in load("sleep") if r["origin"] == OH]
cl = []
for r in sorted(sleep, key=lambda r: r["start"]):
    if cl and r["start"] - cl[-1]["end"] <= G * MIN:
        c = cl[-1]
        c["end"] = max(c["end"], r["end"])
        c["sleep"] += r["end"] - r["start"]
        c["parts"].append((r["start"], r["end"]))
    else:
        cl.append({"start": r["start"], "end": r["end"], "off": r.get("startZoneOffsetS") or 0,
                   "sleep": r["end"] - r["start"], "parts": [(r["start"], r["end"])]})
bykey = collections.defaultdict(list)
for c in cl:
    mid = (c["start"] + c["end"]) / 2 + c["off"] * 1000 + 6 * H
    bykey[dt.datetime.fromtimestamp(mid / 1000, dt.timezone.utc).date()].append(c)
nights = []
for k in sorted(bykey):
    best = max(bykey[k], key=lambda c: c["sleep"])
    if best["sleep"] >= 3 * H:
        nights.append((k, best))
print(f"main nights (G={G}, midpoint key, >=3h): {len(nights)}")

hr = []
for r in load("heart_rate"):
    if r["origin"] == OH and len(r["samples"]) == 1:
        hr.append(r["samples"][0])
hr = np.array(sorted(hr), float)
spo2 = np.array(sorted((r["time"], r["value"]) for r in load("spo2") if r["origin"] == OH), float)
rr = np.array(sorted((r["time"], r["value"]) for r in load("respiratory_rate") if r["origin"] == OH), float)


def inside(arr, c, skip_ms=0):
    if not len(arr):
        return np.array([])
    m = np.zeros(len(arr), bool)
    for s, e in c["parts"]:
        m |= (arr[:, 0] >= s) & (arr[:, 0] <= e)
    m &= arr[:, 0] >= c["start"] + skip_ms
    return arr[m, 1]


def tmean(v, p=0.1):
    v = np.sort(v)
    k = int(len(v) * p)
    return v[k:len(v) - k].mean() if len(v) > 2 * k else np.nan


rows = collections.defaultdict(list)
for k, c in nights:
    h = inside(hr, c, 30 * MIN)
    s = inside(spo2, c)
    r = inside(rr, c, 30 * MIN)
    rows["sleep_h"].append(c["sleep"] / H)
    rows["hr_p10"].append(np.percentile(h, 10) if len(h) >= 20 else np.nan)
    rows["hr_lowhalf"].append(np.sort(h)[: len(h) // 2].mean() if len(h) >= 20 else np.nan)
    rows["spo2_mean"].append(s.mean() if len(s) >= 6 else np.nan)
    rows["spo2_tmean"].append(tmean(s) if len(s) >= 6 else np.nan)
    rows["rr_mean"].append(r.mean() if len(r) >= 15 else np.nan)
    rows["rr_tmean"].append(tmean(r) if len(r) >= 15 else np.nan)
    rows["rr_median"].append(np.median(r) if len(r) >= 15 else np.nan)


def rsd(v):
    v = v[~np.isnan(v)]
    return 1.4826 * np.median(np.abs(v - np.median(v)))


print("\n== night-to-night spread (relative only) ==")
for name, v in rows.items():
    v = np.array(v, float)
    ok = v[~np.isnan(v)]
    d = np.diff(v)
    d = d[~np.isnan(d)]
    a = v[:-1], v[1:]
    m = ~np.isnan(a[0]) & ~np.isnan(a[1])
    ac = np.corrcoef(a[0][m], a[1][m])[0, 1] if m.sum() > 3 else np.nan
    print(f"{name:11s} n={len(ok)} robustSD={rsd(v):.3f} SD={ok.std(ddof=1):.3f} "
          f"p95-p5={np.percentile(ok, 95) - np.percentile(ok, 5):.3f} "
          f"consecutive-diff robustSD={rsd(d):.3f} lag1={ac:.2f} distinct={len(np.unique(np.round(ok, 3)))}")

# rolling baseline simulation
FLOOR_SETS = {
    "floors A (sleep 0.5 h, HR 1.5, SpO2 0.5, RR 0.5)": {"sleep_h": 0.5, "hr_p10": 1.5, "spo2_mean": 0.5, "rr_mean": 0.5},
    "floors B (sleep 0.75 h, HR 2.5, SpO2 0.3, RR 0.3)": {"sleep_h": 0.75, "hr_p10": 2.5, "spo2_mean": 0.3, "rr_mean": 0.3},
}
SIGN = {"sleep_h": -1, "hr_p10": +1, "spo2_mean": -1, "rr_mean": +1}  # direction of concern
print("\n== rolling baseline: share of evaluable nights beyond z (two-sided, and in concern direction) ==")
for (fname, FLOORS), (W, MINN) in [(f, w) for f in FLOOR_SETS.items() for w in ((14, 7), (28, 7), (28, 14))]:
    zs = {}
    floor_hits = {}
    for name in FLOORS:
        v = np.array(rows[name], float)
        z = np.full(len(v), np.nan)
        for i in range(len(v)):
            hist = v[max(0, i - W):i]
            hist = hist[~np.isnan(hist)]
            if len(hist) < MINN or np.isnan(v[i]):
                continue
            sc = max(rsd(hist), FLOORS[name])
            floor_hits.setdefault(name, []).append(rsd(hist) < FLOORS[name])
            z[i] = (v[i] - np.median(hist)) / sc
        zs[name] = z
    print(f"-- {fname}; window {W}, min nights {MINN}")
    for name, z in zs.items():
        ok = z[~np.isnan(z)]
        dz = SIGN[name] * ok
        print(f"  {name:10s} n={len(ok)} |z|>2: {np.mean(np.abs(ok) > 2):.0%} |z|>2.5: {np.mean(np.abs(ok) > 2.5):.0%}"
              f" |z|>3: {np.mean(np.abs(ok) > 3):.0%} floor-active {np.mean(floor_hits.get(name, [0])):.0%}  concern z>2: {np.mean(dz > 2):.0%} z>2.5: {np.mean(dz > 2.5):.0%}")
    allz = np.vstack([SIGN[n] * zs[n] for n in FLOORS])
    ev = ~np.isnan(allz).all(axis=0)
    for t in (2.0, 2.5, 3.0):
        cnt = np.nansum(allz > t, axis=0)
        print(f"  nights with >=2 metrics beyond concern z>{t}: {int((cnt[ev] >= 2).sum())}/{int(ev.sum())}")
