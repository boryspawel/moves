# ADR-016: versioned anatomy-exposure snapshots and separate channels

## Status

Accepted — 2026-07-30.

## Context

Reusable exercise sets need an explainable anatomy-exposure view without deriving
historical meaning from a mutable catalogue or mixing dose-derived quantities with
qualitative anatomy evidence. Published set versions must remain readable after a
catalogue contribution, evidence record or policy changes.

## Decision

Each exercise-set item retains the exact published exercise anatomy snapshot used for
the set version. Draft anatomy analysis reads those item snapshots on demand.
Publication persists the anatomy-analysis result; a published read returns that
immutable result rather than reanalysing the live catalogue.

The result separates exposure into named load channels. It returns direct structure
shares and per-item contribution breakdowns, including provenance/evidence and
laterality where supplied, without collapsing channels into one cross-channel scalar.
Completeness, findings and missing snapshot data remain explicit parts of the result.

## Consequences

The UI can present channels, rankings, shares, evidence, movement patterns and data
limitations while preserving the publication-time meaning of a set. It must not present
the result as a clinical assessment, force measurement or participant-specific safety
decision. Assignment and safety retain those responsibilities.

## Alternatives rejected

- Live catalogue reads for published versions: later catalogue changes would rewrite
  historical exposure.
- A single aggregate anatomy score: it would obscure channel semantics and provenance.
- Storing anatomy result only with a plan or execution: it would detach the result from
  the reusable version that was actually published.
