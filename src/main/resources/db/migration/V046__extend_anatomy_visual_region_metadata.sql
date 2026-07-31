ALTER TABLE anatomy_reference.visual_region
    ADD COLUMN view_name VARCHAR(80) NOT NULL DEFAULT 'BODY',
    ADD COLUMN layer_name VARCHAR(80) NOT NULL DEFAULT 'BASE',
    ADD COLUMN label_key VARCHAR(160),
    ADD COLUMN parent_region_id UUID REFERENCES anatomy_reference.visual_region(id),
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

UPDATE anatomy_reference.visual_region
SET label_key = code
WHERE label_key IS NULL;

ALTER TABLE anatomy_reference.visual_region
    ALTER COLUMN label_key SET NOT NULL;

ALTER TABLE anatomy_reference.visual_region
    ADD CONSTRAINT ck_visual_region_display_order CHECK (display_order >= 0);
CREATE INDEX ix_visual_region_active_view_layer_order
    ON anatomy_reference.visual_region (status, view_name, layer_name, display_order, code);
