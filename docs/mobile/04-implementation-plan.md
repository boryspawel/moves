# Moves Mobile — plan wdrożenia v0.1

**Cel:** natywna aplikacja Android realizująca UJ-01…UJ-06 w zakresie tekstowych notatek i wizyt, oparta na istniejącym backendzie. **Status 2026-11-18:** M2–M4 w kodzie; M5: MOB-050 (S06) i MOB-051 (S09) w kodzie, MOB-052 E2E otwarty. S04 odroczony; S08 pomiary wstrzymane. OIDC login/GET/logout/ponowny login zweryfikowane na Motoroli; odbiór pełnego procesu, refresh, 401/403 i ról pozostaje otwarty.

## Kamienie milowe i kolejność

| Etap | Zakres | Wynik / bramka odbioru |
|---|---|---|
| **M0 — kontrakty i ryzyka** | **Analiza statyczna API/OIDC/danych wykonana 2026-11-12**, bez implementacji | **częściowo**: brak klienta Android, logowania/emulatora i spike generatora; zob. 05-m0-assessment.md |
| **M1 — fundament Android** | Kotlin/Compose, design tokens, nawigacja, auth, klient HTTP/OpenAPI, stany błędów | APK, bezpieczny login/logout, podstawowe smoke UI |
| **M2 — czytanie i organizacja** | S01 Dzień, S02 Uczestnicy, S03 Kartoteka; S04 odroczony | UJ-01, UJ-02, UJ-06; role, dostęp i klient bez konta |
| **M3 — spotkanie** | S05, terminy i ich lifecycle, szybkie planowanie, wznowienie, 409/retry | rozpoczęty termin z backendem i poprawne konflikty |
| **M4 — dokumentacja** | S07 Wywiady i notatki z informacji ustnych; serwerowe szkice/finalizacja; **S08 wstrzymany** | UJ-04 bez pomiarów; immutable finished, bez danych medycznych na dysku telefonu |
| **M5 — zapis wykonania i closeout** | S06 fakty realizacji, S09 warunki i zakończenie | UJ-03, UJ-05; pełny end-to-end Dzień → Zamknięcie |
| **M6 — hardening i pilotaż** | MASVS, dostępność, tablety, releasy, obserwowalność bez PHI | dopuszczenie pilota; checklista bezpieczeństwa i UX |

M1 wymaga M0; M2 wymaga M1; M3 i M4 można częściowo rozwijać równolegle po M2; M5 wymaga M3 i M4. Dopiero po M5 planować M6.

## Status bramki MOB-004 — 2026-11-12

**NO-GO dla pełnego generowanego klienta Kotlin.** Spike pełnego klienta Kotlin z aktualnego snapshotu OpenAPI nie kompiluje się w trzech wspieranych konfiguracjach OpenAPI Generator 7.24.0, a wynik powtórzono na 7.26.0 z aktualnymi stabilnymi zależnościami. Wspólny problem dotyczy modelu `Dose` (`oneOf` razem z `allOf`): generator tworzy niespójne dziedziczenie sześciu wariantów. Nie zmieniono backendu ani kontraktu. M1 zrealizował ograniczony fundament Android z ręcznie typowanym adapterem czterech odczytów; pełny klient, auth i ekrany biznesowe pozostają poza jego zakresem. Szczegóły, polecenia i warianty dalszej pracy opisuje [MOB-004](06-mob-004-openapi-generator.md).

## Backlog do wdrożenia

### M0 — najpierw

- **MOB-001** Audyt mapowania S01–S09 do aktualnego OpenAPI (`web/openapi/openapi.json`), pola `availableActions`, kody błędów, autoryzacja, wersjonowanie, Idempotency-Key.
- **MOB-002** Publiczny klient Android w Keycloak: App Links/redirect, Authorization Code + PKCE, issuer, audience, role; test odbioru tokenu przez backend bez osłabiania walidacji.
- **MOB-003** **Ustalenie M0:** bez trwałych lokalnych szkiców/cache PHI oraz bez uploadu dokumentacji badań i skanów. Nie tworzyć klientowego systemu retencji, zgód ani zaszyfrowanej bazy medycznej.
- **MOB-004** Krótki spike Kotlin OpenAPI generator + HTTP: UUID, Instant, `oneOf`, enum, Problem Details, 409; minimalna poprawka kontraktu wyłącznie jeśli konieczna.
- **MOB-005** Potwierdzenie warunków produktu: onboarding specjalisty web-only w MVP, granice Spraw z istniejących źródeł, zakres katalogu i planów tylko do odczytu.

### M1 — baza

- **MOB-010** Moduł `android` Gradle/Kotlin/Compose, konfiguracje dev/prod, podstawowe lint/compile CI.
- **MOB-011** Logowanie/wylogowanie/odnowienie sesji i token handling; prawdziwy emulator + test 401/403.
- **MOB-012** Typowane API, repository, error mapping, retry i idempotency keys bez logowania medycznych payloadów.
- **MOB-013** Navigation host z Dniem/Uczestnikami/Sprawami, kontekst uczestnika/terminu i standardy loading/empty/error.

### M2 — fundament biznesowy

- **MOB-020 (kod wykonany)** S01: Today, agenda, aktualne/przyszłe terminy i minimalne sprawy; odbiór na fizycznym telefonie pozostaje otwarty.
- **MOB-021 (kod wykonany)** S02: lista aktywnych kartotek, wyszukiwanie lokalne i formularz dodania kartoteki bez konta; test urządzenia i warunki produkcyjnej zgody pozostają otwarte.
- **MOB-022 (kod wykonany)** S03: uprawniony odczyt workspace, cele, aktywny plan i paginowany timeline; odbiór na telefonie otwarty.
- **MOB-023 — ODROCZONE decyzją produktową:** odrębny ekran S04 „Sprawy” nie jest uzasadniony; sygnały i zaległości pozostają w Dniu. Bez mapowania navigationReference przed decyzją UX.
- **MOB-024 (kod wykonany)** Ponowna weryfikacja profilu zawodowego z backendu i usuwanie danych RAM na resume/zmianę kontekstu oraz przy 403. Rola wynika z profilu serwerowego, nie z lokalnego przełącznika.

### M3 — cykl spotkania

- **MOB-030 (kod wykonany)** S05: autoryzowany kontekst wizyty, serwerowy stan lifecycle, rozpoczęcie z wersją i idempotencją, ponowne otwarcie wizyty w toku.
- **MOB-031 (kod wykonany)** Dodanie/zmiana terminu w Androidzie, wolne sloty z Today, optimistic version, Idempotency-Key i obsługa 409.
- **MOB-032 (kod wykonany)** Kontekstowe przejścia S05 → S06/S07/S09 oraz S03 → S07; ponowna autoryzacja kontekstu, bez utraty participantId. Bez pobierania pomiarów S08.

### M4 — dokumentacja

