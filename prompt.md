# MOVES — prompty implementacyjne „adherence-first”

## Zasady wspólne dla wszystkich etapów

Wykonuj prompty sekwencyjnie w sposób ciągły, przechodząc do następnego etapu natychmiast po ukończeniu poprzedniego. Zatrzymaj pracę wyłącznie przy rzeczywistej przeszkodzie wymagającej decyzji użytkownika lub zewnętrznej zmiany stanu. Do każdego etapu stosuj poniższy kontrakt.

### KONTRAKT NADRZĘDNY

Pracujesz bezpośrednio w aktualnym repozytorium `boryspawel/moves`. Masz wykonać rzeczywistą implementację wraz z testami i dokumentacją, a nie jedynie analizę lub plan.

Przed zmianami:

1. przeczytaj wszystkie `AGENTS.md`, `spec.md`, `docs/architecture/technical-overview.md`, istniejące ADR-y oraz dokumenty dotyczące Training Planning, Safety, Training Execution i importu;
2. sprawdź bieżący branch, HEAD i working tree;
3. nie cofaj ani nie nadpisuj zmian użytkownika;
4. zweryfikuj opis zadania z rzeczywistym kodem, migracjami, OpenAPI i frontendem;
5. używaj dostępnych skills oraz MCP IntelliJ do nawigacji, refaktoryzacji, analizy zależności i uruchamiania testów;
6. nie zakładaj nazw klas, endpointów ani tabel bez ich sprawdzenia.

Zasady techniczne:

* Java 25 i Spring Boot 4.1 pozostają bazą;
* przed walidacją Maven aktywuj Java 25 przez SDKMAN zgodnie z instrukcją repozytorium;
* zachowaj modularny monolit, granice modułów i architekturę portów/adaptorów;
* PostgreSQL i Flyway pozostają źródłem prawdy dla DDL;
* migracje są forward-only;
* nowe operacje modyfikujące muszą być autoryzowane zasobowo, audytowane i idempotentne, gdy mogą być ponawiane;
* nie wystawiaj encji JPA w API;
* nie implementuj AI, diagnostyki medycznej ani automatycznej zmiany ograniczeń klinicznych;
* reguły bezpieczeństwa mają pierwszeństwo przed mechanizmami adherence;
* nie twórz osobnych silników dla trenera i fizjoterapeuty;
* nie dodawaj rankingu, sztywnych serii, kar, publicznego feedu ani obowiązkowej gamifikacji;
* nie commituj i nie pushuj bez wyraźnego polecenia.

Po implementacji:

* wykonaj testy domenowe, integracyjne z PostgreSQL, frontendowe i E2E adekwatne do zakresu;
* uruchom pełne walidacje backendu i frontendu;
* zregeneruj klienta OpenAPI, jeśli zmienił się kontrakt;
* sprawdź `git diff --check`;
* wykonaj self-review pod kątem bezpieczeństwa, prywatności, N+1, wyścigów, dostępności i złamania granic modułów.

Raport końcowy ma zawierać:

1. stan repozytorium przed i po pracy;
2. rezultat funkcjonalny;
3. zmienione pliki według modułów;
4. migracje i niezmienniki;
5. wykonane testy;
6. kompromisy i decyzje;
7. niewykonane elementy i ryzyka;
8. jednoznaczną ocenę spełnienia kryteriów zakończenia.

---

# PROMPT 0 — ADR i techniczna rama MVP adherence-first

## Cel

Ustanów obowiązującą ramę architektoniczną dla MVP Moves opartą na czterech momentach: rozpoczęcie, wykonanie, bariera i powrót po przerwie.

## Zadanie

1. Zmapuj obecne możliwości modułów:

   * `trainingplanning`;
   * `trainingexecution`;
   * `safety`;
   * `loadanalysis`;
   * `specialist`;
   * `consent`;
   * `notification`;
   * frontend Angular.
