# ADR-003: Kontrolowana migracja bez merge historii

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Źródła mają różne stosy, nazwy, modele identity i niespójne migracje. Pełny merge lub cherry-pick przeniósłby przypadkową architekturę i historię.

## Decyzja

Każdy fragment otrzymuje PORT, REWRITE, REFERENCE albo REJECT. Pochodzenie i SHA zapisujemy w `docs/migration`; kod implementujemy w neutralnych granicach `moves`. Repozytoria źródłowe pozostają read-only.

## Konsekwencje

Tracimy automatyczną ciągłość Git dla linii kodu, ale zyskujemy audytowalny manifest i świadomy model docelowy.
