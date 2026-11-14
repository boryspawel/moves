# Moves — plan naprawczy po przeglądzie kodu `main`

- **Status:** OPEN / do realizacji; plan, nie raport wykonanych poprawek.
- **Data audytu:** 2026-11-14.
- **Repozytorium / punkt odniesienia:** `boryspawel/moves`, `main` @ `45c252512cc3a824e362d0a8e250aa645f3df774`.
- **Zakres rozpoznania:** 399 plików produkcyjnych Java, 25 głównych pakietów, 69 migracji Flyway (inwentaryzacja); dokładniejszy przegląd `exercisecatalog` (46 plików) i wybranych ścieżek pozostałych modułów. **Nie jest to jeszcze pełny audyt klasa po klasie wszystkich modułów.**
- **Dokumenty odniesienia:** [spec.md](../../spec.md), [AGENTS.md](../../AGENTS.md), [ADR-001](../adr/ADR-001-modular-monolith.md), [ADR-023](../adr/ADR-023-objective-catalog-publication.md), [technical-overview](../architecture/technical-overview.md), [consent debt](../test-participant-record-consent-debt.md), [M0 mobile](../mobile/05-m0-assessment.md).
- **Weryfikacja:** analiza statyczna repozytorium; nie uruchamiano testów, profili bazodanowych, pomiarów SQL ani E2E. Oznaczenia: **[POTW.]** potwierdzone kodem; **[DO SPRAWDZENIA]** hipoteza / wymagany pomiar; **[DECYZJA]** wymaga rozstrzygnięcia produktu, polityki lub kompatybilności.

## Zasady realizacji

1. Priorytety: **P0** — ochrona wrażliwych danych / spójność transakcji (natychmiast); **P1** — integralność architektury i kontraktów; **P2** — kontrolowane usuwanie legacy i refaktoryzacja; **P3** — dokumentacja, porządki i optymalizacje nieblokujące.
2. **Najpierw P0-01–P0-04, bez czekania na odpowiedzi.** P0-05 przygotować równolegle; funkcje bez rzeczywiście egzekwowanej zgody pozostają fail-closed do czasu uzgodnienia modelu.
3. Jeden logiczny problem = mały PR/commit; nie łączyć korekt security z masowym formatowaniem lub kasowaniem legacy. **Nie usuwać migracji Flyway i nie przepisywać niezmiennych historycznych snapshotów.**
4. Preferować JPA/Hibernate i jawne porty modułów, zgodnie z `AGENTS.md`. **`EntityManager.createNativeQuery` to natywny SQL JPA, nie `JdbcTemplate`**; dopuszczenie native SQL nie oznacza zgody na obchodzenie właściciela schematu.
5. Do każdego zadania: wskazane testy regresji, `git diff --check`, minimalne testy dotkniętego modułu oraz aktualizacja odpowiedniej dokumentacji/ADR. Pełne `mvn verify` i szersze E2E tylko przy zmianach kontraktów, migracji, bezpieczeństwa przekrojowego lub przed integracją większej serii.
6. Zadanie oznaczyć `DONE` dopiero po dowodzie testowym i przeglądzie zgodności z dokumentacją. `KEEP / MIGRATE / DELETE` dla legacy ustalać na podstawie konsumentów, migracji i skutków dla historii danych.

## P0 — działania do rozpoczęcia natychmiast

