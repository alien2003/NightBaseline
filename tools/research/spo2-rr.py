#!/usr/bin/env python3
"""SpO2 / respiratory rate / skin temperature / HRV / body temperature structure per source.

Prints aggregates only (counts, coverage, intervals, spreads, differences); no absolute personal values,
no per-night lists.

  mise exec -- python -I tools/research/spo2-rr.py .data
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
SRC = {OH: "ohealth", IMP: "importer"}
MERGE_GAP_MS = 60 * 60 * 1000     # sessions of one origin closer than this form one sleep block
MAIN_MIN_H = 3.0                  # main night: longest block per wake date, at least this long


def load(name):
    p = os.path.join(DS, name + ".jsonl")
    if not os.path.exists(p):
        return []
    with open(p) as f:
        return [json.loads(l) for l in f if l.strip()]


def pct(a, qs=(5, 25, 50, 75, 95)):
    a = np.asarray(a, dtype=float)
    if a.size == 0:
        return "n=0"
    return "n=%d " % a.size + " ".join("p%d=%.3g" % (q, np.percentile(a, q)) for q in qs)


def rsd(a):
    a = np.asarray(a, dtype=float)
    return 1.4826 * np.median(np.abs(a - np.median(a))) if a.size else float("nan")


def local_date(ms, off):
    return dt.datetime.utcfromtimestamp(ms / 1000 + (off or 0)).date()


def local_hour(ms, off):
    return dt.datetime.utcfromtimestamp(ms / 1000 + (off or 0)).hour


# ---------- sleep blocks ----------
sleep = load("sleep")
blocks = collections.defaultdict(list)  # origin -> [(start, end, off)]
for origin in (OH, IMP):
    ss = sorted((r["start"], r["end"], r.get("endZoneOffsetS") or 0) for r in sleep if r["origin"] == origin)
    cur = None
    for s, e, off in ss:
        if cur and s <= cur[1] + MERGE_GAP_MS:
            cur = [cur[0], max(cur[1], e), off]
        else:
            if cur:
                blocks[origin].append(tuple(cur))
            cur = [s, e, off]
    if cur:
        blocks[origin].append(tuple(cur))

main = {}
naps = {}
for origin, bl in blocks.items():
    by_day = collections.defaultdict(list)
    for b in bl:
        by_day[local_date(b[1], b[2])].append(b)
    m, n = [], []
    for d, bs in by_day.items():
        bs.sort(key=lambda b: b[1] - b[0], reverse=True)
        if (bs[0][1] - bs[0][0]) / 3.6e6 >= MAIN_MIN_H:
            m.append(bs[0])
            n.extend(bs[1:])
        else:
            n.extend(bs)
    main[origin] = sorted(m)
    naps[origin] = sorted(n)
    print("SLEEP %-9s sessions=%d blocks=%d main_nights=%d naps/other_blocks=%d main_dur_h %s" % (
        SRC[origin], sum(1 for r in sleep if r["origin"] == origin), len(bl), len(m), len(n),
        pct([(b[1] - b[0]) / 3.6e6 for b in m])))

any_sleep = sorted((r["start"], r["end"]) for r in sleep)
any_starts = np.array([s for s, _ in any_sleep])
any_ends = np.maximum.accumulate(np.array([e for _, e in any_sleep]))


def in_any_sleep(t):
    i = np.searchsorted(any_starts, t, side="right") - 1
    return i >= 0 and any_ends[i] >= t


def in_blocks_vec(t, bl):
    if not bl:
        return np.zeros(len(t), bool)
    st = np.array([b[0] for b in bl])
    en = np.array([b[1] for b in bl])
    i = np.searchsorted(st, t, side="right") - 1
    return (i >= 0) & (en[np.clip(i, 0, None)] >= t)


# ---------- instantaneous metrics ----------
def analyze(metric, bin_min):
    recs = load(metric)
    print("\n================ %s ================" % metric.upper())
    for origin in (OH, IMP):
        rs = sorted((r for r in recs if r["origin"] == origin), key=lambda r: r["time"])
        if not rs:
            print("%s: no records" % SRC[origin])
            continue
        t = np.array([r["time"] for r in rs], dtype=np.int64)
        v = np.array([r["value"] for r in rs], dtype=float)
        offs = [r.get("zoneOffsetS") or 0 for r in rs]
        days = {local_date(a, o) for a, o in zip(t, offs)}
        span = (local_date(t[-1], offs[-1]) - local_date(t[0], offs[0])).days + 1
        print("\n--- %s: records=%d days_with_data=%d span_days=%d" % (SRC[origin], len(rs), len(days), span))
        print("recordingMethod", dict(collections.Counter(r["recordingMethod"] for r in rs)),
              "device", dict(collections.Counter(json.dumps(r.get("device"), sort_keys=True) for r in rs)))
        dup = len(t) - len(np.unique(t))
        print("duplicate timestamps=%d" % dup)
        # resolution
        is_int = np.mean(np.abs(v - np.round(v)) < 1e-9)
        uv = np.unique(v)
        steps = np.diff(uv)
        print("value: integer_fraction=%.4f distinct_values=%d min_step_between_distinct=%.3g range_width=%.3g" % (
            is_int, len(uv), steps.min() if steps.size else float("nan"), uv[-1] - uv[0]))
        if metric == "spo2":
            print("  fraction at 100%%=%.3f  values below (p50-3)=%.4f" % (np.mean(v >= 100), np.mean(v < np.median(v) - 3)))
        print("  value - global median distribution:", pct(v - np.median(v), (1, 5, 25, 75, 95, 99)))
        # intervals
        dts = np.diff(np.unique(t)) / 60000.0
        print("interval_min all:", pct(dts, (5, 10, 25, 50, 75, 90, 95)))
        h = collections.Counter(np.round(dts[dts < 120]).astype(int))
        print("  most common rounded intervals (min:count):", h.most_common(10))
        # day vs night
        ins = np.array([in_any_sleep(x) for x in t])
        inmain = in_blocks_vec(t, main[origin])
        hours = collections.Counter(local_hour(a, o) for a, o in zip(t, offs))
        tot = len(t)
        print("in any-origin sleep session: %.3f; in same-origin main night: %s" % (
            ins.mean(), "%.3f" % inmain.mean() if inmain is not None else "n/a"))
        print("  share by local hour (%%):", " ".join("%02d:%.1f" % (hh, 100 * hours.get(hh, 0) / tot) for hh in range(24)))
        dts_in = []
        dts_out = []
        tu = t
        for i in range(1, len(tu)):
            d = (tu[i] - tu[i - 1]) / 60000.0
            if d <= 0:
                continue
            (dts_in if ins[i] and ins[i - 1] else dts_out).append(d)
        print("interval_min inside sleep:", pct(dts_in, (5, 25, 50, 75, 95)))
        print("interval_min outside sleep:", pct(dts_out, (5, 25, 50, 75, 95)))
        # per main night (same origin sessions; for ohealth also union with pulsestages is the same HR)
        nights = main[origin]
        if origin == OH:
            data_start = t[0]
            nights = [b for b in nights if b[1] >= data_start]
        else:
            data_end = t[-1]
            nights = [b for b in nights if b[0] <= data_end and b[1] >= t[0]]
        counts, cover, first_off, last_off, medians, within_rsd, within_iqr, nmin_list, dens = [], [], [], [], [], [], [], [], []
        zero = 0
        for s, e, _ in nights:
            i0, i1 = np.searchsorted(t, s, "left"), np.searchsorted(t, e, "right")
            seg_t, seg_v = t[i0:i1], v[i0:i1]
            n = len(seg_t)
            counts.append(n)
            dur_h = (e - s) / 3.6e6
            dens.append(n / dur_h)
            nb = int(np.ceil((e - s) / (bin_min * 60000)))
            if n == 0:
                zero += 1
                cover.append(0.0)
                continue
            b = np.unique(((seg_t - s) // (bin_min * 60000)).astype(int))
            cover.append(len(b) / nb)
            first_off.append((seg_t[0] - s) / 60000)
            last_off.append((e - seg_t[-1]) / 60000)
            if n >= 3:
                medians.append(np.median(seg_v))
                within_rsd.append(rsd(seg_v))
                within_iqr.append(np.percentile(seg_v, 75) - np.percentile(seg_v, 25))
        print("main nights in data period=%d, zero-sample nights=%d (%.1f%%)" % (len(nights), zero, 100 * zero / max(1, len(nights))))
        print("samples per main night:", pct(counts))
        print("samples per hour of main night:", pct(dens))
        print("coverage of %d-min bins:" % bin_min, pct(cover))
        cv = np.array(cover)
        print("  nights with coverage >=0.5/0.7/0.9: %.2f/%.2f/%.2f" % (np.mean(cv >= .5), np.mean(cv >= .7), np.mean(cv >= .9)))
        print("first sample after session start (min):", pct(first_off))
        print("last sample before session end (min):", pct(last_off))
        md = np.array(medians)
        if md.size:
            print("nightly median: night-to-night robust SD=%.3g IQR=%.3g p95-p5=%.3g (n=%d)" % (
                rsd(md), np.percentile(md, 75) - np.percentile(md, 25), np.percentile(md, 95) - np.percentile(md, 5), md.size))
            print("  distinct nightly-median values=%d; deviation from personal median (p5,p95)=%.3g,%.3g" % (
                len(np.unique(md)), np.percentile(md - np.median(md), 5), np.percentile(md - np.median(md), 95)))
            print("within-night robust SD:", pct(within_rsd), " within-night IQR:", pct(within_iqr))
            # stability of nightly median vs sample count: subsample
            sub = {}
            rng = np.random.default_rng(1)
            for k in (3, 5, 10, 20, 40):
                errs = []
                for s, e, _ in nights:
                    i0, i1 = np.searchsorted(t, s, "left"), np.searchsorted(t, e, "right")
                    seg = v[i0:i1]
                    if len(seg) >= max(2 * k, 10):
                        full = np.median(seg)
                        for _ in range(20):
                            errs.append(abs(np.median(rng.choice(seg, k, replace=False)) - full))
                sub[k] = ("%.3g" % np.percentile(errs, 90)) if errs else "n/a"
            print("  |median(k random samples) - full-night median| p90 by k:", sub)
        # naps
        nb_ = naps[origin]
        nap_counts = [int(np.searchsorted(t, e, "right") - np.searchsorted(t, s, "left")) for s, e, _ in nb_
                      if (origin == IMP and s <= t[-1]) or (origin == OH and e >= t[0])]
        if nap_counts:
            print("non-main sleep blocks in period=%d, with >=1 sample=%d, samples:" % (
                len(nap_counts), sum(c > 0 for c in nap_counts)), pct(nap_counts, (50, 95)))
        # publication proxy
        lag = np.array([(r["lastModifiedMs"] - r["time"]) / 3.6e6 for r in rs])
        print("lastModified - time (h):", pct(lag, (5, 50, 95)))
        # sub-periods for importer: density per day by age
        if origin == IMP:
            per_day = collections.Counter(local_date(a, o) for a, o in zip(t, offs))
            ds = sorted(per_day)
            q = len(ds) // 4 or 1
            print("  samples/day by quartile of data days (old->new):",
                  [float(np.median([per_day[d] for d in ds[i:i + q]])) for i in range(0, len(ds), q)][:4])


analyze("spo2", 60)
analyze("respiratory_rate", 30)

# ---------- skin temperature ----------
print("\n================ SKIN TEMPERATURE ================")
st = sorted(load("skin_temperature"), key=lambda r: r["start"])
print("records by origin", dict(collections.Counter(r["origin"] for r in st)))
if st:
    print("recordingMethod", dict(collections.Counter(r["recordingMethod"] for r in st)),
          "device", dict(collections.Counter(json.dumps(r.get("device"), sort_keys=True) for r in st)),
          "location", dict(collections.Counter(r.get("location") for r in st)))
    dur = [(r["end"] - r["start"]) / 3.6e6 for r in st]
    nd = [len(r.get("deltas") or []) for r in st]
    print("record duration h:", pct(dur), " deltas/record:", pct(nd))
    base = np.array([r["baselineC"] for r in st if r.get("baselineC") is not None])
    print("baseline present=%d/%d distinct=%d range_width=%.3g robust SD=%.3g" % (
        base.size, len(st), len(np.unique(base)), base.max() - base.min() if base.size else 0, rsd(base)))
    # baseline change over time
    if base.size > 1:
        db = np.diff(base)
        print("  baseline change between consecutive records: zero=%.2f, |change| %s" % (np.mean(db == 0), pct(np.abs(db))))
    alld = np.array([x[1] for r in st for x in (r.get("deltas") or [])])
    if alld.size:
        frac = alld * 100 - np.round(alld * 100)
        uv = np.unique(alld)
        print("delta values: n=%d distinct=%d min_step=%.4g decimals<=2: %.3f" % (
            alld.size, uv.size, np.diff(uv).min() if uv.size > 1 else float("nan"), np.mean(np.abs(frac) < 1e-6)))
    sp = [(b[0] - a[0]) / 60000 for r in st for a, b in zip(r.get("deltas") or [], (r.get("deltas") or [])[1:])]
    print("delta spacing min:", pct(sp))
    first = [(r["deltas"][0][0] - r["start"]) / 60000 for r in st if r.get("deltas")]
    print("first delta after record start (min):", pct(first))
    starts_h = collections.Counter(local_hour(r["start"], r.get("startZoneOffsetS")) for r in st)
    ends_h = collections.Counter(local_hour(r["end"], r.get("endZoneOffsetS")) for r in st)
    print("start local hour counts", sorted(starts_h.items()), " end local hour", sorted(ends_h.items()))
    per_day = collections.Counter(local_date(r["end"], r.get("endZoneOffsetS")) for r in st)
    print("records per wake date:", dict(collections.Counter(per_day.values())))
    span = (max(per_day) - min(per_day)).days + 1
    print("days span=%d days with record=%d" % (span, len(per_day)))
    # overlap with importer main nights
    ov = []
    for r in st:
        best = 0
        for s, e, _ in main[IMP]:
            o = min(e, r["end"]) - max(s, r["start"])
            if o > 0:
                best = max(best, o / (r["end"] - r["start"]))
        ov.append(best)
    ov = np.array(ov)
    print("fraction of record inside an importer main night:", pct(ov), " records with zero overlap=%d" % np.sum(ov == 0))
    mean_delta = [np.mean([x[1] for x in r["deltas"]]) for r in st if r.get("deltas")]
    print("nightly mean delta: robust SD=%.3g IQR=%.3g p95-p5=%.3g" % (
        rsd(mean_delta), np.percentile(mean_delta, 75) - np.percentile(mean_delta, 25),
        np.percentile(mean_delta, 95) - np.percentile(mean_delta, 5)))
    print("  |median nightly mean delta| (is delta centred on baseline?) =%.3g" % abs(np.median(mean_delta)))
    win = [rsd([x[1] for x in r["deltas"]]) for r in st if r.get("deltas") and len(r["deltas"]) >= 3]
    print("within-record robust SD of deltas:", pct(win))
    lag = [(r["lastModifiedMs"] - r["end"]) / 3.6e6 for r in st]
    print("lastModified - end (h):", pct(lag, (5, 50, 95)))

for name in ("hrv_rmssd", "body_temperature"):
    rs = load(name)
    print("\n%s in dataset: %d records" % (name, len(rs)))
for run in sorted(os.listdir(os.path.join(DATA, "hc-dumps"))):
    m = json.load(open(os.path.join(DATA, "hc-dumps", run, "manifest.json")))
    ty = m.get("types", {})
    print("run", run, {k: (ty.get(k, {}).get("status"), ty.get(k, {}).get("count")) for k in ("hrv_rmssd", "body_temperature", "skin_temperature")},
          "perm HRV", any("HEART_RATE_VARIABILITY" in p for p in m.get("grantedPermissions", [])),
          "perm BODY_TEMP", any("BODY_TEMPERATURE" in p for p in m.get("grantedPermissions", [])),
          "perm SKIN_TEMP", any("SKIN_TEMPERATURE" in p for p in m.get("grantedPermissions", [])),
          "skinFeature", m.get("skinTemperatureFeatureAvailable"))
