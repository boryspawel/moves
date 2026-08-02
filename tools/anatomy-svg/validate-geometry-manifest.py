#!/usr/bin/env python3
"""Validate the manual SVG geometry review ledger without inferring anatomy."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
AUDIT_SPEC = importlib.util.spec_from_file_location("svg_audit", HERE / "audit-svg.py")
audit = importlib.util.module_from_spec(AUDIT_SPEC); assert AUDIT_SPEC.loader; AUDIT_SPEC.loader.exec_module(audit)

VIEWS = {"FRONT", "BACK"}
ROLES = {"INTERACTIVE", "DECORATIVE", "SUPPORT", "REFERENCE_ONLY"}
STATUSES = {"UNREVIEWED", "PROPOSED", "APPROVED", "AMBIGUOUS", "REJECTED"}
CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}
# CENTRAL describes geometry on the anatomical midline. NOT_APPLICABLE is for
# non-anatomical reference geometry such as masks, contours and face details.
LATERALITY = {"LEFT", "RIGHT", "CENTRAL", "BILATERAL", "NOT_APPLICABLE"}
CODE_PREFIX = "ANATOMY_VISUAL_MAP_V1:"


def v047_visual_codes(v047_path: Path, v030_path: Path) -> set[str]:
    """Resolve V047's generated codes from its stated V030 BODY_REGION source.

    V047 is authoritative: it defines the prefix and FRONT/BACK construction. V030
    merely supplies the rows V047 selects; no copied dictionary is maintained here.
    """
    v047 = v047_path.read_text(encoding="utf-8")
    required = ("anatomy-visual-map-v1", "ANATOMY_VISUAL_MAP_V1:", "view_name", "BODY_REGION:")
    if not all(part in v047 for part in required):
        raise ValueError("V047 does not contain the expected anatomy-visual-map-v1 visual-region seed")
    body_codes = sorted(set(re.findall(r"'BODY_REGION:([A-Z0-9_]+)'\s*,\s*'BODY_REGION'", v030_path.read_text(encoding="utf-8"))))
    return {f"{CODE_PREFIX}{view}:{code}" for code in body_codes for view in sorted(VIEWS)}


def validate_manifest(manifest_path: Path, source_path: Path, v047_path: Path, v030_path: Path) -> dict:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors: list[str] = []
    if manifest.get("schemaVersion") != 2: errors.append("schemaVersion must be 2")
    if not isinstance(manifest.get("geometryVersion"), str) or not manifest["geometryVersion"]: errors.append("geometryVersion is required")
    if manifest.get("visualMappingVersion") != "anatomy-visual-map-v1": errors.append("visualMappingVersion must be anatomy-visual-map-v1")
    review_stage = manifest.get("reviewStage")
    if review_stage is not None and review_stage != "SET-07A3_FINAL": errors.append("reviewStage must be SET-07A3_FINAL when present")
    actual_sha = hashlib.sha256(source_path.read_bytes()).hexdigest()
    if manifest.get("sourceSha256") != actual_sha: errors.append("sourceSha256 does not match source SVG")
    entries = manifest.get("entries")
    if not isinstance(entries, list):
        errors.append("entries must be an array"); entries = []
    try:
        visual_codes = v047_visual_codes(v047_path, v030_path)
    except ValueError as exc:
        errors.append(str(exc)); visual_codes = set()
    source_audit = audit.audit_svg(source_path)
    element_ids = set(source_audit["ids"])
    path_ids = {item["id"] for item in source_audit["paths"] if item["id"]}
    seen_paths: set[str] = set()
    for index, entry in enumerate(entries):
        prefix = f"entries[{index}]"
        if not isinstance(entry, dict): errors.append(f"{prefix} must be an object"); continue
        element_id = entry.get("sourceElementId")
        if not isinstance(element_id, str) or not element_id: errors.append(f"{prefix}.sourceElementId is required")
        elif element_id not in element_ids: errors.append(f"{prefix}.sourceElementId does not exist in source SVG: {element_id}")
        elif element_id in path_ids and element_id in seen_paths: errors.append(f"{prefix}.sourceElementId is assigned more than once: {element_id}")
        elif element_id in path_ids: seen_paths.add(element_id)
        view = entry.get("view")
        if view not in VIEWS: errors.append(f"{prefix}.view must be FRONT or BACK")
        role = entry.get("geometryRole")
        if role not in ROLES: errors.append(f"{prefix}.geometryRole is invalid")
        status = entry.get("reviewStatus")
        if status not in STATUSES: errors.append(f"{prefix}.reviewStatus is invalid")
        if review_stage == "SET-07A3_FINAL" and status in {"UNREVIEWED", "PROPOSED"}:
            errors.append(f"{prefix}.reviewStatus must be resolved in SET-07A3_FINAL")
        laterality = entry.get("laterality")
        if laterality not in LATERALITY: errors.append(f"{prefix}.laterality is required and must be valid")
        confidence = entry.get("confidence")
        if confidence not in CONFIDENCE: errors.append(f"{prefix}.confidence is required and must be HIGH, MEDIUM or LOW")
        if review_stage == "SET-07A3_FINAL" and status == "APPROVED" and confidence == "LOW":
            errors.append(f"{prefix}.APPROVED entry must not have LOW confidence in SET-07A3_FINAL")
        rationale = entry.get("rationale")
        if not isinstance(rationale, str) or not rationale.strip() or rationale != rationale.strip() or len(rationale) > 500:
            errors.append(f"{prefix}.rationale must be a trimmed non-empty string of at most 500 characters")
        if "reviewerNote" in entry:
            reviewer_note = entry["reviewerNote"]
            if not isinstance(reviewer_note, str) or not reviewer_note.strip() or len(reviewer_note) > 500:
                errors.append(f"{prefix}.reviewerNote must be a non-empty string of at most 500 characters")
        code = entry.get("visualRegionCode")
        if code is not None and code not in visual_codes: errors.append(f"{prefix}.visualRegionCode is not seeded by V047: {code}")
        if code is not None and role == "DECORATIVE": errors.append(f"{prefix}.DECORATIVE entry must not have visualRegionCode")
        if status in {"APPROVED", "PROPOSED"} and role == "INTERACTIVE" and not code:
            errors.append(f"{prefix}.{status} INTERACTIVE entry requires visualRegionCode")
        if review_stage == "SET-07A3_FINAL" and status == "APPROVED" and (role != "INTERACTIVE" or not code):
            errors.append(f"{prefix}.APPROVED entry must be INTERACTIVE with visualRegionCode in SET-07A3_FINAL")
        if review_stage == "SET-07A3_FINAL" and status == "REJECTED" and code is not None:
            errors.append(f"{prefix}.REJECTED entry must not have visualRegionCode in SET-07A3_FINAL")
        if code is not None and view in VIEWS and code.split(":")[1] != view: errors.append(f"{prefix}.view must match FRONT/BACK segment of visualRegionCode")
    missing_paths = sorted(path_ids - seen_paths)
    if missing_paths:
        errors.append(f"all source SVG paths require exactly one manifest entry; missing: {', '.join(missing_paths)}")
    return {"valid": not errors, "errors": errors, "entryCount": len(entries), "pathCount": len(path_ids), "coveredPathCount": len(seen_paths), "approvedInteractiveCount": sum(e.get("reviewStatus") == "APPROVED" and e.get("geometryRole") == "INTERACTIVE" for e in entries if isinstance(e, dict)), "proposedInteractiveCount": sum(e.get("reviewStatus") == "PROPOSED" and e.get("geometryRole") == "INTERACTIVE" for e in entries if isinstance(e, dict)), "visualRegionCodeCount": len(visual_codes), "note": "View validation compares only an explicit manifest view with the V047 code segment. It never compares the positional SVG FRONT/BACK heuristic and never infers anatomy."}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=HERE / "anatomy-geometry-manifest-v1.json")
    parser.add_argument("--source", type=Path, default=Path("docs/Muscles_front_and_back.svg"))
    parser.add_argument("--v047", type=Path, default=Path("src/main/resources/db/migration/V047__seed_anatomy_visual_mapping_v1.sql"))
    parser.add_argument("--v030", type=Path, default=Path("src/main/resources/db/migration/V030__seed_starter_anatomy_reference_v1.sql"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = validate_manifest(args.manifest, args.source, args.v047, args.v030)
    payload = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output: args.output.write_text(payload, encoding="utf-8")
    print(payload, end="")
    if not result["valid"]: raise SystemExit(1)


if __name__ == "__main__": main()
