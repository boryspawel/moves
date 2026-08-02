import importlib.util
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("audit", ROOT / "audit-svg.py")
audit = importlib.util.module_from_spec(spec); spec.loader.exec_module(audit)
validator_spec = importlib.util.spec_from_file_location("manifest_validator", ROOT / "validate-geometry-manifest.py")
validator = importlib.util.module_from_spec(validator_spec); validator_spec.loader.exec_module(validator)
production_spec = importlib.util.spec_from_file_location("production_svg", ROOT / "generate-production-svg.py")
production = importlib.util.module_from_spec(production_spec); production_spec.loader.exec_module(production)

class AuditSvgTest(unittest.TestCase):
    def test_production_svg_has_two_manifest_scoped_base_and_overlay_assets(self):
        source = Path("docs/Muscles_front_and_back.svg")
        manifest = ROOT / "anatomy-geometry-manifest-v1.json"
        ledger = json.loads(manifest.read_text(encoding="utf-8"))
        self.assertEqual(ledger["sourceSha256"], hashlib.sha256(source.read_bytes()).hexdigest())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "assets"; report_path = Path(directory) / "coverage.json"
            report = production.write(output, report_path, source, manifest)
            first = {view: (output / f"anatomy-body-{view.lower()}-v1.svg").read_bytes() for view in ("FRONT", "BACK")}
            production.write(output, report_path, source, manifest)
            self.assertEqual(first, {view: (output / f"anatomy-body-{view.lower()}-v1.svg").read_bytes() for view in ("FRONT", "BACK")})
            self.assertEqual({"FRONT", "BACK"}, set(report["assets"]))
            for view in ("FRONT", "BACK"):
                root = ET.parse(output / f"anatomy-body-{view.lower()}-v1.svg").getroot()
                self.assertEqual(view, root.get("data-anatomy-view")); self.assertEqual(4, len(root.get("viewBox").split()))
                layers = {element.get("data-layer"): element for element in root if element.get("data-layer")}
                self.assertEqual({"base-silhouette", "exposure-overlay"}, set(layers))
                base, overlay = layers["base-silhouette"], layers["exposure-overlay"]
                self.assertEqual(view, base.get("data-view")); self.assertEqual(view, overlay.get("data-view"))
                base_paths = [element for element in base if element.tag.endswith("path")]
                self.assertTrue(base_paths); self.assertTrue(all(path.get("data-anatomy-geometry") == "base" and path.get("fill") == "#e4e7eb" and path.get("pointer-events") == "none" and not path.get("tabindex") and not path.get("data-visual-region-code") for path in base_paths))
                base_source_ids = [path.get("data-source-element-id") for path in base_paths]
                self.assertNotIn("path1379", base_source_ids); self.assertNotIn("path1381", base_source_ids)
                if view == "FRONT":
                    # Source uses mirror the other anatomical half. Their paths
                    # must be flattened into the base rather than left as an
                    # unresolved dependency on the source group hierarchy.
                    self.assertGreater(base_source_ids.count("path2063"), 1)
                    # Rejected-for-interaction reference geometry still completes
                    # the neutral right hand and neck of the silhouette.
                    self.assertTrue({"path987", "path991", "path1186", "path983", "path1157", "path1115", "path1129", "path1101", "path1047", "path1017", "path979", "path995", "path1803", "path1779", "path1419", "path1447"}.issubset(base_source_ids))
                groups = [element for element in overlay if element.get("data-visual-region-code")]
                approved = [entry for entry in ledger["entries"] if entry["view"] == view and entry["reviewStatus"] == "APPROVED" and entry["geometryRole"] == "INTERACTIVE"]
                self.assertEqual({entry["visualRegionCode"] for entry in approved}, {group.get("data-visual-region-code") for group in groups})
                overlay_paths = [path for group in groups for path in group if path.tag.endswith("path")]
                direct_paths = [path for path in overlay_paths if path.get("data-source-instance") == "direct"]
                mirrored_paths = [path for path in overlay_paths if path.get("data-source-instance") == "mirrored"]
                self.assertEqual({entry["sourceElementId"] for entry in approved}, {path.get("data-source-element-id") for path in direct_paths})
                self.assertTrue(mirrored_paths)
                self.assertTrue({path.get("data-source-element-id") for path in mirrored_paths}.issubset({entry["sourceElementId"] for entry in approved}))
                self.assertFalse(any(path.get("data-source-instance") == "mirrored" and next(entry for entry in approved if entry["sourceElementId"] == path.get("data-source-element-id"))["laterality"] == "CENTRAL" for path in mirrored_paths))
                by_code = {}
                for group in groups:
                    by_code.setdefault(group.get("data-visual-region-code"), set()).add(group.get("data-laterality"))
                self.assertTrue(any({"LEFT", "RIGHT"}.issubset(lateralities) for lateralities in by_code.values()))
                self.assertTrue(all(path.get("data-anatomy-geometry") == "exposure" and path.get("fill") == "var(--anatomy-region-fill, transparent)" for group in groups for path in group if path.tag.endswith("path")))
                self.assertFalse(any(token in ET.tostring(root, encoding="unicode").lower() for token in ("#f39079", "#a45e49", "#fde8cc")))
                self.assertFalse(any(element.tag.endswith(("script", "foreignObject")) for element in root.iter()))
                references, missing = production.source_references(root)
                self.assertFalse(missing, references)

    def test_transform_and_path_bbox(self):
        self.assertEqual(audit.transform_matrix("translate(2 3) scale(2)"), (2.0, 0.0, 0.0, 2.0, 2.0, 3.0))
        self.assertEqual(audit.bbox_for(__import__('xml.etree.ElementTree', fromlist=['Element']).Element('path', {'d': 'M 0 0 L 4 5'}), audit.transform_matrix('translate(1 2)'))['width'], 4.0)

    def test_audit_reports_ids_references_and_heuristic(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'sample.svg'
            source.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 20"><defs><linearGradient id="grad"/></defs><g id="outer" transform="translate(1 0)"><path id="left" d="M0 0L10 10" fill="url(#grad)"/><path d="M70 0L80 10" display="none"/></g></svg>')
            result = audit.audit_svg(source)
        self.assertEqual(result['counts']['paths'], 2)
        self.assertEqual(result['paths'][0]['sideHeuristic'], 'FRONT')
        self.assertEqual(result['paths'][1]['sideHeuristic'], 'BACK')
        self.assertTrue(result['references'])
        self.assertEqual(len(result['missingIds']), 1)
        self.assertTrue(result['invisibleElements'])

    def test_atlas_svg_has_one_static_card_per_path(self):
        generator_spec = importlib.util.spec_from_file_location('atlas', ROOT / 'generate-path-atlas.py')
        generator = importlib.util.module_from_spec(generator_spec); generator_spec.loader.exec_module(generator)
        output = generator.build_atlas_svg('<svg><path id="one" d="M0 0"/></svg>', [{"id": "one", "bbox": None, "parentGroup": None, "sideHeuristic": "FRONT"}], [0, 0, 10, 10])
        self.assertIn('1. one', output)
        self.assertIn('isolated geometry', output)
        self.assertIn('full SVG / highlighted geometry', output)

    def test_manifest_validator_accepts_v047_code_and_rejects_invalid_decisions(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "source.svg"; source.write_text('<svg xmlns="http://www.w3.org/2000/svg"><path id="path-a" d="M0 0L1 1"/></svg>')
            v047 = directory / "V047.sql"; v047.write_text("-- anatomy-visual-map-v1\nANATOMY_VISUAL_MAP_V1: view_name BODY_REGION:")
            v030 = directory / "V030.sql"; v030.write_text("('id', 'BODY_REGION:TRUNK', 'BODY_REGION', 'x')")
            manifest = directory / "manifest.json"
            valid = {"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "geometryVersion": "test-v1", "visualMappingVersion": "anatomy-visual-map-v1", "entries": [{"sourceElementId": "path-a", "view": "FRONT", "geometryRole": "INTERACTIVE", "reviewStatus": "PROPOSED", "visualRegionCode": "ANATOMY_VISUAL_MAP_V1:FRONT:TRUNK", "laterality": "CENTRAL", "confidence": "MEDIUM", "rationale": "manual proposal"}]}
            manifest.write_text(json.dumps(valid))
            self.assertTrue(validator.validate_manifest(manifest, source, v047, v030)["valid"])
            valid["entries"].append({"sourceElementId": "path-a", "view": "BACK", "geometryRole": "DECORATIVE", "reviewStatus": "APPROVED", "visualRegionCode": "ANATOMY_VISUAL_MAP_V1:FRONT:TRUNK"})
            manifest.write_text(json.dumps(valid))
            errors = validator.validate_manifest(manifest, source, v047, v030)["errors"]
            self.assertTrue(any("assigned more than once" in error for error in errors))
            self.assertTrue(any("DECORATIVE" in error for error in errors))
            self.assertTrue(any("must match" in error for error in errors))

    def test_manifest_validator_reviewer_note_must_be_concise_nonempty_string(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "source.svg"; source.write_text('<svg xmlns="http://www.w3.org/2000/svg"><path id="path-a" d="M0 0L1 1"/></svg>')
            v047 = directory / "V047.sql"; v047.write_text("-- anatomy-visual-map-v1\nANATOMY_VISUAL_MAP_V1: view_name BODY_REGION:")
            v030 = directory / "V030.sql"; v030.write_text("('id', 'BODY_REGION:TRUNK', 'BODY_REGION', 'x')")
            manifest = directory / "manifest.json"
            base = {"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "geometryVersion": "test-v1", "visualMappingVersion": "anatomy-visual-map-v1", "entries": [{"sourceElementId": "path-a", "view": "FRONT", "geometryRole": "SUPPORT", "reviewStatus": "AMBIGUOUS", "laterality": "NOT_APPLICABLE", "confidence": "LOW", "rationale": "manual review required"}]}
            for note in ("", "   ", 7, "x" * 501):
                base["entries"][0]["reviewerNote"] = note
                manifest.write_text(json.dumps(base))
                self.assertTrue(any("reviewerNote" in error for error in validator.validate_manifest(manifest, source, v047, v030)["errors"]))
            base["entries"][0]["reviewerNote"] = "manual review required"
            manifest.write_text(json.dumps(base))
            self.assertTrue(validator.validate_manifest(manifest, source, v047, v030)["valid"])

    def test_manifest_validator_requires_proposal_metadata_and_full_path_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "source.svg"; source.write_text('<svg xmlns="http://www.w3.org/2000/svg"><path id="path-a" d="M0 0L1 1"/><path id="path-b" d="M1 1L2 2"/></svg>')
            v047 = directory / "V047.sql"; v047.write_text("-- anatomy-visual-map-v1\nANATOMY_VISUAL_MAP_V1: view_name BODY_REGION:")
            v030 = directory / "V030.sql"; v030.write_text("('id', 'BODY_REGION:TRUNK', 'BODY_REGION', 'x')")
            manifest = directory / "manifest.json"
            entry = {"sourceElementId": "path-a", "view": "FRONT", "geometryRole": "INTERACTIVE", "reviewStatus": "PROPOSED", "laterality": "CENTRAL", "confidence": "MEDIUM", "rationale": "  untrimmed rationale  "}
            manifest.write_text(json.dumps({"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "geometryVersion": "test-v1", "visualMappingVersion": "anatomy-visual-map-v1", "entries": [entry]}))
            errors = validator.validate_manifest(manifest, source, v047, v030)["errors"]
            self.assertTrue(any("PROPOSED INTERACTIVE" in error for error in errors))
            self.assertTrue(any("rationale" in error for error in errors))
            self.assertTrue(any("missing" in error for error in errors))

    def test_manifest_validation_never_uses_positional_heuristic_for_view(self):
        validator_source = (ROOT / "validate-geometry-manifest.py").read_text(encoding="utf-8")
        self.assertNotIn("sideHeuristic", validator_source)
        self.assertIn("code.split(\":\")[1] != view", validator_source)
        generator_source = (ROOT / "generate-path-atlas.py").read_text(encoding="utf-8")
        self.assertIn('filter-status', generator_source)
        self.assertIn('filter-view', generator_source)
        self.assertIn('filter-region', generator_source)

    def test_final_review_stage_requires_resolved_non_low_approval(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "source.svg"; source.write_text('<svg xmlns="http://www.w3.org/2000/svg"><path id="path-a" d="M0 0"/></svg>')
            v047 = directory / "V047.sql"; v047.write_text("-- anatomy-visual-map-v1\nANATOMY_VISUAL_MAP_V1: view_name BODY_REGION:")
            v030 = directory / "V030.sql"; v030.write_text("('id', 'BODY_REGION:TRUNK', 'BODY_REGION', 'x')")
            manifest = directory / "manifest.json"
            entry = {"sourceElementId": "path-a", "view": "FRONT", "geometryRole": "INTERACTIVE", "reviewStatus": "APPROVED", "visualRegionCode": "ANATOMY_VISUAL_MAP_V1:FRONT:TRUNK", "laterality": "CENTRAL", "confidence": "LOW", "rationale": "manual decision"}
            payload = {"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "geometryVersion": "test-v1", "visualMappingVersion": "anatomy-visual-map-v1", "reviewStage": "SET-07A3_FINAL", "entries": [entry]}
            manifest.write_text(json.dumps(payload))
            errors = validator.validate_manifest(manifest, source, v047, v030)["errors"]
            self.assertTrue(any("must not have LOW confidence" in error for error in errors))
            entry["confidence"] = "HIGH"; entry["reviewStatus"] = "PROPOSED"
            manifest.write_text(json.dumps(payload))
            errors = validator.validate_manifest(manifest, source, v047, v030)["errors"]
            self.assertTrue(any("must be resolved" in error for error in errors))
            entry["reviewStatus"] = "REJECTED"
            entry.pop("visualRegionCode")
            manifest.write_text(json.dumps(payload))
            self.assertTrue(validator.validate_manifest(manifest, source, v047, v030)["valid"])
            entry["visualRegionCode"] = "ANATOMY_VISUAL_MAP_V1:FRONT:TRUNK"
            manifest.write_text(json.dumps(payload))
            errors = validator.validate_manifest(manifest, source, v047, v030)["errors"]
            self.assertTrue(any("REJECTED entry must not have visualRegionCode" in error for error in errors))

    def test_audit_json_is_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "stable.svg"
            source.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path id="stable" d="M0 0L2 2"/></svg>')
            first = json.dumps(audit.audit_svg(source), sort_keys=True, separators=(",", ":"))
            second = json.dumps(audit.audit_svg(source), sort_keys=True, separators=(",", ":"))
        self.assertEqual(first, second)

    def test_generator_artifacts_are_byte_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            source = directory / "source.svg"
            source.write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path id="stable" d="M0 0L2 2"/></svg>')
            manifest = directory / "manifest.json"
            manifest.write_text(json.dumps({"schemaVersion": 2, "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "geometryVersion": "test-v1", "visualMappingVersion": "anatomy-visual-map-v1", "entries": []}))
            first, second = directory / "first", directory / "second"
            command = [sys.executable, str(ROOT / "generate-path-atlas.py"), "--source", str(source), "--manifest", str(manifest)]
            subprocess.run(command + ["--report-dir", str(first), "--docs-output", str(directory / "first.md")], check=True, capture_output=True, text=True)
            subprocess.run(command + ["--report-dir", str(second), "--docs-output", str(directory / "second.md")], check=True, capture_output=True, text=True)
            for filename in ("anatomy-svg-audit.json", "anatomy-path-atlas.html", "anatomy-path-atlas.svg", "anatomy-svg-source.png", "anatomy-path-atlas.png"):
                self.assertEqual((first / filename).read_bytes(), (second / filename).read_bytes(), filename)
            self.assertEqual((directory / "first.md").read_bytes(), (directory / "second.md").read_bytes())

if __name__ == '__main__': unittest.main()
