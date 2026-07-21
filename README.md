# Motion Ecosystem (`moves`)

Lokalny stos uruchamia się przez Docker Compose: PostgreSQL, Keycloak, backend Spring Boot i produkcyjny build Angulara serwowany przez Nginx. Nie trzeba uruchamiać `mvn spring-boot:run` ani `npm start` na hoście.

## Wymagania

- Docker Engine z Docker Compose v2;
- wolne porty `4200`, `8080`, `8180` i `5432`.

## Uruchomienie

Utwórz lokalną konfigurację sekretów (plik jest ignorowany przez Git):

```bash
cp .env.example .env
```

Wartości w `.env.example` są wyłącznie demonstracyjne i nie nadają się do środowiska współdzielonego ani produkcyjnego. Następnie uruchom cały stos jednym poleceniem:

```bash
docker compose up --build
```

Adresy:

- aplikacja: `http://localhost:4200`;
- backend: `http://localhost:8080` (`/actuator/health`, `/v3/api-docs`, `/swagger-ui.html`);
- Keycloak: `http://localhost:8180` (administrator: `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` z `.env`);
- PostgreSQL: `localhost:5432`.

Realm `motion-local` i klient `motion-web` są importowane przy pierwszym uruchomieniu. Lokalny użytkownik demonstracyjny ma już profil, zgody i dostępność w bazie aplikacji: `demo.participant` / `demo-participant-local-only`. Hasło jest jawnie demonstracyjne i nie może być używane poza lokalnym środowiskiem. Na ekranie logowania przycisk **Utwórz konto** uruchamia standardową rejestrację Keycloak; profil aplikacji jest następnie tworzony podczas onboardingu.

Lokalny przepływ importu jest dostępny dla `demo.editor` / `demo-editor-local-only` pod `/admin/exercise-import`. Przed importem fixture należy utworzyć i opublikować odpowiadające mu fikcyjne struktury anatomii przez API administracyjne. Format, endpointy, wznowienie i retencję opisuje [dokument importu](docs/exercise-import.md); decyzję architektoniczną zapisuje [ADR-010](docs/adr/ADR-010-mass-exercise-import.md).

Zatrzymanie bez kasowania danych:

```bash
docker compose down
```

Świadome skasowanie wyłącznie danych tego projektu:

```bash
docker compose down --volumes
```

Ponowne zbudowanie aplikacji:

```bash
docker compose build --no-cache
docker compose up
```

## Konfiguracja i topologia

Frontend używa `/api` na tym samym originie; Nginx przekazuje ten prefiks do usługi `backend`. Dzięki temu zwykłe wywołania aplikacji nie wymagają CORS. Backend jest także wystawiony na `8080` dla OpenAPI i diagnostyki.

Keycloak emituje tokeny z publicznym issuerem `http://localhost:8180/realms/motion-local`. Backend zachowuje walidację `iss` względem tego adresu, ale pobiera JWK po wewnętrznym adresie `http://keycloak:8080/...`, więc kontenery komunikują się po nazwach usług. Adres Keycloak w produkcyjnym bundle Angulara jest jednorazowo wstrzykiwany z `KEYCLOAK_HOST_PORT` podczas buildu; nie jest to ustawienie z developmentowego `environment.ts`.

Domyślne porty są częścią kontraktu lokalnego realm importu. Dla zmienionych portów należy odpowiednio zmienić `redirectUris` i `webOrigins` w `infra/keycloak/motion-local-realm.json` przed pierwszym importem realm. Konfiguracja produkcyjna powinna mieć konkretne HTTPS originy, zarządzane sekrety i trwałą konfigurację Keycloak.

PostgreSQL 18 używa nazwanego wolumenu `motion-postgres` pod `/var/lib/postgresql`, zgodnie ze zmienionym `PGDATA` obrazu 18. Flyway pozostaje właścicielem schematu, a Hibernate działa w trybie `validate`.
Oryginalne artefakty JSONL są przechowywane w osobnym nazwanym wolumenie `motion-exercise-import`; zwykły restart stosu zachowuje oba wolumeny.

## Diagnostyka i smoke test

```bash
docker compose ps
docker compose logs -f backend keycloak
bash scripts/compose-smoke.sh
```

Smoke test uruchamia osobny projekt Compose z oddzielnym wolumenem i portami, czeka na healthchecki, weryfikuje frontend, routing Angulara, Actuator, konfigurację OIDC i odpowiedź `401` chronionego endpointu, po czym usuwa wyłącznie własne zasoby. Przy błędzie wypisuje logi usług.

Do lokalnych testów Java nadal aktywuj Java 25 w SDKMAN przed Mavenem:

```bash
source /home/pb/.sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-tem
mvn verify
```
