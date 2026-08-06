# Stan bieżący

## EXERCISE-CATALOG-CRUD-01 — redakcja katalogu

Redakcja katalogu jest dostępna wyłącznie dla `CONTENT_ADMIN` pod
`/admin/exercise-catalog`; nie zmienia publicznego readera `/catalog`, importu ani
planowania. Endpoint `GET /api/v1/admin/exercises` zwraca stronicowaną, bieżącą wersję
każdego ćwiczenia wraz z akcjami dozwolonymi przez backend i tokenem optimistic-lock.
Ekran szczegółów zaczyna w trybie odczytu, pozwala edytować tylko draft oraz tworzyć
następną wersję z opublikowanej/wycofanej. Obszary load, anatomii i dowodów są
progresywnie ujawniane; historia wersji jest odczytowa.

TODO: bulk edit, import/export, duplicate exercises, media, pełny review jeśli nie jest
udostępniony, bezpieczne usuwanie porzuconych draftów oraz idempotency/retry dla create.

## Participant Goals — completed scope and next steps

**GOALS-01 through GOALS-05 are complete.** The canonical, participant-owned outcome-goal
aggregate is separate from plan-revision goals and is available in the specialist workspace.
It includes active/achieved/cancelled lifecycle, immutable outcome snapshots, explicit specialist
achievement/cancellation, append-only observations and progress, append-only mutation events,
specialist timeline entries and event deep links. The specialist workspace supports creating,
viewing and updating eligible goals, lifecycle actions and lazy observation-history reading. It
also provides a read-only, code-versioned metric-preset catalog and a dedicated create-from-preset
flow; each resulting goal keeps an immutable outcome snapshot rather than a live catalog reference.
Details and boundaries are in [participant goals](../architecture/participant-goals.md) and
[ADR-018](../adr/ADR-018-participant-goal-ownership.md).

Deliberate current limits: goals are not integrated with training-plan revisions; achievement is
not automatic; there is no participant self-service UI, measurement correction/edit/delete,
unit conversion, charts, alerts, device import, analytics or ML.

### Participant Goals — next-steps roadmap

- **Training-plan integration:** link goals to a revision, snapshot those links in that revision,
  and copy associations when creating a new revision. Validate participant, acting perspective
  and authorization consistently; define the transition strategy for legacy
  `training_planning.training_goal`. Do not automatically back-propagate associations to
  historic revisions.
- **Observation evolution:** add an append-only correcting event, defined unit conversions,
  session and research-result integrations, and derived charts/aggregates.
- **Participant access:** enable `GENERAL_FITNESS` self-service with own-goal reading and only
  explicitly allowed observations; keep participant authorization separate from specialist
  authorization.
- **Lifecycle and data compliance:** define access after relation termination, consent withdrawal
  versus legal retention, retention/access-limitation/anonymization/audit rules, and handling of
  unfinished operations after cooperation ends.
- **Outside the MVP:** notifications, reminders, achievement suggestions, device import,
  analytics and ML.
_Zaktualizowano: 2026-08-05._

## SET-01 / SET-05 — zestawy ćwiczeń i wybór katalogowy

SET-01 jest ukończonym etapem analityczno-projektowym. Kanoniczny model opisuje
[model zestawów ćwiczeń](../architecture/exercise-set-model.md), a decyzję granic
utrwala [ADR-013](../adr/ADR-013-independent-versioned-exercise-sets.md).

**SET-02 jest częściowo ukończony formalnie, ale backendowy pion jest gotowy do SET-03.** Implementacja JPA/Hibernate w
`com.motionecosystem.exercisesets` oraz migracja `V038__create_exercise_sets.sql` dają
niezależny pion backendowy: owner-scoped set, pierwszy draft, wersjonowane pozycje,
typed dose, publikację, następny draft, materializowany draft wariantu i wycofanie.
Tabele `exercise_set.exercise_set`, `exercise_set_version`, `exercise_set_item` oraz
`exercise_set_item_dose` nie przechowują uczestnika, daty, sesji ani wykonania. API
specjalisty jest dostępne pod `/api/v1/specialist/exercise-sets`; published version jest
niemutowalna, a draft chroni wyłącznie `ExerciseSetVersionEntity.@Version`.
Każda udana mutacja wymusza inkrementację tego tokenu i zwraca go po flushu, gotowy do bezpośredniego użycia w następnej
komendzie. Pozycje referencjonują opublikowaną
`ExerciseVersion` przez publiczny port katalogu i zapisują minimalny snapshot odczytowy.

