Poniżej dwa osobne prompty. Najpierw należy wykonać backend, następnie frontend na wygenerowanym kontrakcie OpenAPI.

Prompt 1 — backend kartoteki klienta i timeline
# Zadanie: backend kartoteki klienta i longitudinalnego timeline’u

## Rola

Działaj jako senior backend engineer i architekt domenowy projektu `moves`.

Zaimplementuj backendowy pion kartoteki klienta/pacjenta dla specjalisty:

- podsumowanie współpracy;
- aktywny plan;
- cele;
- zlecone ćwiczenia i zestawy;
- planowane i wykonane sesje;
- postępy i pomiary;
- progresję;
- problemy i alerty;
- longitudinalny timeline.

Pracuj zgodnie z instrukcjami repozytorium. Nie wykonuj commita ani push.

## Główny cel

Frontend ma otrzymać stabilny, ograniczony i autoryzowany read model, który
umożliwi zbudowanie jednego workspace’u klienta bez samodzielnego agregowania
kilkunastu endpointów.

Nie twórz nowej równoległej domeny planów, sesji, celów ani bezpieczeństwa.
Najpierw wykorzystaj istniejące moduły i ich publiczne porty.

# ETAP 1 — audyt as-is

Sprawdź rzeczywisty kod i dokumentację dotyczącą:

- relacji specjalista–uczestnik;
- profilu uczestnika;
- zgód i capability authorization;
- planów i rewizji planów;
- celów;
- przepisywania ćwiczeń;
- planowanych sesji;
- wykonania sesji;
- prób wykonania i wyników;
- postępu;
- RPE;
- bólu i trudności;
- odpowiedzi po 24 godzinach;
- alertów bezpieczeństwa;
- worklisty specjalisty;
- spotkań kalendarzowych;
- recovery episodes;
- notatek;
- dokumentów;
- audytu i provenance.

uzupdłnij dokumentację projektu, nie twórz nowych plików jeżeli to nie jest konieczne

Nie odczytuj bezpośrednio tabel obcego modułu, jeżeli istnieje publiczny port.
Jeżeli portu brakuje, dodaj minimalny port read-only po stronie właściciela
danych.

# ETAP 2 — granice pojęciowe

Zachowaj rozróżnienie:

- `Appointment` — zaplanowane spotkanie ze specjalistą;
- `PlannedSession` — samodzielna albo prowadzona sesja zapisana w planie;
- `SessionExecution` — rzeczywiste wykonanie;
- `PlanRevision` — wersjonowany plan;
- `Goal` — osobny cel;
- `ProgressMeasurement` — pomiar lub obserwacja;
- `Problem/Alert` — sygnał wymagający działania;
- `Note` — dokumentacja specjalisty.

Nie utożsamiaj:

- zaplanowanej sesji z wykonaniem;
- spotkania z planem ćwiczeń;
- bólu z obciążeniem;
- trudności z RPE;
- szablonu zestawu z przypisanym planem klienta;
- globalnego „progresu” z jednym wynikiem liczbowym.

# ETAP 3 — przypadki użycia

Dodaj dwa jawne przypadki użycia:

