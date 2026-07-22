# Status przerwania migracji

> **Snapshot historyczny — przestarzały.** Poniższy zapis opisuje stan z
> 2026-07-20 i nie jest bieżącym statusem adherence. Aktualny zakres i status
> są w [prompt.md](../../prompt.md), [etapach adherence-first](../adherence-first-implementation-stages.md)
> oraz [mapie wymagań](../adherence-first-requirements-map.md). Zachowano go dla
> historii przerwania migracji.

Data przerwania: 2026-07-20, na wyraźne polecenie użytkownika.

## Stan zakończony i zweryfikowany

Prompty 1–6 są zakończone i udokumentowane. Ostatnia pełna walidacja backendu po Prompcie 6:

- Java 25 aktywowana przez SDKMAN;
- `mvn -q verify` — exit 0;
- 22 testy, 0 błędów i 0 failures;
- migracje V001–V005 zastosowane na PostgreSQL 18.4 przez Testcontainers;
- `git diff --check` bez uwag;
- repozytoria `../gra-mateusza` i `../ruszsie` nie zostały zmodyfikowane przez migrację;
- nie wykonano commita ani push.

Po tej pełnej walidacji dodano `OpenApiSnapshotIntegrationTest`. Jego celowane uruchomienie przeszło i utworzyło `web/openapi/openapi.json`, ale pełnego backendowego `verify` po dodaniu tego testu już nie powtórzono z powodu przerwania.

## Prompt 7 — stan częściowy, nieuznany za zakończony

Wykonano:

- audyt aktywnego i legacy frontendu `gra-mateusza`;
- sprawdzenie oficjalnej macierzy zgodności Angular 22 / Node / TypeScript;
- szkielet `web` wygenerowany Angular CLI 22.0.7;
- zależności Angular Material/CDK 22.0.5, TypeScript 6.0.3 i `keycloak-js` 26.2.4;
- zależności OpenAPI Generator 2.40.0, Playwright 1.61.1 i axe-playwright 4.12.1;
- wersjonowany snapshot realnego OpenAPI backendu;
- klient `typescript-fetch` wygenerowany OpenAPI Generator 7.24.0 w `web/src/app/api/generated`;
- częściowa warstwa Keycloak OIDC/PKCE S256, guards UX i fasada wygenerowanego klienta;
- shell, motyw Material oraz częściowe ekrany logowania, onboardingu/profilu, katalogu, planu, sesji i alertów specjalisty.

Frontend nie został skompilowany ani przetestowany po tych zmianach i nie może być traktowany jako gotowy.

## Dług techniczny po przerwaniu

### Blokujące domknięcie Promptu 7

- Trasa `gamification` wskazuje na nieutworzony jeszcze `gamification.page.ts`; obecny build frontendu może z tego powodu nie przejść.
- Należy uruchomić TypeScript/Angular build i naprawić wykryte błędy typów oraz importów. Znany kandydat: `DatePipe` użyty w ekranie alertów bez dodania do imports.
- Trzeba zweryfikować wszystkie enumy i opcjonalne pola wygenerowanego klienta wobec strict TypeScript 6.
- Należy zakończyć ekran gamifikacji: opt-in, pseudonim, widoczność rankingu i prywatny wynik z realnych endpointów.
- Trzeba sprawdzić przepływ Keycloak z rzeczywistą konfiguracją klienta `motion-web`, redirect URI, Web Origins i mapowaniem ról. Guards pozostają wyłącznie UX; backend już autoryzuje zasoby.
- Brakuje konfiguracji środowiska produkcyjnego dla API i Keycloak.
- Trzeba dodać deterministyczny skrypt regeneracji klienta z `openapi/openapi.json` oraz test wykrywający rozjazd snapshotu/klienta.
- Wygenerowany klient zawiera własne metadane/package files; należy zdecydować, które artefakty generatora wersjonować i opisać tę decyzję.
- Stary wygenerowany `app.spec.ts` nie został dostosowany do nowego shellu.

### Testy i jakość frontendu

- Brak ukończonych testów komponentów Vitest.
- Brak testów routingu i guards.
- Brak testu kontraktu wygenerowanego klienta.
- Brak konfiguracji i scenariusza Playwright dla głównego vertical slice.
- Brak automatycznego testu WCAG/axe dla kluczowych ekranów.
- Nie uruchomiono produkcyjnego `ng build`.
- Nie wykonano pełnego audytu, czy w UI nie pozostały odwołania do mock/placeholder API.
- `npm install` zgłosił jedną podatność o niskiej wadze; wymaga identyfikacji przez `npm audit` i świadomej decyzji, bez automatycznego `npm audit fix`.
- Frontend nie został jeszcze dodany do głównego CI.

### Dokumentacja Promptu 7

- Brakuje końcowego manifestu portowanych funkcjonalnie elementów UI, elementów napisanych od nowa i odrzuconego legacy.
- Dokumentacja uruchomienia web, generowania klienta, konfiguracji OIDC i testów E2E wymaga ukończenia.
- Należy zaktualizować główny README o workflow backend + web.

### Prompt 8

Prompt 8 nie został rozpoczęty. Końcowy audyt musi objąć pełny backend verify, frontend install/test/build/E2E/a11y, migracje, OpenAPI, granice modułów, bezpieczeństwo, wyszukanie nazw legacy, stan obu repozytoriów źródłowych oraz końcowy stan Git.

## Uwagi operacyjne

- `../../prompt.md` i `spec.md` były staged przed rozpoczęciem pracy; ich stan nie został wycofany.
- Worktree `moves` zawiera liczne nowe, niecommitowane pliki zgodnie z wykonywaną migracją.
- Nie należy uznawać Promptu 7 ani całej migracji za ukończone, dopóki powyższy dług blokujący nie zostanie zamknięty i Prompt 8 nie przejdzie.
- Podczas edycji istniejących plików sporadycznie zawodził sandbox `bwrap` (`Failed RTM_NEWADDR`). W kilku miejscach po nieudanych próbach `apply_patch` zastosowano wąskie `sed -i`; należy polegać na diffie i buildzie, nie na założeniu poprawności tych zmian.
