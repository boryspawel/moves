# MOVES — prompty implementacyjne dla Codex

Zakres: Training Planning, Exercise Catalog V2, Load Analysis, Safety V2 i Training Execution  
Tryb pracy: lokalne repozytorium otwarte w IntelliJ IDEA  
Kolejność: prompty wykonywać sekwencyjnie, każdy w osobnej turze Codex  
Status bazowy: `main`, commit `950aed64cc2153d20a739f17ea2a50e927eabe18`, Spring Boot 4.1.0, Java 25

## Jak korzystać z dokumentu

1. Udostępnij Codex repozytorium `moves` oraz dokument `moves-training-planning-service-architecture.md`.
2. Na początku wklej **Kontrakt nadrzędny**.
3. Następnie wklej wybrany prompt etapowy.
4. Nie uruchamiaj kolejnego etapu, dopóki poprzedni nie zakończy się zielonym `mvn verify` i przeglądem diffu.
5. Po każdym etapie samodzielnie przejrzyj raport Codex. Commit wykonuj dopiero po akceptacji etapu.

Prompty zakładają pracę backendową. Frontend Angular nie jest częścią tej serii, poza regeneracją i kontrolą kontraktu OpenAPI w końcowym audycie.

---

# KONTRAKT NADRZĘDNY — dołącz do każdego promptu

