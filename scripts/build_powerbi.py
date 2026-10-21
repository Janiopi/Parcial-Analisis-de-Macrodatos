#!/usr/bin/env python3
"""Build the portable Power BI sources, semantic model and six-page PBIR report.

Run the Microsoft powerbi-report-author scaffold first, then run this script.
The model embeds the checked CSV data in compressed Power Query partitions so
Power BI Desktop does not require a Linux path or a machine-specific Windows path.
"""
from __future__ import annotations

import base64
import csv
import gzip
import json
import shutil
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "powerbi"
SOURCES = OUT / "fuentes"
REPORT = OUT / "Informe_Emergencias.Report" / "definition"
MODEL = OUT / "Informe_Emergencias.SemanticModel"
ORIGINAL = ROOT / "3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv"
SCHEMA_VISUAL = "https://developer.microsoft.com/json-schemas/fabric/item/report/definition/visualContainer/2.9.0/schema.json"
SCHEMA_PAGE = "https://developer.microsoft.com/json-schemas/fabric/item/report/definition/page/2.1.0/schema.json"
FOOT = "Fuente: MTC – Emergencias en la Red Vial Nacional (PNDA, 2026-08-24; corte 2026-08-20)"


def write_csv(path: Path, headers: list[str], rows: list[list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(headers)
        writer.writerows(rows)


def json_write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def prepare_sources() -> dict[str, Path]:
    SOURCES.mkdir(parents=True, exist_ok=True)
    with ORIGINAL.open(encoding="cp1252", newline="") as stream:
        original_reader = csv.DictReader(stream, delimiter=";")
        original_headers = list(original_reader.fieldnames or [])
        original = {r["ID_EMERGENCIA"]: r for r in original_reader}
    write_csv(SOURCES / "emergencias_original.csv", original_headers, [[r[h] for h in original_headers] for r in original.values()])
    with (OUT / "emergencias_clusters.csv").open(encoding="utf-8-sig", newline="") as stream:
        points = list(csv.DictReader(stream))
    assert len(points) == len(original) == 1362
    assert len({p["ID_EMERGENCIA"] for p in points}) == 1362
    cluster_count = Counter(p["CLUSTER_ID"] for p in points)
    assert cluster_count["-1"] == 137 and len(cluster_count) - 1 == 64
    assert [cluster_count[str(i)] for i in (13, 31, 9)] == [101, 86, 74]
    map_headers = list(points[0]) + ["FECHA_OCURRENCIA", "Conteo"]
    map_rows = []
    for point in points:
        source = original[point["ID_EMERGENCIA"]]
        date = datetime.strptime(source["FECHA_OCURRENCIA"], "%d/%m/%Y").date().isoformat()
        map_rows.append([point[h] for h in points[0]] + [date, "1"])
    files: dict[str, Path] = {}
    files["Emergencias"] = SOURCES / "emergencias_clusters.csv"
    write_csv(files["Emergencias"], map_headers, map_rows)

    for name, relative, headers in [
        ("Duracion", "duration/result/part-r-00000", ["Rango", "Departamento", "Casos", "Media_Dias"]),
        ("Interrupcion", "interruption/result/part-r-00000", ["Rango", "Via", "Casos", "Porcentaje"]),
    ]:
        raw = ROOT / "results" / "cluster" / relative
        copy_name = "duration.tsv" if name == "Duracion" else "interruption.tsv"
        shutil.copyfile(raw, SOURCES / copy_name)
        with raw.open(encoding="utf-8", newline="") as stream:
            rows = list(csv.reader(stream, delimiter="\t"))
        assert len(rows) == 10 and all(len(r) == 4 for r in rows)
        files[name] = SOURCES / ("duracion.csv" if name == "Duracion" else "interrupcion.csv")
        write_csv(files[name], headers, rows)

    raw_metrics = {
        "mlp-class": ROOT / "results/cluster/mlp-class/metrics/part-r-00000",
        "mlp-reg": ROOT / "results/cluster/mlp-reg/metrics/part-r-00000",
        "dbscan": ROOT / "results/cluster/dbscan/davies-bouldin/part-r-00000",
    }
    for key, path in raw_metrics.items():
        target = SOURCES / "metrics" / key / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)

    # Comparison.csv is absent from the tracked result selection; Table 6 is
    # the explicit fallback in briefpowerbi.md.
    comparison = [
        ["Duración", "93.475", "128.793", "1.378", "37.4", "73.2", "992.2", "1554.9"],
        ["Interrupción", "98.599", "126.506", "1.283", "35.4", "73.1", "1005.5", "1550.1"],
        ["DBSCAN", "484.625", "553.152", "1.141", "32.2", "68.4", "1009.2", "1512.9"],
        ["MLP clasificación", "1706.721", "2531.292", "1.483", "43.2", "72.9", "1067.6", "1552.6"],
        ["MLP regresión", "1669.284", "2452.002", "1.469", "43.9", "73.0", "1064.0", "1554.2"],
    ]
    files["Rendimiento"] = SOURCES / "comparison.csv"
    write_csv(files["Rendimiento"], ["Consulta", "Tiempo_cluster_s", "Tiempo_single_s", "Speedup", "CPU_cluster_pct", "CPU_single_pct", "RAM_cluster_MiB", "RAM_single_MiB"], comparison)
    long_rows = []
    for r in comparison:
        long_rows.extend([[r[0], "Clúster", r[1], r[4], r[6]], [r[0], "Un nodo", r[2], r[5], r[7]]])
    files["RendimientoEntorno"] = SOURCES / "rendimiento_entorno.csv"
    write_csv(files["RendimientoEntorno"], ["Consulta", "Entorno", "Tiempo_s", "CPU_pct", "RAM_MiB"], long_rows)

    metric_rows = {}
    for key in ("mlp-class", "mlp-reg"):
        text = raw_metrics[key].read_text(encoding="utf-8")
        test = next(line for line in text.splitlines() if line.startswith("test\t"))
        metric_rows[key] = dict(piece.split("=", 1) for piece in test.split("\t")[1:])
    cls = metric_rows["mlp-class"]
    reg = metric_rows["mlp-reg"]
    dbi = raw_metrics["dbscan"].read_text(encoding="utf-8").strip().split("\t")
    assert dbi[0] == "DBI" and dbi[2] == "64"
    for name, filename, headers, row in [
        ("Clasificacion", "calidad_clasificacion.csv", ["Ejecucion", "Perdida", "Accuracy", "Precision", "Recall", "F1"], [cls["loss"], cls["accuracy"], cls["precision"], cls["recall"], cls["F1"]]),
        ("Regresion", "calidad_regresion.csv", ["Ejecucion", "Perdida", "MAE_dias", "RMSE_dias"], [reg["loss"], reg["MAE_days"], reg["RMSE_days"]]),
        ("Agrupamiento", "calidad_dbscan.csv", ["Ejecucion", "Grupos", "Ruido", "Davies_Bouldin"], [dbi[2], "137", dbi[1]]),
    ]:
        files[name] = SOURCES / filename
        write_csv(files[name], headers, [[env] + row for env in ("Clúster", "Un nodo")])
    files["ConteoClusters"] = SOURCES / "conteo_clusters.csv"
    write_csv(files["ConteoClusters"], ["CLUSTER_ID", "Emergencias"], [[k, str(v)] for k, v in sorted(cluster_count.items(), key=lambda kv: int(kv[0])) if k != "-1"])
    return files


TYPES = {
    "Emergencias": {"ID_EMERGENCIA": "int64", "LATITUD": "double", "LONGITUD": "double", "CLUSTER_ID": "int64", "FECHA_OCURRENCIA": "dateTime", "Conteo": "int64"},
    "Duracion": {"Rango": "int64", "Casos": "int64", "Media_Dias": "double"},
    "Interrupcion": {"Rango": "int64", "Casos": "int64", "Porcentaje": "double"},
    "Rendimiento": {"Tiempo_cluster_s": "double", "Tiempo_single_s": "double", "Speedup": "double", "CPU_cluster_pct": "double", "CPU_single_pct": "double", "RAM_cluster_MiB": "double", "RAM_single_MiB": "double"},
    "RendimientoEntorno": {"Tiempo_s": "double", "CPU_pct": "double", "RAM_MiB": "double"},
    "Clasificacion": {"Perdida": "double", "Accuracy": "double", "Precision": "double", "Recall": "double", "F1": "double"},
    "Regresion": {"Perdida": "double", "MAE_dias": "double", "RMSE_dias": "double"},
    "Agrupamiento": {"Grupos": "int64", "Ruido": "int64", "Davies_Bouldin": "double"},
    "ConteoClusters": {"CLUSTER_ID": "int64", "Emergencias": "int64"},
}

MEASURES = {
    "Emergencias": [
        ("Total ruido", "CALCULATE(COUNTROWS(Emergencias), Emergencias[CLUSTER_ID] = -1)", "#,0"),
        ("Tooltip ID", "SELECTEDVALUE(Emergencias[ID_EMERGENCIA])", "#,0"),
        ("Tooltip departamento", "SELECTEDVALUE(Emergencias[DEPARTAMENTO])", ""),
        ("Tooltip evento", "SELECTEDVALUE(Emergencias[EVENTO])", ""),
        ("Tooltip estado", "SELECTEDVALUE(Emergencias[ESTADO_INICIAL])", ""),
    ],
    "Clasificacion": [("F1 prueba", "MAX(Clasificacion[F1])", "0.000000")],
    "Regresion": [("MAE prueba", "MAX(Regresion[MAE_dias])", "0.000000")],
    "Agrupamiento": [("Índice Davies-Bouldin", "MAX(Agrupamiento[Davies_Bouldin])", "0.000000")],
}


def semantic_model(files: dict[str, Path]) -> None:
    MODEL.mkdir(exist_ok=True)
    json_write(MODEL / "definition.pbism", {
        "$schema": "https://developer.microsoft.com/json-schemas/fabric/item/semanticModel/definitionProperties/1.0.0/schema.json",
        "version": "5.0", "settings": {"qnaEnabled": False},
    })
    tables = []
    for name, path in files.items():
        with path.open(encoding="utf-8", newline="") as stream:
            headers = next(csv.reader(stream))
        packed = base64.b64encode(gzip.compress(path.read_bytes(), mtime=0)).decode("ascii")
        change_types = []
        for header in headers:
            kind = TYPES[name].get(header, "string")
            m_type = {"string": "type text", "int64": "Int64.Type", "double": "type number", "dateTime": "type date"}[kind]
            change_types.append(f'{{"{header}", {m_type}}}')
        m = [
            "let",
            f'    Bin = Binary.Decompress(Binary.FromText("{packed}", BinaryEncoding.Base64), Compression.GZip),',
            f'    Raw = Csv.Document(Bin, [Delimiter=",", Columns={len(headers)}, Encoding=65001, QuoteStyle=QuoteStyle.Csv]),',
            "    Headers = Table.PromoteHeaders(Raw, [PromoteAllScalars=true]),",
            f'    Typed = Table.TransformColumnTypes(Headers, {{{", ".join(change_types)}}}, "en-US")',
            "in",
            "    Typed",
        ]
        columns = []
        for header in headers:
            kind = TYPES[name].get(header, "string")
            column = {"name": header, "dataType": kind, "sourceColumn": header, "summarizeBy": "none"}
            if kind == "double":
                column["formatString"] = "#,0.0000" if name in ("Duracion", "Interrupcion") else "#,0.000000"
            if kind == "dateTime":
                column["formatString"] = "dd/MM/yyyy"
            columns.append(column)
        table = {"name": name, "columns": columns, "partitions": [{"name": name, "mode": "import", "source": {"type": "m", "expression": m}}]}
        if name in MEASURES:
            table["measures"] = [{"name": label, "expression": expr, **({"formatString": fmt} if fmt else {})} for label, expr, fmt in MEASURES[name]]
        tables.append(table)
    json_write(MODEL / "model.bim", {
        "name": "Informe_Emergencias", "compatibilityLevel": 1600,
        "model": {"culture": "es-PE", "sourceQueryCulture": "en-US", "defaultPowerBIDataSourceVersion": "powerBI_V3", "tables": tables},
    })


def literal(value: object) -> dict:
    if isinstance(value, bool):
        v = "true" if value else "false"
    elif isinstance(value, int):
        v = f"{value}L"
    elif isinstance(value, float):
        v = f"{value}D"
    else:
        v = "'" + str(value).replace("'", "''") + "'"
    return {"expr": {"Literal": {"Value": v}}}


def col(table: str, column: str) -> dict:
    return {"Column": {"Expression": {"SourceRef": {"Entity": table}}, "Property": column}}


def measure(table: str, name: str) -> dict:
    return {"Measure": {"Expression": {"SourceRef": {"Entity": table}}, "Property": name}}


def aggregate(table: str, column: str, function: int = 0) -> dict:
    return {"Aggregation": {"Expression": col(table, column), "Function": function}}


def projection(field: dict, table: str, label: str, kind: str = "column") -> dict:
    if kind == "aggregate":
        query = f"Sum({table}.{label})"
        native = f"Sum of {label}"
    else:
        query, native = f"{table}.{label}", label
    return {"field": field, "queryRef": query, "nativeQueryRef": native, "active": True}


def pc(table: str, column: str) -> dict:
    return projection(col(table, column), table, column)


def pm(table: str, name: str) -> dict:
    return projection(measure(table, name), table, name)


def pa(table: str, column: str) -> dict:
    return projection(aggregate(table, column), table, column, "aggregate")


def visual(name: str, visual_type: str, x: int, y: int, w: int, h: int, roles: dict | None = None, objects: dict | None = None) -> dict:
    result = {"$schema": SCHEMA_VISUAL, "name": name, "position": {"x": x, "y": y, "z": 1000, "height": h, "width": w, "tabOrder": 1000}, "visual": {"visualType": visual_type}}
    if roles:
        result["visual"]["query"] = {"queryState": {role: {"projections": fields} for role, fields in roles.items()}}
    if objects is not None:
        result["visual"]["objects"] = objects
    return result


def textbox(name: str, text: str, x: int, y: int, w: int, h: int, size: int = 17, color: str = "#12304A") -> dict:
    v = visual(name, "textbox", x, y, w, h)
    v["visual"]["objects"] = {"general": [{"properties": {"paragraphs": [{"textRuns": [{"value": text, "textStyle": {"fontFamily": "Segoe UI Semibold", "fontSize": f"{size}px", "color": color}}], "horizontalTextAlignment": "left"}]}}]}
    v["visual"]["visualContainerObjects"] = {
        "background": [{"properties": {"show": literal(False)}}],
        "border": [{"properties": {"show": literal(False)}}],
        "padding": [{"properties": {k: literal(0) for k in ("top", "bottom", "left", "right")}}],
    }
    return v


def add(page: str, item: dict) -> None:
    json_write(REPORT / "pages" / page / "visuals" / item["name"] / "visual.json", item)


def title(page: str, heading: str, subtitle: str = "") -> None:
    add(page, textbox(page + "title", heading, 28, 15, 1215, 42, 24))
    if subtitle:
        add(page, textbox(page + "sub", subtitle, 29, 58, 1200, 33, 13, "#526B82"))
    add(page, textbox(page + "foot", FOOT, 28, 681, 1200, 24, 11, "#526B82"))


def exclude_cluster(noise: bool) -> dict:
    ref = {"Column": {"Expression": {"SourceRef": {"Source": "e"}}, "Property": "CLUSTER_ID"}}
    condition = {"In": {"Expressions": [ref], "Values": [[{"Literal": {"Value": "-1L"}}]]}}
    if not noise:
        condition = {"Not": {"Expression": condition}}
    return {"filters": [{"name": "Filter" + ("Noise" if noise else "Clusters") + "000000000000000000", "field": col("Emergencias", "CLUSTER_ID"), "type": "Categorical", "filter": {"Version": 2, "From": [{"Name": "e", "Entity": "Emergencias", "Type": 0}], "Where": [{"Condition": condition}]}, "howCreated": "User"}]}


def report_pages() -> None:
    pages = [
        ("4f751bcc7d4e4a9483c6", "Mapa de clusters"),
        ("duracion", "Duración por departamento"),
        ("interrupcion", "Interrupción por vía"),
        ("rendimiento", "Rendimiento"),
        ("calidad", "Calidad de modelos"),
        ("conteo_clusters", "Conteo por cluster"),
    ]
    for page, display in pages:
        json_write(REPORT / "pages" / page / "page.json", {"$schema": SCHEMA_PAGE, "name": page, "displayName": display, "displayOption": "FitToPage", "height": 720, "width": 1280})
    pages_path = REPORT / "pages" / "pages.json"
    meta = json.loads(pages_path.read_text(encoding="utf-8"))
    meta["pageOrder"] = [p for p, _ in pages]
    meta["activePageName"] = pages[0][0]
    json_write(pages_path, meta)

    p = pages[0][0]
    title(p, "Concentración geográfica de emergencias viales según DBSCAN (64 grupos)", "Coordenadas reales · 1 225 emergencias agrupadas · 137 puntos de ruido")
    for i, (field, label) in enumerate([("DEPARTAMENTO", "Departamento"), ("EVENTO", "Evento"), ("ESTADO_INICIAL", "Estado inicial"), ("FECHA_OCURRENCIA", "Fecha de ocurrencia")]):
        v = visual(f"mapfilter{i}", "slicer", 29, 116 + i * 98, 205, 87, {"Values": [pc("Emergencias", field)]}, {"data": [{"properties": {"mode": literal("RelativeDatePicker" if field == "FECHA_OCURRENCIA" else "Dropdown")}}], "header": [{"properties": {"show": literal(True), "text": literal(label)}}]})
        add(p, v)
    map_roles = {"Category": [pc("Emergencias", "ID_EMERGENCIA")], "Y": [pc("Emergencias", "LATITUD")], "X": [pc("Emergencias", "LONGITUD")], "Series": [pc("Emergencias", "CLUSTER_ID")], "Tooltips": [pm("Emergencias", k) for k in ("Tooltip ID", "Tooltip departamento", "Tooltip evento", "Tooltip estado")]}
    for n, noise, x, w in [("mapclusters", False, 252, 720), ("mapnoise", True, 988, 260)]:
        roles = {k: v for k, v in map_roles.items() if k != "Series" or not noise}
        v = visual(n, "azureMap", x, 122, w, 416, roles, {"bubbleLayer": [{"properties": {"show": literal(True), "sizeByValue": literal(False), "bubbleRadius": literal(5 if noise else 7), "mapTransparency": literal(30)}}], "legend": [{"properties": {"show": literal(not noise)}}]})
        if not noise:
            v["visual"]["query"]["sortDefinition"] = {"sort": [{"field": col("Emergencias", "CLUSTER_ID"), "direction": "Ascending"}], "isDefaultSort": False}
        v["filterConfig"] = exclude_cluster(noise)
        add(p, v)
    add(p, textbox("noiseheading", "Emergencias clasificadas como ruido por DBSCAN", 988, 91, 265, 26, 14))
    add(p, textbox("clustersnotes", "Grupo 13: 101 casos (89 en Piura)  ·  Grupo 31: 86 (52 Pasco, 31 Junín)  ·  Grupo 9: 74 (Cajamarca)", 255, 550, 990, 66, 14))

    p = pages[1][0]
    title(p, "Top 10 departamentos con mayor duración promedio hasta el cierre", "Días promedio entre ocurrencia y cierre · casos cerrados válidos")
    v = visual("durationbar", "clusteredBarChart", 36, 111, 850, 510, {"Category": [pc("Duracion", "Departamento")], "Y": [pa("Duracion", "Media_Dias")], "Tooltips": [pa("Duracion", "Casos")]})
    v["visual"]["query"]["sortDefinition"] = {"sort": [{"field": aggregate("Duracion", "Media_Dias"), "direction": "Descending"}], "isDefaultSort": False}
    v["visual"]["objects"] = {"labels": [{"properties": {"show": literal(True), "labelPrecision": literal(2)}}]}
    add(p, v)
    add(p, textbox("durationcases", "Casos por departamento", 910, 101, 330, 30, 16))
    add(p, visual("durationtable", "tableEx", 910, 137, 332, 420, {"Values": [pc("Duracion", "Departamento"), pc("Duracion", "Casos"), pc("Duracion", "Media_Dias")]}))
    add(p, textbox("durationnote", "Tumbes registra 56,8 días con solo 5 casos; Piura es más representativa con 118.", 36, 629, 1190, 39, 14))

    p = pages[2][0]
    title(p, "Vías con mayor porcentaje de emergencias inicialmente interrumpidas", "Porcentaje calculado sobre vías con al menos 10 emergencias")
    v = visual("interruptionbar", "clusteredBarChart", 36, 111, 850, 510, {"Category": [pc("Interrupcion", "Via")], "Y": [pa("Interrupcion", "Porcentaje")], "Tooltips": [pa("Interrupcion", "Casos")]})
    v["visual"]["query"]["sortDefinition"] = {"sort": [{"field": aggregate("Interrupcion", "Porcentaje"), "direction": "Descending"}], "isDefaultSort": False}
    v["visual"]["objects"] = {"labels": [{"properties": {"show": literal(True), "labelPrecision": literal(2)}}]}
    add(p, v)
    add(p, textbox("interruptioncases", "Casos por vía", 910, 101, 330, 30, 16))
    add(p, visual("interruptiontable", "tableEx", 910, 137, 332, 420, {"Values": [pc("Interrupcion", "Via"), pc("Interrupcion", "Casos"), pc("Interrupcion", "Porcentaje")]}))
    add(p, textbox("interruptionnote", "PE-5NB llega a 100 % con 11 casos; PE-3S y PE-1SI tienen 36 y 35 casos.", 36, 629, 1190, 39, 14))

    p = pages[3][0]
    title(p, "Comparación de rendimiento: clúster vs. un nodo", "Medianas de tres repeticiones · RAM del clúster = promedio por nodo, no suma de tres VM")
    add(p, textbox("timetitle", "Tiempo de ejecución por consulta: clúster vs. un nodo", 31, 103, 610, 30, 15))
    add(p, visual("timechart", "clusteredColumnChart", 31, 137, 610, 206, {"Category": [pc("RendimientoEntorno", "Consulta")], "Series": [pc("RendimientoEntorno", "Entorno")], "Y": [pa("RendimientoEntorno", "Tiempo_s")]}))
    add(p, textbox("speedtitle", "Speedup por consulta (clúster respecto a un nodo)", 660, 103, 580, 30, 15))
    speed = visual("speedchart", "clusteredColumnChart", 660, 137, 580, 206, {"Category": [pc("Rendimiento", "Consulta")], "Y": [pa("Rendimiento", "Speedup")]}, {"y1AxisReferenceLine": [{"properties": {"show": literal(True), "value": literal(1.0), "displayName": literal("Sin aceleración"), "style": literal("dashed")}}], "labels": [{"properties": {"show": literal(True), "labelPrecision": literal(2)}}]})
    add(p, speed)
    add(p, textbox("resourcetitle", "Uso promedio de CPU y memoria por consulta y entorno", 31, 363, 1205, 29, 15))
    add(p, visual("resourcechart", "lineClusteredColumnComboChart", 31, 398, 1209, 250, {"Category": [pc("Rendimiento", "Consulta")], "Y": [pa("Rendimiento", "CPU_cluster_pct"), pa("Rendimiento", "CPU_single_pct")], "Y2": [pa("Rendimiento", "RAM_cluster_MiB"), pa("Rendimiento", "RAM_single_MiB")]}))
    add(p, textbox("ramnote", "CPU en columnas (%) · RAM en líneas (MiB) · las medidas del clúster son promedio por nodo.", 32, 649, 1200, 28, 12))

    p = pages[4][0]
    title(p, "Métricas de calidad: resultados idénticos en ambos entornos", "Misma semilla y partición · métricas del conjunto de prueba para MLP")
    for n, x, table, meas, label in [("f1", 31, "Clasificacion", "F1 prueba", "F1 clasificación"), ("mae", 450, "Regresion", "MAE prueba", "MAE regresión (días)"), ("dbi", 869, "Agrupamiento", "Índice Davies-Bouldin", "Davies-Bouldin")]:
        add(p, textbox(n + "label", label, x, 104, 380, 27, 16))
        add(p, visual(n + "card", "cardVisual", x, 137, 380, 113, {"Data": [pm(table, meas)]}))
    for n, x, label, table, fields in [
        ("classification", 31, "Clasificación", "Clasificacion", ["Ejecucion", "Perdida", "Accuracy", "Precision", "Recall", "F1"]),
        ("regression", 450, "Regresión", "Regresion", ["Ejecucion", "Perdida", "MAE_dias", "RMSE_dias"]),
        ("dbscanquality", 869, "Agrupamiento", "Agrupamiento", ["Ejecucion", "Grupos", "Ruido", "Davies_Bouldin"]),
    ]:
        add(p, textbox(n + "label", label, x, 285, 380, 28, 17))
        add(p, visual(n + "table", "tableEx", x, 320, 380, 268, {"Values": [pc(table, f) for f in fields]}))
    add(p, textbox("qualitynote", "Clasificación: F1 = 0,384181  ·  Regresión: MAE = 13,518356 días  ·  DBSCAN: 64 grupos, 137 ruidos", 31, 611, 1200, 35, 14))

    p = pages[5][0]
    title(p, "Cantidad de emergencias por grupo DBSCAN", "64 grupos · excluye 137 puntos de ruido · la etiqueta del grupo es un ID técnico")
    v = visual("clustercountchart", "clusteredColumnChart", 31, 108, 1210, 489, {"Category": [pc("ConteoClusters", "CLUSTER_ID")], "Y": [pa("ConteoClusters", "Emergencias")]})
    v["visual"]["query"]["sortDefinition"] = {"sort": [{"field": aggregate("ConteoClusters", "Emergencias"), "direction": "Descending"}], "isDefaultSort": False}
    add(p, v)
    add(p, textbox("clustercountnote", "Destacan los grupos 13 (101 emergencias), 31 (86) y 9 (74).", 31, 617, 1200, 35, 14))


def main() -> None:
    if not (OUT / "Informe_Emergencias.pbip").exists():
        raise SystemExit("Scaffold first: powerbi-report-author scaffold powerbi --name Informe_Emergencias --model-path ../Informe_Emergencias.SemanticModel --offline")
    files = prepare_sources()
    semantic_model(files)
    report_pages()
    print(f"Built {len(files)} embedded tables, {len(list((REPORT / 'pages').glob('*/page.json')))} pages")


if __name__ == "__main__":
    main()
