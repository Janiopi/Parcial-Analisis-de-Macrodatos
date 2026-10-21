#!/usr/bin/env python3
"""Build a comparison table from benchmark.sh and optional worker samples."""
import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("bench")
output = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "comparison.csv"
records = defaultdict(list)
for mode in ("single", "cluster"):
    timing = root / mode / "timing.tsv"
    if not timing.exists():
        continue
    with timing.open() as stream:
        for item in csv.DictReader(stream, delimiter="\t"):
            query, run = item["query"], item["repetition"]
            samples_by_host = defaultdict(list)
            for path in (root / mode).glob(f"{query}-{run}-*.tsv"):
                with path.open() as handle:
                    for sample in csv.DictReader(handle, delimiter="\t"):
                        samples_by_host[sample["host"]].append(sample)
            cpu = statistics.mean(
                statistics.mean(float(x["cpu_pct"]) for x in rows)
                for rows in samples_by_host.values()
            ) if samples_by_host else float("nan")
            memory = statistics.mean(
                statistics.mean(float(x["memory_used_mb"]) for x in rows)
                for rows in samples_by_host.values()
            ) if samples_by_host else float("nan")
            memory_total = sum(
                statistics.mean(float(x["memory_used_mb"]) for x in rows)
                for rows in samples_by_host.values()
            ) if samples_by_host else float("nan")
            records[(mode, query)].append((float(item["seconds"]), cpu, memory, len(samples_by_host), memory_total))

output.parent.mkdir(parents=True, exist_ok=True)
with output.open("w", newline="") as stream:
    writer = csv.writer(stream)
    writer.writerow(["consulta", "n_single", "nodos_muestreados_single", "segundos_single", "cpu_single_pct", "memoria_single_mb", "memoria_total_single_mb", "n_cluster", "nodos_muestreados_cluster", "segundos_cluster", "cpu_cluster_pct", "memoria_cluster_mb", "memoria_total_cluster_mb", "speedup"])
    for query in ("duration", "interruption", "dbscan", "mlp-class", "mlp-reg"):
        s, m = records.get(("single", query), []), records.get(("cluster", query), [])
        def avg(rows, index):
            values = [r[index] for r in rows]
            return statistics.mean(values) if values else float("nan")
        t1 = statistics.median(r[0] for r in s) if s else float("nan")
        t2 = statistics.median(r[0] for r in m) if m else float("nan")
        writer.writerow([query, len(s), min((r[3] for r in s), default=0), t1, avg(s, 1), avg(s, 2), avg(s, 4), len(m), min((r[3] for r in m), default=0), t2, avg(m, 1), avg(m, 2), avg(m, 4), t1 / t2 if s and m and t2 else float("nan")])
print(output)
