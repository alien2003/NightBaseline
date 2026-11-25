#!/usr/bin/env python3
"""Per type and origin: first/last local day and number of distinct days with data. Aggregates only, no values.

  python3 -I tools/research/coverage.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

data = sys.argv[1]
ds = os.path.join(data, "dataset")
for fn in sorted(os.listdir(ds)):
    if not fn.endswith(".jsonl") or fn == "observations.jsonl":
        continue
    days = collections.defaultdict(set)
    with open(os.path.join(ds, fn)) as f:
        for line in f:
            r = json.loads(line)
            t = r.get("time", r.get("start"))
            off = r.get("zoneOffsetS", r.get("startZoneOffsetS")) or 0
            days[r["origin"]].add(dt.datetime.utcfromtimestamp(t / 1000 + off).date())
    for origin, d in sorted(days.items()):
        print(f"{fn[:-6]:20s} {origin[:45]:45s} {min(d)}..{max(d)} days={len(d)}")
