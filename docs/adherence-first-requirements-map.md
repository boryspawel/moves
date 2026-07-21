# Mapa wymagań adherence-first do kodu

| Wymaganie badania | Istniejący element | Luka / etap |
| --- | --- | --- |
| wersjonowany plan i aktywacja | `trainingplanning`, `planworkflow`, `PlanRevisionQueryPort` | warianty sesji — 2 |
| termin i okno sesji | `scheduledDate`, `availableFrom`, `availableTo` w snapshotach | agenda, strefa i statusy — 1 |
| bezpieczne wykonanie | `SessionExecutionService`, `SafetyAssessmentPort`, append-only execution | próba i postęp — 3 |
| safety envelope bez rationale | ADR-008, `SafetyAssessmentPort` | bramkowanie każdego kroku — 1–5 |
| zgłoszenie bólu/trudności i alert | `PainDifficultyReport`, projekcje execution | wersjonowana bariera i reakcje — 4 |
| powrót po przerwie | brak | `RecoveryEpisode`, reguły i projekcja — 5 |
| decyzja specjalisty | relacje i authorization, execution alerts | deduplikowana worklista oraz kontakt — 6 |
| participant UX | `/sessions`, legacy API facade | today-only i guided flow — 7 |
| specialist UX V2 | `/plan`, `/specialist-alerts` | builder V2 oraz worklista — 8 |
| powiadomienia | moduł wskazany w architekturze, brak implementacji | preferencje i reguły — 9 |
| mierzenie adherence | wykonania/projekcje jako dane źródłowe | neutralne metryki i eksperymenty — 10 |
| gamifikacja | osobny `gamification`, ADR-007 | pozostaje opt-in, poza primary flow |
