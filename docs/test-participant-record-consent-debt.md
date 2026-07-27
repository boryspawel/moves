# Test participant-record consent debt

`moves.test-default-consent.enabled=true` is a temporary local/test-only switch. It is accepted only when either `local` or `test` is active and is rejected when `prod` is active, including `prod,local` and `prod,test`. Creating a specialist client with the switch enabled creates an audited `TEST_DEFAULT` override for the account-free participant record; it explicitly records `acceptedByParticipant=false`. It is not participant consent, does not create a legal acknowledgement, and is consulted only by the matching specialist, purpose and scopes.

The temporary scope is limited to record work, plans, calendar, specialist-recorded execution, measurements, notes, timeline, and alerts. The product debt remains: no real participant consent, no legal representative handling, no invitation or claim flow, and no participant self-service.

`V036__add_participant_records` established `participant_record` and the optional
`participant_access_link` as the account boundary. `V037__stabilize_client_create_idempotency`
adds a persisted request fingerprint: a repeated create is accepted only for the
same normalized payload, while pre-V037 idempotency rows without a recoverable
payload are deliberately rejected. Legacy `participant_account_id` columns remain
local read-compatibility bridges; new specialist-client writes use `participant_id`
only.
