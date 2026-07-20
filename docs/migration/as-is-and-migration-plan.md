# Audyt as-is i plan kontrolowanej migracji

Stan audytu: 2026-07-20, Prompt 1. Repozytoria źródłowe analizowano read-only. `spec.md` pozostaje nadrzędną specyfikacją.

## 1. Potwierdzony stan repozytoriów

### `gra-mateusza`

- Ścieżka: `/home/pb/Documents/projects/gra-mateusza`.
- Branch: `main`; aktualny HEAD po zmianie wykonanej poza migracją: `0b994cb6e35319837a2c81de4fb9a556b1943b90`; `origin/main`: `818707b4be91fe87d6a44f132260b9f8d4517f1d`; branch jest ahead 1.
- Working tree po zmianie: nieśledzony `AGENTS.md`. Wcześniejszy pomiar w tym samym audycie pokazywał lokalne modyfikacje, które następnie znalazły się w `0b994cb…`.
- Maven: agregator root → `backend` i techniczny `sql`; backend → uruchamialne `core-service` i `api-gateway`.
- Wersje: Java 21 i Spring Boot 3.3.0 z `backend/pom.xml`; aktywny Angular deklaruje `@angular/core ^17.3.0` i TypeScript `~5.3.3`.
- Deploymenty: osobne kontenery `core-service`, Spring Cloud `api-gateway` i Angular/nginx; infrastruktura Postgres 16, Flyway CLI 10.15, Keycloak 26.4 i MinIO `latest`.
- Baza: migracje w `sql/migration`, uruchamiane osobnym kontenerem; aplikacja ma `ddl-auto=none`, lecz nie ma zależności runtime Flyway.
- Keycloak: Resource Server w gateway/core, realm export i konwerter client/realm roles. Istotna wada: core dopuszcza całe `/api/training/**` bez uwierzytelnienia.
- Testy: JUnit 5 i Testcontainers PostgreSQL; niewiele testów zachowań. Angular ma Karma/Jasmine, a katalog `frontend/` zawiera Cypress i generowanie klientów API.
- Frontend aktywny: `frontend/power-rpg-frontend`; `frontend/src` jest niekompletnym legacy/scaffoldem i nie jest deploymentem.

### `ruszsie`

- Ścieżka: `/home/pb/Documents/projects/ruszsie`.
- Branch: `main`; HEAD i `origin/main`: `2d7656e8b1b8d5da145a1bf570a6985fa6b0b033`; working tree czysty.
- Jeden uruchamialny moduł Maven/JAR, Java 25, Spring Boot 4.1.0; brak frontendu Angular.
- PostgreSQL/Flyway: sześć migracji, osobne schematy `identity`, `onboarding`, `discovery`, `activity`, `safety`; JPA `ddl-auto=validate`; Testcontainers `postgres:17-alpine` przez `@ServiceConnection`.
- `compose.yaml` jest pusty; brak Dockerfile, więc obrazu runtime nie da się potwierdzić.
- Brak Keycloak. Resource Server waliduje tokeny emitowane przez własny prototypowy HS256/JWT; repo zawiera sesje, rodziny refresh tokenów i Google ID-token verifier.
- API domenowe jest warunkowe i żyje pod `/api/prototype/**`.
- Testy integracyjne MockMvc/Testcontainers szeroko sprawdzają identity, onboarding, discovery, activity i safety.

### Niedostępne wskazane obiekty

SHA `bdb481b3e7bef665e76ecf27a40f1a630cb6d7f5`, `fd539c32e09824fca944b1e424030971135726b0` i `f02551285956b5ab7dc150f3c36b4bc44dc33db7` nie występują lokalnie w `gra-mateusza`; publiczne URL patchy zwracają 404. Plan opiera się wyłącznie na potwierdzonym HEAD i dostępnej historii. SHA `ruszsie` `5fecadf…` i `2d7656e…` są dostępne i zweryfikowane.

