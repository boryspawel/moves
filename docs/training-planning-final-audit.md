# Końcowy audyt vertical slice planowania treningowego

Data audytu: 2026-07-21

## Stan bieżący i granice slice'u

Backend posiada profil specjalisty i aktywną relację specjalista–uczestnik,
wersjonowane zgody oraz kontrolę capabilities. Udostępnia plan wraz z
rewizjami, celami i receptami, a także zapis wykonania: RPE, ból, trudność i
raport po 24 godzinach. Dostępne są również safety, worklista specjalisty,
terminy oraz mechanizmy recovery.

Nie ma jeszcze notatek, dokumentów, postępu ani audytowego modelu odczytu.
Nie istnieją ogólne endpointy workspace ani timeline; istniejący ograniczony
widok `Today` nie jest takim modelem. W kolejnych etapach potrzebne będą
publiczne porty odczytowe właścicieli tych danych, zamiast bezpośredniego
odczytu między modułami.

## Zakres ukończony

Zrealizowano prompty 1–10 serii `moves-codex-implementation-prompts-training-planning.md`: granice foundation, taksonomię anatomii, wersjonowany profil ćwiczenia, rewizje planu, planned load, capabilities i consent, Safety V2, atomową aktywację, executed load z alertami oraz współpracę trener–fizjoterapeuta.

Audyt capability potwierdza użycie wszystkich capabilities opieki nad uczestnikiem: `PLAN_PERFORMANCE`, `PLAN_FUNCTIONAL_RECOVERY`, `SET_PERFORMANCE_BUDGET`, `SET_CLINICAL_RESTRICTION`, `VIEW_EFFECTIVE_RESTRICTION`, `VIEW_CLINICAL_RATIONALE`, `ACKNOWLEDGE_PERFORMANCE_WARNING` i `OVERRIDE_CLINICAL_BLOCK`. Capability publikacji ćwiczeń i reguł safety są osobnymi pojęciami redakcyjnymi.

Plan zachowuje jednego ownera i jawnie zakresowanych collaborators. Zablokowana rewizja może zostać skierowana do fizjoterapeuty, który proponuje zmianę albo oznacza gotowość do ponownej walidacji. Clinical restriction ma historię rewizji. DTO trenera nie zawiera source type ani clinical rationale. Każda operacja ponownie sprawdza profil, relację, zgodę, purpose, capability i ownership lub collaboration scope.

## Weryfikacja techniczna

- Migracje V001–V015 są liniowe. Testy obejmują czystą bazę oraz upgrade realistycznego fixture z V005.
- Wewnętrzne relacje nowych tabel mają FK, CHECK constraints i indeksy; referencje między kontekstami pozostają UUID walidowanymi przez porty.
- Testy architektury kontrolują granice modułów i brak cykli. Audyt nie wykazał nowych cross-schema native queries, publicznych setterów, `FetchType.EAGER`, tymczasowych adapterów ani TODO/FIXME.
- Planned i executed load są osobnymi profilami wielowymiarowymi, z wersją kalkulatora/reguł; nie istnieje globalny score.
- OpenAPI jest generowane z uruchomionej aplikacji, a klient `typescript-fetch` przez OpenAPI Generator 7.24.0. Pliki wygenerowane nie są edytowane ręcznie.
- Backend CI używa Temurin Java 25 i wykonuje pełne `mvn --batch-mode verify`.

Ścieżki akceptacyjne są pokryte przez testy integracyjne modułów planowania, workflow, safety i execution:

- trener: performance plan, planned load, warning, acknowledgement, aktywacja, wykonanie i adherence;
- fizjoterapeuta: functional recovery, clinical restriction, assessment/override, aktywacja, pain/post24h, alert i kolejna rewizja;
- współpraca: hard block, minimalny envelope, review, zmiana lub override, revalidation i aktywacja bez clinical leak;
- self-directed: plan uczestnika, warning, nieomijalny clinical hard block i bezpieczna aktywacja;
- retry/concurrency, cofnięta zgoda, zakończona relacja, nieaktualny assessment, correction/reversal oraz recovery projekcji.

