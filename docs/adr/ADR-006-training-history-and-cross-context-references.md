# ADR-006: Historia treningowa i referencje między kontekstami

Status: accepted

## Kontekst

Planowanie musi wskazywać wersję ćwiczenia z katalogu i konto z modułu tożsamości. Wykonanie musi zachować historyczny zapis sesji, wyników, bólu/trudności oraz korekt. Współdzielenie encji JPA albo przypadkowe klucze obce między wszystkimi schematami sprzęgałyby cykle życia modułów.

## Decyzja

- `training_planning` i `training_execution` są osobnymi kontekstami oraz schematami.
- Hierarchia wewnątrz kontekstu używa relacyjnych FK i ograniczeń PostgreSQL.
- Konto uczestnika i wersja ćwiczenia są zapisywane jako stabilne UUID bez międzykontekstowego FK.
- Tworzenie planu waliduje aktywne konto, relację i opublikowaną wersję przez publiczne usługi aplikacyjne.
- Execution, result i report są append-only; korekta jest osobnym rekordem z aktorem, powodem i czasem.
- Rodzaj sesji rozróżnia pracę samodzielną od spotkania offline.

## Konsekwencje

Historia nie jest niszczona przez korektę ani zmianę modelu katalogu. Moduły mogą ewoluować niezależnie, ale wymagają kontroli spójności w przypadkach użycia i testów kontraktowych. Usuwanie danych musi respektować politykę retencji i nie może polegać wyłącznie na kaskadzie FK między kontekstami.