- **MOB-040 (kod wykonany; odbiór lokalny otwarty)** S07: GET/POST wywiadu, sekcje i odpowiedzi, edycja RAM, wersjonowany PUT szkicu, zakończenie POST oraz kolejny wywiad, 409 i jawne ponowienie z Idempotency-Key. Słowniki wyborów przejęte z istniejącego webowego UI; docelowo powinny znaleźć się w kontrakcie backendu.
- **MOB-041 (kod wykonany; odbiór lokalny otwarty)** Notatki: utwórz, edytuj szkic, finalizuj i archiwizuj według `availableActions`, z obsługą konfliktu 409 i niepewnego POST/PUT.
- **MOB-042 — WSTRZYMANE** S08 i inne akcje odczytu/zapisu biomedycznych pomiarów (również przez obserwacje celów). Nie implementować do czasu backendowej kontroli odpowiedniej zgody; nie tworzyć jej w mobile.
- **MOB-043** Utrata sieci, konflikt wersji, odtworzenie zgodne z polityką M0.

### M5 — zamknięcie pionu

- **MOB-050 (kod wykonany; odbiór na telefonie otwarty)** S06: aktualny plan z `execution-context`, fakty zgodnie/zmieniono/pominięto, reakcja po sesji i zapis z idempotencją; bez automatycznych powtórzeń.
- **MOB-051 (kod wykonany; odbiór na telefonie otwarty)** S09: executionRequired/executionRecorded, serwerowe podsumowanie dokumentacji, canComplete i zakończenie przez serwer. DTO Android ignoruje obecne w odpowiedzi pola pomiarów; danych tych nie prezentuje ani nie utrwala.
- **MOB-052** E2E UJ-01–UJ-06, testy statusów, podwójnego kliknięcia, restartu i utraty sieci.

### M6 — produkcja

- **MOB-060** Audyt kontroli OWASP MASVS, tokenów, storage, logów, backupu, izolacji i revocation.
- **MOB-061** Testy UX: TalkBack, font scale, tablet, orientacja, reduced motion i ergonomia pracy jedną ręką.
- **MOB-062** Internal track, podpisany AAB, bezpieczna telemetria, instalacja, rollback i pilotaż dwóch profesji.

## Minimalna strategia testów

| Zmieniony obszar | Minimum walidacji |
|---|---|
| Logowanie/OIDC | emulator: prawdziwy login/logout, negatywny issuer/audience, 401/403 |
| API/read screen | jednostkowy ViewModel/repository + wąski test kontraktu |
| Mutacja terminu/notatki/wykonania | sukces, konflikt 409, retry z tą samą idempotencją, 403 |
| Dokumentacja/PHI | immutable finished, przerwanie sieci, brak danych po logout/revocation |
| UI Compose | accessibility semantics + smoke zmienionego flow |
| Przed wydaniem | pełna ścieżka wizyty, obie role, uczestnik bez konta, kontrola MASVS |

**Nie uruchamiać całego monolitu Maven ani Playwright przy zmianie wyłącznie w Androidzie.** Backend testować celowo, gdy zmienia się backend lub OpenAPI. Każdy task aktualizuje dokumentację i zakres testów.

## Główne ryzyka

- **P0:** audience/issuer i klient Keycloak dla Androida; bez pomijania JWT validation.
- **P0:** wrażliwe dane i szkice na telefonie; bez polityki PHI nie wprowadzać trwałego cache.
- **P0:** błędne przypisanie wykonania/terminu; wersjonowanie i idempotency wymagane.
- **P0:** rozjazd web/mobile; jeden backend i kontrakty, żadnego lokalnego silnika reguł klinicznych.
- **P1:** rozrost zakresu przez push, offline sync, edytor planów i mapy anatomii — świadomie odłożone.

## GO / NO-GO MVP

GO tylko jeżeli: UJ-01–UJ-06 działają przy prawdziwym logowaniu; stan spotkania jest zgodny z serwerem; nie ma duplikacji zapisów; zgody, role i relacje są egzekwowane; PHI nie wydostaje się do logów/backupów; podstawowe ścieżki są dostępne i przetestowane na realnym urządzeniu.

**Następne czynności M0:** bez zmiany kodu backendu zweryfikować provisioning publicznego klienta Android w Keycloak, poprawne aud/iss, login/GET na emulatorze i generowanie Kotlin ze snapshotu. Wdrożenie pomiarów pozostaje zablokowane niezależnie od wyniku tych prób.

**Raport dowodowy i status bramek:** [05 — M0](05-m0-assessment.md).

## M1 — fundament techniczny

M1 zrealizował fundament MOB-010 i kod MOB-011 (AppAuth/PKCE, sesja RAM, provisioning klienta): pojedynczy moduł `android/app` z Compose, Material 3, wariantami `dev`/`prod`, Gradle Wrapper i workflow budującym debug APK. Adapter REST obejmuje cztery odczyty operacyjne; logowanie pozwala na chroniony GET bez ekranów biznesowych, danych medycznych i własnej logiki zgód. MOB-011 oczekuje testów runtime na urządzeniu. Instrukcja builda, wyniki lokalnej walidacji i granice zakresu są w [07 — M1](07-m1-android-build.md).

**MOB-011:** [Konfiguracja Android OIDC/Keycloak i bramki odbiorowe](08-m1-oidc-keycloak.md). Fizyczny telefon: login → GET identity → logout → ponowny login potwierdzone ręcznie. Nie przetestowano refresh oraz negatywnych 401/403 i pełnego zestawu uprawnień.

**MOB-012/013:** Repozytorium Kotlin obejmuje odczyty GET, mapowanie 401/403/409/offline/HTTP i pojedynczy retry tylko na 502/503/504 oraz błąd sieci. Po decyzji o odroczeniu S04 nawigacja główna obejmuje Dzień i Uczestników; kontekst uczestnika/spotkania jest nietrwały. Brak trwałego PHI; 401 wylogowuje, 403 czyści projekcje. Odbiór nowego UI na Motoroli pozostaje do wykonania.

## MOB-020 — S01 Dzień (2026-11-15)

**Implementacja:** Android S01 korzysta wyłącznie z istniejącego `GET /api/v1/specialist/today` (opcjonalne `date=YYYY-MM-DD`). Prezentuje datę i strefę IANA specjalisty, poprzedni/następny dzień, powrót do dnia serwerowego, bieżące/następne spotkanie, chronologiczny harmonogram, neutralne sygnały i zadania. Otwarcie terminu przechowuje wyłącznie identyfikatory w RAM i prowadzi do istniejącego, jeszcze niezaimplementowanego kontekstu M3. Sygnały i zadania pozostają w Dniu; odrębny ekran S04 został odroczony.

