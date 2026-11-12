# M0 — weryfikacja API, OIDC/Keycloak i minimalizacji danych (AS-IS)

**Data audytu:** 2026-11-12  
**Kod referencyjny:** main `fb4b1a551dc02c7c8b6d972ea81d802213b58c4d`; dokumentacja mobile w gałęzi `docs/moves-mobile-mvp-and-rollout`.  
**Metoda:** odczyt repozytorium i analiza statyczna kodu kontrolerów, serwisów, konfiguracji, testów, Flyway i aktualnego `web/openapi/openapi.json`. **Nie** wykonano środowiska, realnych tokenów, testu emulatora, generatora Kotlin, audytu prawnego ani testów penetracyjnych.

## Decyzja produktu — zakres wiążący

1. Mobile służy specjaliście do **minimalnych notatek tekstowych z informacji przekazanych ustnie**. Nie przechowuje dokumentów badań laboratoryjnych, wyników diagnostyki jako dokumentów, PDF, zdjęć RTG/MR, skanów ani załączników. Nie implementować importu ani uploadu medycznych plików.
2. **Pomiary biomedyczne** (np. masa ciała, obwody pacjenta) wymagają właściwej zgody pacjenta. Brak potwierdzonej serwerowej kontroli zgody oznacza **zakaz udostępniania S08 oraz innych operacji mobilnego odczytu/zapisu tych danych**, w tym poprzez cele lub zamknięcie wizyty. Nie tworzyć w Androidzie własnego mechanizmu zgody.
3. Online-first, tylko stan roboczy w pamięci procesu i istniejące serwerowe szkice notatek/wywiadów; bez lokalnej trwałej kopii dokumentacji, synchronizacji offline, nowego systemu retencji lub client-side compliance. Użytkownik otrzymuje ostrzeżenie przed utratą niezapisanej treści.
4. Zachować aktualne backendowe reguły dostępu i statusów; **nie implementować nowych reguł bezpieczeństwa/zgodności ponad backend w ramach M0**. Audyt identyfikuje luki, ale ich nie naprawia.

## 1. REST / OpenAPI — wynik: statycznie ZWERYFIKOWANE

Snapshot `web/openapi/openapi.json`: OpenAPI **3.1.0**, **191 ścieżek**, **322 schematy**. Model `Dose` ma `oneOf` i dyskryminator; istnieją ponadto schematy `allOf`. Generator Kotlin nie został uruchomiony i pozostaje bramką weryfikacyjną.

| Ekrany | Kontrakty | Status |
|---|---|---|
| S01 / Dzień | `GET /api/v1/specialist/today` | dostępne; strefa czasowa profilu, operacyjne sprawy |
| S02 / Uczestnicy | `GET/POST /api/v1/specialist/clients` | dostępne; kanoniczny `participantId`; create z `Idempotency-Key` |
| S03 / Kartoteka | `GET /api/v1/specialist/participants/{participantId}/workspace`, `/timeline` | dostępne; paginacja kursorem, uprawnienia projekcji |
| S04 / Sprawy | `GET /api/v1/specialist/worklist` oraz pola `today` | dostępne; nie tworzyć odrębnej worklisty |
| S05 / Spotkanie | `GET /api/v1/specialist/appointments/{id}`, `POST /{id}/start` | dostępne; status/wersja, mutacje idempotentne |
| S06 / Realizacja | `GET /api/v1/specialist/appointments/{appointmentId}/execution-context`, `POST /execution` | dostępne; dawka rzeczywista oddzielna od snapshotu planu |
| S07 / Notatki i wywiad | `/api/v1/specialist/clients/{participantId}/records/notes` i `/interviews` | dostępne; `actingContext`, szkic, finalizacja, wersja |
| S08 / Pomiary | `/api/v1/specialist/clients/{participantId}/measurements` i `/catalog` | **API istnieje, ekran i odczyt/zapis WSTRZYMANE** |
| S09 / Closeout | `GET /api/v1/specialist/appointments/{appointmentId}/closeout-context`, `POST /appointments/{id}/complete` | dostępne; `canComplete` po stronie serwera |

**Ograniczenia kontraktu:** niektóre operacje (np. `execution-context`, `closeout-context`, `measurements`) nie deklarują w OpenAPI lokalnego `security`, a dokument nie zawiera top-level `security`. To **nie** oznacza publicznego endpointu: `SecurityConfiguration` wymaga uwierzytelnienia dla pozostałych ścieżek. To ostrzeżenie dla przyszłego generatora/autoryzacji klienta, nie wykazana luka umożliwiająca anonimowy odczyt. Nie modyfikować kontraktu w M0.

