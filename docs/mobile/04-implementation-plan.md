# Moves Mobile — plan wdrożenia v0.1

**Cel:** instalowalna natywna aplikacja Android obsługująca UJ-01…UJ-06, oparta na istniejącym backendzie, z bezpiecznym online-first. **Status:** projekt do realizacji, żadna faza nie jest oznaczona jako zakończona.

## Kamienie milowe i kolejność

| Etap | Zakres | Wynik / bramka odbioru |
|---|---|---|
| **M0 — kontrakty i ryzyka** | AS-IS kontraktów, Keycloak Android PKCE/audience, polityka danych/szkiców, spike generatora Kotlin | realne logowanie i autoryzowany GET z emulatora + uzgodniona polityka PHI |
| **M1 — fundament Android** | Kotlin/Compose, design tokens, nawigacja, auth, klient HTTP/OpenAPI, stany błędów | APK, bezpieczny login/logout, podstawowe smoke UI |
| **M2 — czytanie i organizacja** | S01 Dzień, S02 Uczestnicy, S03 Kartoteka, S04 Sprawy | UJ-01, UJ-02, UJ-06; role, dostęp i klient bez konta |
| **M3 — spotkanie** | S05, terminy i ich lifecycle, szybkie planowanie, wznowienie, 409/retry | rozpoczęty termin z backendem i poprawne konflikty |
| **M4 — dokumentacja** | S07 Wywiady/notatki, S08 Pomiary, szkice i finalizacje | UJ-04; immutable finished, kontrolowany stan formularza |
| **M5 — zapis wykonania i closeout** | S06 fakty realizacji, S09 warunki i zakończenie | UJ-03, UJ-05; pełny end-to-end Dzień → Zamknięcie |
| **M6 — hardening i pilotaż** | MASVS, dostępność, tablety, releasy, obserwowalność bez PHI | dopuszczenie pilota; checklista bezpieczeństwa i UX |

M1 wymaga M0; M2 wymaga M1; M3 i M4 można częściowo rozwijać równolegle po M2; M5 wymaga M3 i M4. Dopiero po M5 planować M6.

## Backlog do wdrożenia

### M0 — najpierw

- **MOB-001** Audyt mapowania S01–S09 do aktualnego OpenAPI (`web/openapi/openapi.json`), pola `availableActions`, kody błędów, autoryzacja, wersjonowanie, Idempotency-Key.
- **MOB-002** Publiczny klient Android w Keycloak: App Links/redirect, Authorization Code + PKCE, issuer, audience, role; test odbioru tokenu przez backend bez osłabiania walidacji.
- **MOB-003** Polityka lokalnych szkiców i cache PHI (TTL, Keystore, brak backupów, wylogowanie, revocation, utrata urządzenia); potwierdzenie decyzji bezpieczeństwa i retencji.
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
- **MOB-032** Nawigacja do dokumentacji/pomiarów/realizacji i closeout bez utraty participantId.

### M4 — dokumentacja

- **MOB-040** S07: wywiad sekcyjny, zapis szkicu, zakończenie i kolejny wywiad.
- **MOB-041** Notatki: utwórz, edytuj szkic, finalizuj, archiwizuj tylko według `availableActions`.
- **MOB-042** S08: katalog pomiarów, walidacja typu/jednostki, zapis i aktualizacja workspace.
- **MOB-043** Utrata sieci, konflikt wersji, odtworzenie zgodne z polityką M0.

### M5 — zamknięcie pionu

- **MOB-050** S06: dawka planowana vs faktyczna, zgodnie/zmieniono/pominięto, reakcja po sesji, idempotentny zapis.
- **MOB-051** S09: executionRequired/executionRecorded, pomiary, podsumowanie, canComplete i zakończenie przez serwer.
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

**Następny prompt implementacyjny powinien obejmować wyłącznie MOB-001–MOB-004; nie rozpoczynać ekranów na fikcyjnych DTO przed zamknięciem M0.**
