#!/usr/bin/env python3
"""Publication delay and upload behaviour of OHealth (and PulseStages) from metadata.lastModifiedMs.

Prints aggregates only: counts, shares, percentiles of delays in minutes, local hours of writes. No health values,
no dates. See docs/research (latency) for the interpretation.

  mise exec -- python -I tools/research/latency.py .data

Definitions
  pull       a cluster of OHealth lastModifiedMs values over all types with gaps <= 5 min (one sync from the watch)
  main night per local wake date, the longest OHealth sleep session of >= 3 h; nights that ended before the first
             OHealth write after the initial backfill are skipped (their records were written by the backfill)
"""
import collections
import datetime as dt
import json
import os
import sys
import zoneinfo

import numpy as np

DATA = sys.argv[1]
DS = os.path.join(DATA, "dataset")
OH = "com.heytap.health.international"
PS = "io.github.alien2003.pulsestages"
GAP = 5 * 60000
MIN = 60000
with open(os.path.join(DS, "runs.json")) as f:
    runs = json.load(f)
TZ = zoneinfo.ZoneInfo(runs[-1]["zoneId"])
EXPORT_AT = runs[-1]["startedAtMs"]


def load(t, origin=None):
    out = []
    with open(os.path.join(DS, t + ".jsonl")) as f:
        for line in f:
            r = json.loads(line)
            if origin is None or r["origin"] == origin:
                out.append(r)
    return out


def q(a, ps=(5, 25, 50, 75, 95)):
    a = np.asarray(a, dtype=float)
    if a.size == 0:
        return "n=0"
    return f"n={a.size} " + " ".join(f"p{p}={np.percentile(a, p):.0f}" for p in ps)


def share(cond):
    cond = np.asarray(cond, dtype=bool)
    return f"{cond.sum()}/{cond.size} ({100 * cond.mean():.0f}%)" if cond.size else "0/0"


def clusters(ts, gap=GAP):
    ts = sorted(set(ts))
    out = []
    for t in ts:
        if out and t - out[-1][1] <= gap:
            out[-1][1] = t
        else:
            out.append([t, t])
    return out


def local(ms):
    return dt.datetime.fromtimestamp(ms / 1000, TZ)


def hour(ms):
    d = local(ms)
    return d.hour + d.minute / 60


types = ["sleep", "heart_rate", "spo2", "respiratory_rate", "steps", "resting_heart_rate"]
oh = {t: load(t, OH) for t in types}
ps_sleep = load("sleep", PS)

# ---- pulls -------------------------------------------------------------------------------------------------------
all_lm = [r["lastModifiedMs"] for t in types for r in oh[t]]
pulls = clusters(all_lm)
backfill_end = pulls[0][1]
pulls = pulls[1:]  # the first cluster is the initial backfill of the history
pull_starts = np.array([p[0] for p in pulls])
print(f"OHealth pulls (lastModified clusters, gap 5 min) after the initial backfill: {len(pulls)}")
days = (pulls[-1][0] - pulls[0][0]) / 86400000
print(f"  over {days:.0f} days, {len(pulls) / days:.1f} pulls/day; cluster length {q([(b - a) / MIN for a, b in pulls])}")
hist = collections.Counter(int(hour(p[0])) for p in pulls)
print("  local hour of pull start:", " ".join(f"{h:02d}:{hist.get(h, 0)}" for h in range(24)))
per_type = {}
for t in types:
    c = clusters([r["lastModifiedMs"] for r in oh[t] if r["lastModifiedMs"] > backfill_end])
    per_type[t] = c
    hh = collections.Counter(int(hour(a)) for a, _ in c)
    print(f"  {t:18s} write clusters={len(c):4d}  by hour: " + " ".join(f"{h:02d}:{hh[h]}" for h in range(24) if hh[h]))
# the daily morning pull: per local date, the first pull between 05:00 and 11:00
morning = {}
for a, _ in pulls:
    d = local(a)
    if 5 <= d.hour < 11:
        morning.setdefault(d.date(), a)
