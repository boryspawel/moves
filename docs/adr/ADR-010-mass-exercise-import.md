# ADR-010: PostgreSQL staging JSONB, Spring Batch i niezmienne wersje ćwiczeń

- Status: przyjęta
- Data: 2026-07-21

## Kontekst

Zewnętrzny katalog nie może ominąć granic `exercisecatalog`, `anatomyreference`, `loadanalysis` i `safety`. Import pliku jest zawodny, wymaga wznowienia, zachowania dowodu wejściowego oraz decyzji redakcyjnej. Istniejący monolit ma PostgreSQL, Flyway, Keycloak i transakcyjny outbox.

## Decyzja

Import jest osobnym modułem `exerciseimport`. Oryginał trafia przez port magazynu do trwałego wolumenu, a PostgreSQL przechowuje jego SHA-256, niezmienne rekordy `raw_payload` JSONB, wersjonowany staging znormalizowany, problemy i decyzje. Spring Batch 6 realizuje kroki `RECEIVE → PARSE → NORMALIZE → VALIDATE → MATCH → CREATE_DRAFT` w metadanych zarządzanych przez Flyway. Katalog zachowuje stabilne `Exercise`; każda zmiana jest nowym, niezmiennym po publikacji `ExerciseVersion`. Podobieństwo tworzy wyłącznie kandydaturę, nigdy cross-source merge. Recenzje ludzi i publikacja są transakcyjne, a publikacja dopisuje zdarzenie outbox.

## Konsekwencje

Nie potrzebujemy brokera, MinIO ani drugiej bazy. Artefakty wymagają backupu i jawnej retencji razem z rekordami audytowymi. JSONB nie jest modelem publikowanego katalogu. Trigger PostgreSQL chroni rodzica oraz semantyczne dzieci również przed błędem aplikacji. Spring Batch 6 sygnalizuje deprecjację jego zgodnego wstecz modelu chunk; migracja na nowy model chunk jest wymagana przed Batch 7.
