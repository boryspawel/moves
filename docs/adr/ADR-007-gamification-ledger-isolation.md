# ADR-007: Ledger i izolacja gamifikacji

Status: accepted

## Kontekst

Gamifikacja ma reagować na bezpiecznie dopuszczone wykonanie, ale nie może odczytywać bólu, trudności, notatek ani ograniczeń uczestnika. Punkty muszą być korygowalne bez niszczenia historii, a ranking odbudowywalny.

## Decyzja

- Jedynym źródłem wyniku jest append-only `PointLedgerEntry`.
- Korekta jest ujemnym reversal wskazującym wpis pierwotny.
- `ExecutionQualificationPort` udostępnia wyłącznie neutralne metadane istniejącego deklarowanego wykonania.
- Brak wykonanego rekordu po hard blocku oznacza brak zdarzenia kwalifikującego.
- Reguły są publikowanymi, niezmiennymi wersjami; kod nie zawiera niezatwierdzonych stałych biznesowych.
- Ranking jest pozbawioną danych wrażliwych projekcją ledgeru, filtrowaną przez bieżący opt-in i zgodę na widoczność.

## Konsekwencje

Safety może rozwijać reguły niezależnie i nie przecieka do gamifikacji. Rebuild rankingu jest deterministyczny, a reversal pozostawia audyt. Naliczenie wymaga aktywnej reguły operacyjnej; brak zatwierdzonej konfiguracji jest jawnym stanem, nie ukrytym defaultem.
