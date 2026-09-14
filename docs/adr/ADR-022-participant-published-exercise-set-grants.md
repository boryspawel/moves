# ADR-022: participant grants for exact published exercise-set versions

## Status

Accepted — 2026-09-14.

## Decision

An exercise-set owner may grant an exact `PUBLISHED` `ExerciseSetVersion` to a canonical
`participantId`. The owner remains the sole author; the participant receives a redacted read/use
snapshot and no draft, visibility, clinical-data or plan-read capability. The existing
`SHARE_EXERCISE_SETS` authorization remains narrow: verified role and active relationship are required,
without adding a clinical consent scope because the owner is transmitting owner content.
The exercise-set module may consume only `participant.api` for the existing account-link lookup and
recipient projection; it must not depend on participant domain, application, infrastructure or JPA types.

Revocation and retirement prevent future authoring, validation and activation, including after a draft
was validated. They do not rewrite activated revision snapshots or execution history. A participant can
use a granted version only in an owned `SELF_DIRECTED` plan through the existing V2 materializer and
workflow; no participant goal-creation path or generic ACL/idempotency mechanism is introduced.

## Safety

Participant self-acknowledgement is limited to current `WARNING` factors and records
`PARTICIPANT_SELF_ACKNOWLEDGEMENT` without a professional context. `HARD_BLOCK` cannot be acknowledged
or activated by the participant and clinical override remains a specialist clinical capability.
