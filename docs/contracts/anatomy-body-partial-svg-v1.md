# Anatomy body partial SVG v1 contract

`web/src/assets/anatomy/anatomy-body-partial-v1.svg` is a **PARTIAL TECHNICAL MAP**, generated only by `tools/anatomy-svg/generate-production-svg.py` from the immutable source SVG and the SET-07A3 manifest.

Only `APPROVED + INTERACTIVE` geometry may appear below a semantic group:

```xml
<g id="visual-region-front-thigh"
   data-visual-region-code="ANATOMY_VISUAL_MAP_V1:FRONT:THIGH"
   data-view="FRONT"
   data-laterality="RIGHT">
```

The IDs are stable derivations of the V047 code. Interactive paths use `fill: var(--anatomy-region-fill, currentColor)`. Consumers may style those groups; they must not infer codes from paths, coordinates, labels or colors.

Ambiguous and rejected source geometry is neutral and noninteractive, and has no `data-visual-region-code`. The asset preserves the source `viewBox`, both FRONT and BACK views, gradients, clipping and internal references. It must contain no scripts, event handlers, `foreignObject`, external hrefs or `javascript:` values.

The SVG covers 12/32 V047 regions; 20 regions are table-only `NO_GEOMETRY`. It is not a complete anatomical model. SET-07B may consume it solely as an accessible technical prototype that says it is partial.

## SET-06B data boundary

The SVG is geometry only. A consumer must use `AnatomyAnalysisView.visualRegionExposures`
as its sole data input and join it only by `visualRegionCode`; it must not map structures,
names, SVG paths, labels or colors to a region. The backend supplies the versioned mapping,
concentration band and all shares. The 20 V047 regions with `NO_GEOMETRY` remain in the
textual result list and are never fabricated in SVG. This partial asset is not a complete
anatomical model and must not be interpreted as biomechanical load, clinical risk or force.

`visualMappingVersion` is a non-null snapshot token (`"1"`, `"MIXED"` or
`"UNAVAILABLE"`); each exposure additionally carries its exact numeric `mappingVersion`.

## SET-07B UI consumer

`app-body-map` joins only an exact `VisualRegionExposure.visualRegionCode` to a
`data-visual-region-code` group. It does not derive regions from structures or names, and
does not calculate shares or concentration levels. SVG is loaded as a controlled static
asset and only those semantic groups receive keyboard interaction. Results without a group
remain in the full textual list with `Brak geometrii w mapie V1`. The UI displays the
persisted `visualMappingVersion`, so a published or historical analysis is never
reinterpreted using a current mapping. The presentation is qualitative and is not a
biomechanical load, clinical-risk, force, or safety assessment.
