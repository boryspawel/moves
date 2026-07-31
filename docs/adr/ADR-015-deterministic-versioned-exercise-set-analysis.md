# ADR-015: deterministic, versioned exercise-set analysis snapshot

## Status

Accepted — 2026-07-30.

## Context

Exercise-set publication needs inspectable structural feedback without making a reusable
definition depend on mutable catalogue data, participant facts or future model outputs.
Published versions must remain explainable after catalogue and policy evolution.

## Decision

SET-05 uses a pure deterministic policy over the version's persisted item snapshots and
typed doses. Draft analysis is on demand. Publication runs the same policy and, when it
is not blocked, persists one analysis run and its findings with the policy/rule versions,
analysed lock version, time and metrics. Published reads return this snapshot rather than
live reanalysis.

The analysis boundary is the set version only. Assignment, planning and execution own
their participant, schedule and historical facts and may reference the exact published
version later; they do not alter its analysis.

## Consequences

Results are deterministic, replayable and explainable. A new policy can be introduced
under a new version without silently changing a published result. The current snapshot
does not include body position, difficulty or anatomy/classification, so it explicitly
reports anatomy data unavailable and makes no related rule or clinical claim.

## Alternatives rejected

- ML scoring: non-deterministic and insufficiently explainable for a publication gate.
- Live catalogue reanalysis of published versions: would rewrite historical meaning as
  catalogue data changes.
- Storing analysis only on an assignment or execution: mixes reusable content with
  participant/schedule history and loses publication-time evidence.
