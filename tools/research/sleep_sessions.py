#!/usr/bin/env python3
"""Sleep sessions per origin: counts, sessions per wake day, duration buckets, time-of-day bins, stage presence,
naps, cross-origin overlaps and gaps between sessions of one origin. Prints aggregates only (counts, shares,
quantiles of durations/gaps/differences); never prints per-night values or dates paired with values.

  mise exec -- python -I tools/research/sleep_sessions.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np

data = sys.argv[1]
OH = "com.heytap.health.international"
SHORT = {OH: "OHealth", "org.healthmerge.hcimporter": "importer", "com.urbandroid.sleep": "SleepAsAndroid",
         "io.github.alien2003.pulsestages": "PulseStages"}
MIN = 60_000
H = 3_600_000


def load():
    out = []
    with open(os.path.join(data, "dataset", "sleep.jsonl")) as f:
        for line in f:
            r = json.loads(line)
            r["src"] = SHORT.get(r["origin"], r["origin"])
            r["so"] = (r.get("startZoneOffsetS") or 0) * 1000
            r["eo"] = (r.get("endZoneOffsetS") or 0) * 1000
            r["dur"] = r["end"] - r["start"]
            out.append(r)
    out.sort(key=lambda r: r["start"])
    return out


def local(ms, off):
    return dt.datetime.utcfromtimestamp((ms + off) / 1000)


def hod(ms, off):
    t = local(ms, off)
    return t.hour + t.minute / 60


def q(a, ps=(5, 25, 50, 75, 95)):
    a = np.asarray(a, float)
    if not len(a):
        return "n=0"
    return f"n={len(a)} " + " ".join(f"p{p}={np.percentile(a, p):.1f}" for p in ps)


def rsd(a):
    a = np.asarray(a, float)
    return 1.4826 * np.median(np.abs(a - np.median(a))) if len(a) else float("nan")


def bins(vals, edges, labels):
    c = collections.Counter()
    for v in vals:
        for (lo, hi), lab in zip(edges, labels):
            if lo <= v < hi:
                c[lab] += 1
                break
    n = len(vals) or 1
    return " ".join(f"{lab}:{c[lab]}({100 * c[lab] / n:.0f}%)" for lab in labels)


recs = load()
by = collections.defaultdict(list)
for r in recs:
    by[r["src"]].append(r)

DUR_E = [(0, 1), (1, 3), (3, 4), (4, 6), (6, 8), (8, 10), (10, 99)]
DUR_L = ["<1h", "1-3h", "3-4h", "4-6h", "6-8h", "8-10h", ">=10h"]
HOD_E = [(0, 3), (3, 6), (6, 10), (10, 14), (14, 18), (18, 21), (21, 24)]
HOD_L = ["00-03", "03-06", "06-10", "10-14", "14-18", "18-21", "21-24"]

print("== per origin ==")
for src, rs in sorted(by.items()):
    d0 = local(rs[0]["start"], rs[0]["so"]).date()
    d1 = local(rs[-1]["end"], rs[-1]["eo"]).date()
    offs = collections.Counter(r["startZoneOffsetS"] for r in rs)
    rm = collections.Counter(r.get("recordingMethod") for r in rs)
    print(f"\n[{src}] sessions={len(rs)} span={d0}..{d1} zoneOffsets={dict(offs)} recordingMethod={dict(rm)}")
    wake = collections.Counter(local(r["end"], r["eo"]).date() for r in rs)
    span_days = (d1 - d0).days + 1
    per = collections.Counter(wake.values())
    print(f"  wake-days with >=1 session: {len(wake)}/{span_days} ({100 * len(wake) / span_days:.0f}%);"
          f" sessions per wake-day histogram: {dict(sorted(per.items()))}")
    durh = [r["dur"] / H for r in rs]
    print(f"  duration buckets: {bins(durh, DUR_E, DUR_L)}")
    print(f"  duration IQR width h={np.percentile(durh, 75) - np.percentile(durh, 25):.2f}")
    print(f"  start hod: {bins([hod(r['start'], r['so']) for r in rs], HOD_E, HOD_L)}")
    print(f"  end   hod: {bins([hod(r['end'], r['eo']) for r in rs], HOD_E, HOD_L)}")
    st = [r for r in rs if r.get("stages")]
    codes = collections.Counter(s[2] for r in st for s in r["stages"])
    print(f"  with stages: {len(st)}/{len(rs)}; stage codes (segments): {dict(sorted(codes.items()))}")
    if st:
        seglen = [(s[1] - s[0]) / MIN for r in st for s in r["stages"]]
        cover = []
        inside = 0
        for r in st:
            tot = sum(s[1] - s[0] for s in r["stages"])
            cover.append(100 * tot / max(r["dur"], 1))
            inside += all(r["start"] <= s[0] <= s[1] <= r["end"] for s in r["stages"])
        print(f"  stage segment length min: {q(seglen)}")
        print(f"  stage coverage of session %: {q(cover)}; all segments inside session: {inside}/{len(st)}")
        gaps = []
        for r in st:
            ss = sorted(r["stages"])
            gaps += [(b[0] - a[1]) / MIN for a, b in zip(ss, ss[1:])]
        g = np.asarray(gaps)
        if len(g):
            print(f"  between-segment gaps: n={len(g)} >0:{(g > 0.01).sum()} <0(overlap):{(g < -0.01).sum()}")
    # recent 60 days restriction for multi-year sources
    # gaps between consecutive sessions of the same origin
    gaps = np.array([(b["start"] - a["end"]) / MIN for a, b in zip(rs, rs[1:])])
    print(f"  consecutive-session gap counts: overlap(<0):{(gaps < 0).sum()} 0-30m:{((gaps >= 0) & (gaps <= 30)).sum()}"
          f" 30-60m:{((gaps > 30) & (gaps <= 60)).sum()} 60-120m:{((gaps > 60) & (gaps <= 120)).sum()}"
          f" 2-4h:{((gaps > 120) & (gaps <= 240)).sum()} 4-8h:{((gaps > 240) & (gaps <= 480)).sum()} >8h:{(gaps > 480).sum()}")
    dup = collections.Counter((r["start"], r["end"]) for r in rs)
    print(f"  exact duplicate (start,end) pairs: {sum(v - 1 for v in dup.values() if v > 1)}")
    lag = [(r["lastModifiedMs"] - r["end"]) / MIN for r in rs]
    print(f"  lastModified - end (min): {q(lag)}")

# naps: sessions starting 09:00..19:00 local and not part of the night (duration < 4 h)
print("\n== naps (start in [09:00,19:00) local and duration < 4h) ==")
for src, rs in sorted(by.items()):
    naps = [r for r in rs if 9 <= hod(r["start"], r["so"]) < 19 and r["dur"] < 4 * H]
    print(f"  {src}: {len(naps)}/{len(rs)}; nap duration min: {q([r['dur'] / MIN for r in naps], (25, 50, 75))}")

# cross-origin overlaps, restricted to the OHealth era
print("\n== cross-origin overlaps (OHealth era) ==")
oh = by["OHealth"]
era0 = oh[0]["start"] - 12 * H
for other in ("PulseStages", "SleepAsAndroid", "importer"):
    os_ = [r for r in by.get(other, []) if r["end"] >= era0]
    n_ov = 0
    ds, de, fr = [], [], []
    multi = 0
    for o in os_:
        ov = [r for r in oh if r["start"] < o["end"] and o["start"] < r["end"]]
        if ov:
            n_ov += 1
            multi += len(ov) > 1
            # union of overlapped OHealth sessions
            s0 = min(r["start"] for r in ov)
            e0 = max(r["end"] for r in ov)
            ds.append((o["start"] - s0) / MIN)
            de.append((o["end"] - e0) / MIN)
            inter = sum(max(0, min(o["end"], r["end"]) - max(o["start"], r["start"])) for r in ov)
            fr.append(100 * inter / max(o["dur"], 1))
    print(f"  {other}: sessions in era={len(os_)}, overlapping >=1 OHealth={n_ov}, overlapping >1 OHealth={multi}")
    if n_ov:
        print(f"    start diff ({other} - OHealth) min: {q(ds)}")
        print(f"    end   diff ({other} - OHealth) min: {q(de)}")
        print(f"    % of {other} session covered by OHealth: {q(fr)}")
        exact = sum(1 for a, b in zip(ds, de) if abs(a) < 1 and abs(b) < 1)
        print(f"    start&end within 1 min of OHealth union: {exact}")
# OHealth sessions not covered by others
for other in ("PulseStages", "SleepAsAndroid"):
    os_ = by.get(other, [])
    cov = sum(1 for r in oh if any(o["start"] < r["end"] and r["start"] < o["end"] for o in os_))
    print(f"  OHealth sessions overlapped by {other}: {cov}/{len(oh)}")
# importer vs OHealth hand-over
imp = by["importer"]
last_imp = max(r["end"] for r in imp)
print(f"  importer last end - OHealth first start (h): {(last_imp - oh[0]['start']) / H:.1f}")

# importer internal overlaps by device
print("\n== importer: internal overlaps ==")
ov_pairs = 0
devs = collections.Counter()
for a, b in zip(imp, imp[1:]):
    if b["start"] < a["end"]:
        ov_pairs += 1
        devs[(json.dumps(a.get("device")), json.dumps(b.get("device")))] += 1
print(f"  consecutive overlapping pairs: {ov_pairs}; by device pair (top): {devs.most_common(4)}")
yrs = collections.Counter(local(r["start"], r["so"]).year for r in imp)
print(f"  sessions per year: {dict(sorted(yrs.items()))}")
yst = collections.Counter(local(r["start"], r["so"]).year for r in imp if r.get("stages"))
print(f"  with stages per year: {dict(sorted(yst.items()))}")
ydev = collections.defaultdict(collections.Counter)
for r in imp:
    ydev[local(r["start"], r["so"]).year][(r.get("device") or {}).get("model", "-") + "/" + str(r.get("recordingMethod"))] += 1
for y in sorted(ydev):
    print(f"   {y}: {dict(ydev[y])}")

# pairwise: share of sessions of A overlapping any session of B, within the common period of A and B
print("\n== pairwise overlap within common period ==")
srcs = sorted(by)
for a in srcs:
    for b in srcs:
        if a == b:
            continue
        lo = max(by[a][0]["start"], by[b][0]["start"])
        hi = min(by[a][-1]["end"], by[b][-1]["end"])
        if hi <= lo:
            continue
        A = [r for r in by[a] if lo <= r["start"] <= hi]
        B = [r for r in by[b] if r["end"] >= lo and r["start"] <= hi]
        n = sum(1 for r in A if any(o["start"] < r["end"] and r["start"] < o["end"] for o in B))
        big = [r for r in A if r["dur"] >= 3 * H]
        nb = sum(1 for r in big if any(o["start"] < r["end"] and r["start"] < o["end"] for o in B))
        print(f"  {a} in common period with {b}: {len(A)} sessions, overlapping {b}: {n};"
              f" sessions>=3h: {len(big)}, overlapping: {nb}")