2. Wskaż, które istniejące modele mogą zostać rozszerzone, a gdzie potrzebny jest nowy, cienki moduł orkiestracyjny lub projekcyjny.
3. Nie twórz równoległego modelu planów, wykonań ani alertów.
4. Dodaj ADR opisujący:

   * pozycjonowanie adherence-first;
   * własność danych;
   * przepływ `plan → agenda dnia → próba wykonania → wykonanie/bariera → reakcja → powrót`;
   * rozdzielenie bezpieczeństwa klinicznego od wsparcia adherence;
   * zakaz automatycznej modyfikacji ograniczeń medycznych;
   * brak obowiązkowej gamifikacji;
   * architekturę projekcji uczestnika i worklisty specjalisty.
5. Dodaj dokument etapów implementacji oraz tabelę mapującą wymagania badania na istniejące i brakujące elementy kodu.
6. Zaktualizuj ograniczony zakres dokumentacji technicznej, ale nie wykonuj jeszcze dużych zmian domenowych.

## Kryteria zakończenia

* własność każdej nowej informacji jest jednoznaczna;
* nie powstaje drugi silnik planowania lub wykonania;
* zostały opisane kontrakty między modułami;
* wiadomo, które endpointy i widoki legacy będą później wycofywane;
* dokumentacja nie przedstawia gamifikacji ani funkcji społecznościowych jako rdzenia MVP.

---


# PROMPT 2 — zatwierdzone warianty sesji i „plan minimum”

## Cel

Rozszerz model planu o bezpieczne, wcześniej zatwierdzone warianty sesji: pełny, skrócony i minimum.

## Model

Każda planowana sesja może posiadać:

* wariant `STANDARD`;
* opcjonalny `SHORT`;
* opcjonalny `MINIMUM`.

Wariant zawiera własną uporządkowaną listę recept ćwiczeń albo jawne nadpisania dawki. Musi być częścią wersjonowanej rewizji planu i po aktywacji pozostawać niezmienny.

Nie generuj wariantu automatycznie w czasie wykonania. Specjalista lub autor planu definiuje go wcześniej.

## Reguły

* `MINIMUM` nie może omijać ograniczeń bezpieczeństwa;
* każdy wariant jest oceniany przed aktywacją rewizji;
* wariant skrócony nie jest osobnym planem;
* wykonanie wariantu zachowuje informację, który wariant wybrano;
* wariant nie może samodzielnie zmieniać ćwiczenia na wersję niezatwierdzoną;
* aktywna rewizja pozostaje immutable.

Rozszerz:

* model planowania;
* workflow walidacji i aktywacji;
* snapshot aktywnego planu;
* OpenAPI;
* testy migracyjne i integracyjne.

## Kryteria zakończenia

* standardowa sesja nadal działa bez dodatkowej konfiguracji;
* plan minimum jest opcjonalny;
* wszystkie warianty przechodzą ocenę safety;
* wykonanie może jednoznacznie wskazać wariant;
* nie występuje duplikacja planu ani recept.

---

# PROMPT 3 — prowadzone wykonanie i cykl życia próby sesji

## Cel

Zastąp model „wypełnij formularz po wszystkim” rzeczywistym, prowadzonym cyklem wykonania.

## Model

Dodaj jawny, audytowalny cykl próby wykonania:

* `STARTED`;
* `PAUSED`;
* `RESUMED`;
* `COMPLETED`;
* `ABANDONED`.

Próba ma wskazywać:

* planowaną sesję;
* aktywną rewizję;
* wybrany wariant;
* uczestnika;
* czas rozpoczęcia i ostatniej aktywności;
* postęp po receptach;
* powód zakończenia bez wykonania, jeżeli został podany.

Nie zmieniaj istniejącego finalnego `SessionExecution` w mutowalny rekord. Próba może być mutowalnym agregatem, ale finalne wykonanie pozostaje append-only.

## API uczestnika

Zapewnij operacje:

* rozpoczęcie;
* zapis postępu;
* pauza;
* wznowienie;
* ukończenie;
* zakończenie bez ukończenia.

Wszystkie operacje muszą być idempotentne i kontrolować właściciela zasobu.

Po ukończeniu zbieraj minimalny check-in:

* ból lub reakcja objawowa;
* trudność;
* pewność wykonania techniki.