Snapshot OpenAPI, generowany klient TypeScript oraz `ApiFacade.exerciseSets` obejmują
nowe API. `Dose` jest generowane jako `oneOf` z dyskryminatorem `type`; `api:verify`,
testy MockMvc/PostgreSQL/Testcontainers i frontendowe testy oraz build potwierdziły
zgodność pionu. Globalny `ModuleBoundaryTest` oraz pełne `mvn verify` kończą się
niezerowo z powodu zastanego cyklu modułów i legacy fixtures po V036, niezwiązanych z
`exercisesets`; nie jest to regresja ani zależność nowego modułu. Z tego powodu formalne
kryterium pełnej walidacji SET-02 pozostaje otwarte, choć w zakresie kodu SET-02 nie ma
znanego defektu. Następny etap może rozpocząć się jako SET-03: wyszukiwanie i wybór
konkretnej wersji ćwiczenia.

**SET-03 dostarcza pion wyszukiwanie → filtrowanie → facety → podgląd → wybór
`ExerciseVersion`.** `V039__add_exercise_catalog_search_indexes.sql` wprowadza
PostgreSQL `pg_trgm`, folding polskich znaków i indeksy katalogowe. Endpointy
`POST /api/v2/exercises/search` i `GET /api/v2/exercises/versions/{id}/preview` zwracają
lekki projection tylko aktualnych opublikowanych, wybieralnych wersji. Wyszukiwanie ma
locale `pl-PL`, synonimy z istniejących aliasów, filtry wzorca, poziomu, sprzętu,
pozycji, stronności, anatomii i zastosowania, facety, stabilny ranking oraz seek cursor.
Szczegóły opisuje [dokument wyszukiwania katalogu](../architecture/exercise-catalog-search.md)
i [ADR-014](../adr/ADR-014-postgresql-catalog-search.md).

Ekran `/catalog` używa reużywalnego `ExercisePickerComponent`: debounce, filtry,
facety, paginacja, quick preview i klawiaturowo dostępny wybór. Emisja zawiera dokładne
`exerciseVersionId` oraz minimalną prezentację, ale picker nie zapisuje zestawu — to
pozostaje odpowiedzialnością SET-04. OpenAPI snapshot, generowany klient oraz
`ApiFacade.catalogSearch` są transportem dla tego pionu; wygenerowane pliki nie są
edytowane ręcznie. Testy jednostkowe sprawdzają folding tekstu, a Testcontainers/MockMvc
pokrywa aliasy, filtry, facety, cursor i preview.

Obecny formularz „Nowy plan” (`/plan`) nadal jest legacy: wiąże uczestnika, pojedyncze
ćwiczenie, datę/sesję, płaską dawkę i wariant sesji. Nie został zmigrowany ani połączony
automatycznie z nowym agregatem; `ExercisePrescription`, `SessionVariant` i historyczne
wykonania pozostają bez zmian. Audyt potwierdził niepełną jakość/ekspozycję danych
anatomicznych oraz ograniczoną powierzchnię wyszukiwania katalogu; facety, analiza,
assignment i integracja z planowaniem są zakresem kolejnych etapów.

**SET-04 dostarcza specjalistyczny builder pod `/exercise-sets`.** Lista prowadzi do
odczytu opublikowanej wersji albo edycji szkicu; `/exercise-sets/new` zakłada pierwszy
draft. Edytor używa reużywalnego `ExercisePickerComponent` bez zmiany jego kontraktu,
zapisuje metadane z debounce i przekazuje `lockVersion` do komend metadanych, pozycji,
przeniesienia oraz usunięcia. Dawkowanie jest prezentowane i budowane przez zawężanie
generowanego `Dose.type`; nie uzupełnia brakujących wartości. Kolejność ma przyciski
„W górę/W dół”, więc nie zależy wyłącznie od drag-and-drop. Widok published jest
readonly, a konflikt 409 oferuje odświeżenie albo odrzucenie lokalnej zmiany.

Publikacja przekazuje `expectedVersion` w wygenerowanym `PublishRequest`, dlatego także
nie może cicho nadpisać równoległej zmiany drafu.

