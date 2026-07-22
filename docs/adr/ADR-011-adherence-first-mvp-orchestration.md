# ADR-011: MVP adherence-first i projekcje uczestnika oraz specjalisty

Status: accepted  
Data: 2026-07-21

Aktualny zakres realizacyjny określa [`prompt.md`](../../prompt.md). Starszy
`docs/moves-codex-implementation-prompts-training.md` ma inną numerację i nie
jest statusową roadmapą tego strumienia.

## Kontekst

MVP ma pomagać uczestnikowi przejść przez cztery momenty: rozpoczęcie,
wykonanie, zgłoszenie bariery oraz powrót po przerwie. Nie może to tworzyć
drugiego modelu planu, wykonania ani alertów ani mieszać wsparcia adherence z
bezpieczeństwem klinicznym.

## Decyzja

- `trainingplanning` pozostaje właścicielem planu, aktywnej niezmiennej rewizji,
  planowanych sesji, ich terminów i zatwierdzonych wariantów `STANDARD`, `SHORT`
  i `MINIMUM`.
- `trainingexecution` pozostaje właścicielem próby sesji, append-only finalnego
  `SessionExecution`, rzeczywistej dawki, postępu i check-inu. Próba wskazuje
  sesję oraz rewizję; nie kopiuje planu.
- `safety` pozostaje właścicielem ograniczeń i niezmiennych assessmentów. Każde
  rozpoczęcie, wznowienie, wariant i proponowana ścieżka powrotu są ograniczane
  przez effective safety envelope. Adherence nie diagnozuje, nie ujawnia
  clinical rationale i nigdy automatycznie nie zmienia ograniczeń medycznych.
- Cienki moduł `adherence` będzie właścicielem wyłącznie wersjonowanych reguł
  bariery i powrotu oraz ich audytowalnych decyzji. Komponuje dane przez
  publiczne porty `trainingplanning`, `trainingexecution` i `safety`; nie ma
  własnych planów, wykonań ani alertów bezpieczeństwa.
- `adherence` wystawia zaimplementowaną projekcję uczestnika `TodayAgenda` dla zalogowanego
  uczestnika i publikuje neutralne sygnały do projekcji `specialist` worklisty.
  Worklista jest własnością `specialist`, deduplikuje wzorce wymagające decyzji
  i sprawdza relację, capability, consent oraz purpose przed odczytem i akcją.
- Przepływ jest: `plan → agenda dnia → próba wykonania → wykonanie/bariera →
  deterministyczna reakcja → powrót po przerwie`. Granice okna i sortowanie
  agendy są wyliczane z `scheduledDate`, `availableFrom`, `availableTo` oraz
  strefy uczestnika; UTC pozostaje formatem trwałych chwil.
- Reguły są wersjonowane, wyjaśnialne i zapisywane wraz z decyzją. Operacje
  ponawialne są autoryzowane zasobowo, audytowane oraz idempotentne. Dostęp
  specjalisty wymaga capability, aktywnej relacji, consent i purpose.
- Gamifikacja nie jest częścią tego przepływu ani nawigacji domyślnej. Może
  konsumować wyłącznie dotychczasowe neutralne zdarzenie kwalifikacji i tylko w
  trybie opt-in.

## Kontrakty między modułami

| Konsument | Publiczny kontrakt | Dane minimalne |
| --- | --- | --- |
| `adherence` | `PlanRevisionQueryPort`, `PlannedSessionExecutionPort` | aktywna rewizja, sesja, okno, recepty, stan |
| `adherence` | `SafetyAssessmentPort` | effective result i neutralny explanation code |
| `trainingexecution` | port planowania i safety | zablokowana sesja, rewizja, wariant, dopuszczenie |
| `specialist` | sygnał adherence / port relacji i zgody | kategoria, priorytet, minimalne dane, plan i uczestnik |
| `notification` | wersjonowane decyzje adherence | reason code, kanał, strefa, bez danych klinicznych |

## Konsekwencje

Nie powstaje drugi silnik planowania, wykonania ani safety alertów. Projekcje
mogą zostać odbudowane z rekordów źródłowych i wersji reguł. UI uczestnika nie
prezentuje technicznych UUID ani clinical rationale. Neutralne sygnały i przyszłe
powiadomienia nie zawierają pełnej historii ani danych klinicznych. Funkcje
społeczne, rankingi, serie i kary nie są elementem MVP adherence-first.

P6 (worklista, issue i reply) jest dostarczone w commicie `d004a36`. P7 ma
bieżące, jeszcze niecommitowane zmiany UI; jego ograniczenia statusowe są w
mapie wymagań i etapach, nie w tej ponadczasowej decyzji.