Nie wymagaj notatki ani szczegółowego dziennika.

## Kryteria zakończenia

* nie można równolegle rozpocząć dwóch aktywnych prób tej samej sesji;
* odświeżenie przeglądarki nie traci postępu;
* ukończenie tworzy dokładnie jedno finalne wykonanie;
* ponowione żądanie nie dubluje danych;
* blokada bezpieczeństwa uniemożliwia start lub kontynuację zgodnie z regułą;
* gamifikacja nie jest wywoływana przez podstawowy flow.

---

# PROMPT 4 — zgłoszenie bariery i deterministyczna ścieżka ratunkowa

## Cel

Zaimplementuj mechanizm „bariera zamiast winy”: użytkownik zgłasza problem jednym działaniem, a system proponuje bezpieczną, z góry określoną odpowiedź.

## Kategorie bariery

Wprowadź wersjonowany słownik obejmujący co najmniej:

* `NO_TIME`;
* `PAIN_OR_SYMPTOMS`;
* `TOO_DIFFICULT`;
* `UNSURE_TECHNIQUE`;
* `FATIGUE`;
* `ILLNESS`;
* `LOGISTICS`;
* `LOW_MOTIVATION`;
* `OTHER`.

Zgłoszenie może dotyczyć sesji przed rozpoczęciem albo aktywnej próby.

## Reguły odpowiedzi

Reguły mają być deterministyczne i wyjaśnialne:

* brak czasu → wariant `SHORT` lub `MINIMUM`;
* zmęczenie → wariant zatwierdzony albo przeplanowanie;
* niepewna technika → zatrzymanie ćwiczenia i prośba o kontakt;
* zbyt trudno → zatwierdzony wariant minimum albo zgłoszenie specjaliście;
* ból lub objawy → reakcja zgodna z safety, bez diagnozowania;
* choroba → pauza lub przeplanowanie;
* niski poziom motywacji → plan minimum, przeplanowanie albo rezygnacja na dziś bez kary.

Zapisuj:

* zgłoszoną barierę;
* zaproponowane opcje;
* wybraną akcję;
* wynik akcji;
* wersję zastosowanej reguły.

## Zakazy

* brak komunikatów zawstydzających;
* brak utraty serii;
* brak automatycznej zmiany ograniczeń;
* brak dowolnie generowanych porad medycznych.

## Kryteria zakończenia

* jedno zgłoszenie tworzy jeden rekord;
* każda odpowiedź jest odtwarzalna z wersji reguły;
* użytkownik może wybrać kontakt zamiast automatycznej opcji;
* ból i niepewność techniki mogą utworzyć sygnał dla specjalisty;
* brak czasu samodzielnie nie generuje pilnego alertu.

---

# PROMPT 5 — bezpieczny powrót po przerwie

## Cel

Zaimplementuj powrót po przerwie jako pełnoprawny przypadek użycia, bez resetowania postępu i komunikowania porażki.

## Model


RecoveryEpisode powinien być agregatem w module adherence, nie tylko projekcją. Projekcja jest potrzebna wyłącznie do zasilania ekranu „Dzisiaj”.

Nie należy rozszerzać BarrierReport:

BarrierReport dotyczy pojedynczego problemu przy konkretnej sesji;
RecoveryEpisode opisuje okres przerwy, wybór ścieżki oraz pierwszy powrót;
BARRIER_RESPONSE_V1 pozostaje osobną polityką;
powstaje nowa polityka RECOVERY_RETURN_V1.
Model domenowy

RecoveryEpisode:

id
participantAccountId
status: OPEN | RETURN_IN_PROGRESS | RESOLVED | CLOSED
policyVersionCode
openedAt
participantTimeZone
detectedLocalDate
planRevisionIdAtOpening
primaryTrigger
knownReason
sourceBarrierReportId?
gapDays
missedWindowCount
lastSessionStartedAt?
selectedPath?
selectedAt?
targetPlannedSessionId?
returnAttemptId?
returnStartedAt?
returnLocalDate?
firstSessionExecutionId?
firstSessionOutcome: COMPLETED | ABANDONED
resolvedAt?
version

