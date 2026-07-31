# Anatomy SVG source audit

- Source: `docs/Muscles_front_and_back.svg` (preserved unchanged)
- SHA-256: `56aae1c8068df6bcbbaec2faf074df82581d997afc5bd45912efc555b624e5f3`
- Renderer: `ImageMagick convert`
- Elements: 487; paths: 176; groups: 15; uses: 8; clipPaths: 4; gradients: 89
- Missing IDs on relevant/graphic elements: 0; duplicate IDs: 0; invisible elements: 0.

Bounding boxes are approximate. FRONT/BACK labels use horizontal position only and must not be interpreted as anatomical mapping.

## SET-07A3 independent geometry verification

`tools/anatomy-svg/anatomy-geometry-manifest-v1.json` is a finalized review ledger bound to this source SHA-256 and `anatomy-visual-map-v1`. It contains 176/176 source-path entries: APPROVED 39, AMBIGUOUS 92, REJECTED 45, PROPOSED 0, UNREVIEWED 0; roles INTERACTIVE 68, DECORATIVE 43, SUPPORT 27, REFERENCE_ONLY 38; confidence HIGH 84, MEDIUM 2, LOW 90.

There are 39 approved interactive elements covering 12/32 V047 codes: `ANATOMY_VISUAL_MAP_V1:BACK:CERVICAL`, `ANATOMY_VISUAL_MAP_V1:BACK:LOWER_LEG`, `ANATOMY_VISUAL_MAP_V1:BACK:SHOULDER_GIRDLE`, `ANATOMY_VISUAL_MAP_V1:BACK:THIGH`, `ANATOMY_VISUAL_MAP_V1:BACK:THORACIC`, `ANATOMY_VISUAL_MAP_V1:BACK:UPPER_ARM`, `ANATOMY_VISUAL_MAP_V1:BACK:UPPER_LIMB`, `ANATOMY_VISUAL_MAP_V1:FRONT:FOREARM_WRIST`, `ANATOMY_VISUAL_MAP_V1:FRONT:LOWER_LEG`, `ANATOMY_VISUAL_MAP_V1:FRONT:THIGH`, `ANATOMY_VISUAL_MAP_V1:FRONT:THORACIC`, `ANATOMY_VISUAL_MAP_V1:FRONT:UPPER_ARM`. Regions without approved geometry: `ANATOMY_VISUAL_MAP_V1:BACK:ABDOMINOPELVIC`, `ANATOMY_VISUAL_MAP_V1:BACK:FOOT_ANKLE`, `ANATOMY_VISUAL_MAP_V1:BACK:FOREARM_WRIST`, `ANATOMY_VISUAL_MAP_V1:BACK:HIP_PELVIS`, `ANATOMY_VISUAL_MAP_V1:BACK:KNEE`, `ANATOMY_VISUAL_MAP_V1:BACK:LOWER_LIMB`, `ANATOMY_VISUAL_MAP_V1:BACK:LUMBAR`, `ANATOMY_VISUAL_MAP_V1:BACK:TRUNK`, `ANATOMY_VISUAL_MAP_V1:BACK:WHOLE_BODY`, `ANATOMY_VISUAL_MAP_V1:FRONT:ABDOMINOPELVIC`, `ANATOMY_VISUAL_MAP_V1:FRONT:CERVICAL`, `ANATOMY_VISUAL_MAP_V1:FRONT:FOOT_ANKLE`, `ANATOMY_VISUAL_MAP_V1:FRONT:HIP_PELVIS`, `ANATOMY_VISUAL_MAP_V1:FRONT:KNEE`, `ANATOMY_VISUAL_MAP_V1:FRONT:LOWER_LIMB`, `ANATOMY_VISUAL_MAP_V1:FRONT:LUMBAR`, `ANATOMY_VISUAL_MAP_V1:FRONT:SHOULDER_GIRDLE`, `ANATOMY_VISUAL_MAP_V1:FRONT:TRUNK`, `ANATOMY_VISUAL_MAP_V1:FRONT:UPPER_LIMB`, `ANATOMY_VISUAL_MAP_V1:FRONT:WHOLE_BODY`.

The 92 ambiguous entries deliberately retain no forced V047 code where geometry crosses a region boundary, is too small, or would cause misleading highlighting. The 45 rejected entries are noninteractive face, hand, foot, contour, construction and technical-detail geometry; their role is preserved, but they cannot be used as V047 interaction targets. The formerly proposed entries resolved as ambiguous are `path1366`, `rect1075`, `path1751`, `path988`, `path899`, `path1012`, `path3511`, `path1955` and `path2358`.

## SET-07A4 partial production asset

`tools/anatomy-svg/generate-production-svg.py` deterministically generates `web/src/assets/anatomy/anatomy-body-partial-v1.svg` and `tools/anatomy-svg/anatomy-production-coverage-v1.json`. The artifact is explicitly marked **PARTIAL TECHNICAL MAP**. It exposes stable semantic groups only for the 39 `APPROVED + INTERACTIVE` paths: 12/32 V047 regions. Each group has its `data-visual-region-code`, view and laterality; its paths use `fill: var(--anatomy-region-fill, currentColor)`.

The remaining 20 regions are `NO_GEOMETRY` and are available only in the table/API metadata: no anatomy mapping is invented in the SVG. All 92 `AMBIGUOUS` paths are retained as neutral, noninteractive silhouette/detail geometry. The 45 `REJECTED` paths are retained as neutral visual or technical-integrity geometry (zero are removed); they carry no visual-region code. The generator preserves required gradients, clip paths and internal references, removes editor metadata, `script`, `foreignObject`, event attributes and external/javascript href values.

This is not a complete anatomical model. SET-07B may use this asset only as a technical prototype, with a clear partial-map presentation.

V1 remains a partial technical map: it does not establish complete body coverage and needs final human review before production SVG or UI integration. `reviewStage: SET-07A3_FINAL` forbids `PROPOSED`/`UNREVIEWED`, LOW-confidence approvals and noninteractive approvals; `REJECTED` entries must have no V047 code.

The contract requires `laterality` (`LEFT`, `RIGHT`, `CENTRAL`, `BILATERAL`, or `NOT_APPLICABLE`), confidence and a concise rationale. Use `validate-geometry-manifest.py` before accepting manual entries. The validator reads the V047 visual-region seed (with its V030 source rows) rather than a local frontend dictionary. It validates an explicit manifest view only against the FRONT/BACK segment of an explicit V047 code; it never validates against the positional SVG heuristic. The architecture boundary remains `anatomical structure → visualRegionCode → SVG geometry`.