### P0-01 — Testy izolacji i wycieków danych biomedycznych [POTW.]
**Wdrożono (2026-11-14):** [PR #7](https://github.com/boryspawel/moves/pull/7) scalony do `main` (`fc772f8fbb4622949261042f20d69eb079508bac`). Mapa ścieżek pomiarów oraz 3 celowane testy (odmowa API, redakcja odczytów, prawdziwy grant i cofnięcie). Pełna macierz izolacji aktorów i przegląd pochodnych zdarzeń celów pozostają do P0-05; zgodnie z decyzją ograniczono liczbę nowych testów.

**Mapa ścieżek pomiarów:** `SpecialistParticipantMeasurementController` (catalog/list/record) → `ParticipantMeasurementService`; `SpecialistParticipantReadService` (workspace/recent, timeline/measurement, timeline-event/find) → `ParticipantMeasurementQueryPort`; `SpecialistAppointmentCloseoutController` (closeout/forAppointment) → ten sam port; `SpecialistParticipantHistoryService` (timeline, filtry, szczegóły, encounter) → `SpecialistMeasurementHistoryQueryPort`; `SpecialistParticipantMeasurementApplicationService` (zapis po walidacji wizyty) → serwis oraz projekcje `MeasurementGoalProjectionPort`. **Wszystkie odczyty repozytorium pomiarów przechodzą przez `ParticipantMeasurementService`, który wymaga teraz `MANAGE_PARTICIPANT_RECORDS + VIEW_MEASUREMENTS` i scope `MEASUREMENTS` w decyzji zgody.**

- **Problem:** `ParticipantMeasurementService.authorize()` sprawdza aktywną relację, nie decyzję zgody; `ConsentDecisionPort.DataScope` nie ma zakresu pomiarów. Istnieją odczyty pośrednie przez `ParticipantMeasurementQueryPort`.
- **Zadanie:** dodać macierz testów negatywnych i pozytywnych dla dwóch specjalistów / dwóch uczestników, ról, cofniętej relacji, braku/cofnięcia/wygasłej zgody i `ActingContext`. Objąć `GET/POST /api/v1/specialist/clients/{participantId}/measurements`, `SpecialistParticipantReadService.workspace()/timeline()`, `SpecialistAppointmentCloseoutController`, historię i projekcje celów. Zmapować wszystkie wywołania portu odczytu pomiarów.
- **Odbiór:** testy ujawniają aktualne luki (test red tam, gdzie potrzeba); udokumentowana kompletna mapa wejść/wyjść PHI i kontrakt oczekiwanego `403` lub redakcji odpowiedzi; żaden nowy test nie akceptuje samej relacji jako zgody.
- **Kod:** `participantmeasurements/*`, `application/workspace/SpecialistParticipantReadService.java`, `application/workspace/SpecialistAppointmentCloseoutController.java`, `participantgoals/*`, `consent/api/ConsentDecisionPort.java`.

### P0-02 — Fail-closed dla odczytu i zapisu pomiarów bez skutecznej zgody [POTW.]
**Wdrożono dla bieżących ścieżek pomiarów:** [PR #7](https://github.com/boryspawel/moves/pull/7), CI frontend i compose-smoke PASS, backend PASS przy ponownym uruchomieniu. Bez rzeczywistego grantu `MEASUREMENTS`: `403` dla odczytu/zapisu bezpośredniego, pusta projekcja odczytu pośredniego. `PLAN` nie zastępuje `MEASUREMENTS`; `TEST_DEFAULT` nadal wyłącznie w `local/test`. Pełny proces udzielania zgody kartotekom bez konta pozostaje P0-05.

- **Zadanie:** nie udostępniać pomiarów ani nie akceptować nowych zapisów, dopóki backend nie potrafi wykazać właściwej, aktywnej podstawy dostępu dla aktora, uczestnika, celu i zakresu. Zamknąć zarówno endpoint bezpośredni, jak i pośrednie projekcje API; nie zastępować zgody `PLAN`, rolą, aktywną relacją, checkboxem ani `TEST_DEFAULT` w środowisku produkcyjnym. Prawdziwy `MEASUREMENTS`/analogiczny scope i mechanizm dla kartotek bez konta wyodrębnić do P0-05.
- **Odbiór:** brak PHI w JSON, timeline, closeout, cache i logach przy braku uprawnienia; przypadki izolacji, cofnięcia i wygaśnięcia sprawdzane testami integracyjnymi; poprawne dotychczasowe operacje bez pomiarów działają dalej.
- **Uwaga:** brak grantu oznacza blokadę wrażliwej funkcji, nie automatyczne obdarzenie użytkownika domyślną zgodą.

### P0-03 — Jedna transakcja dla zakończenia sesji `/complete` [POTW.]
**Start: teraz; zależności: brak.**

**Implementacja (2026-11-14):** [PR #6](https://github.com/boryspawel/moves/pull/6) scalony do `main` (`4d805c50bef0d3c10bf9b9068a455c84a70e8d9d`). `SessionExecutionAttemptService.completeGuided()` obejmuje zapis wykonania i status próby jedną transakcją. Jeden test integracyjny weryfikuje zapis i idempotentny retry. GitHub CI: backend, frontend i compose-smoke — **PASS**. Osobny test fault-injection rollback pozostaje do decyzji przy dalszym hardeningu, zgodnie z ograniczeniem liczby testów.

- **Problem:** `SessionExecutionAttemptController.complete()` kolejno wywołuje transakcyjne `SessionExecutionService.declare()` i `SessionExecutionAttemptService.completeAfterFinalDeclaration()`; kontroler nie jest granicą wspólnej transakcji. `/finish` korzysta z jednego przypadku użycia.
- **Zadanie:** przenieść orkiestrację `/complete` do jednego transakcyjnego serwisu, zapewnić atomowość faktu wykonania, statusu próby, kwalifikacji i audytu oraz spójny replay `Idempotency-Key`. Nie usuwać endpointu przed migracją konsumentów.
- **Odbiór:** test z wymuszonym błędem drugiego kroku nie pozostawia utrwalonego wyniku bez stanu próby; równoległe i ponowione żądania nie dublują wykonania; nie zmieniają się historyczne snapshoty ani działanie `/finish`.
- **Kod:** `trainingexecution/SessionExecutionAttemptController.java`, `SessionExecutionAttemptService.java`, `SessionExecutionService.java`; testy integracyjne wykonania.

### P0-04 — Usunąć bezpośrednie ujawnianie PHI przez złożone widoki [POTW.]
**Częściowo zrealizowano:** [PR #7](https://github.com/boryspawel/moves/pull/7) — workspace, timeline, event detail, closeout i history wczytują pomiary wyłącznie przez autoryzowany port. Pozostaje osobny przegląd pochodnych obserwacji celów (P0-05).

- **Problem:** `SpecialistAppointmentCloseoutController` zwraca `measurements.forAppointment(...)` po walidacji kontekstu wizyty, ale bez wyspecjalizowanego rozstrzygnięcia zgody dla pomiarów. `SpecialistParticipantReadService` dołącza `measurements.recent(...)` do kartoteki.
- **Zadanie:** wprowadzić autoryzowany/redagowany port projekcji lub jawny gate na poziomie orkiestracji; nie wystawiać niechronionych metod `recent/timeline/forAppointment/find` jako wygodnych skrótów do chronionych widoków. Zwracane DTO i OpenAPI nie mogą obchodzić odrębnych uprawnień sekcji.
- **Odbiór:** regresja dla closeout, workspace, history/timeline i widoków celów; brak przypadkowego ujawnienia w odpowiedzi przy dostępie do wizyty, ale bez prawa do pomiarów; zachowana obsługa wizyty bez sekcji biometrycznej.
- **Zależność:** wyniki mapy P0-01; wdrażać łącznie z P0-02, jeśli API współdzieli projekcję.

### P0-05 — Docelowe scope'y i egzekwowanie zgody na pomiary/dokumentację [POTW. + DECYZJA]
**Etap techniczny wdrożony — [PR #8](https://github.com/boryspawel/moves/pull/8), commit `71815ffbb36067edf42bd04582fcc29f659cc4a5`, CI backend/frontend/compose-smoke oraz Android APK: PASS.** Oddzielny scope `DOCUMENTATION` i capability `MANAGE_PARTICIPANT_DOCUMENTATION` wymagane wraz z `MANAGE_PARTICIPANT_RECORDS`; istniejąca autoryzacja nadal egzekwuje rolę, relację, recipient, purpose, termin i cofnięcie grantu. Chronione są notatki i wywiady (zapis/odczyt), pośrednie timeline/detail oraz draft interview i podsumowanie wizyty. Zdarzenia dokumentacji są dodatkowo ograniczone do rekordów należących do danego specjalisty. Bez scope: endpointy dokumentacji odrzucają, złożone widoki redagują sekcję. Dodano **2 testy celowane**.
**Nadal otwarte P0-05:** legalny proces grantu dla kartotek bez konta i przedstawicieli, polityka retencji, rozdzielenie pochodnych wartości obserwacji celów i ich zakresu. Nie przyjęto domniemanej zgody ani nowych terminów retencji.

- **Problem:** `SpecialistAuthorizationService.requireCapabilities()` nie mapuje `MANAGE_PARTICIPANT_RECORDS` na żaden `DataScope`; notatki/wywiady mogą być dopuszczane przez samą capability, a pomiary przez relację. `ConsentGrantService` jest powiązany z kontem uczestnika.
- **Zadanie:** zdefiniować zakresy dla pomiarów i dokumentacji, model grantu/odwołania/ważności oraz kartotek bez konta, z uwzględnieniem przedstawiciela ustawowego; scentralizować enforcement przed odczytem i zapisem. Zapewnić fail-closed dla braku, cofnięcia, wygaśnięcia i niewłaściwego purpose; nie utożsamiać odpowiedzi wywiadu z grantem.
- **Odbiór:** reguły ustalone w ADR, testy macierzy aktor–rola–relacja–purpose–scope i przypadki odebrania uprawnień w toku sesji; produkcja nie opiera się na testowym override. Osobne zadanie retencji dla tych danych należy wygenerować na podstawie zatwierdzonej polityki, nie zgadywać terminów.
- **Zależności:** P0-01/02, decyzja w pytaniach na końcu.

**Uwaga walidacyjna:** pierwsze uruchomienie CI PR #7: niepowiązany tematycznie `FlywayMigrationIntegrationTest` zastał 69 zamiast 62 struktur anatomii; ponowny przebieg tego samego backendu bez zmian kodu — PASS. Podejrzenie niestabilnej izolacji danych testowych włączyć do P1-01; nie tworzyć w tym zadaniu dodatkowej baterii testów.

## P1 — architektura, kontrakty, jakość implementacji

### P1-01 — Domknąć audyt wszystkich produkcyjnych plików Java klasa po klasie [DO SPRAWDZENIA]
- **Zadanie:** opracować rejestr wszystkich 399 plików Java z pakietem, odpowiedzialnością, konsumentami, kontraktem/ADR, testem, oceną `KEEP/MIGRATE/DELETE`, wykrytymi smellami, długiem V1/V2 i linkiem do konkretnego miejsca kodu. Prowadzić osobne pakiety przeglądu: `identityaccess/consent/participant/specialist`; `calendar/availability/application/workspace`; `participantdocumentation/participantmeasurements/participantgoals`; `trainingplanning/planworkflow/trainingexecution/safety`; `exercisecatalog/exerciseimport/exercisesets/anatomyreference/loadanalysis`; `adherence/analytics/notification/gamification/audit/onboarding`.
- **Odbiór:** 399/399 plików z odnotowanym wynikiem, brak domniemania `martwy` bez analizy referencji, odkryte problemy dopisane do backlogu i uporządkowane priorytetem. Audyt nie zmienia kodu biznesowego.
- **Uwaga:** wcześniejsza analiza była szczegółowa dla katalogu i wyrywkowa dla reszty — nie deklarować pełnego pokrycia wstecznie.

### P1-02 — Wyeliminować dostęp między modułami przez cudze tabele i encje [POTW.]
- **Zadanie:** usunąć direct SQL z `exercisecatalog/ExerciseCatalogSearchService` do `anatomy_reference`; zastąpić `exercisecatalog/ExerciseReviewReadEntities` mapujące `exercise_import` przez port importu; zbadać `exerciseimport/ImportCatalogReadRepository` czyta `exercise_catalog`, oraz analogiczne cross-schema zapytania (bez automatycznego przenoszenia projekcji wyszukiwania z PostgreSQL do Java memory). Preferować batch query port, dedykowaną projekcję materializowaną albo jawny read model z właścicielem.
- **Odbiór:** jasno zdefiniowana własność tabel/projekcji, brak niedozwolonego odczytu repozytorium lub schematu innego modułu; test kontraktowy i regresja wyszukiwania/facetów/importu, bez pogorszenia wydajności.
- **Dokument:** [ADR-001](../adr/ADR-001-modular-monolith.md).

### P1-03 — Rozszerzyć reguły ArchUnit / statycznego audytu SQL [POTW.]
- **Zadanie:** egzekwować zakaz `JdbcTemplate` i `NamedParameterJdbcTemplate` w całym kodzie produkcyjnym; ograniczyć `java.sql`, wykrywać cross-module `JpaRepository` oraz zależności przez konkretne serwisy zamiast portów. Dodatkowy test/analizator źródeł SQL dla odwołań `schema.table`, których ArchUnit nie widzi. Rozróżnić dozwolony native JPA SQL we własnym module od niedozwolonego bezpośredniego dostępu między modułami.
- **Odbiór:** architektoniczny test red dla kontrolowanego przykładu naruszenia i green po naprawie, bez fałszywej deklaracji, że samo ArchUnit kontroluje tekst SQL.

### P1-04 — Jedna ścieżka publikacji ćwiczeń + transakcyjny outbox [POTW.]
- **Problem:** istnieją `CatalogService.publish()` i `ExerciseEditorialWorkflowService.publish()`; tylko druga ścieżka zapisuje zdarzenie do outboxu. Pozostałe stare operacje recenzji i zatwierdzania współistnieją z ADR-023.
- **Zadanie:** sprowadzić publikację do jednego przypadku użycia, walidacji obiektywnej, optimistic locking, outbox i audytu. `ExerciseCatalogAdminController.approve()` (cztery wywołania `review()`) ujednolicić transakcyjnie albo formalnie oznaczyć jako deprecated compatibility.
- **Odbiór:** wszystkie publiczne trasy publikacji dają ten sam rezultat i dokładnie jedno zdarzenie outbox; test rollback, concurrency i bezpośredni `DRAFT -> PUBLISHED` bez obowiązkowej recenzji.

### P1-05 — Zgodność ról administracyjnych z konfiguracją Keycloak [POTW. + DECYZJA]
- **Problem:** `ConsentController.publishTemplate()` wymaga `ROLE_ADMIN`; `GamificationController` wymaga `ROLE_GAMIFICATION_ADMIN`; lokalny realm deklaruje `SYSTEM_ADMIN` i `CONTENT_ADMIN`, ale nie te role.
- **Zadanie:** zinwentaryzować `hasRole/hasAnyRole` wszystkich kontrolerów, role lokalne i docelowe; ujednolicić role i politykę least privilege. Nie zastępować ślepo `SYSTEM_ADMIN` rolą o niższych uprawnieniach.
- **Odbiór:** autoryzowane operacje administracyjne działają dla właściwych ról; inne są odrzucane; testy Keycloak/JWT i dokumentacja ról. Pytanie o docelową separację ról na końcu.

### P1-06 — Budżety liczby zapytań i usunięcie N+1 [DO SPRAWDZENIA]
- **Kandydaci:** `ExerciseVersionReviewController.reviewQueue()` (odczyty per wersja), `CatalogService.editorialList()` (akcje i readiness per wynik), `ExerciseSetService.grantRecipients()` (nazwa per uczestnik), `ExerciseImportService.issueView()/detail()`, projekcje `SpecialistParticipantReadService`.
- **Zadanie:** pomiar liczby zapytań i `EXPLAIN (ANALYZE, BUFFERS)` na reprezentatywnych danych, dopiero potem batch fetch/projekcje/indeksy.
- **Odbiór:** porównanie before/after i test gwarantujący ograniczoną liczbę zapytań w co najmniej krytycznych ścieżkach; bez zgadywanych SLA.

### P1-07 — Jawne porty zamiast sprzęgania z serwisami sąsiednich modułów [POTW.]
- **Kandydaci:** `calendar/AppointmentService` i `onboarding/OnboardingService` -> konkretny `RecurringAvailabilityService`; `application/workspace/SpecialistAppointmentExecutionService` -> konkretne `SessionExecutionPersistence/SessionExecutionService`; `availability/RecurringAvailabilityService` -> `AdherenceMetricsService`.
- **Zadanie:** ocenić kierunek zależności, przenieść komunikację na publiczne porty lub eventy tam, gdzie moduł jest konsumentem; zachować prostotę modularnego monolitu.
- **Odbiór:** test ArchUnit granic dla poprawionych modułów, brak cykli i nieuzasadnionych zależności od implementacji.

### P1-08 — Sprawdzić kontrakt wykonania i zgodność starych oraz nowych endpointów [POTW. + DO SPRAWDZENIA]
- **Zadanie:** porównać `/session-attempts/{id}/finish`, `/complete`, `/planned-sessions/{id}/executions`, zamknięcie wizyty specjalisty, idempotency i zapis rzeczywistych dawek. Utworzyć macierz przypadków `COMPLETE/PARTIAL/SKIPPED/STOPPED`, korekt i projekcji `append-only`.
- **Odbiór:** brak rozbieżnych terminalnych rezultatów dla identycznych faktów i brak podwójnych kwalifikacji; testy przepływu specjalisty i uczestnika. W pierwszej kolejności zabezpieczyć P0-03.

### P1-09 — Sprawdzić kompletność i prawidłowość wdrożonej retencji [DO SPRAWDZENIA]
- **Zadanie:** odróżnić faktycznie wdrożoną retencję zdarzeń `analytics.adherencemetrics` (180 dni) od wymagań dla pomiarów, notatek, wywiadów, zgód, audytu, zaproszeń, artefaktów importu i backupów. Zebrać miejsca usuwania i nieusuwania danych, wymagania ochrony historii i relacji.
- **Odbiór:** tabela `data owner / klasyfikacja / trigger / okres / mechanizm usuwania / audyt / backup`; luki mają osobne zadania. Nie wprowadzać arbitralnych terminów bez zatwierdzonej polityki.

### P1-10 — Zabezpieczenia środowiskowe obejść testowych [POTW.]
- **Zadanie:** potwierdzić i przetestować fail-closed `TestDefaultConsentSafetyConfiguration`, `TestDefaultConsentOverrideAdapter`, `LocalTestProfessionalScopeOverride` dla `prod`, `local`, `test`, kombinacji profili i wyłączenia flag. Przejrzeć deployment flags, logi oraz możliwość błędnej konfiguracji.
- **Odbiór:** fail-fast produkcyjnego startu dla włączonych override; minimalne testy konfiguracji i opis ścieżki wycofania testowych wyjątków.

## P2 — legacy i refaktoryzacja bez zmiany funkcjonalności

### P2-01 — Wygasić ręczny review workflow katalogu [POTW.]
- **Kandydaci:** `ExerciseVersionReviewController`, `ExerciseReviewQueueController`, `ExerciseReviewQueryService`, `ExerciseReviewQueueRepositoryImpl`, odpowiednie projekcje i pola/enumy `IN_REVIEW/CHANGES_REQUESTED/APPROVED`.
- **Zadanie:** zinwentaryzować użycie przez Angular, klienty API i integracje; zachować historyczny odczyt tam, gdzie potrzebny; oznaczyć endpointy w OpenAPI, ustalić okres wygaszania, usunąć nieużywane fragmenty po migracji. Nie usuwać danych recenzji/migracji.
- **Odbiór:** brak aktywnych konsumentów usuniętego API; `DRAFT -> PUBLISHED` zgodnie z ADR-023; testy historii i starego klienta w okresie kompatybilności.

### P2-02 — Zamknąć V1 tworzenia planów i zweryfikować historyczne odczyty [POTW.]
- **Kandydaci:** `TrainingPlanningService.createSpecialistPlan()` (zawsze `410`), `TrainingPlanningController.create()`, komendy i DTO V1, `TrainingPlanningPersistence`.
- **Zadanie:** sprawdzić wywołania i referencje; wycofać martwy kod komendy, ale zachować odczyt `/planned-sessions` i odtwarzanie historycznych recept, dopóki nie ma pełnego następcy.
- **Odbiór:** dowód braku konsumentów przed skasowaniem trasy, aktualizacja OpenAPI/clienta i brak regresji widoku historii.

### P2-03 — Kontrolowana migracja starszego execution i fallbacku kwalifikacji [POTW.]
- **Kandydaci:** `/session-attempts/{id}/complete`, `JpaSessionExecutionAdapter.hasActiveQualification()` z fallbackiem dla `declarationEventId == null`, inne modele V1.
- **Zadanie:** najpierw ustalić wolumen danych historycznych i klientów, przygotować migrację forward-only lub jawny wieczny read adapter, potem usuwać wyłącznie zbędne ścieżki.
- **Odbiór:** niezmieniona interpretacja historycznych wykonań i kwalifikacji; test z fixture V1 + P4.

### P2-04 — Przegląd raportów `legacy` bez automatycznego kasowania danych [POTW.]
- **Kandydaci:** `SafetyV2Controller` (`admin/legacy/participant-restrictions`), `ExerciseCatalogAdminController` (`legacy/contraindications`), referencje `legacyRestrictions`, stary model celów i planu.
- **Zadanie:** sklasyfikować raporty jako migracyjne, audytowe lub stale używane. Zapisać decyzję `KEEP/MIGRATE/DELETE` z liczbą referencji i skutkiem dla historii.
- **Odbiór:** usunięte tylko rzeczywiście nieużywane ścieżki, pozostałe wyraźnie opisane; testy bezpieczeństwa danych historycznych.

### P2-05 — Rozbić przerośnięte serwisy na przypadki użycia [POTW.]
- **Kandydaci:** `CatalogService` (1039 linii / 14 zależności), `TrainingPlanningV2Service` (~902 l.), `SpecialistParticipantReadService` (~751 l.), `SessionExecutionAttemptService`, `ParticipantDocumentationService`, `ExerciseSetService`.
- **Zadanie:** separować komendy, odczyty, polityki domenowe i mappery DTO; wybierać klasy po zmianach P0/P1. Usunąć konstruktory kompatybilnościowe `SpecialistParticipantReadService` dopiero po ustaleniu referencji w testach. Nie tworzyć fasad pustych semantycznie.
- **Odbiór:** bez zmian REST/OpenAPI/DDL, minimalne testy domenowe i integracyjne, mniej zależności na przypadek użycia.

### P2-06 — Przywrócić sens podziałowi application/domain/infrastructure zestawów [POTW.]
- **Problem:** `ExerciseSetApplicationService` jest prawie wyłącznie delegatorem; `ExerciseSetService` w `infrastructure` realizuje reguły użycia, JPA i kompozycję. `ExerciseSetAggregate` zawiera tylko kilka statycznych walidatorów.
- **Zadanie:** przenieść zasady biznesowe i transakcje do warstwy application/domain, a adapterom zostawić persystencję; unikać mechanicznego dodawania interfejsów.
- **Odbiór:** zachowany kontrakt setów, grantów, published snapshot i analiza; testy domain/ports + granice ArchUnit.

### P2-07 — Zbadać lokalne duplikaty DTO/enkapsulacji i martwe metody [POTW. + DO SPRAWDZENIA]
- **Pewne do usunięcia:** prywatne `ExerciseEditorialWorkflowService.latestApproved()` i `latestReview()`, jeżeli analiza referencji w repo nie wykaże ukrytej potrzeby.
- **Do sprawdzenia:** przeciążone DTO V1/V2, schematy `ImportedExercise*` i `ExerciseReview*`, podwójne translacje enumów, nieużywane overloady.
- **Odbiór:** usunięte tylko potwierdzone symbole, kompilacja i testy modułu bez zmiany publicznych kontraktów.

## P3 — dokumentacja i prace nieblokujące

### P3-01 — Uaktualnić dokumentację do faktycznej architektury [POTW.]
- **Zadanie:** uzgodnić `spec.md` (Ionic/Capacitor i Maven multi-module / Spring Modulith) z aktualnym natywnym Android Kotlin/Compose oraz pojedynczym modułem Maven i ArchUnit, odróżniając plany od stanu wdrożenia. Uaktualnić `technical-overview.md` i `current-state.md` (m.in. już commitowane testy `/sessions`).
- **Odbiór:** jeden wskazany dokument źródłowy dla każdej decyzji technologicznej; brak sprzecznych stwierdzeń as-is; ADR przy zmianie decyzji produktu.

### P3-02 — Standardy czytelności i ograniczona higiena kodu [POTW.]
- **Kandydaci:** zminifikowane klasy `ParticipantMeasurementService`, `ParticipantDocumentationService`, `ExerciseSetService`, wielokrotne importy wildcard, metody w jednej linii, konstruktorowe kompatybilności `null`.
- **Zadanie:** formatować i nazywać po zakończeniu ważniejszych zmian; bez zmiany reguł biznesowych, masowych PR i generowanego kodu.
- **Odbiór:** czytelny diff, brak regresji, jednoznaczna separacja formatowania od poprawek funkcjonalnych.

### P3-03 — Baseline wydajności i obserwowalności [DO SPRAWDZENIA]
- **Zadanie:** reprezentatywne benchmarki wyszukiwania/facetów katalogu, ładowania kartoteki/timeline, listy ćwiczeń i planów. Zebrać liczby zapytań, p95 i plany wykonania na realistycznych danych; oddzielić brak zmierzonego SLA od błędu.
- **Odbiór:** powtarzalne wyniki i jawne limity dopiero po pomiarach.

## Organizacja dostarczania

| Fala | Zadania | Bramka akceptacji |
|---|---|---|
| **A — natychmiast** | P0-01, P0-03, P0-04; rozpoczęcie P0-02 | Testy danych wrażliwych, atomowa finalizacja, brak bocznego ujawnienia pomiarów |
| **B — zabezpieczenie docelowe** | domknięcie P0-02/P0-05, P1-05, P1-10 | Ochrona przez właściwy consent/capability, poprawne role, brak produkcyjnych override |
| **C — granice i pełny audyt** | P1-01, P1-02, P1-03, P1-04, P1-07 | Pokrycie 399 plików, jednolita publikacja i egzekwowane zależności |
| **D — poprawność i mierzalność** | P1-06, P1-08, P1-09 | Testy regresji, zapytań, historii i rejestr retencji |
| **E — kontrolowany cleanup** | P2-01…P2-07, P3-01…P3-03 | Brak zerwanych kontraktów, historyczne dane czytelne, dokumentacja zgodna z kodem |

**Pierwsze zadanie wykonawcze dla Codex:** P0-01 (testy i mapa PHI), a następnie P0-03 (atomowe `/complete`). Nie wdrażać naraz całego backlogu. Przy pracach implementacyjnych przed zmianą sprawdzić aktualne źródła/ADR, po zmianie uaktualnić dokumentację. Testować selektywnie; dla zmian bezpieczeństwa obowiązują integracyjne przypadki negatywne.

## Pytania i decyzje odłożone na koniec

1. **Zgody:** jakie dokładnie podstawy prawne, scope'y i proces udzielania/odwołania zgody mają obowiązywać dla pomiarów, notatek i wywiadów, zwłaszcza dla kartoteki bez konta oraz osoby małoletniej/przedstawiciela? Do czasu decyzji: fail-closed dla danych bez wykazanego uprawnienia.
2. **Role admina:** czy `SYSTEM_ADMIN` ma publikować szablony zgód i zarządzać gamifikacją, czy wymagane są odrębne role `CONSENT_ADMIN` / `GAMIFICATION_ADMIN`? Najpierw wykonać audyt ról i testy negatywne.
3. **Kompatybilność:** czy są zewnętrzni klienci wywołujący V1 `/training-plans`, `/session-attempts/{id}/complete` oraz endpointy review? Jaki jest wymagany okres ich wsparcia?
4. **Historia:** jak długo trzeba zachować dawną semantykę kwalifikacji wykonań, raporty `legacy` i zapis recenzji? Brak zgody na fizyczne usuwanie historii.
5. **Retencja:** jakie okresy i obowiązki prawne dotyczą poszczególnych rodzajów danych medycznych, dziennika audytu, zaproszeń, importów i kopii zapasowych? W pierwszej kolejności sporządzić tabelę P1-09.
6. **Mobile:** czy `spec.md` ma zostać formalnie zmienione z Ionic/Capacitor na Kotlin/Jetpack Compose dla aplikacji specjalisty, z odrębnym rozstrzygnięciem dla aplikacji uczestnika?
