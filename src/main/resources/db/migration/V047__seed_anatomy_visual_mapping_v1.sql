-- anatomy-visual-map-v1
-- Provenance is restricted to the published V030 moves-starter-v1 taxonomy and its explicit PART_OF paths.
-- V045 stores only a numeric version; version_number 1 is the database representation of this named seed.

INSERT INTO anatomy_reference.visual_mapping_version (id, version_number, status, approved_at)
VALUES ('d0b0ab0e-9c4e-5c70-842f-5f699d1b81d5', 1, 'APPROVED', TIMESTAMPTZ '2026-07-30 00:00:00+00');

WITH published_body_regions AS (
    SELECT code, display_name
    FROM anatomy_reference.anatomical_structure
    WHERE type = 'BODY_REGION'
      AND status = 'PUBLISHED'
      AND taxonomy_version = 1
      AND created_by_subject = 'moves-starter-v1'
), visual_region_seed AS (
    SELECT
        body_region.code,
        body_region.display_name,
        view.view_name,
        ROW_NUMBER() OVER (ORDER BY body_region.code) * 2 + view.display_offset AS display_order
    FROM published_body_regions body_region
    CROSS JOIN (VALUES ('FRONT', 0), ('BACK', 1)) AS view(view_name, display_offset)
)
INSERT INTO anatomy_reference.visual_region (
    id, code, display_name, status, view_name, layer_name, label_key, parent_region_id, display_order
)
SELECT
    (
        substr(md5('anatomy-visual-map-v1|' || view_name || '|' || code), 1, 8) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || view_name || '|' || code), 9, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || view_name || '|' || code), 13, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || view_name || '|' || code), 17, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || view_name || '|' || code), 21, 12)
    )::UUID,
    'ANATOMY_VISUAL_MAP_V1:' || view_name || ':' || replace(code, 'BODY_REGION:', ''),
    display_name,
    'ACTIVE',
    view_name,
    'BASE',
    'anatomy.visual-map-v1.' || lower(view_name) || '.' || lower(replace(code, 'BODY_REGION:', '')),
    NULL,
    display_order
FROM visual_region_seed;

WITH RECURSIVE explicit_v030_path(structure_id, ancestor_id) AS (
    SELECT structure.id, structure.id
    FROM anatomy_reference.anatomical_structure structure
    WHERE structure.status = 'PUBLISHED'
      AND structure.taxonomy_version = 1
      AND structure.created_by_subject = 'moves-starter-v1'

    UNION

    SELECT path.structure_id, relation.parent_id
    FROM explicit_v030_path path
    JOIN anatomy_reference.anatomical_structure_relation relation
      ON relation.child_id = path.ancestor_id
     AND relation.relation_type = 'PART_OF'
     AND relation.created_by_subject = 'moves-starter-v1'
), mapped_body_region AS (
    SELECT path.structure_id, body_region.code AS body_region_code
    FROM explicit_v030_path path
    JOIN anatomy_reference.anatomical_structure body_region
      ON body_region.id = path.ancestor_id
     AND body_region.type = 'BODY_REGION'
     AND body_region.status = 'PUBLISHED'
     AND body_region.taxonomy_version = 1
     AND body_region.created_by_subject = 'moves-starter-v1'
    WHERE body_region.code <> 'BODY_REGION:WHOLE_BODY'
       OR path.structure_id = body_region.id
), structure_visual_region AS (
    SELECT mapped_body_region.structure_id, visual_region.id AS visual_region_id
    FROM mapped_body_region
    JOIN anatomy_reference.visual_region visual_region
      ON visual_region.code IN (
          'ANATOMY_VISUAL_MAP_V1:FRONT:' || replace(mapped_body_region.body_region_code, 'BODY_REGION:', ''),
          'ANATOMY_VISUAL_MAP_V1:BACK:' || replace(mapped_body_region.body_region_code, 'BODY_REGION:', '')
      )
)
INSERT INTO anatomy_reference.anatomical_structure_visual_region_mapping (
    id, structure_id, mapping_version_id, visual_region_id
)
SELECT
    (
        substr(md5('anatomy-visual-map-v1|' || structure_id || '|' || visual_region_id), 1, 8) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || structure_id || '|' || visual_region_id), 9, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || structure_id || '|' || visual_region_id), 13, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || structure_id || '|' || visual_region_id), 17, 4) || '-' ||
        substr(md5('anatomy-visual-map-v1|' || structure_id || '|' || visual_region_id), 21, 12)
    )::UUID,
    structure_id,
    'd0b0ab0e-9c4e-5c70-842f-5f699d1b81d5',
    visual_region_id
FROM structure_visual_region;