```text
Pracujesz w lokalnym repozytorium `moves` otwartym w IntelliJ IDEA. Masz wykonać rzeczywistą implementację, a nie tylko przygotować plan lub przykładowy kod.

ZANIM ZMIENISZ KOD

1. Przeczytaj wszystkie obowiązujące `AGENTS.md` i instrukcje repozytorium.
2. Ustal bieżącą gałąź, HEAD i stan working tree. Nie cofaj ani nie nadpisuj zmian użytkownika. Rozdziel istniejące zmiany od zmian tego zadania.
3. Przeczytaj:
   - `spec.md`;
   - `docs/architecture/technical-overview.md`;
   - `moves-training-planning-service-architecture.md` albo przekazany odpowiednik tego dokumentu;
   - kod, migracje i testy modułów objętych zadaniem.
4. Zweryfikuj stan as-is w kodzie, nie polegaj wyłącznie na opisie promptu.
5. Przygotuj krótki plan wykonania i wskaż wykryte konflikty. Jeżeli konflikt zmienia sens domeny, wymaga destrukcyjnej migracji albo narusza dokument nadrzędny, zatrzymaj się i poproś o decyzję. Drobne decyzje implementacyjne podejmuj samodzielnie i dokumentuj.

WERSJE I PLATFORMA

- Java 25 jest obowiązkowa. Przed każdym uruchomieniem Maven/testów aktywuj Java 25 przez SDKMAN i potwierdź wynik `java -version`.
- Używaj najnowszego stabilnego wydania GA Spring Boot dostępnego w chwili wykonywania promptu. Nie używaj wersji snapshot, milestone ani release candidate.
- Na początku pierwszego etapu sprawdź wersję zadeklarowaną w repozytorium oraz najnowszą wersję GA w oficjalnej dokumentacji Spring. Jeżeli repozytorium jest starsze, wykonaj najpierw kontrolowaną aktualizację baseline'u wraz z matrycą kompatybilności, testami regresji i osobnym opisem zmian; nie mieszaj napraw migracyjnych z logiką domenową.
- Stosuj API, wzorce i zalecenia właściwe dla faktycznie używanej, najnowszej wersji Spring Boot oraz zarządzanych przez nią wersji Spring Framework, Spring Data, Jakarta Persistence i Hibernate ORM.
- Nie utrzymuj sztucznej kompatybilności wstecznej ze Spring Boot 4.0.x i nie wykonuj downgrade'u.
- Zależności zarządzane przez Spring Boot pobieraj z BOM-u. Nie przypinaj ręcznie wersji Hibernate, Spring Data, Jacksona ani bibliotek zarządzanych przez Boot.
- Używaj `jakarta.*`, nigdy `javax.*`.
- Nie używaj snapshotów, milestone’ów, release candidate ani nieudokumentowanych wewnętrznych API Spring/Hibernate.
- Nie dodawaj biblioteki tylko dla kilku prostych klas. Każda nowa zależność wymaga uzasadnienia i weryfikacji zgodności z Boot 4.

ARCHITEKTURA

- System pozostaje modularnym monolitem. Nie twórz mikroserwisów, osobnych deployables ani brokera wiadomości.
- Granice modułów są rzeczywiste. Moduł nie używa encji, repozytorium ani tabel innego modułu. Integracja odbywa się przez publiczny port aplikacyjny, neutralny snapshot/DTO albo zdarzenie outbox.
- Nie wykonuj bezpośredniego SQL do obcego schematu.
- Nie twórz wspólnego „god service”. Przypadek użycia orkiestruje porty, a reguły biznesowe należą do modelu domenowego lub wyspecjalizowanej polityki.
- Zachowuj kierunek zależności: API/infrastructure zależy od application/domain, a domain nie zależy od Spring MVC, JPA, Security ani infrastruktury.
- Stosuj SOLID, wysoką kohezję, małe odpowiedzialności i nazwy opisujące język domeny.
- Preferuj kompozycję zamiast dziedziczenia. Nie twórz abstrakcji bez co najmniej jednego realnego zastosowania.
- Nie twórz dwóch silników dla trenera i fizjoterapeuty. Wspólny pozostaje model planu, dawki, obciążenia i wykonania; różne są cele, capabilities, ograniczenia oraz workflow.
- Trener zarządza celami sportowymi i `PlanLoadBudget`. Fizjoterapeuta zarządza celami funkcjonalnymi i klinicznym `Restriction`.
- `PlanLoadBudget` nie może generować `HARD_BLOCK`. Twarda blokada wymaga jawnego ograniczenia klinicznego/operacyjnego i wersjonowanej reguły.
- Spotkanie offline należy do kalendarza. Nie jest typem planowanej sesji samodzielnej.
- Nie wprowadzaj ML do ścieżki dopuszczenia planu.

JAVA 25 I CLEAN CODE

- Używaj rekordów dla niezmiennych DTO, komend, wyników i snapshotów, jeżeli poprawia to czytelność.
- Encje JPA nie mogą być rekordami. Powinny mieć chroniony konstruktor bezargumentowy, kontrolowane metody domenowe i brak publicznych setterów.
- Używaj enumów i value objects zamiast magicznych stringów/liczb.
- Do wartości dziesiętnych i obciążeń używaj `BigDecimal` z jawnie określoną skalą/zaokrągleniem; nie używaj `double` do danych domenowych.
- Chwile zapisuj jako `Instant`, daty planu jako `LocalDate`; strefę IANA przechowuj tylko tam, gdzie potrzebna jest lokalna interpretacja czasu.
- Nie używaj `Optional` w polach encji ani DTO wejściowych.
- Nie zwracaj `null`-owych kolekcji.
- Waliduj niezmienniki w domenie niezależnie od Bean Validation na granicy HTTP.
- Nie łap ogólnego `Exception` bez konkretnej strategii obsługi.
- Nie ukrywaj znaczącej logiki w listenerach JPA, mapperach ani kontrolerach.
- Komentarze mają wyjaśniać decyzję lub ograniczenie, nie przepisywać kod.

JPA I HIBERNATE

- Spring Data JPA i Hibernate są adapterem persystencji, a nie modelem domenowym całej aplikacji.
- Agregat wyznacza granicę transakcji. `@Transactional` umieszczaj na publicznym przypadku użycia; odczyty oznaczaj `readOnly=true`.
- Stosuj `@Version` dla współbieżnie edytowanych agregatów, szczególnie draftów i ograniczeń.
- Preferuj `LAZY`; nie dodawaj `EAGER`, aby naprawić `LazyInitializationException`.
- Nie włączaj Open Session in View. Zachowaj albo ustaw `spring.jpa.open-in-view=false`.
- Unikaj niekontrolowanych relacji dwukierunkowych i `CascadeType.ALL`. Kaskady dobieraj do granic agregatu.
- Nie wystawiaj encji JPA w REST ani przez publiczne porty modułu.
- Dla widoków listowych używaj jawnych projekcji/query DTO i rozwiązuj N+1 przez odpowiednie zapytanie, entity graph lub fetch plan. Nie pobieraj całego katalogu, aby filtrować w pamięci.
- Paginuj potencjalnie rosnące kolekcje.
- Ustal deterministyczne sortowanie.
- Każdy niezmiennik wymagający globalnej spójności wzmocnij ograniczeniem bazodanowym.
- Konflikt unikalności i optimistic locking mapuj na kontrolowany wynik domenowy/HTTP, nie na przypadkowe 500.
- Flyway jest jedynym mechanizmem DDL. `ddl-auto` pozostaje `validate`.
- Migracje są forward-only. Nie zmieniaj V001–V005 ani migracji, które mogły już zostać zastosowane. Wybierz następną wolną wersję po sprawdzeniu repozytorium.
- Cross-module UUID mogą nie mieć FK zgodnie z ADR, ale muszą być walidowane przez port i chronione niezmiennikiem aplikacyjnym. Wewnątrz modułu stosuj FK, indeksy i CHECK constraints.
- JSONB stosuj tylko dla wersjonowanych, rzeczywiście zmiennych metadanych. Pola używane przez reguły, filtrowanie i integralność modeluj relacyjnie.

API I BEZPIECZEŃSTWO

- REST pozostaje pod `/api/v1`; DTO są oddzielone od encji.
- Walidacja wejścia używa Bean Validation oraz walidacji domenowej.
- Błędy zwracaj jako spójny RFC 9457 `ProblemDetail` bez ujawniania danych medycznych i szczegółów infrastruktury.
- Autoryzacja backendu sprawdza rolę/capability, relację z uczestnikiem, zakres zgody, właściciela zasobu i cel operacji.
- Route guard frontendu nigdy nie jest zabezpieczeniem domenowym.
- Dane bólu, ograniczeń i uzasadnień klinicznych nie trafiają do gamifikacji, logów ani neutralnych zdarzeń.
- Operacje podatne na retry przyjmują `Idempotency-Key` i posiadają ochronę w bazie.

TESTY I JAKOŚĆ

- Najpierw dodaj test odtwarzający nowy niezmiennik albo błąd, następnie implementację.
- Reguły domenowe testuj bez Springa.
- Persystencję, transakcje, blokady, migracje i endpointy testuj na PostgreSQL przez Testcontainers, nie przez H2.
- Testuj happy path, przypadki negatywne, autoryzację, współbieżność i idempotencję.
- Dodaj testy ArchUnit/Modulith dla nowych granic i zakazanych zależności.
- Nie osłabiaj testu, aby przeszedł. Nie używaj `|| true`, wyłączania testów ani przypadkowych timeoutów.
- Po zmianach uruchom testy celowane, następnie pełne `mvn verify` na Java 25.
- Sprawdź `git diff --check`, migracje Flyway, OpenAPI oraz brak przypadkowego rozszerzenia zakresu.
- Wykonaj końcowy self-review diffu pod kątem: błędów domenowych, wyścigów, N+1, brakujących indeksów, wycieku danych i złamania granic modułów.

ZASADY PRACY

- Korzystaj z dostępnych skills i MCP IntelliJ do nawigacji, refaktoryzacji, analizy zależności i uruchamiania testów. Nie zakładaj nazw ani ścieżek, których nie zweryfikowałeś.
- Nie zmieniaj `spec.md`, wcześniejszych dokumentów bazowych ani raportu SOTA.
- Możesz dodać nowy ADR lub dokument implementacyjny, jeśli dana decyzja nie mieści się w istniejącej dokumentacji.
- Nie naprawiaj niezależnego frontendu ani innych modułów, chyba że kompilacja jest bezpośrednio złamana przez kontrakt tego etapu.
- Nie commituj, nie pushuj i nie otwieraj PR bez wyraźnego polecenia.
- Jeśli testy zależą od niedostępnej infrastruktury, wyczerp lokalne bezpieczne możliwości i zgłoś dokładny blocker; nie deklaruj sukcesu bez wykonanej walidacji.

RAPORT KOŃCOWY

Podaj:
1. rezultat biznesowy i architektoniczny;
2. stan gałęzi, HEAD i working tree przed oraz po zmianach;
3. listę zmienionych plików według modułu;
4. migracje i niezmienniki bazy;
5. testy dodane oraz dokładnie wykonane walidacje;
6. decyzje i kompromisy;
7. pozostały dług/ryzyka;
8. potwierdzenie, czy etap spełnia wszystkie kryteria „done”.
```

---

# PROMPT 1 — integralność fundamentu i granice modułów

