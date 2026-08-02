#!/usr/bin/env python3
"""Generate deterministic, view-specific anatomy silhouette assets.

The manifest is the only authority for view membership and interactivity.  The
source drawing is never used to guess whether geometry belongs to FRONT/BACK.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG)

SOURCE = Path("docs/Muscles_front_and_back.svg")
MANIFEST = HERE / "anatomy-geometry-manifest-v1.json"
OUTPUT_DIR = Path("web/src/assets/anatomy")
REPORT = HERE / "anatomy-production-coverage-v1.json"
UNSAFE_TAGS = {"script", "foreignObject", "metadata", "namedview"}
EDITOR_NAMESPACES = {"http://www.inkscape.org/namespaces/inkscape", "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd"}
TECHNICAL_ANCESTORS = {"defs", "clipPath", "mask", "marker", "pattern"}
BASE_ROLES = {"INTERACTIVE", "SUPPORT", "REFERENCE_ONLY"}
# Audited manifest entries that are construction axes, not silhouette geometry.
# Keep this source-ID decision explicit rather than deriving it from position.
DETACHED_BASE_SOURCE_IDS = {"path1379", "path1381"}


def local(value: str) -> str:
    return value.rsplit("}", 1)[-1]


def semantic_id(code: str) -> str:
    return "visual-region-" + code.removeprefix("ANATOMY_VISUAL_MAP_V1:").lower().replace(":", "-").replace("_", "-")


def opposite_laterality(laterality: str) -> str:
    return {"LEFT": "RIGHT", "RIGHT": "LEFT", "CENTRAL": "CENTRAL"}[laterality]


def transform_chain(element: ET.Element, parents: dict[ET.Element, ET.Element]) -> str | None:
    values: list[str] = []
    current: ET.Element | None = element
    while current is not None:
        if current.get("transform"):
            values.append(current.get("transform"))
        current = parents.get(current)
    return " ".join(reversed(values)) or None


def sanitize(root: ET.Element) -> None:
    parents = {child: parent for parent in root.iter() for child in parent}
    for element in list(root.iter()):
        namespace = element.tag[1:].split("}", 1)[0] if element.tag.startswith("{") else None
        if local(element.tag) in UNSAFE_TAGS or namespace in EDITOR_NAMESPACES:
            parent = parents.get(element)
            if parent is not None:
                parent.remove(element)
            continue
        for attribute in list(element.attrib):
            namespace = attribute[1:].split("}", 1)[0] if attribute.startswith("{") else None
            value = element.attrib[attribute]
            if local(attribute).lower().startswith("on") or namespace in EDITOR_NAMESPACES:
                del element.attrib[attribute]
            elif local(attribute) == "href" and (not value.startswith("#") or value.lower().startswith("javascript:")):
                del element.attrib[attribute]


def source_references(root: ET.Element) -> tuple[set[str], list[str]]:
    ids = {element.get("id") for element in root.iter() if element.get("id")}
    refs: set[str] = set(); missing: list[str] = []
    for element in root.iter():
        for attribute, value in element.attrib.items():
            targets = [value[1:]] if local(attribute) == "href" and value.startswith("#") else []
            targets.extend(re.findall(r"url\(\s*#([^)\s]+)\s*\)", value))
            for target in targets:
                refs.add(target)
                if target not in ids:
                    missing.append(target)
    return refs, sorted(set(missing))


def _is_technical(element: ET.Element, parents: dict[ET.Element, ET.Element]) -> bool:
    current = parents.get(element)
    while current is not None:
        if local(current.tag) in TECHNICAL_ANCESTORS:
            return True
        current = parents.get(current)
    return False


def _href_target(element: ET.Element) -> str | None:
    for attribute, value in element.attrib.items():
        if local(attribute) == "href" and value.startswith("#"):
            return value[1:]
    return None


def _descendant_paths(element: ET.Element) -> list[ET.Element]:
    return [candidate for candidate in element.iter() if local(candidate.tag) == "path"]


def _relative_transform(element: ET.Element, ancestor: ET.Element, parents: dict[ET.Element, ET.Element]) -> str | None:
    """Return transforms from `ancestor`'s shadow-tree root to `element`."""
    values: list[str] = []
    current: ET.Element | None = element
    while current is not None and current is not ancestor:
        if current.get("transform"):
            values.append(current.get("transform"))
        current = parents.get(current)
    return " ".join(reversed(values)) or None


