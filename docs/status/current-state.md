# Stan bieżący

_Zaktualizowano: 2026-07-27._

## Źródła prawdy

- Kontrakt HTTP: `web/openapi/openapi.json` oraz generowany z niego klient w
  `web/src/app/api/generated/`.
- Konfiguracja lokalnego środowiska: `.env.example`, `compose.yaml` i główny
  `README.md`.
- Historyczne raporty oraz audyty pozostają w `docs/` jako materiały
  referencyjne i nie określają bieżącego stanu wdrożenia.

## Tożsamość uczestnika i rekordy klientów

- `V036__add_participant_records` jest utrwaloną migracją kanonicznych
  kartotek i linków dostępu. `V037__stabilize_client_create_idempotency`
  utrwala fingerprint żądania tworzenia kartoteki i indeks aktywnej relacji;
  istniejący replay bez zapisanego payloadu jest celowo odrzucany.
- Tabele kanoniczne dla nowych kartotek to `participant.participant_record` oraz
  `participant.participant_access_link`. Rekord uczestnika może nie mieć konta.
- `participantId` jest kanoniczną granicą kartoteki i relacji specjalisty.
  Link dostępu do konta pozostaje opcjonalny i jest jedyną drogą z kartoteki do
  `principalAccountId`. Starsze `*AccountId` pozostają lokalnymi mostami
  odczytowymi modułów, które nie zostały jeszcze przeniesione.
- Brak konta nie blokuje pracy specjalisty: można utworzyć i otworzyć kartotekę,
  planować terminy oraz prowadzić workspace. Niedostępne pozostają wyłącznie
  działania wymagające samodzielnego konta uczestnika.

## Workspace specjalisty i lokalne operacje

- Widok listy klientów oraz workspace kartoteki korzystają z wygenerowanego
  kontraktu specjalisty i identyfikatora `participantId`. Workspace łączy
  profil, plan, terminy, sygnały wymagające uwagi i timeline, bez przejmowania
  własności danych przez frontend.
- Widok `Today` prezentuje terminy, wolne sloty i worklistę w utrwalonej strefie
  specjalisty. Testy komponentów pokrywają geometrię slotów, prezentację i
  przejścia workspace; pozostały dług UX to E2E dla mobile viewport, zoomu
  200%, klawiatury i reduced motion.
- Lokalne Compose włącza wyłącznie testowe override'y zgody i zweryfikowanego
  scope'u zawodowego. Nie są one aktywne w profilu produkcyjnym.

## Migracje Flyway

- `bin/flyway-migrate` i `bin/flyway-repair` uruchamiają cele Flyway Maven z
  profilem `local` i parametrami z `.env`; naprawa historii jest operacją
  administracyjną po uprzedniej diagnozie.
- Skrypty wymagają SDKMAN oraz Java 25.0.2-open. Flyway pozostaje właścicielem
  schematu, a Hibernate działa w trybie walidacji.

## Obowiązkowa weryfikacja CI

CI wykonuje `mvn verify`, deterministyczną instalację zależności frontendu,
testy jednostkowe bez watch mode, produkcyjny build Angulara oraz weryfikację
snapshotu i generatora OpenAPI. Smoke test Docker Compose wymaga dostępnego
silnika Docker i nie jest zastępstwem dla tych weryfikacji w środowisku bez
socketu Dockera.

Weryfikacja OpenAPI odświeża specyfikację i klient przez `npm run api:refresh`,
a następnie wymaga braku różnic. Wygenerowane pliki są aktualizowane wyłącznie
tym przepływem, nie ręcznie.

Snapshot i wygenerowany klient obejmują aktualne endpointy kartoteki
specjalisty, workspace i `Today`. Lokalnie `npm run api:refresh` wymaga działającego
Docker/Testcontainers; bez socketu Dockera nie wolno ręcznie aktualizować
snapshotu ani klienta, a odświeżenie pozostaje do wykonania w środowisku z
Dockerem.
