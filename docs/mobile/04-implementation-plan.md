# Moves Mobile — plan wdrożenia v0.1

**Cel:** natywna aplikacja Android realizująca UJ-01…UJ-06 w zakresie tekstowych notatek i wizyt, oparta na istniejącym backendzie. **Status:** M0 — analiza statyczna wykonana, bramki runtime nierozwiązane; nie implementowano klienta, zgód ani zabezpieczeń backendu. S08/pomiary biomedyczne wyłączone do czasu rzeczywistej zgody serwerowej.

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

- **MOB-020** S01: Today, agenda, aktualne/przyszłe terminy i minimalne sprawy.
- **MOB-021** S02: lista/szukaj/dodaj kartotekę; klient bez konta.
- **MOB-022** S03: workspace, cele, aktywny plan i paginowany timeline.
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

M1 zrealizował ograniczony fundament MOB-010: pojedynczy moduł `android/app` z Compose, Material 3, wariantami `dev`/`prod`, Gradle Wrapper i workflow budującym debug APK. Adapter REST obejmuje cztery odczyty operacyjne, bez ekranów biznesowych, OIDC, danych medycznych lub logiki zgód. MOB-011 oraz pozostałe elementy backlogu M1 pozostają odroczone. Instrukcja builda, wyniki lokalnej walidacji i granice zakresu są w [07 — M1](07-m1-android-build.md).
