#!/usr/bin/env python3
"""Merges Health Connect dumps (tools/hc-exporter) into one dataset and prints summaries without raw values.

Layout (all under the git-ignored .data/, see docs/DATASET.md):
  hc-dumps/<runId>/{manifest.json,<type>.jsonl}  one folder per export run, never modified
  dataset/<type>.jsonl                           latest version of each record id across all runs
  dataset/observations.jsonl                     {type,id,lastModifiedMs,firstSeenRun,firstSeenAtMs} per version
  dataset/runs.json                              run ids with start/finish times, merged in order

Usage:
  hc-dataset.py merge --data .data      rebuild dataset/ from every dump (idempotent; a new run only adds)
  hc-dataset.py summary --data .data    counts per type and origin, first/last day (no values)
"""
import argparse
import datetime as dt
import json
import os
import sys


def load_runs(dumps):
    runs = []
    for name in sorted(os.listdir(dumps)):
        m = os.path.join(dumps, name, "manifest.json")
        if os.path.isfile(m):
            with open(m) as f:
                runs.append(json.load(f))
    return runs


def merge(data):
    dumps = os.path.join(data, "hc-dumps")
    out = os.path.join(data, "dataset")
    os.makedirs(out, exist_ok=True)
    runs = load_runs(dumps)
    if not runs:
        sys.exit("no dumps in " + dumps)
    latest = {}      # (type, id) -> record
    observed = {}    # (type, id, lastModifiedMs) -> (runId, startedAtMs)
    for run in runs:
        rid = run["runId"]
        for t, info in run["types"].items():
            if info.get("status") != "OK":
                continue
            with open(os.path.join(dumps, rid, t + ".jsonl")) as f:
                for line in f:
                    r = json.loads(line)
                    key = (t, r["id"])
                    prev = latest.get(key)
                    if prev is None or r["lastModifiedMs"] >= prev["lastModifiedMs"]:
                        latest[key] = r
                    observed.setdefault((t, r["id"], r["lastModifiedMs"]), (rid, run["startedAtMs"]))
    by_type = {}
    for (t, _), r in latest.items():
        by_type.setdefault(t, []).append(r)
    for t, rows in by_type.items():
        rows.sort(key=lambda r: (r.get("time", r.get("start", 0)), r["id"]))
        with open(os.path.join(out, t + ".jsonl"), "w") as f:
            for r in rows:
                f.write(json.dumps(r, separators=(",", ":")) + "\n")
    with open(os.path.join(out, "observations.jsonl"), "w") as f:
        for (t, i, lm), (rid, at) in sorted(observed.items(), key=lambda kv: (kv[1][1], kv[0])):
            f.write(json.dumps({"type": t, "id": i, "lastModifiedMs": lm, "firstSeenRun": rid, "firstSeenAtMs": at}) + "\n")
    with open(os.path.join(out, "runs.json"), "w") as f:
        json.dump([{k: run[k] for k in ("runId", "startedAtMs", "finishedAtMs", "zoneId")} for run in runs], f, indent=1)
    print(f"merged {len(runs)} run(s), {len(latest)} records")
    summary(data, runs[-1])


def day(ms):
    return dt.datetime.fromtimestamp(ms / 1000, dt.timezone.utc).strftime("%Y-%m-%d")


def summary(data, last=None):
    if last is None:
        last = load_runs(os.path.join(data, "hc-dumps"))[-1]
    print(f"last run {last['runId']}: device {last['device']['manufacturer']} {last['device']['model']}, "
          f"Android {last['device']['release']} (SDK {last['device']['sdkInt']}), history feature "
          f"{last['historyFeatureAvailable']}, history permission "
          f"{'android.permission.health.READ_HEALTH_DATA_HISTORY' in last['grantedPermissions']}")
    out = os.path.join(data, "dataset")
    for t, info in sorted(last["types"].items()):
        path = os.path.join(out, t + ".jsonl")
        n, origins, first, lastd = 0, {}, None, None
        if os.path.exists(path):
            with open(path) as f:
                for line in f:
                    r = json.loads(line)
                    n += 1
                    origins[r["origin"]] = origins.get(r["origin"], 0) + 1
                    ts = r.get("time", r.get("start"))
                    first = ts if first is None else min(first, ts)
                    lastd = ts if lastd is None else max(lastd, ts)
        span = f"{day(first)}..{day(lastd)}" if n else "-"
        print(f"  {t:20s} {info.get('status'):18s} dataset={n:8d} {span:23s} {origins}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["merge", "summary"])
    ap.add_argument("--data", required=True)
    a = ap.parse_args()
    merge(a.data) if a.cmd == "merge" else summary(a.data)


if __name__ == "__main__":
    main()