## 2. Inwentaryzacja i decyzje

### `gra-mateusza`

| Element źródłowy | Rodzaj | Cel | Decyzja | Uzasadnienie / adaptacja |
|---|---|---|---|---|
| `training/domain/Exercise`, `ExerciseDifficulty` | model | `exercisecatalog` | REWRITE | Zachować pola instrukcji, sprzętu, trudności i mediów; dodać Exercise/ExerciseVersion, workflow i typowane klasyfikacje. Obecna encja jest swobodnie edytowalna i zawiera RPG `powerMap`. |
| `ExerciseService`, `ExerciseController`, admin DTO/controllers | zachowanie/API | `exercisecatalog` | PORT | Zachować read-only search, ukrywanie niepublicznych i osobny admin workflow; przenieść pod `/api/v1`, filtrować w DB, dodać publikację i autoryzację. |
| `TrainingSet`, `Workout`, `TrainingSetService` | model/zachowanie | `trainingplanning` | REWRITE | Kolejność i zakaz duplikatów są wartościowe; płaskie sety, JSON `items` i brak hierarchii nie spełniają specyfikacji. |
| `training/log/*` | model/API | `execution` | REWRITE | Zachować start/result/finish i RPE jako zachowanie referencyjne; dodać powiązanie z PlannedSession/Prescription, właściciela z tokenu, idempotencję, append-only i korekty. |
| `UserXpService` | reguła | `gamification` | REFERENCE | Formuła level i heurystyka powtórzeń potwierdzają potrzebę anti-farmingu, ale nie ma ledgeru, limitów, cooldown ani idempotencji. |
| `UserXp`, `UserLevelLog`, `UserPower*`, `power_map` | model RPG | `gamification` | REJECT | Nie kopiować power stats ani narracji RPG. Zastąpić append-only PointLedgerEntry i odbudowywalną projekcją. |
| `AppUser.sub`, `AuthenticatedUserService` | identity | `identityaccess` | PORT | Zachować ideę lokalnego mapowania `sub`; przepisać neutralnie, bez zaufania do body userId. |
| konwerter ról Keycloak i realm export | security/infra | `identityaccess` | REFERENCE | Wartościowy wzorzec claim mapping; realm/nazwy/client IDs należy utworzyć od nowa, wraz z audience validation. |
| `api-gateway`, Spring Cloud Gateway | deployment | — | REJECT | Docelowo jeden backend MVC; brak uzasadnienia dla gateway/mikroserwisów. |
| `V3`, `V4`, `V008`, `V009` | migracje | katalog/plan/execution | REWRITE | Zachować intencje kolumn; nie kopiować numerów ani schematu. Obecne typy ID są niespójne (BIGINT/UUID), a wersjonowania brak. |
| `V5__seed_exercises.sql` | dane | katalog | REFERENCE | Przykładowe rekordy wyłącznie jako dane syntetyczne po przeglądzie merytorycznym; linki i instrukcje nie są kanoniczne. |
| `V1`, `V1.001`, `V2`, `V006`, `V007` | migracje identity/RPG | — | REJECT | Konflikt numeracji i modeli użytkownika, jawne hasła/role DB, zdublowane power tables i mieszane UUID/BIGSERIAL. |
| testy Postgres support/DataJpa | testy | test support | REFERENCE | Wzorzec kontenera jest użyteczny, lecz testy mają zostać oparte o Spring Boot 4.1 `@ServiceConnection`. |
| aktywny Angular admin exercises/sets, library, profile | UI | nowy Angular | REFERENCE | Zachować przepływy, pola i feedback; napisać standalone Angular 22 z wygenerowanym klientem. |
| XP progress i level-up modal | UI | gamification | REFERENCE | Zachowanie prywatnego postępu może wrócić bez RPG brandingu. |
| `frontend/src` i ręczne API services | legacy UI | — | REJECT | Niekompletny, wersyjnie niespójny i niedeployowany frontend; typy mają pochodzić z OpenAPI. |
| Postgres/Keycloak compose, nginx | infra | local dev | REFERENCE | Zachować topologię lokalną, zneutralizować nazwy i sekrety, usunąć gateway/MinIO z pierwszego etapu. |