mh = [hour(v) for v in morning.values()]


def hhmm(h):
    return f"{int(h):02d}:{int(round((h % 1) * 60)):02d}"


print(f"  dates with a pull 05-11h: {len(morning)}; first such pull, local time: "
      + " ".join(f"p{p}={hhmm(np.percentile(mh, p))}" for p in (5, 25, 50, 75, 95)))
print(f"  morning pull local time spread: IQR {np.percentile(mh, 75) - np.percentile(mh, 25):.2f} h, "
      f"share within +-5 min of the median: {share(np.abs(np.array(mh) - np.median(mh)) * 60 <= 5)}")

# ---- rewrites: does a record's lastModified belong to a later day than its time? ----------------------------------
print("\nRecords whose lastModified is on a later local date than their (last) sample time, after backfill:")
for t in ("heart_rate", "spo2", "respiratory_rate", "steps", "sleep", "resting_heart_rate"):
    rows = [r for r in oh[t] if r["lastModifiedMs"] > backfill_end]
    def tt(r):
        if "samples" in r:
            return r["samples"][-1][0]
        return r.get("end", r.get("time"))
    later = [local(r["lastModifiedMs"]).date() > local(tt(r)).date() for r in rows]
    print(f"  {t:18s} {share(later)}")
# HR: per local calendar date of samples, how many write clusters touch it, and where is the last one
hr_by_date = collections.defaultdict(list)
for r in oh["heart_rate"]:
    if r["lastModifiedMs"] > backfill_end:
        hr_by_date[local(r["samples"][0][0]).date()].append(r["lastModifiedMs"])
nxt = []
for d, lms in hr_by_date.items():
    lm = max(lms)
    nxt.append((local(lm).date() - d).days)
print(f"  heart_rate: local sample dates={len(hr_by_date)}; final write on the next date: {share(np.array(nxt) == 1)}, "
      f"same date: {share(np.array(nxt) == 0)}")
# sample density of HR written by morning rewrite vs other batches
rw, other = [], []
for r in oh["heart_rate"]:
    if r["lastModifiedMs"] <= backfill_end:
        continue
    (rw if local(r["lastModifiedMs"]).date() > local(r["samples"][0][0]).date() else other).append(r["samples"][0][0])
for name, v in (("rewritten next day", rw), ("same-day writes", other)):
    v = np.sort(np.array(v))
    print(f"  heart_rate {name}: samples={v.size}, gap between samples (s) {q(np.diff(v) / 1000)}")

# ---- main nights --------------------------------------------------------------------------------------------------
by_wake = {}
for r in oh["sleep"]:
    if r["end"] - r["start"] < 3 * 3600000 or r["end"] <= backfill_end:
        continue
    d = local(r["end"]).date()
    if d not in by_wake or r["end"] - r["start"] > by_wake[d]["end"] - by_wake[d]["start"]:
        by_wake[d] = r
nights = sorted(by_wake.values(), key=lambda r: r["start"])
naps = [r for r in oh["sleep"] if r["end"] > backfill_end and r not in nights]
print(f"\nMain nights (OHealth, >= 3 h, after backfill): {len(nights)}; other OHealth sessions: {len(naps)}")
print(f"  local wake hour {q([hour(r['end']) for r in nights])}")

sess_delay = [(r["lastModifiedMs"] - r["end"]) / MIN for r in nights]
print(f"  session lastModified - end (min): {q(sess_delay)}")
print(f"  session lastModified on a later local date than its end: "
      f"{share([local(r['lastModifiedMs']).date() > local(r['end']).date() for r in nights])}")
