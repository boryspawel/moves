# ADR-004: REST i OpenAPI

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Web, późniejszy mobile i integracje wymagają jednego, wersjonowanego kontraktu. GraphQL i ręcznie utrzymywane klienty nie przynoszą wartości w MVP.

## Decyzja

Publiczny kontrakt wewnętrzny używa REST/JSON pod `/api/v1`, OpenAPI, DTO oddzielonych od encji i Problem Details. Klient TypeScript jest generowany z kontraktu. Retry-sensitive commands przyjmują `Idempotency-Key`.

Aktualny kontrakt backendu obejmuje `GET /api/v1/specialist/today` oraz komendy
terminów pod `/api/v1/specialist/appointments`. Wersjonowany snapshot OpenAPI i
wygenerowany klient nie zawierają jeszcze endpointu `Today`, dopóki backend nie
uruchomi workflow `api:refresh`. Frontend używa więc tymczasowego adaptera
zgodnego w czasie wykonania z wygenerowanym klientem (`BaseAPI` i jego
konfiguracja autoryzacji); nie jest to ręcznie utrzymywany docelowy kontrakt.

## Konsekwencje

Zmiany kontraktu są testowane, a frontend nie duplikuje modeli. Filtrowanie jest ograniczone do jawnie dozwolonych pól. Po uruchomieniu backendowego snapshotu należy zregenerować klienta, aby usunąć tymczasowy adapter.
