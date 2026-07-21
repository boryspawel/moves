# Dokumentacja techniczna `moves`

Status: dokument rozwijany wraz z migracją. `spec.md` jest źródłem nadrzędnym i nie jest przez ten dokument zastępowany.

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
- `specialist`: profil specjalisty i relacja do uczestnika;
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
- `calendar`, `notification`: późniejsze moduły zakresu MVP;
- `gamification`: opt-in i append-only ledger bez danych medycznych;
- `audit`: istotne operacje i dostęp do danych wrażliwych.

## Reguły zależności

- Gamifikacja otrzymuje wyłącznie neutralne zdarzenie kwalifikacji wykonania; nie zależy od encji safety ani medycznych DTO.
- Plan wskazuje identyfikator konkretnej opublikowanej wersji ćwiczenia.
- Import nie zapisuje wersji opublikowanych; cross-source match zawsze wymaga decyzji człowieka.
- Execution wskazuje planowaną sesję i receptę, a korekta dopisuje rekord.
- Uprawnienia zasobowe są sprawdzane w przypadkach użycia, nie tylko w route guards lub rolach tokenu.
- Operacja specjalisty wskazuje jawny kontekst zawodowy i purpose; capability, relacja i zgoda są sprawdzane centralnie przy każdym dostępie.
- Każdy moduł posiada własny schemat/tabele i nie odczytuje repozytorium innego modułu bez jawnego portu.

## Dane i czas

- PostgreSQL jest jedynym źródłem prawdy; migracje są liniowe i forward-compatible.
- Identyfikatory domenowe są UUID; `sub` Keycloak pozostaje tekstową referencją zewnętrzną.
- Chwile są zapisywane jako UTC, a strefa IANA obok cyklicznych przedziałów.
- Operacje podatne na retry używają klucza idempotencji i unikalnego ograniczenia w bazie.

## Bezpieczeństwo i prywatność

- OAuth2 Resource Server waliduje issuer i audience; role Keycloak są mapowane do `ROLE_*`.
- Health i kontrakt OpenAPI mogą być publiczne; domenowe API domyślnie wymaga tokenu.
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
