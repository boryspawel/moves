# Prompt 4 — katalog ćwiczeń i podstawy safety

## Pochodzenie i ograniczenie dowodowe

- repo: `../gra-mateusza`, branch `main`, lokalny HEAD `0b994cb6e35319837a2c81de4fb9a556b1943b90`;
- wskazany w zadaniu `bdb481b3e7bef665e76ecf27a40f1a630cb6d7f5` nie istnieje lokalnie, a publiczny patch zwraca HTTP 404;
- dostępny kod nie zawiera implementacji screeningu ani readiness. Nie przypisano im niepotwierdzonych zachowań.

## Decyzje

| Źródło | Cel | Decyzja |
|---|---|---|
| `training.domain.Exercise` | `Exercise` + `ExerciseVersion` | REWRITE; rozdzielona tożsamość i wersja |
| pola instrukcji/media/sprzętu/trudności | pola wersji i klasyfikacje | PORT po typowaniu i normalizacji |
| `powerMap` i power stats | brak | REJECT |
| `ExerciseService.searchExercises` | read-only katalog z dozwolonymi filtrami | PORT zachowania |
| public/admin controllers | `/api/v1/exercises`, `/api/v1/admin/exercises` | REWRITE z Resource Server i `CONTENT_ADMIN` |
| bezpośredni update/deactivate encji | draft/publish/withdraw | REWRITE |
| źródłowe V3/V008/V5 | V003 należące do `moves` | REFERENCE/REWRITE; brak kopiowania numerów i seedów |
| Angular library/admin | przyszły frontend | REFERENCE wyłącznie przepływów |

## Nowy model

- wersja ma status `DRAFT`, `PUBLISHED` albo `WITHDRAWN`;
- wersja opublikowana jest niezmienna (reguła silniejsza niż minimalny zakaz edycji wersji użytej w planie);
- kolejna zmiana tworzy nowy draft z rosnącym numerem;
- publikowany katalog zawiera wyłącznie status `PUBLISHED`;
- filtrowanie jest jawnie ograniczone do query, movement pattern, technical level, equipment i wykluczanego tagu przeciwwskazania;
- equipment i contraindication tags są neutralnymi tagami, bez diagnozy;
- safety przechowuje per-account ograniczenia i append-only check-in bólu/readiness wraz z jawnym non-diagnostic notice.

## Walidacja

Celowany `CatalogAndSafetyIntegrationTest`: 2 testy, sukces. Potwierdzono admin authorization, brak operacji admin dla participant, draft/publish/withdraw, niezmienność opublikowanej wersji, filtry, tagi przeciwwskazań, izolację safety, zakresy bólu/readiness i V001–V003 na PostgreSQL 18.4.

## Dług

- progresje/regresje nie występują w dojrzałej postaci w potwierdzonym źródle i nie zostały dodane spekulacyjnie;
- oznaczenie wersji jako użytej zostanie związane z receptą w Prompt 5; niezmienność po publikacji już chroni historię;
- brak zatwierdzonych automatycznych reguł HARD_BLOCK — Prompt 4 zapisuje wejścia i nie diagnozuje;
- media są referencją tekstową; signed URL/storage adapter pozostaje później;
- filtrowanie in-memory jest poprawne funkcjonalnie dla slice, lecz wymaga przeniesienia do zapytań DB przed dużym katalogiem.
