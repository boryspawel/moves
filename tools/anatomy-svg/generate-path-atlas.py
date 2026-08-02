#!/usr/bin/env python3
"""Generate a review atlas and PNG previews without modifying the SVG source."""
from __future__ import annotations
import argparse, html, importlib.util, json, shutil, subprocess
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("svg_audit", HERE / "audit-svg.py")
audit = importlib.util.module_from_spec(spec); assert spec.loader; spec.loader.exec_module(audit)

def render_png(input_path, output_path, kind):
    convert = shutil.which("convert")
    if not convert: raise RuntimeError("No local SVG renderer found on PATH; install ImageMagick (convert) to generate PNG previews.")
    # A fixed density avoids ImageMagick's pixel-cache limit for the long atlas.
    # ImageMagick otherwise emits run-time PNG chunks (notably tIME), making a
    # geometrically identical review artifact differ byte-for-byte on each run.
    command = [convert, "-density", "48", str(input_path), "-strip", "-define", "png:exclude-chunk=all", str(output_path)]
    completed = subprocess.run(command, capture_output=True, text=True)
    if completed.returncode or not output_path.exists(): raise RuntimeError(f"ImageMagick could not render {kind}: {completed.stderr.strip()}")
    return "ImageMagick convert"

def source_inner_svg(source_text):
    """Return source children for a hidden reusable SVG library, without writing it."""
    return source_text[source_text.find(">", source_text.find("<svg")) + 1:source_text.rfind("</svg>")]

def visual_card(path, index, source_id, width=900, height=330):
    label = html.escape(path["id"] or "(no ID)")
    details = html.escape(f"bbox={path['bbox']} | group={path['parentGroup']} | {path['sideHeuristic']}")
    selected = f'<use href="#{html.escape(path["id"])}" class="selected"/>' if path["id"] else '<text x="12" y="90">No ID: isolated highlight unavailable</text>'
    return f'''<g transform="translate(0 {(index-1)*height})"><rect width="{width}" height="{height-10}" class="card"/><text x="12" y="25" class="title">{index}. {label}</text><text x="12" y="48" class="detail">{details}</text><text x="12" y="70" class="detail">anatomyCode: __________   review status: __________</text><g transform="translate(0 80) scale(.30)"><use href="#{source_id}"/></g><g transform="translate(440 80) scale(.30)" class="context"><use href="#{source_id}"/><g>{selected}</g></g><text x="12" y="310" class="detail">isolated geometry</text><text x="452" y="310" class="detail">full SVG / highlighted geometry</text></g>'''