### `ruszsie`

| Element źródłowy | Rodzaj | Cel | Decyzja | Uzasadnienie / adaptacja |
|---|---|---|---|---|
| `onboarding/LegalAcknowledgement` + repo | model | `consent` | PORT | Unikalność user/type/version daje idempotencję; dodać template version, status/ważność/revocation i docelowe typy. |
| `UserProfile` | model | participant/specialist | REWRITE | Zachować jeden profil na podmiot i optimistic version; rozdzielić role profilu, nie kopiować wymaganego age band. |
| `RecurringAvailability` + repo | model | `availability` | PORT | Zachować day/time/IANA zone i replacement semantics; dodać walidację overlap oraz przestrzeń na date exceptions. |
| `OnboardingService.stateFor` | zachowanie | onboarding application facade | REWRITE | Zachować wyliczanie z bieżących danych; wynik ma być rozszerzalną listą brakujących kroków zależną od profilu PARTICIPANT/SPECIALIST. |
| `OnboardingController` | API | `/api/v1/onboarding` | REWRITE | Zachować własność wynikającą z JWT; usunąć `/api/prototype`, warunek prototypowej sesji i skompresowane DTO. |
| `OnboardingIntegrationTests` | testy | integration | PORT | Zachować brak tokenu, isolation, active account, idempotencję, invalid zone/time i PostgreSQL; rozbudować role/version/overlap. |
| `ActivityPreference`, `ActivityType` | model matching | — | REJECT | WALKING/NORDIC_WALKING itd. są specyficzne dla RuszSię; nie są profilem treningowym. |
| `AGE_18_DECLARED` jako obowiązkowe | zgoda | future guardian consent | REJECT | Blokuje małoletnich. Nie implementować opiekuna bez reguł; krok może sygnalizować wymagane rozstrzygnięcie. |
| `COMMUNITY_RULES_ACCEPTED` jako obowiązkowe | zgoda | gamification opt-in | REWRITE | Wymagane dopiero po włączeniu funkcji społecznościowej. |
| identity domain/repository ports | architektura | identityaccess | REFERENCE | Porty i status aktywnego konta są dobrym wzorcem, ale konto ma być powiązane z Keycloak `sub`. |
| sesje, refresh token family/token, AccessTokenService | auth | — | REJECT | Keycloak jest jedynym wystawcą tokenów; brak równoległego systemu sesji. |
| Google verifier/external login | auth | — | REJECT | Federacja, jeśli potrzebna, należy do konfiguracji Keycloak. |
| discovery, likes, match, activity session/message | matching | future RuszSię context | REJECT | Poza rdzeniem i dopiero etap 5 roadmapy; `ActivitySession` nie jest `SessionExecution`. |
| safety blocks/reports/moderation | community safety | future RuszSię/moderation | REFERENCE | Nie mylić z medycznym safety silnika treningowego; ewentualnie osobny przyszły kontekst. |
| notifications | model | notification | REFERENCE | Wzorzec per-user read state; typy są dziś zależne od matchingu. |
| `V001`–`V006` | migracje | nowe schematy | REWRITE | Nie kopiować numerów/FK między kontekstami; odtworzyć tylko zatwierdzone modele w liniowej historii `moves`. |
| `PostgresTestConfiguration` i testy MockMvc | test infra | wspólne test support | PORT | Java 25/Boot 4.1 i `@ServiceConnection` są zgodne z celem. |

## 3. Endpointy as-is