```text
CEL

Przygotuj bezpieczny fundament pod Training Planning V2. Napraw krytyczne niezmienniki obecnej implementacji oraz ustanów egzekwowalne granice modułów. Nie implementuj jeszcze anatomii ani nowego modelu planu.

KONTEKST AS-IS DO ZWERYFIKOWANIA

- `TrainingPlanningService` odczytuje bezpośrednio `identity_access.principal_account` przez `JdbcTemplate`.
- `SessionExecution` chroni idempotencję kluczem uczestnika, ale baza nie gwarantuje jednego skutecznego wykonania jednej planowanej sesji.
- `PlannedSession.SessionKind` zawiera `OFFLINE_APPOINTMENT`, mimo że appointment jest osobnym procesem.
- Test architektury sprawdza przede wszystkim cykle, ale nie publiczne granice ani zakaz infrastrukturalnych zależności.

ZAKRES IMPLEMENTACJI

1. Wprowadź publiczny port w module właściciela kont/profili, przez który planowanie sprawdza aktywnego uczestnika. Usuń bezpośrednie zapytanie planowania do tabeli `identity_access`.
2. Dodaj bazodanowy niezmiennik zapewniający najwyżej jedno skuteczne wykonanie planowanej sesji. Zachowaj idempotencję per uczestnik i klucz.
3. Obsłuż race condition dla dwóch równoległych żądań z tym samym kluczem oraz dwóch różnych kluczy dla tej samej sesji. Oczekiwany wynik ma być deterministyczny i nie może kończyć się przypadkowym 500.
4. Zablokuj tworzenie nowych `OFFLINE_APPOINTMENT` przez Training Planning. Nie usuwaj destrukcyjnie wartości z istniejącej migracji. Wprowadź kompatybilny etap przejściowy i test.
5. Dla przypadków użycia planowania i wykonania dotkniętych tym zadaniem zastąp SQL składany w serwisach aplikacyjnych repozytoriami/adapterami JPA. Encje i repozytoria pozostają prywatne dla modułu; publiczne porty zwracają DTO/snapshoty. Nie wykonuj masowej konwersji niezwiązanych modułów.
6. Rozszerz testy architektury co najmniej o:
   - zakaz zależności domeny od Spring/JPA/API;
   - zakaz używania repozytorium/infrastruktury innego modułu;
   - zakaz `JdbcTemplate` i natywnego SQL w warstwach domain/application `trainingplanning` oraz w dotkniętym fragmencie `trainingexecution`;
   - brak cykli.
7. Utrzymaj dotychczasowe API tam, gdzie nie zagraża to bezpieczeństwu.

OGRANICZENIA

- Nie rozpoczynaj jeszcze migracji ograniczeń ani katalogu V2.
- Nie przenoś wszystkich pakietów tylko dla estetyki. Wprowadź najmniejszą strukturę, która rzeczywiście egzekwuje granice.
- Nie dodawaj FK między schematami modułów, jeśli narusza to przyjęty ADR.
- Nie zmieniaj znaczenia append-only wykonania.

TESTY OBOWIĄZKOWE

- równoległe wywołanie tego samego wykonania z tym samym kluczem;
- równoległe wywołanie tej samej sesji z różnymi kluczami;
- próba wykonania sesji przez innego uczestnika;
- próba utworzenia offline appointment przez endpoint planowania;
- test portu aktywnego uczestnika;
- rozszerzone testy ArchUnit;
- pełne `mvn verify`.

DONE WHEN

- `trainingplanning` nie czyta tabel `identity_access`;
- jedna planowana sesja nie może wygenerować dwóch skutecznych wykonań;
- retry zwraca stabilny wynik;
- nowe appointment nie przechodzą przez model sesji samodzielnej;
- granice są egzekwowane testami;
- wszystkie testy są zielone.
```

---

# PROMPT 2 — Anatomy Reference

```text
CEL

Zaimplementuj mały, samodzielny moduł `anatomyreference`, który będzie wspólną, wersjonowaną taksonomią dla Exercise Catalog i Safety. Nie implementuj jeszcze obliczania obciążenia.

KRYTERIUM WEJŚCIA

Prompt 1 musi być zakończony, a pełny backend `mvn verify` zielony. Jeżeli granice modułów nie są egzekwowane, zatrzymaj zadanie i zgłoś brak.

MODEL MVP

`AnatomicalStructure`:
- UUID;
- unikalny stabilny `code`;
- `type`: `BODY_REGION`, `MUSCLE_GROUP`, `MUSCLE`, `TENDON_GROUP`, `JOINT`;
- `displayName`;
- `sidePolicy`: `NONE`, `LEFT_RIGHT`;
- status publikacji;
- opcjonalne `externalOntology` i `externalOntologyId`;
- wersja taksonomii/audyt utworzenia.

`AnatomicalStructureRelation`:
- parent;
- child;
- `relationType`: `PART_OF`, `MEMBER_OF`, `FUNCTIONALLY_GROUPED_AS`;
- brak self-reference;
- brak duplikatów;
- brak cykli.

ZAKRES IMPLEMENTACJI

1. Utwórz moduł z podziałem domain/application/infrastructure/api zgodnym z bieżącą organizacją repo, bez nadmiernej liczby interfejsów.
2. Dodaj migrację Flyway w osobnym schemacie `anatomy_reference`.
3. Dodaj indeksy dla kodu, typu, statusu i relacji parent/child.
4. Zaimplementuj przypadki użycia draft/publish/withdraw oraz dodawanie relacji.
5. Opublikowana struktura jest semantycznie niezmienna. Zmiana znaczenia wymaga nowej wersji taksonomii albo nowej struktury; doprecyzuj prosty mechanizm MVP.
6. Udostępnij publiczny port odczytu snapshotu struktury oraz rozwinięcia przodków. Inne moduły nie mogą korzystać z encji ani repozytorium.
7. Dodaj administracyjne REST API z autoryzacją `CONTENT_ADMIN` albo bardziej odpowiednią istniejącą rolą.
8. Nie importuj pełnych FMA/Uberon. Przechowuj tylko opcjonalny external reference.
9. Dodaj minimalny seed testowy wyłącznie w fixtures/testach. Nie wprowadzaj arbitralnego katalogu produkcyjnego bez procesu redakcyjnego.

WAŻNY NIEZMIENNIK

Hierarchia nie może prowadzić do podwójnego liczenia. Na tym etapie zapisz i przetestuj sposób deterministycznego uzyskania ścieżki przodków, ale nie sumuj ekspozycji.

TESTY OBOWIĄZKOWE

- utworzenie i publikacja struktury;
- unikalność kodu;
- ochrona opublikowanej struktury;
- relacja parent/child;
- odrzucenie self-link i cyklu pośredniego;
- autoryzacja admin/non-admin;
- migracja na PostgreSQL;
- publiczny port nie ujawnia encji JPA;
- testy architektury i pełne `mvn verify`.

DONE WHEN

- moduł jest niezależny;
- katalog i safety mogą w przyszłości odwoływać się tylko do UUID/snapshotu;
- cykl hierarchii jest niemożliwy także przy współbieżnych zapisach albo odpowiednio chroniony transakcyjnie;
- brak pełnej ontologii i overengineeringu.
```

---

# PROMPT 3 — Exercise Catalog V2 i profile ekspozycji

