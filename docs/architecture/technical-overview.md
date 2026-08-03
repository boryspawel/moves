# Dokumentacja techniczna `moves`

Status: dokument rozwijany wraz z migracją. `spec.md` jest źródłem nadrzędnym i nie jest przez ten dokument zastępowany. Dla adherence bieżący zakres określa [`prompt.md`](../../prompt.md); starszy `docs/moves-codex-implementation-prompts-training.md` ma inną numerację i nie jest roadmapą statusową.

## Kontekst i topologia

`moves` powstaje jako modularny monolit Java 25 / Spring Boot 4.1 z PostgreSQL i Flyway. Klienci komunikują się wyłącznie przez wersjonowane REST API `/api/v1` i `/api/v2` opisane OpenAPI. Keycloak jest zewnętrznym dostawcą OIDC; backend nie implementuje logowania ani refresh tokenów.

Początkowe deploymenty:

1. `application` — bezstanowy backend Spring MVC i Resource Server;
2. `web` — Angular 22 budowany i wdrażany osobno;
3. lokalna infrastruktura — PostgreSQL i Keycloak w Docker Compose.

## Granice backendu

Kod produkcyjny używa neutralnego prefiksu `com.motionecosystem`. Moduł jest głównym pakietem z publicznym API aplikacyjnym i niewystawianymi bezpośrednio szczegółami `domain`, `application`, `infrastructure`, `api`.

- `identityaccess`: mapowanie `sub`, status konta i role techniczne;
- `participant`: profil uczestnika;
- `specialist`: profil specjalisty, relacja do uczestnika oraz worklista adherence z participant issue/reply i aktywną deduplikacją;
- `calendar`: terminy specjalisty i ich idempotentne komendy; moduł `specialist` składa z nich ograniczony widok `Today`, lecz nie przejmuje własności danych terminu;
- `consent`: wersje dokumentów, granty, wycofanie i ważność;
- `availability`: cykliczne przedziały i przyszłe wyjątki;
- `anatomyreference`: wersjonowana taksonomia struktur anatomicznych;
- `exercisecatalog`: ćwiczenia, niezmienne wersje i publikacja;
- `exerciseimport`: artefakty JSONL, staging, mapowania, deduplikacja i przekazanie szkicu do katalogu;
- `trainingplanning`: cel, owner, collaborators, rewizja, cykl, mikrocykl, sesja i recepta;
- `loadanalysis`: wersjonowane profile planned i executed load bez globalnego score;
- `planworkflow`: walidacja, acknowledgement i atomowa aktywacja rewizji;
- `trainingexecution`: append-only wykonanie i dawka rzeczywista, projekcje, raport bólu/post24h, alerty i korekty;
- `safety`: wersjonowane ograniczenia, niezmienne assessmenty i jawne override;
- `adherence`: `TodayAgenda`, wersjonowana bariera, recovery episode/offer/choice oraz neutralne sygnały do specjalisty;
- `analytics.adherencemetrics`: wewnętrzne, neutralne zdarzenia adherence, trwałe deterministyczne assignmenty trzech eksperymentów i retencja 180 dni;
- `notification.reminders`: preferencje uczestnika, deterministyczne reason codes i neutralny audit/dedupe dostarczeń `IN_APP`;
- `gamification`: opt-in i append-only ledger bez danych medycznych;
- `audit`: istotne operacje i dostęp do danych wrażliwych.

## Reguły zależności

- Gamifikacja otrzymuje wyłącznie neutralne zdarzenie kwalifikacji wykonania; nie zależy od encji safety ani medycznych DTO.
- Plan wskazuje identyfikator konkretnej opublikowanej wersji ćwiczenia.
- Import nie zapisuje wersji opublikowanych; cross-source match zawsze wymaga decyzji człowieka.
- Execution wskazuje planowaną sesję i receptę, a korekta dopisuje rekord.
- Uprawnienia zasobowe są sprawdzane w przypadkach użycia, nie tylko w route guards lub rolach tokenu.
- Operacja specjalisty wskazuje jawny kontekst zawodowy i purpose; capability, relacja i zgoda są sprawdzane centralnie przy każdym dostępie.
- Worklista i neutralne notifications przenoszą minimalne kody, nie pełną historię ani clinical rationale; system nie podejmuje automatycznych decyzji klinicznych.
- Każdy moduł posiada własny schemat/tabele i nie odczytuje repozytorium innego modułu bez jawnego portu.

