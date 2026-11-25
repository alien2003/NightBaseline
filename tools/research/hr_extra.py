#!/usr/bin/env python3
"""Follow-ups to hr_night.py: session fragments around the main night, short-interval samples outside sleep,
publication delay of a night's HR, continuous candidates (IQM, low-half mean) with within-night bootstrap noise,
effect of merging fragments, provisional resting HR. Aggregates only.

  python3 -I tools/research/hr_extra.py .data
"""
import collections
import datetime as dt
import json
import os
import sys

import numpy as np
import pandas as pd

OH = "com.heytap.health.international"
data = sys.argv[1]
ds = os.path.join(data, "dataset")
rng = np.random.default_rng(2)


def load(name):
    with open(os.path.join(ds, name + ".jsonl")) as f:
        return [json.loads(l) for l in f]


def q(a, ps=(0, 5, 25, 50, 75, 95, 100)):
    a = np.asarray(a, float)
    a = a[~np.isnan(a)]
    return (" ".join(f"p{p}={np.percentile(a, p):.3g}" for p in ps) + f" (n={len(a)})") if len(a) else "empty"


def rsd(a):
    a = np.asarray(a, float)
    a = a[~np.isnan(a)]
    return 1.4826 * np.median(np.abs(a - np.median(a)))


def local(ms, off):
    return dt.datetime.utcfromtimestamp(ms / 1000 + off)


hr = [r for r in load("heart_rate") if r["origin"] == OH]
single = [r for r in hr if len(r["samples"]) == 1]
dense = [r for r in hr if len(r["samples"]) > 1]
bgrec = sorted(single, key=lambda r: r["start"])
bg_t = np.array([r["start"] for r in bgrec])
bg_v = np.array([r["samples"][0][1] for r in bgrec], float)
bg_lm = np.array([r["lastModifiedMs"] for r in bgrec])
o = np.argsort(bg_t, kind="stable")
bg_t, bg_v, bg_lm = bg_t[o], bg_v[o], bg_lm[o]
ex = [r for r in load("exercise") if r["origin"] == OH]
sl = sorted([r for r in load("sleep") if r["origin"] == OH], key=lambda r: r["start"])
for r in sl:
    r["dur_h"] = (r["end"] - r["start"]) / 3600e3
    r["wake_date"] = (local(r["start"], r["startZoneOffsetS"]) + dt.timedelta(hours=12)).date()


def win(a, b):
    return np.searchsorted(bg_t, a), np.searchsorted(bg_t, b, side="right")


# short intervals outside sleep: where are they?
d = np.diff(bg_t)
short = d < 59e3
in_ex = np.zeros(len(bg_t), bool)
for e in ex:
    i, j = win(e["start"], e["end"])
    in_ex[i:j] = True
in_dense = np.isin(bg_t, [s[0] for r in dense for s in r["samples"]])
print("background intervals <59 s:", int(short.sum()), "; with both ends inside an OHealth exercise session:",
      int((short & in_ex[:-1] & in_ex[1:]).sum()), "; with an end time duplicated in a 1 Hz record:",
      int((short & (in_dense[:-1] | in_dense[1:])).sum()))
print("background samples inside exercise sessions:", int(in_ex.sum()), "; exercise duration h:", q([(e['end'] - e['start']) / 3600e3 for e in ex]))
nonex = short & ~in_ex[:-1] & ~in_ex[1:]
print("short intervals outside exercise, interval s:", q(d[nonex] / 1000))
print("background exact duplicate times:", int((d == 0).sum()))

# main nights and fragments
by = collections.defaultdict(list)
for r in sl:
    by[r["wake_date"]].append(r)
main = []
for k, rs in by.items():
    L = [r for r in rs if r["dur_h"] >= 3]
    if L:
        main.append(max(L, key=lambda r: r["dur_h"]))
