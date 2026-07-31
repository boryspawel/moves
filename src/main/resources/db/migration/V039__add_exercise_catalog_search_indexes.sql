-- SET-03: PostgreSQL backed, accent-insensitive catalog search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION exercise_catalog.fold_search_text(value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT lower(regexp_replace(
        translate(trim(coalesce(value, '')),
          'ĄĆĘŁŃÓŚŹŻąćęłńóśźż',
          'ACELNOSZZacelnoszz'),
        '\\s+', ' ', 'g'))
$$;

CREATE INDEX ix_exercise_canonical_name_search_trgm
    ON exercise_catalog.exercise USING gin (exercise_catalog.fold_search_text(canonical_name) gin_trgm_ops);
CREATE INDEX ix_exercise_alias_search_trgm
    ON exercise_catalog.exercise_alias USING gin (exercise_catalog.fold_search_text(alias) gin_trgm_ops);
CREATE INDEX ix_exercise_version_text_search_trgm
    ON exercise_catalog.exercise_version_text USING gin (exercise_catalog.fold_search_text(name) gin_trgm_ops);
CREATE INDEX ix_exercise_version_current_published
    ON exercise_catalog.exercise_version(exercise_id, version_number DESC, published_at DESC, id)
    WHERE status = 'PUBLISHED';
CREATE INDEX ix_exercise_movement_characteristic_search
    ON exercise_catalog.exercise_movement_characteristic(exercise_version_id, movement_pattern, position_code, unilateral);
CREATE INDEX ix_exercise_equipment_search
    ON exercise_catalog.exercise_equipment(exercise_version_id, equipment_code);
CREATE INDEX ix_exercise_version_purpose_search
    ON exercise_catalog.exercise_version_purpose(exercise_version_id, purpose);
CREATE INDEX ix_exercise_contribution_structure_search
    ON exercise_catalog.exercise_contribution(exercise_version_id, anatomical_structure_id);