```text
GetSpecialistParticipantWorkspace
GetSpecialistParticipantTimeline

Preferowane endpointy:

GET /api/v1/specialist/participants/{participantId}/workspace
GET /api/v1/specialist/participants/{participantId}/timeline

Parametry timeline:

from
to
types
granularity
cursor
limit

granularity:

DETAIL
WEEK
MONTH

Domyślny zakres:

DETAIL: ostatnie 14 dni;
WEEK: ostatnie 3 miesiące;
MONTH: ostatnie 12 miesięcy.

Nie pozwalaj pobierać nieograniczonej historii jednym requestem.

ETAP 4 — ParticipantWorkspaceView

Zaprojektuj projekcję, np.:

SpecialistParticipantWorkspaceView
  generatedAt
  participant
  relationship
  capabilities
  nextAppointment?
  activePlan?
  goals[]
  adherenceSummary?
  recentProgress?
  activeProblems[]
  attentionItems[]
  quickActions[]
Participant summary

Tylko dane potrzebne do operacyjnej pracy:

participantId;
displayName;
opcjonalny avatar reference;
status współpracy;
data rozpoczęcia współpracy;
etykieta kontekstu: klient/pacjent/zawodnik;
strefa czasowa;
dozwolone akcje.

Nie zwracaj pełnego profilu, wywiadu ani danych klinicznych, jeżeli nie są
potrzebne na nagłówku kartoteki.

Active plan

Minimalnie:

planId;
activeRevisionId;
nazwa;
status;
data aktywacji;
okres obowiązywania;
liczba aktywnych zaleceń;
najbliższa planowana sesja;
ostatnia wykonana sesja;
availableActions.
Goals

Dla każdego celu:

goalId;
nazwa;
typ;
status;
wartość początkowa;
wartość docelowa;
ostatnia wartość;
jednostka;
termin;
kierunek poprawy;
confidence/data quality, jeżeli istnieje;
availableActions.

Nie obliczaj wspólnego procentu dla nieporównywalnych celów.

Adherence summary

Rozdziel:

zaplanowane sesje;
rozpoczęte;
ukończone;
pominięte;
wykonanie zaleceń;
pokrycie raportowania;
zakres czasowy.

Brak danych nie może być interpretowany jako brak wykonania.

Active problems

Minimalnie:

problemId;
typ;
priorytet;
status;
krótki opis;
effectiveAt;
recordedAt;
źródło;
availableActions.

Nie zwracaj pełnej notatki klinicznej w podsumowaniu.

ETAP 5 — wspólny model zdarzenia timeline

Zaprojektuj stabilną projekcję:

ParticipantTimelineEvent
  eventId
  eventType
  category
  status
  effectiveFrom
  effectiveTo?
  recordedAt
  title
  summary
  importance
  sensitivity
  actor?
  source
  relatedGoalIds[]
  relatedPlanRevisionId?
  plannedExecutionComparison?
  measurement?
  problem?
  availableActions[]
Czas

Rozdziel:

effectiveFrom/effectiveTo — kiedy zdarzenie faktycznie wystąpiło;
recordedAt — kiedy zostało zapisane;
updatedAt — kiedy je zmodyfikowano, jeżeli dotyczy.

Nie sortuj historii wyłącznie według czasu zapisu.

Sortowanie:

effectiveFrom DESC;
recordedAt DESC;
stabilny eventId.
Kategorie

Minimalne kategorie:

GOAL
PLAN
PRESCRIPTION
APPOINTMENT
SESSION
EXECUTION
PROGRESS
PROBLEM
NOTE
DOCUMENT

Dodawaj wyłącznie typy posiadające rzeczywiste źródło domenowe.

Przykładowe typy:

GOAL_CREATED
GOAL_UPDATED
GOAL_MILESTONE_REACHED
GOAL_COMPLETED

PLAN_REVISION_ACTIVATED
PLAN_REVISION_REPLACED
PROGRESSION_APPLIED

EXERCISE_ASSIGNED
EXERCISE_SET_ASSIGNED
PRESCRIPTION_CHANGED

APPOINTMENT_SCHEDULED
APPOINTMENT_COMPLETED
APPOINTMENT_CANCELLED
APPOINTMENT_NO_SHOW

SESSION_PLANNED
SESSION_STARTED
SESSION_COMPLETED
SESSION_SKIPPED
SESSION_ABANDONED

PROGRESS_MEASUREMENT_RECORDED

PAIN_REPORTED
DIFFICULTY_REPORTED
POST_24H_RESPONSE_RECORDED
SAFETY_ALERT_RAISED
SAFETY_ALERT_RESOLVED

NOTE_ADDED
DOCUMENT_ADDED

Zweryfikuj rzeczywiste statusy i nazwy domenowe. Nie wymyślaj zdarzeń, których
nie da się deterministycznie wyprowadzić.

ETAP 6 — planowane kontra wykonane

Dla wykonanej sesji lub ćwiczenia udostępnij prezentacyjny model:

PlannedExecutionComparison
  planned
  performed
  completionState
  deviations[]

Przykładowe dane:

serie;
powtórzenia;
czas;
ciężar;
dystans;
tempo;
odpoczynek;
wersja ćwiczenia;
pominięte elementy;
zamieniony wariant.

Nie normalizuj różnych jednostek do jednej arbitralnej liczby.

ETAP 7 — progresja i postępy

Rozdziel następujące dane:

GoalProgress
DoseProgression
PerformanceMeasurement
Adherence
InternalResponse
Symptoms
SafetySignal

Nie twórz globalnego progressScore.

Dla decyzji progresyjnej pokaż:

co zmieniono;
wartość przed;
wartość po;
powód;
źródło decyzji;
specjalistę lub politykę;
datę wejścia w życie;
powiązaną rewizję planu.

Jeżeli decyzja była automatyczną sugestią, odróżnij sugestię od decyzji
zatwierdzonej przez człowieka.

ETAP 8 — agregacja zależna od skali
DETAIL

Zwracaj pojedyncze zdarzenia:

sesje;
wykonania;
zmiany zaleceń;
problemy;
pomiary;
notatki.
WEEK

Agreguj powtarzalne wykonania w tygodniowe podsumowania, ale zachowuj osobno:

problemy;
alerty;
kamienie milowe;
zmiany planu;
ważne pomiary;
decyzje progresyjne.
MONTH

Agreguj:

liczbę zaplanowanych i wykonanych sesji;
wykonanie zaleceń;
pokrycie raportowania;
zakresy pomiarów;
liczbę problemów.

Nie ukrywaj przez agregację zdarzeń istotnych klinicznie lub bezpieczeństwa.

Agregaty muszą być deterministyczne i zawierać zakres czasu oraz liczbę
zdarzeń źródłowych.

ETAP 9 — paginacja i wydajność

Użyj paginacji kursorem, nie offsetu dla długiej historii.

Cursor powinien opierać się na:

effectiveFrom;
recordedAt;
stabilnym identyfikatorze.

Wymagania:

bounded limit, np. 20–100;
brak N+1;
brak pobierania pełnych agregatów;
brak ładowania dokumentów i pełnych notatek w timeline;
indeksy dla kluczowych zakresów czasowych;
stabilne wyniki przy równoległym dopisywaniu nowych zdarzeń.

Jeżeli kompozycja wielu portów staje się zbyt kosztowna, zaproponuj dedykowany
read model. Nie wprowadzaj event store wyłącznie dla UI bez uzasadnienia.

ETAP 10 — szczegóły zdarzenia

Podstawowy event powinien wystarczyć do wyświetlenia panelu kontekstowego.

Nie zwracaj pełnych:

dokumentów;
wywiadów;
notatek klinicznych;
dużych struktur planu.

Dla danych wymagających rozwinięcia zwróć:

detailKind
detailResourceId
availableActions

Frontend może wtedy użyć istniejącego endpointu właściciela zasobu.

Nie twórz jednego uniwersalnego endpointu edycji wszystkich typów zdarzeń.

ETAP 11 — szybkie akcje

workspace.quickActions i event.availableActions mają wynikać z backendowych
capabilities.

Przykładowe akcje:

ADD_NOTE
SCHEDULE_APPOINTMENT
START_APPOINTMENT
OPEN_SESSION
ASSIGN_EXERCISE
ASSIGN_EXERCISE_SET
CREATE_PLAN_REVISION
ADD_MEASUREMENT
ACKNOWLEDGE_PROBLEM
RESOLVE_PROBLEM
OPEN_DOCUMENT

Nie zwracaj akcji, dla których nie istnieje przypadek użycia albo uprawnienie.

ETAP 12 — trener kontra fizjoterapeuta

Ten sam endpoint ma zwracać różny zakres danych zależnie od:

roli;
kwalifikacji;
aktywnej relacji;
organizacji;
zakresu zgody;
celu przetwarzania;
sensitivity danych.

Trener może widzieć między innymi:

cele treningowe;
plan;
wykonanie;
pomiary sportowe;
trudność;
dozwolone sygnały bezpieczeństwa.

Fizjoterapeuta może dodatkowo widzieć:

pomiary funkcjonalne;
wywiad i dokumenty kliniczne;
kliniczne notatki i problemy;
tylko w zakresie relacji i zgód.

Nie ujawniaj trenerowi diagnozy ani pełnej dokumentacji klinicznej przez
timeline.

ETAP 13 — audyt i provenance

Każde zdarzenie powinno mieć możliwość wskazania:

źródłowego agregatu;
autora;
czasu zapisu;
wersji;
statusu korekty lub unieważnienia.

Odczyt danych o podwyższonej wrażliwości musi zostać audytowany zgodnie z
aktualną polityką projektu.

Nie usuwaj historii przez nadpisywanie.

ETAP 14 — testy backendu

Dodaj testy dla:

aktywnej relacji specjalista–uczestnik;
braku relacji;
cofniętej zgody;
różnych zakresów danych trener/fizjoterapeuta;
pustej kartoteki;
aktywnego planu;
celów;
przypisanych ćwiczeń;
planowanej sesji;
wykonanej sesji;
porównania plan–wykonanie;
pominiętej sesji;
pomiaru postępu;
progresji;
bólu i trudności jako osobnych sygnałów;
problemu i alertu;
effectiveAt kontra recordedAt;
sortowania;
kursora;
DETAIL/WEEK/MONTH;
zachowania ważnych zdarzeń podczas agregacji;
limitów i braku N+1;
minimalizacji danych;
dostępnych akcji;
audytu.

Preferuj integracyjne testy z rzeczywistym PostgreSQL.

ETAP 15 — OpenAPI

Po implementacji:

odśwież snapshot OpenAPI;
wygeneruj klienta Angular istniejącym workflow;
nie edytuj plików generowanych ręcznie;
zweryfikuj kompatybilność.
Poza zakresem

Nie implementuj teraz:

pełnego wywiadu medycznego;
edytora dokumentów klinicznych;
storage dokumentów;
automatycznego podsumowania AI;
jednego syntetycznego wyniku postępu;
pełnego systemu komunikacji;
ogólnego event store;
oddzielnej kopii danych planowania.
Raport końcowy

Podaj:

mapę wykorzystanych modułów i portów;
wykryte braki;
nowe lub rozszerzone porty;
kontrakt workspace;
kontrakt timeline;
zasady agregacji;
model autoryzacji i redakcji danych;
wydajność;
listę zmienionych plików;
wyniki testów;
końcowy git status;
ocenę ACCEPTABLE / ACCEPTABLE WITH ISSUES / NOT ACCEPTABLE.

Nie wykonuj commita ani push.


---

## Prompt 2 — frontend kartoteki i własnego timeline’u

```text
# Zadanie: kartoteka klienta z własnym hybrydowym timeline’em

