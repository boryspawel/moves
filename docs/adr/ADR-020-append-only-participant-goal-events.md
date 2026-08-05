# ADR-020: Append-only participant-goal mutation events

## Status

Accepted.

## Decision

`participant_goals.participant_goal_event` is an append-only JPA/Hibernate-owned snapshot ledger for successful participant-goal mutations. It records one event in the same transaction for create, update, observation recording, achieve and cancel. Events retain the goal snapshot, lifecycle transition, effective/recorded times and, for observations, the measurement/progress snapshot.

V054 creates a single baseline for every pre-existing goal using its current row. The baseline is not reconstructed history. The specialist timeline reads events only through `ParticipantGoalEventQueryPort`; it presents a public `GOAL` category and maps a baseline to created, achieved or cancelled according to its snapshot status.

## Consequences

Idempotent replays and failed/rolled-back mutations persist no event. The ledger is not a new plan-integration mechanism and does not make the module event sourced. No UI or timeline visual work is included in this decision.
