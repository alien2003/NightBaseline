#!/usr/bin/env python3
"""OHealth write batches: records grouped by identical lastModifiedMs, per type. Prints, per batch and in order,
the local hour of the write, the number of records, the span of sample times it covers and how far the write is
after its newest sample. Dates are printed only as day numbers relative to the first OHealth batch. No values.

  mise exec -- python -I tools/research/latency_batches.py .data [type ...]
"""
import collections
import datetime as dt
import json
import os
import sys
import zoneinfo

import numpy as np

DATA = sys.argv[1]
TYPES = sys.argv[2:] or ["heart_rate", "spo2", "respiratory_rate", "sleep", "steps", "resting_heart_rate"]
OH = "com.heytap.health.international"
with open(os.path.join(DATA, "dataset", "runs.json")) as f:
    TZ = zoneinfo.ZoneInfo(json.load(f)[-1]["zoneId"])


def times(r):
    if "samples" in r:
        return [s[0] for s in r["samples"]]
    if "time" in r:
        return [r["time"]]
    return [r["start"], r["end"]]


batches = {}
for t in TYPES:
    b = collections.defaultdict(list)
    with open(os.path.join(DATA, "dataset", t + ".jsonl")) as f:
        for line in f:
            r = json.loads(line)
            if r["origin"] == OH:
                b[r["lastModifiedMs"]].extend(times(r))
    batches[t] = b
t0 = min(min(b) for b in batches.values() if b)
d0 = dt.datetime.fromtimestamp(t0 / 1000, TZ).date()


def day(ms):
    return (dt.datetime.fromtimestamp(ms / 1000, TZ).date() - d0).days


def hm(ms):
    return dt.datetime.fromtimestamp(ms / 1000, TZ).strftime("%H:%M")


for t, b in batches.items():
    print(f"== {t}: {len(b)} batches")
    for lm in sorted(b):
        v = b[lm]
        print(f"  write d{day(lm):+d} {hm(lm)}  n={len(v):5d}  covers d{day(min(v)):+d} {hm(min(v))} .. "
              f"d{day(max(v)):+d} {hm(max(v))}  write-newest={(lm - max(v)) / 60000:8.1f} min  "
              f"span={(max(v) - min(v)) / 3.6e6:6.1f} h")
