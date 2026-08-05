# ADR-019: Append-only participant-goal observations

## Status

Accepted (PARTICIPANT-GOALS-02).

## Decision

Measurements are immutable `goal_observation` rows scoped by a composite foreign key to the owning
goal outcome. Recording requires an active specialist-owned goal and is idempotent per specialist,
scoped operation and key. It does not automatically achieve a goal or update its version.

New target outcomes explicitly declare `AT_LEAST` or `AT_MOST`. Existing rows retain a nullable
comparator, with no fabricated migration backfill; their displayed state is `NOT_COMPARABLE`.

## Consequences

The API provides only latest progress in goal projections and a bounded cursor history endpoint.
There is no observation edit/delete endpoint, timeline, UI, automatic lifecycle transition, or
training-plan/workspace integration.