**Bezpieczeństwo:** brak zapisu danych na dysk, lokalnych pomiarów, mutacji i nowych endpointów. Brak treści medycznych z `neutralReason` oraz zewnętrznych `navigationReference` na liście; kategorie sygnałów są zredukowane do neutralnych etykiet. Zwroty API przeterminowane po zmianie daty lub wylogowaniu nie mogą nadpisać aktualnego stanu.

**Weryfikacja:** wąskie testy API daty, kolejności terminów według Instant, formatowania godzin w strefie specjalisty przy DST oraz wyścigu odpowiedzi przy szybkiej zmianie daty. CI Android buduje APK. Na urządzeniu sprawdzić daty, strefę czasu, nawigację do terminu, brak wpisów, błędy API i logout. **MOB-021 i M3 nie są częścią tego zadania.**

## MOB-021 — S02 Uczestnicy (2026-11-15)

- Lista S02 pobiera `GET /api/v1/specialist/clients`: rzeczywiste nazwy kartotek, kontekst relacji, status dostępu, najbliższy termin i `availableActions`. Dotychczasowa lista `GET /specialist/participants` pozostaje osobnym, ograniczonym wyborem aktywnych relacji; jej etykiety `Uczestnik N` nie nadają się do wyszukiwania kartotek.
- Wyszukiwanie lokalne po nazwie, bez nowego endpointu; nowa kartoteka: nazwa (1–80 znaków), opcjonalnie relacja (CLIENT/PATIENT/ATHLETE), e-mail i telefon. Brak wymagania konta uczestnika i danych biomedycznych. Po sukcesie przejście do kontekstu `participantId` (szczegóły S03 pozostają w MOB-022).
- `POST /api/v1/specialist/clients` wymaga `Idempotency-Key` UUID. W niezmienionym formularzu ten sam klucz służy do świadomego ponowienia po niepewnej odpowiedzi; zmiana danych generuje nowy klucz. Brak automatycznych retry POST i równoległych wysłań. 401 wylogowuje, 403 usuwa projekcje, 409 przedstawia komunikat o konflikcie, bez obchodzenia serwera.
- **Ograniczenie backendu:** `SpecialistClientService.create` wymaga zweryfikowanego profilu specjalisty oraz `moves.test-default-consent.enabled=true`. W Compose lokalnym ta funkcja jest włączona do testów; produkcyjne utworzenie uczestnika bez konta jest nadal zablokowane przez backend (409) do czasu odrębnej decyzji o prawidłowych mechanizmach zgód. Android nie implementuje własnej zgody ani nie włącza flag w backendzie.
- Dane listy i formularza są wyłącznie w RAM; nie trafiają do trwałego storage, loggera ani backupu. M0/S08 pomiary biomedyczne pozostają zablokowane.
- Weryfikacja: test klienta POST i nagłówka idempotencji, test obsługi jednego zapisu na próbę i świadomego ponowienia. Przed odbiorem na Motoroli: utworzenie kartoteki bez konta, wyszukiwanie, otwarcie kontekstu i zachowanie przy 403/409.

## MOB-022 — S03 Kartoteka (2026-11-15)

- Z istniejących `GET /api/v1/specialist/participants/{participantId}/workspace` i `.../timeline` wyświetlane są identyfikacja uczestnika, bieżący kontekst operacyjny, następne spotkanie, status aktywnego planu, tekstowe cele oraz historia. `participantId` jest przenoszony z S02 lub S04; każdy odczyt jest ponownie autoryzowany na backendzie. Brak lokalnego wnioskowania uprawnień i brak nowych mutacji.
- Timeline jest paginowany kursorem (`limit=20`, `granularity=DETAIL`). Pierwsza strona używa serwerowego okna 14 dni, kolejne zachowują identyczne `from`/`to` z pierwszej odpowiedzi. Starsze zdarzenia mogą być pobierane tylko w granicach tego samego okna; poszerzanie zakresu historii nie jest częścią MOB-022. Zdarzenia są odróżniane po `eventId`; błędy kolejnych stron nie usuwają dotychczasowych wpisów.
- Lista `types` świadomie zawiera tylko `APPOINTMENT,SESSION,EXECUTION,GOAL,INTERVIEW,NOTE`; nie żąda `MEASUREMENT`. Projekcja Androida nie dekoduje ani nie prezentuje pól `recentMeasurements`, wartości pomiarowych celów, `measurement`, `problem`, szczegółów klinicznych zdarzeń ani swobodnego `summary`. W historii pokazuje tylko neutralny typ, datę i status; cele są tekstowe.
- **Ograniczenie backendu:** dotychczasowy zbiorczy endpoint workspace nadal przesyła pola pomiarowe i może je obliczać serwerowo, a zdarzenia celu mogą mieć wartości obserwacji. Android ignoruje je przy deserializacji, ale ich eliminacja z payloadu sieciowego wymaga serwerowej projekcji/egzekwowania osobnej zgody przed aktywacją S08. MOB-022 nie stanowi zgody na ich wyświetlanie lub składowanie.
- Dane są wyłącznie w RAM; wylogowanie, utrata dostępu (403) i przełączenie uczestnika usuwają poprzednią projekcję, a spóźnione odpowiedzi są ignorowane. `401` unieważnia sesję. Kontekst wizyty dostępny w S05 od MOB-030.
- Walidacja: wąskie testy dekodowania dopuszczonych pól, parametrów i kursora HTTP, izolacji użytkowników i ponownej strony. Odbiór rzeczywistego urządzenia, polityki zgód i retry 401/403 pozostaje otwarty.

## MOB-024 — weryfikacja dostępu i roli (2026-11-15)

- GET `/api/v1/onboarding` potwierdza `stage=READY`, `profileType=SPECIALIST` i `profile.specialistKind`. Dopiero po odpowiedzi serwera dostępne są S01/S02/S03; rzeczywista rola widoczna w pasku aplikacji. Nie przełączamy lokalnie roli na taką, której serwer nie potwierdził: zmiana profilu zawodowego odbywa się w web/onboardingu, a Android wykrywa ją przy ponownym sprawdzeniu.
- Przy pierwszym logowaniu, powrocie aplikacji na pierwszy plan i jawnym „Sprawdź dostęp” natychmiast usuwane są wszystkie projekcje uczestników, historia i wybrany kontekst spotkania. Odpowiedzi dawnych żądań są ignorowane przez licznik generacji; nowe widoki wymagają świeżej autoryzacji backendu.
- `401`: wylogowanie; `403` lub nieaktywny/niekompletny profil: ekran odmowy bez danych; brak sieci: brak dostępu do dawnych rekordów i przycisk ponowienia. Backend pozostaje źródłem praw dla relacji i zgody — samo GET onboarding nie jest dowodem ważnej zgody konkretnego uczestnika; przy otwarciu S03 serwer ponownie egzekwuje dostęp.
- Po odroczeniu MOB-023 usunięto niefunkcjonalną trzecią zakładkę S04, pozostawiając neutralne sygnały i zadania w S01.
- Walidacja: kontrakt GET onboarding i dwa wąskie testy ViewModel (zmiana roli/spóźniona odpowiedź oraz 403). Bramka rzeczywistego cofnięcia relacji i zgody, telefon/foreground, refresh token i role nadal do odbioru na Motoroli; brak push/pollingu aktywnej sesji.