print(f"  session lastModified > end + 2 h: {share(np.array(sess_delay) > 120)}; > 12 h: {share(np.array(sess_delay) > 720)}")
print(f"  session lastModified local hour: {q([hour(r['lastModifiedMs']) for r in nights])}")
print(f"  other sessions lastModified - end (min): {q([(r['lastModifiedMs'] - r['end']) / MIN for r in naps])}")
mid = [r for r in nights if local(r['lastModifiedMs']).date() == local(r['end']).date()]
print(f"  nights whose session was not rewritten on a later date (= latest night(s) only): {len(mid)}; "
      f"their lastModified - end (min): {q([(r['lastModifiedMs'] - r['end']) / MIN for r in mid], (50,))}")


def samples(t, a, b):
    out = []
    for r in oh[t]:
        if "samples" in r:
            out.extend((s[0], r["lastModifiedMs"]) for s in r["samples"] if a <= s[0] <= b)
        elif a <= r["time"] <= b:
            out.append((r["time"], r["lastModifiedMs"]))
    return out


idx = {t: sorted(((s[0] if "samples" in r else r["time"]), r["lastModifiedMs"])
                 for r in oh[t] for s in (r["samples"] if "samples" in r else [None]))
       for t in ("heart_rate", "spo2", "respiratory_rate")}
idx_t = {t: np.array([x[0] for x in v]) for t, v in idx.items()}
idx_lm = {t: np.array([x[1] for x in v]) for t, v in idx.items()}


def in_night(t, a, b):
    i, j = np.searchsorted(idx_t[t], a), np.searchsorted(idx_t[t], b, side="right")
    return idx_t[t][i:j], idx_lm[t][i:j]


print("\nPer main night, samples inside [start, end]:")
first_after = []
stats = collections.defaultdict(lambda: collections.defaultdict(list))
for r in nights:
    S, E = r["start"], r["end"]
    k = np.searchsorted(pull_starts, E)
    fa = pull_starts[k] if k < len(pull_starts) else None
    first_after.append(fa)
    for t in ("heart_rate", "spo2", "respiratory_rate"):
        ts, lms = in_night(t, S, E)
        st = stats[t]
        st["count"].append(ts.size)
        if ts.size == 0:
            continue
        st["delay"].extend(((lms - ts) / MIN).tolist())
        st["batches"].append(len(clusters(lms.tolist())))
        st["complete_after_end"].append((lms.max() - E) / MIN)
        if fa is not None:
            st["after_first_pull"].append(np.mean(lms > fa + GAP))
            st["any_after_first_pull"].append(bool((lms > fa + GAP).any()))
            st["last_after_first_pull"].append((lms.max() - fa) / MIN)
        st["next_date"].append(np.mean([local(x).date() > local(E).date() for x in lms]))
for t, st in stats.items():
    print(f"  {t}: nights with samples {share(np.array(st['count']) > 0)}")
    print(f"    lastModified - sample time (min): {q(st['delay'], (5, 25, 50, 75, 95))}")
    print(f"    distinct write clusters per night: {q(st['batches'], (5, 25, 50, 75, 95))}; "
          f">=2 clusters: {share(np.array(st['batches']) >= 2)}")
    print(f"    last in-night write - session end (min): {q(st['complete_after_end'])}")
    print(f"    nights with any in-night sample written > 5 min after the first pull after the end: "
          f"{share(st['any_after_first_pull'])}; last write - that pull (min) {q(st['last_after_first_pull'])}")
    print(f"    mean share of in-night samples last written on a later date than the wake: "
          f"{np.mean(st['next_date']):.2f}")

fa = np.array([(f - r["end"]) / MIN for r, f in zip(nights, first_after) if f is not None])
print(f"\nFirst OHealth pull after the session end - end (min): {q(fa)}")
print(f"  within 15 min: {share(fa <= 15)}; within 60 min: {share(fa <= 60)}")
# pulls during the night (each one most likely published a provisional session cut at the pull)
during, rem, gap_next = [], [], []
for r in nights:
    S, E = r["start"], r["end"]
    inside = [p for p in pull_starts if S + 30 * MIN < p < E - 2 * MIN]
    during.append(len(inside))
    if inside:
        p = inside[-1]
        rem.append((E - p) / MIN)
        k = np.searchsorted(pull_starts, p, side="right")
        gap_next.append((pull_starts[k] - p) / MIN if k < len(pull_starts) else np.inf)
