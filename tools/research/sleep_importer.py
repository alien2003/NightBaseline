#!/usr/bin/env python3
"""Importer (previous wearable) sleep: zone-offset sanity, per-offset time-of-day bins, coverage of the active
period, main-night rule with the record offset vs. the device zone, stage-vs-session consistency.
Aggregates only.

  mise exec -- python -I tools/research/sleep_importer.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np

data = sys.argv[1]
MIN, H = 60_000, 3_600_000
rs = []
with open(os.path.join(data, "dataset", "sleep.jsonl")) as f:
    for line in f:
        r = json.loads(line)
        if r["origin"] == "org.healthmerge.hcimporter":
            rs.append(r)
rs.sort(key=lambda r: r["start"])


def loc(ms, off_s):
    return dt.datetime.fromtimestamp(ms / 1000 + off_s, dt.timezone.utc).replace(tzinfo=None)


def hb(hs):
    e = [(0, 3), (3, 6), (6, 10), (10, 14), (14, 18), (18, 24)]
    c = collections.Counter(next(f"{a:02d}-{b:02d}" for a, b in e if a <= h < b) for h in hs)
    return dict(sorted(c.items()))


print("per offset: n, year range, start-hour bins, end-hour bins (record offset)")
byoff = collections.defaultdict(list)
for r in rs:
    byoff[r["startZoneOffsetS"]].append(r)
for off, xs in sorted(byoff.items()):
    ys = sorted({loc(r["start"], off).year for r in xs})
    print(f" off={off / 3600:+.0f}h n={len(xs)} years={ys[0]}..{ys[-1]} startOffset!=endOffset:"
          f"{sum(r['startZoneOffsetS'] != r['endZoneOffsetS'] for r in xs)}")
    print(f"   start {hb([loc(r['start'], off).hour for r in xs])}")
    print(f"   end   {hb([loc(r['end'], off).hour for r in xs])}")
    long = [r for r in xs if r["end"] - r["start"] >= 3 * H]
    print(f"   sessions>=3h end {hb([loc(r['end'], off).hour for r in long])}")
    # offset transitions in time order
trans = sum(1 for a, b in zip(rs, rs[1:]) if a["startZoneOffsetS"] != b["startZoneOffsetS"])
print(f"offset changes in time order: {trans}")

# active-period coverage: months with any session
months = collections.Counter(loc(r["end"], r["endZoneOffsetS"]).strftime("%Y-%m") for r in rs)
print(f"months with data: {len(months)}; sessions per month quantiles: "
      f"{np.percentile(list(months.values()), [5, 25, 50, 75, 95]).round(0).tolist()}")
d_all = sorted({loc(r["end"], r["endZoneOffsetS"]).date() for r in rs})
runs = []
for a, b in zip(d_all, d_all[1:]):
    if (b - a).days > 14:
        runs.append((b - a).days)
print(f"gaps >14 days between wake-dates with data: count={len(runs)} lengths(days)={sorted(runs)}")

# stage types per session: sessions with only one stage code / with 'awake'(1) / with out-of-bed(7) / unknown(0)
st = [r for r in rs if r.get("stages")]
single = sum(1 for r in st if len({s[2] for s in r["stages"]}) == 1)
print(f"stage sessions with a single stage code: {single}/{len(st)}")
nost = [r for r in rs if not r.get("stages")]
print(f"no-stage sessions: {len(nost)}; recordingMethod {dict(collections.Counter(r['recordingMethod'] for r in nost))}"
      f"; duration buckets h: {dict(collections.Counter(min(int((r['end'] - r['start']) / H), 10) for r in nost))}")
# does a no-stage session overlap a stage session (duplicate representation)?
ov = 0
for r in nost:
    if any(o["start"] < r["end"] and r["start"] < o["end"] for o in st):
        ov += 1
print(f"no-stage sessions overlapping a staged session: {ov}/{len(nost)}")
# first stage segment code at the session start / awake share at edges
first = collections.Counter(sorted(r["stages"])[0][2] for r in st)
last = collections.Counter(sorted(r["stages"])[-1][2] for r in st)
print(f"first segment code: {dict(first)}; last segment code: {dict(last)}")

# offset sanity via activity: share of importer steps by local hour (record offset), per offset group
steps = collections.defaultdict(lambda: np.zeros(24))
with open(os.path.join(data, "dataset", "steps.jsonl")) as f:
    for line in f:
        r = json.loads(line)
        if r["origin"] != "org.healthmerge.hcimporter":
            continue
        off = r.get("startZoneOffsetS") or 0
        steps[off][loc(r["start"], off).hour] += r.get("value") or 0
for off, v in sorted(steps.items()):
    s = v.sum() or 1
    print(f"importer steps off={off / 3600:+.0f}h share by 3h block %: "
          f"{[round(100 * v[i:i + 3].sum() / s) for i in range(0, 24, 3)]}")

# OHealth sleep lastModified: are sessions written in batches?
oh = []
with open(os.path.join(data, "dataset", "sleep.jsonl")) as f:
    for line in f:
        r = json.loads(line)
        if r["origin"] in ("com.heytap.health.international", "io.github.alien2003.pulsestages"):
            oh.append(r)
for org in ("com.heytap.health.international", "io.github.alien2003.pulsestages"):
    xs = sorted((r for r in oh if r["origin"] == org), key=lambda r: r["end"])
    lm = sorted(r["lastModifiedMs"] for r in xs)
    batches = 1 + sum(1 for a, b in zip(lm, lm[1:]) if b - a > 5 * MIN)
    lag = np.array([(r["lastModifiedMs"] - r["end"]) / H for r in xs])
    rec = lag[-14:]
    print(f"{org.split('.')[-1]}: distinct lastModified batches(>5min apart)={batches}/{len(xs)};"
          f" lag h quantiles all={np.percentile(lag, [5, 50, 95]).round(1).tolist()}"
          f" last14={np.percentile(rec, [5, 50, 95]).round(1).tolist()}"
          f" lag<2h={int((lag < 2).sum())} lag<6h={int((lag < 6).sum())} lag<12h={int((lag < 12).sum())}")
    lmh = collections.Counter(loc(r["lastModifiedMs"], -10800).hour for r in xs)
    print(f"   lastModified local hour histogram: {dict(sorted(lmh.items()))}")

# OHealth: is a late lastModified explained by a rewrite at the next sync (another session's lastModified)?
xs = sorted((r for r in oh if r["origin"] == "com.heytap.health.international"), key=lambda r: r["end"])
same_batch_with_newer = 0
for r in xs:
    later = [o for o in xs if o["end"] > r["end"] and abs(o["lastModifiedMs"] - r["lastModifiedMs"]) < 5 * MIN]
    same_batch_with_newer += bool(later)
print(f"OHealth sessions sharing a lastModified batch with a later-ending session: {same_batch_with_newer}/{len(xs)}")
rel = []
for r in xs:
    prev = [o["end"] for o in xs if o["end"] <= r["lastModifiedMs"]]
    rel.append((r["lastModifiedMs"] - max(prev)) / H)
print(f"OHealth lastModified - latest session end before it (h): "
      f"{np.percentile(rel, [5, 25, 50, 75, 95]).round(1).tolist()}")
newest = [r for r in xs if not any(o["end"] > r["end"] and abs(o["lastModifiedMs"] - r["lastModifiedMs"]) < 5 * MIN
                                   for o in xs)]
nl = np.array([(r["lastModifiedMs"] - r["end"]) / H for r in newest])
print(f"OHealth batch-newest sessions: {len(newest)}; lastModified-end h {np.percentile(nl, [5, 25, 50, 75, 95]).round(1).tolist()}"
      f"; <3h={int((nl < 3).sum())} <12h={int((nl < 12).sum())}")
big = [r for r in newest if r["end"] - r["start"] >= 3 * H]
bl = np.array([(r["lastModifiedMs"] - r["end"]) / H for r in big])
print(f"  of them >=3h: {len(big)}; lag h {np.percentile(bl, [5, 25, 50, 75, 95]).round(1).tolist()}")
ids = {r["id"] for r in xs}
vers = collections.Counter()
with open(os.path.join(data, "dataset", "observations.jsonl")) as f:
    for line in f:
        o = json.loads(line)
        if o.get("id") in ids:
            vers[o["id"]] += 1
print(f"observations per OHealth sleep id: {dict(collections.Counter(vers.values()))}; keys={sorted(o.keys())}")
