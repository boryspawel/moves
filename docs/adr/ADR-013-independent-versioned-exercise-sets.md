# ADR-013: independent, versioned exercise sets

## Status

Accepted — 2026-07-28.

## Context

Plans require a reusable, stable composition of exercises that is separate from a
participant, session and execution. The aggregate and API contract are defined in
[the exercise-set model](../architecture/exercise-set-model.md).

## Decision

`ExerciseSet` is an independent reusable identity and `ExerciseSetVersion` is an immutable
published composition. Neither contains a participant, scheduled date, execution, personal
safety assessment or plan-specific fields. Ordered items reference exact published
`ExerciseVersion` IDs and typed doses. Plans/sessions, assignments and execution refer to
an exact set version through their own aggregates; published variants are fully materialized
versions related to their base.

## Consequences

The `exercisesets` boundary uses JPA/Hibernate. Plans, assignments and execution preserve
their own immutable snapshots of an exact published set version. Publication validates
the set's structural integrity; participant and day-specific safety remain outside the
generic set.

## Alternatives rejected

- Put a reusable-set flag on `ExercisePrescription`: still couples content to session/date.
- Make a set own its participant or plan: prevents reuse and leaks safety/history.
- Store short/minimum as deltas: cannot deterministically replay after base changes.
- Use one optional-field dose record: permits invalid semantic combinations.
