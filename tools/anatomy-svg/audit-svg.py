#!/usr/bin/env python3
"""Dependency-free structural audit for the anatomy SVG source.

Bounding boxes are approximate: curves use their control/end points and SVG
rendering features (markers, filters, text layout and clipping) are not fully
evaluated.  Transform matrices are applied where they are expressed as SVG
transform attributes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

SVG_NS = "http://www.w3.org/2000/svg"
XLINK = "http://www.w3.org/1999/xlink"
INTERESTING = {"path", "g", "use", "clipPath", "linearGradient", "radialGradient"}
GRAPHIC = {"path", "use", "rect", "circle", "ellipse", "polygon", "polyline", "line", "text"}
NUMBER = r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def matrix_multiply(a, b):
    return (a[0]*b[0]+a[2]*b[1], a[1]*b[0]+a[3]*b[1],
            a[0]*b[2]+a[2]*b[3], a[1]*b[2]+a[3]*b[3],
            a[0]*b[4]+a[2]*b[5]+a[4], a[1]*b[4]+a[3]*b[5]+a[5])


IDENTITY = (1., 0., 0., 1., 0., 0.)


def transform_matrix(value: str | None):
    result = IDENTITY
    for kind, raw in re.findall(r"([A-Za-z]+)\s*\(([^)]*)\)", value or ""):
        values = [float(x) for x in re.findall(NUMBER, raw)]
        if kind == "matrix" and len(values) == 6:
            item = tuple(values)
        elif kind == "translate" and values:
            item = (1., 0., 0., 1., values[0], values[1] if len(values) > 1 else 0.)
        elif kind == "scale" and values:
            item = (values[0], 0., 0., values[1] if len(values) > 1 else values[0], 0., 0.)
        elif kind == "rotate" and values:
            angle = math.radians(values[0]); c, s = math.cos(angle), math.sin(angle)
            item = (c, s, -s, c, 0., 0.)
            if len(values) >= 3:
                item = matrix_multiply(matrix_multiply((1., 0., 0., 1., values[1], values[2]), item),
                                       (1., 0., 0., 1., -values[1], -values[2]))
        elif kind == "skewX" and values:
            item = (1., 0., math.tan(math.radians(values[0])), 1., 0., 0.)
        elif kind == "skewY" and values:
            item = (1., math.tan(math.radians(values[0])), 0., 1., 0., 0.)
        else:
            continue
        result = matrix_multiply(result, item)
    return result


def apply(matrix, point):
    return (matrix[0]*point[0] + matrix[2]*point[1] + matrix[4],
            matrix[1]*point[0] + matrix[3]*point[1] + matrix[5])


def path_points(data: str):
    tokens = re.findall(r"[AaCcHhLlMmQqSsTtVvZz]|" + NUMBER, data or "")
    i = 0; command = None; current = (0., 0.); start = current; points = []
    arity = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7}
    while i < len(tokens):
        if tokens[i].isalpha(): command = tokens[i]; i += 1
        if command is None: break
        upper, relative = command.upper(), command.islower()
        if upper == "Z": current = start; points.append(current); command = None; continue
        n = arity.get(upper)
        if n is None or i + n > len(tokens): break
        vals = list(map(float, tokens[i:i+n])); i += n
        def xy(x, y): return (x + current[0], y + current[1]) if relative else (x, y)
        if upper in {"M", "L", "T"}:
            current = xy(vals[-2], vals[-1]); points.append(current)
            if upper == "M": start = current; command = "l" if relative else "L"
        elif upper == "H": current = (vals[0] + (current[0] if relative else 0.), current[1]); points.append(current)
        elif upper == "V": current = (current[0], vals[0] + (current[1] if relative else 0.)); points.append(current)
        elif upper in {"C", "S", "Q"}:
            pair_start = 0
            for j in range(pair_start, len(vals), 2): points.append(xy(vals[j], vals[j+1]))
            current = points[-1]
        elif upper == "A":
            # Include endpoint and radius extents: intentionally conservative.
            end = xy(vals[5], vals[6]); points.extend([end, (end[0]+abs(vals[0]), end[1]+abs(vals[1])), (end[0]-abs(vals[0]), end[1]-abs(vals[1]))]); current = end
    return points


def bbox_for(element, matrix):
    tag = local(element.tag); points = []
    if tag == "path": points = path_points(element.get("d", ""))
    elif tag == "use":
        x, y = float(element.get("x", 0)), float(element.get("y", 0)); points = [(x, y)]
    elif tag in {"rect", "image", "foreignObject"}:
        x, y = float(element.get("x", 0)), float(element.get("y", 0)); points = [(x,y), (x+float(element.get("width",0)), y+float(element.get("height",0)))]
    elif tag in {"circle", "ellipse"}:
        cx, cy = float(element.get("cx",0)), float(element.get("cy",0)); rx = float(element.get("r", element.get("rx",0))); ry = float(element.get("r", element.get("ry",rx))); points = [(cx-rx,cy-ry),(cx+rx,cy+ry)]
    elif tag in {"polygon", "polyline"}:
        vals = [float(x) for x in re.findall(NUMBER, element.get("points", ""))]; points = list(zip(vals[::2], vals[1::2]))
    elif tag == "line": points = [(float(element.get("x1",0)),float(element.get("y1",0))),(float(element.get("x2",0)),float(element.get("y2",0)))]
    if not points: return None
    points = [apply(matrix, p) for p in points]
    xs, ys = zip(*points)
    return {"x": round(min(xs), 4), "y": round(min(ys), 4), "width": round(max(xs)-min(xs), 4), "height": round(max(ys)-min(ys), 4), "quality": "approximate-control-points-and-transforms"}


def parse_style(element, inherited):
    style = dict(inherited)
    for key in ("fill", "stroke", "display", "visibility", "opacity", "fill-opacity", "stroke-opacity"):
        if key in element.attrib: style[key] = element.attrib[key]
    for pair in element.get("style", "").split(";"):
        if ":" in pair:
            key, val = pair.split(":", 1); style[key.strip()] = val.strip()
    return {key: style.get(key) for key in ("fill", "stroke", "display", "visibility", "opacity", "fill-opacity", "stroke-opacity") if key in style}


def refs_for(element):
    refs = []
    for key, value in element.attrib.items():
        if key.endswith("href") or "url(" in value:
            refs.append({"attribute": local(key), "value": value, "targets": re.findall(r"(?:#|url\(\s*#)([^)\s]+)", value)})
    return refs


def is_invisible(style):
    return style.get("display") == "none" or style.get("visibility") == "hidden" or style.get("opacity") == "0" or (style.get("fill") == "none" and style.get("stroke") in (None, "none"))


def audit_svg(source: Path):
    root = ET.parse(source).getroot(); viewbox = [float(x) for x in re.findall(NUMBER, root.get("viewBox", "0 0 0 0"))]
    records = []; ids = []; path_records = []
    def walk(elem, parent_path, inherited_style, matrix, depth, parent_group):
        tag = local(elem.tag); eid = elem.get("id"); path = f"{parent_path}/{tag}[{len(records)}]"
        style = parse_style(elem, inherited_style); combined = matrix_multiply(matrix, transform_matrix(elem.get("transform")))
        bbox = bbox_for(elem, combined); record = {"index": len(records), "tag": tag, "id": eid, "elementPath": path, "depth": depth, "parentGroup": parent_group, "transform": elem.get("transform"), "style": style, "bbox": bbox, "references": refs_for(elem)}
        records.append(record)
        if eid: ids.append(eid)
        if tag == "path": path_records.append(record)
        next_parent_group = eid if tag == "g" and eid else parent_group
        for child in list(elem): walk(child, path, style, combined, depth+1, next_parent_group)
    walk(root, "", {}, IDENTITY, 0, None)
    duplicate_ids = sorted(k for k, count in Counter(ids).items() if count > 1)
    center = viewbox[0] + viewbox[2] / 2 if len(viewbox) == 4 else 0
    for item in path_records:
        box = item["bbox"]
        item["sideHeuristic"] = "FRONT" if box and box["x"] + box["width"]/2 < center else "BACK" if box else "UNKNOWN"
    interesting = [r for r in records if r["tag"] in INTERESTING or r["transform"]]
    return {"schemaVersion": 1, "source": str(source), "sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "viewBox": viewbox, "bboxDisclaimer": "Approximate bounds use path endpoints/control points and transform attributes; inherited transforms are applied. This is not a rendered geometric bounding box.", "frontBackDisclaimer": "FRONT/BACK is a positional heuristic only: a path center left of the SVG viewBox midpoint is FRONT, otherwise BACK. It does not identify anatomy.", "counts": {"elements": len(records), "paths": len(path_records), "groups": sum(r["tag"] == "g" for r in records), "uses": sum(r["tag"] == "use" for r in records), "clipPaths": sum(r["tag"] == "clipPath" for r in records), "gradients": sum(r["tag"] in {"linearGradient", "radialGradient"} for r in records)}, "hierarchy": records, "paths": path_records, "interestingElements": interesting, "ids": sorted(ids), "missingIds": [r["elementPath"] for r in records if r["tag"] in INTERESTING | GRAPHIC and not r["id"]], "duplicateIds": duplicate_ids, "invisibleElements": [r["elementPath"] for r in records if is_invisible(r["style"])], "references": [dict(elementPath=r["elementPath"], references=r["references"]) for r in records if r["references"]]}


def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--source", type=Path, default=Path("docs/Muscles_front_and_back.svg")); parser.add_argument("--output", type=Path, default=Path("build/reports/anatomy-svg-audit.json")); args = parser.parse_args()
    report = audit_svg(args.source); args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__": main()