```text
CEL

Rozszerz `exercisecatalog` o wersjonowane profile anatomiczne i charakterystyki obciążenia. Zastąp model „ćwiczenie ma tag przeciwwskazania” modelem „ćwiczenie opisuje ekspozycję”, bez destrukcyjnego usuwania danych legacy.

KRYTERIUM WEJŚCIA

Moduł `anatomyreference` i jego publiczny port muszą istnieć. Nie używaj jego repozytoriów ani tabel bezpośrednio.

MODEL

Dodaj do opublikowanej wersji ćwiczenia:

- wiele wzorców ruchowych zamiast jednego pola, jeśli potwierdzi to analiza obecnego API;
- `ExerciseContribution`:
  - `anatomicalStructureId`;
  - `role`: `PRIMARY`, `SECONDARY`, `STABILIZER`;
  - `loadChannel`;
  - `contributionBand`: `LOW`, `MODERATE`, `HIGH`;
  - `coefficientLow`, `coefficientHigh`;
  - `confidenceClass`;
  - `evidenceGrade`;
  - `calculationRole`: `ALLOCATION`, `DESCRIPTIVE_ONLY`;
  - warunek wariantu i reguła strony;
- `ExerciseLoadCharacteristic` dla płaszczyzny, rodzaju skurczu, ROM i charakteru: dynamic/isometric/eccentric emphasis/impact/compression/shear/rotation/stabilization;
- `EvidenceSource` i powiązanie contribution–evidence;
- workflow recenzji: draft, in review, changes requested, approved, published, withdrawn.

ZASADY

1. Nie nazywaj współczynnika procentem pracy ani siły mięśnia.
2. Przedział musi spełniać `0 <= low <= high <= 1`.
3. Dla jednej wersji, kanału, wariantu i gałęzi anatomii wpisy `ALLOCATION` nie mogą powodować podwójnego liczenia rodzica i dziecka. Zaimplementuj walidator korzystający z publicznego portu anatomii.
4. Szersze wpisy opisowe mają `DESCRIPTIVE_ONLY` i nie uczestniczą później w kalkulacji.
5. Publikacja wymaga kompletnego i spójnego profilu oraz recenzji.
6. Opublikowana wersja pozostaje niezmienna.
7. `exercise_version_contraindication` pozostaje legacy read-only. Usuń używanie tych tagów w nowych komendach/API, ale przygotuj raport unmapped legacy data. Nie mapuj tagów automatycznie na anatomię.
8. Napraw obecne filtrowanie w pamięci/N+1. Widok katalogu powinien używać paginowanej projekcji i deterministycznie zwracać bieżącą wersję przeznaczoną do nowych planów.
9. Nie pobieraj EAGER wszystkich contributions/equipment dla listy katalogowej.

API

- rozdziel lekką listę katalogu od pełnego szczegółu wersji;
- dodaj endpointy redakcyjne dla profilu, dowodów i review;
- nie wystawiaj encji;
- zaktualizuj OpenAPI i test kontraktu.

TESTY OBOWIĄZKOWE

- walidacja przedziałów;
- konflikt parent/child allocation;
- dozwolone descriptive-only;
- warunki wariantu i strony;
- publikacja bez review odrzucona;
- niezmienność publikacji;
- jednoczesne tworzenie kolejnej wersji bez surowego 500;
- paginacja i brak duplikowania bieżących ćwiczeń;
- test liczby zapytań lub inny wiarygodny test przeciw N+1 dla listy;
- legacy tags nie wpływają na nowe bezpieczeństwo;
- pełne `mvn verify`.

DONE WHEN

- katalog mówi, co ćwiczenie obciąża, a nie komu jest zakazane;
- każde mapowanie ma provenance i niepewność;
- publiczny snapshot wersji wystarcza przyszłemu kalkulatorowi;
- legacy pozostaje czytelne bez wpływu na nowe plany.
```

---

# PROMPT 4 — Training Planning V2: cele, rewizje i recepta

```text
CEL

Przebuduj `trainingplanning` z jednorazowego kreatora aktywnego planu na agregat draft + niezmienne rewizje. Nie implementuj jeszcze finalnej oceny safety; przygotuj port i statusy procesu.

KRYTERIUM WEJŚCIA

Exercise Catalog V2 udostępnia publiczny snapshot opublikowanej wersji z profilem ekspozycji. Fundament współbieżności z Promptu 1 jest zielony.

MODEL DOCELOWY

1. `TrainingGoal`:
   - perspective: `PERFORMANCE`, `FUNCTIONAL_RECOVERY`, `GENERAL_FITNESS`;
   - category;
   - title/description/priority/status/targetDate;
   - wiele `GoalOutcome`: metricCode, baseline, target, unit, measurementMethod, evidenceSource.
2. `TrainingPlan` jako stabilna tożsamość:
   - participant, name, purpose, owner, mode: specialist/self-directed/collaborative;
   - status i currentRevisionId.
3. `PlanRevision`:
   - numer, basedOn, status, phaseIntent, validity dates, author capability, optimistic version;
   - po aktywacji niezmienna;
   - każda modyfikacja aktywnego planu tworzy nową rewizję.
4. `TrainingCycle` i `Microcycle` z datami, kolejnością, intencją i celem fazy.
5. `PlannedSession` wyłącznie dla zadania do wykonania, z datą/oknem dostępności, expected duration i statusem. Usuń `OFFLINE_APPOINTMENT` z nowego modelu/API; zachowaj adapter legacy read.
6. `ExercisePrescription` z:
   - wersją ćwiczenia;
   - pozycją i stroną;
   - `doseType`: dynamic resistance, isometric, impact, endurance, mobility/control;
   - typed dose: sets, reps, duration, distance, contacts, external load, unit, intensity target, RPE/RIR/%1RM/zone, tempo, ROM, rest;
   - opcjonalną grupą zamienników.
7. `PlanLoadBudget` jako intencja sportowa z działaniem wyłącznie `INFO/WARNING`.

PROJEKT PERSYSTENCJI

- Rozwijaj adaptery Spring Data JPA/Hibernate utworzone w fundamencie. Usuń pozostałe bezpośrednie zapisy i odczyty `JdbcTemplate` w `trainingplanning`; kod aplikacyjny nie może składać SQL.
- Zachowaj relacyjnie pola używane przez reguły i zapytania.
- Jeżeli użyjesz JSONB dla modalności dawki, najpierw wykaż, że schemat jest wersjonowany, bezpiecznie walidowany i nie utrudni zapytań safety. Preferowana jest mała relacyjna reprezentacja typed dose.
- Użyj `@Version` dla draftu/rewizji.
- Nie mapuj całego drzewa jako jedna ogromna relacja EAGER.
- Zadbaj o unikalną kolejność i spójność dat ograniczeniami bazy oraz domeny.

WORKFLOW

- `DRAFT` można edytować;
- structural validation zapisuje wynik i checksum dla konkretnej wersji draftu, ale w tym etapie nie przełącza rewizji na `READY` ani `ACTIVE`;
- nie twórz tymczasowego safety adaptera, fikcyjnego assessment ani automatycznego `PASS`;
- statusy i przejścia `VALIDATING`, `READY`, `ACTIVE`, `SUPERSEDED`, `COMPLETED` mogą zostać zamodelowane, lecz use case aktywacji pozostaje niedostępny do czasu Promptu 8;
- nie zachowuj starego zachowania „create = active”.

API

Zaimplementuj małe komendy: create draft, goal, cycle, microcycle, session, prescription, reorder, create revision, validate structurally, get editor view, get revision history. Nie twórz jednego DTO zapisującego całe drzewo bez kontroli konfliktów.

MIGRACJA LEGACY

- forward-only;
- istniejące plany zaimportuj lub odczytuj jako revision 1 z `migrationOrigin=LEGACY_V1` i `assessmentStatus=NOT_ASSESSED`;
- legacy plan jest czytelny, ale nowa aktywacja wymaga nowego workflow;
- dodaj test migracji istniejącego zestawu danych.

TESTY OBOWIĄZKOWE

- edycja draftu i optimistic locking;
- aktywna rewizja niezmienna;
- tworzenie rewizji z poprzedniej;
- walidacja dat i kolejności;
- właściwy typed dose dla każdej modalności MVP;
- brak appointment w nowych sesjach;
- PlanLoadBudget nie ma poziomu hard block;
- self-directed versus specialist-assigned;
- legacy read/migration;
- autoryzacja zasobowa;
- pełne `mvn verify`.

DONE WHEN

- plan można budować iteracyjnie;
- żadna zmiana nie aktywuje go bez osobnego przypadku użycia;
- wykonanie może później wskazać dokładną niezmienną rewizję i receptę;
- stary kontrakt ma jawny adapter/deprecation, nie ukrytą podwójną logikę.
```

