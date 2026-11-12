# Moves Mobile — definicja MVP v0.2

**Cel:** kompletny cykl pracy specjalisty na telefonie: **Dzień → Uczestnik → Spotkanie → Realizacja, dokumentacja i pomiary → Zakończenie**. Użytkownicy: trener i fizjoterapeuta w aktywnym kontekście zawodowym.

## Założenia produktowe

- Mobile jest narzędziem pracy przy uczestniku, web pozostaje pełnym środowiskiem zarządzania i analizy.
- Nie przenosimy menu Angulara 1:1; procesy są zorientowane na zadania, w kontekście uczestnika i terminu.
- Kanoniczny participantId identyfikuje kartotekę; uczestnik nie musi posiadać konta.
- Backend pozostaje autorytatywnym źródłem stanu, uprawnień, zgód, safety, przepisów na ćwiczenia i danych klinicznych.

## Zakres P0 — pięć strumieni

| ID | Funkcja | Rezultat |
|---|---|---|
| M1 — Mój dzień | Agenda, bieżący/następny termin, neutralne sprawy, podstawowe operacje na terminie | Gotowość do pracy |
| M2 — Uczestnicy | Lista i wyszukiwanie, dodanie kartoteki, kontekst, cele, podgląd planu, timeline | Szybki dostęp do aktualnej sytuacji |
| M3 — Prowadzenie wizyty | Start, kontekst bezpieczeństwa, realizacja powiązanej sesji, rzeczywiste parametry i pominięcia | Zapis faktów wykonania |
| M4 — Dokumentacja | Wywiad, notatki, pomiary, szkice i finalizacja według dostępnych akcji | Ciągłość dokumentacji |
| M5 — Zamknięcie | Weryfikacja wymogów, podsumowanie, status końcowy, aktualizacja agendy i historii | Prawidłowe zakończenie spotkania |

## Podział Android / Web

| Operacja | Android MVP | Web |
|---|---|---|
| Agenda, terminy, szybkie planowanie i lifecycle | pełna operacja | pełna operacja |
| Lista i utworzenie kartoteki uczestnika | pełna operacja | pełna operacja |
| Kartoteka, cele, aktywny plan, timeline | kontekstowy odczyt i szybkie akcje | pełna obsługa |
| Rejestracja wykonania sesji | pełna operacja | pełna operacja |
| Wywiady, notatki, pomiary, obserwacje | pełna operacja | pełna operacja |
| Katalog ćwiczeń i safety | minimalny dopuszczony podgląd | pełna obsługa |
| Edycja planów i rewizji, builder zestawów | poza MVP | pełna obsługa |
| Zaawansowane analizy anatomii i obciążenia | poza MVP | pełna obsługa |
| Onboarding, konfiguracja dostępności i administracja | poza MVP (wymagany ukończony onboarding) | pełna obsługa |

## Główne user journeys

| ID | Przepływ | Kryterium ukończenia |
|---|---|---|
| UJ-01 | Login → Dzień → agenda/sprawy → spotkanie | dostępny właściwy termin w strefie specjalisty |
| UJ-02 | Termin → kartoteka → cele/plan/ostatnia historia | specjalista może zacząć pracę z właściwym uczestnikiem |
| UJ-03 | Spotkanie w toku → ćwiczenia → zgodnie/zmieniono/pominięto → zapis | zapis rzeczywistych faktów bez zmiany planowanej recepty |
| UJ-04 | Kartoteka/spotkanie → notatka/wywiad/pomiar → zapis/finalizacja | chronione dane widoczne w odpowiedniej historii |
| UJ-05 | Spotkanie → weryfikacja closeout → zakończenie | serwer potwierdza status końcowy, brak podwójnego zapisu |
| UJ-06 | Dodanie uczestnika → kartoteka → wywiad lub termin | obsługa także uczestnika bez konta |

## Reguły jakości i dostępu

- Role TRAINER/PHYSIOTHERAPIST, jawny ActingContext, purpose/capability, aktywna relacja i zgoda są sprawdzane po stronie serwera.
- Po cofnięciu relacji/zgody nie może pozostać dostęp do chronionych danych przez stan/cache aplikacji.
- Zakończenie spotkania tylko, gdy backend dopuści operację; jeżeli wykonanie powiązanej sesji jest wymagane, nie wolno go pominąć.
- Notatki finalne i ukończone wywiady są niezmienne; UI respektuje zwrócone availableActions.
- Każda operacja wrażliwa na ponowienie korzysta z istniejącego Idempotency-Key i wersji zasobu.
- Nie utożsamiać niezapisanego szkicu z potwierdzonym rekordem backendu; przy 409 ponownie pobrać stan i umożliwić świadome rozstrzygnięcie.
- Podstawowe procesy muszą działać na telefonie, z TalkBack, skalowaniem fontów, bez pułapek nawigacyjnych.

## Poza MVP

Pełny offline, replika dokumentacji medycznej, push, wearables, systemowy kalendarz, AI, dyktowanie, edycja katalogu, pełne analizy i zaawansowane authoring planu.