Dodatkowe encje:

RecoveryEpisodeEvidence — przesłanki uruchomienia epizodu;
RecoveryOffer — migawka aktualnego planu, safety i wygenerowanych opcji;
RecoveryOfferOption — uporządkowane opcje;
RecoveryChoice — append-only historia wyborów użytkownika.

W bazie należy zagwarantować maksymalnie jeden aktywny epizod na uczestnika przez częściowy indeks unikalny.

Polityka RECOVERY_RETURN_V1

Rekomendowane progi startowe, zapisane jako niezmienny rekord w adherence.recovery_policy_version:

Parametr	Wartość
brak rozpoczęcia	3 dni
kolejne niewykonane okna	2
analiza regularności	28 dni
minimalna liczba wcześniejszych rozpoczęć	3
krótka przerwa	3–6 dni
średnia przerwa	7–13 dni
długa przerwa	≥14 dni

Epizod otwiera się, jeśli zachodzi przynajmniej jeden warunek:

co najmniej dwa zakończone okna bez rozpoczęcia;
co najmniej trzy lokalne dni bez rozpoczęcia i wystąpiło przynajmniej jedno zakończone okno;
wcześniejsze minimum trzy rozpoczęcia w 28 dniach, a aktualna przerwa przekroczyła dwukrotność mediany odstępu, nie mniej niż trzy dni;
zgłoszono ILLNESS albo PAIN_OR_SYMPTOMS.

Pojedyncze opuszczenie nie tworzy sygnału specjalisty. Prompt 5 w ogóle nie powinien tworzyć automatycznych alertów — kontakt powstaje wyłącznie po świadomym wyborze CONTACT_SPECIALIST.

Reguły rekomendacji
Sytuacja	Dozwolone opcje
3–6 dni	minimum, skrócona, przeplanowanie, kontakt
7–13 dni	minimum jako główna, skrócona, przeplanowanie, kontakt
≥14 dni	minimum, przeplanowanie, kontakt
brak zatwierdzonego minimum	nie pokazuj minimum
brak zatwierdzonego short	nie pokazuj short
safety BLOCKED lub NOT_ASSESSED	tylko przeplanowanie i kontakt
safety REQUIRES_REVIEW	rekomenduję bez uruchamiania sesji do zakończenia przeglądu
choroba lub pogorszenie	wykonanie dopiero po ocenie safety nowszej niż zgłoszenie

W żadnym aktywnym epizodzie nie wolno proponować STANDARD.

Brakujące porty

Obecny SessionExecutionProgressQueryPort obsługuje tylko podane identyfikatory sesji. Należy dodać:

ParticipantExecutionHistoryQueryPort

Z ograniczonym przedziałem czasu, zwracający:

próbę i jej stan;
wariant;
startedAt, completedAt, abandonedAt;
powód przerwania;
finalne wykonanie istniejące bez próby.

Moduł planning powinien wystawić:

ParticipantPlanWindowHistoryQueryPort

Zwracający zakończone okna również z historycznych rewizji, z informacją o anulowaniu, zastąpieniu albo świadomym przeplanowaniu. Sam aktywny PlanRevisionQueryPort jest niewystarczający.

Wykrywanie

Nie wykonywać zapisu podczas GET /today.

RecoveryDetectionService powinien być uruchamiany:

po BarrierReport;
po rozpoczęciu lub zakończeniu próby;
po finalnym wykonaniu;
po aktywacji nowej rewizji planu;
okresowo przez ograniczony, stronicowany job wykrywający zakończone okna.

Operacja musi być idempotentna i odporna na równoległe uruchomienia.

Integracja z wykonaniem

SessionExecutionAttemptService obsługuje już STANDARD, SHORT, MINIMUM i sprawdza safety, ale można go ominąć, żądając STANDARD.

Należy dodać port należący do execution:

SessionStartAuthorizationPort

Jego implementacja kompozycyjna sprawdza aktywny RecoveryEpisode. Dzięki temu:

bezpośrednie wywołanie startu także nie ominie ograniczeń;
training execution nie importuje bezpośrednio modułu adherence;
nie powstaje cykliczna zależność modułów.

Wybór START_MINIMUM lub START_SHORT i utworzenie próby powinny być jedną operacją aplikacyjną. Epizod zapisuje returnAttemptId, ale nie modyfikuje historii wykonania ani planu.

Projekcja „Dzisiaj”

Obecny TodayAgendaService powinien otrzymać pole najwyższego poziomu:

recovery:
episodeId
state
messageCode
policyVersion
openedAt
gapDays
targetSessionId?
offerId
options[]
selectedPath?
returnState?

Recovery nie może być udawane jako zwykła sesja. Gdy epizod jest aktywny, nextAction sesji nie może zwracać START_SESSION.

Backend zwraca neutralne messageCode; polskie treści tworzy frontend, np. „Dobrze, że wracasz. Zacznijmy spokojnie”.

API
GET  /api/v1/participant/today
GET  /api/v1/participant/recovery-episodes/current
POST /api/v1/participant/recovery-episodes/{id}/choices

POST choices wymaga Idempotency-Key, offerId i wersji agregatu. Nieaktualna oferta po zmianie planu lub safety zwraca 409 RECOVERY_OFFER_STALE wraz z aktualną projekcją.

RESCHEDULE zapisuje prośbę — nie zmienia po cichu niezmiennej rewizji planu. CONTACT_SPECIALIST wykorzystuje istniejący mechanizm sygnałów, rozszerzony o źródło RECOVERY_EPISODE.

Minimalne testy
dokładnie 3, 7 i 14 dni;
dwa niewykonane okna;
pojedyncze opuszczenie bez alertu;
przerwanie wcześniejszej regularności;
choroba i safety starsze niż zgłoszenie;
wszystkie cztery stany safety;
brak wariantu minimum lub short;
próba obejścia przez start STANDARD;
zmiana aktywnej rewizji;
strefa czasowa i DST;
równoległe wykrycie bez dwóch epizodów;
idempotentny wybór;
ukończenie i porzucenie pierwszej sesji;
potwierdzenie, że historia i postęp nie zostały usunięte ani wyzerowane.

Jedyną decyzją produktową wymagającą zatwierdzenia jest przyjęcie powyższej macierzy 3/7/14; rekomenduję ją jako bezpieczny wariant MVP, z późniejszą recenzją specjalisty medycznego.
---

# PROMPT 6 — niskoszumowa worklista specjalisty i kontakt z człowiekiem

## Cel

Zastąp listę surowych wykonań i alertów worklistą wymagającą decyzji specjalisty.

## Worklista

Utwórz projekcję autoryzowanych uczestników i elementów wymagających uwagi:

* nasilające się objawy;
* niepewność techniki;
* powtarzające się bariery;
* kilka nieudanych prób;
* brak powrotu po przerwie;
* niedopasowanie planu;
* wymagany post-24h follow-up.

Każdy element ma posiadać:

* kategorię;
* priorytet wyznaczony regułą;
* wyjaśnienie powodów;
* istotne, minimalne dane;
* stan `OPEN`, `ACKNOWLEDGED`, `SNOOZED`, `RESOLVED`;
* powiązanie z uczestnikiem i planem;
* wersję polityki.

Nie pokazuj pełnej historii medycznej na liście.

## Minimalna komunikacja

Zaimplementuj prostą komunikację asynchroniczną:

* uczestnik zgłasza ustrukturyzowany problem i opcjonalny krótki tekst;
* specjalista odpowiada;
* nie twórz pełnego komunikatora, statusu online, załączników ani grup;
* kontroluj aktywną relację, capability, zgodę i purpose.

## Kryteria zakończenia

* specjalista nie wpisuje ręcznie UUID uczestnika;
* widzi tylko uczestników objętych aktywną relacją;
* jeden wzorzec problemu nie produkuje wielu duplikatów;
* każdy element worklisty wyjaśnia, dlaczego powstał;
* można zmierzyć, czy element był użyteczny;
* dane kliniczne nie trafiają do neutralnych powiadomień.