---

# PROMPT 5 — Load Analysis: planowany profil obciążenia

```text
CEL

Zaimplementuj deterministyczny moduł `loadanalysis` obliczający wielowymiarowy planowany profil obciążenia rewizji planu. Nie oceniaj jeszcze bezpieczeństwa i nie twórz jednej ogólnej liczby.

KRYTERIUM WEJŚCIA

Training Planning V2 dostarcza niezmienny `PlanRevisionSnapshot`, a Exercise Catalog V2 dostarcza profile contribution. Jeżeli któryś snapshot wymaga encji JPA innego modułu, najpierw napraw granicę.

KANAŁY MVP

- `DYN_EXU`;
- `ISO_SEC`;
- `IMPACT_CONTACTS`;
- `ENDURANCE_MIN_ZONE` tylko jeśli obecny typed dose pozwala na poprawny input;
- `SESSION_S_RPE` jako kanał odpowiedzi nie jest planowaną wartością faktyczną; może istnieć jedynie jako target/expected context, nigdy jako rzeczywisty pomiar.

MODEL

- `LoadCalculationVersion` — wersja algorytmu i konfiguracji współczynników;
- `PlannedLoadSnapshot` — identyfikator rewizji, checksum wejścia, wersja katalogu/algorytmu, czas kalkulacji;
- `PlannedLoadObservation` — prescription, structure, side, channel, observation family, unit, low/high, confidence, provenance;
- `LoadAggregateProjection` — session/day/microcycle/cycle/window, odbudowywalna projekcja;
- wynik domenowy `LoadProfile`, bez encji w publicznym porcie.

REGUŁY KALKULACJI

1. Rozłóż prescription na atom dawki odpowiedni dla modalności.
2. Oblicz `BaseDose` wyłącznie z danych właściwych kanałowi.
3. Pomnóż przedział BaseDose przez contribution interval.
4. Sumuj tylko identyczne structure + side + channel + observationFamily + unit + calculationVersion.
5. Nie sumuj RPE, bólu ani difficulty do EXU.
6. Nie dodawaj `overallLoadScore`, `riskScore` ani ukrytego ważenia kanałów.
7. Parent roll-up jest projekcją. Nie dodawaj bezpośredniej alokacji parent do zwiniętych children. Uwzględnij `calculationRole`.
8. Każda obserwacja przechowuje provenance: exercise version, contribution id, dose source, observation mode, evidence/confidence.
9. `BigDecimal` ma jawny MathContext/scale i politykę zaokrągleń. Testy muszą dowodzić stabilności.
10. Kalkulacja jest czystą funkcją domenową; persystencja i orkiestracja są adapterami.

WYDAJNOŚĆ

- Pobierz snapshot planu i katalogu w bounded batch, nie w pętli N+1.
- Projekcje są rebuildable i idempotentne.
- Ponowne żądanie dla tego samego checksumu i wersji zwraca istniejący snapshot albo identyczny wynik.
- Nie przeliczaj całej historii uczestnika podczas zwykłego requestu planu.

API/PORT

Dodaj `PlannedLoadCalculationPort.calculate(revisionSnapshot, version)` i zapytanie o preview. Preview nie może ujawniać clinical restrictions ani tworzyć assessment.

TESTY OBOWIĄZKOWE

- dynamic, isometric i impact;
- lewa/prawa/bilateral;
- low/high interval;
- parent roll-up bez double count;
- descriptive-only pominięte w arytmetyce;
- zakaz sumowania różnych kanałów/jednostek;
- deterministyczne zaokrąglenie;
- idempotentny rebuild;
- wersja algorytmu zmienia tożsamość snapshotu;
- brak N+1 dla planu z wieloma ćwiczeniami;
- PostgreSQL/Testcontainers i pełne `mvn verify`.

DONE WHEN

- dla dowolnej kompletnej rewizji można uzyskać wyjaśnialny profil sesji, mikrocyklu i cyklu;
- wynik można odtworzyć ze snapshotów i wersji;
- żaden element modułu nie twierdzi, że EXU jest siłą biologiczną ani przewiduje uraz.
```

---

# PROMPT 6 — profile, capabilities i zgody zakresowe

