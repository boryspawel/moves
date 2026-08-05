# Participant goals

## Implemented

`participant_goals` owns specialist-authored outcome goals for a canonical
`participant.participant_record`. A goal is independent of `training_planning.training_goal`:
the latter belongs to a particular plan revision, while this module records a participant-level
desired outcome. Neither module writes into the other. GOALS-01 through GOALS-05 are complete.

GOALS-05 adds the read-only, versioned-in-code `GoalMetricPresetCatalog` and a dedicated
create-from-preset command. The catalog is not persisted or administrable: it supplies display
labels, required context, allowed units and comparator defaults to the workspace, while the backend
derives metric code, unit, comparator and measurement method. The resulting single `GoalOutcome`
is an ordinary immutable snapshot, not a live catalog reference. The compact two-step workspace
flow deliberately never exposes metric codes, methods, priority or multiple/reordered outcomes.

Goals are created active with immutable outcome snapshots. Only title, description, priority and
target date may be updated while active. Achieved and cancelled are terminal states. Achievement
is an explicit specialist action; there is no integration, migration, or automatic achievement
from sessions, plans, observations, or execution data.

Every specialist mutation requires an idempotency key and records an audit event. Access requires
an active relationship plus the existing capability/consent authorization boundary: trainer / performance
or physiotherapist / functional. General-fitness goals are intentionally rejected.

Outcome observations are a separate append-only history in `participant_goals.goal_observation`.
Recording an observation is allowed only for an active, owned goal and a goal-scoped outcome; it
snapshots the outcome unit and never changes the goal status, version, or achieved timestamp.
The latest observation and its count are included in the goal projection, while history is exposed
through a bounded seek cursor ordered by `measuredAt DESC, recordedAt DESC, id DESC`.

An outcome created now must declare `AT_LEAST` or `AT_MOST`; progress is `NO_DATA`,
`IN_PROGRESS`, or `TARGET_REACHED` accordingly. Legacy outcomes have a nullable comparator and
therefore deliberately report `NOT_COMPARABLE`: migration V053 does not invent a backfill.

Goal mutations also write one append-only `participant_goal_event` snapshot in the same transaction: creation, metadata update, observation recording, achievement and cancellation. Observation events carry the measured value/unit/metric and progress-state snapshot, and use `measuredAt` as their effective time; other events use the command time. Idempotent replays and failed transactions add no event. V054 adds one current-state `BASELINE` per pre-existing goal; it is migration evidence rather than reconstructed lifecycle history.

The specialist backend timeline consumes those events through a neutral query port, exposes category `GOAL`, and preserves each event separately. Baselines are presented as the corresponding created/achieved/cancelled goal event, never as a technical baseline.

The specialist participant workspace now presents goals in its existing summary area, not in a new route or dashboard. It obtains the explicit acting context only from onboarding `ProfileSummary.specialistKind` and only exposes trainer/performance or physiotherapist/functional work. The UI uses generated participant-goal APIs, renders lifecycle controls only from `availableActions`, and fetches observation history only after a goal is opened. It does not provide plan integration, participant self-service, automatic achievement, charts, or measurement edits/deletes.

## Deliberate limitations

- Goals have no association with a training-plan revision, and neither goal model writes to the
  other.
- Achievement is an explicit specialist action; observations, sessions, plans and execution data
  do not automatically achieve a goal.
- Observations are append-only records: there are no correction, edit or delete operations, unit
  conversions, charts or aggregate projections.
- Access is specialist-only for the supported trainer/performance and physiotherapist/functional
  contexts. `GENERAL_FITNESS` and participant self-service are not implemented.
- There are no notifications, reminders, achievement suggestions, device imports, analytics or ML.

## Future integrations / roadmap

- **Training plans:** link participant goals to a plan revision, snapshot the associations in that
  revision and copy them when a new revision is created. Validate participant, perspective and
  authorization, define a transition strategy for legacy `training_planning.training_goal`, and
  never automatically back-propagate associations to historic revisions.
- **Observations:** introduce an append-only correcting event, explicit unit conversions, session
  and research-result integrations, plus charts and aggregate projections.
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
