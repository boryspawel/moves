# ADR-013: independent, versioned exercise sets

## Status

Accepted — 2026-07-28.

## Context

The legacy `/plan` form (`PlanPage`) creates a participant-specific plan, dated session,
flat prescription and optional session variants in one interaction. It cannot produce a
reusable, stable composition of exercises. The complete target terminology, aggregates and
migration plan are canonical in [the exercise-set model](../architecture/exercise-set-model.md).

## Decision

`ExerciseSet` is an independent reusable identity and `ExerciseSetVersion` is an immutable
published composition. Neither contains a participant, scheduled date, execution, personal
safety assessment or plan-specific fields. Ordered items reference exact published
`ExerciseVersion` IDs and typed doses. Plans/sessions, assignments and execution refer to
an exact set version through their own aggregates; published variants are fully materialized
versions related to their base.

## Consequences

New work introduces the `exercisesets` boundary beside the legacy planning model, then
integrates plans and assignments incrementally. Historical prescriptions/executions remain
readable and frozen. The new model uses JPA/Hibernate; the current JDBC import implementation
is not a precedent for it. Publication can be structurally blocked, but participant and
day-specific safety remain outside the generic set.

## Implementation note — SET-02 (2026-07-28)

The initial backend vertical slice is implemented by
`com.motionecosystem.exercisesets` and Flyway migration
[`V038__create_exercise_sets.sql`](../../src/main/resources/db/migration/V038__create_exercise_sets.sql).
It persists the independent root, version, ordered items, full-snapshot drafts/variants
and typed doses through JPA/Hibernate. `exercise_set_item_dose` uses `SINGLE_TABLE`
inheritance with persistence column `kind`; the DTO/OpenAPI discriminator is `type`.
All set endpoints are owner-scoped specialist
endpoints under `/api/v1/specialist/exercise-sets`.

The SET-02 vertical slice includes the OpenAPI snapshot, generated TypeScript client and
`ApiFacade.exerciseSets`; `Dose` is represented as `oneOf` with the `type`
discriminator. PostgreSQL/Testcontainers integration coverage verifies the persistence
and API path. The deterministic analyser, profile completeness rules, assignment,
planning/execution integration and Angular builder remain subsequent stages. The legacy
`/plan` flow is unchanged.

Focused SET-02 validation is green. The repository-wide `ModuleBoundaryTest` and full
`mvn verify` remain non-zero because of a pre-existing module cycle and legacy fixtures
after V036, not because of this implementation.

## Alternatives rejected

- Put a reusable-set flag on `ExercisePrescription`: still couples content to session/date.
- Make a set own its participant or plan: prevents reuse and leaks safety/history.
- Store short/minimum as deltas: cannot deterministically replay after base changes.
- Use one optional-field dose record: permits invalid semantic combinations.