**SET-05A dostarcza doradcze sprawdzenie zestawu.** `V041__add_exercise_set_analysis.sql`
utrwala wynik publikacji i findings w schemacie `exercise_set`; polityka analizuje
wyłącznie snapshot wersji. Draft można sprawdzić endpointem
`GET .../versions/{versionId}/analysis`, a wersja opublikowana zwraca swój niezmienny,
zapisany wynik. Publiczne stany to `NO_SUGGESTIONS`, `SUGGESTIONS_AVAILABLE` i
`ANALYSIS_UNAVAILABLE`; historyczny `BLOCKED` jest odczytywany jako zestaw z sugestiami.
Findings nie wpływają na publikację: możliwy jest zestaw bez tytułu, profilu lub ćwiczeń,
z jedną pozycją albo wieloma sugestiami. Polityka, timestamp, metryki i findings
pozostają wewnętrznymi danymi audytowymi. Twarde odrzucenie obejmuje wyłącznie brak
uprawnień, konflikt optimistic locking, nieistniejącą wersję ćwiczenia, niespójny request
oraz próbę zmiany opublikowanej wersji. Analiza zestawu ma charakter wyłącznie doradczy.
Ostateczna decyzja dotycząca zawartości zestawu należy do specjalisty. Nie ma ML ani
reanalizy żywego katalogu. Szczegóły: [analiza Exercise Set](../architecture/exercise-set-analysis.md)
i [ADR-015](../adr/ADR-015-deterministic-versioned-exercise-set-analysis.md).

**SET-06 / SET-06A dostarcza wersjonowaną ekspozycję anatomiczną i diagnostykę mapowania wizualnego.** Endpoint
`GET .../versions/{versionId}/anatomy` liczy draft z dokładnych opublikowanych
snapshotów ćwiczeń, a dla publikacji zwraca utrwalony wynik. Builder pokazuje kanały,
kompletność, udziały, rozbicia, dowody, wzorce, findings i braki danych z własnymi
stanami loading/error/stale. Jest to wyłącznie opis jakościowy — nie ocena kliniczna ani
pomiar siły. SET-06A dodaje wersjonowaną kompletność mapowania oraz diagnostykę kodów
regionów, warstwy i widoku; aktywne metadane są dostępne przez
`GET /api/v1/anatomy/visual-regions`. Frontend nie utrzymuje słownika anatomii ani nie
renderuje mapy ciała. Szczegóły: [ekspozycja anatomiczna zestawu](../architecture/exercise-set-anatomy-exposure.md).

**SET-07A3 niezależnie rozstrzyga propozycje geometrii SVG.** Manifest
`anatomy-geometry-manifest-v1.json` jest związany z hashem źródłowego SVG i V047 oraz
obejmuje 176/176 elementów: 39 `APPROVED`, 92 `AMBIGUOUS`, 45 `REJECTED`, bez `PROPOSED` i `UNREVIEWED`.
39 zatwierdzonych elementów `INTERACTIVE` pokrywa 12/32 kodów V047; niejednoznaczne
geometrie pozostały bez wymuszonego kodu. Atlas pokazuje finalne decyzje, region,
confidence i rationale, a etap `SET-07A3_FINAL` odrzuca nierozstrzygnięte wpisy,
zatwierdzenia LOW-confidence i zatwierdzenia nieinteraktywne. Odrzucone detale twarzy,
dłoni, stóp, konturów i konstrukcji nie mają kodów V047. Jest to częściowa mapa techniczna wymagająca końcowej
weryfikacji człowieka przed produkcyjnym SVG lub UI. Granica pozostaje:
`anatomical structure → visualRegionCode → SVG geometry`.

**SET-07A4 dostarcza deterministyczny asset SVG jako PARTIAL TECHNICAL MAP.** Generator
`tools/anatomy-svg/generate-production-svg.py` produkuje
`web/src/assets/anatomy/anatomy-body-partial-v1.svg`: wyłącznie 39 zatwierdzonych ścieżek
jest interaktywnych, w 12 stabilnych grupach regionów. Wszystkie 92 `AMBIGUOUS` pozostają
neutralne, a 45 `REJECTED` zachowano jako neutralną geometrię wizualną/techniczną. Mapa
obejmuje tylko 12/32 regionów; pozostałe 20 `NO_GEOMETRY` jest dostępne wyłącznie
tabelarycznie. To nie jest kompletny model anatomiczny; SET-07B może użyć assetu tylko
jako wyraźnie oznaczonego technicznego prototypu.

**SET-06B dostarcza publiczną projekcję `visualRegionExposures`.** Ten sam endpoint
anatomii zwraca `visualMappingVersion`, kompletność mapowania, wersję polityki bandów i
backendowo wyliczone ekspozycje regionów. Projekcja korzysta tylko z bezpośrednich,
deduplikowanych wkładów snapshotu; nie czyta aktualnego mapowania dla historii. Wynik
opublikowany jest utrwalony w istniejącym JSONB V043, więc nie wymaga migracji. SVG jest
wyłącznie geometrią, a `visualRegionExposures` to jedyne wejście mapy. Asset pozostaje
częściowy (12/32), z 20 regionami dostępnymi tylko tabelarycznie; nie jest oceną
biomechaniczną ani kliniczną. SET-07B może rozpocząć prototyp techniczny.

