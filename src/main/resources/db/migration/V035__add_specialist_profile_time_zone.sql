ALTER TABLE specialist.specialist_profile
    ADD COLUMN time_zone_id VARCHAR(64);

-- Legacy profiles did not retain a locale. UTC is the explicit, deterministic
-- compatibility default; subsequent specialist-profile updates store the
-- specialist's selected IANA zone and Today always reads this persisted value.
UPDATE specialist.specialist_profile
    SET time_zone_id = 'UTC'
    WHERE time_zone_id IS NULL;

ALTER TABLE specialist.specialist_profile
    ALTER COLUMN time_zone_id SET NOT NULL;