```text
CEL

Zbuduj wspólną warstwę autoryzacji domenowej potrzebną Training Planning i Safety. Rozdziel techniczne role tokenu od profili domenowych, capabilities, relacji oraz zgód na konkretny cel i zakres danych.

KRYTERIUM WEJŚCIA

Prompty 1–5 są ukończone. Nie rozpoczynaj clinical restrictions, zanim ten etap nie zapewni wiarygodnej odpowiedzi „kto, wobec kogo, w jakiej roli i w jakim celu może wykonać operację”.

PROBLEM AS-IS

`principal_account.profile_type` wymusza pojedynczy typ PARTICIPANT albo SPECIALIST. Docelowo ta sama osoba może mieć profil uczestnika oraz specjalisty, a specjalista może działać jako trener, fizjoterapeuta albo w obu zweryfikowanych zakresach. Rola Keycloak sama nie wystarcza do uprawnienia zasobowego.

MODEL

1. Zachowaj konto/tożsamość jako stabilny byt techniczny.
2. Modeluj profile domenowe jako odrębne, opcjonalne role powiązane z kontem. Migracja istniejącego `profile_type` zachowuje dane, ale nowe przypadki użycia nie mogą zakładać wyłączności.
3. Wprowadź centralny resolver capabilities w module właściciela relacji/specjalistów. Minimalny zestaw na tym etapie:
   - `PLAN_PERFORMANCE`;
   - `PLAN_FUNCTIONAL_RECOVERY`;
   - `SET_PERFORMANCE_BUDGET`;
   - `SET_CLINICAL_RESTRICTION`;
   - `VIEW_EFFECTIVE_RESTRICTION`;
   - `VIEW_CLINICAL_RATIONALE`;
   - `ACKNOWLEDGE_PERFORMANCE_WARNING`;
   - `OVERRIDE_CLINICAL_BLOCK`.
4. Capability wynika z aktywnego/zweryfikowanego profilu zawodowego, rodzaju specjalisty, relacji z uczestnikiem, zgody i celu operacji. Nie zapisuj capabilities jako bezkrytycznej listy w tokenie.
5. Użytkownik posiadający wiele profili wybiera jawny `actingContext`; audit zapisuje rolę/capability wykorzystaną w operacji.

ZGODY

Rozszerz obecne legal acknowledgements o właściwy model `ConsentTemplateVersion` i `ConsentGrant`:

- grantor participant;
- recipient: konkretny specjalista/relacja albo organizacja;
- data scope, np. plan, wykonanie, efektywne ograniczenia, clinical rationale;
- purpose, np. performance planning, functional recovery, clinical review;
- wersja treści i podstawa;
- grantedAt, validFrom, validTo, revokedAt;
- status oraz audit;
- natychmiastowy efekt cofnięcia dla nowych odczytów i komend.

Nie mieszaj akceptacji regulaminu ze zgodą na udostępnienie danych szczególnych. Nie kopiuj treści medycznej do Consent.

PUBLICZNE PORTY

- `SpecialistAuthorizationPort.requireCapabilities(actor, participant, actingContext, requiredCapabilities, purpose)`;
- `ConsentDecisionPort.requireAccess(actor, participant, dataScopes, purpose)`;
- wyniki są neutralnymi snapshotami bez encji JPA.

MIGRACJA I KOMPATYBILNOŚĆ

- forward-only;
- istniejące konto zachowuje odpowiedni profil wynikający z legacy `profile_type`;
- stare endpointy onboardingowe nadal działają przez adapter, ale nie mogą usuwać drugiego profilu;
- brak zgody klinicznej nie może być automatycznie uznany na podstawie samej akceptacji privacy policy;
- funkcje niewymagające danych szczególnych nie powinny zostać przypadkowo zablokowane.

TESTY OBOWIĄZKOWE

- konto participant-only, trainer-only, physiotherapist-only oraz wieloprofilowe;
- jawny acting context;
- trener ma performance capabilities, ale nie clinical override;
- fizjoterapeuta otrzymuje clinical capability tylko w aktywnej relacji;
- grant według recipient/scope/purpose;
- brak, wygaśnięcie i cofnięcie zgody;
- zakończenie relacji;
- legal acknowledgement nie zastępuje clinical consent;
- audit capability i celu bez treści medycznej;
- autoryzacja negatywna w API i use case;
- pełne `mvn verify`.

DONE WHEN

- żaden nowy przypadek użycia nie opiera uprawnienia wyłącznie na `hasRole`;
- wiele profili jednego konta jest wspierane bez utraty danych legacy;
- Safety może bezpiecznie zapytać o capability i zgodę, nie znając tabel specjalisty ani consent.
```

---

# PROMPT 7 — Safety V2: ograniczenia, reguły i assessment

```text
CEL

Zastąp safety oparte na dowolnych tagach wersjonowanym modelem ograniczeń skierowanych na struktury, wzorce i charakterystyki obciążenia. Zaimplementuj ocenę planowanego profilu przed aktywacją.

KRYTERIUM WEJŚCIA

`loadanalysis` generuje zapisany `PlannedLoadSnapshot`; `anatomyreference` oraz publiczne porty capabilities/consent istnieją. Nie implementuj safety przez zapytania do ich tabel.

MODEL OGRANICZENIA

`Restriction`:
- participant;
- sourceType: `PARTICIPANT_DECLARED`, `PHYSIOTHERAPIST`, `SYSTEM_OPERATIONAL`;
- semanticType: `CONTRAINDICATION`, `CAUTION`, `LIMIT`, `MONITOR`;
- status i wersja;
- validFrom/validTo;
- authorAccountId i authorCapability;
- participant-visible explanation;
- opcjonalny `clinicalRationaleRef`, bez kopiowania notatki klinicznej.

`RestrictionTarget`:
- structure, movement pattern, channel, load characteristic;
- side, ROM, contraction type;
- limit low/high + unit;
- minimum recovery hours.

ZASADY UPRAWNIEŃ

- uczestnik może tworzyć, edytować i wycofać tylko `PARTICIPANT_DECLARED`;
- nie może zastępować pełnej listy ani usuwać ograniczeń fizjoterapeuty;
- trener nie może tworzyć `PHYSIOTHERAPIST` ani `CONTRAINDICATION`;
- fizjoterapeuta potrzebuje aktywnej relacji, capability i zgody;
- historia jest wersjonowana/audytowana;
- wygasłe ograniczenie nie wpływa na nowy assessment, ale pozostaje w historii.

REGUŁY

Zaimplementuj `SafetyRuleVersion` i jawne strategie Java. Nie twórz uniwersalnego DSL/skryptów.

Minimalne reguły:
1. przecięcie planowanej ekspozycji z aktywnym twardym ograniczeniem;
2. przekroczenie jawnego limitu struktura/kanał/jednostka;
3. niewystarczające okno regeneracji, tylko gdy limit został określony;
4. konflikt z deklaracją uczestnika jako `WARNING`, nie automatyczny clinical hard block;
5. brak/niska pewność istotnego mapowania jako `WARNING/INFO`.

`PlanLoadBudget` trenera jest wejściem do osobnej oceny planistycznej i nie może tworzyć clinical `HARD_BLOCK`.

ASSESSMENT

- zapisuj snapshot użytych ograniczeń, ruleset, load snapshot i calculation version;
- wynik: `PASS`, `INFO`, `WARNING`, `HARD_BLOCK`;
- każdy factor ma kod reguły, target, kanał, observed range, threshold, explanation code i evidence grade;
- istniejący assessment jest niezmienny;
- ponowna walidacja tworzy nowy assessment;
- `HARD_BLOCK` jest wąski i wyjaśnialny.

OVERRIDE

- tylko uprawniony fizjoterapeuta lub rola operacyjna dla odpowiedniego typu;
- dotyczy konkretnego factor/assessment;
- ma powód, zakres i ważność;
- nie wyłącza globalnie reguły;
- nie każdy block musi być overridable.

LEGACY

- obecne tagi uczestnika i ćwiczenia pozostają odczytywalne jako legacy;
- nie konwertuj automatycznie tagów na kliniczne restrictions;
- przygotuj raport/endpoint administracyjny unmapped legacy;
- usuń możliwość `replace-all`, zachowując kontrolowane API deklaracji uczestnika.

TESTY OBOWIĄZKOWE

- macierz sourceType × semanticType × result;
- struktura, rodzic/potomek, strona i kanał;
- limit i jednostka;
- wygasłe oraz superseded restriction;
- uczestnik nie usuwa restriction fizjoterapeuty;
- trener nie tworzy/override clinical block;
- physio bez relacji/zgody odrzucony;
- snapshot assessment pozostaje niezmienny;
- override prawidłowy, niedozwolony i wygasły;
- dane kliniczne nie trafiają do logów/DTO neutralnego;
- pełne `mvn verify`.

DONE WHEN

- żadna nowa decyzja safety nie zależy od exercise contraindication tag;
- każde ostrzeżenie i blokada ma jawne wejście, wersję i explanation;
- model nie diagnozuje ani nie obiecuje zapobiegania urazom.
```

