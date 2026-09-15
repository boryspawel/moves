# Participant goals

## Implemented

`participant_goals` owns specialist-authored outcome goals for a canonical
`participant.participant_record`. `ParticipantGoal` is the sole authoring source for a participant-level
desired outcome. `training_planning.training_goal` is a revision-owned immutable snapshot, never a
second goal aggregate; planning reaches this module only through its public query port. GOALS-01 through
GOALS-05 and the P1 revision integration are complete.

GOALS-05 adds the read-only, versioned-in-code `GoalMetricPresetCatalog` and a dedicated
create-from-preset command. The catalog is not persisted or administrable: it supplies display
labels, required context, allowed units and comparator defaults to the workspace, while the backend
derives metric code, unit, comparator and measurement method. The resulting single `GoalOutcome`
is an ordinary immutable snapshot, not a live catalog reference. The compact two-step workspace
flow deliberately never exposes metric codes, methods, priority or multiple/reordered outcomes.

Goals are created active with immutable outcome snapshots. Only title, description, priority and
target date may be updated while active. Achieved and cancelled are terminal states. Achievement
is an explicit specialist action; sessions, plans, observations and execution do not automatically
achieve a goal.

Every specialist mutation requires an idempotency key and records an audit event. Access requires
an active relationship plus the existing capability/consent authorization boundary: trainer / performance
or physiotherapist / functional. General-fitness goals are intentionally rejected.

Outcome observations are a separate append-only history in `participant_goals.goal_observation`.
Recording an observation is allowed only for an active, owned goal and a goal-scoped outcome; it
snapshots the outcome unit and never changes the goal status, version, or achieved timestamp.
The latest observation and its count are included in the specialist goal projection, while history is exposed
through a bounded seek cursor ordered by `measuredAt DESC, recordedAt DESC, id DESC`. `GoalOutcome` is
an immutable outcome definition and `GoalObservation` is the immutable measurement source; neither is
rewritten to derive progress.

An outcome created now must declare `AT_LEAST` or `AT_MOST`. One backend read calculation is shared by
the specialist outcome projection and the safe participant detail projection. It preserves comparator
threshold achievement and reports `NO_MEASUREMENT`, `BASELINE_ONLY`, `INSUFFICIENT_DATA`,
`PROGRESSING`, `MOVING_AWAY`, `UNCHANGED`, `TARGET_REACHED`, or `NOT_COMPARABLE` as applicable.
It uses the latest and preceding compatible-unit observation, falling back to the outcome baseline;
incompatible units are not converted or used for progress/chart data. `baselineToTargetPercent` is
unclipped and exists only when baseline and target form a meaningful direction (`AT_LEAST` target above
baseline or `AT_MOST` target below baseline); no percentage is invented for missing, equal, or
wrong-direction baselines. A reached comparator remains reached independently of that percentage.
Legacy outcomes have a nullable comparator and therefore deliberately report `NOT_COMPARABLE`:
migration V053 does not invent a backfill. Multiple outcomes remain separate; there is no combined
goal score.

Goal mutations also write one append-only `participant_goal_event` snapshot in the same transaction: creation, metadata update, observation recording, achievement and cancellation. Observation events carry the measured value/unit/metric and progress-state snapshot, and use `measuredAt` as their effective time; other events use the command time. Idempotent replays and failed transactions add no event. V054 adds one current-state `BASELINE` per pre-existing goal; it is migration evidence rather than reconstructed lifecycle history.

The specialist backend timeline consumes those events through a neutral query port, exposes category `GOAL`, and preserves each event separately. Baselines are presented as the corresponding created/achieved/cancelled goal event, never as a technical baseline.

The specialist participant workspace now presents goals in its existing summary area, not in a new route or dashboard. It obtains the explicit acting context only from onboarding `ProfileSummary.specialistKind` and only exposes trainer/performance or physiotherapist/functional work. The UI uses generated participant-goal APIs, renders lifecycle controls only from `availableActions`, and fetches observation history only after a goal is opened. The participant owns a separate read-only detail and bounded paged observation-history endpoint; its observation projection omits recorder, note and evidence-source metadata and does not allow observation creation. Progress/chart presentation uses goal IDs and is timeline-first in the specialist workspace. It does not provide automatic achievement or measurement edits/deletes.

## P1 plan-revision snapshots

Adding a goal to an editable specialist revision accepts `participantGoalId`, verifies identical
`participantId`, goal lifecycle and the existing trainer/performance or physiotherapist/functional
capability, then copies its metadata, status and outcomes into revision-owned rows with source ID,
source version and snapshot time. A later goal mutation, achievement or cancellation never rewrites
that revision. Clone copies this provenance and snapshot without refreshing it. Validation/activation
checks the current source eligibility only for a draft; active history is read as stored.

## Deliberate limitations

- Achievement is an explicit specialist action; observations, sessions, plans and execution data
  do not automatically achieve a goal.
- Observations are append-only records: there are no correction, edit or delete operations or unit
  conversions. Charts are read-only per-outcome views, not aggregates or a combined goal score.
- Specialist authoring remains limited to the supported trainer/performance and physiotherapist/functional
  contexts. Participant access is read-only and limited to the owner’s safe detail/history projection;
  `GENERAL_FITNESS` authoring is not implemented.
- There are no notifications, reminders, achievement suggestions, device imports, analytics or ML.

## Future integrations / roadmap

- **Plan authoring UI:** provide a specialist source picker and revision/history surface. The backend
  P1 source link and snapshot semantics are already implemented.
- **Observations:** introduce an append-only correcting event, explicit unit conversions, session
  and research-result integrations, plus aggregate projections.
- **Participant access:** add `GENERAL_FITNESS` self-service for own-goal reading and only allowed
  observations, with authorization distinct from specialist authorization.
- **Lifecycle and compliance:** define post-relation access, consent withdrawal versus legal
  retention, retention/access limitation/anonymization/audit requirements, and treatment of
  unfinished operations when cooperation ends.
- **Outside MVP:** notifications, reminders, achievement suggestions, device import, analytics and
  ML remain future work.
- **Goal authoring:** allow additional outcomes after create and outcome editing, make the preset
  catalog configurable, link strength results/exercises to the exercise catalog, and add further
  measurement types.