`gra-mateusza`: `/api/core/me`, `/me/profile`, `/powers`, `/xp`, `/levels/history`, `/api/core/powers/apply`; katalog `/api/training/exercises`; admin `/api/training/admin/exercises` i `/sets`; logi `/api/training/logs/workout...`. Ścieżki training są obecnie publiczne w core security.

`ruszsie`: wszystkie funkcje są pod `/api/prototype`: auth/session, onboarding, discovery, matches/messages/activity-sessions, safety/reports/moderation i notifications. Żadna ścieżka `/api/prototype` nie przechodzi do celu.

Docelowe kontrakty powstają pod `/api/v1/{onboarding,profiles,availability,consents,exercises,plans,sessions,executions,safety,gamification}` i używają Problem Details oraz `Idempotency-Key` tam, gdzie retry może powielić zapis.

## 4. Proponowana struktura `moves`

Początkowo jeden moduł Maven, aby uniknąć pustych artefaktów; granice są pakietami Spring Modulith/ArchUnit:

```text
src/main/java/com/motionecosystem/
  application/
  identityaccess/
  participant/
  specialist/
  consent/
  availability/
  exercisecatalog/
  trainingplanning/
  execution/
  safety/
  gamification/
  audit/
src/main/resources/db/migration/
web/
docs/adr/
docs/migration/
```

`calendar` i `notification` zostaną utworzone, gdy pierwszy przypadek użycia ich wymaga. Brak pustych pakietów tylko dla zgodności z listą.

## 5. Mapowanie źródło → cel i testy

| Źródło | Cel | Sposób | Zależności | Ryzyko | Wymagane testy |
|---|---|---|---|---|---|
| ruszsie legal acknowledgements | consent | PORT modelu unikalności, REWRITE typów/API | identityaccess, audit | prawna semantyka zgody vs potwierdzenia | wersje, retry, revoke/expiry, ownership |
| ruszsie profile/state | participant/specialist + onboarding facade | REWRITE | identityaccess, consent | rola domenowa vs token role | oba profile, częściowy stan, inactive account |
| ruszsie recurring availability | availability | PORT + overlap validation | identityaccess | DST, strefy, replacement race | IANA zone, reversed/overlap, isolation |
| gra Exercise + admin API | exercisecatalog | REWRITE agregatu, PORT filtrów | identityaccess, audit | niezmienność użytej wersji | publish/withdraw/use, admin/participant, filters |
| gra TrainingSet/Workout | trainingplanning | REWRITE hierarchii | catalog, specialist | konflikt pojęć set/session/workout | hierarchia, relationship authorization, exact version |
| gra training logs | execution | REWRITE append-only | planning, safety, audit | obecne ID i zaufanie do userId | idempotency, declaration, correction history |
| gra XP anti-farming | gamification | REFERENCE algorytmu, nowy ledger | qualified execution event only | farming, leakage medyczny | opt-in, caps, cooldown, diminishing, rebuild |
| gra role converter | identityaccess infra | REWRITE neutralnie | Keycloak config | issuer/audience i role mapping | token claims, missing/invalid audience, role auth |

## 6. Kolejność migracji utrzymująca zielony build

1. Fundament: Boot 4.1/Java 25, PostgreSQL/Flyway, Resource Server, health, OpenAPI, test architektury i Testcontainers.
2. Identity mapping + audit, następnie role profilu, consent i availability jako zamknięty onboarding slice.
3. Exercise Catalog z wersjami i workflow; osobne read/admin API.
4. Safety inputs: ograniczenia, readiness, pain i alert bez diagnozy.
5. Specialist profile + relacja z participant; dopiero potem autoryzacja planowania.
6. Training Planning: goal → plan → cycle → microcycle → planned session → prescription.
7. Execution append-only i korekty; zamknięcie plan → wykonanie → alert.
8. Gamification opt-in, ledger i projekcja karmione tylko kwalifikowanym zdarzeniem.
9. Angular 22 generujący klienta z ustabilizowanego OpenAPI.
10. Pełny audyt, E2E i dopiero decyzja o ustanowieniu `moves` repo głównym.

