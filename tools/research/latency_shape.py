#!/usr/bin/env python3
"""Shape of OHealth / PulseStages records relevant to publication latency. Aggregates only, no values.

Per origin and type: record counts, samples per record, record span, gap between consecutive sample times, and how
many records share one lastModifiedMs (a write batch).

  mise exec -- python -I tools/research/latency_shape.py .data
"""
import collections
import json
import os
import sys

import numpy as np

DATA = sys.argv[1]
DS = os.path.join(DATA, "dataset")
ORIGINS = ("com.heytap.health.international", "io.github.alien2003.pulsestages", "com.urbandroid.sleep")


def q(a, ps=(5, 25, 50, 75, 95)):
    a = np.asarray(a, dtype=float)
    if a.size == 0:
        return "n=0"
    return f"n={a.size} " + " ".join(f"p{p}={np.percentile(a, p):.2f}" for p in ps)


for t in ("sleep", "heart_rate", "spo2", "respiratory_rate", "resting_heart_rate", "steps"):
    recs = collections.defaultdict(list)
    with open(os.path.join(DS, t + ".jsonl")) as f:
        for line in f:
            r = json.loads(line)
            if r["origin"] in ORIGINS:
                recs[r["origin"]].append(r)
    for o, rows in recs.items():
        print(f"== {t} / {o}: records={len(rows)}")
        print("   recordingMethod", dict(collections.Counter(r.get("recordingMethod") for r in rows)))
        print("   device", dict(collections.Counter(json.dumps(r.get("device"), sort_keys=True) for r in rows).most_common(3)))
        if "samples" in rows[0]:
            print("   samples/record", q([len(r["samples"]) for r in rows]))
            ts = sorted(s[0] for r in rows for s in r["samples"])
            print("   gap between samples (min)", q(np.diff(ts) / 60000))
        if "start" in rows[0]:
            print("   span (min)", q([(r["end"] - r["start"]) / 60000 for r in rows]))
        tt = sorted(r.get("time", r.get("start")) for r in rows)
        if "time" in rows[0]:
            print("   gap between records (min)", q(np.diff(tt) / 60000))
        lm = collections.Counter(r["lastModifiedMs"] for r in rows)
        print("   distinct lastModified", len(lm), " records per lastModified", q(list(lm.values())))
        end = [r.get("end", r.get("time")) for r in rows]
        if "samples" in rows[0]:
            end = [max(s[0] for s in r["samples"]) for r in rows]
        d = [(r["lastModifiedMs"] - e) / 60000 for r, e in zip(rows, end)]
        print("   lastModified - (end|time|last sample) (min)", q(d, (1, 5, 25, 50, 75, 95, 99)))
        print("   share negative", f"{np.mean(np.asarray(d) < 0):.3f}")