## MOB-030 — S05 Spotkanie (2026-11-15)

- S05 korzysta z istniejących `GET /api/v1/specialist/appointments/{id}` i `POST /api/v1/specialist/appointments/{id}/start`. Po otwarciu terminu z S01 lub S03 ponownie pobiera rekord: zweryfikowane `appointmentId`, `participantId`, `status`, `version`, `availableActions`, godziny, typ, cel i znacznik powiązanej sesji. Nie przenosi uprawnień przez identyfikator ani przez stan `Today`.
- Rozpoczęcie dostępne wyłącznie przy `START` w `availableActions` oraz aktualnej wersji, a nie na podstawie zegara telefonu; backend decyduje o 30-minutowym oknie startu i o relacji z uczestnikiem. Jeden POST na kliknięcie, bez automatycznych retry. Niepewny wynik (sieć, 5xx, błąd dekodowania) wymaga ręcznego ponowienia z **tym samym** `Idempotency-Key` i wersją. `409` powoduje pobranie aktualnego stanu i decyzję o dalszej akcji na podstawie nowego `availableActions`. `401` wylogowuje, `403` usuwa wszystkie projekcje.
- S01 wskazuje wizytę `IN_PROGRESS` zwróconą przez Today i umożliwia jej ponowne otwarcie również po restarcie aplikacji/logowaniu. S05 nie zapisuje PHI ani sesji na dysku; w razie restartu odtwarza stan wyłącznie przez nowe GET. Powrót nie kończy wizyty. Z S03 można wejść do następnego spotkania; po powrocie ponownie pobierana jest kartoteka.
- W tym zadaniu **nie** implementowano tworzenia/zmiany terminów (MOB-031), realizacji sesji (MOB-050), dokumentacji (MOB-040), kończenia wizyty (MOB-051) ani pomiarów S08. Na S05 wyświetlany jest stan tych etapów jako informacja, bez pozornie aktywnych przycisków.
- Weryfikacja: test kontraktu GET/POST nagłówków i `version`, test manualnego retry z tym samym kluczem, re-fetch przy 409, izolacji odpowiedzi różnych spotkań oraz reguły wyświetlania START. Fizyczna Motorola: potwierdzić start w dopuszczonym oknie, powrót do wizyty, logout/403, utratę sieci przed i po POST. Pełny Maven/Playwright poza zakresem zmiany Android.

## MOB-031 — tworzenie i zmiana terminu (2026-11-15)

- Dodanie terminu z S01 lub kartoteki S03, zmiana z S05 wyłącznie przy serwerowym `availableActions=UPDATE`. Formularz używa `GET /api/v1/specialist/today?date=...` do wyboru bieżącej daty i serwerowych `bookableSlots`. Lista uczestników z aktywnych `GET /api/v1/specialist/clients` musi zawierać `SCHEDULE_APPOINTMENT`. Daty i godziny pokazuje strefa IANA kalendarza specjalisty; brak lokalnego rozstrzygania DST albo konfliktów.
- Istniejące `POST /api/v1/specialist/appointments` oraz `PUT /api/v1/specialist/appointments/{id}` wymagają `Idempotency-Key`. PUT wysyła `version` z uprawnionego GET, zachowuje `participantId` i `plannedSessionId` (nie można ich przypadkowo przepiąć). Serwer sprawdza okna dostępności, kolizję, powiązanie sesji i reguły statusu; naprawiono brak rzucenia wyjątku w `conflictIfOverlapping`.
- Jeden POST/PUT na próbę i brak automatycznych ponowień. Przy niepewnej odpowiedzi świadomy retry niezmienionego payloadu używa tego samego klucza; zmiana formularza generuje nowy klucz. `409`: nie ponawiać automatycznie, pobrać nowe sloty i — dla PUT — ponownie GET szczegółów wraz z wersją. `401`: logout; `403`: wyczyszczenie projekcji i zamknięcie dostępu.
- Po zapisie odświeżane są Dzień i lista kartotek, a użytkownik przechodzi do S05 nowego/zmienionego terminu. Dane formularza pozostają w RAM. Obsługa anulowania, powiadomień i planowania cyklicznego nie należy do MOB-031.
- Walidacja: wąskie testy HTTP POST/PUT (nagłówki, payload, version), ViewModel idempotencji, 409 i izolacji asynchronicznej, reguły slotów oraz jeden test backendu z kolizją. Wymagane jeszcze sprawdzenie na Motoroli.

## MOB-032 — nawigacja kontekstowa spotkania (2026-11-15)

- S05 otwiera osobne trasy S06 **Realizacja sesji**, S07 **Dokumentacja** i S09 **Zakończenie**. S03 umożliwia również przejście do S07 bez powiązanej wizyty. Back wraca na dokładnie poprzedni ekran, nie zmienia statusu spotkania ani identyfikatora uczestnika; żadnych UUID nie umieszczono w ścieżkach nawigacji lub tekstach interfejsu.
- Identyfikatory `appointmentId` i `participantId` są przechowywane wyłącznie w RAM, w `ContextSelection`. Przejście S05 → podstrona nie wywołuje `selectParticipant` ani `selectAppointment`; bezpośrednio przed nawigacją stary snapshot jest unieważniany, a przed pokazaniem danych podstrony następuje ponowny, autoryzowany GET wizyty. Przy wejściu z S03 ponownie pobierana jest kartoteka. Identyfikatory wyniku muszą odpowiadać bieżącemu wyborowi, w przeciwnym razie ekran jest ukryty; 401/403 czyści RAM zgodnie z MOB-024.
- Realizacja wymaga `status=IN_PROGRESS` i powiązanej sesji; zakończenie wymaga `status=IN_PROGRESS`. Dokumentacja z S05 nie otwiera się dla odwołanych lub nieodbytych wizyt. Są to ograniczenia nawigacji, nie zamiennik serwerowych warunków zapisów.
- S06, S07 i S09 są na tym etapie **tylko nieedytowalnymi powłokami nawigacyjnymi**, jasno oznaczonymi jako oczekujące na odpowiednio MOB-050, MOB-040/041 oraz MOB-051. Nie wykonują operacji zapisujących. **Nie wywołano** `closeout-context`, ponieważ backend przesyła w nim także pomiary; brak jakichkolwiek endpointów S08. Właściwe odczyty dokumentacji/realizacji oraz walidacja `canComplete` pozostają odrębnymi zadaniami.
- Walidacja: skoncentrowane testy identyfikacji kontekstu, wyłączania nieuprawnionych tras, wejścia z kartoteki oraz ponownego GET i cofnięcia uprawnienia. Odbiór przycisków/back na Motoroli pozostaje otwarty.