during = np.array(during)
print(f"Pulls during sleep (start+30 min .. end-2 min): nights with >=1 {share(during >= 1)}, >=2 {share(during >= 2)}")
print(f"  sleep remaining after the last in-sleep pull (min): {q(rem)}")
print(f"  gap from that pull to the next pull (min): {q([g for g in gap_next if np.isfinite(g)])}")

# first pull at or after end + 60 min (PulseStages' heart-rate margin) and with a walk (>=5 steps/min) after it
steps = sorted((r["start"], r["end"], r["value"], r["lastModifiedMs"]) for r in oh["steps"])
st_start = np.array([s[0] for s in steps])
w60, walk = [], []
for r in nights:
    E = r["end"]
    k = np.searchsorted(pull_starts, E + 60 * MIN)
    if k < len(pull_starts):
        w60.append((pull_starts[k] - E) / MIN)
    i = np.searchsorted(st_start, E + 60 * MIN)
    cand = [s[3] for s in steps[i:i + 3000] if s[2] / max(1, (s[1] - s[0]) / MIN) >= 5 and s[0] < E + 24 * 3600000]
    if cand:
        walk.append((min(cand) - E) / MIN)
print(f"First pull >= end + 60 min, - end (min): {q(w60)}")
print(f"First write of a >=5 steps/min record starting >= end + 60 min, - end (min): {q(walk)}")

# ---- rule simulation -----------------------------------------------------------------------------------------------
# Model (hypothesis, from PulseStages' log): every pull while asleep publishes a provisional session ending at the
# pull, with heart rate up to the pull; the first pull after the real end publishes the final version. SpO2/RR are
# taken with their real write times (they are not rewritten). A rule is checked every 15 min.
print("\nRule simulation (checks every 15 min, model in the script header):")


def simulate(X, Y, M, Z, need_walk=False):
    fired, premature_sess, premature_any, delays = 0, 0, 0, []
    for r in nights:
        S, E = r["start"], r["end"]
        ps_ = [p for p in pull_starts if S + 30 * MIN < p <= E + 36 * 3600000]
        if not ps_:
            continue
        sp_t, sp_lm = in_night("spo2", S, E)
        rr_t, rr_lm = in_night("respiratory_rate", S, E)
        last_in = max([sp_lm.max() if sp_lm.size else 0, rr_lm.max() if rr_lm.size else 0])
        t = ps_[0]
        end_t = ps_[-1] + 36 * 3600000
        while t < end_t:
            seen = [p for p in ps_ if p <= t]
            if not seen:
                t += 15 * MIN
                continue
            # visible session end: the last in-sleep pull, or the real end once a pull after it happened
            after = [p for p in seen if p >= E - 2 * MIN]
            ev = E if after else seen[-1]
            last_pull = seen[-1]
            hr_to = min(last_pull, E + 36 * 3600000) if after else ev
            ok = t >= ev + X * MIN and t - last_pull >= Y * MIN and (hr_to >= ev + M * MIN)
            if need_walk:
                w = [s for s in steps[np.searchsorted(st_start, ev + M * MIN):][:3000]
                     if s[3] <= t and s[2] / max(1, (s[1] - s[0]) / MIN) >= 5]
                ok = ok and bool(w)
            if ok or t >= ev + Z * 3600000:
                fired += 1
                bad = ev < E - 2 * MIN
                premature_sess += bad
                premature_any += bad or last_in > t
                delays.append((t - E) / MIN)
                break
            t += 15 * MIN
    return fired, premature_sess, premature_any, delays


