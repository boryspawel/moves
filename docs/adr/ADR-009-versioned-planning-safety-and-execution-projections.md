# ADR-009: Wersjonowane planowanie, safety i projekcje wykonania

Status: accepted

## Kontekst

Ocena bezpieczeństwa oraz analiza obciążenia muszą być odtwarzalne dla dokładnej rewizji planu i wersji ćwiczeń. Deklaracje wykonania mogą być ponawiane, korygowane i przetwarzane asynchronicznie. Nadpisywanie wyniku lub agregatu usuwałoby dowody potrzebne do wyjaśnienia decyzji.

## Decyzja

- Rewizja planu jest niezmiennym punktem odniesienia po aktywacji i wskazuje dokładne opublikowane wersje ćwiczeń.
- Planned load i executed load pozostają osobnymi, wielowymiarowymi snapshotami; system nie tworzy globalnego load/risk score.
- Kalkulatory i reguły zapisują code oraz version użyte do obliczenia.
- Safety assessment jest niezmienny. Acknowledgement i ograniczony czasowo override są osobnymi rekordami z aktorem i zakresem.
- Aktywacja rewizji i publikacja neutralnego zdarzenia outbox są atomowe oraz idempotentne.
- Deklaracja wykonania i rzeczywista dawka są append-only. Korekta dopisuje historię, odbudowuje projekcje oraz inicjuje reversal poprzedniej kwalifikacji.
- Konsumpcja zdarzeń używa receipt/idempotency key. Błąd projekcji jest widoczny i może zostać bezpiecznie wznowiony.
- Alert bólu lub post-24h jest zadaniem ze stanem i historią przejść, nie jednorazową flagą.

## Konsekwencje

Decyzję można odtworzyć z rewizji, wersji katalogu, wersji kalkulatora i reguł. Retry nie dubluje aktywacji, wykonań ani projekcji. Korekty nie niszczą danych pierwotnych, a gamifikacja otrzymuje wyłącznie neutralny wynik kwalifikacji.
