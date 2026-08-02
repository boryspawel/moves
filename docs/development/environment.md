# Lokalne środowisko deweloperskie

## Wymagania

- Docker Engine z Docker Compose v2;
- wolne domyślne porty `4200`, `8080`, `8180` i `5432`;
- lokalny plik `.env`, utworzony z `.env.example`.

`.env` nie jest wersjonowany. Zawiera lokalne hasła PostgreSQL i administratora
Keycloak, więc nie należy go udostępniać ani używać jego demonstracyjnych wartości
poza środowiskiem lokalnym. Obsługiwane zmienne to `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` oraz porty
`FRONTEND_HOST_PORT`, `BACKEND_HOST_PORT`, `KEYCLOAK_HOST_PORT` i
`POSTGRES_HOST_PORT`.

## Start i zatrzymanie

```bash
cp .env.example .env
bash scripts/dev-env-up.sh
```

Skrypt najpierw sprawdza konfigurację Compose, następnie buduje obrazy i czeka do
300 sekund na healthchecki wszystkich usług. Na końcu wypisuje `docker compose ps`.
Po błędzie pokazuje status oraz ostatnie 100 linii logów; dalsza diagnostyka to
`docker compose logs -f backend keycloak`.

Standardowe adresy (przy domyślnych portach) to:

- aplikacja: `http://localhost:4200`;
- backend: `http://localhost:8080`, health: `/actuator/health`, OpenAPI:
  `/swagger-ui.html` i `/v3/api-docs`;
- Keycloak: `http://localhost:8180`;
- PostgreSQL: `localhost:5432`.

Zatrzymanie bez utraty danych:

```bash
docker compose down
```

Usługi mają politykę restartu `unless-stopped`. PostgreSQL i artefakty importu
pozostają odpowiednio w nazwanych wolumenach `motion-postgres` i
`motion-exercise-import`, także po zwykłym zatrzymaniu lub restarcie stosu.

## Realm, baza i reset

Keycloak importuje wersjonowany plik realm `motion-local-realm.json` podczas startu.
Nie ma osobnego trwałego wolumenu bazy Keycloak: zachowuje to deterministyczność
importu realm. Jeśli zmieniasz port Keycloak lub frontendu, przed startem dostosuj
`redirectUris` i `webOrigins` w tym pliku importu.

Backend uruchamia migracje Flyway przy starcie. Flyway jest właścicielem schematu, a
Hibernate wyłącznie go waliduje. Nie usuwaj wolumenów, aby „naprawić” pojedynczą
migrację — najpierw sprawdź log backendu i historię Flyway.

Pełny reset lokalnych danych jest świadomą, destrukcyjną operacją:

```bash
docker compose down --volumes
```

Następny start odtworzy PostgreSQL, ponownie wykona Flyway i ponownie zaimportuje
wersjonowany realm.

## Ograniczenie Playwright

Scenariusz runtime QA mapy ciała wymaga lokalnego Compose, poświadczeń specjalisty
i przeglądarki Chromium. W środowiskach bez możliwości uruchomienia sandboxa
Chromium test Playwright może nie wystartować; nie jest wtedy wskaźnikiem stabilności
środowiska DEV-ENV. Nie omijaj tego ograniczenia przez wyłączanie sandboxa bez
odrębnej decyzji bezpieczeństwa.
