#!/usr/bin/env python3
"""'Main night' selection rule test for one or all sleep origins.

Rule: sort sessions of one origin, merge into clusters while the gap to the previous session is <= G minutes;
cluster sleep = sum of session durations, span = first start .. last end; candidate clusters are those whose end
falls in [END_LO, END_HI) local; key = local date of cluster end; the main night is the candidate with the largest
sleep. Ambiguous wake dates: >= 2 candidates and the runner-up sleep >= 50 % of the winner (or >= 3 h).
Prints aggregates only (counts, shares, quantiles of gaps/durations); never per-night values.

  mise exec -- python -I tools/research/sleep_mainnight.py .data [origin-short-name ...]
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np

data = sys.argv[1]
want = set(sys.argv[2:]) or {"OHealth", "importer", "SleepAsAndroid", "PulseStages"}
SHORT = {"com.heytap.health.international": "OHealth", "org.healthmerge.hcimporter": "importer",
         "com.urbandroid.sleep": "SleepAsAndroid", "io.github.alien2003.pulsestages": "PulseStages"}
MIN = 60_000
H = 3_600_000
END_LO, END_HI = 3, 14
OH_ERA = dt.date(2026, 9, 28)

by = collections.defaultdict(list)
with open(os.path.join(data, "dataset", "sleep.jsonl")) as f:
    for line in f:
        r = json.loads(line)
        s = SHORT.get(r["origin"], r["origin"])
        if s in want:
            by[s].append(r)


def loc(ms, off_s):
    return dt.datetime.fromtimestamp(ms / 1000 + (off_s or 0), dt.timezone.utc).replace(tzinfo=None)


def clusters(rs, g):
    out = []
    for r in sorted(rs, key=lambda r: r["start"]):
        if out and r["start"] - out[-1]["end"] <= g * MIN:
            c = out[-1]
            c["end"] = max(c["end"], r["end"])
            c["eo"] = r.get("endZoneOffsetS")
            c["sleep"] += r["end"] - r["start"]
            c["n"] += 1
        else:
            out.append({"start": r["start"], "end": r["end"], "so": r.get("startZoneOffsetS"),
                        "eo": r.get("endZoneOffsetS"), "sleep": r["end"] - r["start"], "n": 1})
    return out


def q(a, ps=(5, 25, 50, 75, 95)):
    a = np.asarray(a, float)
    return f"n={len(a)} " + " ".join(f"p{p}={np.percentile(a, p):.1f}" for p in ps) if len(a) else "n=0"


def select(cl):
    cand = collections.defaultdict(list)
    outside = []
    for c in cl:
        e = loc(c["end"], c["eo"])
        h = e.hour + e.minute / 60
        if END_LO <= h < END_HI:
            cand[e.date()].append(c)
        else:
            outside.append(c)
    main = {}
    amb = 0
    multi = 0
    for d, cs in cand.items():
        cs.sort(key=lambda c: -c["sleep"])
        main[d] = cs[0]
        if len(cs) > 1:
            multi += 1
            if cs[1]["sleep"] >= 0.5 * cs[0]["sleep"] or cs[1]["sleep"] >= 3 * H:
                amb += 1
    return main, amb, multi, outside


for src in sorted(by):
    rs = by[src]
    print(f"\n=== {src}: sessions={len(rs)} ===")
    sel = {}
    for g in (0, 30, 60, 90, 120, 180):
        cl = clusters(rs, g)
        main, amb, multi, outside = select(cl)
        sel[g] = main
        d0 = min(main)
        d1 = max(main)
        span = (d1 - d0).days + 1
        if src == "OHealth":
            d0, span = OH_ERA + dt.timedelta(days=1), (d1 - OH_ERA).days
        days = set(main)
        missing = sum(1 for i in range(span) if d0 + dt.timedelta(days=i) not in days)
        sleep_h = np.array([c["sleep"] / H for c in main.values()])
        span_h = np.array([(c["end"] - c["start"]) / H for c in main.values()])
        frag = sum(1 for c in main.values() if c["n"] > 1)
        big_out = sum(1 for c in outside if c["sleep"] >= 3 * H)
        print(f" G={g:3d}: clusters={len(cl)} wake-dates={len(main)}/{span} missing={missing}"
              f" multi-candidate={multi} ambiguous={amb} main-with->1-session={frag}"
              f" outside-window clusters={len(outside)} (of them >=3h: {big_out})"
              f" short<3h={int((sleep_h < 3).sum())} short<4h={int((sleep_h < 4).sum())}"
              f" span-sleep(min) {q((span_h - sleep_h) * 60, (50, 95))}")
    base = sel[60]
    for g in (0, 30, 90, 120, 180):
        diff = sum(1 for d in set(base) | set(sel[g])
                   if d not in base or d not in sel[g]
                   or (base[d]["start"], base[d]["end"]) != (sel[g][d]["start"], sel[g][d]["end"]))
        print(f"  wake-dates whose main night differs G={g} vs G=60: {diff}")
    # gaps between sessions inside 'same night' (both in [18:00 .. 14:00 next] window), for split-night evidence
    rr = sorted(rs, key=lambda r: r["start"])
    gaps = [(b["start"] - a["end"]) / MIN for a, b in zip(rr, rr[1:]) if 0 <= b["start"] - a["end"] <= 8 * H]
    print(f"  gaps <= 8h between consecutive sessions (min): {q(gaps)}")
    # end-window sensitivity
    cl = clusters(rs, 60)
    ends = [loc(c["end"], c["eo"]) for c in cl if c["sleep"] >= 3 * H]
    eh = np.array([e.hour + e.minute / 60 for e in ends])
    print(f"  clusters >=3h (G=60): {len(eh)}; end before 03:00: {(eh < 3).sum()}, end >= 14:00: {(eh >= 14).sum()},"
          f" end in [03,05): {((eh >= 3) & (eh < 5)).sum()}, end in [12,14): {((eh >= 12) & (eh < 14)).sum()}")
    sh = np.array([loc(c["start"], c["so"]).hour for c in cl if c["sleep"] >= 3 * H])
    print(f"  clusters >=3h start hour in [12,18): {((sh >= 12) & (sh < 18)).sum()}")

# Alternative, window-free rule: key = local date of (cluster midpoint + 6 h), i.e. midpoint in [D-1 18:00, D 18:00)
print("\n=== alternative: midpoint-keyed rule (no end window) ===")
for src in sorted(by):
    rs = by[src]
    for g in (30, 60, 90, 120):
        cl = clusters(rs, g)
        cand = collections.defaultdict(list)
        for c in cl:
            mid = (c["start"] + c["end"]) // 2
            cand[(loc(mid, c["eo"]) + dt.timedelta(hours=6)).date()].append(c)
        main, amb, multi = {}, 0, 0
        for d, cs in cand.items():
            cs.sort(key=lambda c: -c["sleep"])
            main[d] = cs[0]
            if len(cs) > 1:
                multi += 1
                amb += cs[1]["sleep"] >= 0.5 * cs[0]["sleep"] or cs[1]["sleep"] >= 3 * H
        nights = sum(1 for c in main.values() if c["sleep"] >= 3 * H)
        old, _, _, _ = select(cl)
        same = sum(1 for d, c in old.items()
                   if d in main and (main[d]["start"], main[d]["end"]) == (c["start"], c["end"]))
        sh = np.array([c["sleep"] / H for c in main.values()])
        print(f" {src} G={g}: keyed dates={len(main)} (>=3h main: {nights}) multi={multi} ambiguous={amb}"
              f" agree with end-window rule: {same}/{len(old)}"
              f" short<3h={int((sh < 3).sum())} short<4h={int((sh < 4).sum())}")
