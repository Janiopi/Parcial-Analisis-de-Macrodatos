#!/usr/bin/env python3
"""Independent checks for the five outputs on the supplied MTC snapshot."""
import csv
import datetime as dt
import math
import sys
from collections import defaultdict
from pathlib import Path

if len(sys.argv) != 7:
    raise SystemExit("usage: verify_outputs.py source.csv duration_dir interruption_dir dbscan_dir mlp_class_dir mlp_reg_dir")
source, duration, interruption, dbscan, classification, regression = map(Path, sys.argv[1:])
with source.open(encoding="cp1252", newline="") as stream:
    rows = list(csv.DictReader(stream, delimiter=";"))
assert len(rows) == 1362

def lines(directory):
    for part in sorted(directory.glob("part-*")):
        yield from part.read_text(encoding="utf-8").splitlines()

closed = defaultdict(lambda: [0, 0])
roads = defaultdict(lambda: [0, 0])
for row in rows:
    road = roads[row["RED_AFECTADA"]]
    road[0] += row["ESTADO_INICIAL"] == "INTERRUMPIDO"
    road[1] += 1
    if row["FECHA_CIERRE"]:
        start = dt.datetime.strptime(row["FECHA_OCURRENCIA"], "%d/%m/%Y")
        end = dt.datetime.strptime(row["FECHA_CIERRE"], "%d/%m/%Y")
        if start <= end <= dt.datetime(2026, 8, 20):
            item = closed[row["DEPARTEMENTO"]]
            item[0] += (end - start).days
            item[1] += 1

expected_duration = sorted(((key, n, round(s / n, 4)) for key, (s, n) in closed.items()), key=lambda x: (-x[2], x[0]))[:10]
expected_interruption = sorted(((key, n, round(100 * s / n, 4)) for key, (s, n) in roads.items() if n >= 10), key=lambda x: (-x[2], x[0]))[:10]
for name, path, expected in (("duration", duration, expected_duration), ("interruption", interruption, expected_interruption)):
    actual = []
    for line in lines(path / "result"):
        rank, label, n, value = line.split("\t")
        actual.append((label, int(n), float(value)))
    assert actual == expected, f"{name} differs: {actual} != {expected}"
    print(f"{name}: top 10 match independent aggregation")

ids = [int(row["ID_EMERGENCIA"]) for row in rows]
coords = [(math.radians(float(row["LATITUD"])), math.radians(float(row["LONGITUD"]))) for row in rows]
neighbors = [set() for _ in rows]
for i, (a, b) in enumerate(coords):
    for j in range(i + 1, len(rows)):
        c, d = coords[j]
        h = math.sin((c-a)/2)**2 + math.cos(a)*math.cos(c)*math.sin((d-b)/2)**2
        if 12742.0176 * math.atan2(math.sqrt(h), math.sqrt(max(0, 1-h))) <= 20:
            neighbors[i].add(j)
            neighbors[j].add(i)
cores = {i for i, n in enumerate(neighbors) if len(n) + 1 >= 4}
parent = {i: i for i in cores}
def root(i):
    while parent[i] != i:
        parent[i] = parent[parent[i]]
        i = parent[i]
    return i
for i in cores:
    for j in neighbors[i] & cores:
        a, b = root(i), root(j)
        if a != b:
            parent[max(a, b)] = min(a, b)
cluster_ids = defaultdict(list)
for i in cores:
    cluster_ids[root(i)].append(ids[i])
labels = {i: min(cluster_ids[root(i)]) for i in cores}
expected = {ids[i]: labels[i] if i in cores else min((labels[j] for j in neighbors[i] & cores), default=-1) for i in range(len(rows))}
actual = {}
for line in lines(dbscan / "assignments"):
    point = line.split("\t")
    actual[int(point[0])] = int(point[3])
assert actual == expected, "DBSCAN assignments differ from independent all-pairs reference"
print(f"dbscan: all {len(actual)} assignments match; {len(set(actual.values()) - {-1})} clusters")

for name, path in (("classification", classification), ("regression", regression)):
    epochs = sorted(path.glob("epoch-*"))
    assert epochs, f"{name}: no epochs"
    losses = []
    for epoch in epochs:
        record = next(line for line in lines(epoch) if line.startswith("LOSS\t"))
        losses.append(float(record.split("\t")[2]))
    assert losses[-1] < losses[0], f"{name}: loss did not decrease"
    metrics = list(lines(path / "metrics"))
    assert len(metrics) == 2 and all("loss=" in item for item in metrics)
    print(f"{name}: {len(epochs)} epochs, loss {losses[0]:.6f} -> {losses[-1]:.6f}")