## Źródła prawdy

- Kontrakt HTTP: `web/openapi/openapi.json` oraz generowany z niego klient w
  `web/src/app/api/generated/`.
- Konfiguracja lokalnego środowiska: `.env.example`, `compose.yaml` i główny
  `README.md`.
- Historyczne raporty oraz audyty pozostają w `docs/` jako materiały
  referencyjne i nie określają bieżącego stanu wdrożenia.

## Tożsamość uczestnika i rekordy klientów

- `V036__add_participant_records` jest utrwaloną migracją kanonicznych
  kartotek i linków dostępu. `V037__stabilize_client_create_idempotency`
  utrwala fingerprint żądania tworzenia kartoteki i indeks aktywnej relacji;
  istniejący replay bez zapisanego payloadu jest celowo odrzucany.
- Tabele kanoniczne dla nowych kartotek to `participant.participant_record` oraz
  `participant.participant_access_link`. Rekord uczestnika może nie mieć konta.
- `participantId` jest kanoniczną granicą kartoteki i relacji specjalisty.
  Link dostępu do konta pozostaje opcjonalny i jest jedyną drogą z kartoteki do
  `principalAccountId`. Starsze `*AccountId` pozostają lokalnymi mostami
  odczytowymi modułów, które nie zostały jeszcze przeniesione.
- Brak konta nie blokuje pracy specjalisty: można utworzyć i otworzyć kartotekę,
  planować terminy oraz prowadzić workspace. Niedostępne pozostają wyłącznie
  działania wymagające samodzielnego konta uczestnika.

## Workspace specjalisty i lokalne operacje

- Workspace i timeline specjalisty są odczytami po `participantId` przez publiczne
  porty właścicieli danych; składają profil, aktywny plan, terminy, wykonania i
  minimalne sygnały wymagające uwagi, bez przejmowania ich własności przez frontend.
  Każdy odczyt pozostaje za granicą capability, aktywnej relacji i zgody właściwej
  dla kontekstu zawodowego; brak capability do historii wykonań ukrywa ją, a nie
  rozszerza dostęp.
- „Następne spotkanie” jest wybierane względem bieżącego czasu: uwzględnia
  `SCHEDULED` jeszcze nie zakończone, `CONFIRMED` nie rozpoczęte oraz trwające
  `IN_PROGRESS`; statusy końcowe nie są kandydatami. Timeline obsługuje spotkania,
  planowane sesje i wykonania, z zakresem 2 tygodni / 3 miesięcy / 12 miesięcy,
  filtrem typu, kursorem wcześniejszych wyników oraz przełącznikiem osi/listy.
  Zakres, typy, widok i wybrane zdarzenie są kontrolowane przez URL.
- Sygnały uwagi są prezentowane jako minimalne, bezpieczne komunikaty. Zdarzenia
  mają etykiety kategorii, typu, statusu, źródła i czasu; UUID lub nieznany kod nie
  staje się samodzielną etykietą dla użytkownika. Panel szczegółu przyjmuje focus,
  zamyka się przez Escape i zwraca focus do elementu otwierającego; kontrolki mają
  nazwy ARIA, a układ zachowuje użyteczność na małych viewportach. Zaplanowanie
  terminu odświeża workspace i timeline.
- Widok `Today` prezentuje terminy, wolne sloty i worklistę w utrwalonej strefie
  specjalisty. Testy komponentów pokrywają geometrię slotów, prezentację i
  przejścia workspace; pozostały dług UX to E2E dla mobile viewport, zoomu
  200%, klawiatury i reduced motion.
- Lokalne Compose włącza wyłącznie testowe override'y zgody i zweryfikowanego
  scope'u zawodowego. Nie są one aktywne w profilu produkcyjnym.

## Migracje Flyway

- `bin/flyway-migrate` i `bin/flyway-repair` uruchamiają cele Flyway Maven z
  profilem `local` i parametrami z `.env`; naprawa historii jest operacją
  administracyjną po uprzedniej diagnozie.
- Skrypty wymagają SDKMAN oraz Java 25.0.2-open. Flyway pozostaje właścicielem
  schematu, a Hibernate działa w trybie walidacji.

## Obowiązkowa weryfikacja CI