def _visual_uses(view: str, root: ET.Element, by_id: dict[str, ET.Element], entries_by_id: dict[str, dict], parents: dict[ET.Element, ET.Element]) -> list[list[tuple[ET.Element, str, str | None]]]:
    """Find source `<use>` mirrors whose referenced manifest geometry is one view.

    The use itself is not classified by location or name: its view comes solely
    from the manifest entries of its referenced path subtree.
    """
    result = []
    for use in root.iter():
        if local(use.tag) != "use" or _is_technical(use, parents):
            continue
        target_id = _href_target(use); target = by_id.get(target_id)
        if target is None:
            continue
        paths = _descendant_paths(target)
        path_entries = [entries_by_id.get(path.get("id")) for path in paths]
        if not paths or any(entry is None for entry in path_entries) or {entry["view"] for entry in path_entries} != {view}:
            continue
        use_transform = transform_chain(use, parents)
        bounds = [(path, " ".join(filter(None, [use_transform, _relative_transform(path, target, parents)])) or None) for path in paths]
        result.append([(path, path.get("id") or target_id, transform) for (path, transform) in bounds])
    return result


def _clone_geometry(source: ET.Element, source_id: str, transform: str | None, *, neutral: bool) -> ET.Element:
    clone = copy.deepcopy(source)
    # A clone is display geometry, not a reference target; technical IDs remain
    # only in defs.  This prevents duplicate/detached source IDs in a runtime SVG.
    clone.attrib.pop("id", None)
    clone.attrib.pop("style", None)
    for name in ("fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity", "pointer-events", "tabindex"):
        clone.attrib.pop(name, None)
    clone.set("data-source-element-id", source_id)
    if transform:
        clone.set("transform", transform)
    if neutral:
        clone.set("fill", "#e4e7eb")
        clone.set("stroke", "#c7cdd4")
        clone.set("stroke-width", "0.35")
        clone.set("pointer-events", "none")
        clone.set("data-anatomy-geometry", "base")
    else:
        clone.set("fill", "var(--anatomy-region-fill, transparent)")
        clone.set("stroke", "var(--anatomy-region-stroke, transparent)")
        clone.set("data-anatomy-geometry", "exposure")
    return clone


def _audit_module():
    spec = importlib.util.spec_from_file_location("anatomy_audit", HERE / "audit-svg.py")
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def _view_box(elements: list[tuple[ET.Element, str | None]]) -> str:
    audit = _audit_module(); boxes = [audit.bbox_for(element, audit.transform_matrix(transform)) for element, transform in elements]
    boxes = [box for box in boxes if box is not None]
    if not boxes:
        raise ValueError("view contains no visible manifest geometry")
    left = min(box["x"] for box in boxes); top = min(box["y"] for box in boxes)
    right = max(box["x"] + box["width"] for box in boxes); bottom = max(box["y"] + box["height"] for box in boxes)
    pad = max(2.0, max(right - left, bottom - top) * 0.025)
    return " ".join(f"{value:.4f}" for value in (left - pad, top - pad, right - left + 2 * pad, bottom - top + 2 * pad))


def _preserve_reference_targets(root: ET.Element, by_id: dict[str, ET.Element]) -> None:
    """Move source-only reference targets into defs until the asset is closed."""
    defs = next((element for element in root if local(element.tag) == "defs"), None)
    if defs is None:
        defs = ET.Element(f"{{{SVG}}}defs")
        root.insert(0, defs)
    while True:
        _, missing = source_references(root)
        source_targets = [target for target in missing if target in by_id]
        if not source_targets:
            break
        for target in sorted(set(source_targets)):
            dependency = copy.deepcopy(by_id[target])
            # Dependencies are definitions, never display geometry.  Removing
            # editor paint presentation keeps source salmon colours out of the
            # runtime artifact without changing their referenced shape/ID.
            for element in dependency.iter():
                if local(element.tag) in {"path", "use", "rect", "circle", "ellipse", "polygon", "polyline", "line"}:
                    element.attrib.pop("style", None)
                    for name in ("fill", "stroke", "stroke-width", "opacity", "fill-opacity", "stroke-opacity"):
                        element.attrib.pop(name, None)
            defs.append(dependency)
    _, missing = source_references(root)
    if missing:
        raise ValueError("generated SVG has missing references: " + ", ".join(missing))


