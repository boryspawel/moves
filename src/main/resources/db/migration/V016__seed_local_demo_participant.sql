-- Local demonstration data. The external_subject is the fixed Keycloak realm-import user id.
-- This migration does not contain credentials; Keycloak owns the demonstration password.
INSERT INTO identity_access.principal_account (
    id, external_subject, status, profile_type, created_at, last_seen_at, version
) VALUES (
    'cdb59ca4-fab5-4b07-b2c0-842733a6fd93',
    '8f6e9cc1-1c1e-4f7a-98f4-3c0c3f40ac01',
    'ACTIVE', 'PARTICIPANT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

INSERT INTO participant.participant_profile (id, account_id, display_name, created_at, updated_at, version)
VALUES (
    '4db9da87-6f4f-407c-a717-579e3d2c62ac',
    'cdb59ca4-fab5-4b07-b2c0-842733a6fd93',
    'Demo Uczestnik', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

INSERT INTO consent.legal_acknowledgement (id, account_id, acknowledgement_type, document_version, accepted_at)
VALUES
    ('909f70c9-52c4-478b-9942-dc8625de8927', 'cdb59ca4-fab5-4b07-b2c0-842733a6fd93', 'TERMS_OF_SERVICE', 'terms-v1', CURRENT_TIMESTAMP),
    ('319cc4f3-1298-4aa5-b3d4-9e6c3460cf42', 'cdb59ca4-fab5-4b07-b2c0-842733a6fd93', 'PRIVACY_NOTICE', 'privacy-v1', CURRENT_TIMESTAMP);

INSERT INTO availability.recurring_slot (id, account_id, day_of_week, start_time, end_time, time_zone, created_at)
VALUES (
    'b5547d02-7fb0-4c35-aae3-1efeb74fc7ea',
    'cdb59ca4-fab5-4b07-b2c0-842733a6fd93',
    'MONDAY', '09:00', '10:00', 'Europe/Warsaw', CURRENT_TIMESTAMP
);