## Świadomie odłożone

- Nie dodano runtime'owego edytora reguł safety. Reguły są wersjonowanym kodem podlegającym review i release; ewentualny przyszły workflow publikacji musi egzekwować `PUBLISH_SAFETY_RULE` i nie może współdzielić uprawnień z publikacją treści ćwiczeń.
- Nie rozszerzano funkcjonalności Angulara. W tym zakresie aktualizowany jest wyłącznie snapshot OpenAPI i wygenerowany klient, zgodnie z zakresem serii backendowej.
- Nie modyfikowano `spec.md`.

## Blokery

Brak.

## Status kontraktu kartoteki specjalisty — Stage 2

Dodano autoryzowane, ograniczone projekcje odczytowe `GET
/api/v1/specialist/participants/{participantId}/workspace` oraz `GET
/api/v1/specialist/participants/{participantId}/timeline`. Kompozycja należy
do modułu `specialist` i korzysta wyłącznie z publicznych portów właścicieli
danych: skrótu uczestnika, kontekstu strefy czasowej, terminów, aktywnej
rewizji planu oraz historii prób wykonania. Dostęp wymaga aktywnego konta i
profilu specjalisty, zweryfikowanego scope'u zawodowego, aktywnej relacji,
capability właściwego dla roli oraz zgody na plan.

Timeline obsługuje `from`, `to`, `types`, `granularity` (`DETAIL`, `WEEK`,
`MONTH`), cursor i limit 1–100; domyślne zakresy wynoszą odpowiednio 14 dni,
3 miesiące i 12 miesięcy, a większe zakresy są odrzucane. Zwracane są tylko
deterministyczne zdarzenia, które mają obecnie źródło przez porty: terminy i
planowane sesje aktywnej rewizji, a próby wykonania wyłącznie po autoryzacji
`VIEW_ADHERENCE_WORKLIST` oraz zgodzie `EXECUTION`. Bez tego zakresu workspace
pozostaje widokiem plan-only, a timeline redaguje zdarzenia wykonania. Historii rewizji planów,
notatek, dokumentów, pomiarów, problemów ani agregatów tygodniowych/miesięcznych
nie deklarowano, ponieważ nie są jeszcze udostępnione przez odpowiednie porty.

## Status kontraktu kartoteki specjalisty — Stage 4

Workspace zawiera teraz operacyjny nagłówek (w tym datę aktywacji relacji i
strefę czasową), dozwolone capabilities, kolejny termin, minimalny aktywny plan,
cele, ostatnią aktywność wykonania, sygnały worklisty jako attention items i
active problems oraz quick actions. Sygnały worklisty są odczytywane wyłącznie
po osobnej autoryzacji capability `VIEW_ADHERENCE_WORKLIST` i zgody na execution;
przy jej braku nie są zwracane. Ta sama brama chroni historię wykonania, w tym
ostatnią aktywność i wykonanie ostatniej sesji aktywnego planu.

Nieistniejące dane nie są konstruowane: avatar, etykieta kontekstu, nazwa i data
aktywacji planu, ostatnie wartości celów oraz kierunek poprawy pozostają puste.
Adherence używa jawnego statusu `NO_DATA`; brak wartości nie oznacza braku
wykonania. Nie ma jeszcze publicznych portów dla pomiarów, pełnego postępu ani
notatek klinicznych, więc workspace ich nie ujawnia.

## Status kontraktu kartoteki specjalisty — Stage 5

`ParticipantTimelineEvent` zawiera stabilne identyfikatory i typy, kategorię,
status, oddzielne czasy wystąpienia, zapisu i ewentualnej aktualizacji,
metadane źródła i autora oraz pola powiązań i faktów. Planowana sesja ma przez
port dostępny czas zapisu (`createdAt` rewizji) i autora, termin ma czas
utworzenia oraz ostatniej zmiany z właściciela kalendarza, a próby wykonania
pozostawiają autora pustego. Sortowanie i cursor są deterministyczne:
`effectiveFrom DESC`, `recordedAt DESC` (brak czasu zapisu na końcu), `eventId`.

