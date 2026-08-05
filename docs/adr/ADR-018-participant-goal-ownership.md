# ADR-018: participant-level goal ownership separate from training goals

## Decision

Store participant outcome goals in the `participant_goals` schema, owned by the canonical
participant record and specialist actor. Keep them separate from `training_planning.training_goal`,
which is a revision-owned planning artifact.

## Consequences

Participant goals have their own lifecycle, outcome snapshots, optimistic version and idempotency
records. There is no migration from training goals, no automatic achievement, and no integration
which makes a plan, session or execution event change a participant goal. Future cross-module
references must be explicit read integrations through the neutral query port.
