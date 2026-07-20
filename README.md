# Motion Ecosystem (`moves`)

Robocze repozytorium modularnego monolitu opisanego w `spec.md`. Nazwy wewnętrzne są neutralne; `moves` nie jest utrwalane jako nazwa domeny ani prefiks pakietu.

## Wymagania

- SDKMAN z Java `25.0.1-tem`;
- Maven 3.9+;
- Docker z Compose (dla PostgreSQL, Keycloak i testów Testcontainers).

Przed każdym lokalnym buildem lub testem aktywuj Java 25 w tej samej powłoce:

```bash
source /home/pb/.sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-tem
mvn verify
```

## Lokalne uruchomienie

```bash
docker compose up -d postgres keycloak
source /home/pb/.sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-tem
mvn spring-boot:run
```

- health: `http://localhost:8080/actuator/health`;
- OpenAPI: `http://localhost:8080/v3/api-docs`;
- Swagger UI: `http://localhost:8080/swagger-ui.html`;
- Keycloak: `http://localhost:8180`, lokalny realm `motion-local`.

Domyślne dane dostępowe są wyłącznie lokalne i mogą zostać nadpisane zmiennymi `DB_*` oraz `OIDC_*`.

## Granice modułów

Każdy główny pakiet pod `com.motionecosystem` jest granicą modułu. Szczegóły `domain`, `application` i `infrastructure` nie są kontraktami między modułami; współpraca odbywa się przez jawne API aplikacyjne lub zdarzenia. Test ArchUnit wykrywa cykle. Aktualnie istnieją tylko moduły wymagane przez fundament: `identityaccess` i `audit`; kolejne powstają wraz z pierwszym przypadkiem użycia.

## Migracja

Repozytoria `../gra-mateusza` i `../ruszsie` są źródłami read-only. Nie wykonujemy merge historii ani cherry-picków. Decyzje i SHA są w `docs/migration/`.