def build_view(view: str, source: Path = SOURCE, manifest_path: Path = MANIFEST) -> ET.ElementTree:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("sourceSha256") != hashlib.sha256(source.read_bytes()).hexdigest():
        raise ValueError("source SVG SHA-256 does not match immutable geometry manifest")
    source_root = ET.parse(source).getroot(); sanitize(source_root)
    parents = {child: parent for parent in source_root.iter() for child in parent}
    by_id = {element.get("id"): element for element in source_root.iter() if element.get("id")}
    entries = [entry for entry in manifest["entries"] if entry["view"] == view]
    entries_by_id = {entry["sourceElementId"]: entry for entry in manifest["entries"]}
    if not entries or any(entry["sourceElementId"] not in by_id for entry in entries):
        raise ValueError(f"{view} manifest geometry is absent from source SVG")
    # Review statuses decide heatmap eligibility, not whether noninteractive
    # reference/support geometry completes a neutral silhouette.  The two
    # audited construction axes are the only visible-source exclusions.
    visible_base = [entry for entry in entries if entry["geometryRole"] in BASE_ROLES and entry["sourceElementId"] not in DETACHED_BASE_SOURCE_IDS and not _is_technical(by_id[entry["sourceElementId"]], parents)]
    visual_uses = _visual_uses(view, source_root, by_id, entries_by_id, parents)
    view_box = _view_box(
        [(by_id[entry["sourceElementId"]], transform_chain(by_id[entry["sourceElementId"]], parents)) for entry in visible_base]
        + [(path, transform) for mirrored in visual_uses for path, _, transform in mirrored]
    )
    root = ET.Element(f"{{{SVG}}}svg", {"viewBox": view_box, "role": "img", "data-anatomy-view": view, "data-anatomy-asset-version": "v1"})
    # Technical dependencies are added below only when a selected clone refers
    # to them. Display clones cannot become reference targets or expose source
    # editor styling.
    title = ET.SubElement(root, f"{{{SVG}}}title", {"id": f"anatomy-body-{view.lower()}-v1-title"})
    title.text = f"Sylwetka ciała — {('przód' if view == 'FRONT' else 'tył')}"
    base = ET.SubElement(root, f"{{{SVG}}}g", {"id": "base-silhouette", "data-layer": "base-silhouette", "data-view": view, "aria-hidden": "true", "pointer-events": "none"})
    for entry in visible_base:
        source_element = by_id[entry["sourceElementId"]]
        base.append(_clone_geometry(source_element, entry["sourceElementId"], transform_chain(source_element, parents), neutral=True))
    # Flatten mirrored source `<use>` trees into neutral paths.  Keeping a use
    # would depend on source hierarchy/style inheritance and was the reason the
    # mirrored half of each figure disappeared in the runtime artifact.
    for mirrored in visual_uses:
        for source_element, source_id, transform in mirrored:
            base.append(_clone_geometry(source_element, source_id, transform, neutral=True))
    overlay = ET.SubElement(root, f"{{{SVG}}}g", {"id": "exposure-overlay", "data-layer": "exposure-overlay", "data-view": view})
    approved = [entry for entry in entries if entry["reviewStatus"] == "APPROVED" and entry["geometryRole"] == "INTERACTIVE" and not _is_technical(by_id[entry["sourceElementId"]], parents)]
    grouped: dict[tuple[str, str], list[tuple[dict, ET.Element, str | None, str]]] = defaultdict(list)
    for entry in approved:
        source_element = by_id[entry["sourceElementId"]]
        grouped[(entry["visualRegionCode"], entry["laterality"])].append((entry, source_element, transform_chain(source_element, parents), "direct"))
    # A source <use> is a geometric mirror. Only approved interactive members
    # of its already manifest-classified subtree become mirrored heatmap paths;
    # CENTRAL regions intentionally have no second overlay instance.
    for mirrored in visual_uses:
        for source_element, source_id, transform in mirrored:
            entry = entries_by_id.get(source_id)
            if entry is None or entry["reviewStatus"] != "APPROVED" or entry["geometryRole"] != "INTERACTIVE" or entry["laterality"] == "CENTRAL":
                continue
            grouped[(entry["visualRegionCode"], opposite_laterality(entry["laterality"]))].append((entry, source_element, transform, "mirrored"))
    for (code, laterality) in sorted(grouped):
        group = ET.SubElement(overlay, f"{{{SVG}}}g", {"id": f"{semantic_id(code)}-{laterality.lower()}", "data-visual-region-code": code, "data-view": view, "data-laterality": laterality})
        for entry, source_element, transform, instance in grouped[(code, laterality)]:
            clone = _clone_geometry(source_element, entry["sourceElementId"], transform, neutral=False)
            clone.set("data-source-instance", instance)
            group.append(clone)
    _preserve_reference_targets(root, by_id)
    return ET.ElementTree(root)


def write(output_dir: Path = OUTPUT_DIR, report_path: Path = REPORT, source: Path = SOURCE, manifest: Path = MANIFEST) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    assets = {}
    for view in ("FRONT", "BACK"):
        output = output_dir / f"anatomy-body-{view.lower()}-v1.svg"
        tree = build_view(view, source, manifest); ET.indent(tree, space="  ")
        tree.write(output, encoding="utf-8", xml_declaration=True, short_empty_elements=True)
        rendered = tree.getroot()
        overlay_paths = [element for element in rendered.iter() if element.get("data-anatomy-geometry") == "exposure"]
        assets[view] = {"path": str(output), "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "directOverlayPathCount": sum(element.get("data-source-instance") == "direct" for element in overlay_paths), "mirroredOverlayPathCount": sum(element.get("data-source-instance") == "mirrored" for element in overlay_paths)}
    payload = {"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "assets": assets}
    report_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return payload


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=SOURCE); parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR); parser.add_argument("--report", type=Path, default=REPORT)
    args = parser.parse_args(); print(json.dumps(write(args.output_dir, args.report, args.source, args.manifest), indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
