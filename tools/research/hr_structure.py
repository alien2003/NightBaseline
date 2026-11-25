#!/usr/bin/env python3
"""OHealth HeartRateRecord structure: samples per record, record span, sampling intervals, lastModified delay,
sleep session structure. Aggregates only (counts, intervals, delays), no physiological values.

  python3 -I tools/research/hr_structure.py .data
"""
import collections
import json
import os
import sys

import numpy as np

OH = "com.heytap.health.international"
data = sys.argv[1]
ds = os.path.join(data, "dataset")


def load(name):
    with open(os.path.join(ds, name + ".jsonl")) as f:
        return [json.loads(l) for l in f]


def q(a, ps=(0, 5, 25, 50, 75, 95, 100)):
    a = np.asarray(a, float)
    return " ".join(f"p{p}={np.percentile(a, p):.4g}" for p in ps) if len(a) else "empty"


hr = [r for r in load("heart_rate") if r["origin"] == OH]
print("OHealth HR records:", len(hr))
print("samples/record:", collections.Counter(len(r["samples"]) for r in hr).most_common(8))
span = [(r["end"] - r["start"]) / 1000 for r in hr]
print("record span s:", q(span))
outside = sum(1 for r in hr for t, _ in r["samples"] if not (r["start"] <= t <= r["end"]))
print("samples outside record bounds:", outside)
print("first sample time == start:", sum(1 for r in hr if r["samples"][0][0] == r["start"]),
      "end==start:", sum(1 for r in hr if r["end"] == r["start"]))
print("zone offsets:", collections.Counter(r["startZoneOffsetS"] for r in hr))
print("sample time mod 60s top:", collections.Counter((t % 60000) // 1000 for r in hr for t, _ in r["samples"]).most_common(5))
ts_all = sorted(t for r in hr for t, _ in r["samples"])
ts = np.unique(ts_all)
print("total samples", len(ts_all), "unique times", len(ts))
d = np.diff(ts) / 1000
print("interval s (all):", q(d))
bins = [0, 59, 61, 119, 121, 179, 181, 299, 301, 600, 1800, 3600, 6 * 3600, 1e12]
h, _ = np.histogram(d, bins)
for a, b, c in zip(bins[:-1], bins[1:], h):
    print(f"  [{a:>6g},{b:>6g}) {c} ({100 * c / len(d):.1f}%)")
lm = np.array([(r["lastModifiedMs"] - r["end"]) / 3600e3 for r in hr])
print("lastModified - end, hours:", q(lm))
print("distinct lastModified values:", len(set(r["lastModifiedMs"] for r in hr)))
print("records per lastModified batch:", q(list(collections.Counter(r["lastModifiedMs"] for r in hr).values())))
print("span of OH HR data, days:", (ts[-1] - ts[0]) / 86400e3)

sl = [r for r in load("sleep") if r["origin"] == OH]
print("\nOHealth sleep sessions:", len(sl), "with stages:", sum(1 for r in sl if r.get("stages")))
dur = np.array([(r["end"] - r["start"]) / 3600e3 for r in sl])
print("duration h:", q(dur))
print("stage codes:", collections.Counter(s[2] for r in sl for s in (r.get("stages") or [])))
print("sleep lastModified - end h:", q([(r["lastModifiedMs"] - r["end"]) / 3600e3 for r in sl]))