## Dane i czas

- PostgreSQL jest jedynym źródłem prawdy; migracje są liniowe i forward-compatible.
- Identyfikatory domenowe są UUID; `sub` Keycloak pozostaje tekstową referencją zewnętrzną.
- Chwile są zapisywane jako UTC, a strefa IANA obok cyklicznych przedziałów.
- `V034__create_specialist_calendar` tworzy schemat `calendar`, terminy i ich klucze idempotencji; `V035__add_specialist_profile_time_zone` utrwala wymaganą strefę IANA profilu specjalisty (dla istniejących profili deterministyczne `UTC`).
- `V036__add_participant_records` wprowadziła kanoniczny rekord uczestnika i
  opcjonalne powiązanie z kontem (`participant_record` i
  `participant_access_link`). `V037__stabilize_client_create_idempotency`
  utrwala fingerprint tworzenia kartoteki i indeks aktywnej relacji; replay z
  odmiennym payloadem, jak również historyczny replay bez payloadu, jest
  odrzucany. `participantId` jest granicą nowych ścieżek kartoteki, a link
  dostępu jest wyłącznym przejściem do konta. Moduły nieprzeniesione korzystają
  wyłącznie z kontrolowanych, lokalnych mostów legacy `*AccountId`.
- `GET /api/v1/specialist/today?date=YYYY-MM-DD` wyznacza dzień w utrwalonej strefie specjalisty i zwraca terminy, dostępność oraz sprawy worklisty; wolne sloty są wyliczane z dostępności pomniejszonej o zajęte terminy przy skonfigurowanym kroku. Dla bieżącego dnia slot jest możliwy do działania tylko, gdy leży w przyszłości; `operationalTasks` jest obecnie zwracane jako puste.
- Cykliczne okna dostępności nie są slotami terminów. Frontend przy tworzeniu terminu domyślnie proponuje 60 minut i oferuje 15/30/45/60/90/120 minut albo własny czas; koniec jest wyliczany automatycznie, dopóki specjalista nie nadpisze go ręcznie. Dokładne terminy mogą zaczynać się o dowolnej porze i mieć dowolny czas trwania, lecz muszą w całości mieścić się w odpowiednim oknie dostępności.
- Nakładające się cykliczne okna dostępności są akceptowane i sygnalizowane w UI jedynie jako miękkie ostrzeżenie. Konflikt aktywnych terminów pozostaje twardą regułą serwera (`409`); terminy anulowane nie blokują nowego terminu.
- `POST /api/v1/specialist/appointments`, `PUT /api/v1/specialist/appointments/{id}`, `POST /api/v1/specialist/appointments/{id}/cancel` i `POST /api/v1/specialist/appointments/{id}/no-show` wymagają `Idempotency-Key`.
- Operacje podatne na retry używają klucza idempotencji i unikalnego ograniczenia w bazie.

## Bezpieczeństwo i prywatność

- OAuth2 Resource Server waliduje issuer i audience; role Keycloak są mapowane do `ROLE_*`.
- Health i kontrakt OpenAPI mogą być publiczne; domenowe API domyślnie wymaga tokenu.
- Endpointy `Today` i komendy terminów są dostępne wyłącznie dla `SPECIALIST`; widok `Today` pobiera wyłącznie terminy specjalisty oraz uczestników z jego aktywną relacją, a wyświetlana worklista pozostaje ograniczona do minimalnych danych. Dialog tworzenia terminu udostępnia tylko aktywnych uczestników, tworzy termin idempotentnie i przekazuje jednoznaczne konflikty oraz błędy. Brak dostępności kieruje do onboardingu jej edycji z preładowaniem istniejących slotów.
- Kartoteka klienta jest dostępna także bez konta uczestnika. Brak konta ogranicza
  wyłącznie akcje self-service uczestnika; nie wyłącza specjalistycznego
  workspace, planowania terminu ani prowadzenia planu.
