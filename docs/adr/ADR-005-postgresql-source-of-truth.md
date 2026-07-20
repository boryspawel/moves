# ADR-005: PostgreSQL jako źródło prawdy

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Plan, wykonanie, zgody, alerty, audyt i ledger wymagają relacyjnej spójności oraz trwałego śladu. Źródła mają kolidujące i niespójne historie migracji.

## Decyzja

PostgreSQL jest jedynym źródłem prawdy. Schemat rozwija wyłącznie liniowa historia Flyway należąca do `moves`; Hibernate używa `ddl-auto=validate`. Testy uruchamiają migracje na rzeczywistym PostgreSQL w Testcontainers.

## Konsekwencje

Nie kopiujemy numerów migracji źródłowych ani nie używamy pamięci/cache jako źródła stanu. Zmiany są forward-compatible i unikają destrukcyjnych rollbacków.