main.sort(key=lambda r: r["start"])
others = [r for r in sl if r not in main]
frag_before, frag_after, naps = [], [], []
for r in others:
    m = min(main, key=lambda m: min(abs(r["start"] - m["end"]), abs(m["start"] - r["end"])))
    if 0 <= m["start"] - r["end"] <= 90 * 60e3:
        frag_before.append((r, m))
    elif 0 <= r["start"] - m["end"] <= 90 * 60e3:
        frag_after.append((r, m))
    else:
        naps.append(r)
print(f"\nother sessions: within 90 min before a main night {len(frag_before)}, within 90 min after {len(frag_after)}, "
      f"separate (naps) {len(naps)}")
print("fragment gap min:", q([(m['start'] - r['end']) / 60e3 for r, m in frag_before] + [(r['start'] - m['end']) / 60e3 for r, m in frag_after]))
print("fragment duration h:", q([r["dur_h"] for r, _ in frag_before + frag_after]))
print("nap duration h:", q([r["dur_h"] for r in naps]), "; nap start local hour:",
      sorted(collections.Counter(local(r["start"], r["startZoneOffsetS"]).hour for r in naps).items()))
print("main nights with >=1 adjacent fragment:", len(set(id(m) for _, m in frag_before + frag_after)), "/", len(main))
# fragment HR relative to its main night
rel = []
for r, m in frag_before + frag_after:
    i, j = win(r["start"], r["end"])
    a, b = win(m["start"], m["end"])
    if j - i >= 3:
        rel.append(np.median(bg_v[i:j]) - np.median(bg_v[a:b]))
print("fragment median HR minus main night median:", q(rel))
# HR in the gap between main and fragment (awake interval) relative to main night
relg = []
for r, m in frag_after:
    i, j = win(m["end"], r["start"])
    a, b = win(m["start"], m["end"])
    if j - i >= 3:
        relg.append(np.median(bg_v[i:j]) - np.median(bg_v[a:b]))
print("gap (awake between main and later fragment) median HR minus main night median:", q(relg))

# publication delay of a night's HR
print("\nper main night: last in-session HR sample lastModified - session end (h):",
      q([(bg_lm[slice(*win(m['start'], m['end']))].max() - m['end']) / 3600e3 for m in main]))
print("per main night: sleep session lastModified - session end (h):", q([(m["lastModifiedMs"] - m["end"]) / 3600e3 for m in main]))
print("per main night: HR complete later than session (h):",
      q([(bg_lm[slice(*win(m['start'], m['end']))].max() - m['lastModifiedMs']) / 3600e3 for m in main]))
print("HR lastModified batches: distinct batches touching one main night:", q([len(set(bg_lm[slice(*win(m['start'], m['end']))])) for m in main]))