- Frontend specjalisty korzysta z wygenerowanych endpointów kartoteki,
  workspace i timeline. Lista klientów oraz workspace prezentują dane z
  publicznych portów modułów bez własnych, ręcznie utrzymywanych modeli
  kontraktu; `Today` zachowuje tę samą granicę `participantId`.
- Workspace i timeline wykonują odczyty w granicy `participantId` po centralnym
  sprawdzeniu capability, aktywnej relacji i zgody w kontekście zawodowym.
  Timeline nie jest źródłem danych: kompozycja obejmuje wyłącznie dozwolone
  projekcje spotkań, planowanych sesji i wykonań. UI steruje zakresem (2 tygodnie,
  3 miesiące, 12 miesięcy), typami, widokiem osi/listy i wybranym zdarzeniem przez
  URL; panel szczegółów używa etykiet prezentacyjnych i nie pokazuje UUID ani
  nieznanych kodów jako samodzielnych nazw. Wybór „następnego spotkania” jest
  czasowo świadomy: obejmuje nieukończone `SCHEDULED`, przyszłe `CONFIRMED` oraz
  trwające `IN_PROGRESS`, z wyłączeniem statusów końcowych.
- Ból, ograniczenia, wywiad i notatki nie trafiają do gamifikacji ani publicznego profilu.
- Trener widzi wyłącznie effective safety envelope. Clinical rationale jest osobnym widokiem fizjoterapeuty objętym osobną zgodą.
- Collaborator planu ma jawny zakres, który nie zastępuje kontroli capability, relacji i consent.
- System zapisuje deklarację, regułę i alert, ale nie diagnozuje i nie generuje planu medycznego.

## Testowanie

- testy domenowe dla niezmienników;
- testy architektury dla granic pakietów/modułów;
- integracyjne MockMvc + prawdziwy PostgreSQL w Testcontainers;
- walidacja Flyway i `ddl-auto=validate`;
- kontrakt OpenAPI, frontendowe testy komponentów i główny Playwright E2E.

Lokalne cele administracyjne Flyway są dostępne przez `bin/flyway-migrate` i
`bin/flyway-repair`; odczytują parametry z `.env` przez profil Maven `local`.
Naprawa historii Flyway nie jest zwykłym krokiem wdrożenia. Aktualne testy
frontendowe pokrywają funkcje workspace i `Today`; nadal wymagane jest E2E dla
mobile viewport, zoomu 200%, klawiatury i reduced motion.

P6 worklisty jest w `d004a36`. P7 `/sessions` ma niecommitowane testy
komponentowe, lecz nadal wymaga E2E dla mobile viewport, 200% zoomu, klawiatury
i reduced motion. P9 ma reguły timezone/quiet-hours, opt-out, suppression i
neutralny audit. P10 dostarcza `analytics.adherencemetrics` w V032: wyłącznie
techniczne identyfikatory, kody zdarzeń/reguł/wariantów i czas; rekordy wygasają
po 180 dniach, a automatyczny, codzienny cleanup wywołuje wewnętrzny job przez
`purgeExpired()`.
# Participant records (test vertical)

Participant records are separate from access accounts. The generated specialist
client API and Angular flow create, list, open, update and archive account-free
records using `participantId`; workspace and timeline use the same identifier.
`participant.participant_access_link` represents the optional account boundary.
The explicit `TEST_DEFAULT` override is available only in `local` or `test`,
never in `prod`; see [test participant-record consent debt](../test-participant-record-consent-debt.md).

`client-result-observation-model.md` pozostaje osobnym dokumentem przyszłego etapu
CLIENT-UX-01; nie opisuje funkcji dostarczonej w workspace.
