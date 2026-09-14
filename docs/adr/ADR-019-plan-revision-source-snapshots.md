# ADR-019: plan revisions materialize canonical planning sources

`participant_goals.ParticipantGoal` is the only authoring source for a participant goal and
`exercise_set.ExerciseSetVersion` is the only authoring source for session content. Planning uses
their public query ports and never maps provider JPA entities or repositories.

Adding a goal copies metadata, status and outcomes into revision-owned `training_goal` and
`goal_outcome` rows and records source ID, version and snapshot time. Adding a session accepts one
exact published set version and materializes ordered item, catalog, instruction and typed-dose data.
Flat prescription fields are compatibility projections; `materialized_snapshot` preserves full dose.
The session also persists the complete public exercise-set-version payload, including version metadata
and the provider's persisted load/anatomy analyses when available. New session variants select only
whole materialized prescriptions; dose overrides are historical read-only data.

Draft validation and activation recheck same-participant ownership, specialist goal category/lifecycle
and PUBLISHED set-version availability. Active revisions are read as stored and are not reinterpreted
after source changes. Clones copy snapshots and provenance without refresh. P1 new authoring is
specialist-only for `SPECIALIST` and `COLLABORATIVE` modes; `SELF_DIRECTED` is historical read-only
until sharing is designed in P2.

V057 is additive. Existing rows with no provable source are KEEP READ-ONLY. Legacy training goals and
manual prescriptions are DEPRECATED as authoring and retained for historic reads; no source link is invented.
An authorized draft editor can delete only a revision-local legacy goal or session (with its prescriptions
and variants), then add a canonical source-backed replacement. Active and finalized revisions remain immutable.

Legacy disposition is explicit: revision `training_goal` rows MIGRATE into source-aware snapshots when
newly attached (V057 backfills only deterministic snapshot timestamps); unlinked historic `training_goal`,
V1 goal records, old dose overrides and old `SELF_DIRECTED` revisions are KEEP READ-ONLY; manual UI and
V1 writes are DEPRECATED (`V1` create is already `410`); active V2 independent goal and prescription
authoring commands are REMOVED. No historical migration is altered and no canonical provenance is fabricated.

Load analysis stores completeness issues with its immutable planned-load snapshot. An unavailable exact
dose only blocks where the canonical dose type and a catalog contribution make the missing operand
applicable. Safety records `LOAD_COMPLETENESS_REQUIRED` as non-overridable `HARD_BLOCK`; it is neither a
numeric threshold nor an acknowledgement candidate.
