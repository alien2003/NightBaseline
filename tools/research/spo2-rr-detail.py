#!/usr/bin/env python3
"""Follow-up to spo2-rr.py: importer SpO2 eras, zone offsets, OHealth daytime SpO2, finer nightly summaries
for integer-quantized SpO2, OHealth publication lag. Aggregates only.

  mise exec -- python -I tools/research/spo2-rr-detail.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np

DATA = sys.argv[1]
DS = os.path.join(DATA, "dataset")
OH = "com.heytap.health.international"
IMP = "org.healthmerge.hcimporter"


def load(name):
    with open(os.path.join(DS, name + ".jsonl")) as f:
        return [json.loads(l) for l in f if l.strip()]


def pct(a, qs=(5, 25, 50, 75, 95)):
    a = np.asarray(a, dtype=float)
    if a.size == 0:
        return "n=0"
    return "n=%d " % a.size + " ".join("p%d=%.3g" % (q, np.percentile(a, q)) for q in qs)


def rsd(a):
    a = np.asarray(a, dtype=float)
    return 1.4826 * np.median(np.abs(a - np.median(a))) if a.size else float("nan")


def ldate(ms, off):
    return dt.datetime.fromtimestamp(ms / 1000 + (off or 0), dt.timezone.utc).date()


def lhour(ms, off):
    return dt.datetime.fromtimestamp(ms / 1000 + (off or 0), dt.timezone.utc).hour


sleep = load("sleep")


def main_nights(origin, filt=lambda r: True):
    ss = sorted((r["start"], r["end"], r.get("endZoneOffsetS") or 0) for r in sleep if r["origin"] == origin and filt(r))
    bl, cur = [], None
    for s, e, off in ss:
        if cur and s <= cur[1] + 3.6e6:
            cur = [cur[0], max(cur[1], e), off]
        else:
            if cur:
                bl.append(tuple(cur))
            cur = [s, e, off]
    if cur:
        bl.append(tuple(cur))
    by = collections.defaultdict(list)
    for b in bl:
        by[ldate(b[1], b[2])].append(b)
    out = []
    for bs in by.values():
        b = max(bs, key=lambda b: b[1] - b[0])
        if b[1] - b[0] >= 3 * 3.6e6:
            out.append(b)
    return sorted(out), bl


print("== zone offsets (hours) per type/origin ==")
for name in ("sleep", "spo2", "respiratory_rate", "skin_temperature"):
    rs = load(name)
    c = collections.Counter((r["origin"][:12], (r.get("zoneOffsetS", r.get("startZoneOffsetS")) or 0) / 3600 if r.get("zoneOffsetS", r.get("startZoneOffsetS")) is not None else None) for r in rs)
    print(name, sorted(c.items(), key=str))

print("\n== importer sleep: stages vs no-stages sessions, device ==")
c = collections.Counter((bool(r.get("stages")), r["recordingMethod"], json.dumps(r.get("device"), sort_keys=True)) for r in sleep if r["origin"] == IMP)
print(c.most_common())

spo2 = sorted((r for r in load("spo2") if r["origin"] == IMP), key=lambda r: r["time"])
rr_days = {ldate(r["time"], r.get("zoneOffsetS")) for r in load("respiratory_rate") if r["origin"] == IMP}
print("\n== importer SpO2 by era: era B = days with importer RR (last era of the previous wearable), A = earlier ==")
per_day = collections.Counter(ldate(r["time"], r.get("zoneOffsetS")) for r in spo2)
eraB_start = min(rr_days)
for era, sel in (("A", lambda r: ldate(r["time"], r.get("zoneOffsetS")) < eraB_start),
                 ("B", lambda r: ldate(r["time"], r.get("zoneOffsetS")) >= eraB_start)):
    rs = [r for r in spo2 if sel(r)]
    if not rs:
        continue
    t = np.array([r["time"] for r in rs], dtype=np.int64)
    v = np.array([r["value"] for r in rs], float)
    days = sorted({ldate(r["time"], r.get("zoneOffsetS")) for r in rs})
    print("\nera %s: records=%d days=%d span=%d" % (era, len(rs), len(days), (days[-1] - days[0]).days + 1))
    print(" method", dict(collections.Counter(r["recordingMethod"] for r in rs)), "device",
          dict(collections.Counter(json.dumps(r.get("device"), sort_keys=True) for r in rs)))
    print(" samples/day", pct([per_day[d] for d in days]))
    print(" fraction 100%%=%.3f distinct=%d" % (np.mean(v >= 100), len(np.unique(v))))
    print(" value-median", pct(v - np.median(v), (1, 5, 25, 75, 95, 99)))
    dts = np.diff(t) / 60000
    print(" interval min", pct(dts, (5, 10, 25, 50, 75, 90, 95)))
    print(" top intervals", collections.Counter(np.round(dts[dts < 120]).astype(int)).most_common(8))
    print(" hour share %", " ".join("%02d:%.1f" % (h, 100 * c / len(rs)) for h, c in sorted(collections.Counter(lhour(r["time"], r.get("zoneOffsetS")) for r in rs).items())))
    # by recording method within era
    for m in sorted({r["recordingMethod"] for r in rs}):
        tm = np.array([r["time"] for r in rs if r["recordingMethod"] == m])
        vm = np.array([r["value"] for r in rs if r["recordingMethod"] == m], float)
        dd = np.diff(tm) / 60000
        print("  method %d: n=%d interval %s frac100=%.2f" % (m, len(tm), pct(dd, (10, 50, 90)), np.mean(vm >= 100)))
    nights, _ = main_nights(IMP)
    nights = [b for b in nights if b[0] >= t[0] - 864e5 and b[1] <= t[-1] + 864e5]
    cnt, cov, med, mean_, p10, fo, lo = [], [], [], [], [], [], []
    for s, e, _ in nights:
        i0, i1 = np.searchsorted(t, s), np.searchsorted(t, e, "right")
        seg, st = v[i0:i1], t[i0:i1]
        cnt.append(len(seg))
        nb = int(np.ceil((e - s) / 3.6e6))
        cov.append(len(np.unique((st - s) // 3600000)) / nb if len(seg) else 0)
        if len(seg):
            fo.append((st[0] - s) / 6e4)
            lo.append((e - st[-1]) / 6e4)
        if len(seg) >= 3:
            med.append(np.median(seg)); mean_.append(seg.mean()); p10.append(np.percentile(seg, 10))
    cov = np.array(cov)
    print(" main nights=%d zero=%d samples/night %s" % (len(nights), sum(c == 0 for c in cnt), pct(cnt)))
    print(" 1h coverage %s; >=0.5/0.7/0.9: %.2f/%.2f/%.2f" % (pct(cov), np.mean(cov >= .5), np.mean(cov >= .7), np.mean(cov >= .9)))
    print(" first after start", pct(fo), " last before end", pct(lo))
    if med:
        print(" night-to-night robust SD: median=%.3g mean=%.3g p10=%.3g; sd of mean=%.3g" % (rsd(med), rsd(mean_), rsd(p10), np.std(mean_)))
        print(" mean - median per night", pct(np.array(mean_) - np.array(med)))

print("\n== OHealth SpO2 outside sleep ==")
oh = sorted((r for r in load("spo2") if r["origin"] == OH), key=lambda r: r["time"])
t = np.array([r["time"] for r in oh], dtype=np.int64)
v = np.array([r["value"] for r in oh], float)
allb = sorted((r["start"], r["end"]) for r in sleep if r["end"] >= t[0] - 864e5)
nights, blocks = main_nights(OH)
ps_nights, _ = main_nights("io.github.alien2003.pulsestages")
dist = []
inside = np.zeros(len(t), bool)
for i, x in enumerate(t):
    d = min((0 if s <= x <= e else min(abs(x - s), abs(x - e))) for s, e in allb)
    dist.append(d / 6e4)
    inside[i] = d == 0
dist = np.array(dist)
out = dist[dist > 0]
print("outside samples=%d; minutes to nearest sleep session %s" % (len(out), pct(out)))
print(" within 60 min of a session: %.2f, > 3h away: %.2f" % (np.mean(out <= 60), np.mean(out > 180)))
day_per_day = collections.Counter(ldate(x, oh[i].get("zoneOffsetS")) for i, x in enumerate(t) if dist[i] > 180)
print(" days with >3h-away daytime samples: %d of %d, per such day %s" % (len(day_per_day), len({ldate(r['time'], r.get('zoneOffsetS')) for r in oh}), pct(list(day_per_day.values()), (50, 95))))
print(" daytime(>3h away) minus in-sleep value median: %.3g" % (np.median(v[dist > 180]) - np.median(v[inside])))

print("\n== OHealth integer SpO2: nightly summaries ==")
med, mean_, p10, sd = [], [], [], []
for s, e, _ in nights:
    i0, i1 = np.searchsorted(t, s), np.searchsorted(t, e, "right")
    seg = v[i0:i1]
    if len(seg) >= 3:
        med.append(np.median(seg)); mean_.append(seg.mean()); p10.append(np.percentile(seg, 10)); sd.append(seg.std(ddof=1))
med = np.array(med)
print("nights=%d; share of nights whose median equals modal nightly median=%.2f" % (len(med), np.mean(med == collections.Counter(med).most_common(1)[0][0])))
print("night-to-night robust SD of mean=%.3g (SD=%.3g), of p10=%.3g; within-night SD %s" % (rsd(mean_), np.std(mean_), rsd(p10), pct(sd)))

print("\n== same nights via PulseStages sessions (OHealth metrics) ==")
for name, bin_min in (("spo2", 60), ("respiratory_rate", 30)):
    rs = sorted((r for r in load(name) if r["origin"] == OH), key=lambda r: r["time"])
    tt = np.array([r["time"] for r in rs], dtype=np.int64)
    cnt, cov = [], []
    for s, e, _ in ps_nights:
        if e < tt[0]:
            continue
        i0, i1 = np.searchsorted(tt, s), np.searchsorted(tt, e, "right")
        st = tt[i0:i1]
        cnt.append(len(st))
        nb = int(np.ceil((e - s) / (bin_min * 6e4)))
        cov.append(len(np.unique((st - s) // int(bin_min * 6e4))) / nb if len(st) else 0)
    print(name, "pulsestages nights=%d zero=%d samples %s coverage %s" % (len(cnt), sum(c == 0 for c in cnt), pct(cnt), pct(cov)))

print("\n== OHealth: session start/end vs PulseStages main night (min, OH - PS) ==")
ds, de = [], []
for s, e, _ in nights:
    best = None
    for s2, e2, _ in ps_nights:
        if min(e, e2) - max(s, s2) > 0:
            best = (s2, e2)
    if best:
        ds.append((s - best[0]) / 6e4); de.append((e - best[1]) / 6e4)
print("matched=%d start diff %s end diff %s" % (len(ds), pct(ds), pct(de)))

print("\n== OHealth publication proxy: lastModified - session end of last in-night sample (h), last 30 nights ==")
for name in ("spo2", "respiratory_rate"):
    rs = sorted((r for r in load(name) if r["origin"] == OH), key=lambda r: r["time"])
    tt = np.array([r["time"] for r in rs], dtype=np.int64)
    lm = np.array([r["lastModifiedMs"] for r in rs], dtype=np.int64)
    lag = []
    for s, e, _ in nights[-30:]:
        i0, i1 = np.searchsorted(tt, s), np.searchsorted(tt, e, "right")
        if i1 > i0:
            lag.append((lm[i0:i1].max() - e) / 3.6e6)
    print(name, "max lastModified of night - session end (h):", pct(lag))
    lm_s = sorted({r["lastModifiedMs"] for r in rs})
    print("  distinct lastModified values=%d for %d records" % (len(lm_s), len(rs)))

print("\n== importer era A: sleep start local hour vs SpO2 hour, density by month index ==")
eraA_sleep = [r for r in sleep if r["origin"] == IMP and ldate(r["start"], r.get("startZoneOffsetS")) < eraB_start]
print("sleep start hour", sorted(collections.Counter(lhour(r["start"], r.get("startZoneOffsetS")) for r in eraA_sleep).items()))
print("sleep end hour", sorted(collections.Counter(lhour(r["end"], r.get("endZoneOffsetS")) for r in eraA_sleep).items()))
months = collections.Counter()
for r in spo2:
    d = ldate(r["time"], r.get("zoneOffsetS"))
    if d < eraB_start:
        months[(d.year, d.month)] += 1
ks = sorted(months)
print("era A samples per month (index order, old->new):", [months[k] for k in ks])

print("\n== OHealth per-night lastModified spread (h after session end), last 30 nights ==")
for name in ("spo2", "respiratory_rate"):
    rs = sorted((r for r in load(name) if r["origin"] == OH), key=lambda r: r["time"])
    tt = np.array([r["time"] for r in rs], dtype=np.int64)
    lm = np.array([r["lastModifiedMs"] for r in rs], dtype=np.int64)
    mn, nd = [], []
    for s, e, _ in nights[-30:]:
        i0, i1 = np.searchsorted(tt, s), np.searchsorted(tt, e, "right")
        if i1 > i0:
            mn.append((lm[i0:i1].min() - e) / 3.6e6)
            nd.append(len(np.unique(lm[i0:i1])))
    print(name, "min lastModified - end (h):", pct(mn), " distinct lastModified per night:", pct(nd, (50, 95)))
