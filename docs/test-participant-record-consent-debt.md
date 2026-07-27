# Test participant-record consent debt

`moves.test-default-consent.enabled=true` is a temporary local/test-only switch. It creates an audited `TEST_DEFAULT` override for an account-free participant record; it explicitly records `acceptedByParticipant=false`. It is not a participant consent, does not create a legal acknowledgement, and is consulted only by the consent decision path for the matching specialist, purpose and scopes. Startup is rejected whenever `prod` is active, including `prod,local` and `prod,test`.

The temporary scope is limited to record work, plans, calendar, specialist-recorded execution, measurements, notes, timeline, and alerts. The product debt remains: no real participant consent, no legal representative handling, no invitation or claim flow, and no participant self-service.

Legacy `participant_account_id` columns remain read compatibility bridges for modules not yet migrated. New specialist-client writes use `participant_id` only; the next migration must move calendar, workspace, timeline, and worklist projections to that identifier before removing the legacy columns.