## 2. OIDC / Keycloak — WYMAGA DALSZEJ WERYFIKACJI

**Potwierdzone w kodzie:**
- `SecurityConfiguration` ustawia stateless Resource Server oraz sprawdza podpis JWK, issuer, audience `motion-api` i obecność subject; role wyprowadza z `realm_access.roles` oraz `resource_access[security.oauth2.client-id].roles`.
- Lokalny realm `infra/keycloak/motion-local-realm.json` ma jeden klient `motion-web` (`publicClient=true`, standard flow, PKCE S256) oraz mapper audience `motion-api`.
- `deploy/scripts/bootstrap-keycloak.sh` tworzy webowy public client i mapper audience; nie tworzy klienta Android. Produkcyjna konfiguracja faktycznie działającego realm nie została odczytana.
- W `compose.yaml` są lokalne **testowe** przełączniki zgody/scope. Nie są potwierdzeniem produkcyjnego dostępu ani zgody.

**Nierozstrzygnięte:** brak zdefiniowanego publicznego klienta Android, identyfikatora i zarejestrowanego redirect, brak smoke autoryzacji kodem + PKCE, odczytu z emulatora i dowodu poprawnego aud/iss/role dla tokenu mobile. Nie obchodzić walidacji Resource Server i nie używać `motion-web` jako docelowego klienta Android.

**Gate do M1:** operator istniejącego Keycloak potwierdza konfigurację natywnego klienta i zgodność issuer/audience/roles; wykonać minimalny test login + autoryzowany GET na emulatorze, bez nowych mechanizmów uwierzytelniania w backendzie.

## 3. Zgoda na dane medyczne i pomiary — BLOKER S08

**Backend posiada ogólny mechanizm grant/revoke:** `ConsentController` oferuje `POST /api/v1/consent/grants` i revoke, a `ConsentGrantService` waliduje opublikowany template, purpose, scopes, odbiorcę, termin ważności i cofnięcie. **Nie oznacza to gotowej ścieżki zgody na pomiary.**

- `ConsentDecisionPort.DataScope` wymienia tylko `PLAN`, `EXECUTION`, `EFFECTIVE_RESTRICTION`, `CLINICAL_RATIONALE`; **brak zakresu pomiarów biomedycznych** i dedykowanego przejścia zgody w UI.
- `ParticipantMeasurementService.authorize` weryfikuje profil specjalisty i **aktywną relację**, lecz nie wywołuje `ConsentDecisionPort.requireAccess` ani `requireCapabilities` dla dedykowanego scope'u pomiarów. Dotyczy odczytu i zapisu.
- `ParticipantDocumentationService.authorize` sprawdza capability `MANAGE_PARTICIPANT_RECORDS`, profil i relację. `SpecialistAuthorizationService` nie mapuje tej capability na żaden `DataScope`, więc w tym przepływie **nie wykonuje kontroli ConsentDecisionPort**. Notatki są dostępne tylko w ramach istniejących reguł backendu; nie deklarować ich zgody serwerowej.
- `ParticipantDocumentationService.TEMPLATE` zawiera pytanie `consents` („Consents discussed”); zapis odpowiedzi nie jest aktywnym grantem ani podstawą dopuszczenia pomiaru.
- `ConsentGrantService.grant` wymaga profilu i konta `PARTICIPANT`; obsługa kartotek bez konta ma odrębne ograniczenia. `docs/test-participant-record-consent-debt.md` wyraźnie stwierdza, że przełącznik `TEST_DEFAULT` **nie jest zgodą**, tylko lokalnym/testowym obejściem.
- `ConsentController.publishTemplate` wymaga `ROLE_ADMIN`, podczas gdy lokalny realm deklaruje role `SYSTEM_ADMIN`/`CONTENT_ADMIN`. To odrębna niespójność do zbadania przez właściciela backendu, **nie przedmiot zmian M0**.

**Konsekwencja produktu:** wyłączyć w Androidzie S08 i zapisy wagi/obwodów i innych biomedycznych miar także alternatywną ścieżką przez obserwacje celu. Nie pozyskiwać w mobile „zgody” przez checkbox, wywiad ani przycisk potwierdzenia. Dopuścić te funkcje dopiero po weryfikacji, że backend egzekwuje realną zgodę na konkretny typ danych.

