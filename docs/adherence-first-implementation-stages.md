# Etapy implementacji MVP adherence-first

Ten dokument uszczegóławia ADR-011. Etapy wykonujemy kolejno; każdy zachowuje
niezmienniki ADR-008 i ADR-009 oraz kończy się testami granic modułów,
autoryzacji, idempotencji i dostępności adekwatnymi do zakresu.

| Etap | Rezultat | Właściciel danych / zmiana kontraktu |
| --- | --- | --- |
| 0 | ADR, mapa luk i plan wycofań legacy | dokumentacja |
| 1 | `GET` agenda „Dzisiaj” | projekcja `adherence`, wejścia przez porty |
| 2 | zatwierdzone warianty sesji | `trainingplanning`, snapshot rewizji |
| 3 | próba prowadzonego wykonania | `trainingexecution`, finalne execution append-only |
| 4 | bariera i deterministyczna reakcja | `adherence`, sygnały do safety/specialist |
| 5 | epizod powrotu po przerwie | `adherence` projection/aggregate |
| 6 | worklista i kontakt asynchroniczny | `specialist` |
| 7 | participant today-only shell | Angular, kontrakty etapów 1–5 |
| 8 | panel specjalisty V2 | Angular, wyłącznie kontrakty V2 |
| 9 | reminders rules-first | `notification`, decyzje adherence |
| 10 | metryki, eksperymenty i audyt slice | neutralna analityka oraz dokumentacja |

## Wycofywanie legacy

Nowe ekrany nie używają: `POST /api/v1/training-plans` (legacy plan creation),
`GET /api/v1/planned-sessions` jako participantowego ekranu głównego,
`POST /api/v1/planned-sessions/{sessionId}/executions` jako formularza „po
wszystkim” ani `GET /api/v1/specialist/participants/{id}/executions` jako
worklisty. Odpowiadające im widoki Angular (`/plan`, `/sessions`,
`/specialist-alerts`) pozostają do czasu potwierdzenia braku klientów, a potem
są oznaczane deprecated przed usunięciem. Endpointy nie są usuwane w MVP.

## Niezmienniki wdrożeniowe

- Flyway jest jedynym źródłem DDL i migracje są forward-only.
- Zapis mutujący ma audit, kontrolę właściciela zasobu i klucz idempotencji,
  jeżeli klient może go ponowić.
- Safety ma pierwszeństwo; brak lub stary assessment nie jest obchodzony przez
  wariant, barierę, powrót, reminder ani eksperyment.
- Dane kliniczne i swobodny tekst nie są zawartością neutralnych powiadomień,
  worklisty ani analityki.
