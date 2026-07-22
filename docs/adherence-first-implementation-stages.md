# Etapy implementacji MVP adherence-first

Ten dokument uszczegóławia ADR-011. Bieżącym źródłem zakresu jest
[`prompt.md`](../prompt.md); starszy
`docs/moves-codex-implementation-prompts-training.md` ma inną numerację i nie
jest statusową roadmapą adherence. Etapy wykonujemy kolejno; każdy zachowuje
niezmienniki ADR-008 i ADR-009 oraz kończy się testami granic modułów,
autoryzacji, idempotencji i dostępności adekwatnymi do zakresu.

| Etap | Rezultat | Właściciel danych / zmiana kontraktu |
| --- | --- | --- |
| 0 | ADR, mapa luk i plan wycofań legacy | dokumentacja |
| 1 | `GET` agenda „Dzisiaj” — dostarczone | projekcja `adherence`, wejścia przez porty |
| 2 | zatwierdzone warianty sesji — dostarczone | `trainingplanning`, snapshot rewizji |
| 3 | próba prowadzonego wykonania i postęp — dostarczone | `trainingexecution`, finalne execution append-only |
| 4 | bariera i deterministyczna reakcja — dostarczone | `adherence`, sygnały do safety/specialist |
| 5 | epizod powrotu, oferta i wybór — dostarczone | `adherence` projection/aggregate |
| 6 | worklista, issue i reply — dostarczone w `d004a36` | `specialist`, V027/V028 |
| 7 | participant today-only flow — bieżące zmiany niecommitowane | Angular, kontrakty etapów 1–5 |
| 8 | panel specjalisty V2 — dostarczony | Angular, wyłącznie kontrakty V2 i minimalna projekcja aktywnej relacji |
| 9 | reminders rules-first — preferencje i deterministyczne reguły | `notification.reminders`, neutralny audit/dedupe |
| 10 | metryki, trzy eksperymenty i audyt slice — dostarczone | `analytics.adherencemetrics`, V032 i dokumentacja końcowa |

P7 prowadzi przez `/sessions`: `TodayAgenda` → wariant → check-in → próba i
postęp → wynik albo bariera. Id aktywnej próby jest w `sessionStorage`; UI
ponownie wiąże dokładny `sessionId` z receptami planowanej sesji. Warianty UI
tymczasowo wycinają istniejące recepty (`STANDARD`/`SHORT`/`MINIMUM`), a nie
renderują zdefiniowanej przez serwer treści klinicznego wariantu.

## Wycofywanie legacy

Nowe ekrany nie używają: `POST /api/v1/training-plans` (legacy plan creation),
`GET /api/v1/planned-sessions` jako participantowego ekranu głównego,
`POST /api/v1/planned-sessions/{sessionId}/executions` jako formularza „po
wszystkim” ani `GET /api/v1/specialist/participants/{id}/executions` jako
worklisty. Odpowiadające im widoki Angular (`/plan`, `/sessions`,
`/specialist-alerts`) pozostają do czasu potwierdzenia braku klientów, a potem
są oznaczane deprecated przed usunięciem. `/specialist-alerts` nadal eksponuje
ręczny UUID i surowe widoki wykonań; wymaga osobnej deprecacji albo naprawy.
Endpointy nie są usuwane w MVP. Ekrany `/plan` i `/specialist-alerts` korzystają
odpowiednio z V2 workflow oraz worklisty; nie przyjmują ani nie wyświetlają
UUID. Selektor uczestnika pobiera wyłącznie aktywną relację specjalisty i
prezentuje minimalną etykietę UI.

## Niezmienniki wdrożeniowe

- Flyway jest jedynym źródłem DDL i migracje są forward-only.
- Zapis mutujący ma audit, kontrolę właściciela zasobu i klucz idempotencji,
  jeżeli klient może go ponowić.
- Safety ma pierwszeństwo; brak lub stary assessment nie jest obchodzony przez
  wariant, barierę, powrót, reminder ani eksperyment.
- Dane kliniczne i swobodny tekst nie są zawartością neutralnych powiadomień,
  worklisty ani analityki.
- Zdarzenia metryk adherence wygasają po 180 dniach; automatyczne, codzienne
  zadanie wewnętrzne wywołuje idempotentne `purgeExpired()`. Nie jest to kanał
  SaaS ani mechanizm automatycznej decyzji.
- P7 ma testy komponentowe, ale brakuje dowodu E2E dla mobile viewport, 200%
  zoomu, klawiatury i reduced motion.