---

# PROMPT 8 — integracja: walidacja i aktywacja rewizji planu

```text
CEL

Połącz Training Planning V2, Load Analysis i Safety V2 w atomowy, audytowalny workflow walidacji oraz aktywacji rewizji. Domknij przygotowane wcześniej statusy i porty, nie wprowadzając alternatywnej ścieżki aktywacji.

KRYTERIUM WEJŚCIA

Prompty 1–7 są ukończone. Istnieją publiczne porty i zielone testy. Jeżeli planowanie musi czytać tabele load/safety, zatrzymaj się i napraw porty zamiast omijać granice.

PRZYPADKI UŻYCIA

1. `ValidatePlanRevision`:
   - autoryzacja i zgody;
   - optimistic lock draftu;
   - structural validation;
   - pobranie immutable exercise snapshots;
   - obliczenie `PlannedLoadSnapshot`;
   - `SafetyAssessment`;
   - zapis referencji i zmiana statusu na `READY`, `NEEDS_REVIEW` albo `BLOCKED`.
2. `AcknowledgePlanWarning`:
   - konkretne factors;
   - capability odpowiednie do rodzaju warning;
   - uzasadnienie;
   - append-only acknowledgement.
3. `OverrideClinicalBlock`:
   - deleguj do Safety;
   - nie implementuj w planowaniu reguł medycznych.
4. `ActivatePlanRevision`:
   - idempotency key;
   - sprawdzenie niezmienionego checksumu rewizji;
   - aktualność relacji i zgód;
   - aktualność exercise versions dla nowych przypisań;
   - kompletność assessment/acknowledgements/override;
   - oznaczenie poprzedniej rewizji jako superseded;
   - ustawienie current revision;
   - utworzenie/przypisanie sesji;
   - audit i outbox w tej samej transakcji.

WSPÓŁBIEŻNOŚĆ

- dwie aktywacje tej samej rewizji;
- jednoczesna edycja draftu i walidacja;
- zmiana restriction po assessment, lecz przed activation;
- wycofanie exercise version;
- retry po odpowiedzi niepewnej klienta.

Zdefiniuj jawnie politykę freshness. Rekomendacja: assessment odnosi się do restriction snapshotu; przed aktywacją system sprawdza, czy nie istnieje nowsze efektywne ograniczenie. Jeżeli istnieje, wymaga ponownej walidacji.

ZDARZENIA

Dodaj transactional outbox co najmniej dla:
- `PlanRevisionValidated`;
- `PlanRevisionBlocked`;
- `PlanRevisionActivated`;
- `PlanRevisionSuperseded`;
- `ExerciseSetAssigned`.

Event payload nie może zawierać danych klinicznych, notatek ani pełnych restriction factors. Konsument ma być idempotentny. Nie dodawaj brokera; wystarczy outbox oraz kontrolowany lokalny publisher/dispatcher.

API

- komendy przyjmują `Idempotency-Key` tam, gdzie retry może powtórzyć efekt;
- konflikty wersji: kontrolowane 409;
- blokada safety: ProblemDetail z bezpiecznymi explanation codes, bez diagnozy;
- status i assessment są dostępne właścicielowi/uprawnionemu specjaliście.

TESTY OBOWIĄZKOWE

- pełne ścieżki PASS, WARNING + acknowledgement, HARD_BLOCK, override;
- zmiana restriction wymusza revalidation;
- równoległa aktywacja;
- retry idempotency;
- supersede poprzedniej rewizji;
- event i stan domenowy zapisane atomowo;
- brak eventu przy rollbacku;
- payload bez danych medycznych;
- authorization trainer/physio/participant;
- pełne `mvn verify`.

DONE WHEN

- żadna rewizja nie staje się ACTIVE bez odpowiadającego jej assessment;
- aktywacja jest odporna na retry i race conditions;
- zdarzenia są niezawodne i neutralne względem danych klinicznych.
```

---

# PROMPT 9 — Training Execution V2, wykonane obciążenie i alerty

```text
CEL

Rozszerz Training Execution tak, aby zapisywał faktyczną dawkę, uruchamiał obliczenie wykonanego profilu obciążenia i tworzył lifecycle alertów. Zachowaj append-only historię i separację gamifikacji.

KRYTERIUM WEJŚCIA

Aktywna sesja wskazuje konkretną `PlanRevision` i `ExercisePrescription`. Outbox działa. Planned Load oraz Safety V2 są gotowe.

MODEL WYKONANIA

- `SessionExecution` najwyżej jedno skuteczne wykonanie sesji;
- `ExecutionItem` wskazuje dokładną prescription i exercise version;
- actual dose właściwa modalności: sets/reps/duration/contacts/load/intensity/side/modified/skipped;
- `SessionResponse`: session RPE, pain/difficulty bez traktowania ich jako EXU;
- opcjonalny `Post24hResponse` jako osobne późniejsze zdarzenie;
- `ExecutionCorrection` rozszerz tak, aby korekta mogła dotyczyć rzeczywistych wyników, nadal append-only;
- wszystkie obserwacje mają `observationMode`: `DECLARED`, `DEVICE`, `ESTIMATED`.

PRZEPŁYW

1. atomowo zapisz deklarację wykonania i outbox `SessionExecutionDeclared`;
2. idempotentny konsument buduje `ExecutedLoadObservation` tą samą rodziną kalkulatorów, ale z actual dose;
3. utwórz agregaty sesji i okien 7/14/28 dni;
4. Safety ocenia wykonanie i odpowiedź uczestnika;
5. pain/difficulty/post24h mogą tworzyć `ALERT`, ale nie są dodawane do load;
6. dopiero neutralny wynik `ExecutionQualifiedForGamification` może trafić do gamifikacji;
7. korekta powoduje wpis korygujący/reversal i rebuild projekcji, nie edycję historii.

ALERT LIFECYCLE

`SafetyAlert` musi mieć:
- type, priority, owner, status;
- createdAt, dueAt/SLA;
- assignment history;
- comments lub references bez umieszczania pełnej notatki medycznej w logu;
- acknowledge/resolve/reopen;
- powiązanie z execution/assessment;
- audyt.

GAMIFIKACJA

- gamifikacja nie odczytuje pain, restriction ani clinical alert;
- jedno source execution daje najwyżej jedną kwalifikację danego typu;
- korekta mogąca unieważnić kwalifikację tworzy reversal, nie usuwa ledger entry;
- nagłówek idempotency nie może być tylko walidowany i ignorowany.

TESTY OBOWIĄZKOWE

- actual versus planned dose;
- różne modalności i strony;
- jedno wykonanie przy concurrency;
- retry event consumer;
- korekta i rebuild/reversal;
- pain alert oraz post24h alert;
- alert lifecycle i autoryzacja;
- overdue/failed consumer recovery;
- gamification payload nie zawiera danych medycznych;
- jeden award/qualification;
- pełne `mvn verify`.

DONE WHEN

- można porównać planowaną i wykonaną ekspozycję;
- korekty zachowują historię i aktualizują projekcje;
- alert jest obsługiwanym zadaniem, a nie pojedynczym wierszem bez statusu;
- gamifikacja pozostaje odseparowana.
```

