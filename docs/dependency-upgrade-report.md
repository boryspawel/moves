# Raport aktualizacji bibliotek i błędów — 2026-11-12

## Cel i wynik

Zaktualizowano bezpośrednie zależności backendu, web i konfigurację OpenAPI Generator bez zmiany kodu produkcyjnego backendu, kontraktu API, migracji, Keycloak ani mechanizmów zgód. Spring Boot przejął aktualizację własnego zarządzanego BOM; nie dodawano ręcznych pinów zależności przechodnich.

Metadane zweryfikowano 2026-11-12 w Maven Central i npm. Wynik wyszukiwarki Maven Central był opóźniony (pokazywał linię 3.x), dlatego wersje JVM potwierdzono w oficjalnych metadanych artefaktów i przez pobranie podczas kompilacji.

Źródła: [Maven Central — Spring Boot](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml), [springdoc](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml), [ArchUnit](https://repo.maven.apache.org/maven2/com/tngtech/archunit/archunit-junit5/maven-metadata.xml), [properties-maven-plugin](https://repo.maven.apache.org/maven2/org/codehaus/mojo/properties-maven-plugin/maven-metadata.xml), [npm registry](https://registry.npmjs.org/).

## Zmienione wersje bezpośrednie

| Obszar | Biblioteka | Przed | Po |
| --- | --- | ---: | ---: |
| Backend | Spring Boot parent/BOM | 4.1.0 | 4.1.2 |
| Backend | springdoc OpenAPI | 3.0.3 | 3.2.0 |
| Backend test | ArchUnit | 1.4.1 | 1.5.1 |
| Backend local profile | properties-maven-plugin | 1.2.1 | 1.4.0 |
| Web | Angular runtime, compiler, router, forms, browser, animations | 22.0.x | 22.2.6 |
| Web | Angular CLI, build, CDK, Material | 22.0.x | 22.2.6 |
| Web | keycloak-js | 26.2.4 | 26.2.5 |
| Web | RxJS | 7.8.0 | 7.8.2 |
| Web | tslib | 2.3.0 | 2.9.1 |
| Web dev | axe-core/playwright | 4.12.1 | 4.14.0 |
| Web dev | OpenAPI Generator wrapper | 2.40.0 | 2.42.2 |
| Web dev | Playwright | 1.61.1 | 1.65.0 |
| Web dev | jsdom | 28.0.0 | 28.1.0 |
| Web dev | Prettier | 3.8.1 | 3.10.1 |
| Web dev | TypeScript | 6.0.3 | 6.0.3 |
| Web dev | Vitest | 4.0.8 | 4.1.12 |
| Web generator engine | OpenAPI Generator engine | 7.24.0 | 7.24.0 (pozostaje) |

TypeScript pozostał na 6.0.3, ponieważ Angular 22.2 wymaga `>=6.0 <6.1`; nowsza opublikowana linia TypeScript 7 nie spełnia tej granicy. Node 22.22.3 nie został zmieniony. Wrapper npm OpenAPI Generator zaktualizowano do 2.42.2, ale współdzielony silnik web pozostaje na 7.24.0 do osobnej, kontrolowanej migracji wygenerowanego klienta. Lockfile aktualizuje też zależności przechodnie do wersji wybranych przez npm dla powyższych pinów.

## Błędy i ograniczenia dla analityka

| Id / obszar | Stan | Wpływ | Dowód i reprodukcja | Następne działanie |
| --- | --- | --- | --- | --- |
| MOB-004 / pełny klient Kotlin | ograniczenie generatora, bez blokady M1 | Pełny klient OpenAPI dla Androida nie jest przyjęty; nie wpływa na zbudowany fundament M1 | OpenAPI Generator 7.26.0 z Kotlin 2.4.21, Retrofit 3.0.0 i Gradle 8.14.3-bin dochodzi do błędów dziedziczenia `Dose` (`oneOf` + `allOf`) dla kotlinx i Gson | Nie zmieniać backendu tylko dla generatora; ewentualny powrót do pełnej generacji wymaga osobnego override szablonu lub naprawy generatora |
| OpenAPI TypeScript / samodzielny package | ograniczenie narzędzia | Nie blokuje aplikacji webowej | Tymczasowy klient 7.26.0 z domyślnym `tsconfig` generatora (ES6) zgłasza TS4113 dla istniejącej poprawki `FetchError.override cause`; build Angular przechodzi | Wspieranym przepływem jest build Angular z ES2022; nie rozszerzono patcha generatora bez potrzeby produktu |
| Gradle 8.14.5 | ograniczenie środowiska | Nie jest błędem aplikacji | Pobranie wrappera 8.14.5 było uszkodzone; Gradle 8.14.3-bin wykonał kontrolny build | Używać 8.14.3-bin w eksperymencie; nie deklarować 8.14.5 jako przetestowanego |
| WEB-GEN-7.26 / generator TypeScript | odroczona migracja narzędzia | Nie jest regresją działającej aplikacji webowej | Generator 7.26 zmienia 410 ścieżek wygenerowanego klienta, a 23 istniejące modele nie są ponownie generowane; tymczasowy build Angular przechodzi, ale `api:verify` w czystym CI nie byłby spójny z zachowaną historią klienta | Pozostawić silnik web 7.24.0; osobno zaplanować pełną regenerację, przegląd 410 zmian i rozstrzygnięcie 23 modeli |

Nie wykryto regresji backendu ani webu przypisanej do aktualizacji. Ostrzeżenia o budżetach bundle/CSS podczas builda Angular były już regułami budżetów i nie zatrzymały kompilacji. Pierwotny snapshot MOB-004 zatrzymał M1 z powodu pełnego generatora Kotlin; później zamknięto M1 małym, ręcznie typowanym adapterem HTTP dla czterech odczytów. Szczegóły i aktualny wynik są w [M1 — minimalny fundament Android](mobile/07-m1-android-build.md). Testów urządzenia, emulatora i runtime HTTP Android nie wykonano.

## Walidacja

- `mvn -q -DskipTests compile` — sukces.
- `mvn -q -Dtest=OpenApiSnapshotIntegrationTest -Dopenapi.snapshot=/tmp/moves-openapi-snapshot.json test` — sukces; plik tymczasowy jest identyczny z bieżącym `web/openapi/openapi.json`.
- `npm ci --ignore-scripts` — sukces.
- `npm run build` — sukces; log: `/tmp/moves-web-build.log`.
- `npm run build` w tymczasowej kopii aplikacji z klientem TypeScript wygenerowanym przez 7.26.0 — sukces; log: `/tmp/moves-web-7.26-candidate-build.log`. Jest to dowód braku regresji aplikacji, nie akceptacja migracji generatora; bieżące, zmodyfikowane przez użytkownika źródła generatora w repozytorium nie zostały nadpisane.
- `npm test` — 245 z 246 testów przeszło. Jeden istniejący test `participant-documentation.component.spec.ts` oczekuje tekstu `Uzupełniony`, a bieżący zmodyfikowany komponent renderuje `Ukończony`; nie zmieniano tego niezwiązanego kodu. Log: `/tmp/moves-web-test.log`.
- M1 po decyzji o ręcznej granicy HTTP: `:app:compileDevDebugKotlin`, `:app:testDevDebugUnitTest` (5/5) oraz `:app:assembleDevDebug` i `:app:assembleProdDebug` — sukces. `actionlint` zaakceptował workflow Android. Nie uruchomiono zdalnego CI, ADB, emulatora ani urządzenia; szczegóły są w [M1 — minimalny fundament Android](mobile/07-m1-android-build.md).

Logi eksperymentów i buildów pozostają w `/tmp`; nie są artefaktem produktu ani dowodem wdrożenia na urządzeniu.