for X, Y, M, Z, walk_ in [(30, 30, 0, 12, False), (60, 30, 0, 12, False), (60, 60, 0, 12, False),
                          (90, 60, 0, 12, False), (120, 60, 0, 12, False),
                          (0, 15, 60, 12, False), (0, 15, 60, 12, True), (0, 30, 60, 6, False)]:
    f, ps1, ps2, d = simulate(X, Y, M, Z, walk_)
    d = np.array(d)
    print(f"  ended>={X:3d}m ago, quiet>={Y:2d}m, HR to end+{M:2d}m, walk={str(walk_)[0]}, max {Z:2d}h: fired {f}, "
          f"premature (session not final) {ps1}, premature (session or SpO2/RR) {ps2}; delay after real end (min) "
          f"{q(d, (25, 50, 75, 95))}")


# ---- where were in-night SpO2 / RR samples written ------------------------------------------------------------------
print("\nIn-night SpO2/RR samples by the pull that last wrote them (share of samples, all main nights pooled):")
for t in ("spo2", "respiratory_rate"):
    cat = collections.Counter()
    for r, fa_ in zip(nights, first_after):
        ts, lms = in_night(t, r["start"], r["end"])
        for lm in lms:
            if lm < r["end"] - 2 * MIN:
                cat["during sleep"] += 1
            elif fa_ is not None and lm <= fa_ + GAP:
                cat["first pull after end"] += 1
            elif local(lm).date() == local(r["end"]).date():
                cat["later pull, wake date"] += 1
            else:
                cat["later date"] += 1
    n = sum(cat.values())
    print(f"  {t}: " + ", ".join(f"{k} {100 * v / n:.0f}%" for k, v in cat.most_common()))
    # samples written after the first pull after the end: how close to the end of the night they are
    late_pos, late_lag = [], []
    for r, fa_ in zip(nights, first_after):
        if fa_ is None:
            continue
        ts, lms = in_night(t, r["start"], r["end"])
        m = lms > fa_ + GAP
        late_pos.extend(((r["end"] - ts[m]) / MIN).tolist())
        late_lag.extend(((lms[m] - ts[m]) / MIN).tolist())
    lp = np.array(late_pos)
    print(f"    late samples: {lp.size}; minutes before the session end {q(lp)}; within the last 30 min: "
          f"{share(lp <= 30)}; their write - sample time (min) {q(late_lag)}")
    # write lag of the newest sample in each write cluster (how far behind real time a pull's data is)
    lagn = []
    for a, b in per_type[t]:
        sel = [x for x, y in zip(idx_t[t], idx_lm[t]) if a <= y <= b]
        if sel:
            lagn.append((a - max(sel)) / MIN)
    print(f"    per write cluster, write - newest sample (min): {q(lagn)}")
mp = np.median(mh)
e_rel = np.array([(hour(r["end"]) - mp) * 60 for r in nights])
print(f"Session end relative to the median morning-pull time (min): {q(e_rel)}; "
      f"within +-10 min: {share(np.abs(e_rel) <= 10)}")
gap_end = np.array([(f - r["end"]) / MIN for r, f in zip(nights, first_after) if f is not None])
print(f"Nights whose end is 0-3 min before a pull (end looks like a cut at the pull): {share((gap_end >= 0) & (gap_end <= 3))}")
# steps written the same day as they happened, after the wake
post = [s for s in steps for r in nights[:0]]
same, tot = 0, 0
for r in nights:
    i, j = np.searchsorted(st_start, r["end"]), np.searchsorted(st_start, r["end"] + 6 * 3600000)
    for s in steps[i:j]:
        tot += 1
        same += local(s[3]).date() == local(s[0]).date()
print(f"Step records in the 6 h after a wake whose final write is on the same date: {same}/{tot} "
      f"({100 * same / max(1, tot):.0f}%)")


