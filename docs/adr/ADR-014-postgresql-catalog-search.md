# ADR-014: PostgreSQL folded trigram search for the exercise catalog

## Status

Accepted — 2026-07-29.

## Context

The exercise-set builder needs deterministic selection of exact published exercise
versions. The catalog already holds canonical names, locale-aware aliases, version text
and structured relations; an external cluster would add a second source of truth.

## Decision

Catalog search remains in PostgreSQL. V039 enables `pg_trgm`, adds immutable folded-text
search for Polish diacritics and creates trigram/relation indexes. The v2 API uses a
dedicated projection, explainable deterministic ranking and seek/cursor pagination. Only
current published versions are selectable.

## Consequences

Search is transactionally consistent with publication and has no indexing pipeline or
external operational dependency. Facets use a bounded number of PostgreSQL aggregations;
ranking stays explainable instead of ML-derived.

## Alternatives rejected

- `ILIKE` only: insufficient typo tolerance and index support.
- PostgreSQL full-text alone: poorer fit for aliases and partial exercise names.
- Elasticsearch/OpenSearch: unjustified operational complexity and duplicate state.
- Client-side filtering: violates visibility, pagination and ownership boundaries.
