# Moves Mobile — architektura informacji i ekrany UX v0.2

## Model pracy

**Dzień → Uczestnik → Spotkanie**. Dokumentacja, pomiary, plan i ćwiczenia są funkcjami kontekstowymi, a nie dodatkowymi zakładkami głównymi.

### Nawigacja

- Dolny pasek: **Dzień**, **Uczestnicy**. Ekran S04 „Sprawy” odroczony — brak uzasadnienia dla trzeciej głównej zakładki; sygnały i zadania pozostają na Dniu.
- Kartoteka otwierana z Dnia lub Uczestników, zawsze z zachowaniem participantId; z następnego terminu kartoteki można przejść do S05.
- Tworzenie terminu dostępne z Dnia i kartoteki; zmianę terminu otwiera S05 tylko, gdy backend zwróci `UPDATE` w `availableActions`.
- **Spotkanie** nie jest czwartą zakładką: pełnoekranowy kontekst pracy, dostępny z terminu/kartoteki i możliwy do wznowienia.
- Docelowo realizacja i dokumentacja mogą być wykonywane w dowolnej kolejności; brak sztywnego kreatora. S06/S07/S09 mają w MOB-032 bezpieczne, nieedytowalne trasy; ich funkcje biznesowe powstaną w M4/M5. S08 pomiary pozostaje całkowicie niedostępny.
- Krótkie akcje jako bottom sheets/dialogi, długie formularze (np. wywiad) jako osobne strony.
- Back nie kończy spotkania ani nie usuwa szkiców; wyjście z niezapisanym formularzem wymaga świadomej decyzji.
- Tablet/duży ekran: navigation rail, lista–szczegóły; wspierać rotację i powiększenie.
- Deep link jest identyfikatorem zasobu, nie grantem dostępu; ponownie autoryzować odczyt na backendzie.

## Ekrany MVP

| ID | Ekran | Priorytet informacji | Akcja główna |
|---|---|---|---|
| S01 | Dzień | bieżąca/następna wizyta, agenda, zadania | Otwórz / rozpocznij spotkanie |
| S02 | Uczestnicy | wyszukiwanie, kartoteki, najbliższy termin | Otwórz kartotekę; dodaj nową |
| S03 | Kartoteka | aktualna sytuacja, cele, plan, timeline | Otwórz spotkanie lub zapisz wpis |
| S04 — odroczony | Sprawy | sygnały i zaległości pozostają na Dniu S01 | bez osobnej zakładki |
| S05 | Spotkanie | status, cel, powiązana sesja, następna czynność | Rozpocznij / kontynuuj |
| S06 | Realizacja sesji | planowana dawka, fakty wykonania, reakcja po sesji | Zapisz wykonanie |
| S07 | Dokumentacja | wywiad, notatki, szkice | Kontynuuj / zapisz / finalizuj |
| S08 | Pomiary — **projekt rezerwowy, nieaktywny w MVP** | dopuszczony parametr, jednostka, wartość, data | niedostępne do czasu serwerowej zgody |
| S09 | Zamknięcie | kompletność, zapis wykonania, pomiary, podsumowanie | Zakończ spotkanie |

## Przepływ

~~~mermaid
flowchart TD
  A[Logowanie OIDC] --> B[Dzień S01]
  B <--> C[Uczestnicy S02]
  B --> E[Kartoteka S03]
  C --> E
  E --> F[Spotkanie S05]
  B --> F
  F --> G[Realizacja S06]
  F --> H[Dokumentacja S07]
  %% S08 Pomiary: poza aktywnym MVP do czasu zgody po stronie backendu
  G --> F
  H --> F
  F --> J[Zamknięcie S09]
  J --> B
  E --> H
  E --> I
~~~

## Minimalne dane i API AS-IS

| Ekrany | Istniejące kontrakty serwerowe |
|---|---|
| S01 | GET /api/v1/specialist/today |
| S02 | GET/POST /api/v1/specialist/clients (lista rzeczywistych kartotek, tworzenie lokal/test); GET /api/v1/specialist/participants pozostaje wyłącznie wyborem aktywnych relacji |
| S03 | GET /api/v1/specialist/participants/{participantId}/workspace i /timeline (bez prezentacji pomiarów; stronicowanie kursorem) |
| S04 — odroczony | Today attentionItems i operationalTasks pozostają w S01; worklist istnieje w backendzie, lecz nie stanowi osobnego ekranu mobile |
| S05 | GET /api/v1/specialist/appointments/{id}; POST /{id}/start |
| S06 | /api/v1/specialist/appointments/{appointmentId}/execution-context i /execution |
| S07 | /api/v1/specialist/clients/{participantId}/records/interviews i /notes |
| S08 — wstrzymany | /api/v1/specialist/clients/{participantId}/measurements i /catalog istnieją, ale nie weryfikują odrębnej zgody na zapis pomiarów |
| S09 | /api/v1/specialist/appointments/{appointmentId}/closeout-context; POST /api/v1/specialist/appointments/{id}/complete |

Każdy kontrakt powyżej należy zestawić z aktualnym web/openapi/openapi.json podczas M0 (włącznie ze strukturą odpowiedzi i nazwami operacji); nie wytwarzać ręcznego klienta na podstawie samej tabeli.

**Sprawy:** projekcja istniejących bezpiecznych źródeł; nie budować nowej worklisty ani nie kopiować wrażliwej treści do listy.

## Reguły interakcji

- Jedna główna akcja na ekran, kontekstowo zależna od stanu i availableActions.
- Na kartotece najpierw aktualna sytuacja; historia i szczegółowe dane są rozwijane progresywnie.
- Brak technicznych UUID w etykietach, brak klinicznej treści w neutralnych alertach/timeline.
- Stany: loading, empty, error, forbidden, offline, conflict i success.
- Powrót do spotkania z zachowanymi appointmentId i participantId; na wejściu do S06/S07/S09 ponowny autoryzowany GET i weryfikacja obu identyfikatorów. Po restarcie ponowne pobranie aktualnego statusu z backendu.
- Wykonanie (actual) i planowane dawki (planned snapshot) mają odrębną prezentację.

## Ograniczenie M0

Nie podłączać w aplikacji Android S08 ani innych form wprowadzania biomedycznych pomiarów lub obserwacji, dopóki zgoda nie jest egzekwowana przez backend. Prototyp zawiera symulowany ekran S08 wyłącznie jako materiał projektowy. Bez załączników, uploadów wyników badań i diagnostyki obrazowej.

## Artefakt UX

[Otwórz klikalny prototyp 9 ekranów](prototype/index.html). Dane w prototypie są fikcyjne; akcje symulowane w przeglądarce nie zapisują niczego na serwerze Moves. Używać jako referencji UX, nie jako bazy implementacji Android.
