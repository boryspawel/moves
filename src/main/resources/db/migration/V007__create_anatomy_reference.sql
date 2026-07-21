CREATE SCHEMA anatomy_reference;

CREATE TABLE anatomy_reference.anatomical_structure (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    type VARCHAR(32) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    side_policy VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    external_ontology VARCHAR(120),
    external_ontology_id VARCHAR(200),
    taxonomy_version INTEGER NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_anatomical_structure_code UNIQUE (code),
    CONSTRAINT ck_anatomical_structure_type
        CHECK (type IN ('BODY_REGION', 'MUSCLE_GROUP', 'MUSCLE', 'TENDON_GROUP', 'JOINT')),
    CONSTRAINT ck_anatomical_structure_side_policy
        CHECK (side_policy IN ('NONE', 'LEFT_RIGHT')),
    CONSTRAINT ck_anatomical_structure_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT ck_anatomical_structure_taxonomy_version CHECK (taxonomy_version > 0),
    CONSTRAINT ck_anatomical_structure_external_reference CHECK (
        (external_ontology IS NULL AND external_ontology_id IS NULL)
        OR (external_ontology IS NOT NULL AND external_ontology_id IS NOT NULL)
    ),
    CONSTRAINT ck_anatomical_structure_publication_timestamps CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND withdrawn_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND published_at IS NOT NULL AND withdrawn_at IS NOT NULL)
    )
);
CREATE INDEX ix_anatomical_structure_type
    ON anatomy_reference.anatomical_structure (type, code);
CREATE INDEX ix_anatomical_structure_status
    ON anatomy_reference.anatomical_structure (status, code);

CREATE TABLE anatomy_reference.anatomical_structure_relation (
    id UUID PRIMARY KEY,
    parent_id UUID NOT NULL REFERENCES anatomy_reference.anatomical_structure (id),
    child_id UUID NOT NULL REFERENCES anatomy_reference.anatomical_structure (id),
    relation_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    CONSTRAINT uq_anatomical_structure_relation UNIQUE (parent_id, child_id, relation_type),
    CONSTRAINT ck_anatomical_structure_relation_distinct CHECK (parent_id <> child_id),
    CONSTRAINT ck_anatomical_structure_relation_type
        CHECK (relation_type IN ('PART_OF', 'MEMBER_OF', 'FUNCTIONALLY_GROUPED_AS'))
);
CREATE INDEX ix_anatomical_structure_relation_parent
    ON anatomy_reference.anatomical_structure_relation (parent_id, child_id);
CREATE INDEX ix_anatomical_structure_relation_child
    ON anatomy_reference.anatomical_structure_relation (child_id, parent_id);

CREATE TABLE anatomy_reference.hierarchy_guard (
    id SMALLINT PRIMARY KEY,
    CONSTRAINT ck_anatomy_hierarchy_guard_singleton CHECK (id = 1)
);
INSERT INTO anatomy_reference.hierarchy_guard (id) VALUES (1);
