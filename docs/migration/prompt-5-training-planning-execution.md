# Prompt 5 — planowanie i wykonanie treningu

## Pochodzenie i ograniczenie dowodowe

- repo: `../gra-mateusza`, branch `main`, lokalny HEAD `0b994cb6e35319837a2c81de4fb9a556b1943b90`;
- wskazane SHA `fd539c32e09824fca944b1e424030971135726b0` i `f02551285956b5ab7dc150f3c36b4bc44dc33db7` nie istnieją w lokalnej bazie obiektów;
- zweryfikowany HEAD zawiera `Exercise`, `TrainingSet` i `Workout`, ale nie zawiera kompletnego modelu celu, cyklu, mikrocyklu, recepty ani wykonania powiązanego z planem;
- brak MCP IntelliJ w środowisku pozostaje jawnym odstępstwem narzędziowym. Użyto read-only Git/`rg`, inspekcji SQL i testów Maven.

Żadnej reguły nie przypisano nieodczytanym commitom. Implementacja jest kontrolowanym rewrite zgodnym z `spec.md` i wymaganiami promptu.

## Mapowanie PORT / REWRITE / REFERENCE / REJECT

| Model źródłowy / zachowanie | Model docelowy | Decyzja i uzasadnienie |
|---|---|---|
| `TrainingSet` z listą ćwiczeń | `TrainingGoal` → `TrainingPlan` → `TrainingCycle` → `Microcycle` → `PlannedSession` → `ExercisePrescription` | REWRITE; źródłowa płaska lista nie zachowuje wymaganej hierarchii |
| `Workout` z elementami JSON | typowana sesja i recepty w tabelach relacyjnych | REWRITE; odrzucono niekontrolowany JSON jako rdzeń planu |
| identyfikator ćwiczenia | identyfikator konkretnej opublikowanej `ExerciseVersion` | REWRITE; walidacja przez publiczny port katalogu |
| publiczne zestawy i flagi autora | plan przypisany przez specjalistę z aktywną relacją | REWRITE; autoryzacja zasobowa zamiast flagi publiczności |
| brak potwierdzonego modelu wykonania | `SessionExecution`, `ExerciseResult`, `PainDifficultyReport`, `ExecutionCorrection` | REWRITE z wymagań; nie oznaczono jako PORT |
| `WorkoutAuthorType` | `PlanMode.SPECIALIST_ASSIGNED` / zarezerwowane `SELF_DIRECTED` | REFERENCE; self-directed nie jest aktywowane bez modelu zatwierdzonego zakresu |
| mechanizmy XP/power map | brak zależności w planowaniu i wykonaniu | REJECT w tym slice; gamifikacja jest osobnym Promptem 6 |
| kalendarz/rozliczenia/ML | brak | REJECT / poza zakresem |

## Zachowane i nowe reguły

- plan jest tworzony tylko przez konto typu `SPECIALIST` posiadające rolę techniczną i aktywną relację z uczestnikiem;
- obcy specjalista nie odczyta wykonań ani nie przypisze planu;
- recepta zapisuje dokładny UUID opublikowanej wersji ćwiczenia; draft jest odrzucany;
- `SELF_GUIDED` i `OFFLINE_APPOINTMENT` są osobnymi wartościami domenowymi;
- wykonanie wymaga jawnego `declaredCompletion=true`, wskazuje sesję i każdą jej receptę;
- klucz `Idempotency-Key` jest unikalny per uczestnik, a retry zwraca ten sam zapis;
- wykonanie, wynik i raport są append-only. Korekta dopisuje `ExecutionCorrection` i zdarzenie audytowe, nie zmienia raportu pierwotnego;
- każdy zgłoszony ból większy od zera tworzy neutralny alert `PAIN_REPORTED`; nie jest to diagnoza ani próg medyczny;
- przecięcie jawnych tagów ograniczeń uczestnika z tagami przeciwwskazań recepty powoduje hard block przed zapisaniem wykonania;
- safety i ból nie są przekazywane do gamifikacji.

## API vertical slice

- `POST /api/v1/training-plans` — prosty plan specjalisty wraz z jedną sesją i receptami;
- `GET /api/v1/planned-sessions` — sesje bieżącego uczestnika;
- `POST /api/v1/planned-sessions/{sessionId}/executions` — deklaracja wykonania z idempotencją;
- `POST /api/v1/session-executions/{executionId}/corrections` — append-only korekta;
- `GET /api/v1/specialist/participants/{participantAccountId}/executions` — wykonania i alerty dostępne w aktywnej relacji.

Relacja jest wymagana jako istniejąca. Proces zaproszenia, akceptacji i zakończenia relacji nie został wymyślony bez zatwierdzonych reguł produktu.

## Dane i granice

Migracja V004 tworzy schematy `training_planning` i `training_execution` oraz tabelę relacji w schemacie `specialist`. Identyfikatory kont i wersji ćwiczeń są referencjami między kontekstami bez FK; przypadki użycia walidują je przez porty aplikacyjne. Wewnątrz każdego kontekstu hierarchia ma klucze obce i ograniczenia zakresów.

## Walidacja

Celowany `TrainingPlanningExecutionIntegrationTest`: 3 testy, sukces na Java 25 i PostgreSQL 18.4/Testcontainers. Pokrycie obejmuje aktywną/obcą relację, dokładną wersję, rozróżnienie rodzaju sesji, jawne wykonanie, komplet recept, retry, ból/trudność, alert, historię korekt, hard block i publikację ścieżek OpenAPI.

## Dług techniczny

- brak workflow zaproszenia i akceptacji relacji specjalista–uczestnik;
- `SELF_DIRECTED` pozostaje nieaktywne do zdefiniowania zatwierdzonego zakresu samodzielnej edycji;
- planowanie tworzy obecnie jeden cykl, jeden mikrocykl i jedną sesję na żądanie — pełny edytor hierarchii jest późniejszym slice;
- retry nie przechowuje skrótu payloadu; ten sam klucz i ta sama sesja zwracają istniejący wynik;
- wersja ćwiczenia musi pozostawać opublikowana także w chwili wykonania. Semantyka wykonania planu po późniejszym wycofaniu wersji wymaga decyzji produktu;
- hard block korzysta wyłącznie z jawnego przecięcia tagów; nie ma diagnozy ani automatycznych zaleceń;
- brak pełnego kalendarza, wizyt, rozliczeń i powiadomień zgodnie z zakresem promptu.
