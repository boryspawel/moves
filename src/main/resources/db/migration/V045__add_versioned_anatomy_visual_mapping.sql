CREATE TABLE anatomy_reference.visual_region (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    CONSTRAINT ck_visual_region_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE anatomy_reference.visual_mapping_version (
    id UUID PRIMARY KEY,
    version_number BIGINT NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    approved_at TIMESTAMPTZ,
    CONSTRAINT ck_visual_mapping_version_positive CHECK (version_number > 0),
    CONSTRAINT ck_visual_mapping_version_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_visual_mapping_version_approved_at CHECK ((status = 'APPROVED' AND approved_at IS NOT NULL) OR status <> 'APPROVED')
);

CREATE TABLE anatomy_reference.anatomical_structure_visual_region_mapping (
    id UUID PRIMARY KEY,
    structure_id UUID NOT NULL REFERENCES anatomy_reference.anatomical_structure(id),
    mapping_version_id UUID NOT NULL REFERENCES anatomy_reference.visual_mapping_version(id),
    visual_region_id UUID NOT NULL REFERENCES anatomy_reference.visual_region(id),
    CONSTRAINT uq_structure_visual_mapping UNIQUE (structure_id, mapping_version_id, visual_region_id)
);
CREATE INDEX ix_structure_visual_mapping_structure
    ON anatomy_reference.anatomical_structure_visual_region_mapping (structure_id, mapping_version_id);