## Poprawka audytowa: izolacja formularza terminu (2026-11-16)

- Nowy termin `ScheduleTarget.New` nie może odziedziczyć `type`, `locationMode`, `location` ani `shortPurpose` po wcześniej otwartej wizycie innego uczestnika. Przed otwarciem formularza unieważniamy również szczegóły poprzedniego spotkania.
- Przy edycji `ScheduleTarget.Edit` dane wyjściowe pochodzą wyłącznie z uprawnionego szczegółu o **jednocześnie zgodnych** `appointmentId` i `participantId`. Wąskie testy sprawdzają oba przypadki. Pozostałe ustalenia code review pozostają odrębnymi zadaniami.

## Poprawka audytowa: zachowanie kontekstu po wznowieniu (2026-11-16)

- Powrót na pierwszy plan oraz „Sprawdź dostęp” **natychmiast ukrywają** dane serwerowe i unieważniają spóźnione odpowiedzi, ale przy przejściowej rewalidacji zachowują identyfikatory aktualnej trasy, wybraną datę i niezapisane pola formularzy wyłącznie w pamięci RAM.
- NavHost pozostaje w kompozycji pod nieprzezroczystą blokadą (bez dotyku, systemowego Wstecz i semantyki dostępności). Po potwierdzeniu **tej samej roli** ekran odzyskuje kontekst przez nowy autoryzowany GET, nigdy przez wcześniej pobrany obiekt ani automatyczne powtórzenie POST/PUT.
- Zmiana roli, 401, 403, nieaktywny profil i wylogowanie nadal usuwają kontekst oraz niezapisane szkice. Brak sieci blokuje wyświetlanie i pozwala na ponowne sprawdzenie, bez ujawniania poprzednich projekcji. Zapisy w locie oznaczane są jako niepewne, bez samoczynnego retry.
- Formularz zmiany terminu nie resetuje wpisanych pól, gdy podczas rewalidacji znika wersja szczegółu spotkania lub zostaje pobrana jej nowsza wartość. Minimalne testy: wznowienie z edycją, offline/403, zmiana roli, spóźniony odczyt. Odbiór przejścia do tła i powrotu na Motoroli pozostaje otwarty.

## Poprawka audytowa P1: systemowe Wstecz i spójność kontekstu (2026-11-16)

- Dla S05 i formularza terminu systemowy przycisk/gest Wstecz oraz widoczny przycisk `Wróć` korzystają z **tej samej** procedury nawigacji. Podczas zapisu START lub POST/PUT nawigacja Wstecz jest blokowana; po sukcesie zapisu terminu trasę zmienia wyłącznie handler rezultatu.
- Wyjście z S05 do poprzedniej S03 przywraca `ContextSelection.Participant` dla rzeczywistego `participantId` i inicjuje świeży autoryzowany GET kartoteki, zamiast zostawiać kontekst spotkania i pusty ekran.
- Anulowanie formularza zamyka `ScheduleTarget` oraz odświeża właściwą kartotekę albo spotkanie po powrocie; aktualizacja terminu powoduje natychmiastowe ponowne GET S05. Nie zmienia to statusu wizyty, nie uruchamia mutacji i nie odkłada PHI na dysk.
- Wąskie testy regresyjne strategii Wstecz oraz weryfikacja build-only w GitHub Actions. Testy uruchamia deweloper wyłącznie lokalnie; manualna próba systemowego Wstecz na Motoroli pozostaje do odbioru.

## Poprawka audytowa P1: edycja wizyty bez zmiany terminu (2026-11-16)

- S05 → zmiana terminu umożliwia **zachowanie dotychczasowej daty i godziny** podczas edycji typu, formy, miejsca oraz celu spotkania. Bieżące okno jest zajęte przez tę samą wizytę, więc nie musi znajdować się na liście wolnych slotów `Today`.
- Zmiana faktycznej godziny wymaga nadal dokładnego dopasowania do aktualnego `bookableSlots`; utworzenie nowej wizyty zawsze tego wymaga. Dla PUT obowiązuje zgodność `appointmentId`, `participantId`, aktualna wersja i serwerowe `UPDATE`.
- Backend nadal waliduje własne okno dostępności oraz kolizje, pomijając edytowaną wizytę podczas sprawdzania nakładania terminów. Brak zmiany backendu i brak lokalnych reguł przyznawania uprawnień.
- Dwa wąskie testy jednostkowe dodano do repo, ale **nie uruchamiano ich w GitHub**; CI pozostaje wyłącznie build-only. Testy do uruchomienia lokalnie: `cd android && ./gradlew :app:testDevDebugUnitTest`.

## Poprawka audytowa P1: konflikt 409 bez utraty szkicu (2026-11-16)

- Formularz edycji utrwala w pamięci RAM pierwotne pola i **pierwotny przedział czasu**, przypisane do konkretnego `appointmentId` i `participantId`. Nowy GET po 409 nie inicjalizuje ponownie pól formularza ani nie podstawia automatycznie nowej godziny.
- Po 409 odświeżane są szczegóły wizyty (`version`, `availableActions`) i wolne sloty. Gdy termin zmienił się na serwerze, formularz wyświetla konflikt i wymaga **jawnego wyboru** aktualnej godziny z serwera lub innego dostępnego terminu; dotychczasowa godzina może zostać użyta tylko, gdy nadal jest legalna. Nie ma automatycznego ponawiania PUT ani ukrytego rozstrzygania konfliktu.
- Własne pola pozostają w szkicu; cel i miejsce z odświeżonej wersji serwerowej są pokazane oddzielnie do porównania. Zapis nadal wymaga potwierdzonego `UPDATE`, wersji i kontroli dostępności przez backend.
- Dodano wąskie testy reguł snapshotu i braku automatycznego retry. GitHub Actions wykonuje jedynie build; testy jednostkowe pozostają do lokalnego uruchomienia.

## Poprawka audytowa P1: cykl życia ViewModeli i obrót ekranu (2026-11-16)

- `ReadinessViewModel` i `OperationalViewModel` są teraz tworzone przez `androidx.lifecycle.viewmodel.compose.viewModel` i przypięte do `Activity ViewModelStore`. Obrót urządzenia zachowuje tę samą instancję oraz `viewModelScope`, zamiast zostawiać niezależne zadania w starym `remember`.
- Szkice S05 → edycja/dodanie terminu oraz S02 → nowa kartoteka, wraz z idempotency-key, są przechowywane **wyłącznie w RAM ViewModelu**; nie korzystają z `SavedStateHandle`, `rememberSaveable`, plików ani lokalnej bazy. W szczególności baseline 409 i wybór godziny nie są rekonstruowane z nowego GET po obrocie.
- Zakończenie zapisu kartoteki jest sygnalizowane stanem `Saved(id)`, który aktualnie wyświetlany ekran konsumuje przez `LaunchedEffect`; coroutine nie przechwytuje już zniszczonego przy obrocie `NavController`.
- `logout`, 401/403 i zmiana kontekstu zawodowego nadal czyszczą szkice i projekcje; po obrocie dodatkowa bramka wymaga **nowego epoch pozytywnej autoryzacji**, aby zachowane w ViewModelu dane nie pojawiły się choćby na jedną klatkę przed ponownym GET. Zwykła zmiana konfiguracji nie przenosi danych klinicznych do Android Bundle.
- W GitHubie tylko build, testy jednostkowe lokalnie przez `cd android && ./gradlew :app:testDevDebugUnitTest`. Odbiór na Motoroli: obrót na S03, S05, otwartej edycji z wpisanym celem, S02 z otwartym formularzem; wylogowanie i ponowne logowanie.

