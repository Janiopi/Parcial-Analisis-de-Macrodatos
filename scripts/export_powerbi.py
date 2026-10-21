#!/usr/bin/env python3
"""Join MapReduce DBSCAN assignments to the source for a Power BI map."""
import csv
import sys
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("usage: export_powerbi.py source.csv assignments_dir output.csv")
source, assignments, output = map(Path, sys.argv[1:])
clusters = {}
for part in assignments.glob("part-*"):
    with part.open(encoding="utf-8") as stream:
        for line in stream:
            row = line.rstrip("\r\n").split("\t")
            if len(row) != 5:
                raise ValueError(f"Malformed assignment: {line!r}")
            if row[0] in clusters:
                raise ValueError(f"Duplicate ID: {row[0]}")
            clusters[row[0]] = row[3:5]

output.parent.mkdir(parents=True, exist_ok=True)
with source.open(encoding="cp1252", newline="") as src, output.open("w", encoding="utf-8-sig", newline="") as dst:
    rows = csv.DictReader(src, delimiter=";")
    names = ["ID_EMERGENCIA", "DEPARTAMENTO", "PROVINCIA", "EVENTO", "RED_AFECTADA", "ESTADO_INICIAL", "LATITUD", "LONGITUD", "CLUSTER_ID", "TIPO_PUNTO"]
    writer = csv.DictWriter(dst, fieldnames=names)
    writer.writeheader()
    count = 0
    for row in rows:
        point = clusters.get(row["ID_EMERGENCIA"])
        if point is None:
            raise ValueError(f"Missing assignment for ID {row['ID_EMERGENCIA']}")
        fields = {name: row[name if name != "DEPARTAMENTO" else "DEPARTEMENTO"] for name in names[:8]}
        writer.writerow({**fields, "CLUSTER_ID": point[0], "TIPO_PUNTO": point[1]})
        count += 1
if count != len(clusters):
    raise ValueError(f"Source count {count} differs from assignments {len(clusters)}")
print(f"Exported {count} rows to {output}")