def build_atlas_svg(source_text, paths, viewbox):
    source_id = "atlas-source-library"; card_height = 330; width = 900
    library = source_inner_svg(source_text)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{width}" height="{len(paths)*card_height}" viewBox="0 0 {width} {len(paths)*card_height}"><style>.card{{fill:#fff;stroke:#aaa}}.title{{font:700 16px sans-serif}}.detail{{font:12px sans-serif}}.context use{{opacity:.1}}.selected{{opacity:1!important;fill:#ff3b30!important;stroke:#111!important;stroke-width:1!important}}</style><defs><g id="{source_id}">{library}</g></defs>{''.join(visual_card(p, i, source_id, width, card_height) for i,p in enumerate(paths, 1))}</svg>'''

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--source", type=Path, default=Path("docs/Muscles_front_and_back.svg")); parser.add_argument("--manifest", type=Path, default=HERE / "anatomy-geometry-manifest-v1.json"); parser.add_argument("--report-dir", type=Path, default=Path("build/reports")); parser.add_argument("--docs-output", type=Path, default=Path("docs/anatomy-svg-source-report.md")); args = parser.parse_args()
    report = audit.audit_svg(args.source); args.report_dir.mkdir(parents=True, exist_ok=True)
    audit_path = args.report_dir / "anatomy-svg-audit.json"; audit_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    source_text = args.source.read_text(encoding="utf-8")
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    decisions = {entry["sourceElementId"]: entry for entry in manifest.get("entries", []) if isinstance(entry, dict) and entry.get("sourceElementId")}
    status_counts = Counter(entry.get("reviewStatus", "UNREVIEWED") for entry in decisions.values())
    role_counts = Counter(entry.get("geometryRole", "—") for entry in decisions.values())
    confidence_counts = Counter(entry.get("confidence", "—") for entry in decisions.values())
    mapped_regions = sorted({entry["visualRegionCode"] for entry in decisions.values() if entry.get("visualRegionCode")})
    approved_interactive = [entry for entry in decisions.values() if entry.get("reviewStatus") == "APPROVED" and entry.get("geometryRole") == "INTERACTIVE"]
    approved_regions = sorted({entry["visualRegionCode"] for entry in approved_interactive})
    all_v047_regions = {
        f"ANATOMY_VISUAL_MAP_V1:{view}:{region}"
        for view in ("FRONT", "BACK")
        for region in ("ABDOMINOPELVIC", "CERVICAL", "FOOT_ANKLE", "FOREARM_WRIST", "HIP_PELVIS", "KNEE", "LOWER_LEG", "LOWER_LIMB", "LUMBAR", "SHOULDER_GIRDLE", "THIGH", "THORACIC", "TRUNK", "UPPER_ARM", "UPPER_LIMB", "WHOLE_BODY")
    }
    regions_without_approved_geometry = sorted(all_v047_regions - set(approved_regions))
    cards = []
    for index, path in enumerate(report["paths"], 1):
        path_id = path["id"] or f"atlas-path-{index}"
        label = path["id"] or "(no ID)"
        decision = decisions.get(path["id"], {})
        status = decision.get("reviewStatus", "UNREVIEWED (no manifest decision)")
        manifest_view = decision.get("view", "—")
        region = decision.get("visualRegionCode", "—")
        role = decision.get("geometryRole", "—")
        confidence = decision.get("confidence", "—")
        rationale = decision.get("rationale", "—")
        isolated = f'<svg viewBox="0 0 {report["viewBox"][2]} {report["viewBox"][3]}"><use href="#{html.escape(path_id)}"/></svg>' if path["id"] else "<p>Path without ID; see source context.</p>"
        highlighted = f'<svg viewBox="0 0 {report["viewBox"][2]} {report["viewBox"][3]}" class="context"><use href="#atlas-source-library"/><use href="#{html.escape(path_id)}" class="selected"/></svg>' if path["id"] else '<p>No ID: highlight unavailable.</p>'
        cards.append(f'''<article data-status="{html.escape(status)}" data-view="{html.escape(manifest_view)}" data-region="{html.escape(region)}" data-confidence="{html.escape(confidence)}"><h2>{index}. {html.escape(label)}</h2><dl><dt>ID</dt><dd>{html.escape(label)}</dd><dt>Bounding box</dt><dd>{html.escape(json.dumps(path["bbox"]))}</dd><dt>Parent group</dt><dd>{html.escape(str(path["parentGroup"]))}</dd><dt>Manifest status</dt><dd>{html.escape(status)}</dd><dt>Manifest view</dt><dd>{html.escape(manifest_view)}</dd><dt>Geometry role</dt><dd>{html.escape(role)}</dd><dt>visualRegionCode</dt><dd>{html.escape(region)}</dd><dt>Confidence</dt><dd>{html.escape(confidence)}</dd><dt>Rationale</dt><dd>{html.escape(rationale)}</dd><dt>Side heuristic</dt><dd>{path["sideHeuristic"]} (nonbinding)</dd></dl><label>anatomyCode <input name="anatomyCode" value="{html.escape(region)}"></label><label>review status <input name="reviewStatus" value="{html.escape(status)}"></label><div class="preview">{isolated}{highlighted}</div></article>''')
    atlas_path = args.report_dir / "anatomy-path-atlas.html"
    atlas_html = '''<!doctype html><meta charset=utf-8><title>Anatomy SVG path atlas</title><style>body{font:14px system-ui;margin:20px;background:#f5f5f5}article{background:#fff;padding:16px;margin:16px 0;border-radius:8px}h2{margin-top:0}dl{display:grid;grid-template-columns:160px 1fr}dt{font-weight:600}label{display:inline-block;margin:8px 18px 8px 0}input,select{display:block;width:260px}.filters{display:flex;gap:12px;flex-wrap:wrap}.preview{display:flex;gap:12px}.preview svg{width:45%;height:360px;border:1px solid #ddd}.context use{opacity:.1}.selected{opacity:1!important;fill:#ff3b30!important;stroke:#111!important;stroke-width:1!important}</style><svg aria-hidden="true" width="0" height="0"><defs><g id="atlas-source-library">''' + source_inner_svg(source_text) + '''</g></defs></svg><h1>Anatomy path atlas</h1><p>Manifest: ''' + html.escape(str(args.manifest)) + '''. SET-07A3 records independently reviewed decisions; approved mappings remain subject to final human verification before production use. Positional FRONT/BACK is a nonbinding review aid; manifest view is shown separately.</p><div class="filters"><label>Status <select id="filter-status"><option value="">All</option><option>UNREVIEWED (no manifest decision)</option><option>UNREVIEWED</option><option>PROPOSED</option><option>APPROVED</option><option>AMBIGUOUS</option><option>REJECTED</option></select></label><label>Manifest view <select id="filter-view"><option value="">All</option><option>FRONT</option><option>BACK</option><option>—</option></select></label><label>visualRegionCode <input id="filter-region" placeholder="exact code or fragment"></label><label>Confidence <select id="filter-confidence"><option value="">All</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option><option>—</option></select></label></div><script>function filterCards(){const s=document.querySelector('#filter-status').value,v=document.querySelector('#filter-view').value,r=document.querySelector('#filter-region').value.toLowerCase(),c=document.querySelector('#filter-confidence').value;document.querySelectorAll('article').forEach(a=>a.hidden=(s&&a.dataset.status!==s)||(v&&a.dataset.view!==v)||(r&&!a.dataset.region.toLowerCase().includes(r))||(c&&a.dataset.confidence!==c));}document.querySelectorAll('.filters input,.filters select').forEach(e=>e.addEventListener('input',filterCards));</script>''' + "\n".join(cards)
    atlas_path.write_text(atlas_html, encoding="utf-8")
    atlas_svg_path = args.report_dir / "anatomy-path-atlas.svg"; atlas_svg_path.write_text(build_atlas_svg(source_text, report["paths"], report["viewBox"]), encoding="utf-8")
    renderer = render_png(args.source, args.report_dir / "anatomy-svg-source.png", "source SVG")
    render_png(atlas_svg_path, args.report_dir / "anatomy-path-atlas.png", "atlas SVG")
    approved_text = ", ".join(f"`{code}`" for code in approved_regions)
    missing_text = ", ".join(f"`{code}`" for code in regions_without_approved_geometry)
    args.docs_output.write_text(
        f"""# Anatomy SVG source audit

- Source: `{args.source}` (preserved unchanged)
- SHA-256: `{report['sha256']}`
- Renderer: `{renderer}`
- Elements: {report['counts']['elements']}; paths: {report['counts']['paths']}; groups: {report['counts']['groups']}; uses: {report['counts']['uses']}; clipPaths: {report['counts']['clipPaths']}; gradients: {report['counts']['gradients']}
- Missing IDs on relevant/graphic elements: {len(report['missingIds'])}; duplicate IDs: {len(report['duplicateIds'])}; invisible elements: {len(report['invisibleElements'])}.

Bounding boxes are approximate. FRONT/BACK labels use horizontal position only and must not be interpreted as anatomical mapping.

## SET-07A3 independent geometry verification

`tools/anatomy-svg/anatomy-geometry-manifest-v1.json` is a finalized review ledger bound to this source SHA-256 and `anatomy-visual-map-v1`. It contains {len(decisions)}/{report['counts']['paths']} source-path entries: APPROVED {status_counts['APPROVED']}, AMBIGUOUS {status_counts['AMBIGUOUS']}, REJECTED {status_counts['REJECTED']}, PROPOSED {status_counts['PROPOSED']}, UNREVIEWED {status_counts['UNREVIEWED']}; roles INTERACTIVE {role_counts['INTERACTIVE']}, DECORATIVE {role_counts['DECORATIVE']}, SUPPORT {role_counts['SUPPORT']}, REFERENCE_ONLY {role_counts['REFERENCE_ONLY']}; confidence HIGH {confidence_counts['HIGH']}, MEDIUM {confidence_counts['MEDIUM']}, LOW {confidence_counts['LOW']}.

There are {len(approved_interactive)} approved interactive elements covering {len(approved_regions)}/{len(all_v047_regions)} V047 codes: {approved_text}. Regions without approved geometry: {missing_text}.

The {status_counts['AMBIGUOUS']} ambiguous entries deliberately retain no forced V047 code where geometry crosses a region boundary, is too small, or would cause misleading highlighting. The {status_counts['REJECTED']} rejected entries are noninteractive face, hand, foot, contour, construction and technical-detail geometry; their role is preserved, but they cannot be used as V047 interaction targets. The formerly proposed entries resolved as ambiguous are `path1366`, `rect1075`, `path1751`, `path988`, `path899`, `path1012`, `path3511`, `path1955` and `path2358`.

V1 remains a partial technical map: it does not establish complete body coverage and needs final human review before production SVG or UI integration. `reviewStage: SET-07A3_FINAL` forbids `PROPOSED`/`UNREVIEWED`, LOW-confidence approvals and noninteractive approvals; `REJECTED` entries must have no V047 code.

The contract requires `laterality` (`LEFT`, `RIGHT`, `CENTRAL`, `BILATERAL`, or `NOT_APPLICABLE`), confidence and a concise rationale. Use `validate-geometry-manifest.py` before accepting manual entries. The validator reads the V047 visual-region seed (with its V030 source rows) rather than a local frontend dictionary. It validates an explicit manifest view only against the FRONT/BACK segment of an explicit V047 code; it never validates against the positional SVG heuristic. The architecture boundary remains `anatomical structure → visualRegionCode → SVG geometry`.
""",
        encoding="utf-8",
    )

if __name__ == "__main__": main()