CI wykonuje `mvn verify`, deterministyczną instalację zależności frontendu,
testy jednostkowe bez watch mode, produkcyjny build Angulara oraz weryfikację
snapshotu i generatora OpenAPI. Smoke test Docker Compose wymaga dostępnego
silnika Docker i nie jest zastępstwem dla tych weryfikacji w środowisku bez
socketu Dockera.

Weryfikacja OpenAPI odświeża specyfikację i klient przez `npm run api:refresh`,
a następnie wymaga braku różnic. Wygenerowane pliki są aktualizowane wyłącznie
tym przepływem, nie ręcznie.

## GOALS-05 — katalog celów uczestnika

GOALS-05 jest ukończony: workspace specjalisty pobiera neutralny, tylko do odczytu katalog metryk
celów i tworzy cel przez osobny endpoint create-from-preset. Katalog jest wersjonowany w kodzie,
bez migracji, CRUD ani referencji na żywo; zapisany cel ma pojedynczy snapshot wyniku oraz neutralny
priorytet backendu.
Przyszłe prace obejmują edycję/dodawanie wyników po utworzeniu, konfigurowalny katalog, powiązanie
wyników siłowych/ćwiczeń z katalogiem ćwiczeń oraz kolejne typy pomiarów.

Snapshot i wygenerowany klient obejmują aktualne endpointy kartoteki specjalisty,
workspace, `Today` i zestawów ćwiczeń. Są odświeżane wyłącznie przez
`npm run api:refresh`, a `npm run api:verify` wykrywa drift.

## SET-07B — częściowa mapa ciała

Builder oraz odczyt opublikowanej wersji korzystają ze wspólnego komponentu mapy
technicznej. SVG stanowi wyłącznie geometrię; jedynym wejściem wizualnym jest utrwalone
API `visualRegionExposures`, łączone dokładnie przez `visualRegionCode`. Mapa obejmuje
12 z 32 regionów, a wszystkie wyniki — także bez geometrii — pozostają dostępne w
zestawieniu tekstowym z komunikatem `Brak geometrii w mapie V1`. Frontend nie mapuje
struktur, nie liczy udziałów ani nie wyznacza progów koncentracji. Historyczny wynik
pokazuje zapisaną wersję mapowania i nie jest reinterpretowany bieżącym słownikiem.

## SET-07B1 — runtime QA specjalisty

Realny scenariusz Playwright ma osobny OIDC storage state specjalisty w ignorowanym
`web/.auth/`. Po `docker compose up --build` uruchamia się go przez `cd web && npm run
test:e2e -- specialist-body-map`; `E2E_BASE_URL` jest opcjonalny (domyślnie
`http://localhost:4200`), a lokalne poświadczenia `E2E_SPECIALIST_USERNAME` i
`E2E_SPECIALIST_PASSWORD` nie są przechowywane w Git.
Setup tworzy fixture wyłącznie przez publiczne API: uwierzytelnione `POST
/api/v2/exercises/search` i preview wybierają deterministycznie minimalny zestaw
opublikowanych wersji z `DYN_EXU` i `ISO_SEC`, a endpoint anatomii wybiera najmniejszego
kandydata spełniającego FRONT, BACK, geometrię, brak geometrii i rozbicia. `E2E_BODY_MAP_EXERCISE_VERSION_IDS` pozostaje
opcjonalnym, rozdzielanym przecinkami override dla lokalnego katalogu; błąd wyboru podaje
instrukcję jego użycia. Test nie został w tym stanie ponownie wykonany; pokrywa widok
opublikowany/historyczny oraz desktop, 390 i 320 px z kontrolą konsoli, SVG/API,
klawiatury, powrotu focusu i overflow. Runtime QA mapy ciała jest świadomie
odroczone i nie blokuje stabilności DEV-ENV, w szczególności gdy środowisko nie
obsługuje sandboxa Chromium.

# Appointment lifecycle history

Historia lifecycle terminu jest utrwalana append-only w
`calendar.appointment_event`; wiersz terminu pozostaje bieżącym snapshotem. V050
poszerza klasyfikację historii o pięć kolumn, a publiczne zdarzenia terminów mają
własne UUID i są dostępne jako kontekstowo autoryzowany szczegół.

`Today` wylicza deterministycznie `APPOINTMENT_OUTCOME_REQUIRED` dla zaległych,
aktywnych terminów `SCHEDULED`, `CONFIRMED` i `IN_PROGRESS`. To nie jest trwała
worklista ani automatyczna zmiana lifecycle: specjalista wybiera idempotentną akcję
lifecycle z wersją terminu. Deep link do szczegółu zdarzenia zachowuje kontekst
uczestnika i wymaga aktywnej relacji oraz capability właściwych dla tego kontekstu.
