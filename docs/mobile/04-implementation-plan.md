# Moves Mobile — plan wdrożenia v0.1

**Cel:** natywna aplikacja Android realizująca UJ-01…UJ-06 w zakresie tekstowych notatek i wizyt, oparta na istniejącym backendzie. **Status:** M2 rozpoczęte — M1 fundament i MOB-012/013 w main; MOB-020 S01 Dzień implementowany 2026-11-15. OIDC login/GET/logout/ponowny login zweryfikowane na Motoroli. Do zamknięcia M1 pozostają scenariusze refresh, 401/403, role oraz odbiór nowych ekranów na urządzeniu. S08/pomiary biomedyczne wyłączone do czasu rzeczywistej zgody serwerowej.

## Kamienie milowe i kolejność

| Etap | Zakres | Wynik / bramka odbioru |
|---|---|---|
| **M0 — kontrakty i ryzyka** | **Analiza statyczna API/OIDC/danych wykonana 2026-11-12**, bez implementacji | **częściowo**: brak klienta Android, logowania/emulatora i spike generatora; zob. 05-m0-assessment.md |
| **M1 — fundament Android** | Kotlin/Compose, design tokens, nawigacja, auth, klient HTTP/OpenAPI, stany błędów | APK, bezpieczny login/logout, podstawowe smoke UI |
| **M2 — czytanie i organizacja** | S01 Dzień, S02 Uczestnicy, S03 Kartoteka, S04 Sprawy | UJ-01, UJ-02, UJ-06; role, dostęp i klient bez konta |
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
- **MOB-023** S04: bezpieczne projekcje Today/worklist; mapowanie webowych navigationReference na znane cele mobilne.
- **MOB-024** Obsługa cofnięcia relacji/zgody i przełączania kontekstu zawodowego.

### M3 — cykl spotkania

- **MOB-030** S05: kontekst wizyty, stan lifecycle, rozpoczęcie i powrót do wizyty po przerwaniu.
- **MOB-031** Dodanie/zmiana terminu, dostępność i konflikty 409; request version + Idempotency-Key.
- **MOB-032** Nawigacja do dokumentacji/realizacji i closeout bez utraty participantId; nie udostępniać S08 pomiarów.

### M4 — dokumentacja

- **MOB-040** S07: wywiad sekcyjny, zapis szkicu, zakończenie i kolejny wywiad.
- **MOB-041** Notatki: utwórz, edytuj szkic, finalizuj, archiwizuj tylko według `availableActions`.
- **MOB-042 — WSTRZYMANE** S08 i inne akcje odczytu/zapisu biomedycznych pomiarów (również przez obserwacje celów). Nie implementować do czasu backendowej kontroli odpowiedniej zgody; nie tworzyć jej w mobile.
- **MOB-043** Utrata sieci, konflikt wersji, odtworzenie zgodne z polityką M0.

### M5 — zamknięcie pionu

- **MOB-050** S06: dawka planowana vs faktyczna, zgodnie/zmieniono/pominięto, reakcja po sesji, idempotentny zapis.
- **MOB-051** S09: executionRequired/executionRecorded, tekstowe podsumowanie, canComplete i zakończenie przez serwer. Istniejący closeout-context przesyła także pomiary: nie utrwalać ani nie prezentować ich w mobile; ryzyko ekspozycji jest zapisane w audycie M0.
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

**MOB-012/013:** Repozytorium Kotlin obejmuje cztery odczyty GET, mapowanie 401/403/409/offline/HTTP i pojedynczy retry tylko na 502/503/504 oraz błąd sieci. Navigation Compose udostępnia trzy główne sekcje z odczytami, jawny wybór actingContext/purpose dla worklist, przejścia do nietrwałego kontekstu uczestnika/spotkania i stany loading/empty/error. Szczegółowe ekrany i mutacje nadal pozostają w M2/M3. Brak trwałego PHI; 401 wylogowuje, 403 czyści odczytane projekcje. Odbiór nowego UI na Motoroli pozostaje do wykonania.

## MOB-020 — S01 Dzień (2026-11-15)

**Implementacja:** Android S01 korzysta wyłącznie z istniejącego `GET /api/v1/specialist/today` (opcjonalne `date=YYYY-MM-DD`). Prezentuje datę i strefę IANA specjalisty, poprzedni/następny dzień, powrót do dnia serwerowego, bieżące/następne spotkanie, chronologiczny harmonogram, neutralne sygnały i zadania. Otwarcie terminu przechowuje wyłącznie identyfikatory w RAM i prowadzi do istniejącego, jeszcze niezaimplementowanego kontekstu M3. Przejście do Spraw korzysta z istniejącej zakładki S04.

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
- Dane są wyłącznie w RAM; wylogowanie, utrata dostępu (403) i przełączenie uczestnika usuwają poprzednią projekcję, a spóźnione odpowiedzi są ignorowane. `401` unieważnia sesję. Kontekst wizyty pozostaje placeholderem do M3.
- Walidacja: wąskie testy dekodowania dopuszczonych pól, parametrów i kursora HTTP, izolacji użytkowników i ponownej strony. Odbiór rzeczywistego urządzenia, polityki zgód i retry 401/403 pozostaje otwarty.