## Poprawka audytowa P1: błędy odświeżania OIDC (2026-11-16)

- `OidcSession.freshAccessToken()` rozróżnia definitywne błędy OAuth token endpoint (`invalid_grant`, `invalid_client` itp.) od timeoutów, braku sieci i niejednoznacznych błędów Keycloak. Tylko potwierdzone wygaśnięcie uprawnienia lub brak używalnego grantu wywołuje `clear()`; błędy przejściowe nie wylogowują.
- Przejściowy błąd odświeżenia jest sygnalizowany typowanym `TokenRefreshTemporarilyUnavailable`; wszystkie wywołania wymagające świeżego tokenu zatrzymują HTTP GET/POST/PUT. ViewModel **natychmiast ukrywa** ostatnie odpowiedzi serwera, ustawia `Offline`, zachowuje identyfikatory kontekstu i szkice tylko w RAM. Powrót wymaga świadomego `Sprawdź ponownie`, nowego tokenu i autoryzowanego odczytu `onboarding`. Bez automatycznego retry mutacji.
- Usunięto dodatkowe wywołanie `freshAccessToken()` z `MovesApp` przed rewalidacją, które wcześniej mogło wylogować poza kontrolą bramki dostępu; status `readiness` odświeża się dopiero po autoryzacji.
- Dodano wąskie testy polityki AppAuth oraz zachowania ViewModelu przy niedostępnym Keycloak i zapisie przed wysłaniem POST. Testy pozostają **wyłącznie lokalne**; GitHub Actions wykonuje tylko build.

## Poprawka audytowa P1: data wizyty według strefy specjalisty (2026-11-16)

- Podczas otwierania edycji S05 aplikacja **nigdy nie zakłada UTC ani strefy telefonu**. Jeśli istnieje aktualna uprawniona odpowiedź `Today` ze strefą IANA, wyznacza datę rozpoczęcia wizyty w tej strefie.
- Przy wejściu z S03 bez wczytanego Dnia wykonywany jest najpierw autoryzowany `GET /api/v1/specialist/today` (bez `date`) w celu odczytu `timeZoneId`. Dopiero po walidacji strefy i przeliczeniu `startsAt` pobierany jest `GET .../today?date=YYYY-MM-DD` dla właściwego dnia. Do czasu uzyskania strefy UI nie pokazuje przypadkowej daty i nie dopuszcza PUT.
- Brak lub niepoprawna strefa IANA skutkuje błędem odczytu, **bez fallbacku**. Ponowienie następuje ręcznie. Użytkownik może dalej anulować formularz.
- Testy w repo obejmują wizyty blisko północy, brak skonfigurowanej strefy oraz właściwą kolejność wczytywania. GitHub Actions pozostaje build-only; testy uruchamiane lokalnie.

## Drobne porządki kompilatora Kotlin (2026-11-16)

- Usunięto cztery ostrzeżenia o redundantnych operacjach: niepotrzebne `!!` w etykiecie terminu i w asercji testowej, zbędne `?: return null` po sprawdzeniu identyfikatora wizyty oraz `toString()` dla tekstowego issuer OIDC.
- Bez zmiany działania, API, zależności ani testów. GitHub Actions nadal wykonuje jedynie build; testy uruchamiane lokalnie.

## Poprawka audytowa P1: ręczna rewalidacja a asynchroniczny zapis (2026-11-16)

- Przycisk **Sprawdź dostęp** jest niedostępny podczas START wizyty, POST/PUT terminu oraz tworzenia kartoteki. Pozostaje zablokowany także dla zapisanego terminu/kartoteki do chwili obsłużenia wyniku przez aktualny ekran. Sprawdzany jest również aktualny stan ViewModelu wewnątrz callbacka, aby drugi szybki klik nie unieważnił właśnie rozpoczętej mutacji.
- S05/S04 przechodzą do innego ekranu po `Saved` tylko wtedy, gdy formularz, jego cel, stan zapisu i autoryzacja nadal są aktualne; nie wykonują spóźnionej nawigacji podczas bramki reautoryzacji. Systemowe Wstecz pozostaje zablokowane podczas zapisu.
- **Wylogowanie pozostaje zawsze dostępne.** Celowa utrata sesji lub powrót z tła mogą nadal prowadzić do wyniku `Uncertain` (bez automatycznego retry); powyższa blokada dotyczy przypadkowej ręcznej rewalidacji podczas zapisu.
- Dodano jedynie wąski test polityki interakcji. Na żądanie użytkownika: **bez builda, bez testów, bez uruchamiania GitHub Actions**. Walidację połączyć z następną poprawką.

## Poprawka audytowa P2: minimalizacja pomiarów w S03 workspace (2026-11-16)

- `GET /api/v1/specialist/participants/{participantId}/workspace` nie wykonuje już automatycznie `measurements.recent(...)` ani nie generuje sygnału `RECENT_MEASUREMENT` zawierającego wartości pomiarowe. Dla kompatybilności bieżącego kontraktu pole `recentMeasurements` zwraca pustą listę.
- Pomiary nadal są dostępne przez istniejący, osobno autoryzowany `GET /api/v1/specialist/clients/{participantId}/measurements` oraz odczyt szczegółowego zdarzenia osi czasu. Uprawnienia `VIEW_MEASUREMENTS` i zgoda `MEASUREMENTS` pozostają egzekwowane na serwerze.
- Test regresyjny sprawdza pustą projekcję i brak wywołań portu pomiarów przy renderowaniu workspace. Android S03 już ignoruje `recentMeasurements`, więc jego kontrakt i UI nie wymagają modyfikacji.
- GitHub Actions wykonuje **wyłącznie build** (bez testów), obejmując także poprzednią poprawkę #30. Testy uruchamia deweloper lokalnie.

## Poprawka audytowa P1: strefa czasu w prezentacji S01/S05 (2026-11-16)

