# Manifest źródeł migracji

Repozytoria źródłowe są bezwzględnie read-only. Nie wykonujemy w nich commitów, formatowania, cherry-picków ani merge historii. Każdy element przechodzi osobną decyzję PORT/REWRITE/REFERENCE/REJECT opisaną w `as-is-and-migration-plan.md`.

## Rewizje bazowe

| Repozytorium | Branch | SHA użyte jako baza | Stan |
|---|---|---|---|
| `../gra-mateusza` | `main` | `0b994cb6e35319837a2c81de4fb9a556b1943b90` | lokalny branch ahead 1; nieśledzony `AGENTS.md` pozostawiony bez zmian |
| `../ruszsie` | `main` | `2d7656e8b1b8d5da145a1bf570a6985fa6b0b033` | zgodny z `origin/main`, czysty |

Stan `gra-mateusza` zmienił się podczas Promptu 1 poza procesem migracji: wcześniejszy pomiar wskazywał `818707b4be91fe87d6a44f132260b9f8d4517f1d`, następnie lokalne zmiany zostały zapisane jako `0b994cb…`. Dalsze etapy ponownie sprawdzają SHA przed portowaniem.

## Elementy przeznaczone do kontrolowanego użycia

### `ruszsie`

- onboarding z `5fecadfa4ab99a9f27f26084b19013ef22aafbc7` i formatowanie modeli z `2d7656e8b1b8d5da145a1bf570a6985fa6b0b033`;
- idempotentne potwierdzenia typu + wersja;
- profil, aktywny status konta i izolacja danych;
- cykliczne przedziały z IANA time zone;
- Testcontainers PostgreSQL, Flyway, MockMvc i `@ServiceConnection`.

Nie portujemy własnych JWT/sesji/refresh tokenów, Google login, `/api/prototype`, ActivityPreference ani matching/discovery/activity.

### `gra-mateusza`

- filtrowanie i rozdział read/admin katalogu ćwiczeń;
- pola instrukcji, mediów, sprzętu i trudności jako materiał do nowego ExerciseVersion;
- kolejność ćwiczeń oraz walidacja duplikatów w zestawie;
- zachowania start/add result/finish wykonania;
- heurystyki anti-farming i poziomów wyłącznie jako materiał do nowego ledgeru;
- wzorzec mapowania ról Keycloak;
- aktywne ekrany Angular jako źródło przepływów, nie kod bazowy.

Nie portujemy pakietów/nazw RPG, Spring Cloud Gateway, power stats, źródłowych migracji, otwartej konfiguracji `/api/training/**` ani legacy `frontend/src`.

## Brakujące wskazane obiekty

Poniższe SHA nie są dostępne lokalnie i publiczne URL patch zwróciły HTTP 404 dnia 2026-07-20:

- `bdb481b3e7bef665e76ecf27a40f1a630cb6d7f5`;
- `fd539c32e09824fca944b1e424030971135726b0`;
- `f02551285956b5ab7dc150f3c36b4bc44dc33db7`.

Nie przypisujemy im funkcjonalności. Jeśli zostaną dostarczone później, wymagają osobnego diffu względem zapisanej bazy.
