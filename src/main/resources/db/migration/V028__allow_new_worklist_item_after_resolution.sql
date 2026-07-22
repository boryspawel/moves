ALTER TABLE specialist.worklist_item
    DROP CONSTRAINT worklist_item_deduplication_key_key;

CREATE UNIQUE INDEX uq_specialist_worklist_item_active_deduplication_key
    ON specialist.worklist_item (deduplication_key)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED');