**Ryzyko pobocznego ujawnienia:** `SpecialistAppointmentCloseoutController` zwraca `measurements` przez `ParticipantMeasurementQueryPort.forAppointment` jako element `closeout-context`; podobne projekcje mogą wystąpić w kartotece/timeline. Samo ukrycie przycisku S08 nie gwarantuje, że API nie przesyła pomiarów. MVP nie powinno prezentować ani utrwalać tych pól; ograniczenie kontraktu do bezpiecznej projekcji wymaga późniejszej osobnej analizy backendu — bez implementacji w M0.

## 4. Zakres zapisanych danych — obecna funkcja notatki

- `ParticipantDocumentationController` przyjmuje dane tekstowe: `category`, `title`, `content` (limit 8000 znaków), opcjonalne `appointmentId`. Nie ma endpointu załączników/wysyłania obrazów w tym module.
- Wywiad jest szablonem pytań; niektóre pytania dotyczą informacji zdrowotnych (np. `medical_history`, `medications`, `pain_level`). Są to pola danych z deklaracji ustnej; nie oznaczają obsługi dokumentów źródłowych.
- Notatka `DRAFT` jest edytowalna, `FINAL` niezmienna, `ARCHIVED` niedostępna do dalszej edycji; odpowiedzi wywiadu po ukończeniu także są niezmienne.
- `ParticipantMeasurementService` może trwale zapisywać wagę/obwód; to **obecny stan backendu**, nie zatwierdzony zakres Mobile MVP. Backendowe ryzyko nie znika przez wykluczenie Androida.

## 5. Minimalizacja lokalnego przetwarzania — DECYZJA M0

- Brak bazy Room/SQLite na notatki, historii wywiadów, badań i pomiarów; brak załączników, cache plikowego PHI, automatycznej kolejki offline, fotografii i integracji z aparatem.
- Dane uczestnika tylko w pamięci podczas autoryzowanego zadania. Nie utrwalać payloadów ani w logach/debug output.
- Używać istniejącego serwerowego `DRAFT` dla notatki/wywiadu; w pozostałych procesach nie obiecywać odzyskania niezapisanych danych po ubiciu procesu.
- Minimalne zwykłe wymagania klienta OIDC i bezpiecznej sesji pozostają obowiązkiem przyszłej implementacji Androida, ale **nie projektować teraz osobnego systemu bezpieczeństwa lub zgodności**.

## 6. M0 — status i następne kroki

| Bramka | Wynik | Działanie |
|---|---|---|
| MOB-001 Mapowanie API | **PASS — statycznie** | potwierdzić rzeczywiste wartości DTO/akcje na runtime podczas M1 |
| MOB-002 Native OIDC/Keycloak | **PENDING** | wymagany klient Android i test PKCE, aud/iss, rola i 401/403 na emulatorze |
| MOB-003 Minimalizacja danych | **PASS — decyzja projektowa** | nie tworzyć lokalnej bazy medycznej ani własnego zgód/retencji |
| MOB-003 Zgoda na biometrics | **BLOCKED** | odroczyć S08; naprawa/aktywacja tylko w odrębnym przyszłym zadaniu backendowym |
| MOB-004 Kotlin generator spike | **PENDING** | na aktualnym OpenAPI 3.1 zweryfikować `Dose.oneOf`, enum, UUID, Instant, Problem Details, auth i 409; nie sprawdzono buildem |
| Runtime security / E2E | **NOT RUN** | brak uruchomionego środowiska i emulatora w tej analizie |

**Wniosek GO/NO-GO:** M0 daje podstawę do ograniczonego planowania implementacji, ale **nie jest zamknięty** do wejścia w M1 z produkcyjnym auth, bo brak native OIDC smoke i generator spike. **S08 pozostaje NO-GO niezależnie od zakończenia M0**. UJ-01…UJ-06 w przyszłym MVP rozumie się bez pomiarów biomedycznych; wszystkie zapisy wykonania i notatki pozostają ograniczone do istniejącego backendu.

**Brak zmian produkcyjnych w M0:** bez migracji, bez konfiguracji nowego Keycloak client, bez kodu Kotlin, bez implementacji zgód, retencji lub dodatkowego systemu bezpieczeństwa.