- Ujednolicono wyświetlanie godzin przez `specialistCalendarZone`, która wymaga poprawnego `timeZoneId` z serwera. Brak/niewłaściwa strefa nie prowadzi już do automatycznego użycia UTC ani czasu telefonu.
- S01 i lista slotów S05 wyświetlają informację o nieznanej strefie zamiast niepoprawnie przeliczonej godziny. Przy nieznanej strefie formularz S05 **nie dopuszcza wyboru ani zapisu terminu**, także przy edycji istniejącego spotkania; szkic zostaje w RAM i można odświeżyć dane.
- Szczegół spotkania S05, który nie otrzymał jeszcze strefy specjalisty, pokazuje dokładną godzinę z **oryginalnym, jawnie oznaczonym offsetem odpowiedzi serwera** (bez przeliczania według urządzenia). Znana strefa nadal wyświetla czas lokalny specjalisty.
- Trzy małe testy jednostkowe (S01, szczegół S05, formularz S05), bez nowych zależności lub zmian backendu. GitHub Actions pozostaje wyłącznie build-only, testy do wykonania lokalnie.

## MOB-040 / etap 1 — widok S07 i przygotowanie dyktowania (2026-11-16)

- Wejście z S03/S05 wymaga uprzedniego, autoryzowanego GET kartoteki/wizyty z potwierdzonym participantId. Dopiero potem wykonywany jest `GET /api/v1/specialist/clients/{participantId}/records/interviews?actingContext=...`; odpowiedź jest przypisana do bieżącej roli i uczestnika. Lista pokazuje stan szkicu, zakończonych i zastąpionych wywiadów.
- Edytor pokazuje pytania i sekcje otrzymane z serwera; istniejące odpowiedzi tekstowe, liczbowe i daty. Ukończone wywiady są tylko do odczytu. Pola tekstowe mają wspólny komponent `DictationReadyTextField` z opcjonalnym callbackiem po identyfikatorze pola do przyszłego silnika **on-device only**. Dopóki silnik nie istnieje, akcja mikrofonu jest nieaktywna i nic nie nasłuchuje ani nie wysyła głosu. Dyktowanie samej klawiatury zależy od producenta/usługi, nie jest uznawane za gwarantowane offline.
- Zmiany lokalnych pól wywiadu pozostają wyłącznie w RAM istniejącego `OperationalViewModel`, także przy obrocie ekranu i przejściowej rewalidacji. Na utratę relacji, 401/403, zmianę użytkownika lub logout są usuwane. Widok żąda potwierdzenia przed opuszczeniem z niezapisanymi zmianami, bez sugerowania, że szkic jest zapisany na serwerze.
- **Świadoma granica etapu:** przyciski `Rozpocznij nowy`, `Zapisz szkic`, `Zakończ wywiad`, `Rozpocznij kolejny` są wyłączone. Nie ma mutacji ani ukrytego autozapisu. Backend udostępnia pytania typu `SINGLE_SELECT`/`MULTI_SELECT`, ale **bez listy opcji**; widok tylko pokazuje dotychczasowe wartości i sygnalizuje brak słownika do czasu aktualizacji kontraktu. Notatki to następny etap MOB-041.
- Dwa wąskie testy: odczyt tylko w zweryfikowanym kontekście i czyszczenie szkicu po wylogowaniu oraz mapowanie pól dynamicznego wywiadu. GitHub Actions **wyłącznie buduje**, testy wykonywane lokalnie. 

## MOB-040 / etap 2 — zapisywanie i zakończenie S07 (2026-11-16)

- `POST /api/v1/specialist/clients/{id}/records/interviews` pozwala rozpocząć wywiad (oraz kolejny, jeśli poprzedni ma `START_NEXT_INTERVIEW`); wymaga `actingContext` i `Idempotency-Key`. Nie tworzy równoległych wywiadów podczas aktywnego szkicu. Przy niepewnej odpowiedzi ręczne ponowienie stosuje **identyczny klucz**, bez automatycznego retry.
- `PUT .../interviews/{id}` zapisuje `expectedVersion` i pełną listę odpowiedzi; w szczególności nie usuwa nieruszonych wartości wyboru na backendzie, który zastępuje wszystkie odpowiedzi. Pusty tekst usuwa odpowiedź. Daty i liczby są walidowane przed wysłaniem. Błąd sieci w PUT nie powoduje bezwarunkowego powtórzenia.
- `POST .../interviews/{id}/complete` wykonuje nieodwracalne zakończenie wyłącznie przy `COMPLETE`, brakujących niezapisanych edycjach i wszystkich wymaganych polach; wymaga potwierdzenia oraz `Idempotency-Key`. Ukończone wywiady pozostają nieedytowalne, a ich poprzednie wersje oznaczane jako zastąpione przez backend.
- `409`: pobierana jest nowa lista wywiadów, lokalne odpowiedzi nie są nadpisywane. Dopiero po świadomym potwierdzeniu przez specjalistę łączone są nowsze wartości serwera (w nietkniętych polach) z jego edycjami; następuje **oddzielne ręczne** PUT/POST. Niepewne POST używają wcześniejszego klucza, a PUT nie jest ponawiany automatycznie.
- Android wyświetla istniejące słowniki odpowiedzi `SINGLE_SELECT` i `MULTI_SELECT` po kodzie pytania, w zgodzie z `web/src/app/features/specialist-participant-workspace.presentation.ts` (`activity_level`, `sleep`, `consents`). Jest to odtworzenie istniejącego kontraktu UX, bez tworzenia formalnych zgód; nieznane typy pytań pozostają tylko do odczytu. Przeniesienie słowników do backendu pozostaje długiem architektonicznym.
- Wszystkie dane wprowadzane lokalnie pozostają w RAM (w tym po obrocie i przejściowej reautoryzacji); wylogowanie/401/403/zmiana uczestnika czyści szkice, żądania i odpowiedzi. Przycisk ręcznej reautoryzacji oraz gest Wstecz nie mogą przerwać bieżącej mutacji, a nagrania i rozpoznawanie mowy pozostają nieaktywne.
- Wąskie testy: zachowanie odpowiedzi wyboru, rebase 409, brak automatycznego retry PUT, ręczny retry POST z oryginalnym kluczem. **W GitHub Actions tylko build.** Lokalnie: `cd android && ./gradlew :app:testDevDebugUnitTest`.

## MOB-041 — notatki S07 (2026-11-16)

