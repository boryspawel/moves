# CLIENT-UX-01 — kanoniczny model wyników i obserwacji

## Stan bieżący

Obecny publiczny `Measurement` gwarantuje wyłącznie `metricCode`, `value` i `unit`.
Nie identyfikuje rodzaju badania, protokołu, strony, ćwiczenia, autora, źródła, czasu
zaobserwowania ani jakości danych. Nie wolno więc przedstawiać go jako kanonicznego
modelu wyników, klinicznej obserwacji ani porównywalnego postępu.

## Następny etap

CLIENT-UX-01 powinien wprowadzić osobny, kanoniczny model rezultatu/obserwacji z
typowanymi wariantami. Co najmniej następujące rodziny muszą pozostać rozróżnialne:

- masa ciała i obwód;
- wynik siłowy PR: ćwiczenie, wariant, obciążenie i liczba powtórzeń;
- test maksymalnej liczby powtórzeń;
- ROM: staw, ruch, strona i protokół;
- test czasu, dystansu lub sprawności.

Każda obserwacja musi również zachować autora, źródło, czas zaobserwowania oraz
jakość danych. Te informacje są częścią znaczenia wyniku, a nie opcjonalnym opisem UI.

Projekt etapu ma ustalić słowniki, jednostki, walidację wariantów, relację do
uczestnika i uprawnienia zapisu/odczytu, a dopiero potem kontrakty API i persystencję.
Nie należy przedwcześnie wybierać ani generycznego dokumentu JSONB, ani jednej szerokiej
tabeli z nullable kolumnami: oba warianty zacierałyby reguły wymagane przez odmienne
rodziny obserwacji.

Do czasu tej decyzji `Measurement` pozostaje minimalną wartością metryki w istniejącym
widoku i nie jest podstawą do migracji, agregowania lub interpretacji wyników.