Źródłami pozostają wyłącznie terminy, planowane sesje aktywnej rewizji oraz,
po capability i zgodzie na execution, fakty wykonania. Nie są konstruowane
zdarzenia postępu, notatek, dokumentów, pomiarów ani problemów; odpowiednie
pola pozostają puste, dopóki właściciel danych nie wystawi ich przez port.

## Status kontraktu kartoteki specjalisty — Stage 6

Fakt próby wykonania zawiera teraz identyfikator planowanej sesji, rewizji,
wybrany wariant oraz czas ostatniej zmiany. Dla wykonania należącego do
aktywnej rewizji timeline zwraca `PlannedExecutionComparison`: niezmienione
dawki z rewizji (serie, powtórzenia, czas, obciążenie wraz z jednostką,
dystans, tempo i odpoczynek), stan wykonania i różnicę wybranego wariantu.
Jednostki nie są normalizowane. Wartości rzeczywiście wykonanych serii i
powtórzeń nie są jeszcze wystawione przez publiczny port historii wykonania,
dlatego pozostają niedostępne zamiast być rekonstruowane z tabel właściciela.

## Status kontraktu kartoteki specjalisty — Stages 7–13

Nie dodano syntetycznego `progressScore`. Obecne źródła nie udostępniają przez
publiczne porty pomiarów postępu, decyzji progresyjnych, notatek ani dokumentów;
nie są więc reprezentowane jako wymyślone zdarzenia. Workspace i timeline są
audytowane jako odczyty kartoteki. Dostęp nadal wymaga aktywnej relacji,
odpowiedniego celu przetwarzania, zgody i capability właściwego dla roli;
historia wykonania oraz worklista są dodatkowo redagowane bramą
`VIEW_ADHERENCE_WORKLIST`. Trener nie otrzymuje dokumentacji klinicznej ani
diagnoz, ponieważ endpoint nie pobiera ich ze źródeł.

WEEK i MONTH deterministycznie agregują wyłącznie powtarzalne sesje i
wykonania. Agregat zawiera zakres faktycznych czasów źródłowych i ich liczbę;
terminy pozostają pojedynczymi zdarzeniami. Moduły wykonania i kalendarza
udostępniają publiczne, ograniczone seek query porty JPA/Hibernate. Port
kalendarza używa dokładnie porządku timeline: `effectiveFrom`, `recordedAt`
i stabilnego `eventId`, więc wspólny cursor nie ucina źródła terminów.
Bounded batch historii rewizji oraz pełna seek paginacja pozostałych źródeł
nadal wymagają analogicznych rozszerzeń portów planowania i wykonania; obecna
kompozycja nie zastępuje ich filtrowaniem pobranej historii.

## Status kontraktu kartoteki specjalisty — Stages 10–13

Każde obecne zdarzenie udostępnia `detailKind` oraz `detailResourceId`, bez
osadzania pełnego planu, dokumentu lub notatki. `availableActions` zostały
ograniczone do istniejących przypadków użycia: termin można otworzyć, sesję
planowaną prowadzi do aktywnego planu, a wykonanie nie reklamuje nieistniejącej
operacji specjalisty. Workspace oferuje wyłącznie dozwolone `OPEN_TIMELINE`,
`SCHEDULE_APPOINTMENT` oraz akcje na faktycznie obecnych zasobach.

## Status testów kartoteki specjalisty — Stage 14

Dodano integracyjne testy JPA/Hibernate dla aktywnej relacji i odmowy dostępu
bez relacji, redakcji historii wykonania bez zgody `EXECUTION`, deterministycznego
łączenia terminów z wykonaniami oraz seek cursor kalendarza. Pokryte są także
walidacje zakresu, limitu i nieobsługiwanego typu, a `WEEK` i `MONTH` agregują
powtarzalne wykonania bez agregowania terminów. Fixture'y korzystają z usług,
repozytoriów i `EntityManager`; test nie używa `JdbcTemplate`.
