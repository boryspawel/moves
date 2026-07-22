CREATE TABLE adherence.barrier_category_dictionary (
    rule_version_code VARCHAR(80) NOT NULL REFERENCES adherence.barrier_rule_version (code),
    category VARCHAR(32) NOT NULL,
    display_order INTEGER NOT NULL,
    PRIMARY KEY (rule_version_code, category)
);

INSERT INTO adherence.barrier_category_dictionary (rule_version_code, category, display_order) VALUES
    ('BARRIER_RESPONSE_V1', 'NO_TIME', 1),
    ('BARRIER_RESPONSE_V1', 'PAIN_OR_SYMPTOMS', 2),
    ('BARRIER_RESPONSE_V1', 'TOO_DIFFICULT', 3),
    ('BARRIER_RESPONSE_V1', 'UNSURE_TECHNIQUE', 4),
    ('BARRIER_RESPONSE_V1', 'FATIGUE', 5),
    ('BARRIER_RESPONSE_V1', 'ILLNESS', 6),
    ('BARRIER_RESPONSE_V1', 'LOGISTICS', 7),
    ('BARRIER_RESPONSE_V1', 'LOW_MOTIVATION', 8),
    ('BARRIER_RESPONSE_V1', 'OTHER', 9);
