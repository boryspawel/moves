# Kontekst wykonania migracji

## Zasady pracy

- Prompty z `promt.md` są wykonywane kolejno, bez modyfikowania `spec.md` ani repozytoriów źródłowych.
- Źródła: `../gra-mateusza` i `../ruszsie`; nie wykonujemy merge historii ani cherry-picków.
- Każde użycie Maven/JVM poprzedza aktywacja Java 25 przez SDKMAN w tej samej powłoce.
- Brak MCP IntelliJ w bieżącym środowisku jest jawnym odstępstwem narzędziowym. Inspekcja odbywa się przez Git, `rg`, Maven i testy.
- Dostępny skill `deployable-version-inventory` został użyty w Prompcie 1. Brak dedykowanego skilla migracji Spring/Angular.

## Stan wejściowy

- `moves`: branch `main`, HEAD `f049101ae62aa594e418a29ac632313a0e079a96`; `promt.md` i `spec.md` były już staged. Repo istniało przed Promptem 2.
- `ruszsie`: branch `main`, HEAD `2d7656e8b1b8d5da145a1bf570a6985fa6b0b033`, working tree czysty.
- `gra-mateusza`: podczas audytu stan zmienił się poza działaniami migracji. Początkowo odczytano HEAD `818707b4be91fe87d6a44f132260b9f8d4517f1d` i wiele lokalnych zmian. O 2026-07-20 16:00:17 +02:00 zmiany zostały zapisane w lokalnym commicie `0b994cb6e35319837a2c81de4fb9a556b1943b90`. Aktualnie branch `main` jest ahead 1 względem `origin/main`, a working tree zawiera nieśledzony `AGENTS.md`.
- Wskazane SHA `gra-mateusza` (`bdb481...`, `fd539c...`, `f02551...`) nie istnieją w lokalnej bazie obiektów. Read-only próba pobrania patchy z publicznego GitHub zakończyła się HTTP 404. Nie przypisujemy im niezweryfikowanej zawartości.

## Postęp promptów

| Prompt | Status | Rezultat | Walidacja / uwagi |
|---|---|---|---|
| 1. Audyt i plan | zakończony | `docs/migration/as-is-and-migration-plan.md`, `deployable-versions.md`, `deployable-versions.csv`, `docs/architecture/technical-overview.md` | Stan i źródła sprawdzone read-only; MD i CSV porównane semantycznie 1:1 |
| 2. Fundament `moves` | zakończony | Boot 4.1/Java 25, PostgreSQL/Flyway, Resource Server, health/OpenAPI, Compose/Keycloak, CI i ADR-y | `mvn -q verify` exit 0; 7 testów przed kolejnym etapem |
| 3. Onboarding | zakończony | identity mapping, profile participant/specialist, wersjonowane potwierdzenia, dostępność, state, audyt | pełne `mvn -q verify` exit 0; 10 testów, V001+V002 na PostgreSQL 18.4 |
| 4. Katalog i safety | zakończony | wersjonowany katalog ćwiczeń, workflow publikacji/wycofania, klasyfikacje i filtry, ograniczenia uczestnika oraz append-only readiness check-in | pełne `mvn -q verify` exit 0; 12 testów, V001–V003 na PostgreSQL 18.4; `git diff --check` bez uwag; manifest `docs/migration/prompt-4-catalog-safety.md` |
| 5. Planowanie i wykonanie | zakończony | relacja specjalista–uczestnik, pełna hierarchia planu, dokładne wersje ćwiczeń, deklarowane wykonanie, wyniki, ból/trudność, alerty i append-only korekty | pełne `mvn -q verify` exit 0; 15 testów, V001–V004 na PostgreSQL 18.4; `git diff --check` bez uwag; manifest i ADR-006 |
| 6. Gamifikacja | zakończony | opt-in, pseudonim/visibility, wersjonowane reguły, append-only ledger, idempotencja, limity/cooldown/diminishing, reversal i odbudowywalny ranking | pełne `mvn -q verify` exit 0; 22 testy, V001–V005 na PostgreSQL 18.4; `git diff --check` bez uwag; manifest i ADR-007 |
| 7. Angular | przerwany / częściowy | Utworzono szkielet Angular 22, snapshot OpenAPI i wygenerowanego klienta oraz część autoryzacji i ekranów | Frontend nie został zbudowany ani w pełni przetestowany; szczegóły i dług technologiczny: `docs/migration/interrupted-status-and-technical-debt.md` |
| 8. Audyt końcowy | niewykonany | — | Przerwano na polecenie użytkownika przed rozpoczęciem; zakres pozostałych prac opisano w `docs/migration/interrupted-status-and-technical-debt.md` |

## Decyzje przenoszone między etapami

- Neutralny prefiks pakietu: `com.motionecosystem` (nazwa produktu nie jest zakodowana w package root).
- Jeden deployowalny backend — modularny monolit; jeden osobno budowany frontend.
- Schematy PostgreSQL odpowiadają bounded contexts; nowe migracje zaczynają własną, liniową numerację.
- Keycloak `sub` jest zewnętrznym identyfikatorem tożsamości; lokalne konto/profil nie emituje tokenów.
- Pierwszy vertical slice: uwierzytelniony podmiot → profil/zgody/dostępność → katalog → relacja specjalista–uczestnik → planowana sesja → deklarowane wykonanie → alert.
