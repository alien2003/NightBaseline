#!/usr/bin/env python3
"""Times (no health values) of the newest sleep sessions of one origin and the coverage of its channels inside them.

  mise exec -- python -I tools/research/last_night.py .data [origin] [n]
"""
import datetime as dt
import json
import os
import sys

data = sys.argv[1]
origin = sys.argv[2] if len(sys.argv) > 2 else "com.heytap.health.international"
n = int(sys.argv[3]) if len(sys.argv) > 3 else 3
ds = os.path.join(data, "dataset")


def load(t):
    p = os.path.join(ds, t + ".jsonl")
    if not os.path.exists(p):
        return []
    with open(p) as f:
        return [r for r in map(json.loads, f) if r["origin"] == origin]


def local(ms, off):
    return dt.datetime.fromtimestamp(ms / 1000, dt.timezone(dt.timedelta(seconds=off or 0))).strftime("%m-%d %H:%M")


sessions = sorted(load("sleep"), key=lambda r: r["end"])[-n:]
hr = [s[0] for r in load("heart_rate") for s in r["samples"]]
spo2 = [r["time"] for r in load("spo2")]
rr = [r["time"] for r in load("respiratory_rate")]
steps = load("steps")
for s in sessions:
    off = s.get("endZoneOffsetS")
    a, b = s["start"], s["end"]
    inside = lambda ts: [t for t in ts if a <= t <= b]
    h, o, r = inside(hr), inside(spo2), inside(rr)
    last = lambda xs: local(max(xs), off) if xs else "-"
    brisk = [x for x in steps if x["start"] > b + 120000 and x["value"] / max(1, (x["end"] - x["start"]) / 60000) >= 5]
    print(f"session {local(a, off)} -> {local(b, off)} ({(b - a) / 3600000:.1f} h), written {local(s['lastModifiedMs'], off)}, stages={len(s.get('stages', []))}")
    print(f"   HR n={len(h)} last {last(h)} | SpO2 n={len(o)} last {last(o)} | RR n={len(r)} last {last(r)} | brisk step after end: {local(min(x['start'] for x in brisk), off) if brisk else 'none'}")
