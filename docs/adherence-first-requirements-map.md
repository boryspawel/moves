# Mapa wymagań adherence-first do kodu

Bieżącym źródłem zakresu jest [`prompt.md`](../prompt.md); starszy
`docs/moves-codex-implementation-prompts-training.md` ma inną numerację i nie
jest statusową roadmapą adherence.

| Wymaganie badania | Istniejący element | Luka / etap |
| --- | --- | --- |
| wersjonowany plan i aktywacja | `trainingplanning`, `planworkflow`, `PlanRevisionQueryPort` | warianty dostarczone — 2 |
| termin i okno sesji | `scheduledDate`, `availableFrom`, `availableTo`, `TodayAgenda` | dostarczone — 1 |
| bezpieczne wykonanie | `SessionExecutionAttemptService`, postęp i append-only execution | dostarczone — 3 |
| safety envelope bez rationale | `SafetyAssessmentPort` i bramkowanie startu/wznowienia | dostarczone; bez automatycznej decyzji klinicznej — 1–5 |
| zgłoszenie bariery | `BarrierReport`, `BARRIER_RESPONSE_V1`, audit i idempotencja | dostarczone — 4 |
| powrót po przerwie | `RecoveryEpisode`, oferta i idempotentny wybór | dostarczone — 5 |
| decyzja specjalisty | V027/V028: active dedupe, statusy, issue/reply, resource authorization | dostarczone w `d004a36` — 6 |
| participant UX | `/sessions`: today → wariant → check-in → guided attempt/result, `sessionStorage` resume | zmiany P7 niecommitowane; warianty wycinają istniejące recepty, nie treść serwerowego wariantu — 7 |
| specialist UX V2 | worklist API istnieje; `/specialist-alerts` jest legacy | legacy wymaga deprecacji/naprawy — 8 |
| powiadomienia | brak modułu `notification` | P9 niewdrożone |
| mierzenie adherence | wykonania/projekcje jako dane źródłowe | P10 niewdrożone: brak modułu metryk/analityki |
| gamifikacja | osobny `gamification`, ADR-007 | pozostaje opt-in, poza primary flow |

Każdy odczyt i zapis pozostaje ograniczony aktorem i zasobem; akcje specjalisty
wymagają capability, relacji, consent i purpose. Audit i idempotencja chronią
operacje ponawialne. Neutralna worklista oraz przyszłe notifications nie
przenoszą pełnej historii ani danych klinicznych.