Po każdym kroku: compile, testy domenowe, test architektury, Testcontainers/Flyway i skan zakazanych zależności/nazw.

## 7. Pierwszy działający vertical slice

Granica: token Keycloak → lokalne aktywne konto → wybór PARTICIPANT lub SPECIALIST → wymagane aktualne potwierdzenia → profil → cykliczna dostępność → stan onboardingu. Slice nie obejmuje matchingu, community rules dla użytkownika bez gamifikacji, opiekuna małoletniego ani własnych sesji.

Następny slice domyka wartość rdzenia: aktywna relacja specjalista–uczestnik → opublikowana wersja ćwiczenia → prosta planowana sesja → deklarowane wykonanie z bólem/trudnością → alert widoczny specjaliście.

## 8. Ryzyka konfliktów

- Tożsamość: `ruszsie` używa lokalnego UUID jako JWT subject, `gra-mateusza` przechowuje Keycloak `sub` przy BIGSERIAL/UUID; cel musi mieć własne UUID i unikalny tekstowy `external_subject`.
- Migracje: oba źródła zaczynają od V1/V001, a `gra-mateusza` ma `V1` i `V1.001`; kopiowanie numerów jest niedopuszczalne.
- Dane: źródłowe FK przekraczają konteksty, typy exercise/workout log nie pasują (BIGINT vs UUID), a część JSONB ukrywa model planu.
- Bezpieczeństwo: training API w źródle jest publiczne; route-level role nie zastępują ownership/relationship/consent.
- Terminologia: `ruszsie ActivitySession` to spotkanie dopasowanych osób, `gra WorkoutLog` to wykonanie, docelowe Appointment/PlannedSession/SessionExecution są trzema odrębnymi pojęciami.
- Gamifikacja: bieżące XP mutuje saldo bez ledgeru i jest sprzężone z logami oraz polami power; wymaga nowego modelu.
- Brak wskazanych SHA `gra-mateusza` uniemożliwia przypisanie im zachowań; implementacja bazuje na dostępnym kodzie, a manifest zachowuje ten fakt.

## 9. Elementy odrzucone

- pełna historia Git, cherry-picki i kopiowanie całych katalogów;
- Spring Cloud Gateway i podział core/api-gateway;
- nazwy/pakiety/narracja RPG, power attributes i `power_map`;
- własne JWT, refresh tokeny, Google login i `/api/prototype`;
- discovery/matching/likes/chat/activity sessions RuszSię;
- oba istniejące drzewa Angular jako baza projektu, w szczególności legacy `frontend/src`;
- źródłowa numeracja migracji, jawne hasła/role DB i niespójne encje JPA;
- diagnozowanie, automatyczne medyczne plany, gateway, Kafka, Redis i Kubernetes w pierwszym etapie.

## 10. Decyzje wymagające zatwierdzenia

1. Docelowa polityka i treść wersjonowanych dokumentów prawnych, w tym obsługa małoletnich/opiekuna.
2. Model organizacji i ustanawiania/kończenia relacji participant–specialist.
3. Zakres danych profilu wymagany osobno dla uczestnika, trenera i fizjoterapeuty.
4. Właściciel publikacji katalogu i wersjonowanych reguł safety.
5. Progi bólu/trudności, HARD_BLOCK i możliwość override — nie wolno ich wymyślić technicznie.
6. Zakres zgód na dane medyczne i okresy retencji/audytu.
7. Reguły punktowe, limity i cooldown gamifikacji.
8. Docelowa domena/realm/client IDs Keycloak oraz model organizacji w tokenach.
9. Czy i kiedy specjalistyczna dostępność zasila calendar/appointment; w onboardingu zapisujemy tylko cykliczne deklaracje.
10. Dostarczenie poprawnych obiektów dla trzech brakujących SHA albo akceptacja bieżącego HEAD jako jedynego źródła.