---

# PROMPT 7 — frontend uczestnika „tylko dzisiaj” i dostępny shell

## Cel

Przebuduj frontend uczestnika wokół jednego ekranu dziennego i prowadzonego wykonania.

## Zakres

Dodaj główną ścieżkę uczestnika:

`Dzisiaj → wybór pełna/krótsza/minimum → prowadzone wykonanie → check-in → wynik`

Na ekranie głównym umieść:

* jedno dominujące działanie;
* czas i sens sesji;
* opcjonalny wariant krótszy;
* przycisk „Mam problem”;
* prosty postęp tygodniowy;
* kontakt ze specjalistą;
* komunikat powrotu po przerwie.

Nie pokazuj identyfikatorów technicznych, wersji ćwiczeń ani surowych statusów domenowych.

## Dostępność

Interfejs domyślnie ma być dostępny, a uproszczony shell ma wynikać z ustawienia potrzeb użytkownika, nie z wieku.

Wymagania:

* tekst bazowy uproszczonego shellu 18–20 px;
* cele dotykowe co najmniej 44×44 px;
* zgodność WCAG 2.2 AA;
* poprawny reflow i zoom 200%;
* pełna obsługa klawiaturą;
* etykiety tekstowe obok ikon;
* czytelny focus;
* redukcja animacji;
* bezpieczne cofnięcie operacji;
* komunikaty błędów mówiące, co zrobić dalej;
* nie więcej niż jedno dominujące działanie na ekranie.

Usuń gamifikację z domyślnej nawigacji uczestnika. Może pozostać wyłącznie jako oddzielna funkcja opt-in.

## Kryteria zakończenia

* pierwszy trening można uruchomić bez konfiguracji dodatkowych danych;
* wznowienie działa po odświeżeniu;
* zgłoszenie bariery wymaga maksymalnie dwóch działań;
* dostępność jest pokryta testami komponentowymi i E2E;
* istnieją testy dla mobile viewport, zoomu, klawiatury i reduced motion.

---

# PROMPT 8 — panel specjalisty V2 i plan builder oparty na aktywnych kontraktach

## Cel

Usuń z głównych flow specjalisty zależność od formularzy legacy i surowych identyfikatorów.

## Plan builder

Przebuduj obecny ekran planu tak, aby korzystał z:

* Training Planning V2;
* rewizji;
* walidacji strukturalnej;
* safety assessment;
* acknowledgement;
* atomowej aktywacji.

UI ma umożliwiać:

* wybór uczestnika z aktywnych relacji;
* wybór ćwiczeń z katalogu;
* ustawienie celu funkcjonalnego lub treningowego;
* utworzenie sesji i elastycznego okna;
* utworzenie wariantów `STANDARD`, `SHORT`, `MINIMUM`;
* podgląd problemów bezpieczeństwa;
* aktywację dopiero po wymaganych potwierdzeniach.

Nie wymagaj ręcznego kopiowania UUID.

## Worklista

Dodaj:

* listę elementów wymagających uwagi;
* filtrowanie według rodzaju i priorytetu;
* widok minimalnego kontekstu uczestnika;
* odpowiedź na zgłoszenie;
* rozwiązanie, odroczenie i potwierdzenie elementu.

## Zakazy

* bez kalendarza wizyt, billing, pełnego CRM i rozbudowanych raportów;
* bez masowego dashboardu wszystkich możliwych danych;
* bez automatycznej zmiany planu na podstawie samego alertu.

## Kryteria zakończenia

* podstawowy plan można utworzyć bez surowych identyfikatorów;
* aktywacja używa wyłącznie workflow V2;
* legacy endpoint nie jest używany przez nowy ekran;
* błędy optimistic locking są obsługiwane czytelnie;
* panel działa na desktopie i tablecie.

---

# PROMPT 9 — przypomnienia rules-first i kontrola zmęczenia powiadomieniami

## Cel

Zaimplementuj minimalny system przypomnień wspierający wykonanie, bez generowania presji i nadmiaru komunikatów.

## Model

Użytkownik może ustawić:

* preferowane okno przypomnienia;
* kanał;
* wyciszenie;
* maksymalną częstotliwość;
* brak przypomnień;
* zgodę na przypomnienie o powrocie po przerwie.

Reguły:

* nie przypominaj po ukończeniu lub świadomym przeplanowaniu;
* nie wysyłaj kilku komunikatów dotyczących tej samej sesji;
* po zgłoszeniu bólu nie wysyłaj standardowego motywacyjnego reminderu;
* po przerwie wysyłaj komunikat o łatwym powrocie, nie o zaległości;
* respektuj quiet hours i strefę czasową;
* każde powiadomienie musi mieć wersjonowany reason code;
* specjalista nie otrzymuje wiadomości o każdym pominięciu.

Zacznij od deterministycznych reguł. Nie implementuj predykcyjnego wyboru czasu.

## Kryteria zakończenia

* deduplikacja i idempotencja dostarczeń;
* pełny audyt decyzji bez zapisywania w treści danych medycznych;
* użytkownik może wyłączyć powiadomienia;
* metryka liczby komunikatów na aktywnego użytkownika;
* testy stref czasowych, quiet hours, przeplanowania i bólu.

---

# PROMPT 10 — metryki adherence, eksperymenty i końcowy audyt vertical slice

## Cel

Zaimplementuj pomiar rzeczywistego wykonywania ćwiczeń i przeprowadź końcowy audyt całego MVP adherence-first.

## Metryki

Zdefiniuj i wyliczaj co najmniej:

* ukończenie pierwszej sesji w 72 godziny;
* odsetek tygodni z wykonanym planem minimum;
* wykonanie pełnej zaleconej dawki;
* `salvage rate` wariantów krótszych i minimum;
* powrót po przerwie w 7 i 14 dni;
* czas od pierwszego niewykonania do rezygnacji;
* aktywność po 4, 8, 12 i 24 tygodniach;
* liczbę alertów na uczestnika;
* odsetek alertów uznanych za użyteczne;
* czas pracy specjalisty związany z worklistą;
* skuteczność wykonania głównych zadań w uproszczonym shellu.

Nie traktuj czasu w aplikacji, liczby kliknięć ani otwarć jako głównej wartości.

## Prywatność

* analityka nie może zawierać notatek klinicznych ani swobodnego tekstu;
* używaj neutralnych kodów zdarzeń;
* zapewnij retencję i możliwość usunięcia danych zgodnie z polityką;
* nie eksportuj danych do zewnętrznego SaaS bez osobnej decyzji.

## Eksperymenty

Dodaj minimalny, deterministyczny mechanizm przypisania wariantu eksperymentu, początkowo dla:

* klasycznego przypomnienia kontra barrier-first;
* zwykłego dashboardu kontra today-only;
* dostępności planu minimum.

Eksperyment nie może zmieniać safety ani ograniczeń medycznych.

## Audyt końcowy

Zweryfikuj pełny przepływ:

1. specjalista tworzy i aktywuje plan;
2. uczestnik widzi „Dzisiaj”;
3. rozpoczyna i kończy sesję;
4. wybiera plan minimum;
5. zgłasza barierę;
6. wraca po przerwie;
7. specjalista otrzymuje jeden użyteczny element worklisty;
8. uczestnik otrzymuje odpowiedź;
9. metryki zapisują poprawny wynik.

Usuń lub oznacz jako deprecated nieużywane kontrakty legacy, ale nie wykonuj destrukcyjnego usunięcia bez dowodu braku klientów.

## Kryteria zakończenia

* pełne E2E przechodzi na Docker Compose;
* backend, frontend, OpenAPI, Flyway i smoke test są zielone;
* brak obowiązkowej zależności od gamifikacji;
* brak surowych UUID w podstawowych flow UI;
* brak powiadomień i alertów bez uzasadnionej następnej decyzji;
* dokumentacja opisuje rzeczywisty stan po implementacji;
* powstaje końcowy raport techniczny z listą elementów `MVP`, `LATER`, `EXPERIMENT`, `DO_NOT_BUILD`.