# continuous candidates + bootstrap within-night noise
def cands(v):
    v = np.sort(v)
    n = len(v)
    return {"median": np.median(v), "mean": v.mean(), "p10": np.percentile(v, 10),
            "iqm": v[int(n * .25):int(np.ceil(n * .75))].mean(), "lowhalf_mean": v[:n // 2].mean(),
            "trim10_mean": v[int(n * .1):int(np.ceil(n * .9))].mean()}


rows, boot = [], collections.defaultdict(list)
merged = []
for m in main:
    i, j = win(m["start"], m["end"])
    v = bg_v[i:j]
    c = cands(v)
    rows.append(c)
    # block bootstrap (30-min blocks) for within-night noise
    t = bg_t[i:j]
    blk = ((t - m["start"]) // 1800e3).astype(int)
    ub = np.unique(blk)
    bs = collections.defaultdict(list)
    for _ in range(200):
        pick = rng.choice(ub, len(ub))
        vv = np.concatenate([v[blk == b] for b in pick])
        for k, x in cands(vv).items():
            bs[k].append(x)
    for k, x in bs.items():
        boot[k].append(np.std(x))
    # merged with fragments
    fr = [r for r, mm in frag_before + frag_after if mm is m]
    if fr:
        vv = np.concatenate([v] + [bg_v[slice(*win(r["start"], r["end"]))] for r in fr])
        merged.append({k: cands(vv)[k] - c[k] for k in c})
df = pd.DataFrame(rows)
print("\ncandidate: night-to-night robustSD | within-night block-bootstrap SE (median over nights) | reliability "
      "1-SE^2/SD^2 | lag1 autocorr | |effect of merging fragments| median")
mg = pd.DataFrame(merged)
for k in df.columns:
    s = df[k].std()
    se = np.median(boot[k])
    print(f"  {k:12s} rSD={rsd(df[k]):.2f} SD={s:.2f} SE={se:.2f} rel={1 - se ** 2 / s ** 2:.2f} "
          f"ac1={df[k].autocorr(1):.2f} merge={mg[k].abs().median():.2f}")
print("corr (Pearson) iqm/median/mean/p10/lowhalf:")
print(df.corr().round(2).to_string())
# night-level shape: within-night robust SD of samples, and time of the lowest 30-min window
wsd, lowpos = [], []
for m in main:
    i, j = win(m["start"], m["end"])
    wsd.append(rsd(bg_v[i:j]))
    s = pd.Series(bg_v[i:j], index=pd.to_datetime(bg_t[i:j], unit="ms")).rolling("30min", min_periods=10).mean()
    s = s[s.index >= pd.to_datetime(m["start"] + 1800e3, unit="ms")]
    if s.notna().any():
        lowpos.append(((s.idxmin().value // 10 ** 6) - 900e3 - m["start"]) / (m["end"] - m["start"]))
print("within-night robust SD of samples (bpm):", q(wsd))
print("relative position of the lowest 30-min window centre in the night (0=start,1=end):", q(lowpos))
# first 30 min vs rest
fr = []
for m in main:
    a, b = win(m["start"], m["start"] + 1800e3)
    c, e = win(m["start"] + 1800e3, m["end"])
    fr.append(np.median(bg_v[a:b]) - np.median(bg_v[c:e]))
print("median of first 30 min minus median of rest (bpm):", q(fr))

# resting HR provisional record
rh = sorted([r for r in load("resting_heart_rate") if r["origin"] == OH], key=lambda r: r["time"])
last = rh[-1]
print("\nlatest RHR record: lastModified - stamp h:", round((last["lastModifiedMs"] - last["time"]) / 3600e3, 1),
      "; records with lastModified before their day ended:", sum(1 for r in rh if r["lastModifiedMs"] - r["time"] < 24 * 3600e3))
lag = [(r["lastModifiedMs"] - r["time"]) / 3600e3 - 24 for r in rh]
print("RHR lastModified after end of its day (h):", q(lag))

# Sleep as Android: HR records vs its sessions, vs OHealth timestamps
SAA = "com.urbandroid.sleep"
allhr = load("heart_rate")
shr = [r for r in allhr if r["origin"] == SAA]
ssl = [r for r in load("sleep") if r["origin"] == SAA]
print("\nSaA HR records:", len(shr), "; overlapping a SaA sleep session:",
      sum(any(s["start"] < r["end"] and r["start"] < s["end"] for s in ssl) for r in shr))
print("SaA HR record span h:", q([(r["end"] - r["start"]) / 3600e3 for r in shr]), "; samples/record:", q([len(r["samples"]) for r in shr]))
print("SaA HR record start local hour:", sorted(collections.Counter(local(r["start"], r["startZoneOffsetS"]).hour for r in shr).items()))
st = np.array(sorted(s[0] for r in shr for s in r["samples"]))
print("SaA HR intra-record interval s:", q(np.concatenate([np.diff([s[0] for s in r["samples"]]) / 1000 for r in shr if len(r["samples"]) > 1])))
other_t = {}
for r in allhr:
    if r["origin"] != SAA:
        for s in r["samples"]:
            other_t[s[0]] = r["origin"]
m = collections.Counter(other_t.get(t, "none") for t in st)
print("SaA sample times that exactly equal a sample time of another origin:", dict(m))
oh_era = st[st >= bg_t[0]]
print("SaA samples in OHealth era:", len(oh_era), "; sample ms-of-second top:",
      collections.Counter(int(t % 1000) for t in oh_era).most_common(3))
