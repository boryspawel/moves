# ADR-004: REST i OpenAPI

- Status: przyjęta
- Data: 2026-07-20

## Kontekst

Web, późniejszy mobile i integracje wymagają jednego, wersjonowanego kontraktu. GraphQL i ręcznie utrzymywane klienty nie przynoszą wartości w MVP.

## Decyzja

Publiczny kontrakt wewnętrzny używa REST/JSON pod `/api/v1`, OpenAPI, DTO oddzielonych od encji i Problem Details. Klient TypeScript jest generowany z kontraktu. Retry-sensitive commands przyjmują `Idempotency-Key`.

Aktualny snapshot OpenAPI i wygenerowany klient obejmują `Today`, komendy
terminów oraz specjalistyczne flow kartoteki (`/api/v1/specialist/clients`),
workspace i timeline z `participantId`. Frontend używa wygenerowanych API, bez
tymczasowego adaptera kontraktu. Odświeżenie nadal odbywa się wyłącznie przez
`npm run api:refresh`; wymaga ono środowiska z Docker/Testcontainers.

## Konsekwencje

Zmiany kontraktu są testowane, a frontend nie duplikuje modeli. Filtrowanie jest ograniczone do jawnie dozwolonych pól. W środowisku bez socketu Dockera nie należy ręcznie zmieniać snapshotu ani wygenerowanego klienta; odświeżenie wykonuje się w dostępnej walidacji Dockerowej.