def simulate2(rule, Z=12):
    """rule(ev, t, last_pull, after_final, walk_written) -> bool. Returns premature counts and missing shares."""
    out = collections.defaultdict(list)
    for r in nights:
        S, E = r["start"], r["end"]
        ps_ = [p for p in pull_starts if S + 30 * MIN < p <= E + 36 * 3600000]
        if not ps_:
            continue
        t = ps_[0]
        while True:
            seen = [p for p in ps_ if p <= t]
            after = [p for p in seen if p >= E - 2 * MIN]
            ev = E if after else seen[-1]
            i = np.searchsorted(st_start, ev + 2 * MIN)
            walk_w = [s for s in steps[i:i + 3000] if s[3] <= t and s[0] < ev + 6 * 3600000
                      and s[2] / max(1, (s[1] - s[0]) / MIN) >= 5]
            if rule(ev, t, seen[-1], bool(after), walk_w) or t >= ev + Z * 3600000:
                out["premature"].append(ev < E - 2 * MIN)
                out["delay"].append((t - E) / MIN)
                for m in ("spo2", "respiratory_rate"):
                    ts, lms = in_night(m, S, E)
                    out[m].append(float(np.mean(lms > t)) if lms.size else 0.0)
                break
            t += 15 * MIN
    return out


print("\nRule simulation 2 (adds: share of in-night SpO2/RR samples not yet written when the rule fires):")
rules = {
    "A ended>=60m, quiet>=30m": lambda ev, t, lp, af, w: t >= ev + 60 * MIN and t - lp >= 30 * MIN,
    "B ended>=120m, quiet>=60m": lambda ev, t, lp, af, w: t >= ev + 120 * MIN and t - lp >= 60 * MIN,
    "C walk (>=5 steps/min) after visible end written, quiet>=15m":
        lambda ev, t, lp, af, w: bool(w) and t - lp >= 15 * MIN,
    "D walk >= end+60m written (PulseStages-like), quiet>=15m":
        lambda ev, t, lp, af, w: any(s[0] >= ev + 60 * MIN for s in w) and t - lp >= 15 * MIN,
    "E C or (ended>=180m and quiet>=60m)":
        lambda ev, t, lp, af, w: (bool(w) and t - lp >= 15 * MIN) or (t >= ev + 180 * MIN and t - lp >= 60 * MIN),
}
for name, rule in rules.items():
    for Z in (6, 12):
        o = simulate2(rule, Z)
        sp, rr = np.array(o["spo2"]), np.array(o["respiratory_rate"])
        print(f"  {name}, max {Z}h: premature session {share(o['premature'])}; delay (min) {q(o['delay'], (25, 50, 75, 95))}; "
              f"nights missing SpO2 {share(sp > 0)} (mean missing {100 * sp.mean():.0f}%), "
              f"RR {share(rr > 0)} (mean missing {100 * rr.mean():.0f}%)")

# ---- PulseStages -----------------------------------------------------------------------------------------------------
print(f"\nPulseStages sessions: {len(ps_sleep)}")
pd = [(r["lastModifiedMs"] - r["end"]) / MIN for r in ps_sleep]
print(f"  lastModified - end (min): {q(pd)}; > 12 h: {share(np.array(pd) > 720)}")
match, react, vs_oh = [], [], []
for r in ps_sleep:
    ov = [n for n in nights if min(n["end"], r["end"]) - max(n["start"], r["start"]) > 0]
    if ov:
        n = max(ov, key=lambda n: min(n["end"], r["end"]) - max(n["start"], r["start"]))
        match.append(abs(r["end"] - n["end"]) / MIN)
        vs_oh.append((r["lastModifiedMs"] - n["lastModifiedMs"]) / MIN)
    k = np.searchsorted(pull_starts, r["lastModifiedMs"], side="right") - 1
    if k >= 0:
        react.append((r["lastModifiedMs"] - pull_starts[k]) / MIN)
print(f"  matched to an OHealth main night: {len(match)}; |end difference| (min) {q(match)}")
print(f"  PulseStages lastModified - matched OHealth session lastModified (min): {q(vs_oh)}")
print(f"  PulseStages lastModified - latest OHealth pull before it (min): {q(react)}")
print(f"  local hour of PulseStages writes: {q([hour(r['lastModifiedMs']) for r in ps_sleep])}")
