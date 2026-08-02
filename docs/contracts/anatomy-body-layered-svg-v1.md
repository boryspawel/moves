# Anatomy layered body SVG v1

`tools/anatomy-svg/generate-production-svg.py` deterministically produces:

- `web/src/assets/anatomy/anatomy-body-front-v1.svg`
- `web/src/assets/anatomy/anatomy-body-back-v1.svg`

Each asset has one root `viewBox`, `data-anatomy-view` matching its filename,
and exactly two direct runtime layers:

- `#base-silhouette[data-layer="base-silhouette"]` contains neutral,
  noninteractive display clones selected only from manifest entries of that
  view. Noninteractive support/reference geometry may remain in the base even
  when rejected for V047 interaction; the two audited construction axes
  (`path1379`, `path1381`) remain absent. Mirrored source `<use>` composites are flattened into neutral
  paths using the manifest view of their referenced subtree.
- `#exposure-overlay[data-layer="exposure-overlay"]` contains only groups for
  `APPROVED + INTERACTIVE` manifest entries.  A group has the exact
  `data-visual-region-code` and explicit `data-laterality`. Direct paths retain
  `data-source-instance="direct"`; manifest-classified mirrored source
  composites retain `data-source-instance="mirrored"` with LEFT/RIGHT swapped.
  CENTRAL geometry is not mirrored.

Technical source references are copied into `<defs>` only when required by a
selected display clone.  They are not visual layer geometry.  Runtime view
selection must select an entire asset; it must not infer FRONT/BACK from SVG
coordinates, IDs, or element names.
