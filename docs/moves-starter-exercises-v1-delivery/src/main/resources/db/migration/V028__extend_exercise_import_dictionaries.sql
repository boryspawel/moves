-- Minimal dictionary extension used by moves-starter-exercises-v1.
INSERT INTO exercise_catalog.exercise_equipment_dictionary(code, display_name, dictionary_version, active) VALUES
    ('BENCH', 'Ławka', 2, TRUE),
    ('CHAIR', 'Krzesło', 2, TRUE),
    ('KETTLEBELL', 'Kettlebell', 2, TRUE),
    ('STEP_BOX', 'Stopień lub skrzynia', 2, TRUE),
    ('STRAP', 'Pasek treningowy', 2, TRUE),
    ('WALL', 'Ściana lub stabilne oparcie', 2, TRUE);

INSERT INTO exercise_catalog.exercise_position_dictionary(code, display_name, dictionary_version, active) VALUES
    ('FRONT_SUPPORT', 'Podpór przodem', 2, TRUE),
    ('HALF_KNEELING', 'Półklęk', 2, TRUE),
    ('QUADRUPED', 'Klęk podparty', 2, TRUE),
    ('SEATED', 'Siad', 2, TRUE),
    ('SIDE_LYING', 'Leżenie bokiem', 2, TRUE),
    ('SIDE_SUPPORT', 'Podpór bokiem', 2, TRUE),
    ('SPLIT_STANCE', 'Pozycja wykroczna', 2, TRUE),
    ('SQUAT', 'Pozycja przysiadu', 2, TRUE);