- Zakładka Notatki pobiera dane **dopiero po otwarciu**: `GET /api/v1/specialist/clients/{participantId}/records/notes?actingContext=...`, tylko po ponownej autoryzacji kontekstu S03/S05. Odpowiedź pozostaje przypisana do uczestnika i bieżącej roli.
- Dostępny jest POST nowej notatki (stabilny `Idempotency-Key`), wersjonowany PUT szkicu (`expectedVersion`), finalizacja i archiwizacja (POST z oryginalnym kluczem przy jawnym retry). Dostępność wyłącznie według `availableActions`; statusy `DRAFT`, `FINAL`, `ARCHIVED` są egzekwowane. Zweryfikowana bieżąca wizyta może być przypisana do nowej notatki.
- Szkic zawierający dane medyczne istnieje **wyłącznie w RAM** ViewModelu, nie w `SavedState`, Bundle ani plikach. Obrót i czasowa reautoryzacja zachowują szkic, natomiast logout, 401/403 i zmiana uczestnika go usuwają. Nawigacja systemowa i ręczna rewalidacja nie mogą przerwać zapisu.
- Konflikt 409 odświeża dane serwera, zachowuje nietknięte pola z nowszej wersji, lokalnie zmienione pola trzyma w RAM i wymaga obejrzenia obu wersji oraz jawnego potwierdzenia scalenia; dopiero potem użytkownik może wysłać nowy PUT. Niepewny PUT nie jest ponawiany automatycznie. Dla niepewnego POST dostępne jest jawne powtórzenie z poprzednim kluczem.
- Kategoria według istniejących wartości webowych (`GENERAL`, `SESSION`, `HEALTH`, `OTHER`); pole treści współdzieli przygotowanie pod dyktowanie on-device, z nieaktywnym mikrofonem, bez nagrań i chmury. Nie dodano zależności ani migracji.
- Dodano wąskie testy lokalne: izolacja szkicu, ponowienie POST z tym samym kluczem, brak automatycznego retry PUT po 409, rebase po świadomym potwierdzeniu. **GitHub Actions tylko build**, bez testów.

## MOB-050 — realizacja wizyty S06 (2026-11-16)

- S06 otwierane wyłącznie przy **zweryfikowanym** `appointmentId`, `participantId`, `plannedSessionId` i stanie `IN_PROGRESS` z aktualnego, autoryzowanego GET S05. Następnie `GET /api/v1/specialist/appointments/{appointmentId}/execution-context` zwraca snapshot pozycji planu, dawek, tytułów oraz `recordingAllowed`; identyfikatory są obowiązkowo porównywane z S05. Nie wolno przyjmować parametru planu lub ćwiczeń z deep linku.
- UI pokazuje dawki planowane, umożliwia dla każdego ćwiczenia zaznaczenie **zgodnie z planem / zmieniono / pominięto**. Zmiana dawki udostępnia właściwe dla typu ćwiczenia pola liczbowe; wysyłane są wyłącznie fakty zadeklarowane przez specjalistę. `painLevel` 0–10 i `difficultyLevel` 1–10 są wymagane, `sessionRpe` oraz `techniqueConfidenceLevel` 1–10 i krótka notatka do 500 znaków są opcjonalne. Serwer waliduje powiązanie ćwiczeń z planem, relację i stan appointment.
- Po zatwierdzeniu aplikacja wysyła pojedynczy `POST /api/v1/specialist/appointments/{appointmentId}/execution` z `Idempotency-Key`, `declaredCompletion=true` oraz `observationMode=DECLARED`. Klucz i cały payload są zamrażane do czasu potwierdzenia. Przy utracie połączenia/5xx nie ma auto-retry; po GET potwierdzającym brak zapisu użytkownik może jawnie ponowić **dokładnie tę samą operację**. 409 wymaga nowego GET i świadomej decyzji, nigdy automatycznego POST. Po potwierdzonym zapisie UI pokazuje tylko wynik serwera i blokuje kolejną deklarację.
- Wpisane dane pozostają wyłącznie w pamięci RAM ViewModelu (także przy obrocie i chwilowej rewalidacji). Wylogowanie, 401/403 lub zmiana wizyty usuwa je natychmiast. Brak nagrań, załączników, S08 i lokalnych baz danych. Systemowe Wstecz i ręczne „Sprawdź dostęp” są blokowane podczas zapisu; z niezapisanymi faktami wyjście wymaga potwierdzenia.
- Testy jednostkowe są ograniczone do walidacji dawek, stanu i identyfikatorów, bezpiecznego ręcznego ponowienia niepewnej operacji oraz zapobiegania duplikatom. **GitHub Actions tylko buduje**, testów nie uruchamia. Odbiór fizycznego telefonu obejmuje brak sieci, zmianę planu, cofnięcie relacji i pracę w trakcie wizyty. S09/MOB-051 nadal osobnym zadaniem.

## MOB-051 — kontrola kompletności i zakończenie S09 (2026-11-18)

- **Odczyt:** S09 wymaga świeżego, autoryzowanego S05, następnie GET `/api/v1/specialist/appointments/{id}/closeout-context?actingContext=...`. Dopasowuje `appointmentId` i `participantId` i sprawdza stan `IN_PROGRESS` (albo `COMPLETED` przy odzyskiwaniu wyniku). Prezentuje wymaganie i status zapisanej realizacji, `canComplete` oraz opcjonalne tekstowe `summaryNote`; pole `measurements` jest celowo nieobecne w typowanym DTO Androida.
- **Podsumowanie:** endpoint `POST /complete` przyjmuje **wyłącznie wersję** spotkania. Android nie dodaje pozornego pola, które nie zostałoby zapisane. W S07 udostępniono backendową kategorię notatki `APPOINTMENT_SUMMARY` dla notatki przypisanej do bieżącej wizyty. S09 wyświetla wynik istniejącego mechanizmu `summaryNote`; brak podsumowania nie blokuje, jeżeli serwer zwraca `canComplete=true`.
- **Zapis:** `POST /api/v1/specialist/appointments/{id}/complete` wysyła pojedyncze `{ "version": N }` i `Idempotency-Key`. Przycisk jest aktywny tylko przy `canComplete`, serwerowym `COMPLETE` w `availableActions`, zgodności obu identyfikatorów i wersji oraz zapisanej realizacji, jeżeli wymagana. Serwer samodzielnie egzekwuje obowiązek wykonania powiązanej sesji.
- **Niepewny wynik:** po zerwaniu sieci, 5xx lub niejednoznacznej deserializacji kontekst jest ukrywany. Po świeżym GET można świadomie ponowić **identyczny** klucz i wersję; gdy serwer już zakończył wizytę, ekran potwierdza sukces bez kolejnego POST. `409` pobiera nowy stan i wymaga jawnego zaakceptowania wersji przed kolejnym zapisem — nigdy nie wykonuje automatycznego retry.
- **Bezpieczeństwo:** `401` usuwa sesję, `403` usuwa dane i szkice, także przy cofnięciu relacji. Zapis i podsumowanie istnieją wyłącznie w RAM. Ręczna rewalidacja i Wstecz są zablokowane podczas mutacji; po sukcesie odświeżane są Dzień oraz lista uczestników i można wrócić do Dnia.
- **Walidacja:** wąskie testy DTO i żądania HTTP, reguł przycisku, ponowienia po braku odpowiedzi, konfliktu 409 i izolacji uczestnika. CI Android pozostaje build-only; wymagany odbiór S09 na Motoroli w ramach MOB-052. Brak zmian backendu i migracji.
