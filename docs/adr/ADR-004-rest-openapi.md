# ADR-004: REST i OpenAPI

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Web, późniejszy mobile i integracje wymagają jednego, wersjonowanego kontraktu. GraphQL i ręcznie utrzymywane klienty nie przynoszą wartości w MVP.

## Decyzja

Publiczny kontrakt wewnętrzny używa REST/JSON pod `/api/v1`, OpenAPI, DTO oddzielonych od encji i Problem Details. Klient TypeScript jest generowany z kontraktu. Retry-sensitive commands przyjmują `Idempotency-Key`.

## Konsekwencje

Zmiany kontraktu są testowane, a frontend nie duplikuje modeli. Filtrowanie jest ograniczone do jawnie dozwolonych pól.