## Rola

Działaj jako senior Angular frontend engineer i UX implementer projektu
`moves`.

Zaimplementuj kartotekę klienta/pacjenta jako jeden spójny workspace z własnym
komponentem timeline’u.

Nie używaj gotowej biblioteki timeline, kalendarza ani wykresu Gantta.

Pracuj zgodnie z:

- `web/AGENTS.md`;
- standardem frontendowym repozytorium;
- globalnym systemem stylów;
- wygenerowanym klientem OpenAPI.

Nie wykonuj commita ani push.

## Warunek rozpoczęcia

Najpierw potwierdź dostępność stabilnych endpointów:

```http
GET /api/v1/specialist/participants/{participantId}/workspace
GET /api/v1/specialist/participants/{participantId}/timeline

Nie buduj produkcyjnego widoku na mockach ani na ręcznej agregacji wielu
niezależnych endpointów.

Główny cel UX

Specjalista ma pracować z klientem bez przechodzenia przez wiele ekranów.

Z jednego workspace’u powinien móc:

zobaczyć aktywny plan;
ocenić cele i postępy;
zobaczyć planowane i wykonane sesje;
porównać plan z wykonaniem;
zobaczyć zlecone ćwiczenia i zestawy;
zidentyfikować problemy;
otworzyć szczegóły zdarzenia;
dodać notatkę;
zaplanować spotkanie;
zlecić ćwiczenia;
utworzyć rewizję planu;
dodać pomiar;
rozpocząć bieżącą sesję.

Proste operacje mają odbywać się w panelach lub dialogach bez opuszczania
kartoteki.

ETAP 1 — routing

Dodaj lazy-loaded route:

/specialist/clients/:participantId

Preferowana nazwa w interfejsie:

Klienci

Nie używaj terminu „pacjent” globalnie, ponieważ zależy on od relacji i roli.
Nagłówek może pokazywać etykietę kontekstową zwróconą przez backend.

Query parameters:

range=2w|3m|12m|all
types=...
eventId=...
view=timeline|list

URL ma zachowywać:

zakres czasu;
filtry;
wybrane zdarzenie;
tryb widoku;
back/forward;
direct URL.
ETAP 2 — architektura komponentów

Preferowany podział:

SpecialistParticipantWorkspacePage
  ParticipantWorkspaceHeaderComponent
  ParticipantSummaryStripComponent
  ParticipantQuickActionsComponent
  PatientTimelineOverviewComponent
  PatientTimelineFiltersComponent
  PatientTimelineComponent
    TimelinePeriodGroupComponent
    TimelineEventComponent
    TimelineAggregateEventComponent
  PatientTimelineEventPanelComponent
  PatientTimelineListViewComponent

Smart container odpowiada za:

API;
routing;
query parameters;
loading/error;
cursor pagination;
otwieranie panelu zdarzenia;
unieważnianie danych po operacjach.

Komponenty prezentacyjne:

OnPush;
typed inputs;
typed outputs;
bez bezpośrednich requestów;
bez logiki domenowej w szablonie.
ETAP 3 — układ strony
Desktop
┌────────────────────────────────────────────────────────────────────┐
│ Anna Kowalska · aktywna współpraca · następna sesja: jutro 10:00  │
│ [Rozpocznij] [Notatka] [Zleć ćwiczenia] [Zmień plan] [Pomiar]     │
├────────────────────────────────────────────────────────────────────┤
│ Aktywny plan │ Cele │ Wykonanie │ Problemy wymagające reakcji     │
├────────────────────────────────────────────────────────────────────┤
│ [2 tyg.] [3 mies.] [12 mies.] [Całość] [Filtry]                   │
│                                                                    │
│ OVERVIEW STRIP                                                     │
├──────────────────────────────────────────┬─────────────────────────┤
│ SZCZEGÓŁOWY TIMELINE                    │ PANEL ZDARZENIA          │
│                                          │                         │
│ grupy dni/tygodni/miesięcy               │ szczegóły               │
│                                          │ szybkie akcje           │
└──────────────────────────────────────────┴─────────────────────────┘

Proporcje:

timeline około 65–70%;
panel około 30–35%;
panel może być sticky;
zamknięcie panelu nie może zmieniać filtrów ani pozycji scrolla.
Tablet
panel zdarzenia jako drawer;
overview i timeline na pełną szerokość.
Mobile
jedna kolumna;
szybkie akcje w kontrolowanym menu lub przewijanym pasku;
panel zdarzenia jako pełnoekranowy drawer/bottom sheet;
bez poziomego przewijania;
zachowana pionowa oś zdarzeń.
ETAP 4 — nagłówek kartoteki

Pokaż:

display name;
kontekst relacji;
status współpracy;
najbliższe spotkanie;
aktywny plan;
problemy wymagające uwagi;
dostępne akcje.

Nie pokazuj:

technicznych UUID;
diagnozy;
pełnego wywiadu;
pełnej notatki klinicznej.

Szybkie akcje wynikają wyłącznie z workspace.quickActions.

Nie wyświetlaj niedziałających przycisków.

ETAP 5 — summary strip

Zaprojektuj zwarte podsumowanie, bez dużych dashboardowych kart.

Minimalne moduły:

aktywny plan;
cele;
realizacja planu;
ostatnia aktywność;
problemy wymagające reakcji.

Nie pokazuj globalnego „wyniku postępu”.

Rozdziel:

regularność;
realizację dawki;
cele;
pomiary;
objawy;
jakość danych.

Każdy moduł powinien być dostępny jako przycisk filtrujący timeline albo
otwierający odpowiedni kontekst, bez przechodzenia na osobną stronę.

ETAP 6 — własny overview strip

Zbuduj własny lekki komponent przeglądu okresu.

Technologia:

semantyczny HTML;
SVG dla osi czasu i znaczników;
czyste funkcje TypeScript do skali czasu;
bez pełnego D3;
bez vis-timeline;
bez Highcharts;
bez Gantta.

Tory:

Cele
Plany
Sesje
Problemy

Opcjonalnie dodaj tor pomiarów, jeżeli dane są wystarczająco czytelne.

Overview pokazuje:

okresy obowiązywania planów;
kamienie milowe celów;
zagęszczenie sesji;
ważne problemy;
wybrany zakres.

Nie pokazuj wszystkich zdarzeń jako osobnych etykiet.

Interakcje:

kliknięcie znacznika przewija szczegółowy timeline;
wybór okresu aktualizuje range/from/to;
focus jest widoczny;
overview nie jest jedynym sposobem dostępu do zdarzeń.
ETAP 7 — szczegółowy pionowy timeline

Timeline ma być semantyczną listą chronologiczną, nie canvasem.

DOM:

section
  h2 okres
  ol
    li timeline event

Grupowanie zależne od granularity:

DETAIL — dzień;
WEEK — tydzień;
MONTH — miesiąc.

Każdy event pokazuje:

ikonę lub kształt;
kategorię;
datę i godzinę;
tytuł;
krótkie podsumowanie;
status;
najważniejszą wartość;
źródło danych;
jedną główną akcję.

Nie pokazuj surowych enumów.

Planowane i wykonane sesje

Wyraźnie odróżnij:

zaplanowaną;
rozpoczętą;
ukończoną;
pominiętą;
odwołaną;
nieobecność.

Dla wykonanej sesji pokaż kompaktowo:

Plan: 7 ćwiczeń
Wykonanie: 6 ćwiczeń
RPE: 6/10
Ból: 2/10

Nie ukrywaj brakujących raportów jako zera.

Progresja

Pokaż:

Zmieniono obciążenie
30 kg → 32,5 kg
Powód: wykonanie zgodne przez 3 sesje

Rozróżnij sugestię i zatwierdzoną decyzję.

Cele

Pokaż:

nazwę celu;
poprzednią wartość;
aktualną wartość;
wartość docelową;
status;
jednostkę.

Nie przeliczaj nieporównywalnych celów do wspólnego procentu.

Problemy

Pokaż:

typ;
priorytet;
czas;
status;
neutralne podsumowanie;
dozwoloną akcję.

Kolor nie może być jedynym wyróżnikiem.

ETAP 8 — panel kontekstowy

Kliknięcie zdarzenia:

nie opuszcza kartoteki;
zapisuje eventId w URL;
otwiera panel;
zachowuje scroll timeline’u.

Panel pokazuje:

pełniejszy opis;
effective time;
recorded time;
autora;
powiązany plan;
powiązany cel;
porównanie plan–wykonanie;
problemy;
provenance;
dostępne akcje.

Pełne dokumenty i notatki pobieraj dopiero po jawnej akcji.

Po zamknięciu:

focus wraca do zdarzenia;
eventId znika z URL;
scroll pozostaje bez zmian.
ETAP 9 — szybkie operacje bez zmiany ekranu

Użyj Material Dialog/CDK Overlay/Drawer zgodnie ze standardem repozytorium.

Obsłuż tylko operacje posiadające backendowy przypadek użycia:

dodaj notatkę;
zaplanuj spotkanie;
zleć ćwiczenie;
zleć zestaw;
dodaj pomiar;
potwierdź problem;
rozwiąż problem;
rozpocznij sesję;
utwórz rewizję planu.

Po sukcesie:

nie przeładowuj całej aplikacji;
odśwież workspace i odpowiedni zakres timeline’u;
zachowaj filtry i scroll;
pokaż komunikat przy zmienionym obszarze.

Nie implementuj edycji aktywnej rewizji planu w miejscu. Zmiana ma tworzyć
nową rewizję zgodnie z backendem.

ETAP 10 — filtry

Filtry kategorii:

Cele;
Plany i zalecenia;
Sesje;
Wykonanie;
Postępy;
Problemy;
Notatki;
Dokumenty.

Wymagania:

multi-select;
zapis w URL;
szybkie „Wszystkie”;
liczba aktywnych filtrów;
wyczyść filtry;
nie pobieraj niepotrzebnych kategorii;
nie filtruj dużej historii wyłącznie w pamięci.
ETAP 11 — zakres czasu i agregacja

Kontrolki:

2 tygodnie
3 miesiące
12 miesięcy
Całość

Mapowanie:

2 tygodnie → DETAIL;
3 miesiące → WEEK;
12 miesięcy → MONTH;
Całość → MONTH z paginacją wstecz.

Dla „Całość” nie pobieraj pełnej historii jednym requestem.

Wyraźnie oznacz agregaty:

Podsumowanie tygodnia
Podsumowanie miesiąca

Umożliw rozwinięcie agregatu do szczegółów przez zmianę zakresu, bez tworzenia
osobnego ekranu.

ETAP 12 — ładowanie historii

Użyj kursora zwracanego przez backend.

Nie stosuj CDK Virtual Scroll dla kart o silnie zmiennej wysokości, chyba że
agent wykona spike i potwierdzi poprawność.

Preferuj:

przycisk „Pokaż wcześniejsze”;
albo kontrolowany IntersectionObserver;
zachowanie pozycji scrolla;
deduplikację zdarzeń;
anulowanie nieaktualnych requestów.

Nowe zdarzenia dopisane w tle nie mogą przestawiać użytkownikowi historii bez
jawnego odświeżenia.

ETAP 13 — dostępność

Wymagania:

dokładnie jedno h1;
chronologiczna kolejność zdarzeń w DOM;
nagłówki okresów;
pełna obsługa klawiaturą;
Enter/Space otwiera panel;
Escape zamyka panel;
focus wraca do zdarzenia;
jawne etykiety kategorii i statusów;
aria-live dla zapisów;
minimum 44 px dla działań;
widoczny focus;
WCAG 2.2 AA;
prefers-reduced-motion;
alternatywa Widok listy.

Nie używaj ARIA grid bez pełnego zachowania gridu.

Overview SVG:

posiada tekstowy opis zakresu;
nie jest jedynym źródłem informacji;
znaczniki mają dostępne nazwy;
dekoracyjne linie są aria-hidden.
ETAP 14 — prywatność

UI może renderować wyłącznie dane zwrócone przez backend.

Nie próbuj odgadywać uprawnień na podstawie samej roli.

Dane oznaczone jako podwyższonej wrażliwości:

nie pojawiają się w nagłówku;
nie pojawiają się w tooltipach;
są otwierane jawnie;
nie pozostają w title/aria-label po cofnięciu dostępu;
nie są logowane w konsoli.
ETAP 15 — responsywność

Sprawdź:

1440×900;
1024×768;
768×1024;
390×844;
320×700.

Na mobile:

summary jest zwarte;
quick actions nie przepełniają nagłówka;
timeline pozostaje pionowy;
panel jest pełnoekranowy;
filtry otwierają drawer;
brak poziomego scrolla;
overview może być uproszczony, ale nie usunięty bez alternatywy.
ETAP 16 — wydajność

Widok jest lazy-loaded.

Nie dodawaj biblioteki timeline.

Sprawdź:

rozmiar lazy chunka;
brak wpływu na initial bundle;
stabilne track;
brak obliczeń skali w szablonie;
brak ponownego renderowania wszystkich zdarzeń po otwarciu panelu;
zachowanie dla 500 agregatów i 100 szczegółowych zdarzeń;
brak wycieków timerów i observerów.
ETAP 17 — testy

Dodaj testy dla:

workspace loading/error/empty;
nagłówka klienta;
capability-based actions;
zakresów czasu;
filtrów w URL;
cursor pagination;
sortowania zdarzeń;
grupowania okresów;
overview scale;
markerów celów;
zakresów planu;
planowanych sesji;
wykonanych sesji;
porównania plan–wykonanie;
progresji;
pomiarów;
problemów;
agregatów tygodniowych i miesięcznych;
otwierania panelu;
eventId w URL;
zachowania scrolla;
focusu po zamknięciu;
szybkiej akcji i odświeżenia danych;
trenera bez dostępu klinicznego;
fizjoterapeuty z odpowiednią zgodą;
mobile;
klawiatury;
widoku listy;
braku UUID i surowych enumów;
race conditions po zmianie zakresu i filtrów.
ETAP 18 — runtime QA

Na realnych danych sprawdź klienta posiadającego:

aktywny plan;
kilka celów;
zaplanowane sesje;
wykonane sesje;
pominięcie;
zmianę progresji;
pomiar;
problem;
alert;
notatkę.

Zweryfikuj pełny flow:

wejście z listy klientów;
filtrowanie;
zmiana zakresu;
otwarcie zdarzenia;
szybka akcja;
zachowanie scrolla;
direct URL z eventId;
back/forward;
cofnięcie dostępu;
mobile.

Wykonaj zrzuty:

desktop 2 tygodnie;
desktop 3 miesiące;
wybrany event;
plan kontra wykonanie;
problem;
pusty stan;
error;
mobile 390;
mobile 320;
widoczny focus.
Poza zakresem

Nie implementuj teraz:

gotowej biblioteki timeline;
pełnego edytora planu w timeline;
pełnego wywiadu klinicznego;
edytora dokumentów;
AI summary;
jednego globalnego wyniku postępu;
drag-and-drop zdarzeń;
bezpośredniej edycji historycznych zdarzeń.
Raport końcowy

Podaj:

wykorzystany kontrakt backendowy;
strukturę komponentów;
algorytm overview;
model timeline;
działanie panelu;
szybkie akcje;
dostępność;
responsywność;
listę zmienionych plików;
wyniki testów;
rozmiar lazy chunka;
wyniki runtime;
ścieżki do zrzutów;
końcowy git status;
ocenę ACCEPTABLE / ACCEPTABLE WITH ISSUES / NOT ACCEPTABLE.

Nie wykonuj commita ani push.


**Kolejność:** backend → OpenAPI i klient Angular → frontend → kontrola runtime.