---

# PROMPT 10 — współpraca trener–fizjoterapeuta i audyt końcowy

```text
CEL

Domknij model dwóch perspektyw zawodowych, minimalizację danych oraz końcową jakość całego vertical slice. Nie twórz nowych funkcji treningowych poza potrzebnymi do poprawnego uprawnienia istniejących przypadków użycia.

KRYTERIUM WEJŚCIA

Prompty 1–9 są zakończone i zielone. Zrób najpierw przegląd diffów/commitów całej serii oraz ponownie sprawdź bieżący stan repo.

CAPABILITIES — AUDYT I DOMKNIĘCIE

Zweryfikuj i domknij domenowe capabilities wprowadzone w Prompcie 6:

- `PLAN_PERFORMANCE`;
- `PLAN_FUNCTIONAL_RECOVERY`;
- `SET_PERFORMANCE_BUDGET`;
- `SET_CLINICAL_RESTRICTION`;
- `VIEW_EFFECTIVE_RESTRICTION`;
- `VIEW_CLINICAL_RATIONALE`;
- `ACKNOWLEDGE_PERFORMANCE_WARNING`;
- `OVERRIDE_CLINICAL_BLOCK`;
- osobne redakcyjne `PUBLISH_EXERCISE_CONTENT` i `PUBLISH_SAFETY_RULE`.

Sprawdzenie przypadku użycia obejmuje role techniczne, aktywny profil zawodowy, aktywną relację, consent scope, ownership i purpose. Osoba posiadająca oba profile działa w jawnie wybranym kontekście capability; nie zakładaj, że konto ma tylko jedną rolę produktową.

CLINICAL SAFETY ENVELOPE

Trener może zobaczyć wyłącznie wykonalne ograniczenie potrzebne do planowania, np. target, side, kanał, limit, okres i bezpieczne explanation code. Nie otrzymuje diagnozy, wywiadu, clinical rationale ani notatek bez osobnej podstawy i zgody.

Fizjoterapeuta może zarządzać clinical restriction i funkcjonalnym celem w ramach relacji oraz zgody. Cofnięcie zgody natychmiast blokuje nowy dostęp i nowe operacje, pozostawiając wymagany audyt/retencję.

WSPÓŁPRACA

- plan ma jednego ownera i opcjonalnych collaborators;
- zakres współpracy jest jawny;
- trener może wysłać zablokowaną rewizję do review;
- fizjoterapeuta może zmienić własne restriction, zaproponować zmianę lub wykonać dozwolony override;
- odpowiedź nie ujawnia nadmiarowych danych;
- każda decyzja ma audit trail.

AUDYT TECHNICZNY

1. Usuń tymczasowe adaptery i TODO, które po zakończeniu serii nie są już potrzebne.
2. Sprawdź, czy nie powstały:
   - cykle modułów;
   - cross-schema SQL;
   - encje w API;
   - EAGER/N+1;
   - publiczne settery;
   - duże serwisy łamiące SRP;
   - duplikacja reguł trainer/physio;
   - globalny load/risk score;
   - wycieki danych medycznych;
   - brakujące indeksy/constraints;
   - niewersjonowane kalkulatory/reguły.
3. Zweryfikuj wszystkie migracje od czystej bazy i upgrade z V005 z realistycznym legacy fixture.
4. Wygeneruj/zweryfikuj OpenAPI i klienta bez ręcznego edytowania wygenerowanych plików.
5. Dodaj backend do kompletnego CI, jeśli aktualny pipeline pomija istotne testy. Nie naprawiaj w tym zadaniu całego Angulara.
6. Dodaj/uzupełnij ADR-y wynikające z zatwierdzonej architektury, nie zmieniając `spec.md`.

TESTY AKCEPTACYJNE END-TO-END BACKEND

Scenariusz trenera:
- performance goal → draft → prescriptions → planned load → warning → acknowledgement → activation → execution → adherence.

Scenariusz fizjoterapeuty:
- functional goal → clinical restriction → rehab plan → assessment → activation → pain/post24h → alert → revision.

Scenariusz współpracy:
- trener tworzy plan kolidujący z envelope → block → physio review → zmiana/override → revalidation → activation, bez ujawnienia diagnozy.

Scenariusz self-directed:
- uczestnik tworzy plan → participant warning → aktywne clinical restriction wymusza block → uczestnik nie może go obejść.

Scenariusze bezpieczeństwa:
- cofnięta zgoda;
- zakończona relacja;
- nieaktualny assessment;
- concurrency/retry;
- correction/reversal;
- odtworzenie projections.

DONE WHEN

- pełne `mvn verify` przechodzi na Java 25;
- migracje przechodzą clean install i upgrade;
- testy architektury są zielone;
- OpenAPI jest spójne;
- wszystkie cztery ścieżki użytkownika przechodzą;
- raport końcowy rozdziela zakres ukończony, świadomie odłożony i ewentualne blokery;
- nie ma commita ani push bez osobnego polecenia.
```

---

## Oficjalne baseline’y techniczne użyte przy przygotowaniu promptów

- [Spring Boot — System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot — Reference Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Framework — Hibernate integration](https://docs.spring.io/spring-framework/reference/data-access/orm/hibernate.html)
- [Hibernate ORM — official documentation](https://hibernate.org/orm/documentation/)
- [OpenAI Codex — prompting](https://learn.chatgpt.com/docs/prompting)
- [OpenAI Codex — best practices](https://learn.chatgpt.com/guides/best-practices)

## Polityka wersji Spring Boot

Repozytorium `moves` na wskazanym stanie używa Spring Boot 4.1.0, ale nie jest to zamrożony baseline. Każde rozpoczęcie sekwencji wymaga potwierdzenia najnowszego stabilnego wydania GA w oficjalnych źródłach. Projekt ma używać tej wersji oraz wersji Spring Framework, Spring Data, Jakarta Persistence i Hibernate ORM zarządzanych przez jej BOM. Ewentualna aktualizacja musi być wydzielona od zmian domenowych i przejść pełną regresję.
